#ifndef __KSU_UAPI_UTS_VIEW_H
#define __KSU_UAPI_UTS_VIEW_H

#include <linux/types.h>

#define KSU_UTS_FIELD_SYSNAME (1u << 0)
#define KSU_UTS_FIELD_NODENAME (1u << 1)
#define KSU_UTS_FIELD_RELEASE (1u << 2)
#define KSU_UTS_FIELD_VERSION (1u << 3)
#define KSU_UTS_FIELD_MACHINE (1u << 4)
#define KSU_UTS_FIELD_DOMAINNAME (1u << 5)
#define KSU_UTS_FIELD_VALID_MASK ((1u << 6) - 1)

#define KSU_UTS_NAME_LEN 65

#define KSU_UTS_STATE_SUPPORTED (1u << 0)
#define KSU_UTS_STATE_ACTIVE (1u << 1)
#define KSU_UTS_STATE_OBSERVED (1u << 2)

#define KSU_UTS_MODE_GLOBAL (1u << 0)
#define KSU_UTS_MODE_SELECTED (1u << 1)
#define KSU_UTS_MODE_VALID_MASK (KSU_UTS_MODE_GLOBAL | KSU_UTS_MODE_SELECTED)
#define KSU_UTS_MAX_TARGETS 128

struct ksu_uts_view_config {
    __u32 field_mask;
    __u32 state;
    __u32 generation;
    __u32 mode;
    __u32 matched;
    __u32 discarded;
    __u32 last_pid;
    __u32 last_uid;
    __u32 skip_not_app;
    __u32 skip_isolated;
    __u32 skip_not_target;
    __u32 skip_not_zygote;
    __u32 skip_private_ns;
    __u32 reserved0;
    __u32 unshare_failed;
    __u32 merge_missing_ns;
    char sysname[KSU_UTS_NAME_LEN];
    char nodename[KSU_UTS_NAME_LEN];
    char release[KSU_UTS_NAME_LEN];
    char version[KSU_UTS_NAME_LEN];
    char machine[KSU_UTS_NAME_LEN];
    char domainname[KSU_UTS_NAME_LEN];
    __u32 target_count;
    __u32 target_uids[KSU_UTS_MAX_TARGETS];
};

#endif // __KSU_UAPI_UTS_VIEW_H
