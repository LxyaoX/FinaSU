---
title: Theme & Color
createTime: 2026/10/24 00:00:00
permalink: /en/setting/theme/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Theme & Color

<Badge text="Changes the Manager only" type="tip" /> <Badge text="Wallpaper seed takes priority" type="warning" />

"Settings → Appearance → Theme settings" manages light and dark mode, accent colors and Material 3 Expressive visual parameters in one place. It does not change the Android system theme and does not affect Root state.

## Light and Dark Mode

- **Follow system**: uses the device light or dark state.
- **Light**: always a light interface.
- **Dark**: always a dark interface.
- **AMOLED pure black theme**: only appears in dark mode, and sets the main background and surfaces to pure black.

## Dynamic Colors

"Follow light" in the color picker corresponds to the system dynamic seed; after you select a specific color swatch, that ARGB accent color is saved. The interface offers 15 preset colors, and the theme protocol also accepts 5 color tokens carried over from FolkPatch.

The wallpaper page can enable "Pick color from wallpaper". In multi-background mode the seed extracted from the home screen image is fixed as the global accent color and is not regenerated every time a page scrolls; video mode does not extract colors from video frames in real time.

## Color Specification

- **Color standard**: MD3 2021 or M3E 2025.
- **Color style**: choose among hue relationships such as soft, neutral, vibrant, expressive, rainbow, colorful, monochrome, fidelity and content.
- **Button indicators**: adds a status icon inside switches.
- **Discrete sliders**: shows ticks on the slider track.

The color protocol also accepts the fields `colorGenerationMode` and `colorContrast`, but the current page has no separate sliders for them.
