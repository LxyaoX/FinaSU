#ifndef __KSU_H_SELINUX
#define __KSU_H_SELINUX

#include <linux/types.h>
#include <linux/version.h>
#include <linux/cred.h>

#define KSU_LEGACY_SU_DOMAIN "su"
#define KSU_LEGACY_SU_CONTEXT "u:r:" KSU_LEGACY_SU_DOMAIN ":s0"
#define KERNEL_SU_DOMAIN "ksu"
#define KERNEL_SU_FILE "ksu_file"

#define KERNEL_SU_CONTEXT "u:r:" KERNEL_SU_DOMAIN ":s0"
#define KSU_FILE_CONTEXT "u:object_r:" KERNEL_SU_FILE ":s0"
#define ZYGOTE_CONTEXT "u:r:zygote:s0"
#define INIT_CONTEXT "u:r:init:s0"
#define ADBD_CONTEXT "u:r:adbd:s0"

void setup_selinux(const char *, struct cred *);

void setenforce(bool);

bool getenforce();

void cache_sid(void);

bool is_task_ksu_domain(const struct cred *cred);

bool is_ksu_domain();

bool is_zygote(const struct cred *cred);

bool is_init(const struct cred *cred);
bool is_adbd(const struct cred *cred);

void apply_kernelsu_rules();

int handle_sepolicy(void __user *user_data, u64 data_len);

void setup_ksu_cred();

void escape_to_root_for_adb_root();

/* File load policy: temporarily widen the AV rules that let a payload be mapped
 * executable, then restore the original state. Used by the YukiZygisk injector. */
struct file;

struct ksu_file_load_policy {
    u32 src_type;
    u32 tgt_type;
    u32 tmpfs_type;
    u32 process_type;
    u16 target_class;
    u16 process_class;
    u16 dir_class;
    u16 reserved;
    u32 added_av;
    u32 tmpfs_added_av;
    u32 process_added_av;
    u32 dir_added_av;
    u32 file_lease_refs;
    u32 tmpfs_lease_refs;
    u32 dir_lease_refs;
    u32 process_lease_refs;
};

u32 ksu_get_ksu_file_sid(void);

int ksu_file_load_policy_allow_current(struct file *file, struct ksu_file_load_policy *state);
int ksu_file_load_policy_allow_cred(struct file *file, const struct cred *cred, struct ksu_file_load_policy *state);
int ksu_file_load_policy_allow_execmem_current(struct ksu_file_load_policy *state);
int ksu_file_load_policy_allow_execmem_cred(const struct cred *cred, struct ksu_file_load_policy *state);
int ksu_file_load_policy_restore(const struct ksu_file_load_policy *state);

struct inode;
struct inode_security_struct;
struct inode_security_struct *ksu_selinux_inode(const struct inode *inode);

#if defined(MODULE) && defined(CONFIG_DEBUG_INFO_BTF)
int __init ksu_selinux_inode_init(void);
#else
static inline int ksu_selinux_inode_init(void)
{
    return 0;
}
#endif

extern u32 ksu_file_sid;

#ifdef CONFIG_KSU_SUSFS
extern u32 susfs_ksu_sid;
extern u32 susfs_init_sid;
extern u32 susfs_zygote_sid;
extern u32 susfs_zygote_next_sid;
extern u32 susfs_priv_app_sid;

bool susfs_is_sid_equal(const struct cred *cred, u32 sid2);
u32 susfs_get_sid_from_name(const char *secctx_name);
u32 susfs_get_current_sid(void);
void susfs_set_batch_sid(void);
bool susfs_is_current_zygote_domain(void);
bool susfs_is_current_zygote_next_domain(void);
bool susfs_is_current_ksu_domain(void);
bool susfs_is_current_init_domain(void);
bool susfs_is_current_priv_app_domain(void);
#endif

#endif
