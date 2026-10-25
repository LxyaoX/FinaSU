---
title: Updates & Version
createTime: 2026/10/24 00:00:00
permalink: /en/setting/update/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Updates & Version

## Check for Updates

"Check for updates" controls whether the Manager looks for a new version after startup. When an update is found, still verify the signature and the source before installing the official build.

## Check for Module Updates

"Check for module updates" makes the Manager check enabled modules for new versions using module repository metadata. There may be no update notice when a module is disabled, the repository version number is incomplete, or the author did not provide a reliable version.

## These Updates Are Not the Same Thing

- A Manager APK update only replaces the app interface and the bundled resources.
- A kernel driver update requires patching again or flashing the boot partition.
- `ksud`, the built-in engines and the APK are normally released together in a complete official build.

Do not assume the kernel driver was updated as well when only the Manager version changed. For the complete release flow, see [Updating FinaSU](/en/guide/update/).
