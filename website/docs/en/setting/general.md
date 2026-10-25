---
title: General Settings
description: Updates, App Profile templates and language in FinaSU Settings
createTime: 2026/10/24 00:00:00
permalink: /en/setting/general/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# General Settings

<Badge text="Settings entry 1/8" type="info" /> <Badge text="Updating is not flashing the kernel" type="warning" />

General holds the options used for day-to-day maintenance that do not belong to appearance or to the Root runtime policy. It currently contains four features: check for updates, check for module updates, App Profile templates and the app language.

::: tip How to get there

Open the Manager → Settings → tap **General** in the first row.

:::

## Check for Updates

**Check for updates** controls whether the Manager checks for a new FinaSU version automatically after startup.

- When on: the Manager requests release information while refreshing the home screen, and shows an available update entry when a newer version exists.
- When off: this check is not started automatically, and the home screen does not show a version update card obtained from the automatic check.
- A new version must still come from an official source with a consistent signature. Do not install an APK of unknown origin just because its version number is higher.

::: danger A Manager update is not a driver update

The Manager APK only replaces the interface and the bundled resources. LKM, Built-In and Jailbreak mode update the driver in different ways; updating only the APK never replaces a driver that is already loaded or already built into the kernel.

See [Updating FinaSU](/en/guide/update/) for the complete steps.

:::

## Check for Module Updates

**Check for module updates** decides whether the Manager checks enabled modules for new versions using module repository metadata.

- It checks regular modules, not the FinaSU Manager itself; disabled modules are not part of this check.
- No update notice may appear when the module author did not provide a reliable version number, the repository information is incomplete, or the module is not in a known repository.
- After seeing an update, read the module release notes and compatibility range before installing it.

:::: steps

1. **Confirm the source**

   Module updates should come from the repository configured for that module or another trusted source. Do not overwrite-install an unknown ZIP forwarded in a chat group.

2. **Back up first**

   You can enable pre-flash backup under "Settings → Backup" and save the current modules and authorization list.

3. **Reboot after updating**

   Replacing module files usually takes effect only at the mount stage after a reboot. A success message only means the installation flow finished.

4. **Verify the result**

   After rebooting, check the module state, the SU logs and the related features. If a module prevents the device from booting, follow [Bootloop Rescue](/en/guide/rescue-bootloop/).

::::

## App Profile Templates

**App Profile templates** store reusable Root Profile parameters, so you do not have to fill in the UID, user groups, capabilities and SELinux domain again for every app.

::: tip Regular users can just use a preset

The template page provides the loaded local and online presets. If you are not familiar with UID, GID, user groups, capabilities and SELinux domains, ==simply pick the preset you need; there is no need to customize or create a template==.

:::

::: warning Templates change permission boundaries

A template is not an ordinary appearance preset. A wrong UID/GID, user group, SELinux domain or capability combination can give an app too many permissions, or keep it from working at all. Only import templates from trusted sources.

:::

A template usually contains the following fields:

:::: field-group

::: field Identity and description
@type `Required`
@default `Name / description`
Use a recognizable name that states the purpose of the template, such as "Regular app Root" or "File management only". The description should make the scope clear.
:::

::: field UID / GID
@type `Identity`
@default `As needed`
Decides the user and group identity of the Root process at runtime. Not every app needs this changed; keeping the default is usually safer than guessing values.
:::

::: field User groups
@type `Permissions`
@default `As needed`
Supplementary groups widen the range of devices or files the process can access. Do not add every group blindly just to get "more complete permissions".
:::

::: field capabilities
@type `Fine-grained`
@default `As needed`
Allows part of the high-privilege capabilities to be kept under an ordinary identity. Match them to what the app actually does.
:::

::: field SELinux domain
@type `Mandatory access control`
@default `As needed`
Decides the range of operations the Root process may perform. A wrong domain can directly cause access denials.
:::

::: field `no_new_privs`
@type `Restriction`
@default `As needed`
Prevents the process from gaining additional privileges by executing files. Before enabling it, confirm the app does not rely on `su` or setuid behavior.
:::

::::

### Using a Template

:::: steps

1. Go to "Settings → General → App Profile templates".
2. Pick a template that fits the purpose from the preset list. For regular users this is as far as it goes.
3. Select that template in the app's App Profile configuration; saving copies the template fields as they are at that moment.
4. Create or modify a template only when you clearly understand each permission field. Templates are not inherited live; to update an app after editing a template, return to that app, select the template again and apply it.

::::

For how app-level Root authorization relates to templates, see [App Profile](/en/manager/app-profile/).

## Language

**Language** only changes the language used by the FinaSU Manager interface.

::: tip Takes effect immediately after switching

After you select a language, the Manager rebuilds the current interface and loads the matching language resources right away. It is a hot update; you do not need to restart the Manager, and certainly not the device.

:::

There are 20 languages available, in the same order as the Manager:

::: table Manager languages

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

"Follow system" clears the Manager's own language override and uses the language Android selected for the app again. A manual choice only affects the Manager; it does not change the system language, the module script environment or third-party apps.

::: tip Do not use the language setting to troubleshoot modules

Module script output, kernel logs and `ksud` text are normally in English. Switching the Manager language does not translate this content and does not change command arguments.

:::

## FAQ

:::: collapse accordion

- **After turning off "Check for updates", can I still check manually?**

  Yes. Turning the switch back on re-enables the startup check; whether an immediate check is available depends on the update entry of the current version.

- **Why is there no update notice for a module?**

  The module may have no reliable version metadata, no configured repository, or an installed version that cannot be matched to a repository entry. You can also open the module repository manually.

- **Why does the home screen still show the old runtime mode after the Manager version updates?**

  The runtime mode comes from the current driver, not from a value the APK declares itself. Built-in or LKM drivers have to go through their own update path.

- **I changed the Manager language, so why are the logs still in English?**

  Logs are produced by the kernel, `ksud` or modules and are outside the scope of Manager interface localization.

::::

## Related Pages

- [Updating FinaSU](/en/guide/update/)
- [App Profile](/en/manager/app-profile/)
- [Module Repository](/en/manager/module-store/)
- [Device Storage Layout](/en/manager/storage/)
