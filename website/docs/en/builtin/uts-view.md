---
title: UTS View
description: Scope, fields, SUSFS takeover, observation log and troubleshooting for FinaSU kernel info spoofing
createTime: 2026/10/25 00:00:00
permalink: /en/builtin/uts-view/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# UTS View

<Badge text="Kernel Feature" type="info" /> <Badge text="Applies to New Processes" type="warning" /> <Badge text="Up to 128 Apps" type="tip" />

==UTS View lets target processes see a different set of `uname` information==. It modifies the name fields in the Linux UTS namespace, not Android system properties, and not a complete device fingerprint.

::: important Only `uname` Fields Are Affected

UTS View does not modify `ro.build.*`, the security patch level, hardware attestation, actual CPU capabilities or app-private caches. Apps may still obtain the real information through other interfaces.

:::

## Entry and Page

The entry is under **Settings → Features → Kernel Info Spoofing**. The page is divided, from top to bottom, into:

1. Enable state and the current spoofing source.
2. Scope: selected apps or global.
3. Spoofed fields and target apps.
4. Runtime details.
5. Restore or delete settings.

When SUSFS is currently providing `uname` spoofing, the page shows "taken over by SUSFS" and the normal editing controls are restricted. Use "Switch to this feature's spoofing" to perform an explicit handover.

## Six Fields

:::: field-group

::: field System name (sysname)
@type `hidden by default`
Usually `Linux`. Generally no need to change it separately.
:::

::: field Node name (nodename)
@type `hidden by default`
The hostname. Generally no need to change it separately.
:::

::: field Kernel release (release)
@type `Commonly used`
For example `6.1.75-android14`. Most scenarios only need to change this one.
:::

::: field Build version (version)
@type `Commonly used`
The kernel build string, usually containing compiler or build time information.
:::

::: field Machine architecture (machine)
@type `hidden by default`
For example `aarch64`. A value clearly inconsistent with the app's ABI may cause compatibility problems.
:::

::: field Domain name (domainname)
@type `hidden by default`
A few environments read this field.
:::

::::

Each field is at most 64 bytes and cannot contain NUL; `release` additionally requires every character to be printable ASCII or a space. Only fields included in the spoof mask are overridden; unselected fields keep the original values of the target process.

::: warning Fields Must Be Mutually Consistent

Do not assemble clearly contradictory combinations of version, build time and architecture. The goal of spoofing is to replace identity information, not to invent a kernel version the app cannot parse.

:::

## Two Scopes

::: table Selected Apps and Global

| Scope | Matching Targets | Target Limit | How It Works |
| --- | --- | --- | --- |
| **Applies to selected apps only** | Ordinary app processes whose app UID is in the list | 128 UIDs | Creates a private UTS namespace and writes the template when an app starts or switches UID |
| **All apps and terminals** | Processes that still use the initial UTS namespace | No app list used | Modifies the name fields of the initial UTS namespace |

:::

Selected mode only applies to app processes. Non-app processes, isolated processes and processes that already have a private UTS namespace are skipped and counted in the runtime details.

Global mode looks broader, but it is not "rewriting every namespace" either. Containers, apps or system processes that have already created a private UTS namespace are not retroactively modified, and usually need a restart to enter the new initial view.

::: danger Global Mode Has a Very Wide Impact

Before switching to global, the Manager asks for confirmation. Every process sharing the initial UTS namespace will see the new values. Do not apply a wrong `release`, `machine` or a missing field to the whole device.

:::

## Enable Flow

:::: steps

1. **Confirm the current source**

   If SUSFS has taken over, first decide whether to keep using SUSFS or to perform "Switch to this feature's spoofing".

2. **Start from the current values**

   When the real values can be read, prefer "Use this device's current information as a starting point", then change only the necessary fields.

3. **Choose scope and targets**

   For ordinary scenarios choose "Applies to selected apps only", with at most 128 UIDs. Global mode is only for tests or device-level spoofing that genuinely need it.

4. **Save first, then enable**

   While disabled, saving stores a draft; enabling saves and applies the template immediately. Existing target processes do not change automatically.

5. **Fully close and reopen the target app**

   Multi-process apps require terminating all related processes. Switching to the background alone does not guarantee a new app process.

6. **Check the runtime details**

   "Already in effect" only means the kernel observed a target process hit. If the app still shows old values, first confirm the app has not cached the result.

::::

## Status Meanings

::: table UTS View Page States

| Status | Meaning |
| --- | --- |
| **Not configured** | No fields or targets saved |
| **Settings saved but not in effect yet** | A draft exists and the feature is not enabled |
| **Spoofing is on** | The template is enabled, but no target app hit has been observed yet |
| **Already in effect** | At least one target process passed the check and adopted the template |
| **In effect for all apps** | The global template is enabled |
| **Taken over by SUSFS** | SUSFS is currently the `uname` provider, and UTS View cannot take over directly |
| **Not supported by the current kernel** | Required symbols or kernel features are missing |
| **Spoofing state abnormal** | The configuration, kernel, provider or runtime log has an error |

:::

Status is not decided by the switch alone. The page also reads the kernel readback, the current UTS provider, the persistent configuration and the latest runtime errors.

## Relationship with SUSFS

SUSFS can also spoof `uname`. Only one clearly active provider may exist at a time; otherwise who writes the real kernel values becomes unpredictable.

::: timeline Switching from SUSFS to UTS View

1. **SUSFS detected as applied or planned to apply**

   UTS View shows "taken over by SUSFS" and does not overwrite directly.

2. **Prepare the UTS View template**

   Changes on the page are saved as a draft first.

3. **Tap "Switch to this feature's spoofing"**

   This action restores SUSFS's runtime `uname` spoofing first, then enables the UTS View template.

4. **Transaction rollback on failure**

   If a step in the handover fails, the system tries to restore the old UTS View configuration, kernel state and SUSFS state, and reports leftovers.

5. **Restart the target app**

   Avoid old processes continuing to hold the UTS namespace or cached values from before the switch.

:::

When the page shows a SUSFS takeover, "Delete UTS settings" only deletes UTS View's own configuration, ==it does not delete or disable an active SUSFS spoof==.

## Runtime Details

Selected mode records the following observations:

- **Hit count**: number of processes that successfully applied the template.
- **Latest hit**: the most recent UID and PID.
- **Not in app list**: the target is not a selected UID.
- **Non-app process**: the ordinary app UID check failed.
- **Non-ordinary app process**: not the expected Zygote app launch path.
- **Already has a separate runtime environment**: the process already has a private UTS namespace.
- **Isolated process**: isolated UIDs such as WebView or SDK sandboxes.
- **Failed isolated environment creations**: the kernel could not create a private namespace for a hit target.
- **Discarded stale results**: the template changed during the operation, and old observations were not miscounted.

These counters reset when the configuration is reset or reapplied. Seeing "hit" does not mean every app subprocess hit, especially for apps that use separate namespaces or unusual launch paths.

## Restoring Real Information

When SUSFS has not taken over, "Restore real information" at the bottom of the page will:

- Disable UTS View.
- Clear the kernel template.
- Delete the persistent configuration and the selected app list.

Apps that are already running may need a restart to read the real values again. When SUSFS has taken over, only UTS View's own settings are deleted and SUSFS is not touched.

## Troubleshooting

:::: collapse accordion

### It shows "spoofing is on" but nothing hits

Fully close the target app and all of its processes, then reopen it. Then confirm the selected UID is still correct; the UID can change after reinstalling an app or switching work profiles.

### The target app already has a separate runtime environment

That process had a private UTS namespace before UTS View intervened. UTS View will not rewrite it retroactively; restart the app or the relevant runtime environment.

### Global mode has no effect on a terminal or container

It only modifies the initial UTS namespace. When a terminal, container or system service has created its own UTS namespace, it may keep seeing the original values.

### The page cannot read the real kernel release

When SUSFS is taking over, it shows "cannot read real values". Stop the provider or hand it over first, then read the values again; do not guess by hand.

### The app misbehaves after the change

Disable UTS View or restore real information immediately, then restart the app. Focus on whether `release`, `version` and `machine` are mutually consistent.

::::

## Configuration and Diagnostics Locations

```file-tree title="UTS View locations"
/data/adb/ksu
├── uts_view.json               # fields, scope, targets and enable intent
├── uts_view.runtime_error      # latest boot or app error
└── .uts_view.lock              # configuration transaction lock
```

The configuration file is protected by owner and permission checks. Do not rewrite the JSON directly to bypass field length, UID limit or mode validation.

::: code-tabs

@tab Manager

```text
Settings → Features → Kernel Info Spoofing
Check the current provider, page state, runtime details and error text
```

@tab Status

```sh
/data/adb/ksud uts-view status
```

:::

The command line returns the configuration, provider, kernel state and observation counters; daily operations still follow the Manager.

## Usage Advice

1. **Usually change only `release` or `version`.** Keep the other fields as they are unless there is a clear need.
2. **Prefer selected apps.** Global mode has a far wider impact than a single app.
3. **Handle provider conflicts first.** Do not try to enable with two switches at once when SUSFS has taken over.
4. **Verify with new processes.** Old processes continuing to use the old name cache is normal and does not mean UTS View is not working.
5. **Do not treat UTS View as complete anti-detection.** It only changes `uname` fields and does not cover system properties or hardware attestation.
