//! Folk Mount: built-in module mounting.
//!
//! Only two modes are offered: `nomount`, which injects through the NoMount VFS
//! driver, and `off`. The magic-mount engine (tmpfs staging plus bind mounts,
//! its planner and the metamodule mount provider) has been removed: it was the
//! only thing the retired `auto`/`builtin`/`metamodule` modes ever selected, and
//! keeping it as a fallback meant two injection paths to reason about.
//!
//! A device whose NoMount driver cannot be loaded therefore injects nothing this
//! boot and says so, in the runtime record and in `ksud mount status`, instead
//! of quietly mounting through a path nobody wants any more.
//!
//! What is left here is the configuration file, the mode decision, the per-boot
//! runtime record, the guards that stop a boot from repeating a step that killed
//! the previous one, and the `ksud mount status` / `set-mode` protocol.

use std::fmt::Write as _;
use std::fs;
use std::io::Write;
use std::os::fd::AsRawFd;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use log::{info, warn};
use rustix::fs::{FlockOperation, flock};
use serde_json::json;

use crate::defs;
use crate::mount_guard;

/// User-visible mount mode stored in [`defs::FOLK_MOUNT_CONFIG`].
///
/// `auto`, `builtin` and `metamodule` were removed: every one of them ended in
/// the magic-mount engine, and the VFS driver is what actually injects modules
/// now. [`MountMode::parse`] still accepts the old tokens so a device upgrading
/// from an older build migrates instead of failing.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MountMode {
    /// Inject through the NoMount VFS driver (falls back to the built-in engine
    /// when that driver is not installed).
    Nomount,
    /// Inject nothing: modules stay inert until a mode is chosen.
    Off,
}

impl MountMode {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Nomount => "nomount",
            Self::Off => "off",
        }
    }

    /// Parse a config/CLI token; surrounding whitespace is ignored.
    ///
    /// The retired `auto` / `builtin` / `metamodule` tokens resolve to
    /// [`MountMode::Nomount`]: an old configuration file, or an old manager that
    /// still sends one of them, must not leave the device unmounted or on an
    /// engine that can no longer be chosen.
    #[must_use]
    pub fn parse(value: &str) -> Option<Self> {
        match value.trim() {
            "nomount" | "auto" | "builtin" | "metamodule" => Some(Self::Nomount),
            "off" => Some(Self::Off),
            _ => None,
        }
    }
}

/// What performed this boot's module injection.
///
/// Only two values can be written now. `builtin` and `metamodule` are accepted
/// when reading a record left by an older build, but nothing produces them.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Provider {
    Nomount,
    /// Nothing was injected this boot.
    None,
}

impl Provider {
    #[must_use]
    pub const fn as_str(self) -> &'static str {
        match self {
            Self::Nomount => "nomount",
            Self::None => "none",
        }
    }
}

/// Result of a mount attempt.
#[must_use]
#[derive(Debug, Clone)]
pub struct MountOutcome {
    pub provider: Provider,
    /// Final, published targets (direct binds and moved tmpfs roots).
    pub target_count: usize,
    /// Nodes the planner marked unoverlayable and the executor skipped.
    pub skipped_count: usize,
    /// True when rollback or staging cleanup could not undo every mount.
    pub partial: bool,
    /// Mounts that remain because they could not be undone.
    pub residue: Vec<PathBuf>,
    /// True when the provider ran but published nothing (or had no script).
    pub noop: bool,
    /// Provider-specific degradations that did not stop the injection.
    pub warnings: Vec<String>,
}

impl MountOutcome {
    pub const fn empty(provider: Provider) -> Self {
        Self {
            provider,
            target_count: 0,
            skipped_count: 0,
            partial: false,
            residue: Vec::new(),
            noop: true,
            warnings: Vec::new(),
        }
    }

    fn failed(provider: Provider) -> Self {
        Self {
            noop: false,
            ..Self::empty(provider)
        }
    }
}

/// Per-boot runtime record used by `ksud mount status` and reentrancy checks.
#[derive(Debug, Clone, Default)]
struct RuntimeRecord {
    boot_id: String,
    generation: u64,
    provider: Option<String>,
    result: String,
    target_count: Option<usize>,
    error: Option<String>,
    /// Provider-specific degradation that did not stop the injection.
    warning: Option<String>,
}

/// Read the configured mount mode from [`defs::FOLK_MOUNT_CONFIG`].
///
/// A missing file selects [`MountMode::Nomount`] and records it, so the default
/// applies once. A file holding a retired mode (`auto`/`builtin`/`metamodule`)
/// is migrated in place: the returned mode is what the next boot uses, and the
/// file is rewritten so the manager reads the same value. An unrecognised value
/// falls back to [`MountMode::Nomount`] with a warning. Other I/O errors are
/// returned so an unreadable configuration never silently selects a provider.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
pub fn read_mount_mode() -> Result<MountMode> {
    read_mount_mode_at(Path::new(defs::FOLK_MOUNT_CONFIG))
}

/// [`read_mount_mode`], against a path the caller owns.
fn read_mount_mode_at(path: &Path) -> Result<MountMode> {
    match fs::read_to_string(path) {
        Ok(content) => {
            let stored = content.trim();
            let Some(mode) = MountMode::parse(stored) else {
                warn!(
                    "invalid mount mode {stored:?} in {}, falling back to {}",
                    path.display(),
                    MountMode::Nomount.as_str()
                );
                persist_migration(path, MountMode::Nomount);
                return Ok(MountMode::Nomount);
            };
            // A retired token parses to a different spelling: rewrite the file so
            // the stored setting and the next boot agree.
            if mode.as_str() != stored {
                warn!(
                    "mount mode {stored:?} in {} is no longer offered, migrating to {}",
                    path.display(),
                    mode.as_str()
                );
                persist_migration(path, mode);
            }
            Ok(mode)
        }
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
            let mode = MountMode::Nomount;
            // Record the decision so the default only applies once.
            persist_migration(path, mode);
            Ok(mode)
        }
        Err(e) => Err(e).with_context(|| format!("failed to read {}", path.display())),
    }
}

/// Best effort rewrite after a default or a migration: an unwritable path must
/// not fail the boot, because the mode in hand is already the right one.
fn persist_migration(path: &Path, mode: MountMode) {
    if let Err(e) = write_mount_mode_at(path, mode) {
        warn!("folk mount: could not write {}: {e:#}", path.display());
    }
}

/// Write `contents` to `path` atomically: temp file in the same directory,
/// fsync, rename, then fsync the directory.
fn write_atomic(path: &Path, contents: &[u8]) -> Result<()> {
    let dir = path
        .parent()
        .context("atomic write target has no parent directory")?;
    fs::create_dir_all(dir).with_context(|| format!("failed to create {}", dir.display()))?;

    let mut tmp = tempfile::Builder::new()
        .prefix(".folk_mount.")
        .tempfile_in(dir)
        .with_context(|| format!("failed to create temp file in {}", dir.display()))?;
    tmp.write_all(contents)
        .context("failed to write temporary file")?;
    tmp.as_file()
        .sync_all()
        .context("failed to sync temporary file")?;
    tmp.persist(path)
        .with_context(|| format!("failed to persist {}", path.display()))?;

    fs::File::open(dir)
        .and_then(|handle| handle.sync_all())
        .with_context(|| format!("failed to sync {}", dir.display()))?;
    Ok(())
}

/// Atomically persist `mode` to [`defs::FOLK_MOUNT_CONFIG`].
///
/// This only affects the next boot; it never triggers a mount or unmount.
///
/// # Errors
/// Returns an error when the directory cannot be created or the file cannot be
/// written, synced and renamed.
pub fn write_mount_mode(mode: MountMode) -> Result<()> {
    write_mount_mode_at(Path::new(defs::FOLK_MOUNT_CONFIG), mode)
}

/// [`write_mount_mode`], against a path the caller owns.
fn write_mount_mode_at(path: &Path, mode: MountMode) -> Result<()> {
    write_atomic(path, format!("{}\n", mode.as_str()).as_bytes())
}

/// Return `(id, enabled)` for the installed metamodule, if any.
///
/// Nothing selects a metamodule as a mount backend any more; this only feeds
/// the `metamodule_id` / `metamodule_enabled` fields of `ksud mount status` and
/// the install-time warning in `module.rs`.
fn metamodule_state() -> (Option<String>, bool) {
    let Some(path) = crate::metamodule::get_metamodule_path() else {
        return (None, false);
    };
    let enabled =
        !path.join(defs::DISABLE_FILE_NAME).exists() && !path.join(defs::REMOVE_FILE_NAME).exists();
    let id = path
        .file_name()
        .and_then(|name| name.to_str())
        .map(ToString::to_string);
    (id, enabled)
}

const fn provider_for(mode: MountMode) -> Provider {
    match mode {
        MountMode::Nomount => Provider::Nomount,
        MountMode::Off => Provider::None,
    }
}

/// Resolve which provider the next boot should use.
///
/// # Errors
/// Returns an error when the configuration file exists but cannot be read.
pub fn resolve_provider() -> Result<Provider> {
    Ok(provider_for(read_mount_mode()?))
}

// ---------------------------------------------------------------------------
// Runtime record
// ---------------------------------------------------------------------------

fn current_boot_id() -> Option<String> {
    fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .ok()
        .map(|value| value.trim().to_string())
}

fn sanitize(value: &str) -> String {
    value.replace(['\n', '\r'], " ")
}

fn write_runtime(record: &RuntimeRecord) -> Result<()> {
    let contents = format!(
        "boot_id={}\ngeneration={}\nprovider={}\nresult={}\ntarget_count={}\nerror={}\nwarning={}\n",
        record.boot_id,
        record.generation,
        record.provider.as_deref().unwrap_or(""),
        record.result,
        record
            .target_count
            .map_or_else(String::new, |count| count.to_string()),
        record.error.as_deref().map_or_else(String::new, sanitize),
        record.warning.as_deref().map_or_else(String::new, sanitize),
    );
    write_atomic(Path::new(defs::FOLK_MOUNT_RUNTIME), contents.as_bytes())
}

fn read_runtime() -> Option<RuntimeRecord> {
    let content = fs::read_to_string(defs::FOLK_MOUNT_RUNTIME).ok()?;
    let mut record = RuntimeRecord::default();
    for line in content.lines() {
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        match key {
            "boot_id" => record.boot_id = value.to_string(),
            "generation" => record.generation = value.parse().unwrap_or(0),
            "provider" => record.provider = (!value.is_empty()).then(|| value.to_string()),
            "result" => record.result = value.to_string(),
            "target_count" => record.target_count = value.parse().ok(),
            "error" => record.error = (!value.is_empty()).then(|| value.to_string()),
            "warning" => record.warning = (!value.is_empty()).then(|| value.to_string()),
            _ => {}
        }
    }
    Some(record)
}

/// Best-effort record that this boot did not mount modules.
fn record_skipped() {
    let epoch = crate::startup_epoch::current_or_create().unwrap_or_default();
    let record = RuntimeRecord {
        boot_id: epoch.boot_id,
        generation: epoch.generation,
        provider: None,
        result: "skipped".to_string(),
        target_count: None,
        error: None,
        warning: None,
    };
    if let Err(e) = write_runtime(&record) {
        warn!("folk mount: failed to record skipped state: {e:#}");
    }
}

/// Refuse a second run in the same boot and diagnose a `mounting` residue
/// without trying to clean unknown old mounts.
fn guard_reentrancy(boot_id: &str, generation: u64, provider: Provider) -> Result<()> {
    let Some(record) = read_runtime() else {
        return Ok(());
    };
    if boot_id.is_empty() || record.boot_id != boot_id {
        return Ok(());
    }
    if record.generation != generation {
        if record.result == "mounting" {
            bail!(
                "Folk Mount left a 'mounting' record for boot {boot_id}; refusing to repeat the incomplete step"
            );
        }
        return Ok(());
    }
    if record.result == "mounting" {
        bail!(
            "Folk Mount left a 'mounting' record for boot {boot_id}; refusing to mount again. \
             Diagnose the residue manually (work dir / umount list) before rebooting"
        );
    }
    bail!(
        "Folk Mount already ran this boot (provider={provider}); refusing to mount again",
        provider = provider.as_str()
    );
}

/// Hold an exclusive lock on [`defs::FOLK_MOUNT_LOCK`] for the duration of one
/// mount attempt, preventing concurrent runs.
struct MountLock {
    file: fs::File,
}

impl MountLock {
    fn acquire() -> Result<Self> {
        fs::create_dir_all(defs::WORKING_DIR)
            .with_context(|| format!("failed to create {}", defs::WORKING_DIR))?;
        let file = fs::OpenOptions::new()
            .create(true)
            .truncate(false)
            .write(true)
            .open(defs::FOLK_MOUNT_LOCK)
            .with_context(|| format!("failed to open {}", defs::FOLK_MOUNT_LOCK))?;
        flock(&file, FlockOperation::NonBlockingLockExclusive)
            .context("another Folk Mount attempt is already running")?;
        Ok(Self { file })
    }
}

// ---------------------------------------------------------------------------
// Helpers shared with the NoMount planner
// ---------------------------------------------------------------------------

/// Whether `path` carries the overlayfs "opaque" xattr, i.e. its upper layer
/// replaces the lower entries instead of merging with them. The NoMount planner
/// asks this before it decides between merging and replacing a directory, so it
/// lives here even though nothing in this module mounts anything any more.
pub fn read_opaque(path: &Path) -> Result<bool> {
    const REPLACE_DIR_XATTR: &str = "trusted.overlay.opaque";
    match extattr::lgetxattr(path, REPLACE_DIR_XATTR) {
        Ok(value) => Ok(String::from_utf8_lossy(&value) == "y"),
        // `extattr` reports `errno::Errno`; ENODATA/ENOATTR means "absent".
        Err(e) if e.0 == libc::ENODATA => Ok(false),
        Err(e) => Err(e).with_context(|| format!("read {REPLACE_DIR_XATTR} of {}", path.display())),
    }
}

/// Run the built-in NoMount backend.
///
/// The driver is probed first and loaded from the embedded assets when the
/// kernel has no support. A load is never treated as success on its own: the
/// API has to answer afterwards, because the loader rewrites vermagic and
/// relocates symbols and can therefore install a module that does not work.
fn run_nomount() -> Result<MountOutcome> {
    // An installed module that drives NoMount itself owns injection: running our
    // own rules as well would make the two writers race on the same paths.
    if let Some(owner) = crate::nomount::installed_driver_module() {
        warn!("nomount: module {owner} owns NoMount injection; deferring to it");
        return defer_to_installed_module(&owner);
    }
    let result = crate::nomount::apply_current_plan()?;
    for warning in &result.warnings {
        warn!("nomount: {warning}");
    }
    Ok(MountOutcome {
        provider: Provider::Nomount,
        target_count: result.applied,
        skipped_count: result.skipped,
        partial: false,
        residue: Vec::new(),
        noop: result.applied == 0,
        warnings: result.warnings,
    })
}

/// Let an installed NoMount module drive injection instead of us.
///
/// Upstream distributes NoMount as a metamodule whose own `metamount.sh` loads
/// the driver and applies its rules; running our plan on top would make the two
/// writers race on the same paths, so the module keeps ownership and this boot
/// only records that it stepped aside.
///
/// # Errors
/// Returns an error when the module's mount script fails.
fn defer_to_installed_module(owner: &str) -> Result<MountOutcome> {
    crate::metamodule::exec_mount_script(defs::MODULE_DIR)?;
    let mut outcome = MountOutcome::empty(Provider::Nomount);
    outcome.noop = true;
    outcome.warnings.push(format!(
        "Deferred to the installed NoMount module {owner} (it drives the same driver)"
    ));
    Ok(outcome)
}

enum ProviderRun {
    Ok(MountOutcome),
    Failed {
        outcome: MountOutcome,
        error: anyhow::Error,
    },
}

fn run_provider(provider: Provider) -> ProviderRun {
    match provider {
        Provider::Nomount => match run_nomount() {
            Ok(outcome) => ProviderRun::Ok(outcome),
            Err(error) => ProviderRun::Failed {
                outcome: MountOutcome::failed(Provider::Nomount),
                error,
            },
        },
        // Handled before this point: injection is disabled for this boot.
        Provider::None => ProviderRun::Ok(MountOutcome::empty(Provider::None)),
    }
}

/// Execute the provider selected by [`resolve_provider`].
///
/// Used by both cold boot (`on_post_fs_data`) and late-load. Guards against
/// concurrent runs and a second run in the same boot, validates the UAPI and
/// rejects Magisk/safe mode.
///
/// # Errors
/// Returns an error when the configuration cannot be read, the environment
/// checks fail, or the selected provider fails.
pub fn mount_selected_provider() -> Result<MountOutcome> {
    if crate::utils::has_magisk() {
        warn!("folk mount: Magisk detected, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::None));
    }
    if crate::utils::is_safe_mode() {
        warn!("folk mount: safe mode, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::None));
    }
    if let Err(e) = crate::ksucalls::ensure_uapi_version_matched() {
        warn!("folk mount: {e:#}, skip module mount");
        record_skipped();
        return Ok(MountOutcome::empty(Provider::None));
    }

    crate::nomount::preserve_legacy_load_guard()
        .context("Folk Mount refused to overwrite an unfinished NoMount load record")?;
    let provider = resolve_provider()?;
    let epoch = crate::startup_epoch::current_or_create()?;
    if provider == Provider::None {
        info!("folk mount: mounting is disabled (mode=off)");
        finish_runtime(&epoch, provider, "disabled", None, None, None, None);
        return Ok(MountOutcome::empty(Provider::None));
    }

    let boot_id = epoch.boot_id.clone();

    // Driver bootstrap runs before the rules and reports on its own: without a
    // second engine there is nothing to fall back to, so a driver that will not
    // load has to fail this boot loudly instead of being papered over.
    if crate::nomount::installed_driver_module().is_none() {
        match crate::nomount::ensure_driver() {
            Ok(probe) => {
                info!(
                    "nomount: driver ready before injection (version {})",
                    probe.version.as_deref().unwrap_or("unknown")
                );
            }
            Err(error) => {
                let note =
                    format!("NoMount driver could not be prepared ({error:#}); nothing injected");
                warn!("folk mount: {note}");
                finish_runtime(
                    &epoch,
                    provider,
                    "failed",
                    None,
                    Some(format!("{error:#}")),
                    Some(note),
                    Some(provider),
                );
                return Ok(MountOutcome::empty(Provider::None));
            }
        }
    }

    // A step that stopped an earlier boot does not get to run again on its own:
    // the boot would stop the same way, and nothing can take its place now.
    if let Some(record) = mount_guard::read(inflight_path())
        && mount_guard::died(inflight_path())
        && record.provider == provider.as_str()
    {
        let note = format!(
            "the {} never finished, so this boot injects nothing",
            record.describe()
        );
        warn!("folk mount: {note}");
        finish_runtime(
            &epoch,
            provider,
            "skipped",
            None,
            None,
            Some(note),
            Some(provider),
        );
        return Ok(MountOutcome::empty(Provider::None));
    }

    let lock = MountLock::acquire()?;
    guard_reentrancy(&boot_id, epoch.generation, provider)?;

    write_runtime(&RuntimeRecord {
        boot_id: boot_id.clone(),
        generation: epoch.generation,
        provider: Some(provider.as_str().to_string()),
        result: "mounting".to_string(),
        target_count: None,
        error: None,
        warning: None,
    })?;

    // Written before the provider runs and cleared when the boot completes: a
    // provider that breaks path resolution never gets to clear it, and the next
    // boot reads it back (see `on_boot_completed` in `init_event`).
    if let Err(e) = mount_guard::begin(
        inflight_path(),
        mount_guard::Stage::Inject,
        provider.as_str(),
        &format!("mode={}", provider.as_str()),
        &boot_id,
        epoch.generation,
    ) {
        warn!("folk mount: could not record the injection step: {e:#}");
    }

    // Armed by hand for a device that keeps failing to boot: a helper outlives
    // this boot stage and dumps what the kernel was blocked on (see
    // `mount_capture`).
    if crate::mount_capture::enabled().is_some() {
        crate::mount_capture::spawn(lock.file.as_raw_fd());
    }
    let run = run_provider(provider);

    match run {
        ProviderRun::Ok(outcome) => {
            let result = if outcome.noop { "noop" } else { "mounted" };
            log::debug!(
                "folk mount: provider={} targets={} skipped={}",
                outcome.provider.as_str(),
                outcome.target_count,
                outcome.skipped_count
            );
            finish_runtime(
                &epoch,
                provider,
                result,
                Some(outcome.target_count),
                None,
                outcome.warnings.first().cloned(),
                Some(provider),
            );
            Ok(outcome)
        }
        ProviderRun::Failed { outcome, error } => {
            let result = if outcome.partial { "partial" } else { "failed" };
            warn!(
                "folk mount: provider={} {} (targets={}, skipped={}, residue={:?}): {error:#}",
                outcome.provider.as_str(),
                result,
                outcome.target_count,
                outcome.skipped_count,
                outcome.residue
            );
            finish_runtime(
                &epoch,
                provider,
                result,
                Some(outcome.target_count),
                Some(format!("{error:#}")),
                None,
                Some(provider),
            );
            Err(error)
        }
    }
}

/// Where the in-flight step record lives on a device.
fn inflight_path() -> &'static Path {
    Path::new(defs::MOUNT_INFLIGHT)
}

fn finish_runtime(
    epoch: &crate::startup_epoch::StartupEpoch,
    provider: Provider,
    result: &str,
    target_count: Option<usize>,
    error: Option<String>,
    warning: Option<String>,
    configured: Option<Provider>,
) {
    let record = RuntimeRecord {
        boot_id: epoch.boot_id.clone(),
        generation: epoch.generation,
        provider: Some(provider.as_str().to_string()),
        result: result.to_string(),
        target_count,
        error,
        warning,
    };
    if let Err(e) = write_runtime(&record) {
        warn!("folk mount: failed to record runtime state: {e:#}");
    }
    log_mount(&record, configured);
}

/// Append one block to the shared mount log, so a bug report shows which
/// provider ran, what it published and why it degraded.
fn log_mount(record: &RuntimeRecord, configured: Option<Provider>) {
    let mut body = format!(
        "--- folk mount boot={} generation={} stage=inject provider={} result={}",
        record.boot_id,
        record.generation,
        record.provider.as_deref().unwrap_or("none"),
        record.result
    );
    if let Some(configured) = configured {
        let _ = write!(body, " configured={}", configured.as_str());
    }
    let _ = writeln!(body);
    if let Some(count) = record.target_count {
        let _ = writeln!(body, "target_count={count}");
    }
    if let Some(error) = &record.error {
        let _ = writeln!(body, "error={error}");
    }
    if let Some(warning) = &record.warning {
        let _ = writeln!(body, "warning={warning}");
    }
    if let Err(e) =
        mount_guard::append_log(Path::new(defs::MOUNT_LOG), &body, defs::MOUNT_LOG_MAX_BYTES)
    {
        warn!("folk mount: could not write the mount log: {e:#}");
    }
}

// ---------------------------------------------------------------------------
// Status
// ---------------------------------------------------------------------------

/// Print the current Folk Mount status.
///
/// By default this emits stable `key=value` lines; with `json` it emits a single
/// JSON object whose `schema_version` is `1`. Only protocol data is written to
/// stdout; diagnostics go to stderr/log.
///
/// # Errors
/// Returns an error when writing to stdout fails.
pub fn print_status(json: bool) -> Result<()> {
    let (metamodule_id, metamodule_enabled) = metamodule_state();
    let (configured_mode, config_error) = match read_mount_mode() {
        Ok(mode) => (Some(mode), None),
        Err(e) => (None, Some(format!("{e:#}"))),
    };
    let next_provider = configured_mode.map(provider_for);

    let boot_id = current_boot_id();
    let runtime = read_runtime();
    let (boot_id, boot_provider, boot_result, target_count, runtime_error, boot_warning) =
        match (&runtime, &boot_id) {
            (Some(record), Some(current)) if &record.boot_id == current => (
                Some(record.boot_id.clone()),
                record.provider.clone(),
                if record.result.is_empty() {
                    "unknown".to_string()
                } else {
                    record.result.clone()
                },
                record.target_count,
                record.error.clone(),
                record.warning.clone(),
            ),
            _ => (None, None, "unknown".to_string(), None, None, None),
        };
    let error = config_error.or(runtime_error);

    let stdout = std::io::stdout();
    let mut out = stdout.lock();
    if json {
        writeln!(
            out,
            "{}",
            json!({
                "schema_version": 2,
                "configured_mode": configured_mode.map(MountMode::as_str),
                "next_provider": next_provider.map(Provider::as_str),
                "metamodule_id": metamodule_id,
                "metamodule_enabled": metamodule_enabled,
                "boot_id": boot_id,
                "boot_provider": boot_provider,
                "boot_result": boot_result,
                "target_count": target_count,
                "boot_warning": boot_warning,
                "error": error,
            })
        )
        .context("failed to write mount status")?;
    } else {
        writeln!(out, "schema_version=2").context("failed to write mount status")?;
        writeln!(
            out,
            "configured_mode={}",
            configured_mode.map_or("", MountMode::as_str)
        )
        .context("failed to write mount status")?;
        writeln!(
            out,
            "next_provider={}",
            next_provider.map_or("", Provider::as_str)
        )
        .context("failed to write mount status")?;
        writeln!(
            out,
            "metamodule_id={}",
            metamodule_id.as_deref().unwrap_or("")
        )
        .context("failed to write mount status")?;
        writeln!(out, "metamodule_enabled={metamodule_enabled}")
            .context("failed to write mount status")?;
        writeln!(out, "boot_id={}", boot_id.as_deref().unwrap_or(""))
            .context("failed to write mount status")?;
        writeln!(
            out,
            "boot_provider={}",
            boot_provider.as_deref().unwrap_or("")
        )
        .context("failed to write mount status")?;
        writeln!(out, "boot_result={boot_result}").context("failed to write mount status")?;
        writeln!(
            out,
            "target_count={}",
            target_count.map_or_else(String::new, |count| count.to_string())
        )
        .context("failed to write mount status")?;
        writeln!(
            out,
            "boot_warning={}",
            boot_warning.as_deref().unwrap_or("")
        )
        .context("failed to write mount status")?;
        writeln!(out, "error={}", error.as_deref().unwrap_or(""))
            .context("failed to write mount status")?;
    }
    out.flush().context("failed to flush mount status")?;
    Ok(())
}

/// Persist a new mount mode from a CLI token.
///
/// # Errors
/// Returns an error for an unknown mode token or a failed atomic write.
pub fn set_mode(mode: &str) -> Result<()> {
    let Some(parsed) = MountMode::parse(mode) else {
        bail!("invalid mount mode {mode:?}; expected one of: nomount, off");
    };
    // Asking for NoMount by hand is also the retry that the guards are waiting
    // for, so a record of a step that stopped an earlier boot must not outlive
    // the request.
    write_mount_mode(parsed)?;
    if parsed == MountMode::Nomount {
        crate::nomount::forget_inflight()?;
    }
    Ok(())
}
