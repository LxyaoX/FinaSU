use anyhow::{Context, Result, bail};
use serde::{Deserialize, Serialize};
use std::fs::File;
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::Path;

pub const FIELD_VALID_MASK: u32 = (1 << 6) - 1;
pub const MAX_CONFIG_BYTES: usize = 64 * 1024;
pub const MAX_TARGETS: usize = 128;

pub struct ConfigSnapshot {
    pub bytes: Vec<u8>,
    pub mode: u32,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum UtsMode {
    #[default]
    #[serde(alias = "scoped")]
    Selected,
    Global,
}

pub fn parse_snapshot(snapshot: &ConfigSnapshot) -> Result<ConfigFile> {
    if snapshot.bytes.len() > MAX_CONFIG_BYTES {
        bail!("UTS View config is too large");
    }
    let input = std::str::from_utf8(&snapshot.bytes).context("UTS View config is not UTF-8")?;
    let config = serde_json::from_str::<ConfigFile>(input).context("Parse UTS View config")?;
    config.validate()?;
    Ok(config)
}

pub fn write_snapshot_at(dir: &str, path: &str, snapshot: &ConfigSnapshot) -> Result<()> {
    let mut temp = tempfile::NamedTempFile::new_in(dir).context("Create UTS View temp file")?;
    temp.as_file()
        .set_permissions(std::fs::Permissions::from_mode(snapshot.mode))
        .context("Set UTS View temp permissions")?;
    temp.write_all(&snapshot.bytes)
        .context("Write UTS View temp config")?;
    temp.as_file()
        .sync_all()
        .context("Sync UTS View temp config")?;
    temp.persist(Path::new(path))
        .map_err(|error| error.error)
        .context("Persist UTS View config")?;
    File::open(dir)
        .context("Open UTS View directory")?
        .sync_all()
        .context("Sync UTS View directory")?;
    Ok(())
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct ConfigFile {
    pub schema: u32,
    pub enabled: bool,
    #[serde(default)]
    pub mode: UtsMode,
    pub field_mask: u32,
    pub sysname: String,
    pub nodename: String,
    pub release: String,
    pub version: String,
    pub machine: String,
    pub domainname: String,
    #[serde(default)]
    pub targets: Vec<UtsTarget>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct UtsTarget {
    pub package: String,
    pub uid: u32,
}

#[derive(Debug, Clone, Serialize)]
pub struct FieldValues {
    pub sysname: String,
    pub nodename: String,
    pub release: String,
    pub version: String,
    pub machine: String,
    pub domainname: String,
}

impl ConfigFile {
    pub const fn empty(enabled: bool) -> Self {
        Self {
            schema: 1,
            enabled,
            mode: UtsMode::Selected,
            field_mask: 0,
            sysname: String::new(),
            nodename: String::new(),
            release: String::new(),
            version: String::new(),
            machine: String::new(),
            domainname: String::new(),
            targets: Vec::new(),
        }
    }

    pub fn validate(&self) -> Result<()> {
        if self.schema != 1 {
            bail!("unsupported UTS View config schema {}", self.schema);
        }
        if self.field_mask & !FIELD_VALID_MASK != 0 {
            bail!("field_mask contains unsupported bits");
        }
        if self.enabled && self.field_mask == 0 {
            bail!("enabled UTS View requires at least one field");
        }
        if self.mode == UtsMode::Selected && self.enabled && self.targets.is_empty() {
            bail!("selected UTS View mode requires at least one target app");
        }
        if self.targets.len() > MAX_TARGETS {
            bail!("UTS View supports at most {MAX_TARGETS} target apps");
        }
        for (index, target) in self.targets.iter().enumerate() {
            if target.package.is_empty() || target.package.len() > 255 {
                bail!("target package name is empty or too long");
            }
            if target.uid < 10000 {
                bail!("target {} is not an application UID", target.package);
            }
            if self.targets[..index]
                .iter()
                .any(|other| other.uid == target.uid)
            {
                bail!("duplicate target UID {}", target.uid);
            }
        }
        for (name, value) in self.fields() {
            if value.len() > 64 {
                bail!("{name} exceeds 64 bytes");
            }
            if value.as_bytes().contains(&0) {
                bail!("{name} contains NUL");
            }
        }
        if self.field_mask & field_bit(2) != 0
            && !self.release.is_empty()
            && !self
                .release
                .bytes()
                .all(|byte| byte.is_ascii_graphic() || byte == b' ')
        {
            bail!("release contains non-printable characters");
        }
        Ok(())
    }

    pub fn field_values(&self) -> FieldValues {
        FieldValues {
            sysname: self.sysname.clone(),
            nodename: self.nodename.clone(),
            release: self.release.clone(),
            version: self.version.clone(),
            machine: self.machine.clone(),
            domainname: self.domainname.clone(),
        }
    }

    fn fields(&self) -> [(&'static str, &str); 6] {
        [
            ("sysname", &self.sysname),
            ("nodename", &self.nodename),
            ("release", &self.release),
            ("version", &self.version),
            ("machine", &self.machine),
            ("domainname", &self.domainname),
        ]
    }
}

const fn field_bit(field: u32) -> u32 {
    1 << field
}

#[cfg(test)]
mod tests {
    use super::*;

    fn valid() -> ConfigFile {
        ConfigFile {
            schema: 1,
            enabled: true,
            mode: UtsMode::Selected,
            field_mask: 1 << 2,
            sysname: String::new(),
            nodename: String::new(),
            release: "6.1.75-android14".to_owned(),
            version: String::new(),
            machine: String::new(),
            domainname: String::new(),
            targets: vec![UtsTarget {
                package: "com.example.app".to_owned(),
                uid: 10123,
            }],
        }
    }

    #[test]
    fn validates_schema_mask_and_enabled_template() {
        assert!(valid().validate().is_ok());
        assert_eq!(0, ConfigFile::empty(false).field_mask);
        assert_eq!("6.1.75-android14", valid().field_values().release);

        let mut config = valid();
        config.schema = 2;
        assert!(config.validate().is_err());

        let mut config = valid();
        config.field_mask = 1 << 6;
        assert!(config.validate().is_err());

        let mut config = valid();
        config.enabled = true;
        config.field_mask = 0;
        assert!(config.validate().is_err());

        let mut config = valid();
        config.targets.clear();
        assert!(config.validate().is_err());

        let mut config = valid();
        config.mode = UtsMode::Global;
        config.targets.clear();
        assert!(config.validate().is_ok());

        let mut config = valid();
        config.targets.push(UtsTarget {
            package: "com.example.other".to_owned(),
            uid: 10123,
        });
        assert!(config.validate().is_err());
    }

    #[test]
    fn validates_text_length_nul_and_release_ascii() {
        let mut config = valid();
        config.release = "x".repeat(65);
        assert!(config.validate().is_err());

        let mut config = valid();
        config.release = "line\nbreak".to_owned();
        assert!(config.validate().is_err());

        let mut config = valid();
        config.version = "nul\0byte".to_owned();
        assert!(config.validate().is_err());
    }

    #[test]
    fn preserves_mask_semantics_independent_of_text_values() {
        let mut config = valid();
        config.enabled = false;
        config.field_mask = 0;
        config.release = "present-but-unselected".to_owned();
        assert!(config.validate().is_ok());

        let mut config = valid();
        config.release.clear();
        assert!(config.validate().is_ok());
    }

    #[test]
    fn serde_defaults_to_selected_and_rejects_unknown_fields() {
        let json = r#"{
            "schema":1,
            "enabled":false,
            "field_mask":0,
            "sysname":"",
            "nodename":"",
            "release":"",
            "version":"",
            "machine":"",
            "domainname":""
        }"#;
        let config: ConfigFile = serde_json::from_str(json).unwrap();
        assert!(config.validate().is_ok());
        assert_eq!(config.mode, UtsMode::Selected);
        assert!(config.targets.is_empty());
        let legacy_mode: ConfigFile = serde_json::from_str(
            &json.replace("\"enabled\":false", "\"enabled\":false,\"mode\":\"scoped\""),
        )
        .unwrap();
        assert_eq!(legacy_mode.mode, UtsMode::Selected);

        assert!(serde_json::from_str::<ConfigFile>(r#"{"schema":1}"#).is_err());
        assert!(
            serde_json::from_str::<ConfigFile>(&json.replace(
                "\"domainname\":\"\"",
                "\"domainname\":\"\",\"unknown\":true"
            ))
            .is_err()
        );
    }

    #[test]
    fn malformed_config_snapshot_preserves_exact_bytes() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("uts_view.json");
        let snapshot = ConfigSnapshot {
            bytes: b"{\"schema\":1,\xffbroken".to_vec(),
            mode: 0o600,
        };

        write_snapshot_at(
            dir.path().to_str().unwrap(),
            path.to_str().unwrap(),
            &snapshot,
        )
        .unwrap();

        assert_eq!(snapshot.bytes, std::fs::read(path).unwrap());
        assert!(parse_snapshot(&snapshot).is_err());
    }

    #[test]
    fn oversized_utf8_config_is_not_treated_as_a_valid_template() {
        let snapshot = ConfigSnapshot {
            bytes: vec![b' '; MAX_CONFIG_BYTES + 1],
            mode: 0o600,
        };

        assert!(parse_snapshot(&snapshot).is_err());
    }
}
