# Overlay shortcut dictation

This app can open a floating Whisper dictation overlay from a configurable keyboard shortcut.

## What it does

- Press the configured keyboard shortcut while a text field is focused.
- The current keyboard hides.
- A draggable floating overlay appears in the bottom right.
- Recording continues until you press the same shortcut again.
- Pressing the shortcut again stops recording, sends audio to the configured Whisper backend, inserts the transcript, dismisses the overlay, and restores the previous keyboard.

If the recording is too short, the app shows `Recording too short.` and restores the keyboard without sending audio.

## Setup

1. Install the app and open `Whisper To Input`.
2. Configure the speech-to-text backend.
3. Enable the `Whisper Input` keyboard in Android keyboard settings.
4. Enable `Overlay shortcut dictation` in Android accessibility settings.
5. Capture your preferred shortcut in the app settings.
6. Focus a text field and press the configured shortcut to start dictation.

## Shortcut capture

- Use at least one modifier key like `Ctrl`, `Alt`, `Shift`, or `Meta`.
- Press `Escape` while capturing to cancel without changing the shortcut.
- Avoid plain letter shortcuts, since they would interfere with normal typing.

## Troubleshooting

- `Nothing happens when I press the shortcut`: confirm the accessibility service is enabled and a text field is focused.
- `The overlay appears but text is not inserted`: some apps expose limited editor hooks; try another text field or keyboard.
- `The keyboard does not come back`: tap the focused field again, then reopen the keyboard manually.
