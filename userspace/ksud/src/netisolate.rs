use crate::ksu_uapi;
use crate::{ksucalls, utils};
use anyhow::{Context, Error, Result, bail};
use log::{info, warn};
use serde::{Deserialize, Serialize};
use std::fs::{File, OpenOptions};
use std::io::{Read, Write};
use std::os::fd::AsRawFd;
use std::os::unix::fs::{DirBuilderExt, MetadataExt, OpenOptionsExt, PermissionsExt};

const CONFIG_PATH: &str = "/data/adb/ksu/netisolate.json";
const LOCK_PATH: &str = "/data/adb/ksu/.netisolate.lock";
const WORKING_DIR: &str = "/data/adb/ksu";
const MAX_INPUT_BYTES: usize = 64 * 1024;
const FLAG_MASK: u32 = ksu_uapi::KSU_NETISOLATE_FLAG_MASK;
const SUPPORTED_SHIFT: u32 = ksu_uapi::KSU_NETISOLATE_SUPPORTED_SHIFT;
const STRICT_SOCKET_FLAG: u32 = ksu_uapi::KSU_NETISOLATE_FLAG_STRICT_SOCKET;

#[derive(Debug, Clone, clap::Subcommand)]
pub enum Command {
    /// Read network isolation state
    Status,
    /// Validate and apply a JSON configuration from stdin
    Set,
    /// Enable the persisted configuration
    Enable,
    /// Disable isolation while retaining the UID list
    Disable,
    /// Disable isolation and remove persisted state
    Clear,
    /// Reapply the persisted configuration
    Apply,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct ConfigFile {
    enabled: bool,
    uids: Vec<u32>,
    #[serde(default)]
    strict_socket: bool,
}

struct ConfigLock(File);

impl Drop for ConfigLock {
    fn drop(&mut self) {
        unsafe {
            libc::flock(self.0.as_raw_fd(), libc::LOCK_UN);
        }
    }
}

fn ensure_root() -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("Root is required for network isolation management");
    }
    Ok(())
}

fn ensure_working_dir() -> Result<()> {
    match std::fs::symlink_metadata(WORKING_DIR) {
        Ok(metadata) => {
            if !metadata.is_dir() || metadata.uid() != 0 || metadata.mode() & 0o022 != 0 {
                bail!("Unsafe network isolation working directory");
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            std::fs::DirBuilder::new()
                .mode(0o755)
                .create(WORKING_DIR)
                .context("Create network isolation working directory")?;
        }
        Err(error) => return Err(error).context("Inspect network isolation working directory"),
    }
    Ok(())
}

fn acquire_lock() -> Result<ConfigLock> {
    ensure_working_dir()?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(LOCK_PATH)
        .context("Open network isolation lock")?;
    let metadata = file.metadata().context("Inspect network isolation lock")?;
    if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        bail!("Unsafe network isolation lock");
    }
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        bail!("Network isolation is busy; retry later");
    }
    Ok(ConfigLock(file))
}

fn normalize(mut config: ConfigFile) -> Result<ConfigFile> {
    config.uids.sort_unstable();
    config.uids.dedup();
    if config.uids.len() > ksu_uapi::KSU_NETISOLATE_MAX_UIDS as usize {
        bail!(
            "Network isolation supports at most {} UIDs",
            ksu_uapi::KSU_NETISOLATE_MAX_UIDS
        );
    }
    if config.uids.contains(&0) {
        bail!("UID 0 is permanently exempt and cannot be isolated");
    }
    if config.enabled && config.uids.is_empty() {
        bail!("Network isolation requires at least one UID when enabled");
    }
    Ok(config)
}

const fn requested_flags(config: &ConfigFile) -> u32 {
    if config.strict_socket {
        STRICT_SOCKET_FLAG
    } else {
        0
    }
}

const fn kernel_flags(config: &ksu_uapi::ksu_netisolate_config) -> (u32, u32) {
    (
        config.reserved & FLAG_MASK,
        (config.reserved >> SUPPORTED_SHIFT) & FLAG_MASK,
    )
}

const fn strict_socket_supported(config: &ksu_uapi::ksu_netisolate_config) -> bool {
    kernel_flags(config).1 & STRICT_SOCKET_FLAG != 0
}

const fn strict_socket_active(config: &ksu_uapi::ksu_netisolate_config) -> bool {
    kernel_flags(config).0 & STRICT_SOCKET_FLAG != 0
}

const fn normalize_snapshot(
    mut snapshot: ksu_uapi::ksu_netisolate_config,
) -> ksu_uapi::ksu_netisolate_config {
    snapshot.reserved &= FLAG_MASK;
    snapshot
}

fn ensure_strict_supported(
    config: &ConfigFile,
    kernel: Option<&ksu_uapi::ksu_netisolate_config>,
) -> Result<()> {
    if config.strict_socket && !kernel.is_some_and(strict_socket_supported) {
        bail!("Kernel does not support strict socket mode");
    }
    Ok(())
}

fn kernel_unsupported(error: &Error) -> bool {
    error.chain().any(|cause| {
        cause
            .downcast_ref::<std::io::Error>()
            .and_then(std::io::Error::raw_os_error)
            .is_some_and(|errno| errno == libc::EOPNOTSUPP || errno == libc::ENOTTY)
    })
}

fn read_limited<R: Read>(reader: R, limit: usize, label: &str) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    reader
        .take((limit + 1) as u64)
        .read_to_end(&mut bytes)
        .with_context(|| format!("Read {label}"))?;
    if bytes.len() > limit {
        bail!("{label} is too large");
    }
    Ok(bytes)
}

fn read_config() -> Result<Option<ConfigFile>> {
    let file = match OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW | libc::O_NONBLOCK)
        .open(CONFIG_PATH)
    {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).context("Open network isolation config"),
    };
    let metadata = file
        .metadata()
        .context("Inspect network isolation config")?;
    if !metadata.is_file() || metadata.uid() != 0 || metadata.mode() & 0o077 != 0 {
        bail!("Unsafe network isolation config permissions");
    }
    let bytes = read_limited(file, MAX_INPUT_BYTES, "network isolation config")?;
    let config = serde_json::from_slice(&bytes).context("Parse network isolation config")?;
    normalize(config).map(Some)
}

fn parse_input() -> Result<ConfigFile> {
    let bytes = read_limited(
        std::io::stdin().lock(),
        MAX_INPUT_BYTES,
        "network isolation input",
    )?;
    let config = serde_json::from_slice(&bytes).context("Parse network isolation input")?;
    normalize(config)
}

fn write_config(config: &ConfigFile) -> Result<()> {
    ensure_working_dir()?;
    let bytes = serde_json::to_vec_pretty(config).context("Serialize network isolation config")?;
    let mut temp = tempfile::NamedTempFile::new_in(WORKING_DIR)
        .context("Create network isolation temp file")?;
    temp.as_file()
        .set_permissions(std::fs::Permissions::from_mode(0o600))
        .context("Set network isolation temp permissions")?;
    temp.write_all(&bytes)
        .context("Write network isolation temp file")?;
    temp.as_file()
        .sync_all()
        .context("Sync network isolation temp file")?;
    temp.persist(CONFIG_PATH)
        .map_err(|error| error.error)
        .context("Persist network isolation config")?;
    File::open(WORKING_DIR)
        .context("Open network isolation directory")?
        .sync_all()
        .context("Sync network isolation directory")?;
    Ok(())
}

fn remove_config() -> Result<()> {
    match std::fs::remove_file(CONFIG_PATH) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).context("Remove network isolation config"),
    }
    File::open(WORKING_DIR)
        .context("Open network isolation directory")?
        .sync_all()
        .context("Sync network isolation directory")?;
    Ok(())
}

fn kernel_config(config: &ConfigFile) -> ksu_uapi::ksu_netisolate_config {
    let mut output = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_config>() };
    output.enabled = u32::from(config.enabled);
    output.count = config.uids.len() as u32;
    output.reserved = requested_flags(config);
    output.uids[..config.uids.len()].copy_from_slice(&config.uids);
    output
}

fn disabled_kernel_config(
    config: &ConfigFile,
    snapshot: &ksu_uapi::ksu_netisolate_config,
) -> ksu_uapi::ksu_netisolate_config {
    let mut output = kernel_config(config);
    output.enabled = 0;
    output.reserved &= kernel_flags(snapshot).1;
    output
}

fn kernel_readback_matches(
    request: &ksu_uapi::ksu_netisolate_config,
    readback: &ksu_uapi::ksu_netisolate_config,
) -> bool {
    let count = request.count as usize;
    let readback_count = readback.count as usize;
    let max = ksu_uapi::KSU_NETISOLATE_MAX_UIDS as usize;

    count <= max
        && readback_count <= max
        && readback.enabled == request.enabled
        && readback.count == request.count
        && readback.uids[..count] == request.uids[..count]
        && kernel_flags(readback).0 == request.reserved & FLAG_MASK
}

fn apply_kernel_config(kernel: &ksu_uapi::ksu_netisolate_config) -> Result<()> {
    ksucalls::set_netisolate_config(kernel)?;
    let readback = ksucalls::get_netisolate_config()?;
    if !kernel_readback_matches(kernel, &readback) {
        bail!("Kernel network isolation config readback mismatch");
    }
    Ok(())
}

fn apply_with_snapshot(
    config: &ConfigFile,
    snapshot: Option<&ksu_uapi::ksu_netisolate_config>,
) -> Result<()> {
    ensure_strict_supported(config, snapshot)?;
    apply_kernel_config(&kernel_config(config))
}

fn apply(config: &ConfigFile) -> Result<()> {
    let snapshot = ksucalls::get_netisolate_config()?;
    apply_with_snapshot(config, Some(&snapshot))
}

fn restore(snapshot: &ksu_uapi::ksu_netisolate_config) -> Result<()> {
    apply_kernel_config(&normalize_snapshot(*snapshot))
}

fn persist_with_rollback(
    config: &ConfigFile,
    old_kernel: Option<&ksu_uapi::ksu_netisolate_config>,
) -> Result<()> {
    ensure_strict_supported(config, old_kernel)?;
    persist_kernel_with_rollback(config, &kernel_config(config), old_kernel)
}

fn persist_kernel_with_rollback(
    config: &ConfigFile,
    kernel: &ksu_uapi::ksu_netisolate_config,
    old_kernel: Option<&ksu_uapi::ksu_netisolate_config>,
) -> Result<()> {
    apply_kernel_config(kernel)?;
    if let Err(error) = write_config(config) {
        if let Some(snapshot) = old_kernel
            && let Err(rollback) = restore(snapshot)
        {
            bail!("{error:#}; residual=netisolate-kernel:{rollback:#}");
        }
        return Err(error);
    }
    Ok(())
}

fn status() -> Result<()> {
    let kernel = match ksucalls::get_netisolate_config() {
        Ok(config) => Some(config),
        Err(error) if kernel_unsupported(&error) => None,
        Err(error) => return Err(error.context("Read network isolation status")),
    };
    let supported = kernel.is_some();
    let enabled = kernel.as_ref().is_some_and(|config| config.enabled != 0);
    let compat_supported = kernel
        .as_ref()
        .is_some_and(|config| config.compat_supported != 0);
    let strict_supported = kernel.as_ref().is_some_and(strict_socket_supported);
    let strict_socket = kernel.as_ref().is_some_and(strict_socket_active);
    let uids = kernel.as_ref().map_or_else(Vec::new, |config| {
        let count = (config.count as usize).min(ksu_uapi::KSU_NETISOLATE_MAX_UIDS as usize);
        config.uids[..count].to_vec()
    });
    println!(
        "{}",
        serde_json::json!({
            "supported": supported,
            "enabled": enabled,
            "compat_supported": compat_supported,
            "strict_socket_supported": strict_supported,
            "strict_socket": strict_socket,
            "uids": uids,
        })
    );
    Ok(())
}

fn enable() -> Result<()> {
    let _lock = acquire_lock()?;
    let mut config = read_config()?.context("Network isolation is not configured")?;
    if config.uids.is_empty() {
        bail!("Network isolation requires at least one UID");
    }
    config.enabled = true;
    let old = match ksucalls::get_netisolate_config() {
        Ok(snapshot) => Some(snapshot),
        Err(error) if kernel_unsupported(&error) => None,
        Err(error) => return Err(error.context("Read network isolation state")),
    };
    persist_with_rollback(&config, old.as_ref())
}

fn disable() -> Result<()> {
    let _lock = acquire_lock()?;
    let existing = read_config()?;
    let old = match ksucalls::get_netisolate_config() {
        Ok(snapshot) => snapshot,
        Err(error) if kernel_unsupported(&error) => {
            if let Some(mut config) = existing {
                config.enabled = false;
                write_config(&config)?;
            }
            return Ok(());
        }
        Err(error) => return Err(error.context("Read network isolation state")),
    };
    let mut config = existing.unwrap_or(ConfigFile {
        enabled: false,
        uids: Vec::new(),
        strict_socket: false,
    });
    config.enabled = false;
    let kernel = disabled_kernel_config(&config, &old);
    persist_kernel_with_rollback(&config, &kernel, Some(&old))
}

fn clear() -> Result<()> {
    let _lock = acquire_lock()?;
    let empty = ConfigFile {
        enabled: false,
        uids: Vec::new(),
        strict_socket: false,
    };
    match ksucalls::get_netisolate_config() {
        Ok(_) => apply(&empty)?,
        Err(error) if kernel_unsupported(&error) => {}
        Err(error) => return Err(error.context("Read network isolation state")),
    }
    remove_config()
}

fn apply_persisted() -> Result<()> {
    let snapshot = match ksucalls::get_netisolate_config() {
        Ok(snapshot) => snapshot,
        Err(error) if kernel_unsupported(&error) => return Ok(()),
        Err(error) => return Err(error.context("Read network isolation state")),
    };
    let _lock = acquire_lock()?;
    let Some(config) = read_config()? else {
        return Ok(());
    };
    apply_with_snapshot(&config, Some(&snapshot))
}

pub fn run(command: &Command) -> Result<()> {
    ensure_root()?;
    match command {
        Command::Status => status(),
        Command::Set => {
            let config = parse_input()?;
            let _lock = acquire_lock()?;
            let old = match ksucalls::get_netisolate_config() {
                Ok(snapshot) => Some(snapshot),
                Err(error) if kernel_unsupported(&error) => None,
                Err(error) => return Err(error.context("Read network isolation state")),
            };
            persist_with_rollback(&config, old.as_ref())?;
            info!("netisolate: event=set enabled={}", config.enabled);
            Ok(())
        }
        Command::Enable => enable(),
        Command::Disable => disable(),
        Command::Clear => clear(),
        Command::Apply => apply_persisted(),
    }
}

pub fn boot_apply() {
    if utils::is_safe_mode() {
        info!("netisolate: event=boot-apply skipped=safe-mode");
        return;
    }
    if let Err(error) = apply_persisted() {
        warn!("netisolate: event=boot-apply error={error:#}");
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn config(enabled: bool, strict_socket: bool) -> ConfigFile {
        ConfigFile {
            enabled,
            uids: vec![10123],
            strict_socket,
        }
    }

    fn kernel_config(enabled: u32, active: u32, supported: u32) -> ksu_uapi::ksu_netisolate_config {
        let mut config = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_config>() };
        config.enabled = enabled;
        config.count = 1;
        config.uids[0] = 10123;
        config.reserved = active | (supported << SUPPORTED_SHIFT);
        config
    }

    #[test]
    fn legacy_json_defaults_strict_socket_to_false() {
        let config: ConfigFile =
            serde_json::from_str(r#"{"enabled":true,"uids":[10123]}"#).unwrap();
        assert!(!config.strict_socket);
    }

    #[test]
    fn strict_socket_maps_to_the_shared_flag() {
        assert_eq!(STRICT_SOCKET_FLAG, requested_flags(&config(true, true)));
        assert_eq!(0, requested_flags(&config(true, false)));
    }

    #[test]
    fn status_decodes_active_and_supported_flags_separately() {
        let snapshot = kernel_config(1, STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG);
        assert_eq!(
            (STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG),
            kernel_flags(&snapshot)
        );
        assert!(strict_socket_supported(&snapshot));
        assert!(strict_socket_active(&snapshot));
    }

    #[test]
    fn legacy_kernel_does_not_support_strict_socket() {
        let snapshot = kernel_config(0, 0, 0);
        assert!(!strict_socket_supported(&snapshot));
        assert!(ensure_strict_supported(&config(true, true), Some(&snapshot)).is_err());
    }

    #[test]
    fn unsupported_strict_config_is_rejected_before_persisting() {
        let old = kernel_config(1, 0, 0);
        let error = persist_with_rollback(&config(true, true), Some(&old)).unwrap_err();
        assert!(error.to_string().contains("does not support strict socket"));
    }

    #[test]
    fn disable_masks_requested_flags_to_kernel_capabilities() {
        let persisted = config(true, true);
        let old = kernel_config(1, 0, 0);
        let disabled = disabled_kernel_config(&persisted, &old);
        assert_eq!(0, disabled.enabled);
        assert_eq!(0, disabled.reserved & FLAG_MASK);
        assert!(persisted.strict_socket);
    }

    #[test]
    fn restore_uses_only_active_flag_bits() {
        let snapshot = kernel_config(1, STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG);
        assert_eq!(STRICT_SOCKET_FLAG, normalize_snapshot(snapshot).reserved);
    }

    #[test]
    fn readback_compares_active_flags_not_capability_bits() {
        let request = kernel_config(1, STRICT_SOCKET_FLAG, 0);
        let readback = kernel_config(1, STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG);
        assert!(kernel_readback_matches(&request, &readback));

        let wrong_active = kernel_config(1, 0, STRICT_SOCKET_FLAG);
        assert!(!kernel_readback_matches(&request, &wrong_active));
    }
}
