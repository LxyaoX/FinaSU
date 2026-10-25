---
title: Hardware Monitoring
description: StatsUI 5-second sampling, 30-point waveforms, CPU clusters, battery and storage metrics
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-statsui/hardware/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Hardware Monitoring

<Badge text="5-second sampling" type="tip" /> <Badge text="30-point history" type="info" /> <Badge text="zram/swap are often unavailable" type="warning" />

The Stats monitoring card puts device facts and short-term trends in one card. It does not rely on a root shell and reads only Android public APIs and readable sysfs nodes.

## CPU Temperature Waveform

- The current value comes from the preferred CPU/SoC thermal zone;
- The unit is °C;
- The most recent 30 valid samples are drawn as a waveform;
- When it cannot be read, `—` is shown and no 0 is filled in.

## RAM Waveform

- Current usage comes from `/proc/meminfo`;
- A percentage is shown;
- 30 samples are kept;
- When the total is 0 it is shown as unavailable, and no fake curve is drawn.

## CPU Clusters

The `cpufreq` of each core is collected:

- `scaling_cur_freq` is preferred;
- Falling back to `cpuinfo_cur_freq`;
- For the upper limit, `cpuinfo_max_freq` is preferred, then `scaling_max_freq`;
- Cores are grouped by maximum frequency;
- Each group shows the core range, the average current frequency and the average/maximum ratio.

This is "the current frequency as a share of the maximum", not a CPU load percentage. The code does not read `/proc/stat`, because ordinary Android apps usually lack permission to do so.

## Battery

The level comes from the sticky battery broadcast:

- 0–20%: error color;
- 21–50%: tertiary color;
- Above 51%: primary color;
- Unreadable: placeholder using the primary color.

These colors use Material semantic roles and change with the theme.

## zram and swap

The interface shows the corresponding progress bars when `zramTotalBytes > 0` or `swapTotalBytes > 0`. However, the current `SystemMetricsCollector` does not fill these two fields, and a comment states that the platform usually does not expose `/proc/swaps` to apps.

Therefore:

- The data model supports these two rows;
- They are very likely not visible on current ordinary devices;
- Not seeing them does not mean theme resources are missing;
- These two rows should not be written up as a stable selling point of the current version.

## Sampling Boundaries

- Once every 5 seconds;
- Runs only while the Stats/Focus home screen is the current page;
- Ends about 5 seconds after the last subscriber stops;
- Switching away and back does not continuously show the "history" from the period away;
- A placeholder is used when there is no data.

## Related Pages

- [Focus Hardware Info](/en/theme/theme-focus/hardware/)
- [System Metrics Troubleshooting](/en/troubleshoot/runtime/)
- [Global Themes](/en/theme/global/theme-style/)
