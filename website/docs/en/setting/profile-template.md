---
title: App Profile Templates
createTime: 2026/10/24 00:00:00
permalink: /en/setting/profile-template/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# App Profile Templates

A template saves one set of Root Profile parameters as a reusable configuration. The entry is at "Settings → General → App Profile templates".

::: tip Start with a preset, no need to configure it yourself

The page loads local and online presets. Ordinary users can simply choose a preset that fits the purpose; there is no need to understand UID, GID, user groups, capabilities or SELinux domains, and no need to create a custom template.

:::

## Fields

- **ID**: local unique identifier; the ID is recorded when the template is selected.
- **Name**: display name.
- **Description**: what the template is for and where it applies.
- **Configuration**: Root Profile values such as UID, GID, user groups, capabilities, SELinux domain and `no_new_privs`.

Selecting a template copies the template fields as they are at that moment to the app UID; templates are not inherited live. Editing a template does not automatically rewrite app policies that are already saved, so you have to return to the matching App Profile, select the template again and apply it. The template editor also does not list every app that has used that ID, so record the scope yourself before editing.

## Import and Export

Templates can be exported to the clipboard and imported from it. Only import templates from trusted sources; a wrong combination of SELinux domain and capabilities can widen or break permissions.

For how app-level policies relate to templates, see [App Profile](/en/manager/app-profile/).
