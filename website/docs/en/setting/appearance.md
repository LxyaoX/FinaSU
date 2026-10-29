---
title: Appearance Settings
description: Theme, wallpaper, home layout, titles and information visibility
createTime: 2026/10/24 00:00:00
permalink: /en/setting/appearance/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Appearance Settings

<Badge text="Settings entry 2/8" type="info" /> <Badge text="Interface only" type="tip" />

Appearance decides what the Manager looks like: colors, background, home structure, icons and information cards. Most changes here do not affect Root authorization, module state or kernel features, but "Restore the default theme" clears several customized resources.

::: tip How to get there

Open the Manager → Settings → tap **Appearance**, the second entry in the first row.

:::

## Appearance Feature Map

::: table Find an option by need

| What you want to change | Option |
| --- | --- |
| Light and dark, accent color, Material style | Theme settings |
| Background image, video, transparency, blur | Wallpaper |
| Whether the top bar uses a large title or the traditional style | Layout variant |
| Home title text or title image | Top bar title and advanced title image |
| Hide the fingerprint, Zygisk, SUSFS or mount information on the home screen | Home screen information |
| Overall type density, collapsed lines of the module description | Interface scale, maximum module description lines |

:::

## Theme Settings

Theme settings control the Manager's light and dark mode, accent color and Material 3 component appearance.

### Light and Dark Mode

- **Follow system**: the Manager switches together when the system switches between light and dark.
- **Light**: always uses the light interface.
- **Dark**: always uses the dark interface.
- **AMOLED pure black**: uses a pure black or near-black background in dark mode, which suits OLED screens and saves more power.

### Dynamic Colors and Accent Colors

After system dynamic color is enabled, the accent color references the device's current wallpaper or system theme. With dynamic color off, you can choose from the accent colors the Manager provides, such as red, pink, purple, blue, cyan, green, yellow, orange and brown.

"Pick color from wallpaper" is a separate option on the wallpaper page. It generates interface colors from the Manager's own wallpaper and is not the same as system dynamic color.

### Color Standard and Style

- **Color standard**: decides whether colors are generated with the MD3 2021 or the M3E 2025 rules.
- **Color style**: choose among hue relationships such as soft, neutral, vibrant, expressive, rainbow, colorful, monochrome, fidelity and content.
- **Button indicators**: adds a status icon inside switches.
- **Discrete sliders**: shows ticks on the slider track.

These parameters combine with each other. After changing the color standard or color style, it is normal for the previous accent color to look different.

For theme details, see [Theme & Color](/en/setting/theme/).

## Wallpaper

The wallpaper page layers the background and the interface surfaces:

- **Enable wallpaper**: shows the background underneath the pages.
- **Unified or per-page background**: all pages can share one image, or the home screen, plugins, superuser, modules and settings can each use their own.
- **Video background**: replaces the static wallpaper with a looping video, with adjustable video volume.
- **Surface opacity**: controls how much the cards cover the background. Lower values are more transparent, and make text contrast harder to guarantee.
- **Blur**: softens the background and reduces how much a busy image interferes with text.
- **Scrim**: darkens the background to improve readability.
- **Separate day and night scrim**: sets the scrim strength for light and dark mode separately.
- **Card background / focused card background**: makes specific cards use the wallpaper layer.
- **Pick color from wallpaper**: extracts the Manager accent color from the image. In multi-background mode the home image is used as the global seed, so it is not recomputed while pages scroll; video mode does not provide live color extraction.

### Working Cards

The work card - the tall card at the top of the home screen - owns a separate wallpaper surface that does not affect the page background:

- **Image**: pick an image for the work card alone.
- **Enable work card background**: when off, no image is painted and the card keeps its plain fill.
- **Opacity / Dim**: both can switch to separate day and night values; the dim keeps the large status word readable.
- **Hide mark / Hide text / Hide run mode**: drop the check mark in the corner, the version line under the title and the run-mode tag along the top edge respectively.

### Video Background

After the video background is enabled, the video replaces the static image:

- Select a video file.
- Adjust the video volume.
- Clear the current video file.

The scrim can still be used in video mode, but "Surface opacity", "Blur" and "Pick color from wallpaper" are hidden because they only apply to static images. Video decoding and continuous playback use more power, so use it with care on low-performance devices.

## Home Layout

There is one arrangement and no layout picker: the work card first, then the notices and version warnings, the two stat tiles and the system information card. See [Home & Layout](/en/setting/home/) for what each one shows.

## Layout Variants

The layout variant applies to the plugins, superuser and modules pages:

- **M3E**: large title, collapsible inline search bar and a more pronounced scroll hierarchy.
- **Traditional**: compact top bar that takes less vertical space.

If page content is covered by the large title, or you want more entries visible on a tablet in landscape, switch to "Traditional".

## Top Bar Title and Title Image

The top bar title has three sources:

- **App name**: uses the current app name.
- **KernelSU**: shows the KernelSU title.
- **Custom**: enter your own title text.

If **advanced title image** is enabled, the image replaces the text title. Once enabled, you can adjust:

- Day opacity.
- Night opacity.
- Darkening amount.
- Horizontal offset, ranging from `-200 dp` to `200 dp`.

The text title is still kept in the configuration. After the title image is turned off, the selected text title is shown again.

## Home Screen Information

The home screen information area can hide the following rows:

- **System fingerprint**: usually contains the device, system build and kernel identification.
- **Zygisk implementation**: the matching switch only appears when a Zygisk implementation actually exists.
- **SUSFS**: only shown when SUSFS is detected.
- **Module mount**: only shown when the Folk Mount state is readable.

::: info Hiding is not turning off

Hiding only changes what the home screen shows; it does not turn off Zygisk, SUSFS or module mounting, and does not change detection results. When troubleshooting compatibility problems, restore the display first and then judge the actual state.

:::

## Display Density

### Interface Scale

Interface scale ranges from `80%–110%`. It adjusts the overall display ratio of the Manager:

- Larger values make text and controls easier to read, but less content fits on one screen.
- Smaller values fit more content, but too small a value makes tapping and reading harder.
- Scaling does not change the Android system display size.

### Maximum Module Description Lines

The range is `1–5 lines`. A module description longer than that is collapsed; tapping the module card expands the full text.

If a module author wrote long and important installation notes, do not set this to 1 line, or warnings are easy to miss.

## Appearance Setup Order

::: timeline One complete appearance pass

- Choose light or dark mode, dynamic color or an accent color to settle the overall palette
  time=Basics

- Set the wallpaper, blur, scrim and surface opacity so text stays clear
  time=Background

- Pick the layout variant, so list pages use the large title or the compact bar
  time=Structure

- Set the title image, information visibility, scale and description lines
  time=Details

:::

## Related Pages

- [Theme & Color](/en/setting/theme/)
- [Wallpaper](/en/setting/wallpaper/)
- [Home & Layout](/en/setting/home/)
