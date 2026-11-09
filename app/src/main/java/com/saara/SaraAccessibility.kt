package com.saara

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class SaraAccessibility : AccessibilityService() {

    companion object {
        @Volatile var instance: SaraAccessibility? = null

        // Returns visible text of the current screen (capped)
        fun screenText(): String = instance?.collectText() ?: ""
    }

    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; super.onDestroy() }

    fun collectText(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        walk(root, sb)
        return sb.toString().take(4000)
    }

    private fun walk(n: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (n == null || sb.length > 4000) return
        n.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until n.childCount) walk(n.getChild(i), sb)
    }

    // Clicks the first clickable node whose text is long enough to be a video title
    fun clickFirstLongTitle(): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findClickable(root) ?: return false
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun findClickable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        val t = n.text?.toString() ?: ""
        if (n.isClickable && t.length > 15) return n
        for (i in 0 until n.childCount) {
            val r = findClickable(n.getChild(i))
            if (r != null) return r
        }
        return null
    }
}
