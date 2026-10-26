---
title: Jailbreak Mode
description: Load the FinaSU driver inside the current system session without modifying the boot partition
createTime: 2026/10/24 00:00:00
permalink: /en/guide/install/jailbreak/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Jailbreak Mode

<Badge text="Session only" type="warning" /> <Badge text="No boot partition changes" type="tip" />

Jailbreak mode (late load) loads the FinaSU driver after the current system has booted, without modifying `boot` or `init_boot`. It suits devices that cannot or do not want to modify the boot partition, but root only lasts for the current boot session and disappears after an ordinary reboot.

::: tip Already Rooted?

If the device is already rooted by Magisk, KernelSU or one of its forks, or by APatch/KernelPatch, read the matching switching guide first and do not stack a fixed jailbreak tool on top:

- [Switching from Magisk](/en/guide/switch/magisk/)
- [Switching from KernelSU](/en/guide/switch/kernelsu/)
- [Switching from APatch](/en/guide/switch/apatch/)

:::

::: info Scope of This Page

This page covers the installation categories, entry points, lifecycle and troubleshooting of Jailbreak mode. A more complete jailbreak guide will be written separately; for detailed adaptation of fixed tools and CVEs you can also refer to route four in [Switching from KernelSU](/en/guide/switch/kernelsu/).

:::

## How Jailbreak Works

```mermaid
flowchart LR
    A["Existing escalation entry"] --> B["Obtain uid 0 / bypass seccomp"]
    B --> C["Run FinaSU ksud late-load"]
    C --> D["Load kernelsu.ko"]
    D --> E["Set up SELinux domain and authorization model"]
    E --> F["Manager detects Jailbreak mode"]
```

Jailbreak is not a different driver. It still loads the standard `<KMI>_kernelsu.ko` and installs the same `ksud`; the difference is that the driver loads after the system boots, so there is no persistent boot image patch.

## Prerequisites

:::: field-group

::: field Escalation entry
@type `Required`
@default `CVE or Magica`
The device needs a working CVE escalation tool, or must meet the Magica / Permissive SELinux conditions for the Jailbreak button on the FinaSU home screen.
:::

::: field FinaSU payload
@type `Required`
@default `ksud + kernelsu.ko`
Confirm the APK contains `ksud` for the matching ABI and `kernelsu.ko` for the matching KMI.
:::

::: field Kernel match
@type `Required`
@default `uname -r`
CVE tools usually match the kernel exactly by `uname -r`; do not keep retrying when it does not match.
:::

::: field Auto-start
@type `Required for automatic jailbreak`
@default `Allowed`
When using automatic jailbreak from the FinaSU home screen, the Manager must be allowed to auto-start.
:::

::: field Bootloop rescue plan
@type `Recommended`
@default `Stock image`
Jailbreak itself does not write the boot partition, but switching to LKM/Built-In or recovering from a failure still needs the stock image.
:::

::::

::: danger Jailbreak Is Not a Universal Privilege Escalation Vulnerability

It depends on a CVE or Magica escalation environment the device already has. Do not keep SELinux protection off for a long time just for jailbreak, and do not keep retrying on a kernel that does not match.

:::

## Entry One: Jailbreak on the FinaSU Home Screen

The FinaSU Manager has a built-in compatible jailbreak path. It is triggered by `AppZygotePreload`, runs the bundled `ksud late-load --magica 5555 --package-name <current Manager package name>`, and after success force-stops and restarts the Manager.

::: steps

1. **Install the official Manager**

   Confirm the APK contains `ksud` for the matching architecture and `kernelsu.ko` for the matching KMI. Back up the old Manager and the module state.

2. **Confirm the conditions**

   The device needs a working Magica escalation environment and the corresponding SELinux state. Automatic jailbreak also requires the Manager to be allowed to auto-start.

3. **Tap Jailbreak on the home screen**

   The flow temporarily enables the local ADB TCP channel, runs late-load through `adb shell`, and restores the ADB properties afterwards.

4. **Wait for the Manager to restart**

   After success the Manager is force-stopped and restarted. If it has not recovered within about 30 seconds, check the log for the failure cause and do not keep tapping.

5. **Verify**

   The home screen should show **Jailbreak mode** and root should be available. If that state is not reached, check the Magica environment, SELinux and the ADB channel.

:::

::: warning The Home Screen Button Is a Compatibility Path

It does not require a matching CVE kernel profile, but it depends on Permissive SELinux or a Magica environment. When the conditions are not met, use a CVE tool instead; do not lower system security for a long time just to make the button work.

:::

## Entry Two: CVE Escalation Tools

A more common approach today is to escalate from an ordinary unauthorized app to uid 0 through a CVE vulnerability, then run FinaSU's `ksud late-load`. For example, GhostLock uses CVE-2026-43499 to obtain uid 0 on a matching kernel and bypass seccomp, then calls:

```sh
ksud late-load --kmi <KMI> --allow-shell
```

The public implementation is at [YuKongA/ghostlock-app](https://github.com/YuKongA/ghostlock-app). It matches the kernel profile exactly by the device's `uname -r`; it cannot be used when the kernel does not match or the vulnerability is already fixed.

::: warning GhostLock Does Not Know FinaSU by Default

The order in which GhostLock looks for `ksud` is:

1. `ksud` in its working directory
2. `libksud.so` inside an installed KernelSU or fork Manager package
3. `/data/local/tmp/ksud`
4. `/data/adb/ksu/bin/ksud`

FinaSU is not in GhostLock's default package name list. If an old manager is still installed on the device, GhostLock may use the old `ksud` first. To make it load FinaSU, uninstall the old manager, replace the `ksud` in GhostLock's working directory, or, after confirming the first two locations do not exist, place FinaSU's `ksud` at `/data/local/tmp/ksud`.

:::

::: steps

1. **Prepare the FinaSU ksud**

   Install the official FinaSU Manager, or take `libksud.so` for the matching ABI out of the APK. It is effectively the executable `ksud`; rename it to `ksud` when needed.

2. **Push it to the device**

   Put `ksud` where GhostLock can find it and make sure it is executable.

3. **Make GhostLock use it**

   Remove the old KernelSU or fork Manager, or replace the `ksud` in GhostLock's working directory. If you only put the file in `/data/local/tmp` while the working directory already has an old `ksud`, GhostLock will still prefer the old one.

4. **Run the CVE flow**

   Run the profile for the matching kernel in GhostLock. The log should show `ksud=...`, `late-load kmi=...` and `late-load exit=0`, and the `kernelsu` module should then be detected.

5. **Verify FinaSU**

   Open FinaSU; the home screen should show **Jailbreak mode** and root should be available. If that state is not reached, check which `ksud` path GhostLock actually used and the KMI first.

:::

::: code-tabs

@tab Extract ksud from the APK

```sh
# replace arm64-v8a with the device's actual ABI
unzip -p FinaSU.apk lib/arm64-v8a/libksud.so > ksud
```

@tab Push to a temporary directory

```sh
adb push ksud /data/local/tmp/ksud
adb shell chmod 755 /data/local/tmp/ksud
```

:::

::: danger CVE Tools Are Not a Universal Solution

GhostLock needs a profile that exactly matches the device kernel; the Android version alone does not determine support. Do not keep retrying when the kernel has already fixed the vulnerability, the profile does not match, or the device is not on the supported list. Wrong kernel parameters can reboot the device or prevent it from booting.

:::

## Entry Three: Dynamic Escalation Tools

If an escalation tool can select an installed Manager, prefer that approach. The tool reads `libksud.so` (effectively `ksud`) from the selected Manager's native library directory instead of recognizing only hardcoded KernelSU or fork package names.

Select the FinaSU Manager when using it. The package name for official builds is usually `me.yuki.fina`, for PR builds `me.yuki.fina.pr`, and custom builds may differ; use the package name actually installed on the device. The tool should read `libksud.so` inside that package, not a copy left by an old manager.

::: tip Why Prefer a Dynamic Tool

This avoids replacing `ksud` by hand and avoids falling back to an old manager because of a fixed package name list. As long as the tool resolves the selected Manager on every run, it will use the new `ksud` after FinaSU updates the APK; if the tool caches the old path, select the Manager again or clear its cache.

:::

::: warning Selecting a Manager Dynamically Is Not Automatic Escalation

The tool still needs the matching CVE or kernel profile to obtain uid 0. Selecting FinaSU only settles which `ksud` is used; it does not bypass the kernel's vulnerability matching requirements.

:::

## Entry Four: Replacing the Payload of a Fixed Tool

If the only way to trigger a jailbreak on the device is a specific fixed tool, you can try to make it run the FinaSU payload. Such tools usually end up running some `ksud` in a data directory, and that `ksud` then loads a `kernelsu.ko` from the same directory or from its embedded resources; so ==replacing the `ksud` the tool actually runs usually makes it load the FinaSU driver==. Some tools also load their own `kernelsu.ko` directly, in which case the matching module and configuration must be replaced as well.

::: steps

1. **Find the file the tool actually runs**

   Do not guess from file names. Inspect the tool's data directory, logs and start scripts to determine whether it runs `/data/adb/ksud`, a `ksud` in its own directory, or a binary extracted from an APK. Back up the original file completely first.

2. **Prepare a matching FinaSU payload**

   Take the matching version from `lib/<ABI>/libksud.so` in the official FinaSU APK (effectively the executable `ksud`) or from the standalone `ksud` in a release package. When the tool only runs `ksud`, it loads the module matching the KMI from embedded resources; when the tool loads the module directly, you also need the `<KMI>_kernelsu.ko` matching that KMI. Do not replace anything when the ABI, KMI or architecture does not match.

3. **Replace at the original path and keep permissions**

   Overwrite the file the tool actually runs with FinaSU's `ksud`, keeping the original file name, executable permission and SELinux context. Replace only the payload; do not let the tool's state files overwrite the authorization or profiles in `/data/adb/ksu`.

4. **Handle hardcoded package names**

   If the tool also hardcodes the old manager's package name or Activity, change the launch target to FinaSU if you can; if you cannot, the FinaSU driver may still be loaded while the tool restarts the old manager. In that case, open FinaSU manually and check whether the home screen enters **Jailbreak mode**.

5. **Run and verify**

   Run the tool's jailbreak flow again and check the module and `ksud` version actually loaded in the log. The switch only succeeded when the FinaSU home screen shows **Jailbreak mode** and root is available.

6. **Keep files for rollback**

   Keep backups of the original `ksud`, module and configuration. A tool update or re-extraction of the payload can overwrite your replacement, so check again after upgrading.

:::

::: danger Replacing the Payload Is Not an Officially Supported Path

This is manual adaptation for a fixed tool; directory layouts, checks and launch parameters differ between tools. If a tool verifies the payload hash or signature, the replacement may fail outright; do not disable other security protections on the device to bypass the checks. A wrong kernel module can prevent the device from booting.

:::

## Lifecycle

::: timeline A Jailbreak Session

- The system boots normally with no FinaSU root
  time=Boot type=tip

- CVE or Magica obtains uid 0
  time=Escalation

- `ksud late-load` loads the FinaSU driver
  time=Load

- The Manager restarts and detects Jailbreak mode
  time=Take effect

- Root disappears after an ordinary reboot and jailbreaking is needed again
  time=End

:::

::: warning The Driver Is Not Hot-swapped in the Same Session

The jailbreak flow first checks whether KernelSU is already loaded in the system. When it is, the flow skips module loading and only reruns the later install and start scripts. Repeating the jailbreak therefore does not replace the old driver in memory; updating the driver requires a full reboot and a new jailbreak.

:::

::: tip What a Soft Reboot Is For

A soft reboot keeps the loaded kernel modules and restarts only the Android userspace, so it suits making module changes take effect but not replacing the driver. To update the Jailbreak mode driver, do a full reboot and jailbreak again.

:::

## Verify After Installation

::: steps

1. **Confirm the run mode**

   The home screen should show **Jailbreak mode**, not LKM or Built-In.

2. **Confirm the kernel build**

   Check the kernel build version and KMI on the home screen, and confirm the loaded driver is the one embedded in the current APK.

3. **Test root**

   Request root from a test app and confirm the authorization dialog and `su` work.

4. **Verify modules**

   Enable one module and reboot, then confirm Folk Mount and the module scripts work. Do not restore all old modules at once.

5. **Verify the built-in engines**

   When using Sui, YukiZygisk or NoMount, check each status; injection-based features usually need one full reboot.

:::

## Moving to Persistent Root

::: danger Do Not Try to Persist with a Locked Bootloader

Some devices use Jailbreak mode precisely because the bootloader cannot be unlocked or the user may not unlock it. LKM and Built-In both modify `boot` or `init_boot`; flashing on a locked bootloader breaks AVB and can leave the device unable to boot, cause data loss, or remove the official recovery options.

Before moving to persistent root, confirm the device has an unlocked bootloader, the boot partition can be restored, and you have the stock image that exactly matches the current system. If any of these cannot be confirmed, keep using Jailbreak mode and do not try to flash the boot partition. If the device misbehaves, follow [Bootloop Rescue](/en/guide/rescue-bootloop/).

:::

::: table Jailbreak vs LKM and Built-In

| Item | Jailbreak mode | LKM | Built-In |
| --- | --- | --- | --- |
| Modifies the boot partition | No | Yes | Yes |
| Keeps root after a reboot | No | Yes | Yes |
| Depends on an escalation vulnerability or Magica | Yes | No | No |
| Main use | Temporary testing, devices that cannot be flashed | Persistent root for most users | Maintaining your own kernel |

:::

Only after confirming the device can modify the boot partition should you move to persistent root with [LKM installation](/en/guide/install/lkm/) or [Built-In installation](/en/guide/install/built-in/). Before switching, confirm the bootloader state, the partition and the stock image recovery plan.

## Data and Files

```file-tree title="Jailbreak related locations"
/data/adb
├── ksud                      # FinaSU userspace command-line tool
├── ksu/
│   ├── .allowlist            # authorization state
│   └── profile/              # App Profile
└── modules/                  # installed modules

/data/local/tmp
└── ksud                      # some escalation tools read the payload from here
```

Do not let the old manager's state files overwrite the authorization or profiles in `/data/adb/ksu`. Replace only the payload the tool actually runs, and back up the original files before you start.

## Troubleshooting

::: collapse accordion

- **It has not recovered about 30 seconds after tapping Jailbreak on the home screen**

  Check the Magica environment, SELinux state, ADB channel and the Manager's auto-start permission. Read the log first and do not trigger it repeatedly.

- **The CVE tool reports that ksud is missing**

  The tool did not find FinaSU's `ksud`. Put the `libksud.so` from the APK where it can read it, or switch to a dynamic escalation tool that can select FinaSU.

- **The tool used the old manager's ksud**

  Check the tool's working directory and hardcoded package name list. Remove the old manager, or replace the `ksud` in the tool's working directory.

- **The CVE flow cannot obtain uid 0**

  The kernel profile does not match or the vulnerability is already fixed. Do not keep retrying on an unsupported kernel.

- **It reports jailbreak success but FinaSU has no root**

  It may still be operating on the old manager or an old payload. Check the `ksud` path, the `late-load` exit status and the KMI in the log.

- **The driver is still the old version after repeating the jailbreak**

  In the same boot session, the jailbreak flow skips module loading when the driver is already loaded. Do a full reboot and jailbreak again.

- **Modules do not mount**

  Confirm the module supports FinaSU's mounting method and check the Folk Mount provider. Jailbreak mode does not change module compatibility.

- **The device will not boot**

  Jailbreak itself does not write the boot partition; if an image was also flashed earlier, restore it following [Bootloop Rescue](/en/guide/rescue-bootloop/). Do not keep trying unknown payloads.

:::

## FAQ

::: chat title="Jailbreak Mode Installation Questions"
{User}
Does jailbreak modify boot or init_boot?

{.}
No. It loads the driver after the system boots and does not write the boot partition, which is why root disappears after a reboot.

{User}
How does the home screen Jailbreak relate to CVE tools?

{.}
The home screen button is a compatibility path built into FinaSU that depends on Permissive SELinux or Magica. A CVE tool first exploits a vulnerability to obtain uid 0 and then runs FinaSU's `ksud late-load`.

{User}
Why does a fixed tool pick the wrong manager?

{.}
Some tools hardcode the old manager's package name, payload path or signature. You can replace the `ksud` it actually runs, or switch to a dynamic escalation tool that can select the manager.

{User}
After replacing ksud, do I still need to replace kernelsu.ko?

{.}
If the tool only runs `ksud`, FinaSU's `ksud` loads the module from embedded resources; if the tool loads a fixed `kernelsu.ko` directly, the module must be replaced as well.

{User}
Can I use jailbreak long term?

{.}
It is valid only for the current session. For persistent root, install again via [LKM installation](/en/guide/install/lkm/) or [Built-In installation](/en/guide/install/built-in/).

{User}
How do I get root back after a reboot?

{.}
Run the jailbreak flow again. If root has to come back automatically on every boot, use LKM or Built-In instead of relying on jailbreak.
:::

## Related Pages

- [Installation Options](/en/guide/install/)
- [LKM installation](/en/guide/install/lkm/)
- [Built-In installation](/en/guide/install/built-in/)
- [Switching from Magisk](/en/guide/switch/magisk/)
- [Switching from KernelSU](/en/guide/switch/kernelsu/)
- [Switching from APatch](/en/guide/switch/apatch/)
- [Bootloop Rescue](/en/guide/rescue-bootloop/)

*[LKM]: Loadable Kernel Module
*[KMI]: Kernel Module Interface, the Android kernel module interface version
*[GKI]: Generic Kernel Image
*[CVE]: Common Vulnerabilities and Exposures identifier
*[AVB]: Android Verified Boot
