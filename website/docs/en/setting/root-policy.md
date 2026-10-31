---
title: Root & Mount Policy
createTime: 2026/10/24 00:00:00
permalink: /en/setting/root-policy/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Root & Mount Policy

## Kernel-Level Module Unmount

When enabled, the kernel can hide module changes from target apps according to the App Profile policy. The state is reported as manageable, forced by policy or unsupported by the kernel.

This capability, together with the specific module mount backend, decides which files an app sees. NoMount uses its own exception app list and does not follow App Profile automatically.

## Default Unmount Modules

Sets the global default of "Unmount modules" in the non-Root Profile. When enabled, apps without their own configuration see the system file view without module injection.

If an app stops working because it cannot see module files, turn off the "Unmount modules" option for that single app instead of turning off the whole module system.

## Related Settings

- [Hiding & Kernel Security](/en/setting/hiding/)
- [Built-in Capabilities Overview](/en/builtin/)
- [App Profile](/en/manager/app-profile/)
