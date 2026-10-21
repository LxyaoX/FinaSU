//! Android userspace startup epochs.
//!
//! A kernel boot can contain multiple Android userspace starts when jailbreak
//! mode uses a soft reboot. Consumers that protect a startup step need to
//! distinguish those cycles without allowing a second attempt in one cycle.

use anyhow::{Context, Result, bail, ensure};
use serde::{Deserialize, Serialize};
use std::fs::{self, File, OpenOptions};
use std::io::Write;
use std::os::fd::AsRawFd;
use std::os::unix::fs::OpenOptionsExt;
use std::path::Path;
use std::sync::Mutex;
use std::sync::atomic::{AtomicI32, Ordering};

const STATE: &str = "/data/adb/ksu/startup_epoch.json";
const LOCK: &str = "/data/adb/ksu/startup_epoch.lock";
static OPERATION_LOCK: Mutex<Option<File>> = Mutex::new(None);
static OPERATION_LOCK_FD: AtomicI32 = AtomicI32::new(-1);

#[derive(Clone, Debug, Default, Deserialize, PartialEq, Eq, Serialize)]
#[serde(deny_unknown_fields)]
pub struct StartupEpoch {
    pub boot_id: String,
    pub generation: u64,
}

fn current_boot_id() -> Result<String> {
    fs::read_to_string("/proc/sys/kernel/random/boot_id")
        .context("Read kernel boot id")
        .map(|value| value.trim().to_owned())
}

fn read(path: &Path) -> Result<Option<StartupEpoch>> {
    match fs::read(path) {
        Ok(bytes) => serde_json::from_slice(&bytes)
            .context("Invalid Android userspace startup epoch")
            .map(Some),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(None),
        Err(error) => Err(error).context("Read Android userspace startup epoch"),
    }
}

fn write(path: &Path, epoch: &StartupEpoch) -> Result<()> {
    let parent = path.parent().context("Startup epoch has no parent")?;
    fs::create_dir_all(parent)?;
    let mut file = tempfile::NamedTempFile::new_in(parent)?;
    file.as_file()
        .set_permissions(std::os::unix::fs::PermissionsExt::from_mode(0o600))?;
    serde_json::to_writer(&mut file, epoch)?;
    file.write_all(b"\n")?;
    file.as_file().sync_all()?;
    file.persist(path)
        .context("Persist Android userspace startup epoch")?;
    File::open(parent)?.sync_all()?;
    Ok(())
}

fn lock(path: &Path) -> Result<File> {
    let parent = path.parent().context("Startup epoch lock has no parent")?;
    fs::create_dir_all(parent)?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(path)?;
    // SAFETY: the descriptor remains owned by `file` until the guard is dropped.
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX) } != 0 {
        return Err(std::io::Error::last_os_error())
            .context("Lock Android userspace startup epoch");
    }
    Ok(file)
}

/// Acquire a nonblocking exclusive lock for one userspace startup operation.
///
/// # Errors
/// Returns an error when the lock is busy or cannot be created or acquired.
pub fn try_lock(path: &Path, busy: &str) -> Result<File> {
    let parent = path
        .parent()
        .context("Startup operation lock has no parent")?;
    fs::create_dir_all(parent)?;
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .custom_flags(libc::O_CLOEXEC | libc::O_NOFOLLOW)
        .open(path)?;
    // SAFETY: the descriptor remains owned by `file` until the guard is dropped.
    if unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } != 0 {
        let error = std::io::Error::last_os_error();
        if error.kind() == std::io::ErrorKind::WouldBlock {
            bail!("{busy}");
        }
        return Err(error).context("Lock Android userspace startup operation");
    }
    Ok(file)
}

/// Keep the operation lock in the final daemon and publish its FD so nested
/// helpers can drop their inherited copy.
///
/// # Errors
/// Returns an error if another operation lock is already retained.
pub fn retain_operation_lock(file: File) -> Result<()> {
    let fd = file.as_raw_fd();
    let mut retained = OPERATION_LOCK
        .lock()
        .map_err(|_| anyhow::anyhow!("Startup operation lock state is poisoned"))?;
    ensure!(
        retained.is_none(),
        "Startup operation lock is already retained"
    );
    *retained = Some(file);
    drop(retained);
    OPERATION_LOCK_FD.store(fd, Ordering::SeqCst);
    Ok(())
}

pub fn close_inherited_operation_lock() {
    let fd = OPERATION_LOCK_FD.swap(-1, Ordering::SeqCst);
    if fd >= 0 {
        // SAFETY: fd is a duplicate inherited from the parent. The final daemon
        // retains its own descriptor in `OPERATION_LOCK`.
        unsafe {
            libc::close(fd);
        }
    }
}

#[cfg(test)]
fn release_operation_lock() {
    let retained = OPERATION_LOCK
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .take();
    OPERATION_LOCK_FD.store(-1, Ordering::SeqCst);
    drop(retained);
}

fn next_generation(current: u64) -> Result<u64> {
    current
        .checked_add(1)
        .context("Android userspace startup generation overflow")
}

#[must_use]
pub fn stop_confirmed(stop_succeeded: bool, services_stopped: Option<bool>) -> bool {
    stop_succeeded && services_stopped == Some(true)
}

fn current_or_create_at(state: &Path, lock_path: &Path, boot_id: &str) -> Result<StartupEpoch> {
    let _lock = lock(lock_path)?;
    let epoch = match read(state)? {
        Some(epoch) if epoch.boot_id == boot_id && epoch.generation > 0 => epoch,
        Some(epoch) => StartupEpoch {
            boot_id: boot_id.to_owned(),
            generation: next_generation(epoch.generation)?,
        },
        None => StartupEpoch {
            boot_id: boot_id.to_owned(),
            generation: 1,
        },
    };
    if read(state)?.as_ref() != Some(&epoch) {
        write(state, &epoch)?;
    }
    Ok(epoch)
}

fn advance_at(state: &Path, lock_path: &Path, boot_id: &str) -> Result<StartupEpoch> {
    let _lock = lock(lock_path)?;
    let previous = read(state)?.unwrap_or_else(|| StartupEpoch {
        boot_id: boot_id.to_owned(),
        generation: 0,
    });
    let epoch = StartupEpoch {
        boot_id: boot_id.to_owned(),
        generation: next_generation(previous.generation)?,
    };
    write(state, &epoch)?;
    Ok(epoch)
}

pub fn current_or_create() -> Result<StartupEpoch> {
    let boot_id = current_boot_id()?;
    bail_if_empty(&boot_id)?;
    current_or_create_at(Path::new(STATE), Path::new(LOCK), &boot_id)
}

#[cfg_attr(not(target_os = "android"), allow(dead_code))]
pub fn advance_soft_reboot() -> Result<StartupEpoch> {
    let boot_id = current_boot_id()?;
    bail_if_empty(&boot_id)?;
    advance_at(Path::new(STATE), Path::new(LOCK), &boot_id)
}

fn bail_if_empty(boot_id: &str) -> Result<()> {
    if boot_id.is_empty() {
        bail!("Kernel boot id is empty");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn state_paths(dir: &tempfile::TempDir) -> (std::path::PathBuf, std::path::PathBuf) {
        (dir.path().join("epoch.json"), dir.path().join("epoch.lock"))
    }

    #[test]
    fn cold_boots_and_soft_reboots_get_distinct_generations() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let (state, lock_path) = state_paths(&dir);
        let first = current_or_create_at(&state, &lock_path, "boot-a")?;
        assert_eq!(first.generation, 1);
        assert_eq!(current_or_create_at(&state, &lock_path, "boot-a")?, first);
        let soft = advance_at(&state, &lock_path, "boot-a")?;
        assert_eq!(soft.generation, 2);
        assert_eq!(soft.boot_id, "boot-a");
        let cold = current_or_create_at(&state, &lock_path, "boot-b")?;
        assert_eq!(cold.generation, 3);
        assert_eq!(cold.boot_id, "boot-b");
        Ok(())
    }

    #[test]
    fn malformed_epoch_is_not_silently_reset() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let (state, lock_path) = state_paths(&dir);
        fs::write(&state, "broken")?;
        assert!(current_or_create_at(&state, &lock_path, "boot-a").is_err());
        assert_eq!(fs::read_to_string(state)?, "broken");
        Ok(())
    }

    #[test]
    fn a_new_cycle_requires_a_confirmed_stop() {
        assert!(!stop_confirmed(false, None));
        assert!(!stop_confirmed(false, Some(true)));
        assert!(!stop_confirmed(true, None));
        assert!(!stop_confirmed(true, Some(false)));
        assert!(stop_confirmed(true, Some(true)));
    }

    #[test]
    fn an_operation_lock_excludes_a_second_owner() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("soft_reboot.lock");
        let first = try_lock(&path, "soft reboot already running")?;
        let error = try_lock(&path, "soft reboot already running").unwrap_err();
        assert!(error.to_string().contains("already running"));
        drop(first);
        try_lock(&path, "soft reboot already running")?;
        Ok(())
    }

    #[test]
    fn nested_daemon_does_not_keep_the_operation_lock_alive() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let path = dir.path().join("soft_reboot.lock");
        retain_operation_lock(try_lock(&path, "busy")?)?;

        let mut ready = [-1; 2];
        let mut release = [-1; 2];
        assert_eq!(unsafe { libc::pipe(ready.as_mut_ptr()) }, 0);
        assert_eq!(unsafe { libc::pipe(release.as_mut_ptr()) }, 0);

        let pid = unsafe { libc::fork() };
        assert!(pid >= 0);
        if pid == 0 {
            close_inherited_operation_lock();
            let mut byte = 1_u8;
            unsafe {
                libc::write(ready[1], (&raw const byte).cast(), 1);
                libc::read(release[0], (&raw mut byte).cast(), 1);
                libc::_exit(0);
            }
        }

        let mut byte = 0_u8;
        assert_eq!(
            unsafe { libc::read(ready[0], (&raw mut byte).cast(), 1) },
            1
        );
        assert_eq!(unsafe { libc::kill(pid, 0) }, 0);
        release_operation_lock();
        let _next = try_lock(&path, "busy")?;
        let done = 1_u8;
        assert_eq!(
            unsafe { libc::write(release[1], (&raw const done).cast(), 1) },
            1
        );
        let mut status = 0;
        assert_eq!(unsafe { libc::waitpid(pid, &raw mut status, 0) }, pid);
        Ok(())
    }
}
