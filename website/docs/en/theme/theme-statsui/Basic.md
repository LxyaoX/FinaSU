---
title: Basic Styling
description: StatsUI top list/grid switching, wide-screen grouping and surface capabilities
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-statsui/basic/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Basic Styling

<Badge text="Two top forms" type="tip" /> <Badge text="Two columns on wide screens" type="info" /> <Badge text="The monitoring card has no separate image" type="warning" />

The visual structure of StatsUI is decided by the top form, but monitoring, ring statistics and system info are always present.

## Top List

When "List style" is selected:

- The standard status card is used;
- Driver state, version-UAPI, LKM/Built-In are shown;
- Safe mode, jailbreak mode and custom LKM can be shown in addition;
- "Hide status tags" only affects the LKM/Built-In tags;
- The Grid working card is not used.

## Top Grid

When "Grid style" is selected, Stats directly reuses the Grid home screen content:

```text
Left: Grid working card
Right: superuser count + module count
```

The following settings therefore appear on the wallpaper page:

- `layout.grid.workCard`;
- `grid_working_card_background.*`;
- The Grid working card transparency, dim and hiding switches.

The top grid does not draw an extra standard status card, to avoid duplicating the state.

## Wide-Screen Grouping

When the window width reaches 600dp:

```text
Left column: top status/Grid
             monitoring card
Right column: ring statistics
              system info
```

On narrow screens the order is top, monitoring, ring statistics, system info, arranged vertically.

## Background

Stats uses:

- The global or per-page page wallpaper;
- The Grid working card image when the top is grid.

The monitoring card, the ring statistics card and the system info card have no separate image surface. Global wallpaper transparency and dim still apply to the page material.

## Related Pages

- [Hardware Monitoring](/en/theme/theme-statsui/hardware/)
- [Grid Working Card](/en/theme/theme-grid/working-card/)
- [Home & Layout](/en/setting/home/)
