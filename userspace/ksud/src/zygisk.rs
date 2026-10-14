//! FolkZygisk provider selection, external-provider step-aside and status reporting.
//! Engine readiness and injection are separate from next-boot configuration.
//!
//! An active external provider wins: the built-in engine stays idle without touching module
//! installation or rewriting provider modules. Removing the external provider resumes the
//! built-in engine on the next boot because the selection is left untouched.

use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::os::fd::AsRawFd;
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::path::Path;

const CONFIG: &str = "/data/adb/ksu/zygisk.json";
const LOCK: &str = "/data/adb/ksu/zygisk.lock";
const ENGINE: &str = "/data/adb/ksu/bin/folkzygisk";
const MODULES: &str = "/data/adb/modules";
const UPDATES: &str = "/data/adb/modules_update";
const RUNTIME: &str = "/data/adb/ksu/.zygisk/state.json";
const BOOT_GUARD: &str = "/data/adb/ksu/zygisk-boot.json";
const FAILURE_LIMIT: u32 = 3;

#[derive(Default, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct BootGuard {
    boot_id: String,
    failures: u32,
    latched: bool,
}

impl BootGuard {
    fn attempt(&mut self, boot: &str) -> bool {
        if self.latched || self.boot_id == boot {
            return false;
        }
        if self.failures >= FAILURE_LIMIT {
            boot.clone_into(&mut self.boot_id);
            self.latched = true;
            return false;
        }
        boot.clone_into(&mut self.boot_id);
        self.failures += 1;
        true
    }
}

fn load_guard(path: &Path) -> Result<BootGuard> {
    match fs::read(path) {
        Ok(bytes) => serde_json::from_slice(&bytes).context("Invalid FolkZygisk boot guard"),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(BootGuard::default()),
        Err(error) => Err(error).context("Read FolkZygisk boot guard"),
    }
}

/// Remove leftover runtime artifacts from a previous boot.
///
/// The runtime directory is persistent, but its contents (the monitor socket, lock and status
/// file) are per-boot. Keep the lock inode and refuse cleanup if a monitor owns it,
/// including a manually started monitor not recorded by the boot guard.
fn clear_stale_runtime(runtime_dir: &Path) -> Result<()> {
    let lock = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(runtime_dir.join("monitor.lock"))?;
    // SAFETY: lock owns a valid descriptor; closing it releases the flock.
    ensure!(
        unsafe { libc::flock(lock.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0,
        "A FolkZygisk monitor owns the runtime directory"
    );
    for entry in fs::read_dir(runtime_dir).context("Read FolkZygisk runtime directory")? {
        let entry = entry.context("Read FolkZygisk runtime entry")?;
        if entry.file_name() == "monitor.lock" {
            continue;
        }
        let path = entry.path();
        let result = match entry.file_type() {
            Ok(kind) if kind.is_dir() => fs::remove_dir_all(&path),
            _ => fs::remove_file(&path),
        };
        result
            .with_context(|| format!("Clear stale FolkZygisk runtime entry {}", path.display()))?;
    }
    Ok(())
}

/// The kernel starts `ksud post-fs-data` as an init exec (oneshot) service. When that service
/// exits, init SIGKILLs the service's whole cgroup, which would take the monitor down even though
/// `setsid` already detached it from the service's session and process group. Move the monitor into
/// the root cgroup, which init never tears down, so it survives the post-fs-data teardown.
#[cfg(target_os = "android")]
fn detach_monitor_from_service_cgroup() -> std::io::Result<()> {
    // Called after fork: only stack operations and async-signal-safe libc calls.
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

/// SELinux rules the built-in engine needs in order to inject. The engine's runtime tree lives
/// under `/data/adb`, whose ancestor directories are labelled `adb_data_file`; without search
/// access the zygote cannot even resolve the path, so the injected `openat` for `libzygisk.so`
/// fails with `EACCES`. The rules mirror what a working external provider ships (e.g. ReZygisk)
/// and define the `zygisk_file` label the daemon applies to its daemon socket. The running policy
/// is reset on every reboot, so this must be applied on each boot before the monitor starts.
/// `unlabeled`/`sys_chroot` cover the daemon's mount-namespace fd (an `nsfs` inode) that the
/// zygote opens to switch into the module-free namespace at a module's request.
#[cfg(target_os = "android")]
const SEPOLICY_RULES: &str = "\
type zygisk_file file_type
typeattribute zygisk_file mlstrustedobject
allow zygote zygisk_file sock_file { read write }
allow zygote adb_data_file dir search
allow zygote adb_data_file file { read open getattr map }
allow zygote ksu dir search
allow zygote ksu file { read open getattr }
allow zygote proc file { read open }
allow zygote unlabeled file { read open }
allow zygote zygote capability sys_chroot
allow zygote adb_data_file lnk_file read
allow zygote ksu lnk_file read
allow zygote system_file file { read open getattr map }
allow zygote system_file lnk_file read
allow zygote system_lib_file file { read open execute execmod map }
allow zygote tmpfs file *
allow zygote appdomain_tmpfs file *
allow zygote zygote process execmem
allow system_server system_server process execmem
";

#[cfg(target_os = "android")]
fn apply_sepolicy_rules() -> Result<()> {
    crate::sepolicy::live_patch(SEPOLICY_RULES).context("Apply FolkZygisk SELinux policy rules")
}

#[cfg(target_os = "android")]
pub fn post_fs_data(safe_mode: bool) -> Result<()> {
    use std::os::unix::fs::MetadataExt;
    use std::os::unix::process::CommandExt;
    use std::process::{Command as ProcessCommand, Stdio};
    use std::time::{Duration, Instant};
    if safe_mode || crate::utils::is_safe_mode() || crate::utils::has_magisk() {
        return Ok(());
    }
    let _lock = lock()?;
    if !read_config(Path::new(CONFIG))?.enabled {
        return Ok(());
    }
    // An active external provider wins. Step aside before touching the boot guard so that
    // yielding never consumes a startup attempt.
    if !providers(Path::new(MODULES), Path::new(UPDATES))?.is_empty() {
        log::info!("External Zygisk provider active; built-in FolkZygisk steps aside");
        return Ok(());
    }
    let boot = fs::read_to_string("/proc/sys/kernel/random/boot_id")?;
    let mut guard = load_guard(Path::new(BOOT_GUARD))?;
    let attempt = guard.attempt(boot.trim());
    write_json(Path::new(BOOT_GUARD), &guard)?;
    if !attempt {
        return Ok(());
    }
    ensure!(
        engine_available(Path::new(ENGINE)),
        "FolkZygisk engine missing"
    );
    // The zygote must be able to search the engine's parent directories and read the injected
    // library and daemon socket; without these rules the injected remote `openat` fails with EACCES.
    if let Err(e) = apply_sepolicy_rules() {
        log::warn!("FolkZygisk sepolicy rules failed to apply: {e}");
    }
    let runtime_dir = Path::new(RUNTIME).parent().context("Runtime directory")?;
    fs::create_dir_all(runtime_dir)?;
    let metadata = fs::symlink_metadata(runtime_dir)?;
    ensure!(
        metadata.is_dir() && metadata.uid() == 0,
        "Unsafe FolkZygisk runtime directory"
    );
    // Zygisk clients need socket traversal after specialization, not listing.
    fs::set_permissions(runtime_dir, fs::Permissions::from_mode(0o711))?;
    crate::restorecon::setsyscon(runtime_dir)?;
    clear_stale_runtime(runtime_dir)?;
    let executable = if cfg!(target_pointer_width = "64") {
        "bin/zygisk-ptrace64"
    } else {
        "bin/zygisk-ptrace32"
    };
    let mut command = ProcessCommand::new(Path::new(ENGINE).join(executable));
    command
        .arg("monitor")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    // SAFETY: `pre_exec` runs in the forked child before `exec`. `setsid` is async-signal-safe and
    // gives the monitor its own session. Move it out of init's exec-service cgroup
    // before exec, so any daemon children inherit the surviving cgroup too.
    unsafe {
        command.pre_exec(|| {
            if libc::setsid() == -1 {
                return Err(std::io::Error::last_os_error());
            }
            detach_monitor_from_service_cgroup()
        });
    }
    let mut child = command.spawn()?;
    let deadline = Instant::now() + Duration::from_secs(2);
    let result = loop {
        match child.try_wait() {
            Ok(Some(_)) => {
                break Err(anyhow::anyhow!(
                    "FolkZygisk monitor exited before readiness"
                ));
            }
            Err(error) => break Err(error.into()),
            Ok(None) => {}
        }
        match read_runtime(Path::new(RUNTIME), Path::new("/proc")) {
            Ok(Some(runtime))
                if runtime.monitor_pid == child.id() as i32
                    && runtime.ready
                    && runtime.tracing_state == 0 =>
            {
                break Ok(());
            }
            Err(error) => log::debug!("FolkZygisk awaiting valid status: {error:#}"),
            _ => {}
        }
        if Instant::now() >= deadline {
            break Err(anyhow::anyhow!("FolkZygisk readiness timeout"));
        }
        std::thread::sleep(Duration::from_millis(20));
    };
    if result.is_err() {
        let _ = child.kill();
        let _ = child.wait();
    }
    result
}

#[cfg(target_os = "android")]
pub fn services() -> Result<()> {
    if crate::utils::is_safe_mode() || !read_config(Path::new(CONFIG))?.enabled {
        return Ok(());
    }
    // Stepped aside for an external provider: there is no built-in health to report.
    if !providers(Path::new(MODULES), Path::new(UPDATES))?.is_empty() {
        return Ok(());
    }
    let runtime = read_runtime(Path::new(RUNTIME), Path::new("/proc"))?;
    log::info!(
        "FolkZygisk service health: {}",
        injection_state(runtime.as_ref())
    );
    Ok(())
}

#[cfg(target_os = "android")]
pub fn boot_completed() -> Result<()> {
    if crate::utils::is_safe_mode() {
        return Ok(());
    }
    let _lock = lock()?;
    let runtime = read_runtime(Path::new(RUNTIME), Path::new("/proc"))?;
    // Android boot completion alone does not prove the engine is healthy.
    if injection_state(runtime.as_ref()) != "injected" {
        return Ok(());
    }
    let mut guard = load_guard(Path::new(BOOT_GUARD))?;
    if runtime
        .as_ref()
        .is_some_and(|status| status.boot_id == guard.boot_id)
    {
        guard.failures = 0;
        write_json(Path::new(BOOT_GUARD), &guard)?;
    }
    Ok(())
}

#[derive(Clone, Debug, clap::Subcommand)]
pub enum Command {
    /// Show next-boot selection and validated current-boot injection status
    Status {
        #[arg(long)]
        json: bool,
    },
    /// Enable FolkZygisk for the next boot
    Enable,
    /// Disable FolkZygisk for the next boot
    Disable,
    /// Clear the startup failure latch without enabling or starting injection
    ResetFailures,
    /// Install embedded engine assets without enabling or starting injection
    InstallEngine,
}

#[derive(Default, Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
struct Config {
    enabled: bool,
}

#[derive(Serialize)]
struct Status {
    schema_version: u32,
    enabled: bool,
    engine_available: bool,
    enable_blockers: Vec<&'static str>,
    external_providers: Vec<String>,
    // Injection requires a current-boot acknowledgement and matching processes.
    injection_state: &'static str,
    // Unknown until boot integration records the effective provider selection.
    reboot_required: Option<bool>,
    runtime: Option<RuntimeStatus>,
    startup_failures: u32,
    startup_blocked: bool,
}

fn enable_blockers(engine: bool, blocked: bool) -> Vec<&'static str> {
    let mut blockers = Vec::new();
    if !engine {
        blockers.push("engine_missing");
    }
    if blocked {
        blockers.push("startup_protection");
    }
    blockers
}

#[derive(Debug, Deserialize, Serialize)]
struct AbiStatus {
    supported: bool,
    daemon_running: bool,
    daemon_pid: i32,
    daemon_start_ticks: u64,
    zygote_pid: i32,
    zygote_start_ticks: u64,
    injected: bool,
    reason: String,
}

#[derive(Debug, Deserialize, Serialize)]
struct RuntimeStatus {
    schema_version: u32,
    boot_id: String,
    monitor_pid: i32,
    monitor_start_ticks: u64,
    ready: bool,
    tracing_state: u32,
    reason: String,
    abis: std::collections::BTreeMap<String, AbiStatus>,
}

fn start_ticks(stat: &str) -> Option<u64> {
    // comm can contain spaces and parentheses; field 3 follows its final ')'.
    stat.rsplit_once(')')?
        .1
        .split_whitespace()
        .nth(19)?
        .parse()
        .ok()
}

fn live_start_ticks(stat: &str) -> Option<u64> {
    let state = stat.rsplit_once(')')?.1.split_whitespace().next()?;
    // A zombie retains its PID/start time but cannot serve injection requests.
    if matches!(state, "Z" | "X" | "x") {
        return None;
    }
    start_ticks(stat)
}

fn same_process(proc: &Path, pid: i32, ticks: u64) -> bool {
    pid > 0
        && ticks > 0
        && fs::read_to_string(proc.join(pid.to_string()).join("stat"))
            .ok()
            .and_then(|stat| live_start_ticks(&stat))
            == Some(ticks)
}

fn read_runtime(path: &Path, proc: &Path) -> Result<Option<RuntimeStatus>> {
    let bytes = match fs::read(path) {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(None),
        Err(error) => return Err(error).context("Read FolkZygisk runtime status"),
    };
    let mut runtime: RuntimeStatus =
        serde_json::from_slice(&bytes).context("Invalid FolkZygisk runtime status")?;
    ensure!(
        runtime.schema_version == 1,
        "Unsupported FolkZygisk status schema"
    );
    let boot = fs::read_to_string(proc.join("sys/kernel/random/boot_id"))?;
    if runtime.boot_id != boot.trim()
        || !same_process(proc, runtime.monitor_pid, runtime.monitor_start_ticks)
    {
        return Ok(None);
    }
    ensure!(
        runtime.abis.len() == 2
            && runtime.abis.contains_key("64")
            && runtime.abis.contains_key("32"),
        "Invalid FolkZygisk ABI status"
    );
    for abi in runtime.abis.values_mut() {
        abi.daemon_running &= same_process(proc, abi.daemon_pid, abi.daemon_start_ticks);
        abi.injected &= abi.supported
            && abi.daemon_running
            && same_process(proc, abi.zygote_pid, abi.zygote_start_ticks);
    }
    Ok(Some(runtime))
}

fn injection_state(runtime: Option<&RuntimeStatus>) -> &'static str {
    let Some(runtime) = runtime else {
        return "unverified";
    };
    if !runtime.ready || runtime.tracing_state != 0 {
        return "failed";
    }
    let supported: Vec<_> = runtime.abis.values().filter(|abi| abi.supported).collect();
    if supported.is_empty() {
        return "starting";
    }
    if supported.iter().all(|abi| abi.injected) {
        return "injected";
    }
    if supported.iter().any(|abi| abi.injected) {
        return "partial";
    }
    if supported.iter().any(|abi| !abi.daemon_running) {
        return "failed";
    }
    "starting"
}

/// Recognize provider packages, not modules which merely use the Zygisk API.
///
/// Mirrors FolkPatch and drives two things: naming an external implementation in the UI, and the
/// built-in engine stepping aside at boot. It never gates module installation.
fn is_provider(id: &str) -> bool {
    // Zygisk Next, NeoZygisk and HSSkyBoy/NyaZygisk share the module ID zygisksu.
    matches!(
        id,
        "zygisksu" | "zygisknext" | "rezygisk" | "neozygisk" | "shirokozygisk" | "onyxzygisk"
    )
}

fn read_config(path: &Path) -> Result<Config> {
    match fs::read(path) {
        Ok(bytes) => serde_json::from_slice(&bytes).context("Invalid FolkZygisk configuration"),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(Config::default()),
        Err(error) => Err(error).context("Read FolkZygisk configuration"),
    }
}

fn active(path: &Path) -> bool {
    path.is_dir() && !path.join("disable").exists() && !path.join("remove").exists()
}

fn providers(modules: &Path, updates: &Path) -> Result<Vec<String>> {
    let mut ids = BTreeSet::new();
    for root in [modules, updates] {
        let entries = match fs::read_dir(root) {
            Ok(entries) => entries,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(error) => return Err(error).context("Discover Zygisk providers"),
        };
        for entry in entries {
            let entry = entry?;
            let Some(id) = entry.file_name().to_str().map(str::to_owned) else {
                continue;
            };
            if !is_provider(&id) {
                continue;
            }
            let installed = modules.join(&id);
            let pending = updates.join(&id);
            // Boot promotion inherits these markers from the installed copy.
            if installed.join("disable").exists() || installed.join("remove").exists() {
                continue;
            }
            let effective = if pending.is_dir() { pending } else { installed };
            if active(&effective) {
                ids.insert(id);
            }
        }
    }
    Ok(ids.into_iter().collect())
}

/// Serialize FolkZygisk configuration changes and boot-startup checks.
/// flock is released by closing the descriptor, including on error/unwind.
fn lock() -> Result<File> {
    fs::create_dir_all("/data/adb/ksu")?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(LOCK)?;
    // SAFETY: file owns a valid descriptor for the duration of this call.
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        return Err(std::io::Error::last_os_error())
            .context("FolkZygisk configuration is busy; retry after it finishes");
    }
    Ok(file)
}

fn engine_available(root: &Path) -> bool {
    let (monitor, daemon, library) = if cfg!(target_pointer_width = "64") {
        ("bin/zygisk-ptrace64", "bin/zygiskd64", "lib64/libzygisk.so")
    } else {
        ("bin/zygisk-ptrace32", "bin/zygiskd32", "lib/libzygisk.so")
    };
    [monitor, daemon].iter().all(|name| {
        fs::metadata(root.join(name))
            .is_ok_and(|metadata| metadata.is_file() && metadata.permissions().mode() & 0o111 != 0)
    }) && root.join(library).is_file()
}

fn write_config(path: &Path, config: &Config) -> Result<()> {
    write_json(path, config)
}

fn write_json(path: &Path, value: &impl Serialize) -> Result<()> {
    let parent = path.parent().context("Missing configuration directory")?;
    fs::create_dir_all(parent)?;
    let mut file = tempfile::NamedTempFile::new_in(parent)?;
    file.as_file()
        .set_permissions(fs::Permissions::from_mode(0o600))?;
    serde_json::to_writer(&mut file, value)?;
    file.write_all(b"\n")?;
    file.as_file().sync_all()?;
    file.persist(path)
        .context("Persist FolkZygisk configuration")?;
    File::open(parent)?.sync_all()?;
    Ok(())
}

pub fn run(command: &Command) -> Result<()> {
    let _lock = lock()?;
    match command {
        Command::Status { json } => {
            let runtime = read_runtime(Path::new(RUNTIME), Path::new("/proc"))?;
            let guard = load_guard(Path::new(BOOT_GUARD))?;
            let engine = engine_available(Path::new(ENGINE));
            let external = providers(Path::new(MODULES), Path::new(UPDATES))?;
            let blocked = guard.latched || guard.failures >= FAILURE_LIMIT;
            let blockers = enable_blockers(engine, blocked);
            let status = Status {
                schema_version: 1,
                enabled: read_config(Path::new(CONFIG))?.enabled,
                engine_available: engine,
                enable_blockers: blockers,
                external_providers: external,
                injection_state: injection_state(runtime.as_ref()),
                reboot_required: None,
                runtime,
                startup_failures: guard.failures,
                startup_blocked: blocked,
            };
            if *json {
                println!("{}", serde_json::to_string(&status)?);
            } else {
                println!("{}", serde_json::to_string_pretty(&status)?);
            }
        }
        Command::Enable => {
            let guard = load_guard(Path::new(BOOT_GUARD))?;
            let engine = engine_available(Path::new(ENGINE));
            let blocked = guard.latched || guard.failures >= FAILURE_LIMIT;
            let blockers = enable_blockers(engine, blocked);
            ensure!(
                blockers.is_empty(),
                "FOLKZYGISK_UNAVAILABLE: {}",
                blockers.join(", ")
            );
            write_config(Path::new(CONFIG), &Config { enabled: true })?;
            // The configuration is already persisted; the provider scan only picks the hint text,
            // so a scan failure must not turn a successful enable into a CLI error.
            match providers(Path::new(MODULES), Path::new(UPDATES)) {
                Ok(external) if !external.is_empty() => println!(
                    "FolkZygisk enabled, but external provider(s) {} take precedence; the built-in engine will step aside",
                    external.join(", ")
                ),
                Ok(_) => println!("FolkZygisk enabled for the next boot; reboot required"),
                Err(error) => {
                    log::warn!("Could not scan Zygisk providers: {error:#}");
                    println!("FolkZygisk enabled for the next boot; reboot required");
                }
            }
        }
        Command::Disable => {
            write_config(Path::new(CONFIG), &Config { enabled: false })?;
            println!("FolkZygisk disabled for the next boot");
        }
        Command::ResetFailures => {
            let mut guard = load_guard(Path::new(BOOT_GUARD))?;
            guard.failures = 0;
            guard.latched = false;
            // Recovery never permits another attempt during the current boot.
            let boot = fs::read_to_string("/proc/sys/kernel/random/boot_id")?;
            boot.trim().clone_into(&mut guard.boot_id);
            write_json(Path::new(BOOT_GUARD), &guard)?;
            println!("FolkZygisk startup protection reset for the next boot");
        }
        Command::InstallEngine => {
            ensure!(
                read_runtime(Path::new(RUNTIME), Path::new("/proc"))?.is_none(),
                "Cannot update a live FolkZygisk engine; disable and reboot first"
            );
            let boot = fs::read_to_string("/proc/sys/kernel/random/boot_id")?;
            ensure!(
                load_guard(Path::new(BOOT_GUARD))?.boot_id != boot.trim(),
                "Engine startup was attempted this boot; reboot before installing"
            );
            let files = crate::zygisk_assets::FILES
                .iter()
                .map(|name| {
                    let bytes = crate::assets::get_asset_data(&format!("folkzygisk/{name}"))?;
                    Ok(((*name).to_owned(), bytes.into_owned()))
                })
                .collect::<Result<Vec<_>>>()?;
            let version = crate::zygisk_assets::install(
                Path::new(ENGINE).parent().context("Engine parent")?,
                &files,
                |path| {
                    #[cfg(target_os = "android")]
                    crate::restorecon::restore_syscon(path)?;
                    #[cfg(not(target_os = "android"))]
                    let _ = path;
                    Ok(())
                },
            )?;
            println!(
                "FolkZygisk engine installed: {version}; provider selection unchanged, no injection started"
            );
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    #[test]
    fn runtime_cleanup_refuses_live_monitor_lock() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let owner = File::create(dir.path().join("monitor.lock"))?;
        assert_eq!(
            unsafe { libc::flock(owner.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) },
            0
        );
        fs::write(dir.path().join("state.json"), "keep")?;
        assert!(clear_stale_runtime(dir.path()).is_err());
        assert!(dir.path().join("state.json").exists());
        Ok(())
    }
    use super::*;

    #[test]
    fn activation_keeps_operational_protection() {
        assert!(enable_blockers(true, false).is_empty());
        assert_eq!(
            enable_blockers(false, true),
            vec!["engine_missing", "startup_protection"]
        );
    }

    #[test]
    fn boot_guard_counts_once_and_latches_after_three_failed_boots() -> Result<()> {
        let mut guard = BootGuard::default();
        for boot in ["one", "two", "three"] {
            assert!(guard.attempt(boot));
            let count = guard.failures;
            assert!(!guard.attempt(boot));
            assert_eq!(guard.failures, count);
        }
        assert!(!guard.attempt("four"));
        assert!(guard.latched);
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("guard.json");
        write_json(&path, &guard)?;
        assert!(load_guard(&path)?.latched);
        fs::write(&path, "broken")?;
        assert!(load_guard(&path).is_err());
        Ok(())
    }

    fn fake_process(proc: &Path, pid: i32, ticks: u64) -> Result<()> {
        fs::create_dir_all(proc.join(pid.to_string()))?;
        let fields = std::iter::once("S")
            .chain(std::iter::repeat_n("0", 18))
            .collect::<Vec<_>>()
            .join(" ");
        fs::write(
            proc.join(pid.to_string()).join("stat"),
            format!("{pid} (zygote (worker)) {fields} {ticks} 0\n"),
        )?;
        Ok(())
    }

    #[test]
    fn runtime_requires_current_boot_and_process_identity() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let proc = dir.path().join("proc");
        fs::create_dir_all(proc.join("sys/kernel/random"))?;
        fs::write(proc.join("sys/kernel/random/boot_id"), "current\n")?;
        fake_process(&proc, 10, 100)?;
        fake_process(&proc, 20, 200)?;
        fake_process(&proc, 30, 300)?;
        let path = dir.path().join("state.json");
        let mut value = serde_json::json!({
            "schema_version": 1, "boot_id": "current", "monitor_pid": 10,
            "monitor_start_ticks": 100, "ready": true, "tracing_state": 0, "reason": "",
            "abis": {
                "64": {"supported": true, "daemon_running": true, "daemon_pid": 20,
                    "daemon_start_ticks": 200, "zygote_pid": 30, "zygote_start_ticks": 300,
                    "injected": true, "reason": ""},
                "32": {"supported": false, "daemon_running": false, "daemon_pid": -1,
                    "daemon_start_ticks": 0, "zygote_pid": 0, "zygote_start_ticks": 0,
                    "injected": false, "reason": ""}
            }
        });
        fs::write(&path, serde_json::to_vec(&value)?)?;
        let runtime = read_runtime(&path, &proc)?;
        assert_eq!(injection_state(runtime.as_ref()), "injected");
        fake_process(&proc, 30, 301)?;
        assert_eq!(
            injection_state(read_runtime(&path, &proc)?.as_ref()),
            "starting"
        );
        fs::remove_file(proc.join("20/stat"))?;
        assert_eq!(
            injection_state(read_runtime(&path, &proc)?.as_ref()),
            "failed"
        );
        value["boot_id"] = serde_json::json!("old");
        fs::write(&path, serde_json::to_vec(&value)?)?;
        assert!(read_runtime(&path, &proc)?.is_none());
        value["boot_id"] = serde_json::json!("current");
        value["monitor_start_ticks"] = serde_json::json!(101);
        fs::write(&path, serde_json::to_vec(&value)?)?;
        assert!(read_runtime(&path, &proc)?.is_none());
        fs::write(&path, "broken")?;
        assert!(read_runtime(&path, &proc).is_err());
        Ok(())
    }

    #[test]
    fn stat_parser_handles_parentheses_and_rejects_truncation() {
        assert_eq!(start_ticks("1 (odd) name) S 0"), None);
        assert!(!same_process(Path::new("/proc"), 0, 1));
        assert!(!same_process(Path::new("/proc"), 1, 0));
    }

    #[test]
    fn zombies_do_not_validate_as_live_injection_processes() -> Result<()> {
        let dir = tempfile::tempdir()?;
        fake_process(dir.path(), 10, 100)?;
        assert!(same_process(dir.path(), 10, 100));
        let path = dir.path().join("10/stat");
        let stat = fs::read_to_string(&path)?;
        for state in ["Z", "X", "x"] {
            fs::write(&path, stat.replace(") S ", &format!(") {state} ")))?;
            assert!(!same_process(dir.path(), 10, 100));
        }
        Ok(())
    }

    #[test]
    fn recognizes_known_external_providers() {
        for id in [
            "zygisksu",
            "zygisknext",
            "rezygisk",
            "neozygisk",
            "shirokozygisk",
            "onyxzygisk",
        ] {
            assert!(is_provider(id));
        }
        for id in ["lsposed", "nyazygisk", "nyxzygisk", "zygisk_plugin"] {
            assert!(!is_provider(id));
        }
    }

    #[test]
    fn pending_updates_inherit_installed_markers() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let modules = dir.path().join("modules");
        let updates = dir.path().join("modules_update");
        fs::create_dir_all(modules.join("rezygisk"))?;
        fs::create_dir_all(updates.join("rezygisk"))?;
        assert_eq!(providers(&modules, &updates)?, vec!["rezygisk"]);
        for marker in ["disable", "remove"] {
            fs::write(modules.join("rezygisk").join(marker), "")?;
            assert!(providers(&modules, &updates)?.is_empty());
            fs::remove_file(modules.join("rezygisk").join(marker))?;
        }
        fs::write(updates.join("rezygisk/disable"), "")?;
        assert!(providers(&modules, &updates)?.is_empty());
        Ok(())
    }

    #[test]
    fn config_is_default_off_and_rejects_corruption() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("zygisk.json");
        assert!(!read_config(&path)?.enabled);
        write_config(&path, &Config { enabled: true })?;
        assert!(read_config(&path)?.enabled);
        fs::write(&path, "broken")?;
        assert!(read_config(&path).is_err());
        Ok(())
    }

    #[test]
    fn stale_runtime_artifacts_are_cleared_before_startup() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let runtime = dir.path().join("runtime");
        fs::create_dir_all(runtime.join("nested"))?;
        fs::write(runtime.join("state.json"), "{}")?;
        fs::write(runtime.join("monitor.lock"), "")?;
        fs::write(runtime.join("nested/leftover"), "")?;
        clear_stale_runtime(&runtime)?;
        assert_eq!(fs::read_dir(&runtime)?.count(), 1);
        assert!(runtime.join("monitor.lock").exists());
        Ok(())
    }

    #[test]
    fn missing_directories_do_not_imply_an_external_provider() -> Result<()> {
        let dir = tempfile::tempdir()?;
        assert!(providers(&dir.path().join("modules"), &dir.path().join("updates"))?.is_empty());
        assert!(!engine_available(dir.path()));
        Ok(())
    }

    #[test]
    fn pending_only_providers_are_discovered_but_plugins_are_not() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let modules = dir.path().join("modules");
        let updates = dir.path().join("updates");
        fs::create_dir_all(updates.join("zygisksu"))?;
        fs::create_dir_all(modules.join("lsposed/zygisk"))?;
        assert_eq!(providers(&modules, &updates)?, vec!["zygisksu"]);
        fs::write(updates.join("zygisksu/remove"), "")?;
        assert!(providers(&modules, &updates)?.is_empty());
        Ok(())
    }

    #[test]
    fn provider_results_are_unique_and_sorted() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let modules = dir.path().join("modules");
        let updates = dir.path().join("updates");
        for id in ["zygisksu", "rezygisk", "onyxzygisk"] {
            fs::create_dir_all(modules.join(id))?;
            fs::create_dir_all(updates.join(id))?;
        }
        assert_eq!(
            providers(&modules, &updates)?,
            vec!["onyxzygisk", "rezygisk", "zygisksu"]
        );
        Ok(())
    }
}
