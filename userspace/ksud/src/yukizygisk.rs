//! YukiZygisk: the kernel-assisted Zygisk engine this build ships.
//!
//! The engine is two payloads - a `zygiskd` daemon per ABI and the injected libraries - built by
//! CI and embedded in ksud. This module owns everything ksud has to do with them: install the
//! payload under `/data/adb/ksu/lib/yukizygisk`, start the daemons at post-fs-data, answer the
//! manager's status query, and read/write the settings document the daemon reads at startup.
//!
//! Injection itself happens in the kernel (`feature/yukizygisk`); unlike the userspace engine this
//! replaced, nothing here injects anything. The daemon only has to be alive with a driver fd, which
//! it obtains on its own.

use anyhow::{Context, Result, bail, ensure};
use serde_json::{Map, Value, json};
use std::fs;
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

/// The injected libraries. The engine derives these paths from this directory, so it is not a free
/// choice.
pub const LIB_DIR: &str = "/data/adb/ksu/lib/yukizygisk";
/// The daemons sit next to ksud's other helpers: `/data/adb/ksu/bin/zygiskd64`, as the engine's own
/// path constants expect.
pub const DAEMON_DIR: &str = "/data/adb/ksu/bin";
/// Runtime state: settings, catalogs, diagnostics.
pub const STATE_DIR: &str = "/data/adb/ksu/yukizygisk";
/// The settings document the daemon reads when it starts.
pub const CONFIG: &str = "/data/adb/ksu/yukizygisk/yzconfig.json";
/// Present when the user turned the engine off. Absence means on: the engine ships enabled, so a
/// fresh install injects without anyone having to find the switch first.
const DISABLED_MARKER: &str = "/data/adb/ksu/yukizygisk/disabled";

/// Where a module installer looks to answer "is Zygisk available?".
///
/// PlayIntegrityFix and others test that with `find /data/adb/modules -name libzygisk.so`, and this
/// engine keeps its libraries outside the module directory - so the lookup finds nothing and the
/// installer refuses to run. The directory carries neither `module.prop` nor `zygisk/<abi>.so`, so
/// nothing takes it for a module and zygiskd never loads it: it answers that one question.
pub const DETECT_DIR: &str = "/data/adb/modules/yukizygisk";
/// Inside [DETECT_DIR]; the name the lookup above matches.
pub const DETECT_LINK: &str = "/data/adb/modules/yukizygisk/libzygisk.so";

/// The daemons, one per ABI.
pub const DAEMONS: [&str; 2] = ["zygiskd64", "zygiskd32"];
/// The six injected libraries.
pub const LIBRARIES: [&str; 6] = [
    "libzygisk64.so",
    "libzygisk32.so",
    "libyukilinker64.so",
    "libyukilinker32.so",
    "libyukizncore64.so",
    "libyukizncore32.so",
];

/// Where a payload file goes: daemons next to ksud's helpers, libraries under `lib/`.
pub fn target_path(name: &str) -> PathBuf {
    if DAEMONS.contains(&name) {
        Path::new(DAEMON_DIR).join(name)
    } else {
        Path::new(LIB_DIR).join(name)
    }
}

/// Every setting the daemon understands, with the value it falls back to when the document does not
/// carry the key. Mirrors `yukizygisk::settings::defaults()` in the engine.
const BOOL_KEYS: [(&str, bool); 5] = [
    ("yukilinker", true),
    ("anonymous_memory", true),
    ("early_load", false),
    ("dmesg_log", false),
    ("crash_protection", true),
];
const DENYLIST_KEY: &str = "denylist_mode";

fn defaults() -> Map<String, Value> {
    let mut map = Map::new();
    for (key, value) in BOOL_KEYS {
        map.insert(key.to_string(), Value::Bool(value));
    }
    map.insert(DENYLIST_KEY.to_string(), Value::from(0));
    map
}

/// Install the embedded payload, replacing only files whose content changed.
///
/// Called on every boot: a ksud update brings new payloads, and a partially written install has to
/// heal itself.
pub fn install() -> Result<()> {
    fs::create_dir_all(LIB_DIR).with_context(|| format!("create {LIB_DIR}"))?;
    fs::create_dir_all(DAEMON_DIR).with_context(|| format!("create {DAEMON_DIR}"))?;
    fs::create_dir_all(STATE_DIR).with_context(|| format!("create {STATE_DIR}"))?;
    fs::set_permissions(LIB_DIR, fs::Permissions::from_mode(0o755))?;
    fs::set_permissions(STATE_DIR, fs::Permissions::from_mode(0o700))?;
    // The directory needs the label as much as the files do: a zygote that cannot search it never
    // reaches the payload inside.
    if let Err(error) = crate::restorecon::lsetfilecon(LIB_DIR, crate::restorecon::KSU_CON) {
        log::warn!("yukizygisk: could not label {LIB_DIR}: {error:#}");
    }

    for name in DAEMONS.into_iter().chain(LIBRARIES) {
        let asset = crate::assets::embedded(&format!("yukizygisk/{name}"))
            .with_context(|| format!("embedded payload {name} is missing"))?;
        let target = target_path(name);
        // Size first. The compare below reads the library in full, and on a boot where the payload
        // did not change that read is the only work this function does - several megabytes of it.
        let unchanged = fs::metadata(&target)
            .is_ok_and(|meta| meta.len() == asset.data.len() as u64)
            && fs::read(&target).is_ok_and(|on_disk| on_disk.as_slice() == asset.data.as_ref());
        if unchanged {
            continue;
        }
        // A daemon that is still running from the old file keeps its inode; the replacement lands
        // next to it and is picked up by the next start.
        let temp = target.with_extension("new");
        fs::write(&temp, &asset.data).with_context(|| format!("write {}", temp.display()))?;
        fs::set_permissions(&temp, fs::Permissions::from_mode(0o755))?;
        // The zygote reads these files; under `adb_data_file` that is denied. The ksu_file label is
        // the one the injection and its mount namespace are allowed to reach.
        if let Err(error) = crate::restorecon::lsetfilecon(&temp, crate::restorecon::KSU_CON) {
            log::warn!("yukizygisk: could not label {}: {error:#}", temp.display());
        }
        fs::rename(&temp, &target).with_context(|| format!("install {}", target.display()))?;
        log::info!("yukizygisk: installed {name}");
    }
    if let Err(error) = write_detect_link() {
        log::warn!("yukizygisk: could not publish the Zygisk marker: {error:#}");
    }
    Ok(())
}

/// Put a `libzygisk.so` where module installers look for one.
///
/// Only a link, and only the 64-bit library: it exists so a module script's existence check passes,
/// not so anything loads it.
fn write_detect_link() -> Result<()> {
    let target = target_path("libzygisk64.so");
    if !target.exists() {
        return Ok(());
    }
    fs::create_dir_all(DETECT_DIR).with_context(|| format!("create {DETECT_DIR}"))?;
    match fs::symlink_metadata(DETECT_LINK) {
        Ok(meta) if meta.file_type().is_symlink() => {
            if fs::read_link(DETECT_LINK).is_ok_and(|current| current == target) {
                return Ok(());
            }
            fs::remove_file(DETECT_LINK).with_context(|| format!("remove {DETECT_LINK}"))?;
        }
        Ok(_) => {
            fs::remove_file(DETECT_LINK).with_context(|| format!("remove {DETECT_LINK}"))?;
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).with_context(|| format!("stat {DETECT_LINK}")),
    }
    std::os::unix::fs::symlink(&target, DETECT_LINK)
        .with_context(|| format!("link {DETECT_LINK} -> {}", target.display()))?;
    Ok(())
}

/// Whether the payload is fully installed.
pub fn installed() -> bool {
    DAEMONS
        .into_iter()
        .chain(LIBRARIES)
        .all(|name| target_path(name).exists())
}

/// Whether the user left the engine on. On unless the marker exists.
pub fn enabled() -> bool {
    !Path::new(DISABLED_MARKER).exists()
}

/// Turn the engine on or off. Disabling stops the daemons and leaves them down at the next boot,
/// which is the escape hatch when a module makes the system unusable.
pub fn set_enabled(enable: bool) -> Result<()> {
    if enable {
        match fs::remove_file(DISABLED_MARKER) {
            Ok(()) => log::info!("yukizygisk: enabled"),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).context("remove the disable marker"),
        }
        install()?;
        // Daemon first: the kernel only accepts the enable bit once the injection runtime is up.
        start()?;
        set_kernel_enabled(true)?;
    } else {
        // Injection off before the daemon goes away, so nothing tries to inject into a zygote whose
        // daemon is gone.
        let _ = set_kernel_enabled(false);
        fs::create_dir_all(STATE_DIR).with_context(|| format!("create {STATE_DIR}"))?;
        fs::write(DISABLED_MARKER, b"1\n").context("write the disable marker")?;
        let _ = fs::set_permissions(DISABLED_MARKER, fs::Permissions::from_mode(0o600));
        stop();
        log::info!("yukizygisk: disabled");
    }
    Ok(())
}

/// Whether the loaded kernel module carries the injection runtime. A module built before this
/// feature existed answers "no", and that is the case worth naming: everything else in the page can
/// look healthy while nothing injects.
pub fn kernel_supported() -> bool {
    crate::feature::feature_supported(crate::feature_id::FeatureId::YukiZygisk).unwrap_or(false)
}

/// The kernel's own enable bit. Injection happens in the kernel, so this - not the marker file -
/// is what decides whether a module reaches zygote.
pub fn kernel_enabled() -> bool {
    crate::ksucalls::get_feature(crate::feature_id::FeatureId::YukiZygisk as u32)
        .is_ok_and(|state| state.supported && state.value != 0)
}

fn set_kernel_enabled(enable: bool) -> Result<()> {
    if !kernel_supported() {
        bail!("the loaded kernel module does not carry the YukiZygisk runtime");
    }
    crate::ksucalls::set_feature(
        crate::feature_id::FeatureId::YukiZygisk as u32,
        u64::from(enable),
    )
    .context("set the YukiZygisk feature bit")
}

/// `diagnostics/current` is the generation the daemon logs into. It refuses to write unless the
/// directory carries the current boot id, so without this the engine has nowhere to explain itself.
fn prepare_diagnostics() -> Result<()> {
    let root = Path::new(STATE_DIR).join("diagnostics");
    let current = root.join("current");
    let previous = root.join("old");
    fs::create_dir_all(&root).with_context(|| format!("create {}", root.display()))?;
    restrict(&root, 0o700)?;

    let boot_id = fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .context("read the boot id")?
        .trim()
        .to_string();
    let stored = fs::read_to_string(current.join("boot_id")).ok();
    if stored.as_deref().map(str::trim) == Some(boot_id.as_str()) {
        let logs = current.join("logs");
        fs::create_dir_all(&logs).with_context(|| format!("create {}", logs.display()))?;
        restrict(&logs, 0o700)?;
        return Ok(());
    }

    // A different boot: the old generation becomes `old`, which is where the daemon looks for the
    // previous boot's evidence.
    let _ = fs::remove_dir_all(&previous);
    if current.exists() {
        let _ = fs::rename(&current, &previous);
    }
    let logs = current.join("logs");
    fs::create_dir_all(&logs).with_context(|| format!("create {}", logs.display()))?;
    restrict(&current, 0o700)?;
    restrict(&logs, 0o700)?;
    let file = current.join("boot_id");
    fs::write(&file, format!("{boot_id}\n"))
        .with_context(|| format!("write {}", file.display()))?;
    restrict(&file, 0o600)?;
    Ok(())
}

fn restrict(path: &Path, mode: u32) -> Result<()> {
    fs::set_permissions(path, fs::Permissions::from_mode(mode))
        .with_context(|| format!("chmod {mode:o} {}", path.display()))
}

/// The outcome of the last start, so the page can explain a daemon that never came up even when it
/// died before it could write a log line.
fn start_record_path() -> PathBuf {
    Path::new(STATE_DIR).join("last-start.json")
}

fn record_start(ok: bool, detail: &str) {
    let _ = fs::create_dir_all(STATE_DIR);
    let document = json!({ "ok": ok, "detail": detail });
    let _ = fs::write(start_record_path(), format!("{document}\n"));
}

fn last_start_failure() -> Option<String> {
    let text = fs::read_to_string(start_record_path()).ok()?;
    let record: Value = serde_json::from_str(&text).ok()?;
    if record.get("ok").and_then(Value::as_bool) == Some(true) {
        return None;
    }
    record
        .get("detail")
        .and_then(Value::as_str)
        .map(|detail| detail.chars().take(200).collect())
}

/// The settings document, with the engine's defaults filled in for anything it does not carry.
pub fn config() -> Result<Value> {
    let mut merged = defaults();
    match fs::read(CONFIG) {
        Ok(bytes) => {
            let saved: Value = serde_json::from_slice(&bytes).context("parse yzconfig.json")?;
            if let Value::Object(object) = saved {
                // Unknown keys are preserved: the engine may know settings this build does not.
                for (key, value) in object {
                    merged.insert(key, value);
                }
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).context("read yzconfig.json"),
    }
    Ok(Value::Object(merged))
}

/// Write one setting. The daemon reads the document once at startup, so a change takes effect on the
/// next restart, which the caller has to request.
pub fn set(key: &str, value: &str) -> Result<()> {
    let new_value = if key == DENYLIST_KEY {
        let mode: u32 = value.parse().context("denylist_mode must be 0, 1 or 2")?;
        ensure!(mode <= 2, "denylist_mode must be 0, 1 or 2");
        Value::from(mode)
    } else if BOOL_KEYS.iter().any(|(name, _)| *name == key) {
        match value {
            "true" | "1" => Value::Bool(true),
            "false" | "0" => Value::Bool(false),
            _ => bail!("{key} must be true or false"),
        }
    } else {
        bail!("unknown setting {key}");
    };

    let mut document = config()?;
    if let Some(object) = document.as_object_mut() {
        object.insert(key.to_string(), new_value);
    }
    write_config(&document)
}

fn write_config(document: &Value) -> Result<()> {
    fs::create_dir_all(STATE_DIR).with_context(|| format!("create {STATE_DIR}"))?;
    // The daemon also writes this file when it starts; the same lock file keeps a settings change
    // from racing a startup write.
    let lock_path = format!("{CONFIG}.lock");
    let lock = fs::OpenOptions::new()
        .create(true)
        .truncate(false)
        .write(true)
        .open(&lock_path)
        .with_context(|| format!("open {lock_path}"))?;
    // SAFETY: plain flock on a file this function owns.
    if unsafe { libc::flock(std::os::unix::io::AsRawFd::as_raw_fd(&lock), libc::LOCK_EX) } != 0 {
        return Err(std::io::Error::last_os_error()).context("lock yzconfig.json");
    }
    let temp = Path::new(STATE_DIR).join("yzconfig.json.new");
    let mut file = fs::File::create(&temp).context("create yzconfig.json.new")?;
    serde_json::to_writer_pretty(&mut file, document)?;
    file.write_all(b"\n")?;
    file.sync_all()?;
    fs::set_permissions(&temp, fs::Permissions::from_mode(0o600))?;
    fs::rename(&temp, CONFIG).context("replace yzconfig.json")?;
    Ok(())
}

/// The lifetime lock a daemon holds for its ABI. It takes this in its first statements and keeps
/// it open until it exits, so "who holds this file" is "which daemon is alive" - the command name
/// is not usable for that, because every per-module companion shares it.
fn daemon_lock(abi: u32) -> PathBuf {
    Path::new(STATE_DIR).join("recovery").join(match abi {
        32 => "daemon32.lock",
        _ => "daemon64.lock",
    })
}

fn run_dir() -> PathBuf {
    Path::new(STATE_DIR).join("run")
}

fn pid_file(abi: u32) -> PathBuf {
    run_dir().join(match abi {
        32 => "daemon32.pid",
        _ => "daemon64.pid",
    })
}

/// Remember the pid the daemon reported, so the page can name it without guessing.
fn write_pid_file(abi: u32, pid: i32) {
    let _ = fs::create_dir_all(run_dir());
    let _ = fs::set_permissions(run_dir(), fs::Permissions::from_mode(0o700));
    let _ = fs::write(pid_file(abi), format!("{pid}\n"));
}

fn clear_pid_file(abi: u32) {
    let _ = fs::remove_file(pid_file(abi));
}

const fn expected_name(abi: u32) -> &'static str {
    if abi == 32 { "zygiskd32" } else { "zygiskd64" }
}

/// Whether `pid` is still the daemon for `abi`: same executable name, so a recycled pid is not
/// mistaken for it.
fn is_daemon(abi: u32, pid: i32) -> bool {
    let Ok(cmdline) = fs::read(format!("/proc/{pid}/cmdline")) else {
        return false;
    };
    let executable = cmdline.split(|byte| *byte == 0).next().unwrap_or(&[]);
    let executable = String::from_utf8_lossy(executable);
    executable.rsplit('/').next().unwrap_or("") == expected_name(abi)
}

fn parent_of(pid: i32) -> Option<i32> {
    let stat = fs::read_to_string(format!("/proc/{pid}/stat")).ok()?;
    // `comm` may contain spaces and parentheses, so the fields after the last ')' are the ones to
    // read: state, then ppid.
    let rest = stat.rsplit_once(')')?.1;
    let mut fields = rest.split_whitespace();
    fields.next()?;
    fields.next()?.parse().ok()
}

/// Every process that has the daemon's lifetime lock open. That is the daemon plus the companions
/// it forked, which inherit the descriptor.
fn lock_holders(abi: u32) -> Vec<i32> {
    use std::os::unix::fs::MetadataExt;

    let Ok(target) = fs::metadata(daemon_lock(abi)) else {
        return Vec::new();
    };
    let (device, inode) = (target.dev(), target.ino());
    let mut holders = Vec::new();
    let Ok(entries) = fs::read_dir("/proc") else {
        return holders;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(pid) = name.to_str().and_then(|value| value.parse::<i32>().ok()) else {
            continue;
        };
        if pid <= 1 {
            continue;
        }
        let Ok(fds) = fs::read_dir(entry.path().join("fd")) else {
            continue;
        };
        for fd in fds.flatten() {
            let Ok(meta) = fs::metadata(fd.path()) else {
                continue;
            };
            if meta.dev() == device && meta.ino() == inode {
                holders.push(pid);
                break;
            }
        }
    }
    holders
}

/// The daemon among the lock holders: the companions are its children, so it is the one no other
/// holder is the parent of.
fn scan_daemon(abi: u32) -> Option<i32> {
    let holders = lock_holders(abi);
    if holders.is_empty() {
        return None;
    }
    holders
        .iter()
        .copied()
        .find(|pid| parent_of(*pid).is_none_or(|parent| !holders.contains(&parent)))
        .or_else(|| holders.iter().copied().min())
}

/// The daemon for `abi`, if one is alive.
fn daemon_pid(abi: u32) -> Option<i32> {
    let recorded = fs::read_to_string(pid_file(abi))
        .ok()
        .and_then(|text| text.trim().parse::<i32>().ok())
        .filter(|pid| is_daemon(abi, *pid));
    recorded.or_else(|| scan_daemon(abi))
}

/// Live daemons, as `(abi, pid)`: at most one per ABI.
pub fn running() -> Vec<(u32, i32)> {
    let mut found = Vec::new();
    for abi in [64u32, 32] {
        if let Some(pid) = daemon_pid(abi) {
            found.push((abi, pid));
        }
    }
    found
}

/// Every process running the daemon binary: the daemon itself plus the per-module companions it
/// forked. Used to shut the engine down completely - the companions would otherwise outlive it.
fn daemon_processes() -> Vec<i32> {
    let mut found = Vec::new();
    let Ok(entries) = fs::read_dir("/proc") else {
        return found;
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(pid) = name.to_str().and_then(|value| value.parse::<i32>().ok()) else {
            continue;
        };
        let Ok(cmdline) = fs::read(entry.path().join("cmdline")) else {
            continue;
        };
        if cmdline.is_empty() {
            continue;
        }
        let executable = cmdline.split(|byte| *byte == 0).next().unwrap_or(&[]);
        let executable = String::from_utf8_lossy(executable);
        let executable = executable.rsplit('/').next().unwrap_or("");
        if executable == "zygiskd64" || executable == "zygiskd32" {
            found.push(pid);
        }
    }
    found
}

/// Start a daemon for each installed ABI. Already-running daemons are left alone.
///
/// Every daemon is asked to report readiness before this returns, so "the engine is up" means the
/// endpoint is registered, not that a process was forked a moment ago.
pub fn start() -> Result<()> {
    if !installed() {
        bail!("yukizygisk payload is not installed");
    }
    if !kernel_supported() {
        bail!("the loaded kernel module does not carry the YukiZygisk runtime");
    }
    // The daemon scans /data/adb/modules and hands the injected libraries to zygote, so it has to
    // see init's mount namespace; a manager-initiated restart would otherwise run it inside the
    // app's view of the filesystem.
    if let Err(error) = crate::utils::switch_mnt_ns(1) {
        log::warn!("yukizygisk: could not enter the init mount namespace: {error:#}");
    }
    if let Err(error) = prepare_diagnostics() {
        log::warn!("yukizygisk: could not prepare the diagnostics directory: {error:#}");
    }

    let live = running();
    let mut failures = Vec::new();
    for (abi, name) in [(64, "zygiskd64"), (32, "zygiskd32")] {
        // Only an ABI whose own file exists is expected to run: a 64-bit-only build has no 32-bit
        // daemon, and a device with 32-bit support needs both.
        let path = target_path(name);
        if !path.exists() {
            continue;
        }
        if live.iter().any(|(running_abi, _)| *running_abi == abi) {
            continue;
        }
        match spawn_daemon(&path) {
            Ok(pid) => {
                log::info!("yukizygisk: {name} ready as pid {pid}");
                write_pid_file(abi, pid);
            }
            Err(error) => {
                // A second instance loses the lifetime lock and exits; if the ABI ended up with a
                // live daemon anyway, that is the outcome we wanted.
                if running().iter().any(|(running_abi, _)| *running_abi == abi) {
                    log::info!("yukizygisk: {name} was already starting up");
                } else {
                    log::warn!("yukizygisk: {name} failed to start: {error:#}");
                    failures.push(format!("{name}: {error:#}"));
                }
            }
        }
    }

    let alive = running();
    if alive.is_empty() {
        let detail = if failures.is_empty() {
            "no daemon is running".to_string()
        } else {
            failures.join("; ")
        };
        record_start(false, &detail);
        bail!("{detail}");
    }
    let count = alive.len();
    record_start(true, &format!("{count} daemon(s) running"));
    Ok(())
}

/// How long a daemon may take to report that its endpoint is registered.
const READY_TIMEOUT: Duration = Duration::from_secs(15);

fn spawn_daemon(path: &Path) -> Result<i32> {
    use std::os::fd::{AsRawFd, FromRawFd};
    use std::os::unix::process::CommandExt;
    use std::process::{Command, Stdio};

    // The daemon writes one byte here once it is serving; without it "started" only means a fork
    // happened, which is exactly the case that used to look healthy while nothing injected.
    let mut pipe = [0; 2];
    // SAFETY: pipe2 fills both slots on success and reports failure otherwise.
    if unsafe { libc::pipe2(pipe.as_mut_ptr(), libc::O_CLOEXEC) } != 0 {
        return Err(std::io::Error::last_os_error()).context("create the readiness pipe");
    }
    // SAFETY: both descriptors were just created by pipe2 and are owned here.
    let (mut read_end, write_end) = unsafe {
        (
            std::fs::File::from_raw_fd(pipe[0]),
            std::fs::File::from_raw_fd(pipe[1]),
        )
    };
    let write_fd = write_end.as_raw_fd();

    let mut command = Command::new(path);
    command
        .env("YUKIZYGISK_READY_FD", write_fd.to_string())
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    // SAFETY: only async-signal-safe calls between fork and exec. A fresh session keeps the daemon
    // out of any terminal's process group; the cgroup move is best effort - failing it must not
    // stop the daemon from starting, which is what a fatal error here used to do.
    unsafe {
        command.pre_exec(move || {
            libc::setsid();
            libc::setpgid(0, 0);
            let _ = detach_from_service_cgroup();
            // The readiness pipe has to survive exec, so its close-on-exec flag goes away.
            libc::fcntl(write_fd, libc::F_SETFD, 0);
            Ok(())
        });
    }

    let mut child = command
        .spawn()
        .with_context(|| format!("spawn {}", path.display()))?;
    // The child owns the write end now; keeping ours open would hide its exit from the reader.
    drop(write_end);

    let pid = child.id() as i32;
    match wait_ready(&mut read_end) {
        Ok(Startup::Up) => Ok(pid),
        Ok(Startup::RebootRequired) => {
            let _ = child.kill();
            let _ = child.wait();
            bail!("the daemon asked for a reboot before it can inject")
        }
        Ok(Startup::Failed) => {
            let _ = child.kill();
            let status = child.wait().ok();
            bail!(
                "the daemon exited during startup ({})",
                status.map_or_else(|| "no exit status".to_string(), |status| status.to_string())
            )
        }
        Err(error) => {
            let _ = child.kill();
            let _ = child.wait();
            Err(error).context("the daemon did not report ready")
        }
    }
}

enum Startup {
    Up,
    Failed,
    RebootRequired,
}

/// Wait for the daemon's readiness byte. The daemon writes '1' when serving, '0' when it gave up
/// and 'R' when it needs a reboot; end-of-file means it exited without saying anything.
fn wait_ready(read_end: &mut std::fs::File) -> Result<Startup> {
    use std::os::fd::AsRawFd;

    let deadline = Instant::now() + READY_TIMEOUT;
    loop {
        let remaining = deadline.saturating_duration_since(Instant::now());
        if remaining.is_zero() {
            bail!("no readiness report within {READY_TIMEOUT:?}");
        }
        let mut poll = libc::pollfd {
            fd: read_end.as_raw_fd(),
            events: libc::POLLIN,
            revents: 0,
        };
        // SAFETY: one descriptor this function owns, for the timeout it computed.
        let ready = unsafe { libc::poll(&raw mut poll, 1, remaining.as_millis() as libc::c_int) };
        if ready < 0 {
            let error = std::io::Error::last_os_error();
            if error.kind() == std::io::ErrorKind::Interrupted {
                continue;
            }
            return Err(error).context("wait for the readiness report");
        }
        if ready == 0 {
            continue;
        }
        let mut byte = 0u8;
        // SAFETY: one byte into a local buffer.
        let got = unsafe {
            libc::read(
                read_end.as_raw_fd(),
                (&raw mut byte).cast(),
                std::mem::size_of::<u8>(),
            )
        };
        return Ok(match got {
            1 => match byte {
                b'1' => Startup::Up,
                b'R' => Startup::RebootRequired,
                _ => Startup::Failed,
            },
            // End of file: the process is gone, so no readiness byte is coming.
            0 => Startup::Failed,
            _ => {
                let error = std::io::Error::last_os_error();
                if error.kind() == std::io::ErrorKind::Interrupted {
                    continue;
                }
                return Err(error).context("read the readiness report");
            }
        });
    }
}

/// Stop every daemon. Used by soft reboot and by an explicit restart.
///
/// The daemons are signalled first so they can take their companions with them; whatever is still
/// running the binary afterwards is killed outright, because a companion left behind keeps a module
/// loaded in a process that no longer has a daemon to talk to.
pub fn stop() {
    let daemons = running();
    let all = daemon_processes();
    if all.is_empty() {
        return;
    }
    for (_, pid) in &daemons {
        // SAFETY: plain signal delivery to a pid we just discovered.
        unsafe {
            libc::kill(*pid, libc::SIGTERM);
        }
    }
    let deadline = Instant::now() + Duration::from_secs(2);
    while Instant::now() < deadline && !running().is_empty() {
        std::thread::sleep(Duration::from_millis(50));
    }
    for pid in daemon_processes() {
        // SAFETY: same as above; this one ignored SIGTERM for two seconds.
        unsafe {
            libc::kill(pid, libc::SIGKILL);
        }
    }
    let deadline = Instant::now() + Duration::from_secs(1);
    while Instant::now() < deadline && !daemon_processes().is_empty() {
        std::thread::sleep(Duration::from_millis(50));
    }
    for abi in [64u32, 32] {
        clear_pid_file(abi);
    }
}

/// post-fs-data: install the payload and bring the daemons up.
///
/// Safe mode and Magisk both mean "do not inject", which is decided before any module code runs.
pub fn post_fs_data(safe_mode: bool) -> Result<()> {
    if safe_mode || crate::utils::is_safe_mode() || crate::utils::has_magisk() {
        return Ok(());
    }
    if !enabled() {
        log::info!("yukizygisk: disabled by the user, not starting");
        return Ok(());
    }
    install()?;
    start()?;
    if let Err(error) = set_kernel_enabled(true) {
        log::warn!("yukizygisk: could not enable injection in the kernel: {error:#}");
    }
    Ok(())
}

/// `on services`: the daemons are started at post-fs-data, but a daemon that died in between has to
/// be brought back before any app is launched, otherwise the next zygote fork injects nothing.
pub fn ensure_running() -> Result<()> {
    if !enabled() || !installed() {
        return Ok(());
    }
    let before = running().len();
    start()?;
    if running().len() > before {
        log::warn!("yukizygisk: restarted a daemon that had died before zygote started");
    }
    if let Err(error) = set_kernel_enabled(true) {
        log::warn!("yukizygisk: could not enable injection in the kernel: {error:#}");
    }
    Ok(())
}

/// Soft reboot tears down userspace while the kernel module stays loaded: the daemons have to go so
/// the new cycle starts them against the new zygote.
pub fn prepare_soft_reboot() {
    stop();
}

/// The last line the daemon wrote, for the page to show when nothing is running.
fn last_error() -> Option<String> {
    let log = format!("{STATE_DIR}/diagnostics/current/logs/zygiskd64.log");
    let text = fs::read_to_string(&log).ok()?;
    // The daemon prefixes every line with a timestamp and level; the last non-empty one is the
    // reason it stopped, if it said anything at all.
    let line = text.lines().rev().find(|line| !line.trim().is_empty())?;
    Some(line.chars().take(200).collect())
}

/// Everything the manager needs to render the page, in one document.
pub fn status() -> Result<Value> {
    let config = config()?;
    let live = running();
    Ok(json!({
        "installed": installed(),
        // A build without a payload for this architecture (x86_64, riscv64) is not broken -
        // YukiZygisk is arm-only - and the page says that instead of "not installed".
        "supported": crate::assets::embedded(&format!("yukizygisk/{}", DAEMONS[0])).is_some(),
        "configPath": CONFIG,
        "daemons": live
            .iter()
            .map(|(abi, pid)| json!({ "abi": abi, "pid": pid }))
            .collect::<Vec<_>>(),
        "enabled": enabled(),
        // A module built without the feature is the one failure the page cannot show by looking at
        // pids: everything is installed, nothing runs, and the reason lives in the kernel.
        "kernelSupported": kernel_supported(),
        "featureEnabled": kernel_enabled(),
        "lastError": last_error().or_else(last_start_failure),
        "config": config,
    }))
}

#[derive(Debug, Clone, clap::Subcommand)]
pub enum Command {
    /// Print the engine status as JSON
    Status,
    /// Change one setting (yukilinker, anonymous_memory, early_load, dmesg_log,
    /// crash_protection, denylist_mode)
    Set {
        key: String,
        value: String,
        /// Restart the daemons so the change takes effect now
        #[arg(long)]
        restart: bool,
    },
    /// Restart the daemons
    Restart,
    /// Turn the engine on and start it
    Enable,
    /// Turn the engine off and stop it (nothing injects until it is enabled again)
    Disable,
}

pub fn run(command: &Command) -> Result<()> {
    match command {
        Command::Status => println!("{}", serde_json::to_string_pretty(&status()?)?),
        Command::Set {
            key,
            value,
            restart,
        } => {
            set(key, value)?;
            if *restart {
                stop();
                start()?;
                if let Err(error) = set_kernel_enabled(true) {
                    log::warn!("yukizygisk: could not re-enable injection: {error:#}");
                }
            }
        }
        Command::Restart => {
            stop();
            start()?;
            if let Err(error) = set_kernel_enabled(true) {
                log::warn!("yukizygisk: could not re-enable injection: {error:#}");
            }
        }
        Command::Enable => set_enabled(true)?,
        Command::Disable => set_enabled(false)?,
    }
    Ok(())
}

/// `setsid` gives the daemon its own session; this additionally moves it out of the cgroup init put
/// the exec service in, so it survives the service being killed.
///
/// Called after fork: only stack operations and async-signal-safe libc calls.
pub fn detach_from_service_cgroup() -> std::io::Result<()> {
    unsafe {
        let fd = libc::open(
            c"/sys/fs/cgroup/cgroup.procs".as_ptr(),
            libc::O_WRONLY | libc::O_CLOEXEC,
        );
        if fd == -1 {
            return Err(std::io::Error::last_os_error());
        }
        let mut pid = libc::getpid() as u32;
        let mut bytes = [0u8; 10];
        let mut offset = bytes.len();
        while pid > 0 {
            offset -= 1;
            bytes[offset] = b'0' + (pid % 10) as u8;
            pid /= 10;
        }
        let size = bytes.len() - offset;
        let written = libc::write(fd, bytes[offset..].as_ptr().cast(), size);
        let error = if written == -1 {
            Some(std::io::Error::last_os_error())
        } else if written as usize != size {
            Some(std::io::Error::from_raw_os_error(libc::EIO))
        } else {
            None
        };
        libc::close(fd);
        error.map_or(Ok(()), Err)
    }
}
