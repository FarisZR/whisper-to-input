package com.example.whispertoinput.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusedInputEditorTextTest {
    @Test
    fun buildUpdatedTextReplacesSelection() {
        val updatedText = buildUpdatedText(
            currentText = "Hello world",
            insertedText = "Whisper",
            selectionStart = 6,
            selectionEnd = 11,
        )

        assertEquals("Hello Whisper", updatedText)
    }

    @Test
    fun buildUpdatedTextAppendsWhenSelectionMissing() {
        val updatedText = buildUpdatedText(
            currentText = "Hello",
            insertedText = " there",
            selectionStart = -1,
            selectionEnd = -1,
        )

        assertEquals("Hello there", updatedText)
    }
}
