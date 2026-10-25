---
title: Statistics
description: Data sources and interaction limits of the StatsUI module, superuser and plugin ring charts
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-statsui/statistics/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Statistics

<Badge text="Three categories" type="tip" /> <Badge text="The total is shown in the center" type="info" /> <Badge text="The chart is not tappable" type="warning" />

The Stats ring chart consists of three categories of current state.

:::: table Ring Statistics

| Category | Meaning | Color role |
| --- | --- | --- |
| Modules | The number of currently enabled modules | primary |
| Superuser | The current number of grants | tertiary |
| Plugins | The number of currently enabled plugins | secondary |

::::

The center of the ring shows the sum of the three counts. The legend on the right always lists the three names and counts, even when one category is 0.

## Data Sources

The counts come from the current list state of the Manager, not from the theme package, and they are not guessed from root authorization files. After a module is disabled, a plugin is turned off or a user is removed, the next state update reflects the change.

The Ring does not currently represent:

- KPM/APM;
- The number of kernel modules;
- Storage usage;
- CPU temperature;
- Download tasks.

## Interaction

The ring chart and the legend are information displays; they have no tap navigation, filtering or slice hiding. To manage the corresponding objects, use the navigation to reach the Superuser, Modules or Plugins pages.

## Visual Boundaries

The three colors come from the current Material color scheme; no separate Stats chart theme fields are provided. Changing the accent color, the color style, the light/dark mode or wallpaper color extraction affects the chart colors.

## Related Pages

- [Superuser](/en/manager/superuser/)
- [Modules](/en/manager/module/)
- [Plugins](/en/manager/plugin/)
- [StatsUI Hardware Monitoring](/en/theme/theme-statsui/hardware/)
