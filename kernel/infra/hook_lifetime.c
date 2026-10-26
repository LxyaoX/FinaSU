#include <linux/rcupdate.h>
#include <linux/srcu.h>

#include "hook_lifetime.h"

DEFINE_STATIC_SRCU(ksu_lsm_srcu);
DEFINE_STATIC_SRCU(ksu_selinuxfs_srcu);
DEFINE_STATIC_SRCU(ksu_initrc_srcu);

static struct srcu_struct *const hook_domains[] = {
    [KSU_HOOK_LSM] = &ksu_lsm_srcu,
    [KSU_HOOK_SELINUXFS] = &ksu_selinuxfs_srcu,
    [KSU_HOOK_INITRC] = &ksu_initrc_srcu,
};

int ksu_hook_enter(enum ksu_hook_domain domain)
{
	return srcu_read_lock(hook_domains[domain]);
}

void ksu_hook_leave(enum ksu_hook_domain domain, int index)
{
	srcu_read_unlock(hook_domains[domain], index);
}

void ksu_hook_synchronize(enum ksu_hook_domain domain)
{
	/* Let callers holding a retired function pointer enter the SRCU scope.
	 */
	synchronize_rcu_tasks();
	synchronize_srcu(hook_domains[domain]);
}
