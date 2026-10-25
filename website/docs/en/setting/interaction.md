---
title: Interaction & Compatibility
createTime: 2026/10/24 00:00:00
permalink: /en/setting/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Interaction & Compatibility

## Legacy SU Command Support

Controls the `/system/bin/su` compatibility entry:

- **Enabled (default)**: allows legacy calls.
- **Disabled until next reboot**: disabled for the current boot.
- **Always disabled**: stays disabled on later boots.

When the kernel does not support it or the policy is forced, the option shows the corresponding state. After you turn it off, apps that call `su` only through an absolute path may no longer escalate privileges.

## Soft Reboot

When installing, updating, enabling or disabling modules, use a soft reboot instead of a full reboot. A soft reboot reloads the userspace services faster, but it cannot replace a boot image or kernel driver update. Jailbreak mode forces a soft reboot to keep the current Root.

## WebView Debugging

Enables WebView debugging for module WebUI. This option widens the debugging surface, so enable it only while developing or troubleshooting page problems.

## Auto Jailbreak

Configurable only in Jailbreak mode. When Permissive SELinux is detected at boot, Manager automatically tries to escalate privileges through Magica. It requires the autostart permission for Manager, and success is not guaranteed after every environment change.
