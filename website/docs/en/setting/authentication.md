---
title: Verification
description: FinaSU Manager app lock, PIN, pattern and protection for sensitive operations
createTime: 2026/10/24 00:00:00
permalink: /en/setting/authentication/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Verification

<Badge text="Settings entry 6/8" type="info" /> <Badge text="Manager app lock" type="tip" />

Verification confirms that the person operating Manager is the device owner. It is not the same mechanism as Root authorization, the Android lock screen or SELinux.

::: tip How to get there

Open Manager → Settings → tap the third entry in the second row, **Verification**.

:::

## What It Protects

Verification protects only sensitive entries and operations inside FinaSU Manager. It does not:

- Replace the device lock screen.
- Stop other Root apps from running commands.
- Change an app's Root allowlist.
- Encrypt the modules or authorization files under `/data/adb`.

Its value is this: even if someone else gets hold of the already unlocked phone, they cannot open Manager, install modules or flash partitions directly.

## Set Credentials First, Then Choose the Default Method

The page first offers two credentials, "numeric password" and "pattern", then offers the "default verification method".

### Numeric PIN

- It can only be 4 or 6 digits.
- When unset, it shows "Set numeric PIN".
- When set, it shows "Change numeric PIN" and "Clear numeric PIN".
- The current PIN must be verified before changing or clearing it.

### Pattern

- Connect at least 4 dots.
- A dot cannot be used twice.
- When unset, it shows "Set pattern".
- When set, it shows "Change pattern" and "Clear pattern".
- The current pattern must be verified before changing or clearing it.

### Default Verification Method

There are four default methods:

::: table Verification methods

| Method | Requirement | Notes |
| --- | --- | --- |
| **None** | None | No verification; every protected target behaves as if it were off |
| **Fingerprint** | The device has strong biometric hardware and an enrolled fingerprint | The UI text says "fingerprint", but the Android strong biometric API handles it |
| **Numeric PIN** | A PIN is set | Uses the 4 or 6 digit PIN |
| **Pattern** | A pattern is set | Uses a pattern connecting at least 4 dots |

:::

If you choose numeric PIN or pattern without having set the matching credential, Manager refuses the change and asks you to set the credential first. When biometric hardware is missing or nothing is enrolled, the "fingerprint" option does not appear.

::: important The default method applies to every protected target

The current page uses a "single default method": once you turn on protection for a target, it uses the default verification method. Setting the default to "None" also removes the actual interception from every protected target.

:::

## Protected Targets

You can enable the following protections separately:

::: table Sensitive operations

| Target | When it triggers | Once enabled |
| --- | --- | --- |
| **App launch** | Every time you enter Manager | A verification page appears first after launch |
| **Module install** | Installing a module from local storage, the repository or a flashing flow | Verification is required before installing |
| **Module uninstall** | Deleting a module | Verification is required before uninstalling |
| **Module disable** | Turning a module switch off | Verification is required before disabling |
| **Partition flashing** | Patching, flashing or restoring a boot partition | Verification is required before writing |
| **Theme / FPT import** | Importing a `.fpt` theme package | Verification is required before applying theme resources |
| **Reboot operations** | A reboot, soft reboot or related recovery action started by Manager | Verification is required before executing |

:::

::: warning Partition flashing carries the highest risk

The "partition flashing" protection cannot replace backups, image matching and bootloop preparation. Verification only confirms who the operator is; it does not check whether the image belongs to the current device or slot.

:::

## First-Time Setup

:::: steps

1. **Set a credential**

   Choose "Set numeric PIN" or "Set pattern" and enter it twice as prompted. Prepare at least one of PIN and pattern.

2. **Choose the default verification method**

   Go back to the category home page and select the PIN or pattern you just set under "default verification method"; you can also choose fingerprint when the device supports it.

3. **Enable protections one by one**

   Start with "App launch", then add "Module install", "Module uninstall", "Partition flashing" and "Reboot operations".

4. **Exit and verify**

   Quit Manager completely and open it again, confirming that the verification page appears and can be passed normally.

5. **Record your recovery method**

   Remember the PIN or pattern. If you forget it, reinstalling the APK will not bypass it; if the credential storage is corrupted, the page becomes non-editable and the data problem has to be handled first.

::::

## Changing and Clearing

- **Change PIN**: verify the old PIN, then enter a new 4 or 6 digit number.
- **Change pattern**: verify the old pattern, then draw it again.
- **Clear PIN/pattern**: the old credential must be verified first.
- After you clear the credential that the current default method relies on, the related protections fall back to "None" so no verification is left that can never be completed.

::: danger Do not delete security configuration manually without knowing where the data lives

Deleting Manager's SharedPreferences or app data directly may cost you the existing protection configuration and may also affect other Manager state. Export the logs first and confirm the recovery path.

:::

## How Credentials Are Stored

Manager does not store the PIN or pattern in plain text. Credentials are checked with a PBKDF2-HMAC-SHA256 record using a random salt, and only the derived result is stored. After repeated failed attempts, retries are delayed progressively.

If the page reports a storage error:

- Every verification setting becomes non-editable.
- A corrupted record is not treated as "no password set yet".
- Manager keeps handling sensitive operations according to the protection state instead of silently letting them through.

In that case, back up the accessible data first, then investigate app storage or restore an imported configuration.

## Recommended Configurations

::: table Choose by usage intensity

| Scenario | Suggestion |
| --- | --- |
| Personal device, frequent debugging | App launch + module install + partition flashing |
| Daily driver | App launch + module install / uninstall / disable + reboot |
| Frequently testing themes | Also enable "Theme / FPT import" |
| Lending the device temporarily | At least app launch, and turn off ADB Root |
| Jailbreak mode | Keep reboot protection; auto jailbreak still needs the autostart permission |

:::

## Verification Flow

::: timeline A single sensitive operation

- Trigger a protected operation in Manager
  time=Start

- Manager reads whether that target is protected and the current default method
  time=Check

- The fingerprint, PIN or pattern prompt appears
  time=Verify

- On success the original operation continues; cancel or failure stops it
  time=Result type=success

- Whether the operation itself succeeds is still up to the module, kernel and storage state
  time=Follow-up

:::

::: info Passing verification does not mean the operation succeeds

The app lock only stops people who have not been verified. When the image does not match, the module ZIP is corrupted, storage is full or the kernel refuses the write, the operation still fails.

:::

## Common Questions

:::: collapse accordion

- **What if I forget the PIN or pattern?**

  The settings page has no bypass entry. First check whether you can still use the other credential or are still in a signed-in session; otherwise you have to handle Manager data without destroying other data.

- **Why is there no "fingerprint" option?**

  The device may lack strong biometric hardware, may have no enrolled fingerprint, or the current system cannot use strong biometrics.

- **After enabling app launch, do I have to verify every time I switch apps?**

  It depends on whether Manager treats that entry as a new launch-protection session. Quitting completely and opening again reproduces it most reliably.

- **Do the protected targets remain after I turn off the default verification method?**

  The target switches may still show as protected, but with the default set to "None" there is no actual interception. You need to select a valid credential again.

- **Can a Root app bypass the app lock?**

  A Root app that can modify device data directly is outside this app lock's security boundary. Verification mainly prevents an ordinary device holder from operating directly inside the Manager UI.

::::

## Related Pages

- [Security](/en/setting/security/)
- [App Profile](/en/manager/app-profile/)
- [Backup](/en/setting/backup/)
- [Bootloop Rescue](/en/guide/rescue-bootloop/)
