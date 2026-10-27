---
title: Maintenance & Risky Actions
description: The uninstall entry on the Settings home screen and what to prepare before flashing
createTime: 2026/10/24 00:00:00
permalink: /en/setting/maintenance/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Maintenance & Risky Actions

<Badge text="Home screen shortcut" type="info" /> <Badge text="Back up first" type="danger" />

The maintenance entry on the Settings home screen is not a ninth settings category. It currently contains only the "Uninstall" entry, which appears in LKM mode.

Before you actually flash, you should still:

- Save the stock boot image.
- Back up modules and the Superuser list.
- Confirm the device model, system version and slot.
- Prepare working `fastboot`, Recovery or the official rescue tool.

## Uninstall

"Uninstall" appears only in LKM mode; the entry is unavailable in Jailbreak mode (late load).

The current dialog offers two options:

:::: field-group

::: field Permanent uninstall
@type `Removes Root`
@default `Cannot be undone`
Enters a flashing flow that completely removes the KernelSU-family Root and module management data. Back up the modules and authorization list you want to keep before running it.
:::

::: field Restore stock image
@type `Flash back the boot partition`
@default `Requires a matching image`
Select a stock boot image that matches the current device, system and slot, and flash it back. This restores the boot partition; it does not mean cleaning all management data in `/data`.
:::

::::

::: danger Do not use "Uninstall" instead of bootloop rescue

When the device already cannot boot, the uninstall entry inside Manager usually cannot be used either. Go directly to the Bootloader or Recovery as described in [Bootloop Rescue](/en/guide/rescue-bootloop/) and restore a matching stock image.

:::

## Pre-Operation Checklist

:::: steps

1. Create a backup under "Settings → Backup" and confirm the entry exists in "Manage backups".
2. Record the Manager version, the driver runtime mode, the device model, the Android build and the KMI.
3. Find the stock `boot` / `init_boot` image that exactly matches the current system.
4. Confirm the computer can recognize the device, and know the current A/B slot.
5. Keep enough battery charge and do not unplug, cut power or run a system OTA at the same time during the operation.

::::

::: warning Before restoring the stock image

The stock image must come from the current device model and the current system version; do not use an image from an adjacent version, another region's firmware, or another device. On A/B devices, also confirm the target slot.

:::

## Related Pages

- [Bootloop Rescue](/en/guide/rescue-bootloop/)
- [Updating FinaSU](/en/guide/update/)
- [Backup](/en/setting/backup/)
