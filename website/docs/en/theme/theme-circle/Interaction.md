---
title: Interaction
description: CircleUI tap areas, install entry, navigation and system animation boundaries
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-circle/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Animation & Interaction

<Badge text="Material 3 Expressive" type="tip" /> <Badge text="No standalone animation engine" type="info" /> <Badge text="Long press does not edit the card surface" type="warning" />

CircleUI feedback comes from Compose Material 3 Expressive: card press, ripple, page transitions and system predictive back. A theme cannot inject custom animation scripts, and there are no Circle-specific animation speed or duration fields.

## Tappable Areas

:::: table Home Screen Interaction

| Area | Tap result |
| --- | --- |
| Status card | Enters the install/update flow; disabled in lazy-loading mode |
| Superuser count | Jumps to the Superuser page |
| Module count | Jumps to the Modules page |
| SUSFS row | Opens the SUSFS management page |
| Support card | Opens the corresponding external URL |
| Top bar install | Enters install/update |
| Top bar reboot | Opens the reboot action list |

::::

The Circle status card has no long-press action. Long-press editing of the image and transparency is a Grid, Focus or Dashboard surface behavior.

## Navigation

The bottom bar, side rail, automatic mode and floating mode are decided by settings. Custom navigation icons are theme resources, but they do not change the number of pages or the target routes.

Whether predictive back is available also depends on the Android version and system animations. FinaSU can only enable it according to settings; it cannot add the capability to a system that does not support it.

## Reduced Motion

The current theme protocol has no separate "disable all animations" field. System reduced motion, developer options and the Android version affect some system-level animations; in-app card feedback is still controlled by the Compose theme.

## Related Pages

- [Navigation](/en/setting/navigation/)
- [Interaction & Compatibility](/en/setting/interaction/)
- [Theme Preview & Usage](/en/theme/theme-use/)
