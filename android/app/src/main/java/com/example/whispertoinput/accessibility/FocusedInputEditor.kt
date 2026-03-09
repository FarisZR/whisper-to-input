package com.example.whispertoinput.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.InputMethod
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
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
    private var lastFocusedEditableNode: AccessibilityNodeInfo? = null

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !shouldTrackFocusEvent(event)) {
            return
        }
        val source = event.source ?: return
        try {
            val candidate = findEditableCandidate(source)
            if (candidate != null) {
                rememberFocusedNode(candidate)
            }
        } finally {
            source.recycle()
        }
    }

    fun findFocusedEditableNode(allowRememberedNode: Boolean = true): AccessibilityNodeInfo? {
        if (allowRememberedNode) {
            val cachedNode = refreshedEditableNode(lastFocusedEditableNode)
            if (cachedNode != null) {
                lastFocusedEditableNode = cachedNode
                return cachedNode
            }
        }

        return findLiveFocusedEditableNode()
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

    private fun rememberFocusedNode(node: AccessibilityNodeInfo?) {
        val editableNode = refreshedEditableNode(node) ?: findEditableCandidate(node) ?: return
        clearRememberedNode()
        lastFocusedEditableNode = editableNode
    }

    private fun clearRememberedNode() {
        lastFocusedEditableNode?.recycle()
        lastFocusedEditableNode = null
    }

    private fun refreshedEditableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val candidate = node ?: return null
        if (!candidate.refresh()) {
            return null
        }
        return candidate.takeIf(::isEditableTarget)
    }

    private fun findLiveFocusedEditableNode(): AccessibilityNodeInfo? {
        val rootNode = accessibilityService.rootInActiveWindow ?: run {
            clearRememberedNode()
            return null
        }

        try {
            val inputFocusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            val inputFocusedNode = findEditableCandidate(inputFocusNode)
            inputFocusNode?.recycle()
            if (inputFocusedNode != null) {
                rememberFocusedNode(inputFocusedNode)
                return inputFocusedNode
            }

            val accessibilityFocusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            val accessibilityFocusedNode = findEditableCandidate(accessibilityFocusNode)
            accessibilityFocusNode?.recycle()
            if (accessibilityFocusedNode != null) {
                rememberFocusedNode(accessibilityFocusedNode)
                return accessibilityFocusedNode
            }

            val descendantFocusedNode = findFocusedEditableDescendant(rootNode)
            if (descendantFocusedNode != null) {
                rememberFocusedNode(descendantFocusedNode)
                return descendantFocusedNode
            }

            clearRememberedNode()
            return null
        } finally {
            rootNode.recycle()
        }
    }

    private fun findEditableCandidate(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var currentNode: AccessibilityNodeInfo? = node
        var acquiredNode: AccessibilityNodeInfo? = null

        while (currentNode != null) {
            val parent = currentNode.parent
            if (isEditableTarget(currentNode)) {
                val result = AccessibilityNodeInfo.obtain(currentNode)
                acquiredNode?.recycle()
                return result
            }
            acquiredNode?.recycle()
            acquiredNode = parent
            currentNode = acquiredNode
        }

        acquiredNode?.recycle()
        return null
    }

    private fun findFocusedEditableDescendant(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val isRootNode = node === rootNode

            if (isEditableTarget(node) && (node.isFocused || node.isAccessibilityFocused)) {
                val result = AccessibilityNodeInfo.obtain(node)
                queue.removeIf { it === rootNode }
                queue.forEach { it.recycle() }
                if (!isRootNode) {
                    node.recycle()
                }
                return result
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }

            if (!isRootNode) {
                node.recycle()
            }
        }

        return null
    }

    private fun shouldTrackFocusEvent(event: AccessibilityEvent): Boolean {
        return event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
    }
}
