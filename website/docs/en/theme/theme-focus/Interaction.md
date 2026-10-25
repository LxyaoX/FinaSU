---
title: Interaction
description: FocusUI card taps, long-press backgrounds, sampling start and stop, responsive switching
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-focus/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Animation & Interaction

<Badge text="All four cards support long press" type="tip" /> <Badge text="The status card performs installation" type="info" /> <Badge text="No standalone animation engine" type="warning" />

The interaction focus of Focus is the independent image entry of each of the four cards and the status card actions. Card reflow is triggered by window size changes, not by a Focus-internal mode the user switches manually.

## Taps

:::: table Card Interaction

| Area | Tap | Long press |
| --- | --- | --- |
| Status card | The action buttons inside the card install/update or jailbreak; the card body itself has no tap action | Opens the status card image options |
| Manager card | The SUSFS row opens the management page | Opens the Manager card image options |
| Device card | None | Opens the device card image options |
| Storage card | None | Opens the storage card image options |

::::

Long press only edits the corresponding child card image; transparency and dim are controlled by the shared parent `layout.focus`.

## Responsive Switching

When the window goes from portrait to landscape and meets the breakpoint, the four cards reflow between a single column and 2×2. There is no horizontal page-switch animation, and card content is not duplicated.

Returning to a size that no longer meets the conditions restores the single column immediately. This process does not reset the images or the shared numeric values of the four cards.

## Metric Sampling

The device card and storage card update only while the Focus home screen is actually shown. Switching to another main page stops continuous sampling; coming back reads new values. When there is no data a placeholder is shown, and old values are not kept pretending to be live.

## Navigation and Animation

Navigation form, predictive back, page transitions and system motion effects are decided by global settings and the Android version. A theme cannot provide standalone Focus animation scripts or speed fields.

## Related Pages

- [Focus Tablet Layout](/en/theme/theme-focus/tablet/)
- [Navigation](/en/setting/navigation/)
- [Interaction & Compatibility](/en/setting/interaction/)
