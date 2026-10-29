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

The work card at the top of the home screen owns a wallpaper surface of its own, independent of the page background:

- **Image**: pick an image for the work card alone.
- **Enable work card background**: when off, no image is painted and the card keeps its plain fill.
- **Opacity**: the image's own opacity, switchable to separate day and night values.
- **Dim**: darkens the image so the large status word stays readable, also day and night aware.
- **Hide mark / Hide text / Hide run mode**: drop the check mark in the corner, the version line under the title and the run-mode tag along the top edge.

The stat tiles and the system information card follow the page background and cannot take an image of their own.

