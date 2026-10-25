---
title: Multimedia
description: Touch haptics, background music and sound effects in FinaSU Manager
createTime: 2026/10/24 00:00:00
permalink: /en/setting/multimedia/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Multimedia

<Badge text="Settings entry 7/8" type="info" /> <Badge text="Affects Manager only" type="tip" />

Multimedia controls Manager's touch feedback, background music, touch sound effects and startup sound. They do not change Root, modules, authorization or SELinux.

::: tip How to get there

Open Manager → Settings → tap the fourth entry in the second row, **Multimedia**.

:::

## Feature Overview

::: table Four kinds of feedback

| Feature | When it appears | What you can customize |
| --- | --- | --- |
| **Touch haptics** | When the user touches or operates a control in the UI | Feedback scope, strength |
| **Background music** | When Manager enters and plays background audio | File, autoplay, loop, volume, progress |
| **Touch sound effects** | When the user touches a UI control | Local file or preset sound, feedback scope |
| **Startup sound** | When Manager launches and plays startup feedback | Local file or preset sound |

:::

## Touch Haptics

Touch haptics provide haptic feedback in the UI, independent of whether Root authorization succeeds.

Once enabled, you can set:

- **Feedback scope: all touches**: every interaction that uses Manager's haptic feedback may trigger.
- **Feedback scope: navigation controls**: mainly limited to the bottom, side or floating navigation.
- **Strength**: the slider is `0%–100%`; a higher value usually means stronger vibration.

The actual effect is still limited by the device's vibration hardware, system haptic policy and power-saving settings. A strength of 100% does not mean every device produces the same amplitude.

::: tip Dragging the strength slider plays one test vibration

After you release the slider and the current percentage is saved, Manager plays the feedback once so you can compare the strength directly.

:::

## Background Music

Background music is an audio file chosen by the user and played by Manager.

### Basic Switches

- **Background music**: the master switch. When off, autoplay, loop, volume and playback controls are hidden.
- **Choose file**: opens the system file picker to select an audio file.
- **Autoplay**: when music is on and the file is available, Manager can start automatically after entering a playback screen.
- **Loop**: restarts after reaching the end of the file.
- **Volume**: music volume inside Manager, range `0%–100%`.

### Playback Controls

After a file is chosen and music is on, these appear:

- **Play / pause**: toggles the current playback state.
- **Time and progress bar**: shows the current position and total duration; drag to jump to a position.
- **Clear file**: asks for confirmation, then stops playback and deletes the current music selection saved by Manager.

::: warning When playback fails, check the file first

When the page says "cannot play", the file may be corrupted, in an unsupported format, deleted or unreadable. Choose a new audio file in a common format instead of toggling the music switch back and forth to judge.

:::

### Recommended Setup Order

:::: steps

1. Turn on background music.
2. Tap "Choose file" and pick audio in the system file picker.
3. Confirm the file appears below "Choose file".
4. Turn on autoplay and loop as needed.
5. Adjust the volume and use play/pause to confirm the result.

::::

## Touch Sound Effects

Touch sound effects play a short sound for UI interactions.

### Source

:::: code-tabs

@tab Local file

```text
Tap "Choose file" and pick an audio clip from the device.
Suits using your own notification sound.
```

@tab Preset sound

```text
Pick from the preset sounds provided by Manager.
No local file is needed, so switching is more direct.
```

::::

### Feedback Scope

- **All touches**: every interaction that uses Manager's sound feedback.
- **Navigation controls**: mainly limited to navigation-related operations.

Once touch sound effects are on, you can tap "Play" to preview the current source. If a file is selected, you can also clear it; preset mode needs no local file.

::: tip Keep local sound effects short

Touch sound effects are for short feedback. Choosing a long track makes every tap play from the beginning, which is hard to hear and gets in the way.

:::

## Startup Sound

The startup sound plays only during Manager's launch flow.

Configurable items:

- **Startup sound switch**.
- **Source: local file / preset sound**.
- **Local file selection**.
- **Preset sound selection**.
- **Preview playback**.
- **Clear the current file**.

The startup sound has no "feedback scope" because it is not triggered by a touch location.

::: warning The startup sound depends on the system launch state

When the system restores Manager directly, when the process is still in the background, or when the launch screen is not fully replayed, the startup sound may not play again. Do not treat it as a boot notification sound; it belongs only to Manager's launch feedback.

:::

## Audio File Management

:::: field-group

::: field Choose file
@type `Local`
Opens the system file picker. The file becomes the current selection only after the import succeeds.
:::

::: field Import failure
@type `Error`
When the file is unreadable, the format is unsupported or copying fails, Manager keeps the previous selection and reports that the import failed.
:::

::: field Clear file
@type `Confirmation`
Deletes the current media selection saved by Manager. Some clear actions also stop the audio that is playing.
:::

::: field Preset sound
@type `No file needed`
Uses the sound built into Manager directly; switching presets does not create a custom audio file on the device.
:::

::::

## Troubleshooting Feedback Issues

:::: collapse accordion

- **Haptics are on but I feel nothing**

  Check whether haptic feedback is turned off in the system, whether the device is in silent or power-saving mode, and try raising the strength. Haptic implementations can also differ between apps.

- **Background music does not autoplay**

  First confirm that the music master switch, the file and autoplay are all on. Tapping "Play" manually tells you whether the problem is the file or the autoplay conditions.

- **Sound effects play nothing**

  Check the media volume, Do Not Disturb mode and the current audio output device. Touch sound effects and background music each use their own selection and switch.

- **The switch is still on after clearing the file**

  Clearing a file and toggling a feature are two separate things. Turn off the matching master switch to disable it completely.

- **Do multimedia settings affect system sounds**

  No. They do not replace the system ringtone, notification sounds or system fonts. They only manage FinaSU Manager's own feedback.

::::

## Related Pages

- [Theme & Color](/en/setting/theme/)
- [Wallpaper](/en/setting/wallpaper/)
- [SU Log](/en/manager/su-log/)
