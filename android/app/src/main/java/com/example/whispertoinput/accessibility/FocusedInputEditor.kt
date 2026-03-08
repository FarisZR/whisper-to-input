package com.example.whispertoinput.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo

private const val NEW_CURSOR_POSITION = 1

fun isEditableTarget(node: AccessibilityNodeInfo?): Boolean {
    return node != null && node.isEditable && !isPasswordField(node)
}

fun isPasswordField(node: AccessibilityNodeInfo): Boolean {
    if (node.isPassword) {
        return true
    }
    val inputType = node.inputType
    return inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
        inputType and InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
        inputType and InputType.TYPE_NUMBER_VARIATION_PASSWORD == InputType.TYPE_NUMBER_VARIATION_PASSWORD
}

fun buildUpdatedText(
    currentText: CharSequence?,
    insertedText: String,
    selectionStart: Int,
    selectionEnd: Int,
): CharSequence {
    val original = currentText?.toString().orEmpty()
    val start = if (selectionStart >= 0) selectionStart else original.length
    val end = if (selectionEnd >= 0) selectionEnd else start
    val safeStart = start.coerceIn(0, original.length)
    val safeEnd = end.coerceIn(safeStart, original.length)
    return buildString {
        append(original.substring(0, safeStart))
        append(insertedText)
        append(original.substring(safeEnd))
    }
}

class FocusedInputEditor(
    private val accessibilityService: AccessibilityService,
) {
    fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        val focusedNode = accessibilityService.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return if (isEditableTarget(focusedNode)) {
            focusedNode
        } else {
            null
        }
    }

    fun insertText(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }

        val focusedNode = findFocusedEditableNode() ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val inputMethod: InputMethod = accessibilityService.inputMethod ?: return insertTextWithNode(focusedNode, text)
            val inputConnection = inputMethod.currentInputConnection ?: return insertTextWithNode(focusedNode, text)
            inputConnection.commitText(text, NEW_CURSOR_POSITION, null)
            return true
        }

        return insertTextWithNode(focusedNode, text)
    }

    private fun insertTextWithNode(
        focusedNode: AccessibilityNodeInfo,
        insertedText: String,
    ): Boolean {
        val updatedText = buildUpdatedText(
            currentText = focusedNode.text,
            insertedText = insertedText,
            selectionStart = focusedNode.textSelectionStart,
            selectionEnd = focusedNode.textSelectionEnd,
        )
        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                updatedText,
            )
        }
        return focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }
}
