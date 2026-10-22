# NoMount upstream provenance

NoMount is vendored into FinaSU so that the kernel module is built by this
repository's own pipeline instead of being downloaded from upstream releases.
This mirrors how FolkZygisk vendors ReZygisk under `userspace/folkzygisk`.

Only the kernel-side sources are imported here. FinaSU reimplements the
userspace control path (the `nm` CLI's keyring payload protocol) in Rust inside
`userspace/ksud`, and generates injection rules from the module directory in
Rust, so no upstream shell scripts or WebUI are imported.

## Pinned sources

- NoMount: https://github.com/maxsteeel/nomount
  commit `87603f4c6ddbc00eec99930a4438296781908453`, the head of the `dev`
  branch (the nightly line upstream ships as "Nightly builds"). The last tagged
  release, `v2.1.1` / `7046c7ad`, is 45 commits behind it and was the previous
  pin here. Imported from `kernel/src/` only:

  | upstream path | vendored path | sha256 |
  | --- | --- | --- |
  | `kernel/src/nomount.c` | `kernel/src/nomount.c` | `e355d181fa432c222b83328092106d45f6d58c4d664b7a9c68698728eeb9712f` |
  | `kernel/src/nomount.h` | `kernel/src/nomount.h` | `96966c1e945dfcb7ef2d86fae0bfca0e2b989fe5017232e9de23b84f17c4d878` |
  | `kernel/src/Kconfig` | `kernel/src/Kconfig` | `1df7ad3589a282128f797f7a46b28aee315d080550aae26b681a898f11c7abc7` |
  | `kernel/src/Makefile` | `kernel/src/Makefile` | `1191fe9ef8e2f3549419fd90a118fbe3d11fc02a04b6353bae0e81be27e03e62` |
  | `kernel/LICENSE` | `kernel/LICENSE` | `8177f97513213526df2cf6184d8ff986c675afb514d4e68a404010521b880643` |
  | `LICENSE` | `LICENSE` | `3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986` |

All imported files are byte-identical to upstream. No copyright notice has been
replaced or added inside them; FinaSU attribution is not applied to upstream
files.

### What the dev sync changed

The kernel side was reworked between `v2.1.1` and this pin; the control
interface FinaSU speaks did not move, which is why the Rust side needed no
change:

- opaque directory support: a new `NM_FLAG_OPAQUE` rule flag and directory-view
  bookkeeping. FinaSU never sets the flag, so injected directories keep the
  merge behaviour they had.
- rules and leaves are retired with `call_rcu` instead of being freed under a
  held lock, and the rule arrays are replaced in one pass.
- readdir merging is bypassed for native replacements, and only injected names
  are invalidated instead of the whole dentry.
- a `rcu_guard()` scope guard, an fsuid cache in the actor proxy, and a Bloom
  filter skip for small child sets.
- the internal `nomount_*` symbols were renamed to `nm_*`; `NM_MAGIC_SIG`
  replaces `NOMOUNT_MAGIC_SIG`. Both are private to the module.
- `kernel/src/Makefile` probes `include/linux/xattr.h` for the `__get` member
  and defines `NM_HAS_XATTR__GET`, so the same source builds against 6.1 and
  6.18 DDK headers.

Unchanged and still relied on by `userspace/ksud/src/nomount.rs`:
`NOMOUNT_BASE_VERSION` is still `21`, the `NM_CMD_*` command set is identical,
and `struct nm_payload` has the same layout.

## Deliberately not imported

- `module/*.sh` (`customize.sh`, `metainstall.sh`, `metamount.sh`,
  `post-fs-data.sh`, `rule-paths.sh`, `service.sh`, `boot-completed.sh`):
  FinaSU's built-in backend performs driver loading, rule generation and UID
  filtering itself.
- `module/webroot/**`: the built-in backend ships its own manager UI instead of
  a module WebUI.
- `userspace/src/nm.c`, `userspace/src/nm.h`: the keyring payload protocol is
  reimplemented in Rust. This also avoids shipping a GPL-3 binary and the
  `/data/adb/ksu/bin/nm` symlink the upstream installer relies on.
- `kernel/setup.sh`: it patches a full GKI source tree (`fs/Kconfig`,
  `fs/Makefile`) for in-tree integration. FinaSU builds NoMount as an
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
part as GPL-3-only. FinaSU releases must keep both license texts and provide
matching source for the exact revision they build.

Files authored by FinaSU in this directory (`UPSTREAM.md`, `NOTICE`,
`.gitignore`, `build-lkm.sh`) are not upstream files. `build-lkm.sh` carries
`SPDX-License-Identifier: GPL-2.0-only` because it mirrors upstream's LKM build
steps and builds GPL-2.0-only code.

Release source obligations are already met by the existing per-target source
tarball (`.github/workflows/ksud.yml` runs `git archive HEAD`, attached by
`release.yml`), which contains this directory verbatim.

## FinaSU modifications

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
false, so the built module reports the plain base version (`21`, unchanged
between v2.1.1 and this pin).
FinaSU's driver check therefore compares against the base version, not a commit
count.

## Open items

- `android17-6.18` is in FinaSU's KMI matrix but is not in upstream's test
  matrix; compiling NoMount for it is unverified.
- This pin tracks upstream `dev`, not a release: re-check it whenever upstream
  tags a new version and re-pin to the tag then.
- Upstream's LKM workflow strips page-pinner symbols from the DDK headers before
  building; the same workaround is needed here.
- Stripped module size per KMI is not yet measured, so the APK size delta is
  unknown.
