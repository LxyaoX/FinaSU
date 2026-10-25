---
title: Share Theme
icon: material-symbols:share-outline
description: Distributing FinaSU .fpt themes, receiver import and cross-FolkPatch compatibility notes
createTime: 2026/10/24 00:00:00
permalink: /en/theme/export/share/
category: Themes
tag:
  - FPT
  - Import
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Share Theme

<Badge text="A single .fpt file" type="tip" /> <Badge text="Verify before going public" type="warning" /> <Badge text="No source signature" type="danger" />

`.fpt` wraps `theme.json` and the resources in one file, which suits distribution through cloud drives, instant messaging, the project community or a compatible theme index. The receiver still has to complete the import confirmation in FinaSU.

## Before Sharing

Run at least one export, import on another clean environment, export again regression pass:

- confirm the theme name, author, version and device type are correct;
- confirm none of the five layouts reports an error or looks misaligned;
- confirm text is still readable in light mode, dark mode and AMOLED;
- confirm both phone and tablet sizes were checked, or state clearly that only one is supported;
- confirm video, GIFs, fonts and audio decode on the target device;
- confirm the theme does not depend on other private fields, external files or local paths.

If the theme went through FolkPatch or another compatible implementation, also check whether extension fields, image-presence flags and disabled states are still kept. A compatible container is not a lossless round trip.

## Suggested Accompanying Notes

:::: table Distribution information

| Information | Example |
| --- | --- |
| Theme name and version | `Aurora 1.2.0` |
| Author | Name or team |
| Target device | Phone, tablet or both |
| Recommended layout | Circle, Grid, Focus, Dashboard, Stats |
| FinaSU version | The Manager version that was tested |
| Known limits | "Focus only", "large video", "font without complete CJK" |
| File checksum | Optional SHA-256 |

::::

A checksum only helps confirm that the downloaded file matches the value the publisher gave. It cannot prove the publisher's identity and does not make up for the lack of a signature in `.fpt`.

## How the Receiver Imports

:::: steps

1. Save the complete `.fpt` file; do not preview it online or let a chat app rename it automatically.
2. Open the file with FinaSU from the file manager, or pick it from Settings → Appearance → Wallpaper → Import theme package.
3. Check the preview, name, author, version, device type and resource count.
4. Confirm and apply after passing theme import authentication.
5. Check the background, colors, font, layout, navigation icons, music and sound effects.
6. If the result is not as expected, import a known-good theme again or use Restore default theme.

::::

## Distribution Across FolkPatch

FinaSU and FolkPatch share many historical keys and surface names, but the two are not the same complete implementation:

- `default` and `sign` map to CircleUI in FinaSU;
- unknown fields and unused resources can be kept, but other implementations are not guaranteed to understand FinaSU extensions;
- the same field may have a different default value or switch meaning on either side;
- after a third-party editor saves the theme again, unknown fields may be dropped or rewritten;
- some images may be kept as resources even when display is disabled, and whether they survive the crossing depends on the other implementation.

So when publishing, write "compatible" explicitly rather than promising that "FolkPatch and FinaSU are fully lossless interoperable".

:::: warning Receiver device differences

Player decoding capability, font glyphs, screen ratio and performance can all make the same theme look different. A theme author cannot use JSON to force a device to have a missing decoder, font or hardware.

::::

## Security Notes

::::: collapse accordion

:::: collapse Do not run a "theme install script"
A FinaSU theme is a flat resource package, not a module. Any theme that requires flashing a module, granting root or running a shell before it can be installed is already outside this theme format.
::::

:::: collapse Do not ignore abnormal size
Videos of hundreds of MB, unusually large fonts or many duplicated images consume storage, increase import time and may fail on low-memory devices.
::::

:::: collapse Do not leak private resources in public channels
Fonts, music, images and video may be restricted by copyright or portrait rights. The person sharing should confirm the resource licenses.
::::

:::: collapse How to preserve evidence when an import fails
Keep the original `.fpt`, the failure message, the Manager version and the theme metadata. Do not unpack the file and recompress it into an arbitrary ZIP before reporting the problem.
::::

:::::

## Related Pages

- [Export Theme](/en/theme/export/info/): export scope and file location.
- [Theme Preview & Usage](/en/theme/theme-use/): the complete import, store and restore flow.
- [Theme Package Structure](/en/developer/theme-package/): cross-platform fields and debugging.
