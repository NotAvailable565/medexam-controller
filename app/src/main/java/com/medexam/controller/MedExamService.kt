package com.medexam.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class MedExamService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var debugMode = false
    private var targetPackage = ""
    private var lastKeyEventTime = 0L

    private val prevTexts = listOf("上一题", "上一页", "上一道", "previous")
    private val nextTexts = listOf("下一题", "下一页", "下一道", "next")
    private val submitTexts = listOf("提交", "交卷", "确认", "确定", "submit")
    private val favoriteTexts = listOf("收藏", "标记", "star")

    companion object {
        private const val DEBOUNCE_MS = 250L

        val KEY_ACTION = mapOf(
            // D-Input mode (gamepad standard)
            KeyEvent.KEYCODE_BUTTON_A to "A",
            KeyEvent.KEYCODE_BUTTON_B to "B",
            KeyEvent.KEYCODE_BUTTON_X to "C",
            KeyEvent.KEYCODE_BUTTON_Y to "D",
            KeyEvent.KEYCODE_BUTTON_R1 to "E",
            KeyEvent.KEYCODE_BUTTON_L1 to "SUBMIT",
            KeyEvent.KEYCODE_BUTTON_START to "NEXT",
            KeyEvent.KEYCODE_BUTTON_SELECT to "PREV",
            KeyEvent.KEYCODE_DPAD_UP to "SCROLL_UP",
            KeyEvent.KEYCODE_DPAD_DOWN to "SCROLL_DOWN",
            KeyEvent.KEYCODE_DPAD_LEFT to "PREV",
            KeyEvent.KEYCODE_DPAD_RIGHT to "NEXT",
            // Keyboard mode fallback
            KeyEvent.KEYCODE_ENTER to "SUBMIT",
            KeyEvent.KEYCODE_SPACE to "NEXT",
            KeyEvent.KEYCODE_DEL to "PREV",
            KeyEvent.KEYCODE_1 to "A",
            KeyEvent.KEYCODE_2 to "B",
            KeyEvent.KEYCODE_3 to "C",
            KeyEvent.KEYCODE_4 to "D",
            KeyEvent.KEYCODE_5 to "E",
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        setServiceInfo(info)
        loadPrefs()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = System.currentTimeMillis()
        if (now - lastKeyEventTime < DEBOUNCE_MS) return false
        lastKeyEventTime = now

        val action = KEY_ACTION[event.keyCode] ?: return false

        if (debugMode) {
            toast("Key ${event.keyCode} -> $action")
        }

        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: ""

        if (debugMode && action == "SUBMIT") {
            toast("当前包名: $pkg")
            return true
        }

        if (targetPackage.isNotEmpty() && pkg != targetPackage) {
            if (debugMode) toast("非目标App ($pkg)")
            return false
        }

        return handleAction(action, root)
    }

    private fun handleAction(action: String, root: AccessibilityNodeInfo): Boolean {
        return when (action) {
            "A", "B", "C", "D", "E" -> clickOption(action, root)
            "PREV" -> clickByTextList(prevTexts, root)
            "NEXT" -> clickByTextList(nextTexts, root)
            "SUBMIT" -> clickByTextList(submitTexts, root)
            "SCROLL_UP" -> scrollInDir(root, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "SCROLL_DOWN" -> scrollInDir(root, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            "FAVORITE" -> clickByTextList(favoriteTexts, root)
            else -> false
        }
    }

    private fun clickOption(letter: String, root: AccessibilityNodeInfo): Boolean {
        val patterns = listOf(letter, "$letter.", "$letter、", "$letter ", "($letter)")
        for (pat in patterns) {
            val nodes = root.findAccessibilityNodeInfosByText(pat)
            for (node in nodes) {
                if (tryClick(node)) return true
                var p = node.parent
                while (p != null) {
                    if (tryClick(p)) return true
                    p = p.parent
                }
            }
        }
        return false
    }

    private fun clickByTextList(texts: List<String>, root: AccessibilityNodeInfo): Boolean {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            for (node in nodes) {
                if (tryClick(node)) return true
                var p = node.parent
                while (p != null) {
                    if (tryClick(p)) return true
                    p = p.parent
                }
            }
        }
        return false
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        if ((node.isClickable || node.isCheckable) && node.isEnabled) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    private fun scrollInDir(root: AccessibilityNodeInfo, action: Int): Boolean {
        val scrollables = findScrollableNodes(root)
        for (node in scrollables.reversed()) {
            if (node.performAction(action)) return true
        }
        return false
    }

    private fun findScrollableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (node.isScrollable) result.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { result.addAll(findScrollableNodes(it)) }
        }
        return result
    }

    private fun loadPrefs() {
        val prefs = getSharedPreferences("medexam", MODE_PRIVATE)
        targetPackage = prefs.getString("target_package", "") ?: ""
        debugMode = prefs.getBoolean("debug_mode", false)
    }

    fun refresh() { loadPrefs() }

    private fun toast(msg: String) {
        handler.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    override fun onInterrupt() {}
}
