---
title: Bootloop Rescue
createTime: 2026/10/24 00:00:00
permalink: /en/guide/rescue-bootloop/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

Flashing can leave a device "bricked". If you only flashed an unsuitable boot partition image or installed a problematic module, recovery is usually still possible with the right steps. This article gives first-aid procedures by source of failure, but do not flash unknown images just to try something.

## Bricked While Flashing a Partition Image

If the device will not boot after flashing a boot partition image patched by FinaSU, common causes include:

- The device is outside the KMI support range of that LKM.
- A wrong or mismatched boot partition image was used.
- The wrong `boot`, `init_boot` or A/B slot was patched.
- Several kernel-level root implementations are in use at the same time.
- An AnyKernel3 package from an unknown source or one that is incompatible was used.

::: warning

This kind of failure happens at the kernel or boot image level, and the volume key safe mode below may never get a chance to run. Restore the matching stock boot partition first instead of rebooting the same image over and over.

:::

## Bricked After Flashing a Module

This is the more common case. The module may not support the current device, or it may have broken the boot environment through `post-fs-data.sh`, `service.sh`, a Metamodule script or a managed kernel module.

::: important

Modules run with high privileges. Modules from unknown sources or redistributed by someone else may contain malicious code and can leave the device unable to boot or even cause irreversible damage. Install only modules with a clear source that support the current system and device.

:::

If the device starts a boot loop after installing or updating a module, try FinaSU safe mode first. If safe mode cannot be reached either, restore the stock boot partition.

## Rescue Mechanisms

FinaSU includes an early-boot safe mode: once the trigger condition is detected, it disables regular modules and managed kernel modules and skips the module scripts, Folk Mount, FolkZygisk and Sui startup flows.

### Volume Key Rescue

When the device starts booting and the screen has just lit up, press and release the volume down key 3 times as quickly as you can. FinaSU counts key presses early in boot and enables safe mode once the count reaches 3.

::: info

Volume key rescue must be done early. FinaSU only collects key presses early in boot and stops listening after it enters `post-fs-data`. A wrong LKM, a KMI mismatch or a damaged boot image may mean the driver never runs at all, in which case the volume key cannot trigger safe mode.

:::

Safe mode writes a disable marker for all regular modules and managed kernel modules. These markers persist across reboots; safe mode itself is valid only for the current boot session. After a normal reboot and a successful boot, do not enable every module at once; enable them one by one with a reboot in between to find the module that actually caused the problem.

::: tip

If late load (jailbreak) mode did not collect the volume keys during boot, you cannot rely on this volume key flow to enter safe mode.

:::

### Booting Safe Mode from Recovery

Some ROMs can enable Android's own safe mode from Recovery. FinaSU recognizes `persist.sys.safemode` or `ro.sys.safemode`, so entering Android safe mode also skips the module startup flow.

::: important

All third-party apps are gone after a successful boot?

This usually means the device entered Android safe mode, not only FinaSU safe mode. The Manager itself is a third-party app, so it may be temporarily invisible. Another normal reboot usually leaves Android safe mode; the module disable markers written by FinaSU remain, so handle the problem modules after the Manager is back.

:::

Safe mode cannot undo abnormal code that has already entered the kernel, and it cannot recover from a kernel panic. If the system still cannot be reached after safe mode, restore the stock boot partition.

## Restoring the Stock Partition Image

When safe mode cannot be reached, flash back the boot partition that was actually modified during installation. The image must match the device model, regional variant, Android build, security patch level and slot.

Prefer the stock image saved before installation. Without a backup, extract it from the complete firmware package provided by the device vendor. Do not use "universal rescue images" from other models, other system versions or repackaged by third-party sites.

### Third-Party Recovery

If the device can enter a third-party Recovery, you can flash the stock `boot` or `init_boot` image to the matching partition there. Before doing so, confirm the partition name and target slot shown by the Recovery, and do not format `/data` or other partitions along the way.

If the device has a vendor Recovery and the vendor officially supports a full OTA package or local upgrade, you can also restore the whole package as the vendor instructs. That flow may overwrite the system or wipe data, so confirm the consequences first.

### Fastboot

If the device can enter Fastboot, connect it to a computer and confirm it is visible:

```sh
fastboot devices
```

If there is no output, do not keep flashing. Check the cable, the USB port, the platform driver and the Fastboot driver first.

On A/B devices, confirm the current slot first:

```sh
fastboot getvar current-slot
```

Flash only the partition FinaSU actually modified:

::: code-tabs

@tab:active boot

```sh
fastboot flash boot boot.img
```

@tab init_boot

```sh
fastboot flash init_boot init_boot.img
```

:::

If Fastboot does not select the current slot automatically, append `_a` or `_b` to the partition name, for example `boot_b` or `init_boot_b`. Do not flash the other slot based on a guess.

Reboot after flashing:

```sh
fastboot reboot
```

::: danger

`boot` and `init_boot` cannot be used interchangeably. You must confirm which partition was actually modified from the Manager's patch result or the installation log at the time. A wrong partition, a wrong system version and a wrong slot can all keep the device from booting.

:::

If the failure happened during "install to inactive slot" after an OTA and the other slot still holds a previously bootable system, you can try switching back to that slot:

```sh
fastboot getvar current-slot
fastboot set_active a
fastboot reboot
```

The `a` above is only an example and must be replaced with the other slot. Do not use this method when the old slot has already been overwritten, the OTA has already been merged, or the system versions of the two slots cannot be confirmed.

::: danger

If the device can reach neither the system, Fastboot nor Recovery, do not keep trying EDL, Firehose or other deep flashing tools from unknown sources. Such tools differ strictly by model and authentication, and a wrong file can cause irreversible damage. First try the forced reboot described in the official manual, then contact vendor support or use the recovery flow officially supported for that model.

:::

Once the device is restored, do not immediately reinstall modules or root. First confirm the device model, Android build, KMI and current slot, then determine whether the LKM, the boot partition, the slot, AnyKernel3, a module script or a root conflict caused the problem. Keep the installation method and the boot logs, and consult [Logs and Feedback](/en/troubleshoot/logs/) for help if needed.
