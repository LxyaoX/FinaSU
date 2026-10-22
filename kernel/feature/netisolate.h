#ifndef __KSU_FEATURE_NETISOLATE_H
#define __KSU_FEATURE_NETISOLATE_H

#include "uapi/netisolate.h"

int __init ksu_netisolate_init(void);
void ksu_netisolate_exit(void);

int ksu_netisolate_get_config(struct ksu_netisolate_config *config);
int ksu_netisolate_set_config(const struct ksu_netisolate_config *config);

#endif // __KSU_FEATURE_NETISOLATE_H
