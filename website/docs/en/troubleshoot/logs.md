---
title: Logs & Feedback
description: Export a FinaSU diagnostic bundle, capture minimal system logs and identify sensitive data
createTime: 2026/10/24 00:00:00
permalink: /en/troubleshoot/logs/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Logs & Feedback

<Badge text="Reproduce before exporting" type="tip" /> <Badge text="The archive contains sensitive data" type="danger" /> <Badge text="Keep the timeline" type="warning" />

Useful feedback is not "all the logs are here"; it lets someone read the timeline and see what was done, what was expected, what actually happened, and which layer was running at the time. FinaSU's "Send logs" exports a full environment snapshot, not just a few lines of the Manager's own records.

:::: steps

1. **Record the problem time**

   Write down the device local time, the time zone and the last reboot time. Do not just write "just now".

2. **Follow fixed reproduction steps**

   For example "open app X → request Root → run a command → error appears". Record the expected and the actual result.

3. **Export right after reproducing**

   Export the log before trying a reboot, clearing caches or changing modules. Later actions change the scene.

4. **Check for sensitive data**

   The log archive contains package names, UIDs, paths, device information and authorization state. Review each item before making it public.

::::

## What Send Logs Actually Contains

The entry is **Settings → Diagnostics & Feedback → Send logs**. The Manager generates:

```text
/cache/KernelSU_bugreport_YYYY-MM-DD_HH_mm.tar.gz
```

The archive contains the following core items:

```file-tree title="Diagnostic bundle contents"
KernelSU_bugreport_YYYY-MM-DD_HH_mm.tar.gz
├── basic.txt                 # device, build, kernel, SELinux, run mode
├── process.txt               # processes, threads, UID, command line and SELinux label
├── dmesg.txt                 # kernel log snapshot of this boot
├── logcat.txt                # all buffer, with UIDs, one-shot dump
├── bootlog.tar.gz            # /data/adb/ksu/log
├── mounts.txt                # mountinfo of PID 1
├── filesystems.txt           # filesystems supported by the kernel
├── adb_tree.txt              # /data/adb directory tree
├── adb_details.txt           # recursive /data/adb permissions and SELinux contexts
├── ksu_size.txt              # space used by each item in /data/adb/ksu
├── packages.txt              # /data/system/packages.list
├── props.txt                 # full getprop properties
├── allowlist.bin             # Root authorization and App Profile data
├── modules.json              # current module state
├── proc_modules.txt          # loaded kernel modules
├── boot_config.txt           # /proc/bootconfig
├── defconfig.gz              # /proc/config.gz (when the device provides it)
├── kallsyms.txt              # kernel symbol snapshot
├── tombstones.tar.gz         # /data/tombstones
├── dropbox.tar.gz            # /data/system/dropbox
├── pstore.tar.gz             # /sys/fs/pstore
├── diag.tar.gz               # diag on some Xiaomi/Redmi devices
└── oplus.tar.gz              # boot logs on some OPPO/OnePlus devices
```

When a directory does not exist on the device, or the current permissions cannot read it, some items may be missing or empty. A single missing optional item does not mean the archive is corrupt.

::: important A full export depends on a working Root environment

"Send logs" collects system information through the Root Shell. When the Manager identity, `ksud` or the Root Shell is unavailable, the export may fail or be incomplete. In that case, collect as much as you can with ADB and clearly mark what is missing.

:::

## What Each Log Can Answer

:::: table Log purposes

| Data | Good for answering | Not enough on its own for |
| --- | --- | --- |
| `basic.txt` | Device, Android, kernel, SELinux, LKM state, safe mode | Why a Root request was rejected |
| `dmesg.txt` | Kernel panic, module loading, SELinux/AVC, driver initialization | Full app-side stack traces |
| `logcat.txt` | Android services, app crashes, Manager and Sui logs | Kernel errors from before boot |
| `bootlog.tar.gz` | `ksud` boot scripts, mounting, SU logs and fallback records | Early panics of the kernel itself |
| `tombstones` / `dropbox` | Java/native crashes and system anomaly records | Whether module files are visible |
| `pstore` | Persistent record of the last crash on some devices | Being present on every model |
| `mounts.txt` / `proc_modules.txt` | Current mounts and loaded modules | Why the kernel does not provide a capability |
| `allowlist.bin` / `modules.json` | Structured snapshot of authorization, Profiles and module state | Fully explaining app behavior |
| `packages.txt` | Package name to UID mapping | Whether the app really requested Root |

::::

## SU Logs and Mount Logs

SU logs live in `/data/adb/ksu/log/` and record Root execve, traditional SU, authorization and daemon events. The mount logs are mainly:

```file-tree title="Common event logs"
/data/adb/ksu/log
├── sulog-YYYY-MM-DD.log
├── sulog-YYYY-MM-DD-1.log
├── mount.log
└── mount_capture.log
```

`mount.log` records this boot's Folk Mount or NoMount provider, injection results and fallback reasons. `mount_capture.log` only appears after anomaly boot capture is explicitly enabled and is not present on every boot.

Locate the time in the Manager first, then export the full bundle. Do not clear the SU logs or `dmesg` first "to make the logs clean".

## Minimal Collection with ADB

Run these commands on a computer. When the device lacks the corresponding permission, the output may be empty or show `Permission denied`; do not fake it as success.

:::: code-tabs

@tab:active Basic information

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.fingerprint
adb shell uname -a
adb shell getenforce
```

@tab Kernel and Android logs

```sh
adb shell dmesg -r > dmesg.txt
adb shell logcat -b all -v uid -d > logcat.txt
```

@tab FinaSU state

```sh
adb shell /data/adb/ksud boot-info current-kmi > current-kmi.txt
adb shell /data/adb/ksud boot-info supported-kmis > supported-kmis.txt
adb shell /data/adb/ksud module list > modules.txt
adb shell /data/adb/ksud nomount status --json > nomount.json
```

@tab Files and mounts

```sh
adb shell cat /proc/1/mountinfo > mountinfo.txt
adb shell cat /proc/modules > proc_modules.txt
adb shell ls -alRZ /data/adb > adb-details.txt
```

::::

When ADB is a plain Shell, some commands may be denied by SELinux or permissions. If the device provides ADB Root, enable it as described in [Security Settings](/en/setting/security/) and collect afterwards; disable it when troubleshooting ends.

::: danger Do not clear logs before collecting

`dmesg -c`, clearing SU logs or deleting `/data/adb/ksu/log` destroys the timeline. Unless a maintainer explicitly asks and the original logs have been saved, do not clean anything.

:::

## How to Build a Timeline

When reporting, put "action" and "log time" together:

```text
20:41:03  reboot the device
20:42:10  open the Manager, the home screen shows LKM / driver connected
20:42:18  run a Root request in the target app
20:42:19  the app reports "no Root"
20:42:25  open Superuser, the UID is 10xxx, the Root switch is off
20:43:00  export logs
```

If the logs use UTC, kernel monotonic time or another format, keep the original time and add the converted device local time. Do not provide only a screenshot that crops the status bar clock.

## Privacy Review

:::: collapse accordion

### Device and Account Information

Properties, package lists and logs may contain the device model, build fingerprint, serial number, account-related fields, network addresses and installation sources. Keep only the parts needed to locate the problem.

### Apps and UIDs

`packages.txt`, SU logs, process snapshots and the allowlist expose installed packages, running processes, UIDs and authorization state. If some app names are sensitive, replace them with consistent codenames before publishing and state the replacement rule.

### File Paths and Command Lines

A process command line may contain script paths, mount points, accounts, tokens, intranet addresses or private arguments. Delete the values rather than the whole context.

### Manager and Backup Credentials

Do not publish the Manager password, WebDAV user name/password, app lock PIN, private keys or backup keys. An ordinary FinaSU log bundle should not be treated as an attachment that can be published directly.

### Kernel Symbols and Memory Addresses

`kallsyms.txt` may contain a large number of kernel symbols and addresses. Provide it only when a maintainer asks, and ask whether an alternative collection method exists before publishing.

::::

## Minimal Excerpt or Full Bundle

::::: field-group

:::: field The full bundle suits
@type `Complex problems`
Boot anomalies, module mounting, kernel capabilities, Manager identity and multi-layer dependency problems. Maintainers can cross-check, but privacy must be handled first.
::::

:::: field A minimal excerpt suits
@type `Single problems`
A specific error text, the SU request of one app, or one definite UAPI error. After trimming, still keep the time, the process and the surrounding context.
::::

:::: field Do not send only screenshots
@type `Insufficient evidence`
Screenshots cannot be searched, do not keep full stack traces, and often crop the status bar clock. Provide the raw text as well.
::::

:::::

## Related Pages

- [Diagnostics & Feedback](/en/setting/diagnostics/): the settings entry and the feedback boundaries.
- [SU Logs](/en/manager/su-log/): event types, filtering and privacy.
- [Reporting Issues](/en/troubleshoot/report/): the report template and required fields.
- [Driver Runtime Errors](/en/troubleshoot/runtime/): runtime-layer problems commonly seen in logs.
