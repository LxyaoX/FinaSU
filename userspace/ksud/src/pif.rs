//! PlayIntegrityFix, embedded in ksud and installed beside the YukiZygisk payload.
//!
//! Upstream ships as a Magisk/KernelSU module, and it cannot simply be unpacked somewhere quiet: a
//! Zygisk implementation discovers modules by walking `/data/adb/modules` for `zygisk/<abi>.so`, and
//! the library then opens `/data/adb/modules/playintegrityfix/{module.prop,pif.prop,classes.dex}` by
//! absolute path in the process it just hooked. No argument or environment variable moves those
//! paths.
//!
//! So the payload lives under [ROOT] - the manager's own directory, refreshed on every boot the same
//! way the engine's libraries are - and that directory is linked into `/data/adb/modules` as a
//! symbolic link. The link is what makes the hardcoded paths resolve while keeping the thing out of
//! the module list: `foreach_module`, NoMount's planner and `next_boot_modules` all decide "is this
//! a module directory?" with `symlink_metadata`, which reports a link as a link, while zygiskd only
//! looks for `zygisk/<abi>.so` behind the path and loads it like any other module.
//!
//! What a module would do through its own scripts, ksud does instead: [install] runs from
//! post-fs-data (a ksud update is how the payload reaches the device), and the two stage scripts
//! travel with it and are executed by [run_stage] at the stages they are named after.

use anyhow::{Context, Result, bail};
use const_format::concatcp;
use log::{info, warn};
use std::fs;
use std::os::unix::fs::{PermissionsExt, symlink};
use std::path::Path;

use crate::module::ScriptWait;

/// Upstream release carried in the assets; keep in sync with the payload and its NOTICE.
pub const VERSION: &str = "v4.9-inject-s";

/// The module id. It is not a free choice: the library spells this name in the paths it opens, and
/// zygiskd reports it as the module's name.
pub const ID: &str = "playintegrityfix";

/// Where the payload is installed. Inside the engine's library directory so one `restorecon` covers
/// both, and so a zygote that can search the engine's directory can search this one too.
pub const ROOT: &str = concatcp!(crate::yukizygisk::LIB_DIR, "/builtin/", ID);

/// The path the library insists on. A symbolic link to [ROOT].
pub const LINK: &str = concatcp!(crate::defs::MODULE_DIR, ID);

/// The file the page edits.
pub const PROP: &str = concatcp!(ROOT, "/pif.prop");

/// Present means "installed but not loaded"; zygiskd skips a module that carries it.
pub const DISABLE: &str = concatcp!(ROOT, "/disable");

/// Present means "this module was just written".
///
/// Upstream treats the flag as an update in progress and skips its `module.prop` checksum check
/// while it exists - which is also the only read-write open the library performs on this tree. The
/// payload here is ksud's and is rewritten on every boot, so the checksum can never be trusted to
/// stay in step, and a zygote that may only read `ksu_file` would fail that open outright.
pub const UPDATE_MARKER: &str = concatcp!(ROOT, "/update");

const PROP_FILE: &str = "pif.prop";

/// Payload files: the asset name under `bin/<arch>/pif`, and where it goes inside [ROOT].
const PAYLOAD: [(&str, &str); 7] = [
    ("module.prop", "module.prop"),
    ("classes.dex", "classes.dex"),
    ("arm64-v8a.so", "zygisk/arm64-v8a.so"),
    ("armeabi-v7a.so", "zygisk/armeabi-v7a.so"),
    ("common_func.sh", "common_func.sh"),
    ("post-fs-data.sh", "post-fs-data.sh"),
    ("service.sh", "service.sh"),
];

/// What `/data/adb/modules/playintegrityfix` currently is.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LinkState {
    /// The path does not exist; zygiskd never sees the module.
    Missing,
    /// The path is our link; zygiskd loads the payload through it.
    Linked,
    /// A real directory is in the way - an upstream install, or the module an earlier build wrote.
    /// Nothing is overwritten; the page offers to adopt it.
    Occupied,
}

/// Whether the user left the module on. On unless the marker exists.
pub fn enabled() -> bool {
    !Path::new(DISABLE).exists()
}

/// Install the payload and make the library's paths resolve.
///
/// Runs on every boot: a ksud update carries a new payload, and a half-written install has to heal
/// itself. `pif.prop` is only written when it is missing - it holds the identity the user chose.
pub fn install() -> Result<()> {
    install_payload()?;
    if link()? == LinkState::Occupied {
        warn!("pif: {LINK} is a real directory, leaving the payload unlinked");
    }
    Ok(())
}

/// Turn an upstream install into the built-in one: keep its `pif.prop`, drop the directory, link.
///
/// Only called when the user asks for it. The properties the upstream module sets are the same ones
/// this payload sets, and its `pif.prop` is where its identity lives, so nothing is lost - but that
/// directory is someone else's until they say otherwise, hence a separate command.
pub fn adopt() -> Result<()> {
    let link = Path::new(LINK);
    match fs::symlink_metadata(link) {
        Ok(meta) if meta.file_type().is_symlink() => return install(),
        Ok(_) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return install(),
        Err(error) => return Err(error).with_context(|| format!("stat {LINK}")),
    }

    let kept = fs::read(link.join(PROP_FILE)).ok();
    // Upstream's uninstall.sh is the one thing in that tree that knows how to put back the
    // properties it changed; a failure there must not stop the adoption.
    let uninstaller = link.join("uninstall.sh");
    if uninstaller.exists()
        && let Err(error) = crate::module::exec_script(&uninstaller, ScriptWait::NoWait)
    {
        warn!("pif: upstream uninstall.sh failed: {error:#}");
    }
    fs::remove_dir_all(link).with_context(|| format!("remove {LINK}"))?;

    install_payload()?;
    let prop = Path::new(ROOT).join(PROP_FILE);
    match kept {
        Some(bytes) if !bytes.is_empty() => {
            fs::write(&prop, bytes).with_context(|| format!("restore {PROP}"))?;
            let _ = fs::set_permissions(&prop, fs::Permissions::from_mode(0o644));
        }
        Some(_) => {}
        None => {
            if !prop.exists() {
                write_payload(PROP_FILE, &prop)?;
            }
        }
    }
    link()?;
    info!("pif: adopted the upstream install");
    Ok(())
}

/// Run one of the payload's stage scripts, if the module is installed, linked and on.
///
/// An occupied link means upstream's module is the one running: its own script is executed by the
/// module stage like any other module's, and running ours on top would apply the same properties
/// twice.
pub fn run_stage(stage: &str, wait: ScriptWait) -> Result<()> {
    if !enabled() {
        info!("pif: disabled, skip {stage}");
        return Ok(());
    }
    if link_state()? != LinkState::Linked {
        return Ok(());
    }
    let script = Path::new(ROOT).join(format!("{stage}.sh"));
    if !script.exists() {
        return Ok(());
    }
    crate::module::exec_script(&script, wait)
}

/// The status the manager reads.
pub fn status() -> Result<serde_json::Value> {
    let state = link_state()?;
    Ok(serde_json::json!({
        "id": ID,
        "version": VERSION,
        "installed": Path::new(ROOT).join("module.prop").exists(),
        "linked": state == LinkState::Linked,
        "external": state == LinkState::Occupied,
        "enabled": enabled(),
        "engine": crate::yukizygisk::enabled(),
        "engineSupported": crate::yukizygisk::kernel_supported(),
        "path": ROOT,
    }))
}

/// CLI entry point; the manager drives every write through it so one place owns the layout.
#[derive(Debug, Clone, clap::Subcommand)]
pub enum Command {
    /// Print the module status as JSON
    Status,
    /// (Re)install the payload, keeping `pif.prop`
    Install,
    /// Take over an upstream install at `/data/adb/modules/playintegrityfix`
    Adopt,
    /// Turn the module on
    Enable,
    /// Turn the module off; zygiskd stops loading it at the next boot
    Disable,
}

pub fn run(command: &Command) -> Result<()> {
    match command {
        Command::Status => println!("{}", serde_json::to_string_pretty(&status()?)?),
        Command::Install => {
            install()?;
            if link_state()? != LinkState::Linked {
                bail!("{LINK} is a real directory; run `ksud pif adopt` to replace it");
            }
        }
        Command::Adopt => adopt()?,
        Command::Enable => set_enabled(true)?,
        Command::Disable => set_enabled(false)?,
    }
    Ok(())
}

fn set_enabled(enable: bool) -> Result<()> {
    if enable {
        match fs::remove_file(DISABLE) {
            Ok(()) => info!("pif: enabled"),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error).context("remove the disable marker"),
        }
        install()?;
    } else {
        fs::write(DISABLE, b"1\n").context("write the disable marker")?;
        let _ = fs::set_permissions(DISABLE, fs::Permissions::from_mode(0o644));
        info!("pif: disabled");
    }
    Ok(())
}

fn install_payload() -> Result<()> {
    let root = Path::new(ROOT);
    ensure_dir(root)?;
    ensure_dir(&root.join("zygisk"))?;

    for (asset, relative) in PAYLOAD {
        write_payload(asset, &root.join(relative))?;
    }
    let prop = root.join(PROP_FILE);
    if !prop.exists() {
        write_payload(PROP_FILE, &prop)?;
        info!("pif: wrote the default {PROP_FILE}");
    }
    // Every boot writes this payload afresh, which is exactly what upstream's flag describes.
    fs::write(UPDATE_MARKER, b"1\n").with_context(|| format!("write {UPDATE_MARKER}"))?;
    let _ = fs::set_permissions(UPDATE_MARKER, fs::Permissions::from_mode(0o644));
    Ok(())
}

fn link_state() -> Result<LinkState> {
    match fs::symlink_metadata(LINK) {
        Ok(meta) if meta.file_type().is_symlink() => {
            let ours = fs::read_link(LINK).is_ok_and(|target| target == Path::new(ROOT));
            Ok(if ours { LinkState::Linked } else { LinkState::Occupied })
        }
        Ok(_) => Ok(LinkState::Occupied),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(LinkState::Missing),
        Err(error) => Err(error).with_context(|| format!("stat {LINK}")),
    }
}

/// Point `/data/adb/modules/playintegrityfix` at [ROOT], replacing a link of ours that went stale.
fn link() -> Result<LinkState> {
    let link = Path::new(LINK);
    match fs::symlink_metadata(link) {
        Ok(meta) if meta.file_type().is_symlink() => {
            if fs::read_link(link).is_ok_and(|target| target == Path::new(ROOT)) {
                return Ok(LinkState::Linked);
            }
            fs::remove_file(link).with_context(|| format!("remove stale link {LINK}"))?;
        }
        Ok(_) => return Ok(LinkState::Occupied),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error).with_context(|| format!("stat {LINK}")),
    }
    fs::create_dir_all(crate::defs::MODULE_DIR).with_context(|| "create the module directory")?;
    symlink(ROOT, link).with_context(|| format!("link {LINK} -> {ROOT}"))?;
    info!("pif: linked {LINK} -> {ROOT}");
    Ok(LinkState::Linked)
}

fn ensure_dir(path: &Path) -> Result<()> {
    fs::create_dir_all(path).with_context(|| format!("create {}", path.display()))?;
    fs::set_permissions(path, fs::Permissions::from_mode(0o755))
        .with_context(|| format!("chmod {}", path.display()))?;
    // The zygote reads the payload and is denied under `adb_data_file`; ksu_file is the label the
    // engine's own directory carries. The label is per inode, so every level needs it.
    if let Err(error) = crate::restorecon::lsetfilecon(path, crate::restorecon::KSU_CON) {
        warn!("pif: could not label {}: {error:#}", path.display());
    }
    Ok(())
}

/// Write one embedded file, replacing it only when its contents changed.
fn write_payload(asset_name: &str, target: &Path) -> Result<()> {
    let asset = crate::assets::embedded(&format!("pif/{asset_name}"))
        .with_context(|| format!("embedded pif/{asset_name} is missing"))?;
    if fs::read(target).is_ok_and(|on_disk| on_disk.as_slice() == asset.data.as_ref()) {
        return Ok(());
    }
    let temp = target.with_extension("new");
    fs::write(&temp, &asset.data).with_context(|| format!("write {}", temp.display()))?;
    // 0644 rather than 0444: the library opens `module.prop` read-write to replace its description
    // when the checksum does not match, and a read-only file makes it give up instead.
    fs::set_permissions(&temp, fs::Permissions::from_mode(0o644))
        .with_context(|| format!("chmod {}", temp.display()))?;
    if let Err(error) = crate::restorecon::lsetfilecon(&temp, crate::restorecon::KSU_CON) {
        warn!("pif: could not label {}: {error:#}", temp.display());
    }
    fs::rename(&temp, target).with_context(|| format!("install {}", target.display()))?;
    info!("pif: installed {}", target.display());
    Ok(())
}
