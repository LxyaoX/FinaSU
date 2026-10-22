use anyhow::{Context, Result};
use std::{ffi::CString, fs, path::Path};

use crate::ksucalls;

pub fn load_module(module: &Path, params: &str) -> Result<()> {
    let module = module
        .canonicalize()
        .with_context(|| format!("resolve module path failed: {}", module.display()))?;
    let module_data =
        fs::read(&module).with_context(|| format!("read module failed: {}", module.display()))?;
    let cparams = CString::new(params).context("module parameters contain NUL")?;

    ksuinit::load_module_with_resolver(&module_data, &cparams, |names| {
        if ksucalls::get_info().uapi_version >= 6 {
            ksucalls::lookup_symbols(names).or_else(|error| {
                log::warn!("kernel symbol lookup failed: {error:#}; using /proc/kallsyms");
                ksuinit::resolve_kallsyms(names)
            })
        } else {
            ksuinit::resolve_kallsyms(names)
        }
    })
    .with_context(|| format!("load module failed: {}", module.display()))
}
