---
title: Interaction
description: DashboardUI Hero actions, long-press surface, count navigation and system animation
createTime: 2026/10/24 00:00:00
permalink: /en/theme/theme-dashboard/interaction/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Animation & Interaction

<Badge text="Tap to install" type="tip" /> <Badge text="Long press to adjust the Hero image" type="info" /> <Badge text="No separate animation switch" type="warning" />

The Dashboard Hero is the main interaction area of the home screen, the count cards handle navigation, and the SUSFS row in the system info provides a capability entry.

## Hero

- Tap: enters the install/update flow; disabled in lazy-loading mode;
- Long press: opens the Hero surface options;
- Jailbreak button: appears only when not installed and SELinux is Permissive;
- Image loading: the opaque surface is kept first and switches after decoding succeeds.

## Counts

The superuser and module cards each open the corresponding main page. When unavailable they only hold a placeholder and do not navigate.

## System Info

The SUSFS row opens the management page when detected. Other rows only show facts; tapping the background does not grant root or change modules.

## Animation Boundaries

Dashboard uses Material 3 Expressive press, shape and page transitions, plus system predictive back. FinaSU has no Dashboard-specific animation engine, duration or easing fields.

## Related Pages

- [Dashboard Basic Styling](/en/theme/theme-dashboard/basic/)
- [Navigation](/en/setting/navigation/)
- [Interaction & Compatibility](/en/setting/interaction/)
