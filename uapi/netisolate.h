#ifndef __KSU_UAPI_NETISOLATE_H
#define __KSU_UAPI_NETISOLATE_H

#include <linux/types.h>

#define KSU_NETISOLATE_MAX_UIDS 256

struct ksu_netisolate_config {
    __u32 enabled;
    __u32 count;
    __u32 compat_supported;
    __u32 reserved;
    __u32 uids[KSU_NETISOLATE_MAX_UIDS];
};

#endif // __KSU_UAPI_NETISOLATE_H
