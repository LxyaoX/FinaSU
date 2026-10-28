---
title: Appearance Settings
description: Theme, wallpaper, font, home layout, titles and information visibility
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

Appearance decides what the Manager looks like: colors, background, font, home structure, icons and information cards. Most changes here do not affect Root authorization, module state or kernel features, but "Restore the default theme" clears several customized resources.

::: tip How to get there

Open the Manager → Settings → tap **Appearance**, the second entry in the first row.

:::

## Appearance Feature Map

::: table Find an option by need

| What you want to change | Option |
| --- | --- |
| Light and dark, accent color, Material style | Theme settings |
| Background image, video, transparency, blur | Wallpaper |
| Interface font | Font |
| The Manager icon on the home screen | Launcher icon |
| Whether the home screen is circular, a grid, a dashboard or a stats page | Home layout |
| Whether the top bar uses a large title or the traditional style | Layout variant |
| Recommended or custom card at the bottom of the home screen | Home bottom card |
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

Layouts that support this surface can set the working card background separately: the Grid working card can control the image, opacity, darkness, day and night opacity and three hide switches; the four Focus cards share the parent opacity and darkness; Dashboard uses the Hero image. CircleUI has no separate working card image. Some layouts let you long-press a card to edit it directly.

### Video Background

After the video background is enabled, the video replaces the static image:

- Select a video file.
- Adjust the video volume.
- Clear the current video file.

The scrim can still be used in video mode, but "Surface opacity", "Blur" and "Pick color from wallpaper" are hidden because they only apply to static images. Video decoding and continuous playback use more power, so use it with care on low-performance devices.

## Font

The font page has three modes:

- **App default**: uses the font bundled with the Manager.
- **System default**: follows the device system font.
- **Custom**: picks a font file from the device.

A custom font only affects the manager UI; it does not replace the Android system font. If glyphs are missing, the file is damaged or the format is unsupported, restore the default font.

"Restore default font" deletes the imported file and switches back to **App default**; if you want to follow the system font, restore first and then select "System default" manually.

See [Font](/en/setting/font/).

## Launcher Icon

The launcher icon decides which icon style FinaSU uses on the home screen, for example default, geometric or the KernelSU style. It only changes the launcher presentation and does not change the package name, signature, authorization or runtime mode.

## Home Layout

Five home layouts are currently available:

::: table Home layouts

| Layout | Main features | Suits |
| --- | --- | --- |
| **CircleUI** | A circular working card and a status-focused home screen | Users who like to see Root status in one place |
| **GridUI** | Two-column cards | Users who want more entries on the home screen |
| **FocusUI** | Focuses the main status and adapts to screen orientation | Using a phone and a tablet in turn |
| **DashboardUI** | Dashboard-style information cards | Users who like sectioned information panels |
| **StatsUI** | Centers on superusers, modules and live metrics | Users who need statistics at a glance |

:::

Switching the layout only changes the home screen arrangement; it does not change theme colors, Root state or module data.

## Layout Variants

The layout variant applies to the plugins, superuser and modules pages:

- **M3E**: large title, collapsible inline search bar and a more pronounced scroll hierarchy.
- **Traditional**: compact top bar that takes less vertical space.

If page content is covered by the large title, or you want more entries visible on a tablet in landscape, switch to "Traditional".

## Home Bottom Card

A shortcut card can be placed at the very bottom of the home screen. It appears after the home screen content and takes no part in judging Root or module state.

::: table Four display modes

| Option | Card content | Tap behavior |
| --- | --- | --- |
| **Learn about FinaSU** | One line, "Learn about FinaSU / Learn about FinaSU features and modules" | Opens the FinaSU website |
| **Support development and KernelSU** | Two lines: support development, learn about KernelSU | Opens the donation page and the KernelSU guide respectively |
| **Hidden** | No card is shown | None |
| **Custom** | The title, subtitle and link you fill in yourself | Opens the HTTP(S) link you entered |

:::

"Learn about FinaSU" is the default option. Switching to "Hidden", or leaving the custom content empty for now, only affects what the home screen shows; it does not delete custom text you already entered.

### Custom Card

The following three items only appear after you select "Custom":

:::: field-group

::: field Card title
@type `Custom`
@default `Bold title`
Shown on the first line of the card, it is the visual focus.
:::

::: field Card subtitle
@type `Custom`
@default `Descriptive text`
Shown below the title to explain what the card is for.
:::

::: field Link address
@type `HTTP(S)`
@default `Required`
Opens when the whole card is tapped. Only `http://` or `https://` addresses are accepted.
:::

::::

::: warning The card appears only when all three fields are filled in

The title, subtitle and link must all be non-empty. If any one is empty, custom mode does not show a half-finished card; the effect is the same as "Hidden". An invalid link is not saved; the edit box stays open and asks you to enter it again.

:::

Write the content with "who reads this text and what should a tap do" in mind. For example:

```text
Title: Module usage notes
Subtitle: See the installation and troubleshooting steps for the current module
Link: https://example.com/module-guide
```

`example.com`, `ftp://example.com` and empty content do not qualify; the full `http://` or `https://` is required.

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

- Choose the home layout, layout variant and home bottom card
  time=Structure

- Set the font, launcher icon, title image, information visibility, scale and description lines
  time=Details

:::

## Related Pages

- [Theme & Color](/en/setting/theme/)
- [Wallpaper](/en/setting/wallpaper/)
- [Font](/en/setting/font/)
- [Home & Layout](/en/setting/home/)
