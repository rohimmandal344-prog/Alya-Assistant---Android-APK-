package com.example.device

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.core.logger.AlyaLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlyaAutomationService : AccessibilityService() {

    companion object {
        private const val TAG = AlyaLogger.TAG_ACCESSIBILITY

        @Volatile
        var instance: AlyaAutomationService? = null
            private set

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _lastActionStatus = MutableStateFlow<String>("Service idle")
        val lastActionStatus: StateFlow<String> = _lastActionStatus.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        _lastActionStatus.value = "Alya Automation Service connected"
        AlyaLogger.i(TAG, "AccessibilityService connected successfully")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceActive.value = false
            _lastActionStatus.value = "Service disconnected"
        }
        AlyaLogger.i(TAG, "AccessibilityService destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive monitoring only when needed for accessibility verification
    }

    override fun onInterrupt() {
        AlyaLogger.w(TAG, "AccessibilityService interrupted")
    }

    // --- Advanced Navigation & Node Operations ---

    fun findNodesByText(text: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        return root.findAccessibilityNodeInfosByText(text)
    }

    fun findNodesByViewId(viewId: String): List<AccessibilityNodeInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        return root.findAccessibilityNodeInfosByViewId(viewId)
    }

    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                _lastActionStatus.value = "Click ${if (success) "succeeded" else "failed"} on node"
                AlyaLogger.i(TAG, "Click action executed on clickable node: $success")
                return success
            }
            current = current.parent
        }
        return false
    }

    fun clickElementByText(query: String): Boolean {
        val nodes = findNodesByText(query)
        for (node in nodes) {
            if (clickNode(node)) {
                _lastActionStatus.value = "Clicked element containing '$query'"
                return true
            }
        }
        _lastActionStatus.value = "Could not find clickable element for '$query'"
        return false
    }

    fun scroll(forward: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val targetNode = findScrollableNode(root)
        if (targetNode != null) {
            val success = targetNode.performAction(action)
            _lastActionStatus.value = "Scroll ${if (forward) "down/forward" else "up/backward"}: $success"
            AlyaLogger.i(TAG, "Scroll action performed: $success")
            return success
        }
        _lastActionStatus.value = "No scrollable container found on current screen"
        return false
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isScrollable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findScrollableNode(child)
            if (result != null) return result
        }
        return null
    }

    fun performGlobal(globalAction: Int): Boolean {
        val success = performGlobalAction(globalAction)
        _lastActionStatus.value = "Global action $globalAction: $success"
        AlyaLogger.i(TAG, "Global action $globalAction performed: $success")
        return success
    }

    // --- Advanced Gestures ---

    fun dispatchTap(x: Float, y: Float, onComplete: (Boolean) -> Unit) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                _lastActionStatus.value = "Tap gesture completed at ($x, $y)"
                AlyaLogger.i(TAG, "Tap gesture completed at ($x, $y)")
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                _lastActionStatus.value = "Tap gesture cancelled at ($x, $y)"
                AlyaLogger.w(TAG, "Tap gesture cancelled at ($x, $y)")
                onComplete(false)
            }
        }, null)
    }

    fun dispatchSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300,
        onComplete: (Boolean) -> Unit
    ) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                _lastActionStatus.value = "Swipe completed from ($startX, $startY) to ($endX, $endY)"
                AlyaLogger.i(TAG, "Swipe completed")
                onComplete(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                _lastActionStatus.value = "Swipe cancelled"
                AlyaLogger.w(TAG, "Swipe gesture cancelled")
                onComplete(false)
            }
        }, null)
    }

    // --- Advanced Text Editing ---

    fun setNodeText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (!node.isEditable && !node.isFocused) {
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        _lastActionStatus.value = "Set text: $success"
        AlyaLogger.i(TAG, "Set text on editable node: $success")
        return success
    }

    fun typeTextIntoFocusedOrFirstEditable(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && setNodeText(focused, text)) {
            return true
        }
        val editable = findFirstEditableNode(root)
        if (editable != null && setNodeText(editable, text)) {
            return true
        }
        _lastActionStatus.value = "No editable text input found on screen"
        return false
    }

    private fun findFirstEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val result = findFirstEditableNode(child)
            if (result != null) return result
        }
        return null
    }

    fun readVisibleScreenText(): String {
        val root = rootInActiveWindow ?: return "Screen content unavailable"
        val sb = StringBuilder()
        collectNodeText(root, sb)
        return sb.toString().trim()
    }

    private fun collectNodeText(node: AccessibilityNodeInfo, sb: StringBuilder) {
        val text = node.text ?: node.contentDescription
        if (!text.isNullOrBlank()) {
            sb.append(text).append("\n")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectNodeText(child, sb)
        }
    }

    /**
     * Traverses visible screen nodes via BFS to locate and click any Settings Switch or Toggle widget.
     */
    fun toggleSettingSwitch(): Boolean {
        val root = rootInActiveWindow ?: return false
        val queue = java.util.LinkedList<AccessibilityNodeInfo>()
        queue.add(root)

        while (!queue.isEmpty()) {
            val node = queue.poll() ?: continue
            val className = node.className?.toString() ?: ""
            if (className.contains("Switch") || className.contains("ToggleButton") || node.isCheckable) {
                if (node.isClickable) {
                    val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    _lastActionStatus.value = "Toggled settings switch: $success"
                    AlyaLogger.i(TAG, "Successfully clicked settings switch via automation")
                    return success
                } else {
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            val success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            _lastActionStatus.value = "Toggled settings switch parent: $success"
                            AlyaLogger.i(TAG, "Successfully clicked parent of settings switch")
                            return success
                        }
                        parent = parent.parent
                    }
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return false
    }
}
