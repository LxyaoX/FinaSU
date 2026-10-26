---
title: Built-in Capabilities
createTime: 2026/10/24 00:00:00
permalink: /en/setting/builtin/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Built-in Capabilities

<Badge text="Compatibility page" type="info" /> <Badge text="Full documentation has moved" type="tip" />

The Features page lists the built-in engines driven by `ksud` in one place. Each capability has its own state and availability conditions. For entries, dependencies and the full documentation, start from the [Built-in Capabilities Overview](/en/builtin/).

## YukiZygisk

Views the engine switch, the external provider, modules and recent boot diagnostics. Enabling it and updating the engine usually need a reboot. See [YukiZygisk](/en/builtin/yukizygisk/).

## Sui

Manages the built-in Shizuku API backend, payload, permissions and diagnostics. Sui needs a Zygisk provider. See [Sui Guide](/en/builtin/sui/).

## Folk Mount

Chooses automatic, built-in, Metamodule only, NoMount or mounting off. The status bar shows the provider actually used this boot, and changes affect only the next boot. See [Folk Mount](/en/builtin/folk-mount/).

## NoMount

Views the driver, injection rules, module contributions and exception apps. When the driver API is too old or the module for the current KMI is missing, switch to another mount backend. See [NoMount](/en/builtin/nomount/).
