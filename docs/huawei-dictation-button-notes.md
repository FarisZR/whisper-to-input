# Huawei dictation button notes

## Goal

Identify the hardware keyboard button that triggers Huawei/Celia dictation so another app can later react to it, likely via an accessibility service.

## Findings

- The Huawei dictation button maps to Android `KEYCODE_VOICE_INPUT`.
- Android key code: `204`
- Raw hardware scan code observed from the keyboard: `0x700b0`
- Input device: `HUAWEI Glide Keyboard Consumer Control`
- Input event device: `/dev/input/event10`

## Behavior

- A short press does not trigger dictation.
- A press and hold is required.
- The button only triggers the dictation flow when a text input is focused.
- When triggered successfully, the system routes through the input method path and Celia starts voice input.

Observed log evidence included:

- `InputMethodManager.dealPressVoiceInputKey`
- `CeliaKeyboard_LatinIME: show global voice for key`
- `CeliaKeyboard_GlobalVoiceView: showSpeechView`
- `CeliaKeyboard_GlobalVoiceView: start voiceInput`

## Accessibility service implementation note

The best candidate to monitor is:

- `keyCode == 204` (`KEYCODE_VOICE_INPUT`)

Expected behavior to mirror:

- listen for the key event
- detect long-press behavior
- only act when an editable text field is focused

## Celia app package

The Huawei/Celia input method package identified on the device was:

- `com.huawei.ohos.inputmethod`

This package came from a system app path containing `CeliaKeyboard`.

## ADB commands used

Find the package:

```bash
adb shell pm list packages | grep -i celia
adb shell dumpsys package | grep -i -A 2 -B 2 celia
```

Disable Celia:

```bash
adb shell pm disable-user com.huawei.ohos.inputmethod
```

Re-enable Celia:

```bash
adb shell pm enable com.huawei.ohos.inputmethod
```

Inspect keyboard devices:

```bash
adb shell dumpsys input
adb shell getevent -p /dev/input/event10
```

Watch the raw hardware event:

```bash
adb shell getevent -lt -l /dev/input/event9 /dev/input/event10
```

The relevant raw event observed was:

```text
EV_MSC MSC_SCAN 000700b0
```

## Conclusion

For future app integration, start by handling `KEYCODE_VOICE_INPUT` (`204`) as the Huawei dictation key. Keep `0x700b0` documented as the raw hardware scan code from the physical keyboard.
