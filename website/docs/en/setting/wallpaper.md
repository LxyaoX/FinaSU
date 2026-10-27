---
title: Wallpaper
createTime: 2026/10/24 00:00:00
permalink: /en/setting/wallpaper/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Wallpaper

The wallpaper page sets a global or per-page background for the Manager and controls the layering between the wallpaper and the surfaces.

## Background

- **Enable wallpaper**: shows the image underneath the interface.
- **Multi-page background**: picks an image separately for the home screen, plugins, superuser, modules and settings.
- Each page can select, change or remove its image.

### Video Background

After the video background is enabled, the video replaces the static wallpaper. You can select or clear a video file and adjust the video volume.

The scrim can still be set in video mode; "Surface opacity", "Blur" and "Pick color from wallpaper" only apply to static images and are hidden for now. Video is decoded and played continuously, so low-performance devices may use more power.

## Display

- **Surface opacity**: how much the cards and page surfaces cover the background.
- **Blur**: sets the blur radius of the background image.
- **Scrim**: darkens the background to keep text contrast.
- **Separate day and night scrim**: sets the scrim for light and dark mode separately.
- **Card background / focused card background**: makes specific cards use the wallpaper layer.
- **Pick color from wallpaper**: uses the current background to generate the accent color.

## Working Cards

Layouts with a dedicated surface can pick an image separately: the Grid working card has full opacity, darkness, day and night opacity and three hide switches; the four Focus cards share the parent values; the Dashboard Hero has its own image and day and night surface parameters; CircleUI has no separate working card image.

