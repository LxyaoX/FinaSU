#include <linux/cred.h>
#include <linux/fdtable.h>
#include <linux/fs.h>
#include <linux/miscdevice.h>
#include <linux/module.h>
#include <linux/syscalls.h>
#include <linux/version.h>

#include "endpoint_impl.c"

void yz_close_current_fd(int fd) {
#if LINUX_VERSION_CODE < KERNEL_VERSION(5, 11, 0)
  ksys_close(fd);
#else
  close_fd(fd);
#endif
}

static long endpoint_ioctl(struct file *file, unsigned int command,
                           unsigned long argument) {
  if (!uid_eq(current_euid(), GLOBAL_ROOT_UID))
    return -EPERM;
  switch (command) {
  case KSU_IOCTL_YZ_REGISTER_ENDPOINT:
    return yz_endpoint_register((void __user *)argument);
  case KSU_IOCTL_YZ_UNREGISTER_ENDPOINT:
    return yz_endpoint_unregister((void __user *)argument);
  case KSU_IOCTL_YZ_GET_ENDPOINT_STATE_FD:
    return yz_endpoint_get_fd((void __user *)argument);
  case _IO('E', 0):
    yz_endpoint_suspend();
    return 0;
  case _IO('E', 1):
    yz_endpoint_enable();
    return 0;
  default:
    return -ENOTTY;
  }
}

static const struct file_operations endpoint_fops = {
    .owner = THIS_MODULE,
    .unlocked_ioctl = endpoint_ioctl,
    .compat_ioctl = endpoint_ioctl,
};

static struct miscdevice endpoint_device = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = "endpoint_probe",
    .fops = &endpoint_fops,
};

static int __init endpoint_probe_init(void) {
  int ret = yz_endpoint_init();

  if (ret)
    return ret;
  ret = misc_register(&endpoint_device);
  if (ret)
    yz_endpoint_exit();
  return ret;
}

static void __exit endpoint_probe_exit(void) {
  misc_deregister(&endpoint_device);
  yz_endpoint_exit();
}

module_init(endpoint_probe_init);
module_exit(endpoint_probe_exit);
MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Endpoint lifecycle test fixture");
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 13, 0)
MODULE_IMPORT_NS("VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver");
#else
MODULE_IMPORT_NS(VFS_internal_I_am_really_a_filesystem_and_am_NOT_a_driver);
#endif
