#[cfg(target_os = "android")]
use crate::defs;
#[cfg(not(target_os = "android"))]
mod defs {
    pub const WORKING_DIR: &str = "/data/adb/ksu/";
    pub const PREINIT_LOCK_PATH: &str = "/data/adb/ksu/.preinit.lock";
    pub const PREINIT_STATUS_PATH: &str = "/data/adb/ksu/preinit_status";
    pub const PREINIT_SUBDIR: &str = "preinit";
    pub const MODULE_PREINIT_DIR: &str = "preinit";
    pub const PREINIT_CONF: &str = "preinit.conf";
    pub const PREINIT_BOOTCOUNT_FILE: &str = ".preinit_bootcount";
    pub const PREINIT_DIR_DEFAULT: &str = "/metadata/ksu/";
}
#[cfg(target_os = "android")]
use crate::restorecon::lsetfilecon;
use anyhow::{Context, Result, bail};
use log::warn;
use std::{
    collections::{BTreeMap, BTreeSet, HashMap},
    fmt::Write as _,
    fs::{self, File, OpenOptions, Permissions},
    os::unix::fs::{MetadataExt, OpenOptionsExt, PermissionsExt},
    path::{Path, PathBuf},
};

const BLOCK: u64 = 4096;
const LIMIT_TOTAL: u64 = 4 << 20;
const RESERVE: u64 = 8 << 20;
const BOOTCOUNT_LIMIT: u64 = 3;

pub fn lock() -> Result<File> {
    fs::create_dir_all(defs::WORKING_DIR)?;
    let file = OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW | libc::O_CLOEXEC)
        .open(defs::PREINIT_LOCK_PATH)?;
    rustix::fs::flock(&file, rustix::fs::FlockOperation::LockExclusive)?;
    Ok(file)
}

struct Entry {
    rel: PathBuf,
    dir: bool,
    size: u64,
    mode: u32,
}

fn scan(root: &Path, rel: &Path, out: &mut Vec<Entry>) -> Result<()> {
    let mut entries = fs::read_dir(root.join(rel))?.collect::<std::io::Result<Vec<_>>>()?;
    entries.sort_by_key(fs::DirEntry::file_name);
    for entry in entries {
        let rel = rel.join(entry.file_name());
        let meta = fs::symlink_metadata(entry.path())?;
        if !meta.is_dir() && !meta.is_file() {
            bail!("unsupported file type: {}", rel.display());
        }
        if rel.to_str().is_none_or(|s| s.contains(['\n', '\r', '\t'])) {
            bail!("invalid preinit path");
        }
        out.push(Entry {
            rel: rel.clone(),
            dir: meta.is_dir(),
            size: meta.len(),
            mode: meta.mode() & 0o755,
        });
        if meta.is_dir() {
            scan(root, &rel, out)?;
        }
    }
    Ok(())
}

fn usage(entries: &[Entry]) -> u64 {
    BLOCK
        + entries
            .iter()
            .map(|e| {
                if e.dir {
                    BLOCK
                } else {
                    e.size.div_ceil(BLOCK) * BLOCK
                }
            })
            .sum::<u64>()
}

fn available(path: &Path) -> Result<u64> {
    let stat = rustix::fs::statvfs(path)?;
    Ok(stat.f_bavail.saturating_mul(stat.f_frsize))
}

fn durable(path: &Path) -> Result<()> {
    File::open(path)?
        .sync_all()
        .with_context(|| format!("sync {}", path.display()))
}

fn secure(path: &Path, mode: u32, con: &str) -> Result<()> {
    #[cfg(target_os = "android")]
    std::os::unix::fs::lchown(path, Some(0), Some(0))?;
    fs::set_permissions(path, Permissions::from_mode(mode & 0o755))?;
    #[cfg(target_os = "android")]
    lsetfilecon(path, con)?;
    #[cfg(not(target_os = "android"))]
    let _ = con;
    Ok(())
}

fn remove(path: &Path) -> Result<()> {
    match fs::symlink_metadata(path) {
        Ok(meta) if meta.is_dir() => fs::remove_dir_all(path)?,
        Ok(_) => fs::remove_file(path)?,
        Err(e) if e.kind() == std::io::ErrorKind::NotFound => (),
        Err(e) => return Err(e.into()),
    }
    Ok(())
}

fn recover(root: &Path) -> Result<()> {
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        let name = entry.file_name();
        let Some(name) = name.to_str() else { continue };
        if let Some(id) = name.strip_prefix('.').and_then(|s| s.strip_suffix(".old")) {
            let dst = root.join(id);
            if dst.exists() {
                remove(&entry.path())?;
            } else {
                fs::rename(entry.path(), dst)?;
                durable(root)?;
            }
        } else if name.starts_with('.') && name.strip_suffix(".tmp").is_some() {
            remove(&entry.path())?;
        }
    }
    Ok(())
}

fn same_copy(dst: &Path, src: &Path, entries: &[Entry]) -> bool {
    let mut current = Vec::new();
    if scan(dst, Path::new(""), &mut current).is_err() || current.len() != entries.len() {
        return false;
    }
    entries.iter().zip(current).all(|(want, have)| {
        want.rel == have.rel
            && want.dir == have.dir
            && want.mode == have.mode
            && (want.dir
                || (want.size == have.size
                    && matches!(
                        (fs::read(src.join(&want.rel)), fs::read(dst.join(&want.rel))),
                        (Ok(a), Ok(b)) if a == b
                    )))
    })
}

fn install(root: &Path, id: &str, src: &Path, entries: &[Entry], con: &str) -> Result<()> {
    let tmp = root.join(format!(".{id}.tmp"));
    let dst = root.join(id);
    remove(&tmp)?;
    fs::create_dir(&tmp)?;
    secure(&tmp, 0o755, con)?;
    for entry in entries {
        let to = tmp.join(&entry.rel);
        if entry.dir {
            fs::create_dir(&to)?;
        } else {
            let from = src.join(&entry.rel);
            let mut input = OpenOptions::new()
                .read(true)
                .custom_flags(libc::O_NOFOLLOW)
                .open(&from)?;
            let meta = input.metadata()?;
            if !meta.is_file() || meta.len() != entry.size {
                bail!("preinit source changed: {}", from.display());
            }
            let mut output = OpenOptions::new()
                .write(true)
                .create_new(true)
                .mode(0o600)
                .open(&to)?;
            let copied = std::io::copy(
                &mut std::io::Read::take(&mut input, entry.size + 1),
                &mut output,
            )?;
            if copied != entry.size {
                bail!("preinit source changed: {}", from.display());
            }
            output.sync_all()?;
        }
        secure(&to, entry.mode, con)?;
        durable(&to)?;
    }
    for entry in entries.iter().rev().filter(|e| e.dir) {
        durable(&tmp.join(&entry.rel))?;
    }
    durable(&tmp)?;
    if dst.exists() {
        rustix::fs::renameat_with(
            rustix::fs::CWD,
            &tmp,
            rustix::fs::CWD,
            &dst,
            rustix::fs::RenameFlags::EXCHANGE,
        )?;
    } else {
        fs::rename(&tmp, &dst)?;
    }
    durable(root)?;
    remove(&tmp)?;
    durable(root)
}

fn write_atomic(path: &Path, content: &str) -> Result<()> {
    let tmp = path.with_extension("tmp");
    let mut file = OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(&tmp)?;
    std::io::Write::write_all(&mut file, content.as_bytes())?;
    file.sync_all()?;
    fs::rename(tmp, path)?;
    durable(path.parent().context("missing parent")?)
}

pub fn sync(
    ksu_dir: &Path,
    obsolete_dirs: &[&Path],
    modules: &BTreeMap<String, PathBuf>,
    con: &str,
    supported: bool,
) -> Result<()> {
    sync_to(
        ksu_dir,
        obsolete_dirs,
        modules,
        con,
        supported,
        Path::new(defs::PREINIT_STATUS_PATH),
    )
}

fn sync_to(
    ksu_dir: &Path,
    obsolete_dirs: &[&Path],
    modules: &BTreeMap<String, PathBuf>,
    con: &str,
    supported: bool,
    status_path: &Path,
) -> Result<()> {
    fs::create_dir_all(ksu_dir)?;
    secure(ksu_dir, 0o755, con)?;
    let current_root = ksu_dir.join(defs::PREINIT_SUBDIR);
    if current_root.exists() {
        recover(&current_root)?;
    }
    let root = ksu_dir.join(".preinit.next");
    remove(&root)?;
    fs::create_dir(&root)?;
    secure(&root, 0o755, con)?;
    let mut statuses = String::new();
    let mut wanted = BTreeSet::new();
    let mut used = 2 * BLOCK;
    let mut failed = false;
    for (id, module) in modules {
        let src = module.join(defs::MODULE_PREINIT_DIR);
        if !src.exists() {
            continue;
        }
        if !supported {
            writeln!(statuses, "{id} unsupported kernel does not run preinit")?;
            continue;
        }
        wanted.insert(id.clone());
        let result = (|| -> Result<&str> {
            if id.starts_with('.')
                || !id
                    .bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-' || b == b'.')
            {
                bail!("invalid module id");
            }
            if !fs::symlink_metadata(&src)?.is_dir()
                || !fs::symlink_metadata(src.join(defs::PREINIT_CONF))?.is_file()
            {
                bail!("invalid preinit directory or configuration");
            }
            let mut entries = Vec::new();
            scan(&src, Path::new(""), &mut entries)?;
            let need = usage(&entries);
            if used.saturating_add(need) > LIMIT_TOTAL {
                return Ok("no_space");
            }
            used += need;
            if !same_copy(&root.join(id), &src, &entries) {
                if available(ksu_dir)? < need.saturating_add(RESERVE) {
                    return Ok("no_space");
                }
                install(&root, id, &src, &entries, con)?;
            }
            Ok("ok")
        })();
        match result {
            Ok(state) => {
                if state != "ok" {
                    failed = true;
                }
                writeln!(statuses, "{id} {state} ")?;
            }
            Err(e) => {
                failed = true;
                let message = format!("{e:#}").replace(['\n', '\r'], " ");
                writeln!(statuses, "{id} error {message}")?;
            }
        }
    }
    for entry in fs::read_dir(&root)? {
        let entry = entry?;
        let name = entry.file_name().to_string_lossy().into_owned();
        if !name.starts_with('.') && !wanted.contains(&name) {
            remove(&entry.path())?;
        }
    }
    durable(&root)?;
    if failed {
        remove(&root)?;
        let statuses = statuses.replace(
            " ok ",
            " error transaction aborted; previous configuration retained ",
        );
        write_atomic(status_path, &statuses)?;
        bail!("some preinit modules were not synchronized; previous copies retained");
    }
    let mut entries = Vec::new();
    scan(&root, Path::new(""), &mut entries)?;
    if same_copy(&current_root, &root, &entries) {
        remove(&root)?;
    } else {
        if current_root.exists() {
            rustix::fs::renameat_with(
                rustix::fs::CWD,
                &root,
                rustix::fs::CWD,
                &current_root,
                rustix::fs::RenameFlags::EXCHANGE,
            )?;
        } else {
            fs::rename(&root, &current_root)?;
        }
        durable(ksu_dir)?;
        remove(&root)?;
        remove(&ksu_dir.join(defs::PREINIT_BOOTCOUNT_FILE))?;
        durable(ksu_dir)?;
    }
    for dir in obsolete_dirs {
        remove(&dir.join(defs::PREINIT_SUBDIR))?;
        remove(&dir.join(defs::PREINIT_BOOTCOUNT_FILE))?;
        if dir.exists() {
            durable(dir)?;
        }
    }
    write_atomic(status_path, &statuses)?;
    Ok(())
}

#[cfg(target_os = "android")]
fn bootcount_path() -> PathBuf {
    Path::new(crate::module::preinit_ksu_dir()).join(defs::PREINIT_BOOTCOUNT_FILE)
}

#[cfg(not(target_os = "android"))]
fn bootcount_path() -> PathBuf {
    Path::new(defs::PREINIT_DIR_DEFAULT).join(defs::PREINIT_BOOTCOUNT_FILE)
}

fn read_bootcount() -> u64 {
    fs::read_to_string(bootcount_path())
        .ok()
        .and_then(|s| s.trim().parse().ok())
        .unwrap_or(0)
}

pub fn reset_bootcount() -> Result<()> {
    let _lock = lock()?;
    let path = bootcount_path();
    remove(&path)?;
    if let Some(parent) = path.parent().filter(|p| p.exists()) {
        durable(parent)?;
    }
    Ok(())
}

pub fn on_boot_completed() {
    let result = (|| -> Result<()> {
        let _lock = lock()?;
        let count = read_bootcount();
        if count > 0 && count <= BOOTCOUNT_LIMIT {
            let path = bootcount_path();
            remove(&path)?;
            durable(path.parent().context("missing counter parent")?)?;
        }
        Ok(())
    })();
    if let Err(e) = result {
        warn!("preinit boot completion: {e:#}");
    }
}

pub fn module_states() -> HashMap<String, String> {
    let parked = read_bootcount() > BOOTCOUNT_LIMIT;
    fs::read_to_string(defs::PREINIT_STATUS_PATH)
        .unwrap_or_default()
        .lines()
        .filter_map(|line| {
            let mut parts = line.splitn(3, ' ');
            let id = parts.next()?;
            let state = parts.next()?;
            Some((
                id.to_owned(),
                if parked && state == "ok" {
                    "bootloop"
                } else {
                    state
                }
                .to_owned(),
            ))
        })
        .collect()
}

pub fn status_of(id: &str) -> Option<(String, String)> {
    let states = module_states();
    let state = states.get(id)?.clone();
    let content = fs::read_to_string(defs::PREINIT_STATUS_PATH).ok()?;
    let message = content
        .lines()
        .find_map(|line| {
            let mut parts = line.splitn(3, ' ');
            (parts.next()? == id).then(|| {
                parts.next();
                parts.next().unwrap_or_default().to_owned()
            })
        })
        .unwrap_or_default();
    Some((state, message))
}

pub fn print_status() {
    println!(
        "preinit: next-boot synchronization status; bootcount {}/{BOOTCOUNT_LIMIT}",
        read_bootcount()
    );
    let mut states: Vec<_> = module_states().into_iter().collect();
    states.sort();
    for (id, state) in states {
        let message = status_of(&id).map_or_else(String::new, |(_, message)| message);
        println!("{id} {state} {message}");
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::symlink;

    #[test]
    fn scan_rejects_symlinks_and_accounts_for_blocks() {
        let root = tempfile::tempdir().unwrap();
        fs::write(root.path().join("preinit.conf"), b"# empty\n").unwrap();
        let mut entries = Vec::new();
        scan(root.path(), Path::new(""), &mut entries).unwrap();
        assert_eq!(usage(&entries), 2 * BLOCK);
        symlink("preinit.conf", root.path().join("link")).unwrap();
        assert!(scan(root.path(), Path::new(""), &mut Vec::new()).is_err());
    }

    #[test]
    fn same_copy_checks_bytes_not_just_size() {
        let src = tempfile::tempdir().unwrap();
        let dst = tempfile::tempdir().unwrap();
        for dir in [src.path(), dst.path()] {
            fs::write(dir.join("preinit.conf"), b"abc").unwrap();
        }
        let mut entries = Vec::new();
        scan(src.path(), Path::new(""), &mut entries).unwrap();
        assert!(same_copy(dst.path(), src.path(), &entries));
        fs::write(dst.path().join("preinit.conf"), b"xyz").unwrap();
        assert!(!same_copy(dst.path(), src.path(), &entries));
    }

    #[test]
    fn recovery_preserves_live_copy_and_restores_missing_copy() {
        let root = tempfile::tempdir().unwrap();
        fs::create_dir(root.path().join(".first.old")).unwrap();
        fs::create_dir(root.path().join("second")).unwrap();
        fs::create_dir(root.path().join(".second.old")).unwrap();
        fs::create_dir(root.path().join(".third.tmp")).unwrap();
        recover(root.path()).unwrap();
        assert!(root.path().join("first").is_dir());
        assert!(root.path().join("second").is_dir());
        assert!(!root.path().join(".second.old").exists());
        assert!(!root.path().join(".third.tmp").exists());
    }

    #[test]
    fn atomic_write_replaces_content_and_rejects_temp_symlink() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("status");
        write_atomic(&path, "first").unwrap();
        write_atomic(&path, "second").unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "second");
        symlink(&path, path.with_extension("tmp")).unwrap();
        assert!(write_atomic(&path, "third").is_err());
        assert_eq!(fs::read_to_string(path).unwrap(), "second");
    }

    #[test]
    fn transaction_preserves_configuration_and_counter_on_invalid_source() {
        let root = tempfile::tempdir().unwrap();
        let metadata = root.path().join("metadata");
        let module = root.path().join("module");
        fs::create_dir_all(module.join("preinit")).unwrap();
        fs::write(module.join("preinit/preinit.conf"), b"# first\n").unwrap();
        let modules = BTreeMap::from([("example".to_owned(), module.clone())]);
        let status = root.path().join("status");
        sync_to(&metadata, &[], &modules, "", true, &status).unwrap();
        let live = metadata.join("preinit/example/preinit.conf");
        let counter = metadata.join(defs::PREINIT_BOOTCOUNT_FILE);
        fs::write(&counter, "4").unwrap();
        sync_to(&metadata, &[], &modules, "", true, &status).unwrap();
        assert_eq!(fs::read_to_string(&counter).unwrap(), "4");
        symlink("preinit.conf", module.join("preinit/link")).unwrap();
        assert!(sync_to(&metadata, &[], &modules, "", true, &status).is_err());
        assert_eq!(fs::read(&live).unwrap(), b"# first\n");
        assert_eq!(fs::read_to_string(&counter).unwrap(), "4");
        assert!(
            fs::read_to_string(&status)
                .unwrap()
                .contains("example error")
        );
        fs::remove_file(module.join("preinit/link")).unwrap();
        fs::write(module.join("preinit/preinit.conf"), b"# next\n").unwrap();
        sync_to(&metadata, &[], &modules, "", true, &status).unwrap();
        assert_eq!(fs::read(live).unwrap(), b"# next\n");
        assert!(!counter.exists());
    }

    #[test]
    fn unsupported_and_removal_clear_payloads_only_after_success() {
        let root = tempfile::tempdir().unwrap();
        let metadata = root.path().join("metadata");
        let module = root.path().join("module");
        fs::create_dir_all(module.join("preinit")).unwrap();
        fs::write(module.join("preinit/preinit.conf"), b"# empty\n").unwrap();
        let modules = BTreeMap::from([("example".to_owned(), module)]);
        let status = root.path().join("status");
        sync_to(&metadata, &[], &modules, "", true, &status).unwrap();
        assert!(metadata.join("preinit/example").is_dir());
        sync_to(&metadata, &[], &modules, "", false, &status).unwrap();
        assert!(!metadata.join("preinit/example").exists());
        assert!(
            fs::read_to_string(&status)
                .unwrap()
                .contains("example unsupported")
        );
        sync_to(&metadata, &[], &BTreeMap::new(), "", true, &status).unwrap();
        assert_eq!(fs::read_to_string(status).unwrap(), "");
    }

    #[test]
    fn transaction_rejects_over_budget_and_keeps_obsolete_location() {
        let root = tempfile::tempdir().unwrap();
        let metadata = root.path().join("metadata");
        let obsolete = root.path().join("obsolete");
        fs::create_dir_all(obsolete.join("preinit/previous")).unwrap();
        let module = root.path().join("module");
        fs::create_dir_all(module.join("preinit")).unwrap();
        fs::write(module.join("preinit/preinit.conf"), b"# empty\n").unwrap();
        File::create(module.join("preinit/large"))
            .unwrap()
            .set_len(LIMIT_TOTAL)
            .unwrap();
        let modules = BTreeMap::from([("example".to_owned(), module)]);
        let status = root.path().join("status");
        assert!(sync_to(&metadata, &[&obsolete], &modules, "", true, &status).is_err());
        assert!(!metadata.join("preinit").exists());
        assert!(obsolete.join("preinit/previous").is_dir());
        assert!(
            fs::read_to_string(status)
                .unwrap()
                .contains("example no_space")
        );
    }
}
