---
title: Passing Detection
description: Keep the target app working first instead of chasing all-green detection tools
createTime: 2026/10/25 00:00:00
permalink: /en/troubleshoot/pass-detection/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Passing Detection

<Badge text="Target app first" type="tip" /> <Badge text="Do not chase all green" type="warning" />

The goal of passing detection is to keep daily apps working, not to make every detection tool show green. Detection tools actively look for Root, injection, mounts and integrity anomalies, usually more aggressively than regular apps; a red result from a detection tool does not mean FinaSU is broken.

## Decide Whether to Continue

:::: table Two situations

| Symptom | Suggestion |
| --- | --- |
| Daily apps work and only a detection tool reports red | Usually no further action is needed |
| An app explicitly refuses to start, sign in or pay | Record the app name, the message and the last change, then investigate |

::::

If the app itself works, do not keep adding hiding items just to get an all-green detection result. The more a tool is dedicated to detecting Root, the more likely it deliberately checks extreme traits, so failing them is not surprising.

## Do Not Stack Hiding Solutions

More hiding items are not better. Several hiding packages of unknown origin may compete for the same kernel interface, and in the end Root authorization, module mounting or other normal apps break first.

Do not enable all hiding options at once, and do not treat "one-click detection bypass" as a universal fix. Keep the configuration minimal so that when a problem appears you can tell which item caused it.

## Minimal Handling Order

:::: steps

1. **Keep a working configuration first**

   Remove recently added, non-essential hiding items and packages of unknown origin. Confirm first that FinaSU's Root authorization and the modules themselves are fine.

2. **Confirm whether the target app has Root**

   A UID that is already allowed Root does not continue to use the non-Root Profile's module unmount policy. Check App Profile first instead of adjusting mounts blindly.

3. **Change only the one item related to the symptom**

   Module visibility, mount records, SELinux differences and kernel capabilities belong to different layers. Adjust one item at a time to avoid changing several variables at once.

4. **Restart the target app**

   Fully close and reopen the app first; if the change requires a reboot, test again after a complete reboot.

5. **Stop as soon as it works again**

   Once the target feature works, stop chasing the detection tool's score. Stacking more settings usually only adds conflicts and makes troubleshooting harder.

::::

::: warning Do not keep SELinux enforcing mode disabled long term

Setting SELinux to Permissive lowers the system security boundary and does not guarantee passing detection. Use it temporarily only during explicit troubleshooting or controlled procedures.

:::

::: warning Do not make all green the goal

If your software works normally, do not obsess over detection software.

==Live your life, not the detection.==

:::

If the target app still refuses, use [Detection & Hiding](/en/troubleshoot/detection/) to confirm what it actually sees, then handle the matching layer.
