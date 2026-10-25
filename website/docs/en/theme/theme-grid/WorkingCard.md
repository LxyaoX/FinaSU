---
title: Working Card
description: Grid working card image, opacity, dim, day/night values and three hiding switches
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-grid/working-card/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Working Card Advanced Customization

<Badge text="Independent surface" type="tip" /> <Badge text="Long-press editing supported" type="info" /> <Badge text="Image present is not the same as enabled" type="warning" />

The Grid working card is currently the only working card surface in FinaSU that has an image, opacity, dim and element hiding items at the same time. The StatsUI grid top reuses the same configuration.

## Complete Options

::::: table Working Card Parameters

| Option | Single day/night value | After split is enabled | Range/values |
| --- | --- | --- | --- |
| Background switch | `enabled` | — | Boolean |
| Image | `imageUri` maps to the actual resource | — | Image file |
| Opacity | `opacity` | `day_opacity` / `night_opacity` | 0–1 |
| Opacity split | — | `dual_opacity` | Boolean |
| Dim | `dim` | No day/night split for Grid | 0–1 |
| Hide status icon | `hide_icon` | — | Boolean |
| Hide title | `hide_text` | — | Boolean |
| Hide mode tags | `hide_mode` | — | Boolean |

:::::

The primary keys in a theme are:

```text
surface.layout.grid.workCard.enabled
surface.layout.grid.workCard.opacity
surface.layout.grid.workCard.dual_opacity
surface.layout.grid.workCard.day_opacity
surface.layout.grid.workCard.night_opacity
surface.layout.grid.workCard.dim
surface.layout.grid.workCard.flag.hide_icon
surface.layout.grid.workCard.flag.hide_text
surface.layout.grid.workCard.flag.hide_mode
```

The compatibility implementation also writes `isGridWorkingCardBackgroundEnabled`, `gridWorkingCardBackgroundOpacity`, `gridWorkingCardBackgroundDim`, the day and night opacities and the three hiding flags. FinaSU reads the primary keys first, then falls back to the historic keys.

## Image and Enabling

The image entry supports:

```text
jpg png gif webp jpeg
```

GIF is handled by the system's animated image decoding path; other formats load as ordinary images. Whether the image is drawn is decided by "an image exists and enabled".

Two states are therefore easy to misread:

- An image is selected but the background is off: the file is kept, and the card does not show the image;
- The background is on but the file is missing: a resource warning is produced, and the card falls back to semantic colors.

## Text and Tags

::::: field-group

:::: field Hide status icon
@type `hide_icon`
Hides the success/warning/blocked icon in the top right corner without affecting card taps.
::::

:::: field Hide title
@type `hide_text`
Hides "Running/Not installed/Not supported". The state can still be judged from the icon, the mode and the install button.
::::

:::: field Hide mode tags
@type `hide_mode`
Hides `LKM` / `Built-In`. Safe mode, jailbreak mode and custom LKM may still appear as other markers.
::::

:::::

Hiding the icon, the title and the mode at the same time and then removing other page entries is not recommended; when the state is abnormal, the home screen is left without any information to locate the problem.

## Long Press and Reset

Long-pressing the working card opens the surface options. After an image is selected, the image cropper processes it according to the actual working card bounds.

"Restore the default theme" clears the working card image and resets the surface. Clearing only the working card image does not automatically clear the page wallpaper.

## FAQ

::::: collapse accordion

:::: collapse Image selected but not visible
Check the background switch, whether the file can be decoded, and whether the current layout is Grid, or whether the Stats top is `grid`.
::::

:::: collapse Switching day/night opacity changes nothing
Enable "Separate day and night opacity" first, then set the day and night values separately.
::::

:::: collapse Can dim be split between day and night
The current Grid working card has no `dual_dim`, `day_dim` or `night_dim` fields; day and night dim separation only appears on surfaces that support it.
::::

:::: collapse Can LKM/Built-In still be told apart after hiding the mode tags
The home screen working card no longer shows them, but you can check the install/update page or the status details. Hiding only affects card drawing.
::::

:::::

## Related Pages

- [Background](/en/theme/theme-grid/background/)
- [Component Hiding](/en/theme/theme-grid/component-hiding/)
- [Theme Package Structure](/en/developer/theme-package/#surface-fields)
