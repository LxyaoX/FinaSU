---
title: Language
description: Languages supported by the FinaSU Manager and how to switch instantly
createTime: 2026/10/24 00:00:00
permalink: /en/setting/language/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Language

<Badge text="20 options" type="tip" /> <Badge text="Instant switching" type="warning" />

"App language" selects the language used by the FinaSU Manager interface. Switching is a hot update: after you choose a language, the current interface is rebuilt and the language resources load immediately. You do not need to exit the Manager, let alone reboot the device.

::: info Changes the Manager interface only

The language setting does not change the Android system language, the module script environment, kernel logs or the language of third-party apps. `ksud` commands and module output are not translated when you switch the interface language either.

:::

## All Languages

::: table Language list

| Display name | Language tag |
| --- | --- |
| Follow system | `default` |
| English | `en` |
| Polski | `pl` |
| Bahasa Indonesia | `in` |
| Русский | `ru` |
| Tiếng Việt | `vi` |
| Português (Brasil) | `pt-BR` |
| Español | `es` |
| العربية | `ar` |
| Türkçe | `tr-TR` |
| 日本語 | `ja` |
| 한국어 | `ko` |
| Chinese (Simplified) | `zh-CN` |
| Chinese (Traditional) | `zh-TW` |
| Catgirl | `zh-AG` |
| Heavenly Script | `zh-WC` |
| Magical Girl | `mgl` |
| Angelic Language | `zh-AT` |
| Inn Speech | `zh-CK` |
| Block World | `zh-MC` |

:::

## How to Switch

:::: steps

1. Go to "Settings → General → Language".
2. Tap the language you need.
3. The current interface is rebuilt and shows the new language immediately.
4. To return to the system choice, tap "Follow system".

::::

A brief interface rebuild may occur during the switch; this is normal. Do not tap several languages in succession during the rebuild.

## Custom Language Variants

The second half of the list contains special Chinese variants provided by the project:

- **Catgirl**: `zh-AG`
- **Heavenly Script**: `zh-WC`
- **Magical Girl**: `mgl`
- **Angelic Language**: `zh-AT`
- **Inn Speech**: `zh-CK`
- **Block World**: `zh-MC`

They use the same instant switching mechanism as the other languages and only change the Manager interface text.

## Notes

- "Follow system" is not a fixed language; it cancels the Manager's language override.
- Chinese is split into Simplified, Traditional and several style variants, which are independent options.
- Theme packages can store the app language field; after importing a theme, the language choice may change together with the theme.
- This documentation site is still written in Simplified Chinese, and the document language does not switch with the language selected in the Manager.
