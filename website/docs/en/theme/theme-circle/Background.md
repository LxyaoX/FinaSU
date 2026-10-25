---
title: Background
description: CircleUI page background, per-page wallpaper, video and the dedicated card surface that does not exist
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-circle/background/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Background

<Badge text="Page background" type="tip" /> <Badge text="Per-page supported" type="info" /> <Badge text="No dedicated working card image" type="warning" />

The CircleUI background comes from the global wallpaper layer, not from a Circle-specific card surface. It supports a single image, per-page images and video, but it does not overlay a separate image on the status card.

## Single Image Background

Enable the wallpaper and pick an image in "Settings → Appearance → Wallpaper". The image is cropped to the window and used as the shared background for all pages.

:::: tip Image Selection

You can pick an ordinary image directly, or download one from the wallpaper gallery first and then select it manually. Downloading from the gallery does not automatically apply the image as the Manager wallpaper.

::::

## Per-Page Background

Once multiple backgrounds are enabled, each of the five main pages can use its own image:

:::: table Page Mapping

| Page | Theme entry |
| --- | --- |
| Home | `background_home.*` |
| Plugins | `background_kernel.*` |
| Superuser | `background_superuser.*` |
| Modules | `background_system_module.*` |
| Settings | `background_settings.*` |

::::

The Circle home screen only uses `background_home.*`. When no image is set for that page, the page does not borrow the background of another page.

## Video Background

Video uses `video_background.*` and is gated as a whole by the wallpaper master switch. While playing it replaces the image background layer and fills the window according to the crop mode. See [Video Background](/en/theme/global/video/) for details.

## Surface Layers

There are two more layers above the background:

- Background dim: a black overlay that improves contrast;
- Foreground surface opacity: how much of the background the cards cover.

Image blur applies only to images, not to video. The Circle status card has no separate image or opacity settings, so it follows the global page surface material.

## Theme Fields

```json
{
  "isBackgroundEnabled": true,
  "backgroundOpacity": 0.6,
  "backgroundBlur": 20,
  "backgroundDim": 0.2,
  "isDualBackgroundDimEnabled": true,
  "backgroundDayDim": 0.2,
  "backgroundNightDim": 0.4,
  "isMultiBackgroundEnabled": true,
  "wallpaper_use_color": false
}
```

## Related Pages

- [Wallpaper](/en/setting/wallpaper/)
- [Global Background](/en/theme/global/transparency/)
- [Background Blur](/en/theme/global/blur/)
