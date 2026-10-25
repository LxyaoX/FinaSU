---
title: Basic Styling
description: DashboardUI Hero layout, content, image surface and responsive boundaries
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-dashboard/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Basic Styling

<Badge text="Hero first" type="tip" /> <Badge text="Vertical card flow" type="info" /> <Badge text="The Hero has its own surface" type="warning" />

The visual focus of Dashboard is a Hero occupying roughly the full width, using larger inner padding and three layers of information: the state, the state extras and three facts.

## Hero Content

:::: table Hero Areas

| Area | Content |
| --- | --- |
| Top left | The status icon and Running/Not installed/Not supported |
| Below the title | The version summary, a prompt to tap and install, or the reason for the lack of support |
| Status row | Run mode, safe mode, jailbreak mode, custom LKM |
| Bottom three items | Module version, kernel, SELinux |

::::

The three facts use equal-width columns. Overlong values are ellipsized and do not push the whole card off the screen.

## Hero Background

Theme path:

```text
dashboard_card_bg.jpg
dashboard_card_bg.png
dashboard_card_bg.gif
dashboard_card_bg.webp
dashboard_card_bg.jpeg
```

Surface:

```text
layout.dashboard.hero
```

Supported:

- The image and the enable switch;
- Single or day/night opacity;
- Single or day/night dim.

Before the image finishes loading, the Hero uses an opaque semantic surface; after loading it switches to a transparent card and gives the text a contrast color suited to the image, so white text does not land on a blank background first.

## Count Cards

In a full-featured environment, the superuser and module counts are arranged horizontally. When counts are unavailable they can still keep a placeholder, and whether they are drawn at all is decided by the full-featured environment.

## System Info

Dashboard contains the system info card itself, and does not draw another global InfoCard outside the page. The content matches the other layouts and is controlled by the same hiding items.

## Responsive Behavior

The current code has no Dashboard-specific wide-screen branch. Do not infer from the Focus 2×2 conditions that Dashboard also splits into columns.

## Related Pages

- [Status Info](/en/theme/theme-dashboard/hardware/)
- [Global Background](/en/theme/global/transparency/)
- [Home & Layout](/en/setting/home/)
