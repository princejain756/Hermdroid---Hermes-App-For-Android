package com.princejain.hermroid.automation

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

class HermroidAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        active = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (active.get() === this) active.clear()
        super.onDestroy()
    }

    fun global(action: AndroidGlobalAction): Boolean = performGlobalAction(
        when (action) {
            AndroidGlobalAction.BACK -> GLOBAL_ACTION_BACK
            AndroidGlobalAction.HOME -> GLOBAL_ACTION_HOME
            AndroidGlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            AndroidGlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
        },
    )

    fun tapText(label: String): Boolean {
        val nodes = rootInActiveWindow?.findAccessibilityNodeInfosByText(label).orEmpty()
        val target = nodes.firstOrNull { it.isVisibleToUser && it.isEnabled } ?: return false
        return target.clickSelfOrParent()
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: root.walk().firstOrNull { it.isEditable && it.isVisibleToUser && it.isEnabled }
            ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    companion object {
        private var active = WeakReference<HermroidAccessibilityService>(null)
        fun current(): HermroidAccessibilityService? = active.get()
    }
}

private fun AccessibilityNodeInfo.clickSelfOrParent(): Boolean {
    var node: AccessibilityNodeInfo? = this
    while (node != null) {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        node = node.parent
    }
    return false
}

private fun AccessibilityNodeInfo.walk(): Sequence<AccessibilityNodeInfo> = sequence {
    yield(this@walk)
    for (index in 0 until childCount) {
        val child = getChild(index) ?: continue
        yieldAll(child.walk())
    }
}
