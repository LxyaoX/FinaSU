---
title: Hardware Info
description: Sources, sampling interval, placeholders and historical limits of FocusUI device and storage metrics
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-focus/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Hardware Info

<Badge text="5-second sampling" type="tip" /> <Badge text="Current home screen only" type="info" /> <Badge text="zram/swap data source is limited" type="warning" />

Focus and Stats are the only two home layouts that continuously read device metrics. Circle, Grid and Dashboard use only the status snapshot and do not keep polling metrics.

## Sampling Lifecycle

- Collection starts when the home screen is the current page and the layout is Focus/Stats;
- Values are read once every 5 seconds;
- The most recent 30 samples are kept for the waveform;
- Collection stops after leaving the home screen or switching to a layout that does not sample;
- The shared flow stops about 5 seconds after the last consumer disappears.

## Device Card Sources

:::: table Metrics and Their Actual Sources

| Metric | Source | Result on failure |
| --- | --- | --- |
| Battery level | The `ACTION_BATTERY_CHANGED` sticky broadcast | `—` |
| Battery temperature | `EXTRA_TEMPERATURE` of the same broadcast | `—` |
| CPU temperature | The first `cpu*` thermal zone in `/sys/class/thermal`, falling back to SoC/AOSS/TSENS | `—` |

::::

Temperature is used for display only and does not trigger thermal control. The displayed CPU temperature is whichever thermal zone the device exposes and is not guaranteed to equal the highest temperature across all cores or inside the SoC.

## Storage Card Sources

:::: table Storage and Memory

| Metric | Source |
| --- | --- |
| Data used/total | `StatFs` reading the data partition |
| RAM used/total | `MemTotal` and `MemAvailable` from `/proc/meminfo` |
| zram / swap | Supported by the data model, but the current collector does not fill them |

::::

Code comments state explicitly that Android apps usually cannot read `/proc/swaps`, so the current `SystemMetricsCollector` does not set `zramTotalBytes` and `swapTotalBytes`. Even though the interface has the corresponding rows, this should not be promised to appear on every device.

## Unknown Values

Unknown metrics are uniformly shown as `—`. This is safer than showing 0:

- 0°C looks like a valid reading;
- 0% looks like a real battery level;
- A total of 0 B could be mistaken for the device having no storage.

## Metrics Focus Does Not Show

The Focus device card has no CPU frequency clusters, RAM waveform or battery threshold color bar. CPU clusters and short-term waveforms belong to the StatsUI monitoring card.

## Related Pages

- [StatsUI Hardware Monitoring](/en/theme/theme-statsui/hardware/)
- [Focus Tablet Layout](/en/theme/theme-focus/tablet/)
- [Basic Styling](/en/theme/theme-focus/basic/)
