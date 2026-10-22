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
    output.uids[..config.uids.len()].copy_from_slice(&config.uids);
    output
}

fn apply(config: &ConfigFile) -> Result<()> {
    let kernel = kernel_config(config);
    ksucalls::set_netisolate_config(&kernel)?;
    let readback = ksucalls::get_netisolate_config()?;
    let count = kernel.count as usize;
    if readback.enabled != kernel.enabled
        || readback.count != kernel.count
        || readback.uids[..count] != kernel.uids[..count]
    {
        bail!("Kernel network isolation config readback mismatch");
    }
    Ok(())
}

fn restore(snapshot: &ksu_uapi::ksu_netisolate_config) -> Result<()> {
    ksucalls::set_netisolate_config(snapshot)
}

fn persist_with_rollback(
    config: &ConfigFile,
    old_kernel: Option<&ksu_uapi::ksu_netisolate_config>,
) -> Result<()> {
    apply(config)?;
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
    });
    config.enabled = false;
    persist_with_rollback(&config, Some(&old))
}

fn clear() -> Result<()> {
    let _lock = acquire_lock()?;
    let empty = ConfigFile {
        enabled: false,
        uids: Vec::new(),
    };
    match ksucalls::get_netisolate_config() {
        Ok(_) => apply(&empty)?,
        Err(error) if kernel_unsupported(&error) => {}
        Err(error) => return Err(error.context("Read network isolation state")),
    }
    remove_config()
}

fn apply_persisted() -> Result<()> {
    match ksucalls::get_netisolate_config() {
        Ok(_) => {}
        Err(error) if kernel_unsupported(&error) => return Ok(()),
        Err(error) => return Err(error.context("Read network isolation state")),
    }
    let _lock = acquire_lock()?;
    let Some(config) = read_config()? else {
        return Ok(());
    };
    apply(&config)
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
