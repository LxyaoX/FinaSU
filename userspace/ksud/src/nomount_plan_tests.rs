//! Host tests for NoMount rule generation.

use std::collections::{HashMap, HashSet};
use std::path::{Path, PathBuf};

use super::*;

/// An in-memory module tree, so whiteouts and opaque directories can be tested
/// without root or a real character device.
#[derive(Default)]
struct FakeFs {
    dirs: HashMap<PathBuf, Vec<String>>,
    kinds: HashMap<PathBuf, EntryKind>,
    opaque: HashSet<PathBuf>,
    present: HashSet<PathBuf>,
}

impl FakeFs {
    fn dir(mut self, path: &str, names: &[&str]) -> Self {
        self.dirs.insert(
            PathBuf::from(path),
            names.iter().map(ToString::to_string).collect(),
        );
        self.kinds.insert(PathBuf::from(path), EntryKind::Dir);
        self
    }

    fn entry(mut self, path: &str, kind: EntryKind) -> Self {
        self.kinds.insert(PathBuf::from(path), kind);
        self
    }

    fn opaque(mut self, path: &str) -> Self {
        self.opaque.insert(PathBuf::from(path));
        self
    }

    /// Mark a real filesystem path as existing (the device side, not the module).
    fn present(mut self, path: &str) -> Self {
        self.present.insert(PathBuf::from(path));
        self
    }
}

impl PlanFs for FakeFs {
    fn canonical_dir(&self, path: &Path) -> Result<Option<PathBuf>> {
        Ok((self.kind(path)? == EntryKind::Dir).then(|| path.to_path_buf()))
    }

    fn read_dir(&self, dir: &Path) -> Result<Vec<String>> {
        Ok(self.dirs.get(dir).cloned().unwrap_or_default())
    }

    fn kind(&self, path: &Path) -> Result<EntryKind> {
        Ok(self.kinds.get(path).copied().unwrap_or(EntryKind::Other))
    }

    fn exists(&self, path: &Path) -> Result<bool> {
        Ok(self.present.contains(path) || self.kinds.contains_key(path))
    }

    fn is_opaque(&self, path: &Path) -> Result<bool> {
        Ok(self.opaque.contains(path))
    }
}

fn config<'a>(modules: &'a Path, root: &'a Path, system: &'a Path) -> PlanConfig<'a> {
    PlanConfig::new(modules, root, system)
}

const MODULES: &str = "/data/adb/modules";
const MODULE: &str = "/data/adb/modules/m";

fn base() -> FakeFs {
    FakeFs::default()
        .dir(MODULES, &["m"])
        .dir(MODULE, &["system"])
        .present("/system")
        .present("/vendor")
}

#[test]
fn virtual_paths_remap_nested_partitions() {
    assert_eq!(virtual_path("system/etc/hosts"), "/system/etc/hosts");
    assert_eq!(
        virtual_path("system/vendor/lib/libfoo.so"),
        "/vendor/lib/libfoo.so"
    );
    assert_eq!(virtual_path("system/vendor"), "/vendor");
    assert_eq!(virtual_path("system/system_ext/app/x"), "/system_ext/app/x");
    assert_eq!(virtual_path("system/my_bigball/app/x"), "/my_bigball/app/x");
    assert_eq!(virtual_path("vendor/etc/x"), "/vendor/etc/x");
    assert_eq!(virtual_path("system/my_product"), "/my_product");
}

#[test]
fn files_symlinks_whiteouts_and_other_kinds() {
    let fs = base()
        .dir(&format!("{MODULE}/system"), &["etc", "lib"])
        .dir(&format!("{MODULE}/system/etc"), &["hosts", "gone", "fifo"])
        .entry(&format!("{MODULE}/system/etc/hosts"), EntryKind::File)
        .entry(&format!("{MODULE}/system/etc/gone"), EntryKind::Whiteout)
        .entry(&format!("{MODULE}/system/etc/fifo"), EntryKind::Other)
        .dir(&format!("{MODULE}/system/lib"), &["libfoo.so"])
        .entry(
            &format!("{MODULE}/system/lib/libfoo.so"),
            EntryKind::Symlink,
        );

    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![
            // Sorted directory order: fifo (skipped), gone, hosts, then lib.
            Rule::whiteout("/system/etc/gone"),
            Rule::redirect("/system/etc/hosts", format!("{MODULE}/system/etc/hosts")),
            Rule::redirect(
                "/system/lib/libfoo.so",
                format!("{MODULE}/system/lib/libfoo.so")
            ),
        ]
    );
    assert!(plan.skipped.is_empty());
}

#[test]
fn lifecycle_marks_skip_a_module() {
    for mark in SKIP_MARKS {
        let fs = base()
            .dir(&format!("{MODULE}/system"), &["etc"])
            .dir(&format!("{MODULE}/system/etc"), &["hosts"])
            .entry(&format!("{MODULE}/system/etc/hosts"), EntryKind::File)
            .entry(&format!("{MODULE}/{mark}"), EntryKind::File);
        let plan = collect_rules(
            &fs,
            &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
        )
        .unwrap();
        assert!(plan.rules.is_empty(), "{mark} must skip the module");
        assert_eq!(plan.skipped, vec![("m".to_string(), mark.to_string())]);
    }
}

#[test]
fn an_opaque_directory_is_replaced_without_descending() {
    let fs = base()
        .present("/product")
        .dir(&format!("{MODULE}/product"), &["fonts"])
        .dir(&format!("{MODULE}/product/fonts"), &["sample.ttf"])
        .entry(
            &format!("{MODULE}/product/fonts/sample.ttf"),
            EntryKind::File,
        )
        .opaque(&format!("{MODULE}/product/fonts"));

    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![Rule::redirect(
            "/product/fonts",
            format!("{MODULE}/product/fonts")
        )],
        "a replaced directory redirects to the module copy and its children come from that listing"
    );
}

#[test]
fn a_replace_marker_replaces_its_directory() {
    let fs = base()
        .present("/product")
        .dir(&format!("{MODULE}/product"), &["fonts"])
        .dir(
            &format!("{MODULE}/product/fonts"),
            &[REPLACE_MARKER, "sample.ttf"],
        )
        .entry(
            &format!("{MODULE}/product/fonts/{REPLACE_MARKER}"),
            EntryKind::File,
        )
        .entry(
            &format!("{MODULE}/product/fonts/sample.ttf"),
            EntryKind::File,
        );

    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![Rule::redirect(
            "/product/fonts",
            format!("{MODULE}/product/fonts")
        )]
    );
}

#[test]
fn nested_partitions_under_system_are_remapped() {
    let fs = base()
        .dir(&format!("{MODULE}/system"), &["vendor"])
        .dir(&format!("{MODULE}/system/vendor"), &["etc"])
        .dir(&format!("{MODULE}/system/vendor/etc"), &["audio.conf"])
        .entry(
            &format!("{MODULE}/system/vendor/etc/audio.conf"),
            EntryKind::File,
        );

    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![Rule::redirect(
            "/vendor/etc/audio.conf",
            format!("{MODULE}/system/vendor/etc/audio.conf")
        )]
    );
}

#[test]
fn modules_without_partition_content_are_reported() {
    let fs = base().dir(&format!("{MODULE}/system"), &[]);
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert!(plan.rules.is_empty());
    assert_eq!(
        plan.skipped,
        vec![("m".to_string(), "no partition content".to_string())]
    );
}

#[test]
fn partitions_missing_on_the_device_are_skipped() {
    let fs = base()
        .dir(&format!("{MODULE}/odm"), &["etc"])
        .dir(&format!("{MODULE}/odm/etc"), &["x"])
        .entry(&format!("{MODULE}/odm/etc/x"), EntryKind::File);
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert!(plan.rules.is_empty());
    assert_eq!(
        plan.skipped,
        vec![("m".to_string(), "no partition content".to_string())]
    );
}

#[test]
fn modules_are_processed_in_a_stable_order() {
    let fs = FakeFs::default()
        .dir(MODULES, &["zeta", "alpha"])
        .dir("/data/adb/modules/zeta", &["system"])
        .dir("/data/adb/modules/alpha", &["system"])
        .present("/system")
        .dir("/data/adb/modules/zeta/system", &["z"])
        .entry("/data/adb/modules/zeta/system/z", EntryKind::File)
        .dir("/data/adb/modules/alpha/system", &["a"])
        .entry("/data/adb/modules/alpha/system/a", EntryKind::File);

    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    let order: Vec<&str> = plan
        .rules
        .iter()
        .map(|rule| rule.virtual_path.as_str())
        .collect();
    assert_eq!(order, vec!["/system/a", "/system/z"]);
}

#[test]
fn protected_replacements_merge_files_and_never_inject_the_marker() {
    for target in [
        "system",
        "vendor",
        "odm",
        "product",
        "system_ext",
        "system/lib64",
        "system/lib",
        "system/bin",
        "system/etc",
    ] {
        for opaque in [false, true] {
            let partition = target.split('/').next().unwrap();
            let mut fs = base().present(&format!("/{partition}"));
            if let Some((parent, child)) = target.rsplit_once('/') {
                fs = fs.dir(&format!("{MODULE}/{parent}"), &[child]);
            }
            let dir = format!("{MODULE}/{target}");
            fs = fs
                .dir(
                    &dir,
                    if opaque {
                        &["libsample.so"]
                    } else {
                        &[".replace", "libsample.so"]
                    },
                )
                .entry(&format!("{dir}/libsample.so"), EntryKind::File);
            if opaque {
                fs = fs.opaque(&dir);
            } else {
                fs = fs.entry(&format!("{dir}/.replace"), EntryKind::File);
            }
            let plan = collect_rules(
                &fs,
                &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
            )
            .unwrap();
            assert_eq!(plan.rules.len(), 1, "{target}");
            assert_eq!(
                plan.rules[0].virtual_path,
                format!("/{target}/libsample.so")
            );
            assert!(!plan.rules[0].opaque);
            assert!(plan.directory_replacements.is_empty());
            assert!(
                plan.skipped
                    .iter()
                    .any(|(_, reason)| reason == &format!("dir-replace-blocked:/{target}"))
            );
        }
    }
}

#[test]
fn nonempty_stock_directory_degrades_to_merge() {
    for opaque in [false, true] {
        let fs = base()
            .present("/product")
            .dir(&format!("{MODULE}/product"), &["fonts"])
            .dir(&format!("{MODULE}/product/fonts"), &[".replace", "new"])
            .entry(&format!("{MODULE}/product/fonts/.replace"), EntryKind::File)
            .entry(&format!("{MODULE}/product/fonts/new"), EntryKind::File)
            .dir("/product/fonts", &["stock"]);
        let fs = if opaque {
            let mut fs = fs.opaque(&format!("{MODULE}/product/fonts"));
            fs.dirs.insert(
                PathBuf::from(format!("{MODULE}/product/fonts")),
                vec!["new".to_string()],
            );
            fs
        } else {
            fs
        };
        let plan = collect_rules(
            &fs,
            &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
        )
        .unwrap();
        assert_eq!(
            plan.rules,
            vec![Rule::redirect(
                "/product/fonts/new",
                format!("{MODULE}/product/fonts/new")
            )]
        );
        assert!(
            plan.skipped
                .iter()
                .any(|(_, reason)| reason == "dir-replace-nonempty:/product/fonts")
        );
    }
}

#[test]
fn empty_stock_directory_allows_allowlisted_replacement() {
    let fs = base()
        .present("/product")
        .dir(&format!("{MODULE}/product"), &["fonts"])
        .dir(&format!("{MODULE}/product/fonts"), &[".replace"])
        .dir("/product/fonts", &[]);
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![Rule::redirect(
            "/product/fonts",
            format!("{MODULE}/product/fonts")
        )]
    );
    assert!(!plan.rules[0].opaque);
    assert!(plan.directory_replacements.contains("/product/fonts"));
}

#[test]
fn unlisted_empty_target_still_merges() {
    let fs = base()
        .dir(&format!("{MODULE}/system"), &["etc"])
        .dir(&format!("{MODULE}/system/etc"), &["foo"])
        .dir(&format!("{MODULE}/system/etc/foo"), &[".replace", "new"])
        .entry(&format!("{MODULE}/system/etc/foo/new"), EntryKind::File)
        .dir("/system/etc/foo", &[]);
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(plan.rules[0].virtual_path, "/system/etc/foo/new");
    assert!(plan.directory_replacements.is_empty());
    assert!(
        plan.skipped
            .iter()
            .any(|(_, reason)| reason == "dir-replace-blocked:/system/etc/foo")
    );
}

#[test]
fn remove_marker_whiteouts_its_directory_without_injecting_the_marker() {
    let fs = base()
        .dir(&format!("{MODULE}/system"), &["priv-app"])
        .dir(&format!("{MODULE}/system/priv-app"), &["Old"])
        .dir(&format!("{MODULE}/system/priv-app/Old"), &[REMOVE_MARKER])
        .entry(
            &format!("{MODULE}/system/priv-app/Old/{REMOVE_MARKER}"),
            EntryKind::File,
        );
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(plan.rules, vec![Rule::whiteout("/system/priv-app/Old")]);
    assert!(!plan.rules[0].opaque);
}

#[test]
fn remove_marker_never_takes_over_a_protected_root() {
    let fs = base()
        .dir(&format!("{MODULE}/vendor"), &[REMOVE_MARKER, "libfoo.so"])
        .entry(&format!("{MODULE}/vendor/{REMOVE_MARKER}"), EntryKind::File)
        .entry(&format!("{MODULE}/vendor/libfoo.so"), EntryKind::File);
    let plan = collect_rules(
        &fs,
        &config(Path::new(MODULES), Path::new("/"), Path::new("/system")),
    )
    .unwrap();
    assert_eq!(
        plan.rules,
        vec![Rule::redirect(
            "/vendor/libfoo.so",
            format!("{MODULE}/vendor/libfoo.so")
        )]
    );
    assert!(
        plan.skipped
            .iter()
            .any(|(_, reason)| reason == "dir-remove-blocked:/vendor")
    );
}

#[test]
fn directory_aliases_merge_once_and_cycles_are_bounded() {
    use std::os::unix::fs::symlink;
    let temp = tempfile::tempdir().unwrap();
    let modules = temp.path().join("modules");
    let module = modules.join("m");
    let root = temp.path().join("root");
    std::fs::create_dir_all(module.join("odm/lib64")).unwrap();
    std::fs::create_dir_all(module.join("system")).unwrap();
    std::fs::create_dir_all(root.join("odm")).unwrap();
    std::fs::create_dir_all(root.join("system")).unwrap();
    std::fs::write(module.join("odm/lib64/liboplusvibratoreffect.so"), b"test").unwrap();
    symlink("../odm", module.join("system/odm")).unwrap();
    symlink(".", module.join("odm/loop")).unwrap();
    symlink("self", module.join("odm/self")).unwrap();
    let fs = StdFs::new(|_| Ok(false));
    let system = root.join("system");
    let cfg = config(&modules, &root, &system);
    let plan = collect_rules(&fs, &cfg).unwrap();
    assert_eq!(plan.rules.len(), 1);
    assert_eq!(
        plan.rules[0].virtual_path,
        "/odm/lib64/liboplusvibratoreffect.so"
    );
    assert_eq!(
        plan.rules[0].real_path.as_deref(),
        module.join("odm/lib64/liboplusvibratoreffect.so").to_str()
    );
    assert!(
        plan.skipped
            .iter()
            .any(|(_, reason)| reason.starts_with("directory-cycle-or-depth:"))
    );
    assert!(
        plan.skipped
            .iter()
            .any(|(_, reason)| reason.starts_with("symlink-unresolved:"))
    );
    assert_eq!(plan.rules, collect_rules(&fs, &cfg).unwrap().rules);
}
