#[cfg(target_os = "android")]
use anyhow::{Context, Result, bail};
use clap::Subcommand;
#[cfg(target_os = "android")]
use log::{info, warn};

#[cfg(target_os = "android")]
use const_format::concatcp;
#[cfg(target_os = "android")]
use prop_rs_android::resetprop::ResetProp;
#[cfg(target_os = "android")]
use prop_rs_android::sys_prop;
#[cfg(target_os = "android")]
use std::fs::{File, OpenOptions, Permissions};
#[cfg(target_os = "android")]
use std::io::Write;
#[cfg(target_os = "android")]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
#[cfg(target_os = "android")]
use std::path::Path;

const PROPERTIES: [(&str, &str); 16] = [
    ("ro.boot.vbmeta.device_state", "locked"),
    ("ro.boot.verifiedbootstate", "green"),
    ("ro.boot.flash.locked", "1"),
    ("ro.boot.veritymode", "enforcing"),
    ("vendor.boot.vbmeta.device_state", "locked"),
    ("vendor.boot.verifiedbootstate", "green"),
    ("ro.boot.warranty_bit", "0"),
    ("ro.warranty_bit", "0"),
    ("ro.vendor.boot.warranty_bit", "0"),
    ("ro.vendor.warranty_bit", "0"),
    ("ro.secureboot.lockstate", "locked"),
    ("ro.boot.realmebootstate", "green"),
    ("ro.boot.realme.lockstate", "1"),
    ("ro.debuggable", "0"),
    ("ro.adb.secure", "1"),
    ("ro.secure", "1"),
];

#[cfg(target_os = "android")]
const MARKER_PATH: &str = concatcp!(crate::defs::WORKING_DIR, ".boot_hide");

#[derive(Debug, Clone, Subcommand)]
pub enum Command {
    Status,
    Enable,
    Disable,
}

fn apply_with(
    mut get: impl FnMut(&str) -> Option<String>,
    mut set: impl FnMut(&str, &str) -> bool,
    mut delete: impl FnMut(&str),
) -> usize {
    let mut writes = 0;
    for &(key, value) in &PROPERTIES {
        let Some(current) = get(key) else {
            continue;
        };
        if current != value {
            set(key, value);
            writes += 1;
        }
    }
    if get("service.adb.root").is_some() {
        delete("service.adb.root");
    }
    writes
}

#[cfg(target_os = "android")]
fn marker_path() -> &'static Path {
    Path::new(MARKER_PATH)
}

#[cfg(target_os = "android")]
fn marker_exists() -> Result<bool> {
    marker_path()
        .try_exists()
        .context("Inspect FinaSU Hide marker")
}

#[cfg(target_os = "android")]
fn write_marker() -> Result<()> {
    let path = marker_path();
    let mut file = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)
        .with_context(|| format!("Open {}", path.display()))?;
    file.set_permissions(Permissions::from_mode(0o600))
        .context("Set FinaSU Hide marker permissions")?;
    file.flush().context("Write FinaSU Hide marker")?;
    file.sync_all().context("Sync FinaSU Hide marker")?;
    File::open(crate::defs::WORKING_DIR)
        .context("Open working directory")?
        .sync_all()
        .context("Sync working directory")?;
    Ok(())
}

#[cfg(target_os = "android")]
fn remove_marker() -> Result<()> {
    match std::fs::remove_file(marker_path()) {
        Ok(()) => {}
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(()),
        Err(error) => return Err(error).context("Remove FinaSU Hide marker"),
    }
    File::open(crate::defs::WORKING_DIR)
        .context("Open working directory")?
        .sync_all()
        .context("Sync working directory")?;
    Ok(())
}

#[cfg(target_os = "android")]
fn rebuild_if_needed(rp: &ResetProp, key: &str, needed: bool) -> Result<()> {
    if needed {
        let context = sys_prop::get_context(key)
            .with_context(|| format!("Get property context for {key}"))?;
        rp.rebuild(&context)
            .with_context(|| format!("Rebuild property area for {key}"))?;
    }
    Ok(())
}

#[cfg(target_os = "android")]
fn apply() -> Result<usize> {
    sys_prop::init().context("Initialize system property API")?;
    let rp = ResetProp {
        skip_svc: true,
        persistent: false,
        persist_only: false,
        verbose: false,
        show_context: false,
        rebuild: false,
    };

    let mut set_error = None;
    let mut delete_error = None;
    let writes = apply_with(
        sys_prop::get,
        |key, value| {
            if set_error.is_some() {
                return false;
            }
            match rp
                .set(key, value)
                .with_context(|| format!("Set {key}"))
                .and_then(|needed| rebuild_if_needed(&rp, key, needed))
            {
                Ok(()) => true,
                Err(error) => {
                    set_error = Some(error);
                    false
                }
            }
        },
        |key| {
            if delete_error.is_some() {
                return;
            }
            if let Err(error) = rp
                .delete(key)
                .with_context(|| format!("Delete {key}"))
                .and_then(|needed| rebuild_if_needed(&rp, key, needed))
            {
                delete_error = Some(error);
            }
        },
    );

    if let Some(error) = set_error.or(delete_error) {
        return Err(error);
    }
    Ok(writes)
}

#[cfg(target_os = "android")]
fn ensure_root() -> Result<()> {
    if unsafe { libc::geteuid() } != 0 {
        bail!("Root is required for FinaSU Hide");
    }
    Ok(())
}

#[cfg(target_os = "android")]
pub fn boot_apply() {
    match marker_exists() {
        Ok(false) => return,
        Ok(true) => {}
        Err(error) => {
            warn!("Inspect FinaSU Hide marker failed: {error:#}");
            return;
        }
    }
    if crate::utils::is_safe_mode() {
        warn!("safe mode, skip FinaSU Hide");
        return;
    }
    match apply() {
        Ok(writes) => info!("FinaSU Hide applied {writes} properties"),
        Err(error) => warn!("Apply FinaSU Hide failed: {error:#}"),
    }
}

#[cfg(target_os = "android")]
pub fn run(command: &Command) -> Result<()> {
    ensure_root()?;
    match command {
        Command::Status => {
            println!(
                "{}",
                if marker_exists()? {
                    "enabled"
                } else {
                    "disabled"
                }
            );
            Ok(())
        }
        Command::Enable => {
            write_marker()?;
            if let Err(error) = apply() {
                warn!("Apply FinaSU Hide failed: {error:#}");
            }
            Ok(())
        }
        Command::Disable => remove_marker(),
    }
}

#[cfg(test)]
#[path = "boot_hide_tests.rs"]
mod tests;
