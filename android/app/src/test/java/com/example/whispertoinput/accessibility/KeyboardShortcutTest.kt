package com.example.whispertoinput.accessibility

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardShortcutTest {
    @Test
    fun matchesShortcutWhenKeyAndModifiersAlign() {
        val shortcut = KeyboardShortcut(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

        assertTrue(matchesKeyboardShortcut(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_LEFT_ON, shortcut))
    }

    @Test
    fun rejectsShortcutWhenModifiersDiffer() {
        val shortcut = KeyboardShortcut(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)

        assertFalse(matchesKeyboardShortcut(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_LEFT_ON, shortcut))
    }

    @Test
    fun formatsShortcutForDisplay() {
        val shortcut = defaultKeyboardShortcut()

        assertEquals("Ctrl+Shift+Space", formatKeyboardShortcut(shortcut))
    }

    @Test
    fun capturesShortcutWhenModifierIsPresent() {
        val shortcut = captureKeyboardShortcut(KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_SHIFT_LEFT_ON)

        assertEquals("Ctrl+Shift+K", formatKeyboardShortcut(shortcut!!))
    }

    @Test
    fun rejectsShortcutWithoutModifier() {
        val shortcut = captureKeyboardShortcut(KeyEvent.KEYCODE_K, 0)

        assertEquals(null, shortcut)
    }

    @Test
    fun rejectsModifierOnlyShortcut() {
        val shortcut = captureKeyboardShortcut(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_LEFT_ON)

        assertEquals(null, shortcut)
    }
}
