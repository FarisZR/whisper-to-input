# Huawei hardware dictation takeover

This app can take over the Huawei hardware dictation shortcut and replace the built-in Celia dictation UI with a floating Whisper overlay.

## What it does

- Long-press the Huawei dictation key while a text field is focused.
- The current keyboard hides.
- A draggable floating overlay appears in the bottom right.
- Recording continues only while the key stays pressed.
- Releasing the key stops recording, sends audio to the configured Whisper backend, inserts the transcript, dismisses the overlay, and restores the prior keyboard.

If the hold is too short, the app shows `Recording too short.` and restores the keyboard without sending audio.

## Setup

1. Install the app and open `Whisper To Input`.
2. Configure the speech-to-text backend.
3. Enable the `Whisper Input` keyboard in Android keyboard settings.
4. Enable `Huawei dictation takeover` in Android accessibility settings.
5. Focus a text field and long-press the Huawei hardware dictation key.

The Huawei keyboard research notes are in `docs/huawei-dictation-button-notes.md`.

## Replacing the built-in Huawei keyboard dictation path

The Huawei/Celia keyboard package observed during testing was:

- `com.huawei.ohos.inputmethod`

Before disabling it, make sure you already enabled another keyboard. If you disable Celia without another enabled keyboard, you can lock yourself out of text input.

Use ADB to disable the package:

```bash
adb shell pm disable-user com.huawei.ohos.inputmethod
```

Re-enable it later with:

```bash
adb shell pm enable com.huawei.ohos.inputmethod
```

If your device uses a different package name, rediscover it first:

```bash
adb shell pm list packages | grep -i celia
adb shell dumpsys package | grep -i -A 2 -B 2 celia
```

## Troubleshooting

- `Nothing happens when I hold the key`: confirm the accessibility service is enabled and the focused control is editable.
- `The overlay appears but text is not inserted`: some apps expose limited editor hooks; try another text field or keyboard.
- `The keyboard does not come back`: tap the focused field again, then reopen the keyboard manually.
- `The wrong service still opens`: disable the Huawei/Celia package or switch away from the Huawei keyboard.
