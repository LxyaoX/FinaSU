---
title: YukiZygisk
description: The Zygisk engine built into FinaSU, its injection switch, daemon state and settings
createTime: 2026/10/26 00:00:00
permalink: /en/builtin/yukizygisk/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# YukiZygisk

<Badge text="Zygisk injection" type="info" /> <Badge text="Needs a matching kernel module" type="warning" />

YukiZygisk is the Zygisk engine that ships with FinaSU. Unlike the earlier userspace-only engine, the part that maps modules into zygote lives in the kernel (`feature/yukizygisk`); `ksud` installs the payload, starts the `zygiskd` daemons at boot, and writes the settings into `yzconfig.json`.

## What it is made of

| Part | Location | Role |
| --- | --- | --- |
| Kernel feature | `feature/yukizygisk` inside `kernelsu.ko` | Hooks zygote's exec, registers the injection endpoint, performs injection |
| Daemons | `/data/adb/ksu/bin/zygiskd64` (plus `zygiskd32` where 32-bit apps run) | Scan modules, keep the endpoint, hand settings and state to the kernel |
| Injected libraries | `/data/adb/ksu/lib/yukizygisk/lib*.so` | The runtime that enters app processes |
| Settings | `/data/adb/ksu/yukizygisk/yzconfig.json` | Six switches, read when a daemon starts |
| Diagnostics | `/data/adb/ksu/yukizygisk/diagnostics/current/` | Per-boot logs and evidence |

::: warning Installing the manager alone injects nothing

Injection happens in the kernel. After updating the manager, flash the matching `kernelsu.ko` too, or the page will report "Kernel module too old".

:::

## States on the page

| State | Meaning | What to do |
| --- | --- | --- |
| Running · N daemons | The daemons are up and their endpoint is registered | Nothing; each ABI's pid is listed below |
| Injection is off in the kernel | Daemons run, but the kernel's injection bit is clear | Tap the row to restart the engine |
| Kernel module too old | This `kernelsu.ko` has no injection runtime | Flash a kernel module from this release |
| Engine stopped | A daemon failed to start | The page shows its last log line |
| Not running | Payload installed, no process | Tap to retry, or read the log |

A daemon forks one companion per loaded module, so `ps` showing several `zygiskd64` processes is expected: the state row counts daemons, one per ABI.

## The master switch

The switch at the top of the page is on by default. Turning it off stops the daemons and keeps them down at the next boot - the quickest way out when a module makes the system unusable. The state is just the presence of `/data/adb/ksu/yukizygisk/disabled`.

## Settings

| Setting | Default | Effect |
| --- | --- | --- |
| Use YukiLinker | on | Load modules with the built-in linker instead of the system `dlopen` |
| Anonymous memory | on | Map the runtime core and modules into anonymous memory |
| Early native loading | off | Load some native modules during early boot |
| Module crash protection | on | Isolate modules that crash the framework and retry startup |
| Kernel log | off | Mirror engine messages into the kernel log |
| Denylist mode | off | How modules are treated for apps on the denylist: off / force / restore |

Changing any of them writes `yzconfig.json` and restarts the engine immediately, because a daemon only reads that file when it starts.

## Logs and diagnostics

```sh
su -c '/data/adb/ksud yukizygisk status'
su -c 'tail -n 50 /data/adb/ksu/yukizygisk/diagnostics/current/logs/zygiskd64.log'
```

`status` prints `installed`, `supported`, `enabled`, `kernelSupported`, `featureEnabled`, `daemons` and `config`; every row on the page comes from it. When nothing is running, `lastError` carries the last failure.

## Common questions

::: collapse accordion

### The APK is installed and the switch is on, but nothing is injected

Check whether the page says "Kernel module too old" first. Half of injection lives in the kernel; an older `kernelsu.ko` has no such feature, so the daemon exits during startup. Flash the kernel module from this release and look at the state again.

### Why does `ps` list several `zygiskd64` processes?

Those are per-module companions, not extra daemons. The state row counts daemons by their lifetime lock, so an ABI shows up once.

### Do I need to reboot after changing a setting?

No - saving restarts the engine for you. A new kernel module still has to be flashed and the device rebooted.

:::
