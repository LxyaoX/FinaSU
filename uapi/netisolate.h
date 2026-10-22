#ifndef __KSU_UAPI_NETISOLATE_H
#define __KSU_UAPI_NETISOLATE_H

#include <linux/types.h>

#define KSU_NETISOLATE_MAX_UIDS 256
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

#endif // __KSU_UAPI_NETISOLATE_H
