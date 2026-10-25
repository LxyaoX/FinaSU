---
title: Diagnostics & Feedback
createTime: 2026/10/24 00:00:00
permalink: /en/setting/diagnostics/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Diagnostics & Feedback

## SU Log

Turns on the KernelSU-family SU log, which records Root execution, legacy SU, authorization and log daemon events. When the kernel does not support it or policy forces it on, the setting is unavailable or cannot be changed. See [SU Log](/en/manager/su-log/).

## Send Logs

"Send logs" on the Settings home screen collects Manager logs for problem reports. The logs may contain package names, UIDs, paths, kernel version and device state; check for sensitive information before submitting.

## Issue Feedback

The feedback entry opens the project's issue page. Before submitting, confirm:

- Manager version, kernel version and runtime mode.
- Device model, Android version and full KMI.
- Installation method and the modules, themes or engines changed most recently.
- Repeatable steps, expected result and actual result.
- Related logs, and any rescue operations done before and after the failure.

Do not paste the Manager password, WebDAV credentials or private keys into a public issue.
