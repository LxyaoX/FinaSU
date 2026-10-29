---
title: FinaSU
createTime: 2026/10/24 00:00:00
permalink: /en/
pageLayout: home
externalLinkIcon: false
config:
  - type: doc-hero
    full: true
    background: tint-plate
    tintPlate: 219,229,255
    hero:
      name: FinaSU
      tagline: Kernel-based Android Root
      text: A simple and feature-rich Root manager solution
      image: /icon.png
      actions:
        - theme: brand
          text: Get Started
          link: /en/guide/get-started/
          suffixIcon: ri:arrow-right-line
        - theme: alt
          text: GitHub
          link: https://github.com/LyraVoid/FinaSU
          icon: ri:github-fill
          target: _blank
          rel: noopener noreferrer
  - type: features
    features:
      - title: Highly Customizable
        icon:
          src: /icons/home/palette.svg
          alt: Highly Customizable
          width: 26
          height: 26
          wrap: true
        details: Replaceable wallpaper, video backgrounds, colors, music, sound effects, and haptic feedback.
        link: /en/setting/appearance/
      - title: Home Work Card
        icon:
          src: /icons/home/layout.svg
          alt: Home Work Card
          width: 26
          height: 26
          wrap: true
        details: A large-type work card, two stat tiles and a system information card, with its own background image.
        link: /en/setting/home/
      - title: Kernel-Level Root
        icon:
          src: /icons/home/shield-lock.svg
          alt: Kernel-Level Root
          width: 26
          height: 26
          wrap: true
        details: Authorization, the allowlist, and App Profiles are decided in the kernel. Set UID/GID, capabilities, SELinux domain, and module unmount policy per app.
        link: /en/manager/app-profile/
      - title: Built-In Engines
        icon:
          src: /icons/home/folder-open.svg
          alt: Built-In Engines
          width: 26
          height: 26
          wrap: true
        details: Folk Mount, YukiZygisk, NoMount, and Sui ship with FinaSU and are managed by ksud, so you no longer install a matching set of upstream modules.
        link: /en/builtin/
      - title: Zygisk & Sui
        icon:
          src: /icons/home/plug.svg
          alt: Zygisk & Sui
          width: 26
          height: 26
          wrap: true
        details: YukiZygisk provides the Zygisk environment. Sui provides a backend for apps that use the Shizuku API, and external compatible providers work too.
        link: /en/builtin/yukizygisk/
      - title: Kernel Features
        icon:
          src: /icons/home/network.svg
          alt: Kernel Features
          width: 26
          height: 26
          wrap: true
        details: SUSFS integration is exposed according to actual kernel support. It requires source integration or a matching SUSFS-LKM.
        link: /en/builtin/susfs/
      - title: Modules & Plugins
        icon:
          src: /icons/home/cube.svg
          alt: Modules & Plugins
          width: 26
          height: 26
          wrap: true
        details: Supports systemless modules, WebUI, Metamodules, and Lua plugins, with modules from built-in, community, KernelSU, or custom repositories.
        link: /en/manager/module-store/
      - title: Three Installation Methods
        icon:
          src: /icons/home/install.svg
          alt: Three Installation Methods
          width: 26
          height: 26
          wrap: true
        details: LKM keeps your kernel and modifies the ramdisk. Built-In compiles the driver into the kernel. Jailbreak mode leaves the boot partition untouched but does not keep Root across reboots.
        link: /en/guide/install/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---
