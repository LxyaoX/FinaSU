#!/bin/sh
# shellcheck shell=sh disable=SC1091,SC3010,SC3043,SC2155
# Run under busybox ash on the device: `local` and `[[ ]]` are ash builtins there, and the sourced
# helper sits next to this file rather than in shellcheck's search path.
# PlayIntegrityFix's late property work, run by ksud once the boot has completed instead of by a
# module's service.sh. Upstream waits for sys.boot_completed in a loop at the top; ksud calls this
# from the boot-completed stage, which is after that flag is set, so the wait is not repeated.
#
# The chmod on selinuxfs is upstream's and fails harmlessly under a read-only selinuxfs.
MODPATH="${0%/*}"
. "$MODPATH"/common_func.sh

# Conditional sensitive properties

# Magisk Recovery Mode
resetprop_if_match ro.boot.mode recovery unknown
resetprop_if_match ro.bootmode recovery unknown
resetprop_if_match vendor.boot.mode recovery unknown

# SELinux
resetprop_if_diff ro.boot.selinux enforcing
# use toybox to protect stat access time reading
if [ "$(toybox cat /sys/fs/selinux/enforce)" = "0" ]; then
    chmod 640 /sys/fs/selinux/enforce
    chmod 440 /sys/fs/selinux/policy
fi

# Conditional late sensitive properties

# SafetyNet/Play Integrity + OEM
# avoid bootloop on some Xiaomi devices
resetprop_if_diff ro.secureboot.lockstate locked
# avoid breaking Realme fingerprint scanners
resetprop_if_diff ro.boot.flash.locked 1
resetprop_if_diff ro.boot.realme.lockstate 1
# avoid breaking Oppo fingerprint scanners
resetprop_if_diff ro.boot.vbmeta.device_state locked
# avoid breaking OnePlus display modes/fingerprint scanners
resetprop_if_diff vendor.boot.verifiedbootstate green
# avoid breaking OnePlus/Oppo fingerprint scanners on OOS/ColorOS 12+
resetprop_if_diff ro.boot.verifiedbootstate green
resetprop_if_diff ro.boot.veritymode enforcing
resetprop_if_diff vendor.boot.vbmeta.device_state locked

# Other
resetprop_if_diff sys.oem_unlock_allowed 0
