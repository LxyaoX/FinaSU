#include <linux/types.h>

#include "supercall/internal.h"
#include "manager/manager_identity.h"
#include "policy/allowlist.h"

bool only_manager(void)
{
    return is_manager();
}

bool only_root(void)
{
    return current_uid().val == 0;
}

bool manager_or_root(void)
{
    return current_uid().val == 0 || is_manager();
}

bool always_allow(void)
{
    return true;
}

bool allowed_for_su(void)
{
    return is_manager() || ksu_is_allow_uid_for_current(current_uid().val);
}

/* Gate for KSU_IOCTL_YZ_UNMAP_SELF: only an app process may ask the kernel to
 * unmap part of its own address space. The handler re-validates the segments,
 * so an app-uid gate is enough; it keeps root/system callers out. */
bool injected_app(void)
{
    return is_appuid(current_uid().val);
}
