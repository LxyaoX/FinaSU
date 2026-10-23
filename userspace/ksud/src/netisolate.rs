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
const ALLOW_LOOPBACK_FLAG: u32 = ksu_uapi::KSU_NETISOLATE_FLAG_ALLOW_LOOPBACK;

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
    #[serde(default)]
    allow_loopback: bool,
    #[serde(default)]
    policies: Option<Vec<NetIsolatePolicy>>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct NetIsolatePolicy {
    uid: u32,
    strict_socket: bool,
    allow_loopback: bool,
}

const fn empty_config() -> ConfigFile {
    ConfigFile {
        enabled: false,
        uids: Vec::new(),
        strict_socket: false,
        allow_loopback: false,
        policies: Some(Vec::new()),
    }
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
    if config.uids.len() > ksu_uapi::KSU_NETISOLATE_MAX_UIDS as usize {
        bail!(
            "Network isolation supports at most {} UIDs",
            ksu_uapi::KSU_NETISOLATE_MAX_UIDS
        );
    }
    if config.uids.windows(2).any(|uids| uids[0] == uids[1]) {
        bail!("Network isolation UIDs must be unique");
    }
    if config.uids.contains(&0) {
        bail!("UID 0 is permanently exempt and cannot be isolated");
    }
    if config.policies.is_none() && config.strict_socket && config.allow_loopback {
        bail!("Strict socket mode and loopback exemption cannot be enabled together");
    }

    let mut policies = if let Some(policies) = config.policies.take() {
        policies
    } else {
        let flags = requested_flags(&config);
        config
            .uids
            .iter()
            .map(|&uid| policy_from_flags(uid, flags))
            .collect()
    };
    if policies.len() > ksu_uapi::KSU_NETISOLATE_MAX_POLICIES as usize {
        bail!(
            "Network isolation supports at most {} policies",
            ksu_uapi::KSU_NETISOLATE_MAX_POLICIES
        );
    }
    policies.sort_unstable_by_key(|policy| policy.uid);
    if policies.windows(2).any(|pair| pair[0].uid == pair[1].uid) {
        bail!("Network isolation policy UIDs must be unique");
    }
    if policies.iter().any(|policy| policy.uid == 0) {
        bail!("UID 0 is permanently exempt and cannot be isolated");
    }
    if policies
        .iter()
        .any(|policy| policy.strict_socket && policy.allow_loopback)
    {
        bail!("Strict socket mode and loopback exemption cannot be enabled together");
    }
    let policy_uids: Vec<u32> = policies.iter().map(|policy| policy.uid).collect();
    if policy_uids != config.uids {
        bail!("Network isolation policies must match the UID list exactly");
    }
    if config.enabled && config.uids.is_empty() {
        bail!("Network isolation requires at least one UID when enabled");
    }
    let common = common_policy_flags(&policies);
    config.strict_socket = common & STRICT_SOCKET_FLAG != 0;
    config.allow_loopback = common & ALLOW_LOOPBACK_FLAG != 0;
    config.policies = Some(policies);
    Ok(config)
}

const fn requested_flags(config: &ConfigFile) -> u32 {
    let mut flags = 0;

    if config.strict_socket {
        flags |= STRICT_SOCKET_FLAG;
    }
    if config.allow_loopback {
        flags |= ALLOW_LOOPBACK_FLAG;
    }
    flags
}

const fn policy_flags(policy: &NetIsolatePolicy) -> u32 {
    let mut flags = 0;

    if policy.strict_socket {
        flags |= STRICT_SOCKET_FLAG;
    }
    if policy.allow_loopback {
        flags |= ALLOW_LOOPBACK_FLAG;
    }
    flags
}

const fn policy_from_flags(uid: u32, flags: u32) -> NetIsolatePolicy {
    NetIsolatePolicy {
        uid,
        strict_socket: flags & STRICT_SOCKET_FLAG != 0,
        allow_loopback: flags & ALLOW_LOOPBACK_FLAG != 0,
    }
}

fn common_policy_flags(policies: &[NetIsolatePolicy]) -> u32 {
    let Some(first) = policies.first() else {
        return 0;
    };
    policies[1..]
        .iter()
        .fold(policy_flags(first), |common, policy| {
            common & policy_flags(policy)
        })
}

fn policies_have_same_flags(policies: &[NetIsolatePolicy]) -> bool {
    policies.first().is_none_or(|first| {
        policies
            .iter()
            .all(|policy| policy_flags(policy) == policy_flags(first))
    })
}

const fn kernel_flags(config: &ksu_uapi::ksu_netisolate_config) -> (u32, u32) {
    (
        config.reserved & FLAG_MASK,
        (config.reserved >> SUPPORTED_SHIFT) & FLAG_MASK,
    )
}

const fn normalize_snapshot(
    mut snapshot: ksu_uapi::ksu_netisolate_config,
) -> ksu_uapi::ksu_netisolate_config {
    snapshot.reserved &= FLAG_MASK;
    snapshot
}

struct KernelSnapshot {
    legacy: Option<ksu_uapi::ksu_netisolate_config>,
    policies: Option<ksu_uapi::ksu_netisolate_policies>,
}

fn ensure_policy_flags_supported(config: &ConfigFile, snapshot: &KernelSnapshot) -> Result<()> {
    let policies = config.policies.as_deref().unwrap_or_default();
    if let Some(kernel) = snapshot.policies.as_ref() {
        let supported = kernel.supported_flags & FLAG_MASK;
        if policies
            .iter()
            .any(|policy| policy_flags(policy) & !supported != 0)
        {
            bail!("Kernel does not support the requested network isolation flags");
        }
        return Ok(());
    }

    if !policies_have_same_flags(policies) {
        bail!("Kernel does not support per-app network isolation policies");
    }
    let legacy = snapshot
        .legacy
        .as_ref()
        .context("Kernel does not support network isolation")?;
    let supported = kernel_flags(legacy).1;
    if policies
        .iter()
        .any(|policy| policy_flags(policy) & !supported != 0)
    {
        bail!("Kernel does not support the requested network isolation flags");
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

fn legacy_kernel_config(config: &ConfigFile) -> ksu_uapi::ksu_netisolate_config {
    let mut output = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_config>() };
    output.enabled = u32::from(config.enabled);
    output.count = config.uids.len() as u32;
    output.reserved = requested_flags(config);
    output.uids[..config.uids.len()].copy_from_slice(&config.uids);
    output
}

fn kernel_policies(config: &ConfigFile) -> ksu_uapi::ksu_netisolate_policies {
    let mut output = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_policies>() };
    let policies = config.policies.as_deref().unwrap_or_default();
    output.enabled = u32::from(config.enabled);
    output.count = policies.len() as u32;
    for (slot, policy) in output.policies.iter_mut().zip(policies) {
        slot.uid = policy.uid;
        slot.flags = policy_flags(policy);
    }
    output
}

fn legacy_readback_matches(
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

fn policies_readback_matches(
    request: &ksu_uapi::ksu_netisolate_policies,
    readback: &ksu_uapi::ksu_netisolate_policies,
) -> bool {
    let count = request.count as usize;
    let readback_count = readback.count as usize;
    let max = ksu_uapi::KSU_NETISOLATE_MAX_POLICIES as usize;

    count <= max
        && readback_count <= max
        && readback.enabled == request.enabled
        && readback.count == request.count
        && (0..count).all(|index| {
            readback.policies[index].uid == request.policies[index].uid
                && readback.policies[index].flags == request.policies[index].flags
        })
}

fn apply_legacy_kernel_config(kernel: &ksu_uapi::ksu_netisolate_config) -> Result<()> {
    ksucalls::set_netisolate_config(kernel)?;
    let readback = ksucalls::get_netisolate_config()?;
    if !legacy_readback_matches(kernel, &readback) {
        bail!("Kernel network isolation config readback mismatch");
    }
    Ok(())
}

fn apply_kernel_policies(kernel: &ksu_uapi::ksu_netisolate_policies) -> Result<()> {
    ksucalls::set_netisolate_policies(kernel)?;
    let readback = ksucalls::get_netisolate_policies()?;
    if !policies_readback_matches(kernel, &readback) {
        bail!("Kernel network isolation policy readback mismatch");
    }
    Ok(())
}

fn read_kernel_snapshot() -> Result<KernelSnapshot> {
    let legacy = match ksucalls::get_netisolate_config() {
        Ok(config) => Some(config),
        Err(error) if kernel_unsupported(&error) => None,
        Err(error) => return Err(error.context("Read network isolation state")),
    };
    let policies = if legacy.is_some() {
        match ksucalls::get_netisolate_policies() {
            Ok(config) => Some(config),
            Err(error) if kernel_unsupported(&error) => None,
            Err(error) => return Err(error.context("Read network isolation policies")),
        }
    } else {
        None
    };
    Ok(KernelSnapshot { legacy, policies })
}

fn apply_config_to_kernel(config: &ConfigFile, snapshot: &KernelSnapshot) -> Result<()> {
    ensure_policy_flags_supported(config, snapshot)?;
    if snapshot.policies.is_some() {
        apply_kernel_policies(&kernel_policies(config))
    } else {
        apply_legacy_kernel_config(&legacy_kernel_config(config))
    }
}

fn restore(snapshot: &KernelSnapshot) -> Result<()> {
    snapshot.policies.as_ref().map_or_else(
        || {
            snapshot.legacy.as_ref().map_or(Ok(()), |legacy| {
                apply_legacy_kernel_config(&normalize_snapshot(*legacy))
            })
        },
        apply_kernel_policies,
    )
}

fn persist_with_rollback(config: &ConfigFile, snapshot: &KernelSnapshot) -> Result<()> {
    apply_config_to_kernel(config, snapshot)?;
    if let Err(error) = write_config(config) {
        if let Err(rollback) = restore(snapshot) {
            bail!("{error:#}; residual=netisolate-kernel:{rollback:#}");
        }
        return Err(error);
    }
    Ok(())
}

fn status() -> Result<()> {
    let legacy = match ksucalls::get_netisolate_config() {
        Ok(config) => Some(config),
        Err(error) if kernel_unsupported(&error) => None,
        Err(error) => return Err(error.context("Read network isolation status")),
    };
    let policies_kernel = if legacy.is_some() {
        match ksucalls::get_netisolate_policies() {
            Ok(config) => Some(config),
            Err(error) if kernel_unsupported(&error) => None,
            Err(error) => return Err(error.context("Read network isolation policies")),
        }
    } else {
        None
    };
    let supported = legacy.is_some();
    let policy_supported = policies_kernel.is_some();
    let enabled = policies_kernel.as_ref().map_or_else(
        || legacy.as_ref().is_some_and(|config| config.enabled != 0),
        |config| config.enabled != 0,
    );
    let compat_supported = legacy
        .as_ref()
        .is_some_and(|config| config.compat_supported != 0);
    let (supported_flags, policies) = match (policies_kernel.as_ref(), legacy.as_ref()) {
        (Some(config), _) => {
            let count = (config.count as usize).min(ksu_uapi::KSU_NETISOLATE_MAX_POLICIES as usize);
            (
                config.supported_flags & FLAG_MASK,
                config.policies[..count]
                    .iter()
                    .map(|policy| policy_from_flags(policy.uid, policy.flags))
                    .collect::<Vec<_>>(),
            )
        }
        (None, Some(config)) => {
            let count = (config.count as usize).min(ksu_uapi::KSU_NETISOLATE_MAX_UIDS as usize);
            let active = kernel_flags(config).0;
            (
                kernel_flags(config).1,
                config.uids[..count]
                    .iter()
                    .map(|&uid| policy_from_flags(uid, active))
                    .collect::<Vec<_>>(),
            )
        }
        (None, None) => (0, Vec::new()),
    };
    let common = common_policy_flags(&policies);
    let uids = policies.iter().map(|policy| policy.uid).collect::<Vec<_>>();
    println!(
        "{}",
        serde_json::json!({
            "supported": supported,
            "enabled": enabled,
            "compat_supported": compat_supported,
            "policy_supported": policy_supported,
            "strict_socket_supported": supported_flags & STRICT_SOCKET_FLAG != 0,
            "strict_socket": common & STRICT_SOCKET_FLAG != 0,
            "allow_loopback_supported": supported_flags & ALLOW_LOOPBACK_FLAG != 0,
            "allow_loopback": common & ALLOW_LOOPBACK_FLAG != 0,
            "uids": uids,
            "policies": policies,
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
    let snapshot = read_kernel_snapshot()?;
    persist_with_rollback(&config, &snapshot)
}

fn disable() -> Result<()> {
    let _lock = acquire_lock()?;
    let existing = read_config()?;
    let snapshot = read_kernel_snapshot()?;
    if snapshot.legacy.is_none() {
        if let Some(mut config) = existing {
            config.enabled = false;
            write_config(&config)?;
        }
        return Ok(());
    }
    let mut config = existing.unwrap_or_else(empty_config);
    config.enabled = false;
    if snapshot.policies.is_some() {
        apply_kernel_policies(&kernel_policies(&config))?;
    } else {
        apply_legacy_kernel_config(&legacy_kernel_config(&config))?;
    }
    if let Err(error) = write_config(&config) {
        if let Err(rollback) = restore(&snapshot) {
            bail!("{error:#}; residual=netisolate-kernel:{rollback:#}");
        }
        return Err(error);
    }
    Ok(())
}

fn clear() -> Result<()> {
    let _lock = acquire_lock()?;
    let snapshot = read_kernel_snapshot()?;
    let empty = empty_config();
    if snapshot.policies.is_some() {
        apply_kernel_policies(&kernel_policies(&empty))?;
    } else if snapshot.legacy.is_some() {
        apply_legacy_kernel_config(&legacy_kernel_config(&empty))?;
    }
    remove_config()
}

fn apply_persisted() -> Result<()> {
    let _lock = acquire_lock()?;
    let Some(config) = read_config()? else {
        return Ok(());
    };
    let snapshot = read_kernel_snapshot()?;
    if snapshot.legacy.is_none() {
        return Ok(());
    }
    apply_config_to_kernel(&config, &snapshot)
}

pub fn run(command: &Command) -> Result<()> {
    ensure_root()?;
    match command {
        Command::Status => status(),
        Command::Set => {
            let config = parse_input()?;
            let _lock = acquire_lock()?;
            let snapshot = read_kernel_snapshot()?;
            persist_with_rollback(&config, &snapshot)?;
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

    fn config(enabled: bool, strict_socket: bool, allow_loopback: bool) -> ConfigFile {
        ConfigFile {
            enabled,
            uids: vec![10123],
            strict_socket,
            allow_loopback,
            policies: None,
        }
    }

    fn policy(uid: u32, strict_socket: bool, allow_loopback: bool) -> NetIsolatePolicy {
        NetIsolatePolicy {
            uid,
            strict_socket,
            allow_loopback,
        }
    }

    fn policy_config(enabled: bool, policies: Vec<NetIsolatePolicy>) -> ConfigFile {
        ConfigFile {
            enabled,
            uids: policies.iter().map(|policy| policy.uid).collect(),
            strict_socket: false,
            allow_loopback: false,
            policies: Some(policies),
        }
    }

    fn legacy_kernel(enabled: u32, active: u32, supported: u32) -> ksu_uapi::ksu_netisolate_config {
        let mut config = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_config>() };
        config.enabled = enabled;
        config.count = 1;
        config.uids[0] = 10123;
        config.reserved = active | (supported << SUPPORTED_SHIFT);
        config
    }

    fn policy_kernel(
        policies: &[NetIsolatePolicy],
        supported: u32,
    ) -> ksu_uapi::ksu_netisolate_policies {
        let mut output = unsafe { std::mem::zeroed::<ksu_uapi::ksu_netisolate_policies>() };
        output.enabled = 1;
        output.count = policies.len() as u32;
        output.supported_flags = supported;
        for (slot, policy) in output.policies.iter_mut().zip(policies) {
            slot.uid = policy.uid;
            slot.flags = policy_flags(policy);
        }
        output
    }

    #[test]
    fn legacy_json_expands_shared_flags_to_policies() {
        let config: ConfigFile =
            serde_json::from_str(r#"{"enabled":true,"uids":[10123]}"#).unwrap();
        let config = normalize(config).unwrap();
        assert_eq!(Some(vec![policy(10123, false, false)]), config.policies);
    }

    #[test]
    fn new_json_encodes_each_policy_flags() {
        let config: ConfigFile = serde_json::from_str(
            r#"{
                "enabled": true,
                "uids": [10200, 10123],
                "policies": [
                    {"uid": 10200, "strict_socket": true, "allow_loopback": false},
                    {"uid": 10123, "strict_socket": false, "allow_loopback": true}
                ]
            }"#,
        )
        .unwrap();
        let config = normalize(config).unwrap();
        let kernel = kernel_policies(&config);
        assert_eq!(10123, kernel.policies[0].uid);
        assert_eq!(ALLOW_LOOPBACK_FLAG, kernel.policies[0].flags);
        assert_eq!(10200, kernel.policies[1].uid);
        assert_eq!(STRICT_SOCKET_FLAG, kernel.policies[1].flags);
    }

    #[test]
    fn serialized_config_writes_sorted_policies_and_common_flags() {
        let config = normalize(policy_config(
            true,
            vec![policy(10200, true, false), policy(10123, false, true)],
        ))
        .unwrap();
        let serialized = serde_json::to_value(config).unwrap();
        assert_eq!(Some(false), serialized["strict_socket"].as_bool());
        assert_eq!(Some(false), serialized["allow_loopback"].as_bool());
        assert_eq!(10123, serialized["policies"][0]["uid"].as_u64().unwrap());
        assert_eq!(
            true,
            serialized["policies"][0]["allow_loopback"]
                .as_bool()
                .unwrap()
        );
    }

    #[test]
    fn duplicate_uids_are_rejected() {
        let mut legacy = config(true, false, false);
        legacy.uids.push(10123);
        assert!(normalize(legacy).is_err());

        assert!(
            normalize(policy_config(
                true,
                vec![policy(10123, false, false), policy(10123, false, true)],
            ))
            .is_err()
        );
    }

    #[test]
    fn policy_uid_set_must_match_exactly() {
        let mut missing = policy_config(true, vec![policy(10123, false, false)]);
        missing.uids.push(10200);
        assert!(normalize(missing).is_err());

        let mut extra = policy_config(true, vec![policy(10123, false, false)]);
        extra.policies = Some(vec![
            policy(10123, false, false),
            policy(10200, false, false),
        ]);
        assert!(normalize(extra).is_err());
    }

    #[test]
    fn uid_zero_is_rejected() {
        let mut legacy = config(false, false, false);
        legacy.uids = vec![0];
        assert!(normalize(legacy).is_err());
        assert!(normalize(policy_config(false, vec![policy(0, false, false)])).is_err());
    }

    #[test]
    fn strict_and_loopback_are_mutually_exclusive() {
        let error = normalize(config(true, true, true)).unwrap_err();
        assert!(error.to_string().contains("cannot be enabled together"));
        assert!(normalize(policy_config(true, vec![policy(10123, true, true)])).is_err());
    }

    #[test]
    fn status_decodes_active_and_supported_flags_separately() {
        let snapshot = legacy_kernel(1, STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG);
        assert_eq!(
            (STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG),
            kernel_flags(&snapshot)
        );
    }

    #[test]
    fn status_decodes_loopback_capability() {
        let snapshot = legacy_kernel(1, ALLOW_LOOPBACK_FLAG, ALLOW_LOOPBACK_FLAG);
        assert_eq!(
            (ALLOW_LOOPBACK_FLAG, ALLOW_LOOPBACK_FLAG),
            kernel_flags(&snapshot)
        );
    }

    #[test]
    fn legacy_kernel_rejects_unsupported_flags() {
        let snapshot = KernelSnapshot {
            legacy: Some(legacy_kernel(0, 0, 0)),
            policies: None,
        };
        assert_eq!((0, 0), kernel_flags(snapshot.legacy.as_ref().unwrap()));
        assert!(
            ensure_policy_flags_supported(
                &normalize(config(true, true, false)).unwrap(),
                &snapshot
            )
            .is_err()
        );
        assert!(
            ensure_policy_flags_supported(
                &normalize(config(true, false, true)).unwrap(),
                &snapshot
            )
            .is_err()
        );
    }

    #[test]
    fn legacy_kernel_accepts_uniform_policy_flags() {
        let snapshot = KernelSnapshot {
            legacy: Some(legacy_kernel(1, 0, ALLOW_LOOPBACK_FLAG)),
            policies: None,
        };
        let config = normalize(policy_config(
            true,
            vec![policy(10123, false, true), policy(10200, false, true)],
        ))
        .unwrap();
        assert!(ensure_policy_flags_supported(&config, &snapshot).is_ok());
        let legacy = legacy_kernel_config(&config);
        assert_eq!(2, legacy.count);
        assert_eq!(ALLOW_LOOPBACK_FLAG, legacy.reserved & FLAG_MASK);
    }

    #[test]
    fn legacy_kernel_rejects_mixed_flags() {
        let snapshot = KernelSnapshot {
            legacy: Some(legacy_kernel(
                1,
                0,
                STRICT_SOCKET_FLAG | ALLOW_LOOPBACK_FLAG,
            )),
            policies: None,
        };
        let config = normalize(policy_config(
            true,
            vec![policy(10123, false, false), policy(10200, false, true)],
        ))
        .unwrap();
        let error = ensure_policy_flags_supported(&config, &snapshot).unwrap_err();
        assert!(error.to_string().contains("does not support per-app"));
    }

    #[test]
    fn policy_readback_detects_one_changed_flags_entry() {
        let policies = vec![policy(10123, false, false), policy(10200, false, true)];
        let request = policy_kernel(&policies, STRICT_SOCKET_FLAG | ALLOW_LOOPBACK_FLAG);
        let mut readback = request;
        assert!(policies_readback_matches(&request, &readback));
        readback.policies[1].flags = STRICT_SOCKET_FLAG;
        assert!(!policies_readback_matches(&request, &readback));
    }

    #[test]
    fn legacy_restore_uses_only_active_flag_bits() {
        let snapshot = legacy_kernel(1, STRICT_SOCKET_FLAG, STRICT_SOCKET_FLAG);
        assert_eq!(STRICT_SOCKET_FLAG, normalize_snapshot(snapshot).reserved);
    }

    #[test]
    fn legacy_readback_compares_active_flags_not_capability_bits() {
        let request = legacy_kernel(1, ALLOW_LOOPBACK_FLAG, 0);
        let readback = legacy_kernel(1, ALLOW_LOOPBACK_FLAG, ALLOW_LOOPBACK_FLAG);
        assert!(legacy_readback_matches(&request, &readback));

        let wrong_active = legacy_kernel(1, 0, ALLOW_LOOPBACK_FLAG);
        assert!(!legacy_readback_matches(&request, &wrong_active));
    }

    #[test]
    fn disable_retains_policy_entries() {
        let mut config = normalize(policy_config(
            true,
            vec![policy(10123, false, false), policy(10200, false, true)],
        ))
        .unwrap();
        config.enabled = false;
        let kernel = kernel_policies(&config);
        assert_eq!(0, kernel.enabled);
        assert_eq!(2, kernel.count);
        assert_eq!(ALLOW_LOOPBACK_FLAG, kernel.policies[1].flags);
    }

    #[test]
    fn clear_builds_empty_policy_set() {
        let kernel = kernel_policies(&empty_config());
        assert_eq!(0, kernel.enabled);
        assert_eq!(0, kernel.count);
    }
}
