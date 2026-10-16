//! Host tests for the NoMount transport and wire encoding.

use std::cell::RefCell;
use std::rc::Rc;

use super::*;

/// How the fake kernel behaves.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Mode {
    /// Answers everything, keeps what it is given.
    Ready,
    /// Refuses every command, as an unregistered key type does.
    Silent,
    /// Accepts rules but reports none back.
    ForgetsRules,
    /// Answers the version but refuses the listing.
    ListingRefused,
}

struct FakeState {
    rules: Vec<Rule>,
    uids: Vec<u32>,
    isolated: bool,
    commands: Vec<Command>,
}

struct FakeKernel {
    mode: Mode,
    version: &'static str,
    state: RefCell<FakeState>,
}

impl FakeKernel {
    fn new(mode: Mode) -> Self {
        Self {
            mode,
            version: "21",
            state: RefCell::new(FakeState {
                rules: Vec::new(),
                uids: Vec::new(),
                isolated: false,
                commands: Vec::new(),
            }),
        }
    }

    fn with_version(mut self, version: &'static str) -> Self {
        self.version = version;
        self
    }

    fn commands(&self) -> Vec<Command> {
        self.state.borrow().commands.clone()
    }

    fn written_rules(&self) -> Vec<Rule> {
        self.state.borrow().rules.clone()
    }
}

fn decode_command(raw: u32) -> Command {
    match raw {
        1 => Command::GetVersion,
        2 => Command::AddRule,
        3 => Command::DelRule,
        4 => Command::AddUid,
        5 => Command::DelUid,
        7 => Command::ClearRules,
        9 => Command::GetList,
        10 => Command::GetUids,
        11 => Command::BlockIsolatedUids,
        12 => Command::GetIsolatedState,
        other => panic!("unexpected command {other}"),
    }
}

/// Encode one rule the way the kernel would report it.
///
/// The client encoder only produces redirects and whiteouts; the kernel also
/// reports the intermediate directories it created, which carry no target.
fn kernel_record(rule: &Rule) -> Vec<u8> {
    let flags = if rule.whiteout {
        WHITEOUT_FLAG
    } else if rule.virtual_dir {
        VIRTUAL_DIR_FLAG
    } else {
        0
    };
    let real = rule.real_path.as_deref().unwrap_or_default();
    let mut out = Vec::new();
    out.extend_from_slice(&flags.to_le_bytes());
    out.extend_from_slice(&rule.uid.to_le_bytes());
    out.extend_from_slice(
        &u16::try_from(rule.virtual_path.len())
            .expect("path fits")
            .to_le_bytes(),
    );
    out.extend_from_slice(
        &u16::try_from(real.len())
            .expect("target fits")
            .to_le_bytes(),
    );
    out.extend_from_slice(rule.virtual_path.as_bytes());
    out.extend_from_slice(real.as_bytes());
    out
}

impl Transport for Rc<FakeKernel> {
    fn send(&self, payload: &mut Payload) -> Result<()> {
        self.dispatch(payload)
    }
}

impl FakeKernel {
    fn dispatch(&self, payload: &mut Payload) -> Result<()> {
        let magic = payload.magic;
        assert_eq!(magic, MAGIC, "payload magic must be stamped");
        let command = decode_command(payload.cmd);
        self.state.borrow_mut().commands.push(command);

        if self.mode == Mode::Silent {
            payload.status = -1;
            let command = decode_command(payload.cmd);
            bail!("nomount: command {command:?} failed with -1");
        }

        match command {
            Command::GetVersion => {
                let bytes = self.version.as_bytes();
                payload.buffer[..bytes.len()].copy_from_slice(bytes);
                payload.data_size = u32::try_from(bytes.len())?;
            }
            Command::GetList => {
                if self.mode == Mode::ListingRefused {
                    payload.status = -1;
                    bail!("nomount: listing refused");
                }
                let rules: Vec<Rule> = if self.mode == Mode::ForgetsRules {
                    Vec::new()
                } else {
                    self.state.borrow().rules.clone()
                };
                let start = usize::try_from(payload.arg1)?;
                let mut cursor = 0_usize;
                for rule in rules.iter().skip(start) {
                    let record = kernel_record(rule);
                    if cursor + record.len() > BUFFER_SIZE {
                        break;
                    }
                    payload.buffer[cursor..cursor + record.len()].copy_from_slice(&record);
                    cursor += record.len();
                }
                payload.data_size = u32::try_from(cursor)?;
                payload.arg1 = u32::try_from(start + rules.len().saturating_sub(start))?;
            }
            Command::AddRule => {
                let len = usize::try_from(payload.data_size)?;
                let decoded = parse_list_records(&payload.buffer[..len])?;
                let mut state = self.state.borrow_mut();
                // Mirror the kernel: a rule for the same virtual path and uid
                // replaces the previous one instead of stacking.
                for rule in decoded {
                    state.rules.retain(|existing| {
                        existing.virtual_path != rule.virtual_path || existing.uid != rule.uid
                    });
                    state.rules.push(rule);
                }
            }
            Command::DelRule => {
                let len = usize::try_from(payload.data_size)?;
                let mut cursor = 0_usize;
                let mut doomed = Vec::new();
                while cursor + 6 <= len {
                    let v_len = usize::from(u16::from_le_bytes([
                        payload.buffer[cursor + 4],
                        payload.buffer[cursor + 5],
                    ]));
                    doomed.push(
                        String::from_utf8_lossy(&payload.buffer[cursor + 6..cursor + 6 + v_len])
                            .to_string(),
                    );
                    cursor += 6 + v_len;
                }
                self.state
                    .borrow_mut()
                    .rules
                    .retain(|rule| !doomed.contains(&rule.virtual_path));
            }
            Command::AddUid => self.state.borrow_mut().uids.push(payload.target_uid),
            Command::DelUid => self
                .state
                .borrow_mut()
                .uids
                .retain(|uid| *uid != payload.target_uid),
            Command::ClearRules => self.state.borrow_mut().rules.clear(),
            Command::GetUids => {
                let start = usize::try_from(payload.arg1)?;
                let mut cursor = 0_usize;
                let mut written = 0_usize;
                for uid in self.state.borrow().uids.iter().skip(start) {
                    if cursor + 4 > BUFFER_SIZE {
                        break;
                    }
                    payload.buffer[cursor..cursor + 4].copy_from_slice(&uid.to_le_bytes());
                    cursor += 4;
                    written += 1;
                }
                payload.data_size = u32::try_from(cursor)?;
                payload.arg1 = u32::try_from(start + written)?;
            }
            // The kernel replies with "1\n" or "0\n".
            Command::BlockIsolatedUids => {
                self.state.borrow_mut().isolated = payload.arg1 == 1;
            }
            Command::GetIsolatedState => {
                let isolated = self.state.borrow().isolated;
                payload.buffer[0] = if isolated { b'1' } else { b'0' };
                payload.buffer[1] = b'\n';
                payload.data_size = 2;
            }
        }
        payload.status = 0;
        Ok(())
    }
}

/// Build a driver plus a handle that can still inspect the fake kernel.
fn driver(mode: Mode) -> (Driver<Rc<FakeKernel>>, Rc<FakeKernel>) {
    let kernel = Rc::new(FakeKernel::new(mode));
    (Driver::new(Rc::clone(&kernel)), kernel)
}

/// As [`driver`], with a specific API version string.
fn driver_versioned(mode: Mode, version: &'static str) -> Driver<Rc<FakeKernel>> {
    Driver::new(Rc::new(FakeKernel::new(mode).with_version(version)))
}

#[test]
fn payload_is_exactly_one_page() {
    assert_eq!(size_of::<Payload>(), PAGE_SIZE);
    assert_eq!(std::mem::offset_of!(Payload, buffer), 28);
    assert_eq!(BUFFER_SIZE, 4068);
    assert_eq!(MAGIC.to_be_bytes(), *b"\0NOMOUNT");
}

#[test]
fn api_version_parses_the_leading_integer() {
    assert_eq!(parse_api_version("21"), Some(21));
    assert_eq!(parse_api_version("21 (1234)"), Some(21));
    assert_eq!(parse_api_version(" 20\n"), Some(20));
    assert_eq!(parse_api_version("unknown"), None);
    assert_eq!(parse_api_version(""), None);
}

#[test]
fn only_an_absent_driver_is_worth_loading() {
    // A registered key type cannot be registered twice, so a ready or outdated
    // driver (kernel built-in included) must never be followed by a load.
    assert!(should_load(&DriverProbe {
        state: DriverState::Absent,
        version: None,
    }));
    assert!(!should_load(&DriverProbe {
        state: DriverState::Outdated,
        version: Some("20".to_string()),
    }));
    assert!(!should_load(&DriverProbe {
        state: DriverState::Ready,
        version: Some("21".to_string()),
    }));
    assert_eq!(DriverKind::Builtin.as_str(), "builtin");
    assert_eq!(DriverKind::Module.as_str(), "module");
}

#[test]
fn module_loaded_matches_proc_modules() {
    assert!(module_loaded(
        "nomount 123456 0 - Live 0x0\nother 1 0 - Live\n"
    ));
    assert!(!module_loaded("nomountx 123456 0 - Live\n"));
    assert!(!module_loaded(""));
}

#[test]
fn redirect_and_whiteout_records_follow_the_wire_layout() {
    let redirect = Rule::redirect("/system/etc/hosts", "/data/adb/modules/m/system/etc/hosts");
    let batches = encode_batches(std::slice::from_ref(&redirect)).unwrap();
    assert_eq!(batches.len(), 1);
    let bytes = &batches[0];
    assert_eq!(
        u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        0
    );
    assert_eq!(
        u32::from_le_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]),
        0
    );
    let v_len = usize::from(u16::from_le_bytes([bytes[8], bytes[9]]));
    let r_len = usize::from(u16::from_le_bytes([bytes[10], bytes[11]]));
    assert_eq!(v_len, "/system/etc/hosts".len());
    assert_eq!(r_len, "/data/adb/modules/m/system/etc/hosts".len());
    assert_eq!(&bytes[12..12 + v_len], b"/system/etc/hosts");

    let whiteout = Rule::whiteout("/system/etc/removed");
    let batches = encode_batches(std::slice::from_ref(&whiteout)).unwrap();
    let bytes = &batches[0];
    assert_eq!(
        u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]),
        WHITEOUT_FLAG
    );
    let r_len = usize::from(u16::from_le_bytes([bytes[10], bytes[11]]));
    assert_eq!(r_len, 0);
    assert_eq!(bytes.len(), 12 + "/system/etc/removed".len());
}

#[test]
fn batches_split_before_overflowing_the_payload_buffer() {
    let rules: Vec<Rule> = (0..80)
        .map(|index| {
            Rule::redirect(
                format!("/system/etc/file-{index:04}"),
                format!("/data/adb/modules/m/system/etc/file-{index:04}"),
            )
        })
        .collect();
    let batches = encode_batches(&rules).unwrap();
    assert!(batches.len() > 1, "expected the rules to be split");
    for batch in &batches {
        assert!(batch.len() <= BUFFER_SIZE);
    }
    let total: usize = batches.iter().map(Vec::len).sum();
    assert_eq!(total, rules.iter().map(record_len).sum::<usize>());
}

fn record_len(rule: &Rule) -> usize {
    12 + rule.virtual_path.len() + rule.real_path.as_deref().unwrap_or_default().len()
}

#[test]
fn redirect_without_a_target_is_rejected() {
    let broken = Rule {
        virtual_path: "/system/etc/hosts".to_string(),
        real_path: None,
        whiteout: false,
        virtual_dir: false,
        uid: 0,
    };
    assert!(encode_batches(std::slice::from_ref(&broken)).is_err());
}

#[test]
fn rule_lists_round_trip_and_reject_truncation() {
    let rules = vec![
        Rule::redirect("/system/etc/hosts", "/data/adb/modules/m/system/etc/hosts"),
        Rule::whiteout("/system/etc/gone"),
    ];
    let bytes = encode_batches(&rules).unwrap().concat();
    assert_eq!(parse_list_records(&bytes).unwrap(), rules);
    assert!(parse_list_records(&bytes[..bytes.len() - 1]).is_err());
}

#[test]
fn apply_rules_reports_the_readback_count() {
    let (driver, kernel) = driver(Mode::Ready);
    let rules = vec![Rule::redirect(
        "/system/etc/hosts",
        "/data/adb/modules/m/system/etc/hosts",
    )];
    assert_eq!(driver.apply_rules(&rules).unwrap(), 1);
    assert_eq!(kernel.written_rules(), rules);
}

#[test]
fn apply_rules_fails_when_the_kernel_does_not_keep_them() {
    let (driver, _kernel) = driver(Mode::ForgetsRules);
    let rules = vec![Rule::redirect(
        "/system/etc/hosts",
        "/data/adb/modules/m/system/etc/hosts",
    )];
    let error = driver.apply_rules(&rules).unwrap_err().to_string();
    assert!(
        error.contains("did not accept"),
        "unexpected error: {error}"
    );
}

#[test]
fn probe_reports_absent_outdated_and_ready() {
    let (absent, _kernel) = driver(Mode::Silent);
    assert_eq!(absent.probe().state, DriverState::Absent);

    let outdated = driver_versioned(Mode::Ready, "20");
    let probe = outdated.probe();
    assert_eq!(probe.state, DriverState::Outdated);
    assert_eq!(probe.version.as_deref(), Some("20"));
    assert!(ensure_usable(&probe).is_err());

    let (refused, _kernel) = driver(Mode::ListingRefused);
    assert_eq!(refused.probe().state, DriverState::Outdated);

    let (ready, _kernel) = driver(Mode::Ready);
    let probe = ready.probe();
    assert!(probe.ready());
    assert_eq!(probe.version.as_deref(), Some("21"));
    assert!(ensure_usable(&probe).is_ok());
}

#[test]
fn uid_exclusions_are_applied_and_listed() {
    let (driver, kernel) = driver(Mode::Ready);
    driver.add_uids(&[1000, 10123]).unwrap();
    assert_eq!(driver.list_uids().unwrap(), vec![1000, 10123]);
    driver.del_uids(&[1000]).unwrap();
    assert_eq!(driver.list_uids().unwrap(), vec![10123]);
    driver.del_uids(&[10123]).unwrap();
    assert!(driver.list_uids().unwrap().is_empty());
    // Every step must have reached the kernel, whatever the paging did.
    assert!(kernel.commands().contains(&Command::AddUid));
    assert!(kernel.commands().contains(&Command::DelUid));
}

#[test]
fn deleting_rules_batches_and_clears_state() {
    let (driver, _kernel) = driver(Mode::Ready);
    let rules: Vec<Rule> = (0..60)
        .map(|index| {
            Rule::redirect(
                format!("/system/etc/file-{index:04}"),
                format!("/data/adb/modules/m/system/etc/file-{index:04}"),
            )
        })
        .collect();
    driver.apply_rules(&rules).unwrap();
    assert_eq!(driver.list_rules().unwrap().len(), rules.len());

    let paths: Vec<String> = rules.iter().map(|rule| rule.virtual_path.clone()).collect();
    driver.delete_rules(&paths).unwrap();
    assert!(driver.list_rules().unwrap().is_empty());
}

#[test]
fn reconcile_rules_drops_stale_paths() {
    let (driver, kernel) = driver(Mode::Ready);
    driver
        .apply_rules(&[
            Rule::redirect("/system/etc/old", "/data/adb/modules/m/system/etc/old"),
            Rule::redirect("/system/etc/keep", "/data/adb/modules/m/system/etc/keep"),
        ])
        .unwrap();

    let (applied, removed) = driver
        .reconcile_rules(&[Rule::redirect(
            "/system/etc/keep",
            "/data/adb/modules/m/system/etc/keep",
        )])
        .unwrap();
    assert_eq!((applied, removed), (1, 1));
    let paths: Vec<String> = kernel
        .written_rules()
        .into_iter()
        .map(|rule| rule.virtual_path)
        .collect();
    assert_eq!(paths, vec!["/system/etc/keep"]);
}

#[test]
fn a_later_rule_for_the_same_path_wins() {
    // The kernel replaces a rule with the same virtual path and uid, so the
    // last module to claim a path wins. Documented because the built-in engine
    // resolves the same conflict the other way (first wins).
    let (driver, kernel) = driver(Mode::Ready);
    driver
        .apply_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/first/system/etc/hosts",
        )])
        .unwrap();
    driver
        .apply_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/second/system/etc/hosts",
        )])
        .unwrap();
    let rules = kernel.written_rules();
    assert_eq!(rules.len(), 1);
    assert_eq!(
        rules[0].real_path.as_deref(),
        Some("/data/adb/modules/second/system/etc/hosts")
    );
}

#[test]
fn reconcile_keeps_kernel_created_virtual_directories() {
    let (driver, kernel) = driver(Mode::Ready);
    // The fake kernel reports one of our rules and one directory it made up.
    kernel.state.borrow_mut().rules.push(Rule {
        virtual_path: "/system/etc/hosts".to_string(),
        real_path: Some("/data/adb/modules/m/system/etc/hosts".to_string()),
        whiteout: false,
        virtual_dir: false,
        uid: 0,
    });
    kernel.state.borrow_mut().rules.push(Rule {
        virtual_path: "/system/etc/newdir".to_string(),
        real_path: None,
        whiteout: false,
        virtual_dir: true,
        uid: 0,
    });

    let (applied, removed) = driver
        .reconcile_rules(&[Rule::redirect(
            "/system/etc/hosts",
            "/data/adb/modules/m/system/etc/hosts",
        )])
        .unwrap();
    assert_eq!(
        (applied, removed),
        (1, 0),
        "the virtual directory must survive"
    );
    assert!(
        kernel
            .written_rules()
            .iter()
            .any(|rule| rule.virtual_dir && rule.virtual_path == "/system/etc/newdir")
    );
}

#[test]
fn visibility_config_round_trips_and_rejects_junk() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("nomount_uids");

    // Missing file: nothing wanted, nothing blocked.
    assert_eq!(read_uid_config(&path).unwrap(), UidConfig::default());

    write_uid_config(
        &path,
        &UidConfig {
            uids: vec![1000, 10123],
            block_isolated: true,
        },
    )
    .unwrap();
    let parsed = read_uid_config(&path).unwrap();
    assert_eq!(parsed.uids, vec![1000, 10123]);
    assert!(parsed.block_isolated);

    std::fs::write(&path, "isolated=on\nnot-a-uid\n").unwrap();
    assert!(read_uid_config(&path).is_err());
    std::fs::write(&path, "isolated=maybe\n").unwrap();
    assert!(read_uid_config(&path).is_err());
}

#[test]
fn sync_uids_applies_the_diff_and_the_isolated_flag() {
    let (driver, _kernel) = driver(Mode::Ready);
    driver.add_uids(&[1000, 2000]).unwrap();

    let (added, removed) = sync_uids(
        &driver,
        &UidConfig {
            uids: vec![2000, 3000],
            block_isolated: true,
        },
    )
    .unwrap();
    assert_eq!((added, removed), (1, 1));
    assert_eq!(driver.list_uids().unwrap(), vec![2000, 3000]);
    assert!(driver.isolated_state().unwrap());

    // A second identical call changes nothing.
    let (added, removed) = sync_uids(
        &driver,
        &UidConfig {
            uids: vec![2000, 3000],
            block_isolated: true,
        },
    )
    .unwrap();
    assert_eq!((added, removed), (0, 0));
}

#[test]
fn runtime_record_is_key_value_text() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("nomount_runtime");
    write_runtime_record(
        &path,
        &RuntimeRecord {
            boot_id: "boot-1".to_string(),
            driver_state: "ready".to_string(),
            driver_version: "21".to_string(),
            rules_applied: 42,
            rules_removed: 3,
            uid_count: 2,
            isolated_blocked: true,
            error: None,
        },
    )
    .unwrap();
    let text = std::fs::read_to_string(&path).unwrap();
    for expected in [
        "boot_id=boot-1",
        "driver_state=ready",
        "driver_version=21",
        "rules_applied=42",
        "rules_removed=3",
        "uid_count=2",
        "isolated_blocked=true",
        "error=",
    ] {
        assert!(text.contains(expected), "missing {expected} in {text}");
    }
}
