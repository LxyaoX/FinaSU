---
title: Hiding & Kernel Security
createTime: 2026/10/24 00:00:00
permalink: /en/setting/hiding/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Hiding & Kernel Security

## Hiding SELinux Modifications

Prevents apps from detecting the SELinux policy changes loaded by FinaSU. When the kernel does not support it or policy forces it, the switch shows a non-editable state. It usually needs a reboot to take effect.

## Hiding Mount Records

Removes Root and module-related records from the `/proc` mount view of ordinary apps and isolated processes. This capability is limited by the kernel build and runtime state; when it reports unavailable, do not keep toggling it.

## AVC Log Spoofing

Handles the target context in SELinux audit logs so the logs do not directly expose FinaSU-related objects. It does not hide every system difference, and it does not replace per-app module unmounting.

## SELinux Mode

The switch dialog offers only Enforcing and Permissive as targets, and executes `setenforce 1` and `setenforce 0` underneath. There is no "switch to Disabled" option.

If the underlying `getenforce` returns `Disabled`, the page shows "disabled" as the current state; this is a read-only state, not a selectable mode. `setenforce 0` gives Permissive, not Disabled.

Permissive lowers the system security boundary and should not be kept long term except for the jailbreak flow or explicit troubleshooting. For the full explanation, see [Security](/en/setting/security/).

::: warning
Do not enable conflicting hiding approaches at the same time. Reboot after a change, then verify authorization, module loading and the app file view separately.
:::
