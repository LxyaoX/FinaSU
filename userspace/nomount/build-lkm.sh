#!/bin/sh
# SPDX-License-Identifier: GPL-2.0-only
#
# Builds the vendored NoMount module inside an Android DDK container and drops
# the stripped module at $NOMOUNT_OUT. Invoked by build.sh and by
# .github/workflows/ddk-lkm.yml; it is a FolkSU-authored file, not upstream
# (see UPSTREAM.md).
#
# The vendored sources are never written to: the module is compiled from a copy
# in a temporary directory, so `userspace/nomount/kernel/src/` stays
# byte-identical to upstream and re-syncing remains a mechanical diff.
#
# Required environment (provided by the DDK image unless noted):
#   KDIR          kernel build directory inside the container
#   NOMOUNT_OUT   absolute path for the produced .ko (host-visible)
#
set -eu

# Resolve the vendored sources relative to this script so the same entry point
# works from build.sh (repository at /build) and from CI (repository at the
# runner workspace).
SELF_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC="$SELF_DIR/kernel/src"

[ -d "$SRC" ] || { echo "NoMount source not found at $SRC" >&2; exit 1; }
[ -n "${KDIR:-}" ] || { echo "KDIR is not set; run inside the Android DDK image" >&2; exit 1; }
[ -n "${NOMOUNT_OUT:-}" ] || { echo "NOMOUNT_OUT is not set" >&2; exit 1; }

# Some KMIs ship DDK headers that reference page-pinner helpers the kernel does
# not export, which fails the build for a module that never uses them. Disable
# the include and its single call site in the header copy, as upstream does.
#
# This writes outside the workspace (/opt/ddk), which only works when the
# container user owns those headers. Patch best-effort: if it is not permitted,
# continue, so a build that actually needed the patch fails on its own with a
# readable compiler error instead of an obscure permission failure here.
patched=0
for mm in /opt/ddk/src/*/include/linux/mm.h /opt/ddk/kdir/*/include/linux/mm.h; do
    [ -f "$mm" ] || continue
    grep -q '^#include <linux/page_pinner.h>' "$mm" || continue
    if cp "$mm" "$mm.orig" 2>/dev/null &&
        sed -i 's|^#include <linux/page_pinner.h>|/* NoMount: page_pinner disabled */|' "$mm" 2>/dev/null &&
        sed -i 's|^\tpage_pinner_put_page(page);|\t/* NoMount: page_pinner disabled */|' "$mm" 2>/dev/null; then
        echo "-- patched $mm"
        patched=$((patched + 1))
    else
        echo "!! cannot patch $mm (headers not writable); continuing" >&2
    fi
done
echo "-- page_pinner headers patched: $patched"

build_dir="$(mktemp -d)"
trap 'rm -rf "$build_dir"' EXIT
cp -a "$SRC/." "$build_dir/"

echo "-- building NoMount from $build_dir against $KDIR"
CONFIG_NOMOUNT=m make -C "$KDIR" ARCH=arm64 LLVM=1 LLVM_IAS=1 M="$build_dir" modules

ko="$build_dir/nomount.ko"
[ -f "$ko" ] || { echo "nomount.ko was not produced in $build_dir" >&2; exit 1; }

# Match the kernelsu.ko assets: strip debug/unneeded and drop BTF.
llvm-strip --strip-unneeded -R .BTF "$ko"

mkdir -p "$(dirname "$NOMOUNT_OUT")"
cp "$ko" "$NOMOUNT_OUT"
ls -la "$NOMOUNT_OUT"
