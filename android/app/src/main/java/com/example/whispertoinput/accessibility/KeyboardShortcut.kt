package com.example.whispertoinput.accessibility

import android.view.KeyEvent
import androidx.datastore.preferences.core.Preferences
import com.example.whispertoinput.SHORTCUT_KEY_CODE
import com.example.whispertoinput.SHORTCUT_MODIFIERS

data class KeyboardShortcut(
    val keyCode: Int,
    val modifiers: Int,
)

fun defaultKeyboardShortcut(): KeyboardShortcut {
    return KeyboardShortcut(
        keyCode = KeyEvent.KEYCODE_SPACE,
        modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON,
    )
}

fun normalizeShortcutModifiers(metaState: Int): Int {
    var normalized = 0
    if (metaState and KeyEvent.META_CTRL_MASK != 0) {
        normalized = normalized or KeyEvent.META_CTRL_ON
    }
    if (metaState and KeyEvent.META_ALT_MASK != 0) {
        normalized = normalized or KeyEvent.META_ALT_ON
    }
    if (metaState and KeyEvent.META_SHIFT_MASK != 0) {
        normalized = normalized or KeyEvent.META_SHIFT_ON
    }
    if (metaState and KeyEvent.META_META_MASK != 0) {
        normalized = normalized or KeyEvent.META_META_ON
    }
    return normalized
}

fun isModifierKey(keyCode: Int): Boolean {
    return keyCode == KeyEvent.KEYCODE_SHIFT_LEFT ||
        keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
        keyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
        keyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
        keyCode == KeyEvent.KEYCODE_ALT_LEFT ||
        keyCode == KeyEvent.KEYCODE_ALT_RIGHT ||
        keyCode == KeyEvent.KEYCODE_META_LEFT ||
        keyCode == KeyEvent.KEYCODE_META_RIGHT
}

fun matchesKeyboardShortcut(event: KeyEvent, shortcut: KeyboardShortcut): Boolean {
    return matchesKeyboardShortcut(event.keyCode, event.metaState, shortcut)
}

fun matchesKeyboardShortcut(keyCode: Int, metaState: Int, shortcut: KeyboardShortcut): Boolean {
    return keyCode == shortcut.keyCode &&
        normalizeShortcutModifiers(metaState) == shortcut.modifiers
}

fun captureKeyboardShortcut(keyCode: Int, metaState: Int): KeyboardShortcut? {
    if (isModifierKey(keyCode)) {
        return null
    }
    val modifiers = normalizeShortcutModifiers(metaState)
    if (modifiers == 0) {
        return null
    }
    return KeyboardShortcut(keyCode, modifiers)
}

fun formatKeyboardShortcut(shortcut: KeyboardShortcut): String {
    val parts = mutableListOf<String>()
    if (shortcut.modifiers and KeyEvent.META_CTRL_ON != 0) {
        parts.add("Ctrl")
    }
    if (shortcut.modifiers and KeyEvent.META_ALT_ON != 0) {
        parts.add("Alt")
    }
    if (shortcut.modifiers and KeyEvent.META_SHIFT_ON != 0) {
        parts.add("Shift")
    }
    if (shortcut.modifiers and KeyEvent.META_META_ON != 0) {
        parts.add("Meta")
    }
    parts.add(
        keyCodeLabel(shortcut.keyCode),
    )
    return parts.joinToString("+")
}

fun Preferences.toKeyboardShortcut(): KeyboardShortcut {
    val defaultShortcut = defaultKeyboardShortcut()
    return KeyboardShortcut(
        keyCode = this[SHORTCUT_KEY_CODE] ?: defaultShortcut.keyCode,
        modifiers = this[SHORTCUT_MODIFIERS] ?: defaultShortcut.modifiers,
    )
}

private fun keyCodeLabel(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_TAB -> "Tab"
        KeyEvent.KEYCODE_ESCAPE -> "Escape"
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> {
            ('A'.code + (keyCode - KeyEvent.KEYCODE_A)).toChar().toString()
        }
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
            ('0'.code + (keyCode - KeyEvent.KEYCODE_0)).toChar().toString()
        }
        else -> "Key $keyCode"
    }
}
