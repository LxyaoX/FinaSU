---
title: Font
description: Font modes, import and restore in the FinaSU Manager
createTime: 2026/10/24 00:00:00
permalink: /en/setting/font/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Font

<Badge text="Changes the Manager only" type="tip" /> <Badge text="Recoverable on failure" type="warning" />

The font setting only changes the text in the FinaSU Manager interface; it does not replace the Android system font, and it does not change modules, Root authorization or other apps.

## Three Font Modes

::: table Font modes

| Mode | Which font it uses | Suits |
| --- | --- | --- |
| **App default** | Uses the font bundled with FinaSU | Users who want the Manager's own unified look |
| **System default** | Follows the device's current system font | Users who want consistency with other apps |
| **Custom** | Uses a font file imported by the user | Users who know exactly which font they want |

:::

After you switch modes, the Manager interface applies the font again.

## Using a Custom Font

:::: steps

1. Go to "Settings → Appearance → Font".
2. Select "Custom".
3. Tap "Select font file".
4. Pick a font file in the system file picker.
5. Return to the home screen and other pages and check that the text displays completely.

::::

After a successful import, the font file is copied to the Manager's private directory; deleting the original file afterwards does not affect the imported font.

::: warning Font format and character coverage

A font that is damaged, in an unsupported format or missing common characters causes missing glyphs, tofu boxes or an outright failure to apply. If that happens, tap "Restore default font" instead of stacking further font files.

:::

## Restore the Default Font

In custom mode, "Restore default font" appears once a font file has been selected:

- Tapping it shows a confirmation dialog first.
- Confirming deletes the custom font file stored by the Manager.
- The restore target is **App default**, not "System default".
- If you only want to follow the system font, restore first and then select "System default" manually.

## Relation to Theme Packages

The font is part of the Manager appearance, but it never replaces the system font automatically. When a theme package should contain font resources, use the complete `.fpt` export flow.

## FAQ

:::: collapse accordion

- **Why does the custom font look like the system font?**

  When no usable custom font file is available, the Manager falls back to the available default font. Select the font file again.

- **Why do some characters turn into boxes?**

  The font file lacks the matching glyphs. Switch to a font with complete glyph coverage.

- **Does restoring the default delete system fonts?**

  No. It only deletes the custom font in the Manager's private directory and switches back to the app default.

::::

## Related Pages

- [Appearance](/en/setting/appearance/)
- [Theme & Color](/en/setting/theme/)
- [Theme Store](/en/setting/theme-store/)
