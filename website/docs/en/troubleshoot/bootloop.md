---
title: Device Won't Boot
description: Separate image, kernel, driver and module faults by boot stage, and recover with safe mode or the stock image
createTime: 2026/10/24 00:00:00
permalink: /en/troubleshoot/bootloop/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Device Won't Boot

<Badge text="Stop first" type="danger" /> <Badge text="Judge by boot stage" type="warning" /> <Badge text="Keep the stock image" type="tip" />

"No boot" can mean a wrong boot image, an incompatible kernel, a driver panic, a stuck boot script, or an app that keeps the system UI from finishing startup. The order of handling depends on where the device stops; flashing a second and then a third package while the fault layer is unknown only widens the damage.

::: danger Do not stack more unknown changes

When the device can no longer reach the system, do not keep installing modules, switching Root solutions, trying different `.ko` files, or flashing AnyKernel3 packages of unknown origin. Confirm whether you can reach Fastboot, Recovery or Android first, then handle the matching stage.

:::

## Where the Device Stops

:::: table Boot stages and common causes

| Symptom | What already completed | More likely fault layer |
| --- | --- | --- |
| No reaction to the power key at all, or not even the Bootloader logo | May not have entered the normal boot chain | Battery, hardware, Bootloader, keys, or a completely corrupt boot partition |
| The vendor logo or Bootloader appears, but the kernel boot screen does not | Bootloader loaded the boot image | Wrong `boot`, `init_boot`, `vendor_boot`, slot, or kernel image |
| The system boot animation appears, then loops | Kernel and Android `init` already started | Boot scripts, module mounting, SELinux, kernel modules, or system services |
| The lock screen appears, but the launcher, system UI or target app keeps crashing | Android is mostly booted | Module file injection, app Profile, built-in capabilities, or the app's own data |
| The system boots, but Root, modules or a capability is unavailable | System and driver may already be running | Manager identity, authorization, user-space service, or capability configuration |

::::

==The boot animation appearing usually means the Linux kernel has started==, but that does not mean the FinaSU driver, `ksud` and all modules are fine. Conversely, when the device hangs before the boot animation, the volume-key safe mode usually has no chance to run.

:::: timeline From fault to safe recovery

- Confirm whether the device stops at the Bootloader, the boot animation or the system UI
  time=Observe type=warning

- Stop flashing unknown modules or images and record the last change
  time=Contain type=danger

- At the boot animation stage, try FinaSU safe mode first
  time=Recover type=tip

- At the boot image stage, flash the stock image for the partition you actually modified
  time=Rescue type=warning

- After the system is back, verify Root, modules, Zygisk and Sui one by one
  time=Verify type=success

::::

## Risk Points of the Three Installation Methods

::::: field-group

:::: field LKM
@type `Persistent install`
Patches the ramdisk of the boot image, usually in `init_boot`, or in `boot` when there is no `init_boot`. A KMI mismatch, a wrong partition or a wrong slot makes the kernel fail early in boot.
::::

:::: field Built-In
@type `Persistent install`
Compiles the driver into the Linux kernel, usually flashed as a full `boot`. When the kernel source, configuration, version, AnyKernel3 package or partition do not match, the device may fail before the boot animation.
::::

:::: field Jailbreak mode
@type `Current session`
Does not modify the boot partition and loads the module after Android boots. A normal reboot clears this load; if the module load causes a crash, it usually will not load automatically after the reboot, but it can still make the current session restart or go black suddenly.
::::

:::::

::: warning "Did not touch boot" is not absolute safety

Jailbreak mode does not write to the persistent boot chain, but it still executes a high-privilege module in the running kernel. A wrong KMI, wrong architecture or incompatible kernel interface can still crash the current system. When recovering, do not flash an image that does not match the current system.

:::

## Try FinaSU Safe Mode

With a persistent installation, FinaSU counts volume-down key presses early in boot. After **3** presses, this boot is treated as FinaSU safe mode.

:::: steps

1. **Get ready when the device starts booting**

   After the vendor logo appears and before the system reaches a later boot stage, press and release the volume-down key three times in a row. Every press must be a clear press action.

2. **Wait for the system to boot**

   Do not hold the key dozens of times because one attempt did not trigger it. FinaSU stops listening for early input once the condition is met, and further presses do not change the decision already made.

3. **Confirm safe mode**

   The Manager home screen should show the safe mode state. Android itself may also enter safe mode, which shows as third-party apps being temporarily unavailable; do not confuse the two.

4. **Handle suspicious modules**

   FinaSU safe mode skips the module boot scripts, Folk Mount, FolkZygisk, Sui and other boot flows, and disables regular modules and managed kernel modules. After booting, re-enable one module at a time and verify with a reboot each time.

::::

Safe mode is not "undo the bad code already written into the kernel". If the driver never loaded, the kernel crashes before the volume-key listener runs, or the boot image itself is wrong, the volume-key flow will not work.

::: important Delayed loading does not use volume-key safe mode

Jailbreak mode has no persistent boot patch and no boot-stage input listener. Do not keep pressing volume keys expecting FinaSU safe mode; perform a normal reboot to clear this delayed load, then inspect the payload that caused the crash and the kernel match.

:::

## Android Safe Mode and Pre-initialization Protection

`ksud` also recognizes the system properties `persist.sys.safemode=1` or `ro.sys.safemode=1`. Therefore, when some ROMs enter Android safe mode through Recovery or key presses, the FinaSU module boot flow is skipped as well.

Module pre-initialization also has its own consecutive-failure protection:

- After several consecutive boots that do not complete pre-initialization, pre-initialization is paused and the Manager may show "pre-initialization paused after several incomplete boots".
- The purpose of pausing is to avoid repeating the same potentially hanging path on every boot.
- To resolve it, disable the most recently changed or most suspicious module instead of treating the pause hint as a normal cache error and rebooting repeatedly.

## Recover by Stage

### Fails Before the Boot Animation

This kind of fault should be handled as a boot image problem first. Confirm which partition the installation actually modified, then restore with a stock image that matches the device model, Android build, kernel version and slot.

```file-tree title="Installation methods and common recovery targets"
Boot chain
├── LKM
│   ├── has init_boot and KMI is not android12-* → stock init_boot
│   ├── no init_boot, or KMI is android12-* → stock boot
│   └── vendor_boot was selected manually during install → stock vendor_boot
└── Built-In
    └── usually → stock boot
```

The Manager installation page, the installation log or the actual flash command is authoritative. `boot`, `init_boot` and `vendor_boot` are not interchangeable generic partitions.

::: danger A/B devices must confirm the current slot

Handle only the slot that was actually modified. Do not flash both slots "just to be safe", and do not blindly switch the active slot when an OTA has already been merged or the two slots run different versions. See [Bootloop Rescue](/en/guide/rescue-bootloop/) for the full procedure.

:::

### Boot Animation Loop

If the boot animation appears, try FinaSU safe mode first. After the system boots:

:::: steps

1. **Preserve the current state**

   Record safe mode, recently installed modules, the mount provider, the FolkZygisk/Sui state and the time point. Do not clear `/data/adb` yet.

2. **Restore modules one by one**

   Enable only one module and reboot. Module files, `post-fs-data.sh`, `service.sh`, Metamodules and managed `.ko` files can all block or break boot.

3. **Check the pre-initialization warning**

   If pre-initialization is paused, handle the corresponding module first. Do not force it to retry the same broken configuration by deleting several state files.

4. **Restore a minimal capability set**

   Temporarily disable non-essential capabilities such as Folk Mount, NoMount, FolkZygisk, Sui and UTS View. Once basic Root and system boot are stable, enable them one by one following their dependencies.

::::

If safe mode still cannot be reached, recover through the boot image path. Do not conclude that the problem is solved just because you deleted a module directory; the module may already have changed early boot configuration or taken part in kernel module loading.

### Boots but the UI Keeps Crashing

This problem is usually not a corrupt boot image. Check first:

- whether a recently installed or updated module changed a dependency of the system UI;
- whether the target app's App Profile restricts UID, GID, SELinux or namespaces;
- whether NoMount exceptions and "unmount modules" make the app see the wrong file view;
- whether a Zygisk module supports only a single ABI;
- whether Sui app permissions, background notifications or the Manager identity are abnormal.

For the matching entry points, see [Driver Runtime Errors](/en/troubleshoot/runtime/) and [Detection & Hiding](/en/troubleshoot/detection/).

## When Brick Rescue Is Required

When any of the following occurs, stop relying on in-Manager operations:

- the system cannot boot and safe mode cannot recover it;
- after flashing, not even Recovery or Fastboot can be reached;
- the device keeps restarting and there is not enough time to complete the key sequence;
- you have confirmed that a wrong partition, wrong slot or clearly mismatched kernel image was written;
- the boot log shows a kernel panic, a module vermagic error, or that the root filesystem cannot be mounted.

In these cases, follow [Bootloop Rescue](/en/guide/rescue-bootloop/) to confirm the Fastboot, Recovery and vendor official recovery paths. Do not use EDL, Firehose or "one-click rescue" tools of unknown origin.

## Verify After Recovery

:::: steps

1. **Confirm the system itself can boot**

   After restoring the stock image, complete at least one normal system boot before reinstalling FinaSU.

2. **Check the device facts**

   Record the model, Android build, full kernel version, KMI, ABI, current slot and boot partition again. See [Device Compatibility](/en/troubleshoot/compatibility/).

3. **Use a minimal install**

   After the first recovery, install only FinaSU and the matching Manager, and do not immediately restore all modules and hiding settings.

4. **Restore item by item**

   Verify Root first, then one regular module, and finally dependent capabilities such as Zygisk, Sui and NoMount.

5. **Save the logs**

   If the problem reappears, keep the archive exported by "Send logs" and the relevant boot logs, then provide the timeline as described in [Reporting Issues](/en/troubleshoot/report/).

::::

## Related Pages

- [Bootloop Rescue](/en/guide/rescue-bootloop/): the main text on Fastboot, Recovery, partitions and slot recovery.
- [Device Compatibility](/en/troubleshoot/compatibility/): KMI, ABI and the build matrix.
- [Manager Authentication Failure](/en/troubleshoot/manager-auth/): the system boots but the Manager has no driver identity.
- [Driver Runtime Errors](/en/troubleshoot/runtime/): user-space and module problems after the driver connects.
- [Logs & Feedback](/en/troubleshoot/logs/): export boot logs and the full diagnostic bundle.
