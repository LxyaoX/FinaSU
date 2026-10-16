//! Dynamic-manager signature persistence and kernel replay.
//!
//! FolkSU's dynamic-manager trust model keeps a volatile table of user-claimed
//! signatures in the kernel. This module owns the userspace half: it extracts
//! APK v2 certificate signatures, persists the user's list as JSON at
//! [`defs::DYNAMIC_MANAGER_PATH`] with mode 0600 and pushes the table back to
//! the kernel through `KSU_IOCTL_SET_DYNAMIC_MANAGERS`. The list is replayed on
//! every boot from `on_post_fs_data`.

use std::fs::{OpenOptions, Permissions, set_permissions};
use std::io::Write;
use std::os::raw::c_char;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;
use std::process::Command;
use std::time::Duration;

use anyhow::{Context, Result, bail, ensure};
use log::{info, warn};

use crate::{apk_sign, defs, ksu_uapi, ksucalls, utils};

/// One Android user covers 100000 UIDs; the app id is `uid % PER_USER_RANGE`.
const PER_USER_RANGE: u32 = 100_000;

/// Maximum number of signatures the kernel accepts.
const MAX_SIGNS: usize = ksu_uapi::KSU_DYNAMIC_MANAGER_MAX_SIGNS as usize;

/// Maximum number of authorized appids the kernel accepts.
const MAX_GRANTS: usize = ksu_uapi::KSU_DYNAMIC_MANAGER_MAX_APPS as usize;

/// How long a mutation waits for a concurrent one before giving up.
const LOCK_ATTEMPTS: u32 = 50;
const LOCK_RETRY_DELAY: Duration = Duration::from_millis(100);

/// Stable failure codes, so a caller can tell cases apart without matching prose.
#[derive(Debug, Clone, Copy)]
#[repr(u32)]
enum ErrorCode {
    /// The kernel has no authorized-appid list.
    KernelUnsupported = 1,
    /// The configuration file is unreadable or malformed.
    ConfigInvalid = 2,
    /// The configuration could not be written; the kernel was rolled back.
    ConfigWriteFailed = 3,
    /// The kernel refused a table we pushed.
    KernelRejected = 4,
    /// The kernel does not agree with what we just pushed.
    KernelStateMismatch = 5,
    /// Refused an APK path that cannot become an authorized install.
    ApkPathInvalid = 6,
    /// Another update is in flight.
    LockBusy = 7,
}

impl ErrorCode {
    fn fail<T>(self, message: impl std::fmt::Display) -> Result<T> {
        bail!("E{:04}: {message}", self as u32)
    }
}

/// Fails with a stable code from inside the mutation closures.
macro_rules! fail {
    ($code:expr, $($arg:tt)*) => {
        return $code.fail(format!($($arg)*))
    };
}

/// A normalized `(size, sha256)` certificate signature, optionally carrying the
/// version code of the manager APK it was extracted from.
#[derive(Debug, Clone, PartialEq, Eq)]
struct Sign {
    size: u32,
    hash: String,
    version_code: u32,
}

impl Sign {
    /// Whether two signatures cover the same certificate. The version code is
    /// metadata used by the kernel to report a matching manager version, not
    /// part of the certificate identity.
    fn same_cert(&self, other: &Self) -> bool {
        self.size == other.size && self.hash == other.hash
    }

    /// Encode into the UAPI struct, copying the hash into its fixed 65-byte
    /// NUL-terminated buffer.
    fn to_uapi(&self) -> ksu_uapi::ksu_dynamic_manager_sign {
        let mut hash = [c_char::default(); 65];
        for (dst, byte) in hash.iter_mut().zip(self.hash.bytes()) {
            *dst = byte as c_char;
        }
        ksu_uapi::ksu_dynamic_manager_sign {
            size: self.size,
            version_code: self.version_code,
            hash,
        }
    }
}

/// Parse a certificate size, accepting decimal or `0x`-prefixed hex.
fn parse_size(value: &str) -> Result<u32> {
    let value = value.trim();
    value
        .strip_prefix("0x")
        .or_else(|| value.strip_prefix("0X"))
        .map_or_else(|| value.parse::<u32>(), |hex| u32::from_str_radix(hex, 16))
        .with_context(|| format!("invalid size: {value}"))
}

/// Validate and lowercase a 64-character hex SHA-256.
fn normalize_hash(value: &str) -> Result<String> {
    ensure!(
        value.len() == 64,
        "hash must be 64 hex characters, got {}",
        value.len()
    );
    let mut normalized = String::with_capacity(64);
    for ch in value.chars() {
        ensure!(
            ch.is_ascii_hexdigit(),
            "hash contains a non-hex character: {ch}"
        );
        normalized.push(ch.to_ascii_lowercase());
    }
    Ok(normalized)
}

fn parse_sign(size_arg: &str, hash_arg: &str) -> Result<Sign> {
    Ok(Sign {
        size: parse_size(size_arg)?,
        hash: normalize_hash(hash_arg)?,
        version_code: 0,
    })
}

fn parse_size_value(value: &serde_json::Value) -> Option<u32> {
    match value {
        serde_json::Value::Number(number) => u32::try_from(number.as_u64()?).ok(),
        serde_json::Value::String(text) => parse_size(text).ok(),
        _ => None,
    }
}

fn sign_from_json(map: &serde_json::Map<String, serde_json::Value>) -> Option<Sign> {
    let size = map.get("size").and_then(parse_size_value)?;
    let hash = map
        .get("hash")
        .and_then(serde_json::Value::as_str)
        .and_then(|value| normalize_hash(value).ok())?;
    let version_code = map
        .get("version_code")
        .and_then(serde_json::Value::as_u64)
        .and_then(|value| u32::try_from(value).ok())
        .unwrap_or(0);
    Some(Sign {
        size,
        hash,
        version_code,
    })
}

/// Walk a parsed config, tolerating both a bare array and an object that nests
/// the array under one of the historical keys. Deduplicates `(size, hash)`.
fn collect_signs(value: &serde_json::Value, signs: &mut Vec<Sign>) {
    match value {
        serde_json::Value::Array(items) => {
            for item in items {
                collect_signs(item, signs);
            }
        }
        serde_json::Value::Object(map) => {
            if let Some(sign) = sign_from_json(map) {
                if !signs.iter().any(|existing| existing.same_cert(&sign)) {
                    signs.push(sign);
                }
                return;
            }
            for key in ["managers", "dynamic_managers", "signs"] {
                if let Some(inner) = map.get(key) {
                    collect_signs(inner, signs);
                }
            }
        }
        _ => {}
    }
}

fn parse_signs(content: &str) -> Result<Vec<Sign>> {
    let value: serde_json::Value =
        serde_json::from_str(content).context("failed to parse dynamic manager config")?;
    let mut signs = Vec::new();
    collect_signs(&value, &mut signs);
    if signs.len() > MAX_SIGNS {
        warn!(
            "dynamic manager: truncating {} signature(s) to {MAX_SIGNS}",
            signs.len()
        );
        signs.truncate(MAX_SIGNS);
    }
    Ok(signs)
}

/// The persisted configuration: the recognized signatures plus the appids the
/// user authorized as dynamic managers.
#[derive(Debug, Default, Clone)]
struct Config {
    signs: Vec<Sign>,
    uids: Vec<u32>,
    /// False when the file predates the authorization list, i.e. the list still
    /// has to be seeded from whatever the kernel currently honours.
    uids_present: bool,
}

/// Pull the authorized appid list out of a parsed config, tolerating the same
/// nesting the signature list accepts so older hand-written files keep working.
fn parse_uids(value: &serde_json::Value) -> Result<Option<Vec<u32>>> {
    fn find(value: &serde_json::Value) -> Option<&serde_json::Value> {
        let map = value.as_object()?;
        if let Some(entry) = map.get("uids") {
            return Some(entry);
        }
        for key in ["managers", "dynamic_managers", "signs"] {
            if let Some(inner) = map.get(key)
                && let Some(found) = find(inner)
            {
                return Some(found);
            }
        }
        None
    }

    let Some(entry) = find(value) else {
        return Ok(None);
    };
    let mut uids = Vec::new();
    for item in entry.as_array().context("uids must be an array")? {
        let uid = item
            .as_u64()
            .and_then(|value| u32::try_from(value).ok())
            .context("uids must hold appids")?;
        ensure!(uid < PER_USER_RANGE, "appid out of range: {uid}");
        if !uids.contains(&uid) {
            uids.push(uid);
        }
        ensure!(
            uids.len() <= MAX_GRANTS,
            "too many authorized appids: {} > {MAX_GRANTS}",
            uids.len()
        );
    }
    Ok(Some(uids))
}

fn config_to_json(config: &Config) -> String {
    let signs = &config.signs;
    let entries = signs
        .iter()
        .map(|sign| {
            serde_json::json!({
                "size": sign.size,
                "hash": sign.hash,
                "version_code": sign.version_code,
            })
        })
        .collect::<Vec<_>>();
    let value = serde_json::json!({ "signs": entries, "uids": config.uids });
    let mut text =
        serde_json::to_string_pretty(&value).unwrap_or_else(|_| String::from("{\"signs\":[]}"));
    text.push('\n');
    text
}

fn load_config() -> Result<Config> {
    let path = Path::new(defs::DYNAMIC_MANAGER_PATH);
    let content = match std::fs::read_to_string(path) {
        Ok(content) => content,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Config::default()),
        Err(e) => return Err(e).with_context(|| format!("failed to read {}", path.display())),
    };
    let value: serde_json::Value =
        serde_json::from_str(&content).context("failed to parse dynamic manager config")?;
    let signs = parse_signs(&content)?;
    let uids = parse_uids(&value)?;
    Ok(Config {
        signs,
        uids_present: uids.is_some(),
        uids: uids.unwrap_or_default(),
    })
}

fn save_config(config: &Config) -> Result<()> {
    let path = Path::new(defs::DYNAMIC_MANAGER_PATH);
    if let Some(parent) = path.parent() {
        utils::ensure_dir_exists(parent)?;
    }
    let mut file = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(path)
        .with_context(|| format!("failed to open {}", path.display()))?;
    file.write_all(config_to_json(config).as_bytes())
        .with_context(|| format!("failed to write {}", path.display()))?;
    // `OpenOptionsExt::mode` only applies on creation, so enforce it here too.
    set_permissions(path, Permissions::from_mode(0o600))
        .with_context(|| format!("failed to chmod {}", path.display()))?;
    Ok(())
}

/// Push `signs` to the kernel, replacing whatever table is there.
fn apply_signs(signs: &[Sign]) -> Result<()> {
    ensure!(
        signs.len() <= MAX_SIGNS,
        "E{:04}: too many dynamic manager signatures: {} > {MAX_SIGNS}",
        ErrorCode::ConfigInvalid as u32,
        signs.len()
    );
    let raw = signs.iter().map(Sign::to_uapi).collect::<Vec<_>>();
    ksucalls::set_dynamic_managers(&raw)
        .map_err(|err| anyhow::anyhow!("E{:04}: {err:#}", ErrorCode::KernelRejected as u32))
}

/// Whether the kernel understands the authorized-appid list.
fn grants_supported() -> Result<bool> {
    let state = ksucalls::get_feature(ksu_uapi::ksu_feature_id_KSU_FEATURE_DYNAMIC_MANAGER_GRANTS)?;
    Ok(state.supported)
}

/// Serializes every configuration read-modify-write.
///
/// Two commands racing (a manager toggle and the boot replay, say) would read the
/// same file and the second write would drop the first change.
struct ConfigLock {
    _file: std::fs::File,
}

impl ConfigLock {
    fn acquire() -> Result<Self> {
        let path = Path::new(defs::DYNAMIC_MANAGER_LOCK_PATH);
        if let Some(parent) = path.parent() {
            utils::ensure_dir_exists(parent)?;
        }
        let file = OpenOptions::new()
            .create(true)
            .write(true)
            .truncate(false)
            .mode(0o600)
            .open(path)
            .with_context(|| format!("failed to open {}", path.display()))?;

        for _ in 0..LOCK_ATTEMPTS {
            if rustix::fs::flock(&file, rustix::fs::FlockOperation::NonBlockingLockExclusive)
                .is_ok()
            {
                return Ok(Self { _file: file });
            }
            std::thread::sleep(LOCK_RETRY_DELAY);
        }
        ErrorCode::LockBusy.fail("another dynamic manager update is in progress")
    }
}

/// Push `uids` to the kernel, replacing whatever authorization list is there.
///
/// The first successful call also switches the kernel from "any trusted
/// signature is a manager" to "only authorized appids are", so a kernel without
/// the feature is a hard error rather than a silent no-op.
fn apply_grants(uids: &[u32]) -> Result<()> {
    ensure!(
        grants_supported()?,
        "E{:04}: this kernel does not support the authorized dynamic-manager list",
        ErrorCode::KernelUnsupported as u32
    );
    ensure!(
        uids.len() <= MAX_GRANTS,
        "too many authorized appids: {} > {MAX_GRANTS}",
        uids.len()
    );
    ksucalls::set_dynamic_manager_grants(uids)
        .map_err(|err| anyhow::anyhow!("E{:04}: {err:#}", ErrorCode::KernelRejected as u32))
}

/// Reads the configuration, applies `mutate` and commits the result.
///
/// The kernel and the file are updated together while the config lock is held, so
/// a failed write cannot leave them disagreeing about who is authorized.
fn mutate_config<F>(mutate: F) -> Result<Config>
where
    F: FnOnce(&mut Config) -> Result<bool>,
{
    let _lock = ConfigLock::acquire()?;
    let grants = grants_supported()?;
    let mut config = load_config()?;

    /*
     * A configuration written before the authorization list existed carries no
     * list, so adopting what the kernel honours now is the difference between
     * "authorize this appid" and "authorize this appid and revoke everyone else".
     */
    if grants && !config.uids_present {
        seed_grants(&mut config)?;
    }

    let previous = config.clone();
    if !mutate(&mut config)? {
        return Ok(config);
    }
    commit_config(&previous, &config, grants)?;
    Ok(config)
}

/// Pushes `next` to the kernel and persists it, undoing the kernel side when it
/// cannot be written. Returns the kernel to `previous`.
fn rollback(previous: &Config, grants: bool) -> Result<()> {
    apply_signs(&previous.signs)?;
    if grants && previous.uids_present {
        /*
         * The strict flag itself is sticky by design (that is what keeps a reboot
         * from reopening the window), so only the list can be restored.
         */
        ksucalls::set_dynamic_manager_grants(&previous.uids)?;
    }
    Ok(())
}

fn commit_config(previous: &Config, next: &Config, grants: bool) -> Result<()> {
    apply_signs(&next.signs)?;
    if grants {
        ksucalls::set_dynamic_manager_grants(&next.uids)?;
        if let Err(err) = verify_grants(&next.uids) {
            if let Err(rollback) = rollback(previous, grants) {
                warn!("dynamic manager: rollback failed: {rollback:#}");
            }
            return Err(err);
        }
    }

    if let Err(err) = save_config(next) {
        warn!("dynamic manager: cannot persist the new configuration: {err:#}");
        /*
         * The file is what the next boot replays, so a kernel that is ahead of it
         * would silently lose this change on the next boot. Put the kernel back.
         */
        if let Err(rollback) = rollback(previous, grants) {
            warn!("dynamic manager: rollback failed: {rollback:#}");
        }
        return ErrorCode::ConfigWriteFailed.fail(err);
    }
    Ok(())
}

/// Register a signature (recognition only).
fn register_sign(config: &mut Config, sign: Sign) -> Result<()> {
    if let Some(existing) = config
        .signs
        .iter_mut()
        .find(|existing| existing.same_cert(&sign))
    {
        if sign.version_code != 0 {
            existing.version_code = sign.version_code;
        }
    } else {
        if config.signs.len() >= MAX_SIGNS {
            fail!(
                ErrorCode::ConfigInvalid,
                "too many dynamic manager signatures: {} >= {MAX_SIGNS}",
                config.signs.len()
            );
        }
        config.signs.push(sign);
    }
    Ok(())
}

/// Reads the kernel's app table back.
fn kernel_apps() -> Result<Vec<ksu_uapi::ksu_dynamic_manager_app>> {
    ksucalls::get_dynamic_managers()
}

/// Confirms the kernel publishes exactly the authorization list we pushed.
///
/// An appid that is authorized but whose APK no scan has seen yet is not reported
/// at all; that is expected, so it only warns.
fn verify_grants(expected: &[u32]) -> Result<()> {
    let apps = kernel_apps()?;
    for app in &apps {
        let reported = app.flags & ksu_uapi::KSU_DYNAMIC_MANAGER_FLAG_GRANTED != 0;
        let wanted = expected.contains(&app.appid);
        if reported != wanted {
            return ErrorCode::KernelStateMismatch.fail(format!(
                "appid {} is {} in the kernel but {} in the configuration",
                app.appid,
                if reported {
                    "authorized"
                } else {
                    "not authorized"
                },
                if wanted {
                    "authorized"
                } else {
                    "not authorized"
                }
            ));
        }
    }
    for appid in expected {
        if !apps.iter().any(|app| app.appid == *appid) {
            warn!(
                "dynamic manager: appid {appid} is authorized but its installed APK was not \
                 recognized yet"
            );
        }
    }
    Ok(())
}

/// Confirms the kernel recognized `appid` after its signature was registered.
///
/// A signature only matters for the install the kernel scans under /data/app, so
/// this is what turns "the table was accepted" into "this app is a manager".
fn verify_appid_trusted(appid: u32) -> Result<()> {
    let apps = kernel_apps()?;
    let trusted = apps.iter().any(|app| {
        app.appid == appid && app.flags & ksu_uapi::KSU_DYNAMIC_MANAGER_FLAG_TRUSTED != 0
    });
    if !trusted {
        return ErrorCode::KernelStateMismatch.fail(format!(
            "the kernel did not recognize appid {appid} after its signature was registered"
        ));
    }
    Ok(())
}

/// Authorize an appid (no effect on the signature list).
fn authorize_appid(config: &mut Config, appid: u32) -> Result<()> {
    if !config.uids.contains(&appid) {
        if config.uids.len() >= MAX_GRANTS {
            fail!(
                ErrorCode::ConfigInvalid,
                "too many authorized appids: {} >= {MAX_GRANTS}",
                config.uids.len()
            );
        }
        config.uids.push(appid);
    }
    config.uids_present = true;
    Ok(())
}

/// Authorize one appid, keeping the persisted signature list as it is.
pub fn grant(uid: u32) -> Result<()> {
    let appid = uid % PER_USER_RANGE;
    mutate_config(|config| {
        authorize_appid(config, appid)?;
        Ok(true)
    })?;
    println!("authorized appid: {appid}");
    Ok(())
}

/// Revoke one appid. The signature stays registered: it keeps *recognizing* the
/// app, it just stops honouring it.
pub fn ungrant(uid: u32) -> Result<()> {
    let appid = uid % PER_USER_RANGE;
    mutate_config(|config| {
        let before = config.uids.len();
        config.uids.retain(|existing| *existing != appid);
        if config.uids.len() == before {
            fail!(ErrorCode::ConfigInvalid, "appid {appid} is not authorized");
        }
        config.uids_present = true;
        Ok(true)
    })?;
    println!("revoked appid: {appid}");
    Ok(())
}

fn add_sign(sign: &Sign) -> Result<()> {
    let size = sign.size;
    let hash = sign.hash.clone();
    mutate_config(|config| {
        register_sign(config, sign.clone())?;
        Ok(true)
    })?;
    println!("size: {size:#x}");
    println!("hash: {hash}");
    Ok(())
}

fn sign_from_apk(apk: &str, version_code: u32) -> Result<Sign> {
    let (size, hash) = apk_sign::get_apk_signature(apk)
        .with_context(|| format!("failed to get APK signature: {apk}"))?;
    Ok(Sign {
        size,
        hash: normalize_hash(&hash)?,
        version_code,
    })
}

/// Run a program and return its stdout when it exits successfully.
fn run_command(program: &str, args: &[&str]) -> Option<String> {
    let output = Command::new(program).args(args).output().ok()?;
    if output.status.success() {
        Some(String::from_utf8_lossy(&output.stdout).into_owned())
    } else {
        None
    }
}

/// Read a package's versionCode from `dumpsys package`, returning 0 when it
/// cannot be resolved. `dumpsys` lists it as `versionCode=<N> ...`.
fn version_code_for_package(package: &str) -> u32 {
    let Some(output) = run_command("dumpsys", &["package", package]) else {
        return 0;
    };
    output
        .lines()
        .find_map(|line| line.trim().strip_prefix("versionCode="))
        .and_then(|value| value.split_whitespace().next())
        .and_then(|value| value.parse::<u32>().ok())
        .unwrap_or(0)
}

/// Map a UID to package names using `cmd package` / `pm`.
fn packages_for_uid(uid: u32) -> Vec<String> {
    let user = (uid / PER_USER_RANGE).to_string();
    let variants: [(&str, Vec<&str>); 4] = [
        (
            "cmd",
            vec!["package", "list", "packages", "--user", &user, "-U"],
        ),
        ("pm", vec!["list", "packages", "--user", &user, "-U"]),
        ("cmd", vec!["package", "list", "packages", "-U"]),
        ("pm", vec!["list", "packages", "-U"]),
    ];
    let listing = variants.into_iter().find_map(|(program, args)| {
        let output = run_command(program, &args)?;
        if output.trim().is_empty() {
            None
        } else {
            Some(output)
        }
    });
    let Some(listing) = listing else {
        return Vec::new();
    };
    listing
        .lines()
        .filter_map(|line| package_from_line(line.trim(), uid))
        .collect()
}

fn package_from_line(line: &str, uid: u32) -> Option<String> {
    let package_pos = line.find("package:")?;
    let uid_pos = line.find("uid:")?;
    let package = line[package_pos + "package:".len()..]
        .split_whitespace()
        .next()?;
    let parsed_uid = line[uid_pos + "uid:".len()..]
        .split_whitespace()
        .next()?
        .parse::<u32>()
        .ok()?;
    if parsed_uid == uid && !package.is_empty() {
        Some(package.to_string())
    } else {
        None
    }
}

/// Resolve the APK paths of a package, preferring `base.apk`.
fn apk_paths_for_package(package: &str, uid: u32) -> Vec<String> {
    let user = (uid / PER_USER_RANGE).to_string();
    let variants: [(&str, Vec<&str>); 4] = [
        ("cmd", vec!["package", "path", "--user", &user, package]),
        ("pm", vec!["path", "--user", &user, package]),
        ("cmd", vec!["package", "path", package]),
        ("pm", vec!["path", package]),
    ];
    for (program, args) in variants {
        if let Some(output) = run_command(program, &args) {
            let paths = parse_package_paths(&output);
            if !paths.is_empty() {
                return paths;
            }
        }
    }
    Vec::new()
}

fn parse_package_paths(output: &str) -> Vec<String> {
    let mut paths = output
        .lines()
        .filter_map(|line| line.trim().strip_prefix("package:"))
        .map(str::trim)
        .filter(|path| !path.is_empty())
        .map(str::to_string)
        .collect::<Vec<_>>();
    // Stable sort keeps `base.apk` first without disturbing the rest.
    paths.sort_by_key(|path| !path.ends_with("/base.apk"));
    paths
}

/// Find the first installed APK with a usable v2 signature for `uid`.
fn apk_from_uid(uid: u32) -> Result<(String, String)> {
    let packages = packages_for_uid(uid);
    ensure!(!packages.is_empty(), "no package found for uid {uid}");
    for package in &packages {
        for path in apk_paths_for_package(package, uid) {
            if apk_sign::get_apk_signature(&path).is_ok() {
                return Ok((package.clone(), path));
            }
        }
    }
    bail!("no APK with v2 signature found for uid {uid}")
}

fn print_sign_json(size: u32, hash: &str) {
    let value = serde_json::json!({
        "v2": {
            "has": true,
            "hash": hash,
            "size": format!("{size:#x}"),
        }
    });
    println!("{value}");
}

/// `ksud dynamic get-sign [--json] <APK> | --uid <UID>`
pub fn get_sign(apk: Option<&str>, uid: Option<u32>, json: bool) -> Result<()> {
    let (package, apk_path) = if let Some(uid) = uid {
        let (package, path) = apk_from_uid(uid)?;
        (Some(package), path)
    } else if let Some(apk) = apk {
        (None, apk.to_string())
    } else {
        bail!("usage: ksud dynamic get-sign [--json] <APK> | --uid <UID>");
    };

    let (size, hash) = apk_sign::get_apk_signature(&apk_path)
        .with_context(|| format!("failed to get APK signature: {apk_path}"))?;
    let hash = normalize_hash(&hash)?;

    if json {
        print_sign_json(size, &hash);
    } else {
        if let Some(package) = &package {
            println!("package: {package}");
            println!("apk: {apk_path}");
        }
        println!("size: {size:#x}");
        println!("hash: {hash}");
    }
    Ok(())
}

/// `ksud dynamic set-hash <size> <hash>`
pub fn set_hash(size_arg: &str, hash_arg: &str) -> Result<()> {
    add_sign(&parse_sign(size_arg, hash_arg)?)
}

/// `ksud dynamic set-apk <APK> [--version-code <CODE>]`
pub fn set_apk(apk: &str, version_code: Option<u32>) -> Result<()> {
    ensure_installed_apk_path(apk)?;
    add_sign(&sign_from_apk(apk, version_code.unwrap_or(0))?)
}

/// Only an installed APK can become an authorized dynamic manager.
///
/// The kernel registers a certificate for whatever install it scans under
/// /data/app, so accepting a path from anywhere else would set up a permission
/// that can never be claimed. `get-sign` still reads any file, and `set-hash`
/// registers a signature directly, so this guards the one verb that names an APK.
fn ensure_installed_apk_path(apk: &str) -> Result<()> {
    let path = Path::new(apk);
    if !path.is_absolute() {
        fail!(ErrorCode::ApkPathInvalid, "{apk} is not an absolute path");
    }
    // Resolve first, so a symlink cannot make an arbitrary file look installed.
    let resolved = std::fs::canonicalize(path).unwrap_or_else(|_| path.to_path_buf());
    let installed = resolved.starts_with("/data/app/")
        && resolved.file_name().is_some_and(|name| name == "base.apk");
    if !installed {
        fail!(
            ErrorCode::ApkPathInvalid,
            "{} is not an installed APK (/data/app/**/base.apk)",
            resolved.display()
        );
    }
    Ok(())
}

/// `ksud dynamic set-uid <UID>`
///
/// Registers the APK's certificate *and* authorizes the appid: this is the
/// "make this app a dynamic manager" action the manager UI uses.
pub fn set_uid(uid: u32) -> Result<()> {
    let (package, apk) = apk_from_uid(uid)?;
    let version_code = version_code_for_package(&package);
    let sign = sign_from_apk(&apk, version_code)?;
    println!("package: {package}");
    println!("apk: {apk}");
    println!("version_code: {version_code}");

    let appid = uid % PER_USER_RANGE;
    mutate_config(|config| {
        register_sign(config, sign.clone())?;
        authorize_appid(config, appid)?;
        Ok(true)
    })?;
    println!("size: {:#x}", sign.size);
    println!("hash: {}", sign.hash);

    /*
     * The kernel has a scan in the same ioctl that installs the table, so the app
     * has to be recognized by now. If it is not, the signature can never be
     * honoured (the APK is not one the scan can reach) and the caller has to know.
     */
    verify_appid_trusted(appid)?;
    Ok(())
}

/// `ksud dynamic list [--json]`
pub fn list(json: bool) -> Result<()> {
    let config = load_config()?;
    if json {
        print!("{}", config_to_json(&config));
        return Ok(());
    }
    for sign in &config.signs {
        println!("size: {:#x}", sign.size);
        println!("hash: {}", sign.hash);
    }
    if !config.uids.is_empty() {
        println!(
            "authorized appids: {}",
            config
                .uids
                .iter()
                .map(u32::to_string)
                .collect::<Vec<_>>()
                .join(", ")
        );
    }
    Ok(())
}

/// `ksud dynamic del <size> <hash>`
///
/// Removes the signature only. The appid keeps its authorization, so
/// re-registering the same certificate restores the previous managers instead of
/// silently re-authorizing them.
pub fn del(size_arg: &str, hash_arg: &str) -> Result<()> {
    let target = parse_sign(size_arg, hash_arg)?;
    mutate_config(|config| {
        let before = config.signs.len();
        config.signs.retain(|sign| !sign.same_cert(&target));
        if config.signs.len() == before {
            fail!(ErrorCode::ConfigInvalid, "signature not found");
        }
        Ok(true)
    })?;
    Ok(())
}

/// `ksud dynamic clear`
pub fn clear() -> Result<()> {
    mutate_config(|config| {
        config.signs.clear();
        config.uids.clear();
        config.uids_present = true;
        Ok(true)
    })?;
    println!("cleared signatures and authorized appids");
    Ok(())
}

/// Appids the kernel currently honours, and how many apps it has tracked at all.
///
/// A zero tracked count means no scan has produced anything yet, which is the
/// difference between "the user authorized nobody" and "not known yet".
fn kernel_trusted_appids() -> Result<(Vec<u32>, usize)> {
    let apps = ksucalls::get_dynamic_managers()?;
    let mut trusted = Vec::new();
    for app in &apps {
        if app.flags & ksu_uapi::KSU_DYNAMIC_MANAGER_FLAG_TRUSTED != 0
            && !trusted.contains(&app.appid)
        {
            trusted.push(app.appid);
        }
    }
    Ok((trusted, apps.len()))
}

/// Adopt the set the kernel already honours as the initial authorization list.
///
/// A configuration written before the list existed authorized every app its
/// signatures matched, so seeding with exactly that set is what keeps the user's
/// own dynamic managers working across the upgrade. Runs once: afterwards the
/// file carries `uids` and the list is simply replayed.
fn seed_grants(config: &mut Config) -> Result<bool> {
    let (mut uids, tracked) = kernel_trusted_appids()?;
    if !config.signs.is_empty() && tracked == 0 {
        /*
         * Nothing has been scored yet, so an empty set here would mean
         * "authorize nobody" because of a timing artifact rather than because
         * the user asked for it. Stay on the legacy rule and retry later.
         */
        warn!(
            "dynamic manager: no tracked app to adopt yet, keeping the legacy rule for this boot"
        );
        return Ok(false);
    }

    uids.sort_unstable();
    uids.dedup();
    info!(
        "dynamic manager: adopted {} authorized appid(s)",
        uids.len()
    );
    config.uids = uids;
    config.uids_present = true;
    save_config(config)?;
    apply_grants(&config.uids)?;
    Ok(true)
}

/// Boot-time replay: load the persisted configuration and restore kernel state.
///
/// The kernel table is volatile, so this runs on every boot. It is idempotent
/// and callers are expected to log-and-continue on error.
pub fn load_and_apply() -> Result<()> {
    // The replay rewrites the file when it seeds a legacy configuration, so it has
    // to hold the same lock a CLI mutation does.
    let _lock = ConfigLock::acquire()?;
    let mut config = load_config()?;
    apply_signs(&config.signs)?;
    info!(
        "dynamic manager: applied {} signature(s)",
        config.signs.len()
    );

    if !grants_supported()? {
        info!("dynamic manager: kernel has no authorized-appid list, keeping the legacy rule");
        return Ok(());
    }

    if config.uids_present {
        apply_grants(&config.uids)?;
        info!(
            "dynamic manager: applied {} authorized appid(s)",
            config.uids.len()
        );
        return Ok(());
    }

    seed_grants(&mut config)?;
    Ok(())
}

/// Second chance for the migration, from the boot completed event.
///
/// `load_and_apply()` may have found nothing to adopt at post-fs-data; by now the
/// package scan has certainly run, so the seed is either correct or the
/// configuration genuinely matches no installed app.
pub fn finish_migration() -> Result<()> {
    let mut config = load_config()?;
    if config.uids_present || !grants_supported()? {
        return Ok(());
    }
    if seed_grants(&mut config)? {
        info!("dynamic manager: authorization migration finished at boot completed");
    }
    Ok(())
}
