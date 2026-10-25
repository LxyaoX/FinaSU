---
title: Sound Effects
icon: material-symbols:volume-up
description: FinaSU touch sound effects, boot sound effect, preset resources, feedback scope and theme export boundaries
createTime: 2026/10/24 00:00:00
permalink: /en/theme/global/sound/
copyright:
  creation: original
  license: CC-BY-4.0
  author:
    name: LyraVoid
---

# Sound Effects

<Badge text="Touch and boot are separate" type="tip" /> <Badge text="Local or preset" type="info" /> <Badge text="Boot sound is not exported with themes" type="warning" />

FinaSU has two independent sound effect features:

- **Touch sound effects**: played when interface controls are tapped; can cover all touches or navigation controls only;
- **Boot sound effect**: played when the Manager opens; currently not exported with a `.fpt` theme.

## Touch Sound Effects

:::: table Touch sound effect settings

| Setting | Behavior |
| --- | --- |
| Master switch | No touch sound effect plays when off |
| Source | Local file or preset sound effect |
| Local file | Imported through the `audio/*` picker |
| Preset sound effects | `Zako`, `Zako2`, `Imoi`, `Ehe`, `Baka`, `Ciallo` |
| Feedback scope | `global` (all touches) or `bottom_bar` (navigation controls) |
| Preview | Plays once with the current settings |

::::

The corresponding theme JSON:

```json
{
  "isSoundEffectEnabled": true,
  "soundEffectFilename": "sound_effect_custom.wav",
  "soundEffectScope": "bottom_bar"
}
```

A preset sound effect is exported as:

```text
sound_effect_preset_<Preset>.wav
```

A local sound effect uses its actual file name after import. During import the same-named ZIP entry is looked up first, then copied into the Manager sound effect directory and the source type is restored to local file.

## Boot Sound Effect

The boot sound effect has its own switch, local or preset source and preview button. The current presets are:

```text
Zako
Hentai
```

It is not an export item of `.fpt`, and importing a theme does not overwrite the boot sound effect. `isSoundEffectEnabled` and `soundEffectScope` in a theme describe touch sound effects only.

:::: warning Do not mix up the two switches

When touch sound effects are off, the boot sound effect can still be on separately, and the other way around. When checking "no sound when opening the app", look at the boot sound effect first; when checking "no sound when tapping a button", check the touch sound effect and the feedback scope.

::::

## Playback Scope

`global` means the global touch feedback path that responds to the user. It does not mean changing the Android system touch sound and does not affect other apps.

`bottom_bar` covers navigation controls only, which is useful to avoid a sound for every interaction on the whole page. If the current action is outside the navigation area, no sound is the expected behavior in this mode.

## Common Questions

::::: collapse accordion

:::: collapse A local sound effect has no sound
Check whether the system can decode the file, the master switch and the feedback scope. Clear it and pick the file again instead of keeping a corrupted old file.
::::

:::: collapse The theme imported a sound effect but the device still uses its own settings
The boot sound effect and vibration do not enter a theme. Touch sound effects also require the theme resource and `soundEffectFilename` to match exactly.
::::

:::: collapse A preset sound effect does not play on another device
Preset resources are exported with the theme as WAV files, but playback still depends on the media decoding capability of the target device.
::::

:::: collapse Can the volume be adjusted
The current touch sound effect has no separate volume slider. It uses the device media volume and the gain of the resource itself; when you need a lower loudness, use an audio file that is already quieter.
::::

:::::

## Related Pages

- [Multimedia Settings](/en/setting/multimedia/): sound effect entries and operations.
- [Background Music](/en/theme/global/music/): the independent music player.
- [Haptics](/en/theme/global/haptic/): haptic feedback that is not a sound effect.
- [Theme Package Structure](/en/developer/theme-package/): sound effect resource naming and validation.
