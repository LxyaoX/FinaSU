---
title: Transparency & Surfaces
icon: material-symbols:opacity
description: FinaSU wallpaper surface opacity, dim, day and night separation and layout-specific surfaces
createTime: 2026/10/24 00:00:00
permalink: /en/theme/global/transparency/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Transparency & Surfaces

<Badge text="Opacity applies to interface surfaces" type="tip" /> <Badge text="Dim applies to the background" type="info" /> <Badge text="Circle has no dedicated card surface" type="warning" />

FinaSU draws a background image or video separately from the foreground surfaces. Opacity does not fade the wallpaper itself; it adjusts the alpha of foreground cards and containers. Dim is what overlays a black mask on the background.

## Main Wallpaper

The wallpaper master switch controls all background modes. Once enabled you can choose:

- a single-image background `background.*`;
- a per-page background for each of the five pages;
- a video background `video_background.*`.

The main configuration contains:

:::: table Main wallpaper fields

| Field | Effect | Range |
| --- | --- | --- |
| `backgroundOpacity` | Opacity of the foreground surfaces | 0–1 |
| `backgroundDim` | A single dim shared by day and night | 0–1 |
| `isDualBackgroundDimEnabled` | Whether day and night dim are separate | Boolean |
| `backgroundDayDim` | Day dim | 0–1 |
| `backgroundNightDim` | Night dim | 0–1 |
| `isMultiBackgroundEnabled` | Whether each main page uses its own background | Boolean |
| `wallpaper_use_color` | Whether to extract the color seed from the wallpaper | Boolean |

::::

The higher the opacity, the more the background is covered by cards and the more stable text contrast becomes; the lower the opacity, the more visible the background texture.

## Page Backgrounds

Multi-background mode provides five slots:

:::: table Per-page resources

| Page | ZIP base name | Internal page index |
| --- | --- | --- |
| Home | `background_home.*` | Home |
| Plugin | `background_kernel.*` | Plugin |
| Superuser | `background_superuser.*` | Superuser |
| Module | `background_system_module.*` | Module |
| Settings | `background_settings.*` | Settings |

::::

Plugin uses `background_kernel` because of a historical compatibility name; it does not write the image into the kernel. When a page has no image of its own, that page draws no per-page background; do not mistake it for a broken resource.

## Layout-Specific Surfaces

::::: field-group

:::: field Grid working card
@type `layout.grid.workCard`
A dedicated image `grid_working_card_background.*`. It supports a single opacity, day and night opacity, a single dim, an enabled flag and three hiding switches.
::::

:::: field Four Focus cards
@type `layout.focus`
The parent stores the shared opacity, day and night opacity and day and night dim; the four child cards only store their own images. When the parent is off, none of the four cards shows a background image.
::::

:::: field Dashboard hero
@type `layout.dashboard.hero`
The image `dashboard_card_bg.*`, supporting its own enabled flag, day and night opacity and day and night dim.
::::

:::: field Circle status card
@type `No dedicated image surface`
CircleUI provides no separate working card image. It only uses the page background and the global surface material, so the wallpaper page shows no Circle-specific working card entry.
::::

:::::

## Day and Night Separation

Day and night opacity only takes effect after `dual_opacity` is enabled on the corresponding surface; day and night dim only takes effect after `dual_dim` is enabled. Do not change the day or night values while forgetting to turn the separation switch on.

The Grid working card supports day and night opacity, but the current registry has no day and night dim for it; the Dashboard and Focus parents support separation for both opacity and dim.

## Video Background

Video and images share the wallpaper master switch. In video mode:

- the video is cropped to fill and loops in the foreground;
- the black mask uses the wallpaper dim;
- image blur does not apply to video;
- in video mode the Surface opacity and Blur sliders are hidden; an already saved opacity still takes part in the global surface tokens, and blur does not apply to the video;
- extracting color from the wallpaper depends on an already saved image seed; video frames do not extract a new seed in real time, and when no seed is available it falls back to the accent color or system dynamic color.

Do not confuse video dim with video volume. The volume field is `videoVolume` and only controls the video sound.

## Common Questions

::::: collapse accordion

:::: collapse I changed the opacity but the background is still completely invisible
Check the main wallpaper switch, whether the current page has a background, whether the surface is enabled, and whether the card itself is an opaque material.
::::

:::: collapse Only one page has no background
Multi-background mode requires that page slot to have its own image. A page without configuration does not borrow an image from another page.
::::

:::: collapse Only one of the four Focus cards shows an image
This is an allowed state. The four child card images are independent, while the parent switch and shared values apply to all of them.
::::

:::: collapse Day and night look the same
Confirm that day and night separation is enabled and that the current theme is actually in the matching light or dark mode.
::::

:::: collapse I want to set a working card image on Circle
The current FinaSU CircleUI has no such surface. Switch to Grid, Focus or Dashboard, or use a page background.
::::

:::::

## Related Pages

- [Wallpaper](/en/setting/wallpaper/): all sliders and the per-page selection entry.
- [Background Blur](/en/theme/global/blur/): the image blur range.
- [Video Background](/en/theme/global/video/): video looping and lifecycle.
- [Grid Working Card](/en/theme/theme-grid/working-card/): the complete Grid surface fields.
- [Focus Basic Styling](/en/theme/theme-focus/basic/): the shared configuration of the four cards.
