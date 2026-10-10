package com.kartik.jarvis

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisService : AccessibilityService() {

    companion object {
        var instance: JarvisService? = null
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (found in root.findAccessibilityNodeInfosByText(text)) {
            var node: AccessibilityNodeInfo? = found
            while (node != null && !node.isClickable) node = node.parent
            if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val box = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(down: Boolean): Boolean {
        val target = findScrollable(rootInActiveWindow ?: return false) ?: return false
        return target.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findScrollable(child)?.let { return it }
        }
        return null
    }

    fun readScreen(): String {
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { sb.append(it).append(". ") }
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(rootInActiveWindow)
        return sb.toString().take(500)
    }
}
