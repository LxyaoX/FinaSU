---
title: Settings Overview
description: Find one of the eight FinaSU Manager settings entries quickly by goal
createTime: 2026/10/24 00:00:00
permalink: /en/setting/overview/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Settings Overview

<Badge text="8 entries" type="tip" /> <Badge text="Start from a goal" type="warning" />

The settings home screen is not a long list of switches. The device and profile header sits at the top, eight entries fill two rows of four in the middle, and only below that come feedback, logs, about, and the conditionally shown uninstall entry.

::: tip Fastest way to find an option

First ask yourself "what do I want to do", then look it up in the table below. If you only know the name of a switch, you can also tap the search button at the top of the Settings page and search by name or description.

:::

## I Want To

::: table Enter by goal

| I want to | Go to | Most common location |
| --- | --- | --- |
| Check Manager updates and module updates, change the language or edit App Profile templates | [General](/en/setting/general/) | Settings → General |
| Change colors, wallpaper, icons or the home layout | [Appearance](/en/setting/appearance/) | Settings → Appearance |
| Change the navigation bar, gestures, module cards, SU compatibility or WebView debugging | [Behavior](/en/setting/behavior/) | Settings → Behavior |
| Turn on Zygisk, Sui, Folk Mount, NoMount or network isolation | [Features](/en/setting/function/) | Settings → Features |
| Change SELinux, ADB Root or Shell Root restrictions | [Security](/en/setting/security/) | Settings → Security |
| Add a password, pattern or fingerprint to the Manager or to sensitive actions | [Verification](/en/setting/authentication/) | Settings → Verification |
| Change vibration, background music, touch sound effects or startup sound effects | [Multimedia](/en/setting/multimedia/) | Settings → Multimedia |
| Back up modules, the authorization list or the boot partition, or connect WebDAV | [Backup](/en/setting/backup/) | Settings → Backup |

:::

## The Eight Entries

::: table Settings categories

| Entry | What it does in one line | If you do not know where to start |
| --- | --- | --- |
| **General** | Holds global options that do not belong to appearance, Root capabilities or data maintenance. | Updates, language, App Profile templates |
| **Appearance** | Decides what the interface looks like. | Theme, wallpaper, home screen and module card appearance |
| **Behavior** | Decides how the interface operates and navigates, plus some interaction compatibility. | Navigation, floating bar, gestures, module card behavior |
| **Features** | Manages kernel features, built-in engines and file system isolation. | Zygisk, Sui, Folk Mount, NoMount, network isolation |
| **Security** | Manages kernel security boundaries and debugging entry points. | SELinux, ADB Root, Shell Root restrictions |
| **Verification** | Protects startup and sensitive actions with the Manager's own credentials. | App lock, PIN, pattern, action protection |
| **Multimedia** | Manages interface vibration and sound. | Vibration, background music, touch sound effects, startup sound effects |
| **Backup** | Saves and restores modules, Root authorization and the boot partition. | Back up now, WebDAV, restore |

:::

## Find It in Two Minutes

:::: steps

1. **Start with the home screen grid**

   Only the entry name is written under each icon. After you open a category page, all related options are listed under the same title, grouped together.

2. **Judge the impact first**

   Replacing wallpaper and layout usually only affects the Manager; switching mounts, kernel features, SELinux or partition restore changes system behavior. Read the warnings on that category page first.

3. **Read same-name features in context**

   "Module" may mean a regular module, a kernel module or the Root driver. The "Modules" entry in Settings handles module management; "Features → Folk Mount" decides how module files are injected.

4. **Confirm how the change takes effect**

   Some switches take effect immediately, some after a reboot, and some only for the next boot. Each category page states this explicitly; do not treat every "saved" as "already in effect".

::::

## Other Entries on the Settings Home

These entries are not part of the eight categories, but they can be used directly from the settings home screen.

::: table Home screen shortcuts

| Entry | What it can do | Note |
| --- | --- | --- |
| **Profile header** | Tap the avatar to edit the nickname, signature, avatar and avatar opacity. The avatar can be a local image, a QQ avatar or a Gravatar. | Only changes the displayed profile inside the Manager. |
| **Bug feedback** | Opens the project's public issue tracker. | Before submitting, prepare the version, runtime mode, reproduction steps and logs; never publish credentials. |
| **Send logs** | Select and export Manager diagnostic logs. | Logs may contain package names, paths and device information; check them before sending. |
| **About** | View Manager, kernel, runtime mode and project information. | Record the version and runtime mode from here when reporting a problem. |
| **Uninstall** | Shown only in LKM mode; provides permanent uninstall and restoring the stock image. | Not available in Jailbreak mode (late load); back up before using it. |

:::

::: warning Cannot find an option

First check whether the current mode supports it. Some options only appear on a specific system version or with Zygisk, SUSFS, Folk Mount or Jailbreak mode; when the kernel does not support them, the switch may also be greyed out. Do not force-enable an unsupported control by editing configuration files.

:::

## Why a State Cannot Be Changed

A feature item may be in one of four states:

:::: field-group

::: field `supported`
@type `Editable`
The current kernel and runtime state support the feature, so it can be toggled directly.
:::

::: field `forced`
@type `Forced`
The boot configuration already decided the value, and the Manager is not allowed to override it here.
:::

::: field `managed`
@type `Module-managed`
The capability is currently managed by a module, and the Settings page should not compete with it for control.
:::

::: field `unsupported`
@type `Unsupported`
The current kernel lacks the corresponding capability. Upgrading to a kernel or package that contains the feature is the only possible fix.
:::

::::

## Continue Reading

- [General](/en/setting/general/): updates, language, App Profile templates.
- [Appearance](/en/setting/appearance/): theme, wallpaper, home layout.
- [Behavior](/en/setting/behavior/): navigation, floating bar, SU compatibility, module display.
- [Features](/en/setting/function/): mount policy, YukiZygisk, Sui, NoMount and more.
- [Security](/en/setting/security/): SELinux, ADB Root and Shell restrictions.
- [Verification](/en/setting/authentication/): Manager app lock and sensitive action protection.
- [Multimedia](/en/setting/multimedia/): vibration, background music and sound effects.
- [Backup](/en/setting/backup/): local and WebDAV backup and restore.
