---
title: Interaction
description: StatsUI top switching, Grid working card long press, monitoring updates and navigation
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-statsui/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Animation & Interaction

<Badge text="The top form can be switched" type="tip" /> <Badge text="Grid supports long-press card editing" type="info" /> <Badge text="Trends are not animation scripts" type="warning" />

Stats interaction is decided by the top form, the wide-screen grouping and live sampling together.

## Top List

- Tapping the status card enters install/update;
- The install path is disabled in lazy-loading mode;
- The run mode tags are controlled by "Hide status tags";
- There is no long press on the card surface.

## Top Grid

- Reuses the Grid working card and the counts;
- Tapping the working card enters install/update;
- Long-pressing the working card opens the Grid surface;
- Tapping a count opens the Superuser or Modules page;
- When counts are unavailable the placeholder is kept.

## Monitoring and Statistics

The waveform and progress bars receive new samples every 5 seconds and update the curve and values. When no data is available a placeholder is shown and no "fake animation" is played.

The ring chart and the legend have no tap actions. Changing theme colors changes the chart colors but not the counts.

## Wide-Screen Switching

When the width reaches 600dp the layout switches to two columns. This is a layout reflow: the monitoring card or statistics card is not duplicated, and there is no separate manual "desktop mode" switch.

## Animation Boundaries

Page and control feedback is provided by Material 3 Expressive and the system. A theme cannot add standalone Stats animations, scripted dashboards or chart transition parameters.

## Related Pages

- [StatsUI Basic Styling](/en/theme/theme-statsui/basic/)
- [Home & Layout](/en/setting/home/)
- [Navigation](/en/setting/navigation/)
