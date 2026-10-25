---
title: Statistics Entry
description: DashboardUI superuser and module counts, navigation and the limits of its statistics
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-dashboard/statistics/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Statistics Entry

<Badge text="Two counts" type="tip" /> <Badge text="Tap to navigate" type="info" /> <Badge text="No ring statistics" type="warning" />

Dashboard's "statistics" are only two tappable count cards and do not draw charts.

:::: table The Two Entries

| Card | Number source | Tap |
| --- | --- | --- |
| Superuser | The number of currently authorized users | Superuser page |
| Modules | The number of currently enabled modules | Modules page |

::::

The count area is drawn only in a full-featured environment; when the environment is incomplete these entries are not shown. In a full-featured environment the numbers come from the current Manager state, and 0 also means an actual count result.

## What Is Not Included

Dashboard does not have:

- A ring chart of enabled plugin counts;
- CPU/RAM waveforms;
- CPU clusters;
- Battery threshold charts;
- Separate KPM/APM statistics;
- Tap-through chart details.

When you need the three-category ring chart, use [StatsUI Statistics](/en/theme/theme-statsui/statistics/).

## Reading the Counts

The module count means enabled modules, not the total number of entries in the module directory. The superuser count comes from the current authorization state and is not stored in the theme package.

## Related Pages

- [StatsUI Statistics](/en/theme/theme-statsui/statistics/)
- [Superuser](/en/manager/superuser/)
- [Modules](/en/manager/module/)
