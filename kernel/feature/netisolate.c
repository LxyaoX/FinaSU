#include <linux/compiler.h>
#include <linux/cred.h>
#include <linux/errno.h>
#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/mutex.h>
#include <linux/printk.h>
#include <linux/ptrace.h>
#include <linux/socket.h>
#include <linux/spinlock.h>
#include <linux/string.h>
#include <linux/types.h>
#include <linux/uaccess.h>

#include <net/compat.h>

#include "feature/netisolate.h"
#include "hook/patch_memory.h"
#include "hook/syscall_hook.h"
#include "infra/symbol_resolver.h"
#include "manager/manager_identity.h"

static_assert(sizeof(struct ksu_netisolate_config) == 1040, "netisolate ABI drift");

#define NI_NATIVE_BASE 0
#define NI_COMPAT_BASE 4
#define NI_HOOK_COUNT 8
#define NI_SYSCALL_LIMIT 1024
#define NI_SENDMMSG_CHECK_MAX 16

enum ni_operation {
    NI_CONNECT,
    NI_SENDTO,
    NI_SENDMSG,
    NI_SENDMMSG,
};

struct ni_hook {
    syscall_fn_t *table;
    int nr;
    syscall_fn_t original;
    syscall_fn_t replacement;
    bool installed;
};

struct ni_state {
    u32 enabled;
    u32 count;
    u32 uids[KSU_NETISOLATE_MAX_UIDS];
};

static DEFINE_MUTEX(ni_lifecycle_lock);
static DEFINE_SPINLOCK(ni_state_lock);
static struct ni_state ni_state;
static struct ni_hook ni_hooks[NI_HOOK_COUNT];
static bool ni_supported;
static bool ni_compat_supported;
static bool ni_stopping;

static const int ni_native_nrs[] = {
    __NR_connect,
    __NR_sendto,
    __NR_sendmsg,
    __NR_sendmmsg,
};

#if defined(__aarch64__)
static const int ni_compat_nrs[] = { 283, 290, 296, 374 };
#elif defined(__x86_64__)
static const int ni_compat_nrs[] = { 362, 369, 370, 345 };
#endif

static syscall_fn_t ni_original(enum ni_operation op, bool compat)
{
    return ni_hooks[(compat ? NI_COMPAT_BASE : NI_NATIVE_BASE) + op].original;
}

static unsigned long ni_arg(const struct pt_regs *regs, unsigned int index, bool compat)
{
#if defined(__x86_64__)
    if (compat) {
        switch (index) {
        case 0:
            return regs->bx;
        case 1:
            return regs->cx;
        case 2:
            return regs->dx;
        case 3:
            return regs->si;
        case 4:
            return regs->di;
        default:
            return regs->bp;
        }
    }

    switch (index) {
    case 0:
        return regs->di;
    case 1:
        return regs->si;
    case 2:
        return regs->dx;
    case 3:
        return regs->r10;
    case 4:
        return regs->r8;
    default:
        return regs->r9;
    }
#else
    (void)compat;
    if (index >= 6)
        return 0;
    return regs->regs[index];
#endif
}

static void __user *ni_user_ptr(const struct pt_regs *regs, unsigned int index, bool compat)
{
    unsigned long value = ni_arg(regs, index, compat);

#if defined(__x86_64__) || defined(__aarch64__)
    if (compat)
        return compat_ptr((compat_ulong_t)value);
#endif
    return (void __user *)value;
}

static bool ni_uid_selected(uid_t uid)
{
    unsigned long flags;
    bool selected = false;
    u32 i;

    if (!READ_ONCE(ni_state.enabled))
        return false;
    if (uid == 0 || is_uid_manager(uid))
        return false;

    spin_lock_irqsave(&ni_state_lock, flags);
    if (ni_state.enabled) {
        for (i = 0; i < ni_state.count; i++) {
            if (ni_state.uids[i] == uid) {
                selected = true;
                break;
            }
        }
    }
    spin_unlock_irqrestore(&ni_state_lock, flags);
    return selected;
}

static int ni_sockaddr_target(const void __user *address, bool *target)
{
    __u16 family;

    if (!address) {
        *target = false;
        return 0;
    }
    if (copy_from_user(&family, address, sizeof(family)))
        return -EFAULT;
    *target = family == AF_INET || family == AF_INET6;
    return 0;
}

static int ni_sendmsg_target(const struct pt_regs *regs, bool compat, bool *target)
{
    void __user *name;
    int ret;

    if (compat) {
        struct compat_msghdr __user *msg =
            (struct compat_msghdr __user *)ni_user_ptr(regs, 1, true);
        compat_uptr_t raw_name;

        if (!msg) {
            *target = false;
            return 0;
        }
        if (get_user(raw_name, &msg->msg_name))
            return -EFAULT;
        name = compat_ptr(raw_name);
    } else {
        struct user_msghdr __user *msg =
            (struct user_msghdr __user *)ni_user_ptr(regs, 1, false);
        void __user *raw_name = NULL;

        if (!msg) {
            *target = false;
            return 0;
        }
        if (get_user(raw_name, &msg->msg_name))
            return -EFAULT;
        name = raw_name;
    }

    ret = ni_sockaddr_target(name, target);
    return ret;
}

static int ni_sendmmsg_target(const struct pt_regs *regs, bool compat, bool *target)
{
    unsigned long base = ni_arg(regs, 1, compat);
    unsigned long vlen = ni_arg(regs, 2, compat);
    unsigned long count = min_t(unsigned long, vlen, NI_SENDMMSG_CHECK_MAX);
    unsigned long i;

    *target = false;
    if (!base || !count)
        return 0;

    for (i = 0; i < count; i++) {
        void __user *name;
        bool item_target;
        int ret;

        if (compat) {
            unsigned long offset = i * sizeof(struct compat_mmsghdr);
            struct compat_mmsghdr __user *entry;
            compat_uptr_t raw_name;

            if (offset > ULONG_MAX - base)
                return -EFAULT;
            entry = (struct compat_mmsghdr __user *)(base + offset);
            if (get_user(raw_name, &entry->msg_hdr.msg_name))
                return -EFAULT;
            name = compat_ptr(raw_name);
        } else {
            unsigned long offset = i * sizeof(struct mmsghdr);
            struct mmsghdr __user *entry;
            void __user *raw_name = NULL;

            if (offset > ULONG_MAX - base)
                return -EFAULT;
            entry = (struct mmsghdr __user *)(base + offset);
            if (get_user(raw_name, &entry->msg_hdr.msg_name))
                return -EFAULT;
            name = raw_name;
        }

        if (!name)
            continue;
        ret = ni_sockaddr_target(name, &item_target);
        if (ret)
            return ret;
        if (item_target) {
            *target = true;
            return 0;
        }
    }
    return 0;
}

static bool __nocfi ni_should_refuse(const struct pt_regs *regs, enum ni_operation op, bool compat)
{
    bool target = false;

    if (!ni_uid_selected(current_uid().val))
        return false;

    switch (op) {
    case NI_CONNECT:
        if (ni_sockaddr_target(ni_user_ptr(regs, 1, compat), &target))
            return false;
        break;
    case NI_SENDTO:
        if (ni_sockaddr_target(ni_user_ptr(regs, 4, compat), &target))
            return false;
        break;
    case NI_SENDMSG:
        if (ni_sendmsg_target(regs, compat, &target))
            return false;
        break;
    case NI_SENDMMSG:
        if (ni_sendmmsg_target(regs, compat, &target))
            return false;
        break;
    }
    return target;
}

#define NI_WRAPPER(name, op, compat)                                                                                   \
    static long __nocfi name(const struct pt_regs *regs)                                                              \
    {                                                                                                                  \
        if (ni_should_refuse(regs, op, compat))                                                                        \
            return -ECONNREFUSED;                                                                                      \
        return ni_original(op, compat)(regs);                                                                          \
    }

NI_WRAPPER(ni_connect, NI_CONNECT, false)
NI_WRAPPER(ni_sendto, NI_SENDTO, false)
NI_WRAPPER(ni_sendmsg, NI_SENDMSG, false)
NI_WRAPPER(ni_sendmmsg, NI_SENDMMSG, false)

#if defined(__aarch64__) || defined(__x86_64__)
NI_WRAPPER(ni_connect_compat, NI_CONNECT, true)
NI_WRAPPER(ni_sendto_compat, NI_SENDTO, true)
NI_WRAPPER(ni_sendmsg_compat, NI_SENDMSG, true)
NI_WRAPPER(ni_sendmmsg_compat, NI_SENDMMSG, true)
#endif

#undef NI_WRAPPER

static const syscall_fn_t ni_native_replacements[] = {
    ni_connect,
    ni_sendto,
    ni_sendmsg,
    ni_sendmmsg,
};

#if defined(__aarch64__) || defined(__x86_64__)
static const syscall_fn_t ni_compat_replacements[] = {
    ni_connect_compat,
    ni_sendto_compat,
    ni_sendmsg_compat,
    ni_sendmmsg_compat,
};
#endif

static void ni_fill_native_hooks(void)
{
    u32 i;

    for (i = 0; i < ARRAY_SIZE(ni_native_nrs); i++) {
        struct ni_hook *hook = &ni_hooks[NI_NATIVE_BASE + i];

        hook->table = ksu_syscall_table;
        hook->nr = ni_native_nrs[i];
        hook->replacement = ni_native_replacements[i];
        if (hook->table && hook->nr >= 0 && hook->nr < NI_SYSCALL_LIMIT)
            hook->original = READ_ONCE(hook->table[hook->nr]);
    }
}

static bool ni_native_hooks_valid(void)
{
    u32 i;

    for (i = 0; i < ARRAY_SIZE(ni_native_replacements); i++) {
        struct ni_hook *hook = &ni_hooks[NI_NATIVE_BASE + i];

        if (!hook->table || hook->nr < 0 || hook->nr >= NI_SYSCALL_LIMIT || !hook->original)
            return false;
    }
    return true;
}

static void ni_fill_compat_hooks(void)
{
#if defined(__aarch64__)
    const char *table_name = "compat_sys_call_table";
    const char *ni_name = "__arm64_sys_ni_syscall";
#elif defined(__x86_64__)
    const char *table_name = "ia32_sys_call_table";
    const char *ni_name = "__ia32_sys_ni_syscall";
#else
    return;
#endif
    syscall_fn_t *table;
    syscall_fn_t ni_syscall;
    u32 i;

    table = (syscall_fn_t *)ksu_resolve_symbol_for_functable_hook(table_name);
    ni_syscall = (syscall_fn_t)ksu_resolve_symbol_for_functable_hook(ni_name);
    if (!table || !ni_syscall)
        return;

    for (i = 0; i < ARRAY_SIZE(ni_compat_nrs); i++) {
        struct ni_hook *hook = &ni_hooks[NI_COMPAT_BASE + i];

        hook->table = table;
        hook->nr = ni_compat_nrs[i];
#if defined(__aarch64__) || defined(__x86_64__)
        hook->replacement = ni_compat_replacements[i];
#endif
        if (hook->nr >= 0 && hook->nr < NI_SYSCALL_LIMIT)
            hook->original = READ_ONCE(hook->table[hook->nr]);
        if (!hook->original || hook->original == ni_syscall) {
            int nr = hook->nr;

            memset(&ni_hooks[NI_COMPAT_BASE], 0,
                   sizeof(ni_hooks[NI_COMPAT_BASE]) * ARRAY_SIZE(ni_compat_nrs));
            pr_info("netisolate: compat table unavailable entry=%d nr=%d\n", i, nr);
            return;
        }
    }
    ni_compat_supported = true;
}

static int ni_patch(const struct ni_hook *hook, syscall_fn_t replacement)
{
    if (!hook->table || hook->nr < 0 || hook->nr >= NI_SYSCALL_LIMIT)
        return -EINVAL;
    return ksu_patch_text(&hook->table[hook->nr], &replacement, sizeof(replacement),
                          KSU_PATCH_TEXT_FLUSH_DCACHE);
}

static int ni_uninstall_hooks_locked(void);

static int ni_install_hooks_locked(void)
{
    u32 i;

    for (i = 0; i < NI_HOOK_COUNT; i++) {
        struct ni_hook *hook = &ni_hooks[i];
        int ret;

        if (!hook->table || hook->installed)
            continue;
        ret = ni_patch(hook, hook->replacement);
        if (ret) {
            pr_err("netisolate: patch failed table=%s nr=%d result=%d\n",
                   hook->table == ksu_syscall_table ? "native" : "compat", hook->nr, ret);
            break;
        }
        hook->installed = true;
    }
    if (i == NI_HOOK_COUNT)
        return 0;

    {
        int rollback = ni_uninstall_hooks_locked();

        if (rollback)
            pr_err("netisolate: install rollback failed result=%d\n", rollback);
    }
    return -EIO;
}

static int ni_uninstall_hooks_locked(void)
{
    int ret = 0;
    int i;

    for (i = NI_HOOK_COUNT - 1; i >= 0; i--) {
        struct ni_hook *hook = &ni_hooks[i];
        int err;

        if (!hook->table || !hook->installed)
            continue;
        err = ni_patch(hook, hook->original);
        if (err) {
            pr_err("netisolate: restore failed table=%s nr=%d result=%d\n",
                   hook->table == ksu_syscall_table ? "native" : "compat", hook->nr, err);
            ret = err;
            continue;
        }
        hook->installed = false;
    }
    return ret;
}

static void ni_publish_locked(const struct ksu_netisolate_config *config)
{
    unsigned long flags;
    u32 i;

    spin_lock_irqsave(&ni_state_lock, flags);
    ni_state.enabled = config->enabled ? 1 : 0;
    ni_state.count = config->count;
    for (i = 0; i < config->count; i++)
        ni_state.uids[i] = config->uids[i];
    spin_unlock_irqrestore(&ni_state_lock, flags);
}

static int ni_validate_config(const struct ksu_netisolate_config *config)
{
    u32 i;
    u32 j;

    if (config->reserved)
        return -EINVAL;
    if (config->count > KSU_NETISOLATE_MAX_UIDS)
        return -EINVAL;
    if (config->enabled && !config->count)
        return -EINVAL;
    for (i = 0; i < config->count; i++) {
        if (!config->uids[i])
            return -EINVAL;
        for (j = 0; j < i; j++) {
            if (config->uids[j] == config->uids[i])
                return -EINVAL;
        }
    }
    return 0;
}

int __init ksu_netisolate_init(void)
{
    mutex_lock(&ni_lifecycle_lock);
    if (ni_supported) {
        mutex_unlock(&ni_lifecycle_lock);
        return -EBUSY;
    }
    memset(ni_hooks, 0, sizeof(ni_hooks));
    memset(&ni_state, 0, sizeof(ni_state));
    ni_compat_supported = false;
    ni_fill_native_hooks();
    if (!ni_native_hooks_valid()) {
        mutex_unlock(&ni_lifecycle_lock);
        return -ENODEV;
    }
    ni_fill_compat_hooks();
    ni_supported = true;
    mutex_unlock(&ni_lifecycle_lock);
    pr_info("netisolate: event=init result=ok compat=%u\n", ni_compat_supported);
    return 0;
}

void ksu_netisolate_exit(void)
{
    mutex_lock(&ni_lifecycle_lock);
    ni_stopping = true;
    ni_publish_locked(&(struct ksu_netisolate_config){});
    {
        int ret = ni_uninstall_hooks_locked();

        if (ret)
            pr_err("netisolate: exit restore incomplete result=%d\n", ret);
    }
    ni_supported = false;
    ni_compat_supported = false;
    mutex_unlock(&ni_lifecycle_lock);
}

int ksu_netisolate_get_config(struct ksu_netisolate_config *config)
{
    unsigned long flags;
    u32 i;

    if (!config)
        return -EINVAL;
    mutex_lock(&ni_lifecycle_lock);
    if (!ni_supported || ni_stopping) {
        mutex_unlock(&ni_lifecycle_lock);
        return -EOPNOTSUPP;
    }
    memset(config, 0, sizeof(*config));
    spin_lock_irqsave(&ni_state_lock, flags);
    config->enabled = ni_state.enabled;
    config->count = ni_state.count;
    for (i = 0; i < ni_state.count; i++)
        config->uids[i] = ni_state.uids[i];
    spin_unlock_irqrestore(&ni_state_lock, flags);
    config->compat_supported = ni_compat_supported;
    mutex_unlock(&ni_lifecycle_lock);
    return 0;
}

int ksu_netisolate_set_config(const struct ksu_netisolate_config *config)
{
    int ret;

    if (!config)
        return -EINVAL;
    ret = ni_validate_config(config);
    if (ret)
        return ret;

    mutex_lock(&ni_lifecycle_lock);
    if (!ni_supported || ni_stopping) {
        ret = -EOPNOTSUPP;
        goto out;
    }
    if (config->enabled) {
        ret = ni_install_hooks_locked();
        if (ret)
            goto out;
        ni_publish_locked(config);
    } else {
        ni_publish_locked(config);
        ret = ni_uninstall_hooks_locked();
    }
out:
    mutex_unlock(&ni_lifecycle_lock);
    return ret;
}
