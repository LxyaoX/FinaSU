---
title: Tablet Layout
description: The exact FocusUI 2×2 breakpoint, window size and automatic navigation mode
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-focus/tablet/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Tablet Layout Guide

<Badge text="600dp width" type="tip" /> <Badge text="Must be wider than tall" type="info" /> <Badge text="Page scaling does not change the navigation breakpoint" type="warning" />

FocusUI does not decide on a tablet by device type; it decides between a single column and 2×2 based on the current window size.

## Exact Conditions

```text
Full-featured environment
&& screenWidthDp >= 600
&& screenWidthDp > screenHeightDp
```

When all of them hold:

```text
Status      Manager
Device      Storage
```

Otherwise the cards are arranged vertically as status, Manager, device, storage.

:::: table Common Results

| Window | Result |
| --- | --- |
| Phone width, portrait | Single column |
| Phone landscape with a width below 600dp | Single column |
| Tablet portrait, 600dp wide but taller | Single column |
| Tablet landscape, 600dp wide and wider than tall | 2×2 |

::::

## Page Scaling

The Focus breakpoint reads `screenWidthDp` from the Android configuration. Page scaling changes the visual density of app content, but it cannot be treated as a switch that "turns any phone into a tablet layout".

The navigation "Automatic" mode separately uses the unscaled window container size:

```text
widthDp >= 600 && widthDp > heightDp
```

So the Focus reflow and the side navigation usually change in the same direction, but they are two different implementations and should not be written up as one general "tablet mode" in the documentation.

## Navigation Selection

- Automatic: use the side rail when the width and height conditions above hold, otherwise the bottom bar or the app default form;
- Always side: actively select the rail;
- Always bottom: actively select the docked bar;
- Floating: use the floating bar.

## Card Layout and Cropping

In 2×2, cards in the same row are aligned by intrinsic height. The image surface is cropped to each card's actually measured bounds; very long text, fonts and page scaling may change card height and the effective composition of the image.

When releasing a tablet theme, test at least:

- The critical 600×? width;
- Switching between landscape and portrait;
- Three-button and gesture navigation;
- The side rail and the floating bar;
- Large fonts and page scaling.

## Related Pages

- [Navigation](/en/setting/navigation/)
- [Focus Basic Styling](/en/theme/theme-focus/basic/)
- [Focus Hardware Info](/en/theme/theme-focus/hardware/)
