package com.medexam.controller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import android.accessibilityservice.GestureDescription

class MedExamService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var debugMode = false
    private var targetPackage = ""
    private var lastKeyEventTime = 0L

    // OCR cache
    private var cachedPositions: MutableMap<String, Point>? = null
    private var isProcessingScreenshot = false

    private val recognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }

    companion object {
        private const val DEBOUNCE_MS = 250L
        private val OPTIONS = listOf("A", "B", "C", "D", "E")

        val KEY_ACTION = mapOf(
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            cachedPositions = null
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val now = System.currentTimeMillis()
        if (now - lastKeyEventTime < DEBOUNCE_MS) return false
        lastKeyEventTime = now

        val action = KEY_ACTION[event.keyCode] ?: return false

        if (debugMode) toast("Key ${event.keyCode} -> $action")

        val root = rootInActiveWindow ?: return false
        val pkg = root.packageName?.toString() ?: ""

        if (debugMode && action == "SUBMIT") {
            toast("包名: $pkg")
            return true
        }

        if (targetPackage.isNotEmpty() && pkg != targetPackage) {
            if (debugMode) toast("非目标App: $pkg")
            return false
        }

        return handleAction(action)
    }

    private fun handleAction(action: String): Boolean {
        return when (action) {
            "A", "B", "C", "D", "E" -> clickOptionOCR(action)
            "PREV", "NEXT", "SUBMIT" -> clickByTextOCR(action)
            "SCROLL_UP" -> scrollScreen(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            "SCROLL_DOWN" -> scrollScreen(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            else -> false
        }
    }

    // ---- OCR-based clicking ----

    private fun clickOptionOCR(letter: String): Boolean {
        // Use cache if available
        cachedPositions?.get(letter)?.let { pos ->
            dispatchTap(pos.x, pos.y)
            return true
        }

        // Trigger OCR capture, tap happens in callback
        triggerOCRCapture()
        return true
    }

    private fun clickByTextOCR(action: String): Boolean {
        // For prev/next/submit, try accessibility first (fast), then OCR
        val root = rootInActiveWindow
        if (root != null) {
            val texts = when (action) {
                "PREV" -> listOf("上一题", "上一页", "上一道", "previous")
                "NEXT" -> listOf("下一题", "下一页", "下一道", "next")
                "SUBMIT" -> listOf("提交", "交卷", "确认", "确定", "submit")
                else -> emptyList()
            }
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
        }
        // Fallback: try OCR for these too (less reliable for nav buttons)
        if (action == "PREV") {
            val root2 = rootInActiveWindow ?: return false
            return scrollScreen(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        if (action == "NEXT") {
            val root2 = rootInActiveWindow ?: return false
            return scrollScreen(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        return false
    }

    private fun triggerOCRCapture() {
        if (isProcessingScreenshot) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            toast("Android 9+ required for OCR")
            return
        }
        isProcessingScreenshot = true

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            result.hardwareBuffer, result.colorSpace
                        )
                        if (bitmap != null) {
                            runOCR(bitmap)
                        }
                    } finally {
                        result.hardwareBuffer.close()
                    }
                }
                override fun onFailure(errorCode: Int) {
                    isProcessingScreenshot = false
                    if (debugMode) toast("Screenshot failed: $errorCode")
                }
            }
        )
    }

    private fun runOCR(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text -> onOCRResult(text) }
            .addOnFailureListener {
                isProcessingScreenshot = false
                if (debugMode) toast("OCR failed")
            }
    }

    private fun onOCRResult(text: Text) {
        val positions = mutableMapOf<String, Point>()
        val navPositions = mutableMapOf<String, Point>()

        for (block in text.textBlocks) {
            for (line in block.lines) {
                val txt = line.text.trim()
                val rect = line.boundingBox ?: continue
                val cx = rect.centerX()
                val cy = rect.centerY()

                // Match options
                for (opt in OPTIONS) {
                    if (txt.length in 1..3 && (
                        txt == opt ||
                        txt.startsWith("$opt.") ||
                        txt.startsWith("$opt、") ||
                        txt.startsWith("$opt ") ||
                        txt == "($opt)" ||
                        txt == "（$opt）")
                    ) {
                        positions[opt] = Point(cx, cy)
                    }
                }

                // Match nav text
                if (txt.contains("上一题") || txt.contains("上一道") || txt.contains("上一页")) {
                    navPositions["PREV"] = Point(cx, cy)
                }
                if (txt.contains("下一题") || txt.contains("下一道") || txt.contains("下一页")) {
                    navPositions["NEXT"] = Point(cx, cy)
                }
                if (txt.contains("提交") || txt.contains("交卷") || txt.contains("确认")) {
                    navPositions["SUBMIT"] = Point(cx, cy)
                }
            }
        }

        cachedPositions = positions

        if (debugMode) {
            toast("OCR: ${positions.keys} options found")
        }

        // Also try to find nav buttons via accessibility
        val root = rootInActiveWindow
        if (root != null && navPositions.isEmpty()) {
            for ((action, texts) in mapOf(
                "PREV" to listOf("上一题", "上一页", "上一道"),
                "NEXT" to listOf("下一题", "下一页", "下一道"),
                "SUBMIT" to listOf("提交", "交卷", "确认", "确定")
            )) {
                for (t in texts) {
                    val nodes = root.findAccessibilityNodeInfosByText(t)
                    for (node in nodes) {
                        if (node.isClickable || node.isCheckable) {
                            val r = Rect()
                            node.getBoundsInScreen(r)
                            navPositions[action] = Point(r.centerX(), r.centerY())
                            break
                        }
                    }
                    if (navPositions[action] != null) break
                }
            }
        }

        isProcessingScreenshot = false
    }

    // ---- Gesture dispatch ----

    private fun dispatchTap(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        dispatchGesture(gesture, null, null)
    }

    // ---- Scroll ----

    private fun scrollScreen(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollables = mutableListOf<AccessibilityNodeInfo>()
        collectScrollable(root, scrollables)
        for (node in scrollables.reversed()) {
            if (node.performAction(action)) return true
        }
        return false
    }

    private fun collectScrollable(node: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
        if (node.isScrollable) out.add(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectScrollable(it, out) }
        }
    }

    // ---- Accessibility fallback ----

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        if ((node.isClickable || node.isCheckable) && node.isEnabled) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        return false
    }

    // ---- Preferences ----

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
