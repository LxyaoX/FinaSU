---
title: Custom Fonts
icon: material-symbols:font-download-outline
description: FinaSU font modes, file formats, font theme resources and missing glyph fallback
createTime: 2026/10/24 00:00:00
permalink: /en/theme/global/font/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Custom Fonts

<Badge text="Manager only" type="tip" /> <Badge text="TTF / OTF" type="info" /> <Badge text="Missing glyphs fall back" type="warning" />

FinaSU supports three modes: app default, system default and custom font. The font only affects the Compose interface of the Manager; it does not replace the Android system font and does not affect other apps or the output of root commands.

## Three Modes

:::: table Font modes

| Interface name | `fontMode` | File requirement |
| --- | --- | --- |
| App default | `app` | No font file required |
| System default | `system` | Uses the current Android system font |
| Custom | `custom` | Import a TTF or OTF through the system file picker |

::::

Picking a file switches to custom mode automatically. Switching back to app default or system default does not delete the saved custom file; clearing the custom font deletes the copy saved by the Manager.

## File Formats and Storage

The theme resource policy prefers:

```text
font.ttf
font.otf
```

The file the user picks is copied into the Manager private directory, so moving, renaming or deleting the original does not affect the applied font. Theme export uses the protocol name `font.ttf` to carry the font bytes; even when the source file is an OTF, the exported ZIP entry may still be `font.ttf`, and the renderer works from the file content and Android font support.

:::: warning A file extension is not proof of font quality

Renaming any file to `.ttf` does not make it a font. If text cannot be read or applying fails after import, use a real font file that Android can parse.

::::

## Character Coverage

When a font only contains the characters the interface actually uses, the missing glyphs are displayed through Android font fallback. The usual result is inconsistent stroke weight between Chinese and Latin text, digits and punctuation.

At minimum check the following before publishing a theme font:

- the complete Simplified Chinese interface;
- Latin letters, digits, percent signs and units;
- common punctuation, arrows and degree symbols;
- the text in a custom theme title;
- the extended glyphs required by the target languages.

If the font has no complete CJK coverage, FinaSU does not generate the missing glyphs and does not merge another font into it.

## Theme Fields

```json
{
  "fontMode": "custom",
  "isFontEnabled": true
}
```

An older theme may only have `isFontEnabled`. On import:

- when `fontMode` is available, it takes precedence;
- when `fontMode` is absent but `isFontEnabled=true`, it is interpreted as custom;
- when `fontMode` is absent and `isFontEnabled=false`, it falls back to the app default;
- when `fontMode=custom` but the file is missing, a warning is produced and the app font is used instead.

## Restoring

Restore default font only handles the font mode and the font file; it does not clear wallpaper, colors or layout. The full Restore default theme clears the font together with the other theme resources.

## Common Questions

::::: collapse accordion

:::: collapse The font is visible on the settings page but other pages do not change
Confirm that custom mode is selected, then restart the Manager page or recreate the interface the way the system provides. The font only applies to the Manager.
::::

:::: collapse Chinese text becomes boxes or mixes in another font
The font lacks the corresponding glyphs and the system falls back to another font. Use a font with a complete character set; do not force an override by editing theme fields.
::::

:::: collapse The original file in the file manager disappears after importing a font
FinaSU copies an independent private copy, so deleting the original should not affect the saved font. If the original was deleted by another tool, restore it or pick the font again.
::::

:::: collapse The theme font did not load
Check `fontMode`, `isFontEnabled`, the `font.ttf` / `font.otf` entry and whether the font can be parsed. A missing font falls back and does not necessarily fail the whole theme.
::::

:::::

## Related Pages

- [Font Settings](/en/setting/font/): the entry point for each operation.
- [Theme Package Structure](/en/developer/theme-package/): font resources and field validation.
- [Dark Mode, Colors & Titles](/en/theme/global/theme-style/): text-related styling.
