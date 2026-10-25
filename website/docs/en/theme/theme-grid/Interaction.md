---
title: Interaction
description: GridUI working card taps, long press, count navigation and animation boundaries
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-grid/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Animation & Interaction

<Badge text="Tap to install" type="tip" /> <Badge text="Long press to adjust the card surface" type="info" /> <Badge text="Count placeholders do not jump" type="warning" />

GridUI keeps its two-column space stable: when counts are unavailable only the placeholder text changes, the cards are not removed and the system info card is not pushed into the side column.

## Working Card

- Tap: enters the install/update flow; disabled in lazy-loading mode;
- Long press: opens the working card surface options;
- Before the image loads: semantic colors are used so unreadable text does not appear on a transparent card;
- After the image loads: it is drawn with the surface's opacity and dim.

## Count Cards

When counts are available, tapping opens:

- The Superuser page;
- The Modules page.

When counts are unavailable, a placeholder is shown and no navigation happens. The numbers here come from current Manager data; they are not a tappable "install module" button.

## System Info and Support Card

The SUSFS row opens the management page; the support card opens the FinaSU website, Patreon, the KernelSU documentation or a custom HTTP(S) link depending on its mode.

Page transitions, back gestures and press feedback are decided by Material 3 Expressive and the system. Themes have no standalone animation scripts or duration fields.

## Related Pages

- [Working Card](/en/theme/theme-grid/working-card/)
- [Navigation](/en/setting/navigation/)
- [Interaction & Compatibility](/en/setting/interaction/)
