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
        val candidate = findEditableCandidate(event.source)
        if (candidate != null) {
            rememberFocusedNode(candidate)
        }
    }

    fun findFocusedEditableNode(allowRememberedNode: Boolean = true): AccessibilityNodeInfo? {
        if (allowRememberedNode) {
            val cachedNode = refreshedEditableNode(lastFocusedEditableNode)
            if (cachedNode != null) {
                lastFocusedEditableNode = cachedNode
                return AccessibilityNodeInfo.obtain(cachedNode)
            }
        }

        val liveNode = findLiveFocusedEditableNode() ?: return null
        return AccessibilityNodeInfo.obtain(liveNode)
    }

    fun insertText(text: String): Boolean {
        if (text.isEmpty()) {
            return false
        }

        val focusedNode = findFocusedEditableNode() ?: return false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val inputMethod: InputMethod? = accessibilityService.inputMethod
                val inputConnection = inputMethod?.currentInputConnection
                if (inputConnection != null) {
                    inputConnection.commitText(text, NEW_CURSOR_POSITION, null)
                    return true
                }
            }

            return insertTextWithNode(focusedNode, text)
        } finally {
            focusedNode.recycle()
        }
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

    private fun rememberFocusedNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        val editableNode = refreshedEditableNode(node) ?: findEditableCandidate(node) ?: return null
        clearRememberedNode()
        lastFocusedEditableNode = editableNode
        return editableNode
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

        val inputFocusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val inputFocusedNode = findEditableCandidate(inputFocusNode)
        if (inputFocusedNode != null) {
            rootNode.recycle()
            return rememberFocusedNode(inputFocusedNode)
        }

        val accessibilityFocusNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        val accessibilityFocusedNode = findEditableCandidate(accessibilityFocusNode)
        if (accessibilityFocusedNode != null) {
            rootNode.recycle()
            return rememberFocusedNode(accessibilityFocusedNode)
        }

        val descendantFocusedNode = findFocusedEditableDescendant(rootNode)
        if (descendantFocusedNode != null) {
            rootNode.recycle()
            return rememberFocusedNode(descendantFocusedNode)
        }

        rootNode.recycle()
        clearRememberedNode()
        return null
    }

    private fun findEditableCandidate(
        node: AccessibilityNodeInfo?,
        recycleInputNode: Boolean = true,
    ): AccessibilityNodeInfo? {
        var currentNode: AccessibilityNodeInfo? = node
        var shouldRecycleCurrentNode = recycleInputNode

        while (currentNode != null) {
            if (isEditableTarget(currentNode)) {
                val result = AccessibilityNodeInfo.obtain(currentNode)
                if (shouldRecycleCurrentNode) {
                    currentNode.recycle()
                }
                return result
            }

            val parent = currentNode.parent
            if (shouldRecycleCurrentNode) {
                currentNode.recycle()
            }
            currentNode = parent
            shouldRecycleCurrentNode = true
        }

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
                if (!isRootNode) {
                    node.recycle()
                }
                recycleQueuedNodes(queue)
                return result
            }

            for (index in 0 until node.childCount) {
                val child = node.getChild(index)
                if (child != null) {
                    queue.addLast(child)
                }
            }

            if (!isRootNode) {
                node.recycle()
            }
        }

        return null
    }

    private fun recycleQueuedNodes(queue: ArrayDeque<AccessibilityNodeInfo>) {
        while (queue.isNotEmpty()) {
            queue.removeFirst().recycle()
        }
    }

    private fun shouldTrackFocusEvent(event: AccessibilityEvent): Boolean {
        return event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
    }
}
