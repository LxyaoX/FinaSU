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
        let unchanged =
            fs::read(&target).is_ok_and(|on_disk| on_disk.as_slice() == asset.data.as_ref());
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
        start()?;
    } else {
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

/// Live daemon processes, as `(abi, pid)`.
pub fn running() -> Vec<(u32, i32)> {
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
        let abi = match executable {
            "zygiskd64" => 64,
            "zygiskd32" => 32,
            _ => continue,
        };
        found.push((abi, pid));
    }
    found
}

/// Start a daemon for each installed ABI. Already-running daemons are left alone.
pub fn start() -> Result<()> {
    if !installed() {
        bail!("yukizygisk payload is not installed");
    }
    let live = running();
    let mut started = Vec::new();
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
            Ok(child) => {
                log::info!("yukizygisk: {name} started as pid {child}");
                started.push(child);
            }
            Err(error) => log::warn!("yukizygisk: {name} failed to start: {error:#}"),
        }
    }
    // Give the daemons a moment to settle: a daemon that exits immediately is a startup failure
    // worth reporting, not a success that silently leaves the device without injection.
    if !started.is_empty() {
        std::thread::sleep(Duration::from_millis(400));
        for pid in started {
            if !Path::new(&format!("/proc/{pid}")).exists() {
                log::warn!("yukizygisk: daemon {pid} exited during startup");
            }
        }
    }
    Ok(())
}

fn spawn_daemon(path: &Path) -> Result<i32> {
    use std::os::unix::process::CommandExt;
    use std::process::{Command, Stdio};
    let mut command = Command::new(path);
    command
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    // SAFETY: `pre_exec` runs in the forked child before `exec`; both calls are async-signal-safe.
    // The daemon must outlive init's exec-service cgroup, and a fresh session keeps it out of a
    // terminal's process group.
    unsafe {
        command.pre_exec(|| {
            if libc::setsid() == -1 {
                return Err(std::io::Error::last_os_error());
            }
            detach_from_service_cgroup()
        });
    }
    let child = command
        .spawn()
        .with_context(|| format!("spawn {}", path.display()))?;
    Ok(child.id() as i32)
}

/// Stop every daemon. Used by soft reboot and by an explicit restart.
pub fn stop() {
    let live = running();
    if live.is_empty() {
        return;
    }
    for (_, pid) in &live {
        // SAFETY: plain signal delivery to a pid we just discovered.
        unsafe {
            libc::kill(*pid, libc::SIGTERM);
        }
    }
    let deadline = Instant::now() + Duration::from_secs(2);
    while Instant::now() < deadline {
        if running().is_empty() {
            return;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    for (_, pid) in running() {
        // SAFETY: same as above; the daemon ignored SIGTERM for two seconds.
        unsafe {
            libc::kill(pid, libc::SIGKILL);
        }
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
    start()
}

/// `on services`: the daemons are started at post-fs-data, but a daemon that died in between has to
/// be brought back before any app is launched, otherwise the next zygote fork injects nothing.
pub fn ensure_running() -> Result<()> {
    if !enabled() || !installed() {
        return Ok(());
    }
    if !running().is_empty() {
        return Ok(());
    }
    log::warn!("yukizygisk: no daemon alive at services, restarting");
    start()
}

/// Soft reboot tears down userspace while the kernel module stays loaded: the daemons have to go so
/// the new cycle starts them against the new zygote.
pub fn prepare_soft_reboot() {
    stop()
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
        "lastError": last_error(),
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
            }
        }
        Command::Restart => {
            stop();
            start()?;
        }
        Command::Enable => set_enabled(true)?,
        Command::Disable => set_enabled(false)?,
    };
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
