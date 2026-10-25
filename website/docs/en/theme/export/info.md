---
title: Export Theme
icon: material-symbols:save-as-outline
description: Export entry points, metadata, resource scope, save location and limits for FinaSU .fpt
createTime: 2026/10/24 00:00:00
permalink: /en/theme/export/info/
category: Themes
tag:
  - Export
  - FPT
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Export Theme

<Badge text="Snapshot of the current state" type="tip" /> <Badge text="Written to Downloads" type="info" /> <Badge text="No authorization or modules" type="danger" />

An export combines the themeable resources and settings currently in the Manager into a single `.fpt` file. It is not a full device backup and not a module installer.

## Entry Point

Open Settings → Appearance → Wallpaper and choose Export theme package in the Theme package group. The Manager opens the metadata editor first.

:::: table Export metadata

| Field | Required | Purpose |
| --- | --- | --- |
| Name | Yes | Import preview, file name and theme identification |
| Device type | Yes | `phone` or `tablet`, used for classification and preview |
| Version | No | Content version string, default `1` |
| Author | No | Displayed credit |
| Description | No | Describes the theme content and limits |

::::

The name is only checked for being non-empty. Characters unsuitable for a file name are replaced when generating the file name; the original name is still stored inside the theme.

## What Is Actually Exported

::::: table Content exported with the theme

| Category | Resource or field | Export condition |
| --- | --- | --- |
| Main wallpaper | `background.<image extension>`, opacity, blur, dim, per-page switches | When the currently saved wallpaper resource exists |
| Per-page wallpaper | `background_home.*`, `background_kernel.*`, `background_superuser.*`, `background_system_module.*`, `background_settings.*` | When multi-background is enabled and the corresponding page has a resource |
| Video background | `video_background.*`, video switch and volume | When the video file exists |
| Advanced title | `title_image.*`, visibility, opacity, dim, offset, text mode | When the title resource exists |
| Grid working card | `grid_working_card_background.*`, surface fields and visibility flags | When the surface file or configuration exists, even if the current home screen is not Grid or Stats |
| Focus cards | The four `focus_card_*_bg.*` files and the shared parent fields | When the surface file or configuration exists |
| Dashboard | `dashboard_card_bg.*` and the hero surface fields | When the surface file or configuration exists |
| Font | `font.*` and the font mode | When a custom font is enabled and the file exists |
| Navigation icons | `nav_icon_<page>.png` and `navIcons` | When custom navigation icons are enabled and the files exist |
| Music | The actual file name of the current music and the playback fields | When music is enabled and the file exists |
| Sound effects | The actual local sound effect file name or `sound_effect_preset_<Preset>.wav` | When touch sound effects are enabled and the resource exists |
| Settings | Colors, layout, Stats top area, status tag, language and more | Current FinaSU theme fields |

:::::

An export also merges the unknown fields kept in `theme_passthrough/theme.json` and the retained resources that do not belong to the current Manager resource mapping. This supports re-import and other compatible implementations as far as possible. Surfaces are exported by walking the registry, so previously saved Grid, Focus or Dashboard resources are not dropped just because the current home screen is CircleUI.

## What Is Not Exported

- root authorization, allowlist and App Profile;
- installed modules, module enable state and the module list;
- kernel, SUSFS, Folk Mount, NoMount or SuperCall configuration;
- app lock, Manager authentication, WebDAV password and backup keys;
- the boot sound;
- vibration switch, strength and feedback scope;
- server-side or non-theme resources such as module banners and module cards;
- any executable scripts or directory structure.

:::: warning Boot sound and vibration are not theme resources

The theme import notes state explicitly that they remain device settings. Do not extend the fields by hand to carry a boot sound or vibration in a theme; the current export flow does not generate such resources.

::::

## Save Location

The Manager writes to the public downloads collection through `MediaStore.Downloads`:

```text
Download/FinaSU/Themes/<theme name>.fpt
```

The default base name is `wallpaper.fpt`, but the normal export dialog generates the file name from the theme name. `application/octet-stream` is chosen to stop some download providers from renaming the unknown `.fpt` extension to `.bin`.

If the system has no writable downloads collection, storage is insufficient, or the write is interrupted, the export fails and deletes the pending file it already created.

## Packaging Process

::::: steps

1. Read the current theme resources and retained fields.
2. Generate the supported fields for the current version and write the metadata.
3. Select the current file for each resource and check for entry name conflicts.
4. Validate the JSON that is about to be written one more time.
5. Generate a random 16-byte IV and wrap the flat ZIP with AES-CBC/PKCS5.
6. Write through a temporary `IS_PENDING` download entry and publish it as a visible file when complete.

:::::

:::: table Hard export limits

| Limit | Value |
| --- | --- |
| Entry count | Resource entries must be fewer than 128, with JSON counted separately |
| `theme.json` | No more than 1 MiB |
| Total budget after decompression | No more than 1 GiB |
| Encrypted container budget | Decryption limit 1 GiB + 32 MiB of ZIP overhead |
| Empty resource | Not allowed |

::::

These are container boundaries, not performance recommendations. With long video, several high-resolution wallpapers and a complete font, a package may be unsuitable for distribution to low-storage devices even when it stays under the limits.

## Troubleshooting a Failed Export

::::: collapse accordion

:::: collapse The prompt says the name is empty
The name is the only required metadata. Enter a non-empty name and export again.
::::

:::: collapse The file does not appear in the expected directory
On some systems the downloads directory needs the media library to refresh first. Confirm that `FinaSU/Themes` exists in the system Downloads, then check storage permission and free space.
::::

:::: collapse The file extension becomes `.bin`
This should only happen in other SAF write flows. The current FinaSU export to Downloads explicitly keeps the `.fpt` name; if it is still rewritten, save the file, restore the extension manually and verify again.
::::

:::: collapse The package is unusually large after export
Check whether video, GIFs, high-resolution wallpapers, several per-page backgrounds or a large font are enabled. The theme package writes the current actual files as they are.
::::

:::: collapse A setting is missing after import
First confirm whether that setting belongs to the theme protocol. The boot sound, vibration, root permissions and module state never enter a `.fpt`.
::::

:::::

## Related Pages

- [Share Theme](/en/theme/export/share/): distribution and receiving after export.
- [Become a Theme Developer](/en/theme/theme-dev/): modifying fields and resources.
- [Theme Package Structure](/en/developer/theme-package/): container security, fields and debugging.
- [Wallpaper](/en/setting/wallpaper/): entry points for theme packages and wallpaper settings.
