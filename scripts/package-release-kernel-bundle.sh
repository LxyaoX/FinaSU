#!/bin/sh
set -eu

source_dir=${1:-.}
output=${2:-}
if [ -z "$output" ]; then
    echo "usage: $0 <artifact-dir> <output.zip>" >&2
    exit 2
fi

case "$output" in
    /*) ;;
    *) output="$PWD/$output" ;;
esac

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT

package_arch() {
    artifact_prefix=$1
    output_arch=$2
    mkdir -p "$stage/$output_arch"
    set -- "$source_dir/$artifact_prefix"-android*-lkm/*_kernelsu.ko

    for file do
        if [ ! -f "$file" ]; then
            echo "missing $artifact_prefix kernel module artifacts" >&2
            exit 1
        fi
    done
    cp "$@" "$stage/$output_arch/"
}

package_arch aarch64 arm64
package_arch x86_64 x86_64

package_ksud() {
    artifact=$1
    output_name=$2
    if [ -f "$source_dir/$artifact/ksud" ]; then
        cp "$source_dir/$artifact/ksud" "$stage/ksud/$output_name"
    elif [ -f "$source_dir/$artifact/ksud.exe" ]; then
        cp "$source_dir/$artifact/ksud.exe" "$stage/ksud/$output_name"
    else
        echo "missing $artifact/ksud" >&2
        exit 1
    fi
}

mkdir -p "$stage/ksud"
package_ksud ksud-aarch64-linux-android android-arm64
package_ksud ksud-x86_64-linux-android android-x86_64
package_ksud ksud-riscv64-linux-android android-riscv64
package_ksud ksud-aarch64-unknown-linux-musl linux-arm64
package_ksud ksud-x86_64-unknown-linux-musl linux-x86_64
package_ksud ksud-aarch64-apple-darwin macos-arm64
package_ksud ksud-x86_64-apple-darwin macos-x86_64
package_ksud ksud-x86_64-pc-windows-gnu windows-x86_64.exe

for arch in arm64 x86_64; do
    kernelsu=$(find "$stage/$arch" -maxdepth 1 -name '*_kernelsu.ko' | wc -l)
    if [ "$kernelsu" -ne 8 ]; then
        echo "$arch has $kernelsu kernelsu.ko; expected 8" >&2
        exit 1
    fi
done

ksud_count=$(find "$stage/ksud" -maxdepth 1 -type f | wc -l)
if [ "$ksud_count" -ne 8 ]; then
    echo "ksud has $ksud_count binaries; expected 8" >&2
    exit 1
fi

rm -f "$output"
(cd "$stage" && python3 -m zipfile -c "$output" arm64 x86_64 ksud)
echo "created $output"
