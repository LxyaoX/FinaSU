# NoMount upstream provenance

NoMount is vendored into FolkSU so that the kernel module is built by this
repository's own pipeline instead of being downloaded from upstream releases.
This mirrors how FolkZygisk vendors ReZygisk under `userspace/folkzygisk`.

Only the kernel-side sources are imported here. FolkSU reimplements the
userspace control path (the `nm` CLI's keyring payload protocol) in Rust inside
`userspace/ksud`, and generates injection rules from the module directory in
Rust, so no upstream shell scripts or WebUI are imported.

## Pinned sources

- NoMount: https://github.com/maxsteeel/nomount
  commit `7046c7ad3ee0bf4ae66e80f6b93b1e30db0cb4e3` (tag `v2.1.1`).
  Imported from `kernel/src/` only:

  | upstream path | vendored path | sha256 |
  | --- | --- | --- |
  | `kernel/src/nomount.c` | `kernel/src/nomount.c` | `b082476d72ee755e3549b850fd3cf1e233234b6440f3d844b5ea190e392e0c69` |
  | `kernel/src/nomount.h` | `kernel/src/nomount.h` | `8a805e7fe3bbc6bf8daf2f4fd26c1f00d880a5480ca66dd013485509028146ed` |
  | `kernel/src/Kconfig` | `kernel/src/Kconfig` | `1df7ad3589a282128f797f7a46b28aee315d080550aae26b681a898f11c7abc7` |
  | `kernel/src/Makefile` | `kernel/src/Makefile` | `ce9ca3ca442e6e30e3091168dbc1091a93d65dfbe0e93caf92d3a21be148779f` |
  | `kernel/LICENSE` | `kernel/LICENSE` | `8177f97513213526df2cf6184d8ff986c675afb514d4e68a404010521b880643` |
  | `LICENSE` | `LICENSE` | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |

All imported files are byte-identical to upstream. No copyright notice has been
replaced or added inside them; FolkSU attribution is not applied to upstream
files.

## Deliberately not imported

- `module/*.sh` (`customize.sh`, `metainstall.sh`, `metamount.sh`,
  `post-fs-data.sh`, `rule-paths.sh`, `service.sh`, `boot-completed.sh`):
  FolkSU's built-in backend performs driver loading, rule generation and UID
  filtering itself.
- `module/webroot/**`: the built-in backend ships its own manager UI instead of
  a module WebUI.
- `userspace/src/nm.c`, `userspace/src/nm.h`: the keyring payload protocol is
  reimplemented in Rust. This also avoids shipping a GPL-3 binary and the
  `/data/adb/ksu/bin/nm` symlink the upstream installer relies on.
- `kernel/setup.sh`: it patches a full GKI source tree (`fs/Kconfig`,
  `fs/Makefile`) for in-tree integration. FolkSU builds NoMount as an
  out-of-tree module, so this script is not vendored.

## License mapping

Upstream splits its licenses by directory and carries no SPDX identifiers:

- `kernel/**` (everything vendored here): GNU General Public License v2.0,
  full text in `kernel/LICENSE`. The module itself declares
  `MODULE_LICENSE("GPL")` and `MODULE_AUTHOR("maxsteeel")`.
- Everything outside `kernel/**` upstream: GNU General Public License v3.0.
  That text is retained verbatim as `LICENSE` for provenance even though no
  non-kernel upstream file is vendored.

Upstream source files contain no copyright headers, so provenance rests on the
repository notice and the pinned commit above. Do not advertise the vendored
part as GPL-3-only. FolkSU releases must keep both license texts and provide
matching source for the exact revision they build.

Files authored by FolkSU in this directory (`UPSTREAM.md`, `NOTICE`,
`.gitignore`, `build-lkm.sh`) are not upstream files. `build-lkm.sh` carries
`SPDX-License-Identifier: GPL-2.0-only` because it mirrors upstream's LKM build
steps and builds GPL-2.0-only code.

Release source obligations are already met by the existing per-target source
tarball (`.github/workflows/ksud.yml` runs `git archive HEAD`, attached by
`release.yml`), which contains this directory verbatim.

## FolkSU modifications

None applied to the imported files as of the pinned revision. Any local change
must be recorded here with its rationale and kept minimal, so that re-syncing
from upstream stays a mechanical diff.

## Build contract

`kernel/src/` is built as an out-of-tree kernel module with the same Android
DDK image matrix used for `kernelsu.ko`:

```
CONFIG_NOMOUNT=m make -C "$KDIR" ARCH=arm64 LLVM=1 LLVM_IAS=1 \
    M=<repo>/userspace/nomount/kernel/src modules
```

The KMI name is derived from the DDK image (`ghcr.io/ylarod/ddk-min:<kmi>-<ddk_release>`),
matching `userspace/ksud/bin/aarch64/<kmi>_kernelsu.ko`. Output is staged as
`userspace/ksud/bin/aarch64/<kmi>_nomount.ko` for ksud's embedded assets.

The upstream `kernel/src/Makefile` appends a commit count to
`NOMOUNT_VERSION` only when the source tree's enclosing git repository has an
origin URL containing `nomount`. Inside this repository that condition is
false, so the built module reports the plain base version (`21` for v2.1.1).
FolkSU's driver check therefore compares against the base version, not a commit
count.

## Open items

- `android17-6.18` is in FolkSU's KMI matrix but is not in upstream's test
  matrix; compiling NoMount for it is unverified.
- Upstream's LKM workflow strips page-pinner symbols from the DDK headers before
  building; the same workaround is needed here.
- Stripped module size per KMI is not yet measured, so the APK size delta is
  unknown.
