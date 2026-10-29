#!/bin/sh
# shellcheck shell=sh disable=SC1091,SC3010,SC3043,SC2155
# Run under busybox ash on the device: `local` and `[[ ]]` are ash builtins there, and the sourced
# helper sits next to this file rather than in shellcheck's search path.
# Helpers for the two stage scripts below. Upstream's file also carries the download plumbing its
# own installer uses; nothing here installs anything, so only these two are left.
resetprop_if_diff() {
    local NAME="$1"
    local EXPECTED="$2"
    local CURRENT="$(resetprop "$NAME")"

    [ -z "$CURRENT" ] || [ "$CURRENT" = "$EXPECTED" ] || resetprop -n "$NAME" "$EXPECTED"
}

resetprop_if_match() {
    local NAME="$1"
    local CONTAINS="$2"
    local VALUE="$3"

    [[ "$(resetprop "$NAME")" = *"$CONTAINS"* ]] && resetprop -n "$NAME" "$VALUE"
}
