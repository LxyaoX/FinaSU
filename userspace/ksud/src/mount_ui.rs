#[cfg(target_os = "android")]
use std::{path::Path, time::Duration};

#[cfg(target_os = "android")]
use crate::{defs, mount_guard, utils};

fn delay(content: &str) -> Option<u64> {
    if content.lines().any(|line| line.trim() == "off") {
        return None;
    }
    Some(
        content
            .lines()
            .filter_map(|line| line.strip_prefix("delay=")?.parse::<u64>().ok())
            .next_back()
            .unwrap_or(180)
            .clamp(30, 900),
    )
}

fn ready(boot: Option<&str>, surface: Option<&str>, animation: Option<&str>) -> bool {
    boot == Some("1") && surface == Some("running") && animation == Some("stopped")
}

#[cfg(target_os = "android")]
pub fn previous_failure(boot_id: &str) -> Option<String> {
    previous_failure_at(Path::new(defs::MOUNT_UI_PENDING), boot_id)
}

#[cfg(any(target_os = "android", test))]
fn previous_failure_at(path: &std::path::Path, boot_id: &str) -> Option<String> {
    let record = crate::mount_guard::read(path)?;
    if record.boot_id == boot_id || record.provider != "nomount" || record.detail != "ui-pending" {
        return None;
    }
    Some(format!(
        "NoMount UI readiness was not confirmed in boot {}; using builtin this boot (not proof of a NoMount fault)",
        record.boot_id
    ))
}

#[cfg(target_os = "android")]
pub fn clear() {
    mount_guard::clear(Path::new(defs::MOUNT_UI_PENDING));
}

#[cfg(target_os = "android")]
pub fn arm(boot_id: &str, lock_fd: std::os::fd::RawFd) {
    let content = std::fs::read_to_string(defs::MOUNT_UI_CONFIG).unwrap_or_default();
    let Some(seconds) = delay(&content) else {
        return;
    };
    if let Err(error) = mount_guard::begin(
        Path::new(defs::MOUNT_UI_PENDING),
        mount_guard::Stage::Inject,
        "nomount",
        "ui-pending",
        boot_id,
    ) {
        log::warn!("nomount: cannot arm UI guard: {error:#}");
        return;
    }
    let _ = mount_guard::append_log(
        Path::new(defs::MOUNT_LOG),
        &format!("--- nomount UI boot={boot_id}\nobservation armed delay={seconds}s\n"),
        defs::MOUNT_LOG_MAX_BYTES,
    );
    if !utils::create_daemon_with(true, || {
        unsafe { libc::close(lock_fd) };
        Ok(())
    })
    .unwrap_or(false)
    {
        return;
    }
    std::thread::sleep(Duration::from_secs(seconds));
    let boot = utils::getprop("sys.boot_completed");
    let surface = utils::getprop("init.svc.surfaceflinger");
    let animation = utils::getprop("init.svc.bootanim");
    if mount_guard::read(Path::new(defs::MOUNT_UI_PENDING))
        .is_some_and(|record| record.boot_id == boot_id)
    {
        let healthy = ready(boot.as_deref(), surface.as_deref(), animation.as_deref());
        let message = format!(
            "UI observation after {seconds}s: boot_completed={boot:?} surfaceflinger={surface:?} bootanim={animation:?}; {}",
            if healthy {
                "readiness signals observed (not a display health guarantee)"
            } else {
                "readiness unconfirmed; next boot uses builtin"
            }
        );
        let _ = mount_guard::append_log(
            Path::new(defs::MOUNT_LOG),
            &format!("--- nomount UI boot={boot_id}\n{message}\n"),
            defs::MOUNT_LOG_MAX_BYTES,
        );
        if healthy {
            clear();
        } else {
            crate::magic_mount::record_ui_warning(boot_id, &message);
        }
    }
    unsafe { libc::_exit(0) };
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deadline_is_bounded_and_can_be_disabled() {
        assert_eq!(delay(""), Some(180));
        assert_eq!(delay("delay=1"), Some(30));
        assert_eq!(delay("delay=9999"), Some(900));
        assert_eq!(delay("off"), None);
    }

    #[test]
    fn boot_completed_alone_is_not_ui_readiness() {
        assert!(!ready(Some("1"), Some("stopped"), Some("stopped")));
        assert!(!ready(Some("1"), Some("running"), Some("running")));
        assert!(!ready(None, Some("running"), Some("stopped")));
        assert!(ready(Some("1"), Some("running"), Some("stopped")));
    }

    #[test]
    fn pending_ui_survives_boot_completion_guard_cleanup() {
        let temp = tempfile::tempdir().unwrap();
        let pending = temp.path().join("ui");
        let inflight = temp.path().join("inflight");
        let boot_id = crate::mount_guard::current_boot_id();
        for path in [&pending, &inflight] {
            crate::mount_guard::begin(
                path,
                crate::mount_guard::Stage::Inject,
                "nomount",
                "ui-pending",
                &boot_id,
            )
            .unwrap();
        }
        crate::mount_guard::clear_if_ours(&inflight);
        assert!(!inflight.exists());
        assert!(previous_failure_at(&pending, &boot_id).is_none());
        assert!(
            previous_failure_at(&pending, "new")
                .unwrap()
                .contains("builtin")
        );
        crate::mount_guard::clear(&pending);
        assert!(previous_failure_at(&pending, "new").is_none());
    }
}
