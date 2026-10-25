---
title: Background
description: GridUI page background, per-page wallpaper and the independent working card image
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-grid/background/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Background

<Badge text="Two background layers" type="tip" /> <Badge text="Page wallpaper plus working card" type="info" /> <Badge text="No separate count card image" type="warning" />

GridUI can use two background layers at the same time:

1. Page background: the global wallpaper or the per-page home screen wallpaper;
2. Working card background: the left status card's own image.

The two count cards and the system info card have no separate image surface.

## Page Background

The page background is controlled by "Settings → Appearance → Wallpaper":

- Single image mode uses `background.*`;
- Multi-background mode uses `background_home.*` for the home screen;
- Video mode uses `video_background.*`;
- Opacity, dim, blur and color extraction come from the global wallpaper configuration.

Grid itself does not change the crop or coverage of the page wallpaper.

## Working Card Background

Independent working card entries:

```text
grid_working_card_background.jpg
grid_working_card_background.png
grid_working_card_background.gif
grid_working_card_background.webp
grid_working_card_background.jpeg
```

They are selected in the extension priority order above. Once the image exists, the corresponding surface must also be enabled; with the resource present but not enabled, the card still shows semantic colors.

::::: field-group

:::: field Enabled
@type `surface.layout.grid.workCard.enabled`
When turned off, the image file is kept but the card does not draw it.
::::

:::: field Opacity
@type `0..1`
Use a single `opacity`, or enable `dual_opacity` and then set separate day/night values.
::::

:::: field Dim
@type `0..1`
The Grid working card has only one `dim`; the current registry has no separate day and night dim.
::::

:::: field Cropping
@type `Actual card bounds`
The image is cropped and filled to the actually measured size of the working card; after layout changes, the actual width and height take precedence.
::::

:::::

## Stats Grid Top

When the home layout is StatsUI and `statsTopLayout=grid`, the top area reuses the Grid working card and count cards. The wallpaper page then also shows the Grid working card settings.

After switching back to the Stats `list` top, the surface is no longer used by Stats, but GridUI can still use the same configuration.

## Entry Points

There are two ways to reach the working card settings:

- "Settings → Appearance → Wallpaper", and select the working card of the current layout;
- Long-press the working card on the home screen to open the surface options directly.

## Related Pages

- [Working Card](/en/theme/theme-grid/working-card/)
- [Global Background](/en/theme/global/transparency/)
- [Wallpaper](/en/setting/wallpaper/)
