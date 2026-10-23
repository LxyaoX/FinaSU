#ifndef __KSU_UAPI_NETISOLATE_H
#define __KSU_UAPI_NETISOLATE_H

#include <linux/types.h>

#define KSU_NETISOLATE_MAX_UIDS 256
#define KSU_NETISOLATE_MAX_POLICIES 256
#define KSU_NETISOLATE_EVENT_MAX 64
#define KSU_NETISOLATE_FLAG_MASK 0xffffu
#define KSU_NETISOLATE_SUPPORTED_SHIFT 16u

#define KSU_NETISOLATE_FLAG_STRICT_SOCKET (1u << 0)  /* Mutually exclusive with loopback */
#define KSU_NETISOLATE_FLAG_ALLOW_LOOPBACK (1u << 1) /* Allow IPv4/IPv6 loopback targets */

struct ksu_netisolate_config {
    __u32 enabled;          /* Input/Output */
    __u32 count;            /* Input/Output */
    __u32 compat_supported; /* Output; ignored on SET */
    __u32 reserved;         /* SET: active flags. GET: active | supported << 16 */
    __u32 uids[KSU_NETISOLATE_MAX_UIDS];
};

struct ksu_netisolate_policy {
    __u32 uid;
    __u32 flags;
};

struct ksu_netisolate_policies {
    __u32 enabled;         /* Input/Output */
    __u32 count;           /* Input/Output */
    __u32 supported_flags; /* Output; ignored but must be 0 on SET */
    __u32 reserved;        /* Input/Output; must be 0 */
    struct ksu_netisolate_policy policies[KSU_NETISOLATE_MAX_POLICIES];
};

struct ksu_netisolate_event {
    __u64 timestamp_ns;
    __u32 uid;
    __u16 port; /* Host byte order; 0 when no transport port was parsed */
    __u8 family;
    __u8 operation;
    __u8 address[16];
    __u32 reserved;
};

struct ksu_netisolate_events {
    __u32 count;
    __u32 capacity;
    __u64 dropped;
    struct ksu_netisolate_event events[KSU_NETISOLATE_EVENT_MAX];
};

#endif // __KSU_UAPI_NETISOLATE_H
