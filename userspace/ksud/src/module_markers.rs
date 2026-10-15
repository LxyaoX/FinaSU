//! Marker handling shared by the module lifecycle commands.
//!
//! This lives outside `module.rs` on purpose: that module only compiles for Android, while the
//! marker dance is plain filesystem work that deserves host unit tests.

use std::path::Path;

use anyhow::{Context, Result};

/// Clear one marker file on both boot-promotion copies, rolling back on rc failure.
///
/// Boot promotion copies the pending module over the installed one, so a pending copy inherits
/// the installed module's markers. Clearing only the installed copy would let a bare
/// `enable`/`undo uninstall` silently revert on the next boot.
pub fn clear_activation_markers(
    installed: &Path,
    pending: &Path,
    marker: &str,
    mut refresh: impl FnMut() -> Result<()>,
) -> Result<()> {
    let mut previous = Vec::new();
    for directory in [installed, pending] {
        let path = directory.join(marker);
        match std::fs::read(&path) {
            Ok(bytes) => previous.push((path, bytes)),
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
            Err(error) => return Err(error.into()),
        }
    }

    let result = (|| {
        for (path, _) in &previous {
            std::fs::remove_file(path)?;
        }
        refresh()
    })();
    if let Err(error) = result {
        for (path, bytes) in previous {
            std::fs::write(path, bytes).context("Restore activation marker")?;
        }
        refresh().context("Restore activation rc")?;
        return Err(error);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use anyhow::bail;

    #[test]
    fn activation_markers_clear_together_and_restore_on_failure() -> Result<()> {
        let dir = tempfile::tempdir()?;
        let installed = dir.path().join("installed");
        let pending = dir.path().join("pending");
        std::fs::create_dir_all(&installed)?;
        std::fs::create_dir_all(&pending)?;

        for marker in ["disable", "remove"] {
            for path in [&installed, &pending] {
                std::fs::write(path.join(marker), b"original")?;
            }

            let mut calls = 0;
            assert!(
                clear_activation_markers(&installed, &pending, marker, || {
                    calls += 1;
                    if calls == 1 {
                        bail!("rc failure");
                    }
                    Ok(())
                })
                .is_err()
            );
            for path in [&installed, &pending] {
                assert_eq!(std::fs::read(path.join(marker))?, b"original");
            }

            clear_activation_markers(&installed, &pending, marker, || Ok(()))?;
            for path in [&installed, &pending] {
                assert!(!path.join(marker).exists());
            }
        }
        Ok(())
    }
}
