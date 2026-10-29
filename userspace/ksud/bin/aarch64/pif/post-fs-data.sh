# PlayIntegrityFix's early property work, run by ksud at post-fs-data instead of by a module.
#
# Upstream's script opens with two blocks that only make sense for a Magisk module - editing
# Magisk's denylist for Play services, and hiding action.sh from the module page. Neither applies to
# a payload that lives under /data/adb/ksu and is not installed as a module, so they are gone and
# everything from "Conditional early sensitive properties" down is upstream's, in its order.
#
# Upstream also has a block that sets PROPS and then echoes $PROPERTIES, so it has never done
# anything; it is dropped here rather than copied, and should come back if upstream fixes it.
MODPATH="${0%/*}"
. "$MODPATH"/common_func.sh

# Conditional early sensitive properties

# Samsung
resetprop_if_diff ro.boot.warranty_bit 0
resetprop_if_diff ro.vendor.boot.warranty_bit 0
resetprop_if_diff ro.vendor.warranty_bit 0
resetprop_if_diff ro.warranty_bit 0

# Realme
resetprop_if_diff ro.boot.realmebootstate green

# OnePlus
resetprop_if_diff ro.is_ever_orange 0

# Microsoft
for PROP in $(resetprop | grep -oE 'ro.*.build.tags'); do
    resetprop_if_diff "$PROP" release-keys
done

# Other
for PROP in $(resetprop | grep -oE 'ro.*.build.type'); do
    resetprop_if_diff "$PROP" user
done
resetprop_if_diff ro.adb.secure 1
resetprop_if_diff ro.debuggable 0
resetprop_if_diff ro.force.debuggable 0
resetprop_if_diff ro.secure 1

# Work around custom ROM PropImitationHooks conflict when their persist props don't exist
if resetprop | grep -qE "ro.aospa.version|net.pixelos.version|ro.afterlife.version" || [ -f /data/system/gms_certified_props.json ]; then
    resetprop_if_diff persist.sys.pihooks.first_api_level ""
    resetprop_if_diff persist.sys.pihooks.security_patch ""
fi

# LeafOS "gmscompat: Dynamically spoof props for GMS"
# https://review.leafos.org/c/LeafOS-Project/android_frameworks_base/+/4416
# https://review.leafos.org/c/LeafOS-Project/android_frameworks_base/+/4417/5
if [ -f /data/system/gms_certified_props.json ] && [ ! "$(resetprop persist.sys.spoof.gms)" = "false" ]; then
    resetprop persist.sys.spoof.gms false
    resetprop -c $(resetprop -Z "persist.sys.spoof.gms") >/dev/null 2>&1 || true
fi

resetprop -c >/dev/null 2>&1 || true
