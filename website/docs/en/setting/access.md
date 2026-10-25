---
title: Access Control
createTime: 2026/10/24 00:00:00
permalink: /en/setting/access/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Access Control

## Allow Any Dynamic Manager

By default, only a preset manager signature can show "Set as dynamic manager". This experimental option allows the entry to appear for any app. Enabling it significantly widens the range of managers the kernel can trust.

For the specific dynamic manager flow, see [Dynamic Manager](/en/manager/dynamic-manager/).

## ADB Root

Runs `adbd` with Root privileges. This capability may require restarting the ADB service, and it is limited by the system build and SELinux. Turn it off as needed after use.

## Shell Root via ADB Only

Adds a source check for an already authorized Shell: only a Shell started by `adbd` or by a KernelSU-family Root process can escalate. It prevents an ordinary app from escalating to Shell first and then using that Shell to get Root.

## Recommendations

- Keep the experimental entry off when you do not use a dynamic manager.
- Enable ADB Root only during debugging.
- Keep the official manager signature and do not re-sign and distribute it under the same package name.
