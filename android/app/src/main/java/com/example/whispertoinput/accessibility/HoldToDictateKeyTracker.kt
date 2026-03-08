package com.example.whispertoinput.accessibility

class HoldToDictateKeyTracker(
    private val targetKeyCode: Int,
) {
    private var keyPressed: Boolean = false
    private var dictationStarted: Boolean = false

    fun onKeyDown(keyCode: Int): Boolean {
        if (keyCode != targetKeyCode || keyPressed) {
            return false
        }
        keyPressed = true
        dictationStarted = false
        return true
    }

    fun onLongPress(): Boolean {
        if (!keyPressed || dictationStarted) {
            return false
        }
        dictationStarted = true
        return true
    }

    fun onKeyUp(keyCode: Int): Boolean {
        if (keyCode != targetKeyCode || !keyPressed) {
            return false
        }
        val shouldStop = dictationStarted
        keyPressed = false
        dictationStarted = false
        return shouldStop
    }

    fun cancel() {
        keyPressed = false
        dictationStarted = false
    }

    fun isPressed(): Boolean = keyPressed
}
