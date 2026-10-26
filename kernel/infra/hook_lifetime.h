#ifndef __KSU_HOOK_LIFETIME_H
#define __KSU_HOOK_LIFETIME_H

enum ksu_hook_domain {
	KSU_HOOK_LSM,
	KSU_HOOK_SELINUXFS,
	KSU_HOOK_INITRC,
};

int ksu_hook_enter(enum ksu_hook_domain domain);
void ksu_hook_leave(enum ksu_hook_domain domain, int index);
void ksu_hook_synchronize(enum ksu_hook_domain domain);

#endif
