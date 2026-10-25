---
title: Working Card
description: CircleUI status card run states, mode tags, tap behavior and background limits
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-circle/working-card/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Working Card

<Badge text="Status card" type="tip" /> <Badge text="Tap to install/update" type="info" /> <Badge text="No long-press card editing" type="warning" />

The CircleUI "working card" is the full-width status card at the top of the home screen. It reports whether the driver is available, the version, the UAPI and the current run mode, and provides entries for installing, updating or jailbreaking.

## States and Tags

:::: table Actual Display

| Condition | Title | Supporting information |
| --- | --- | --- |
| Driver connected | Running | `version-UAPI` |
| Not connected and the kernel is GKI | Not installed | Prompt to tap and install |
| Not connected and the kernel does not qualify | Not supported | Reason for the lack of support |

::::

When the driver is connected, the status card can show:

- The `LKM` run mode tag;
- The `Built-In` run mode tag;
- A safe mode tag;
- A jailbreak mode tag;
- A custom LKM tag.

The "Hide status tags" setting only affects the run mode tags at the top of the Circle and Stats list; it does not hide safe mode, jailbreak mode or custom LKM information.

When the driver is not installed, the kernel is installable and SELinux is Permissive, a jailbreak button may appear on the right. It is a temporary route for the current session and cannot replace installing into the boot image.

## Tap Behavior

- Driver available: tapping enters the install/update flow;
- Not installed: tapping starts installation;
- Lazy-loading mode: tapping is disabled so that a session unsuitable for flashing does not enter installation;
- Jailbreak button: uses a separate lazy-loading flow;
- Long press: there is no Circle-specific surface editing panel.

## Background Limits

The Circle status card does not have the following fields:

- A separate image;
- Separate opacity;
- Separate dim;
- Hiding the status icon;
- Hiding the title.

These controls correspond to an actual surface only on the Grid working card page. The Circle status card uses only the global wallpaper and theme surface material.

## Colors

When no wallpaper is enabled, the status card uses semantic container colors: normal states and error/not-installed states use different roles. Once a wallpaper is enabled, it switches to the shared wallpaper surface tokens so that text contrast stays readable over a translucent background.

## Related Pages

- [Component Hiding](/en/theme/theme-circle/component-hiding/)
- [Background](/en/theme/theme-circle/background/)
- [Home & Layout](/en/setting/home/)
