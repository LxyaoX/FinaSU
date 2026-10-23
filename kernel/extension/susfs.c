#include <linux/cred.h>
#include <linux/fs.h>
#include <linux/jump_label.h>
#include <linux/types.h>
#include <linux/uaccess.h>
#include <linux/susfs.h>
#include <linux/susfs_def.h>

#include "uapi/supercall.h"

struct user_arg_ptr;

DEFINE_STATIC_KEY_FALSE(ksu_is_input_hook_enabled);
DEFINE_STATIC_KEY_FALSE(ksu_is_init_rc_hook_enabled);

int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return -EINVAL;
}

int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags)
{
    return -EINVAL;
}

int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv, void *envp, int *flags,
                                      int *retval)
{
    return 0;
}

int ksu_setup_preinit_file(struct filename **filename_ptr, struct user_arg_ptr *argv_user)
{
    return 0;
}

int ksu_preinit_handle_post_execveat(int *fd, struct filename **filename_ptr, void *argv_user, void *envp_user,
                                     int *flags, int *retval, int *preinit_fd)
{
    return 0;
}

int ksu_handle_faccessat(int *dfd, struct filename **filename, int *mode, int *unused_flags)
{
    return 0;
}

int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{
    return 0;
}

void ksu_handle_sys_read(unsigned int fd)
{
}

void ksu_handle_vfs_fstat(int fd, loff_t *size)
{
}

int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    if (magic1 != KSU_INSTALL_MAGIC1)
        return -EINVAL;

    if (magic2 == KSU_INSTALL_MAGIC2)
        return 0;

    if (magic2 != SUSFS_MAGIC || current_uid().val != 0)
        return -EINVAL;

    switch (cmd) {
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
    case CMD_SUSFS_ADD_SUS_PATH:
        susfs_add_sus_path(arg);
        return 0;
    case CMD_SUSFS_ADD_SUS_PATH_LOOP:
        susfs_add_sus_path_loop(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
    case CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS:
        susfs_set_hide_sus_mnts_for_non_su_procs(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
    case CMD_SUSFS_ADD_SUS_KSTAT:
        susfs_add_sus_kstat(arg);
        return 0;
    case CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY:
        susfs_add_sus_kstat(arg);
        return 0;
    case CMD_SUSFS_UPDATE_SUS_KSTAT:
        susfs_update_sus_kstat(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
    case CMD_SUSFS_SET_UNAME:
        susfs_set_uname(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
    case CMD_SUSFS_ENABLE_LOG:
        susfs_enable_log(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
    case CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG:
        susfs_set_cmdline_or_bootconfig(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
    case CMD_SUSFS_ADD_OPEN_REDIRECT:
        susfs_add_open_redirect(arg);
        return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MAP
    case CMD_SUSFS_ADD_SUS_MAP:
        susfs_add_sus_map(arg);
        return 0;
#endif
    case CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING:
        susfs_set_avc_log_spoofing(arg);
        return 0;
    case CMD_SUSFS_SHOW_ENABLED_FEATURES:
        susfs_get_enabled_features(arg);
        return 0;
    case CMD_SUSFS_SHOW_VARIANT:
        susfs_show_variant(arg);
        return 0;
    case CMD_SUSFS_SHOW_VERSION:
        susfs_show_version(arg);
        return 0;
    default:
        return -EINVAL;
    }
}
