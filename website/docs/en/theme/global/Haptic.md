---
title: Haptics
icon: material-symbols:vibration
description: Switch, strength, scope and theme export boundaries of FinaSU Manager touch vibration
createTime: 2026/10/24 00:00:00
permalink: /en/theme/global/haptic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Haptics

<Badge text="Local device setting" type="tip" /> <Badge text="Not part of .fpt" type="warning" /> <Badge text="Strength 0–1" type="info" />

Haptics provide short tactile feedback for touches inside the Manager. They do not change Android system vibration, call vibration or the haptic settings of other apps.

## Settings

:::: table Vibration options

| Option | Value | Behavior |
| --- | --- | --- |
| Touch vibration | On / off | Master switch |
| Strength | 0–1 | Mapped to the device vibration amplitude |
| Feedback scope | `global` / `bottom_bar` | All touches or navigation controls only |

::::

Dragging the strength slider and releasing plays a test vibration once. A strength of 0 triggers no vibration.

## Actual Behavior

The Manager creates one single vibration of about 30 ms:

- when the device supports amplitude control, the 0–1 strength is mapped to 1–255;
- when amplitude control is not supported, the device default amplitude is used;
- when the device has no usable vibrator or the system rejects the call, it fails silently without blocking the interface action.

So the same 50% strength may feel different on different phones. Hardware amplitude capability, system haptic policy and power saving settings all affect the result.

## Why It Is Not in the Theme Package

The theme import interface states explicitly that the boot sound and vibration remain device settings. The FinaSU `.fpt` export mapping has no vibration fields and no vibration resource files, so:

- a theme cannot carry the vibration switch;
- a theme cannot set the vibration strength;
- a theme cannot change the feedback scope to all touches;
- restoring the default theme does not reset vibration.

:::: warning The feedback scope and the sound effect scope are set separately

Touch sound effects and vibration each store their own `global` / `bottom_bar`. Changing one does not make the other follow automatically.

::::

## Common Questions

::::: collapse accordion

:::: collapse Taps make a sound but do not vibrate
Check the touch vibration master switch, whether the strength is above 0, whether the feedback scope covers the current control, and whether the system turned haptic feedback off.
::::

:::: collapse The test vibration works but normal taps do not
The test vibration and a business tap use different call paths. First confirm that the control belongs to the current feedback scope, then check the system power saving policy.
::::

:::: collapse Vibration changed after importing a theme
The current theme protocol does not write vibration. If it changed, check the system settings or other apps instead of continuing to replace the theme package.
::::

:::::

## Related Pages

- [Multimedia Settings](/en/setting/multimedia/): the vibration entry point.
- [Sound Effects](/en/theme/global/sound/): touch sound effects and the boot sound effect.
- [Theme Package Structure](/en/developer/theme-package/): resources that clearly do not belong to a theme.
