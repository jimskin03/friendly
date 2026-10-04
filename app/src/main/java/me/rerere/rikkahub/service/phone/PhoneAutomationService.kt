package me.rerere.rikkahub.service.phone

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "PhoneAutomationService"

data class ScreenNodeInfo(
    val id: Int,
    val text: String,
    val description: String,
    val viewId: String,
    val className: String,
    val clickable: Boolean,
    val editable: Boolean,
    val scrollable: Boolean,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val centerX: Int,
    val centerY: Int,
)

data class ScreenInspectionResult(
    val packageName: String,
    val windowTitle: String,
    val interactiveElements: List<ScreenNodeInfo>,
    val textElements: List<ScreenNodeInfo>,
)


enum class PhoneCallUiAction { Answer, End }

data class CallControlPoint(val x: Float, val y: Float)

enum class PhoneAutomationWorkStatus {
    Idle,
    Running,
    Error,
}

enum class PhoneAutomationStep {
    None,
    LaunchApp,
    Screenshot,
    Inspect,
    Click,
    Swipe,
    Type,
    PressKey,
    PlaceCall,
    EndCall,
    ReadCall,
    Other,
}

data class PhoneAutomationActivity(
    val toolRunning: Boolean = false,
    val step: PhoneAutomationStep = PhoneAutomationStep.None,
    val holdingForGeneration: Boolean = false,
    val failed: Boolean = false,
)

class PhoneAutomationService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isConnected.value = true
        _workStatus.value = PhoneAutomationWorkStatus.Idle
        _activity.value = PhoneAutomationActivity()
        Log.i(TAG, "PhoneAutomationService connected and active")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op for passive automation
    }

    override fun onInterrupt() {
        Log.w(TAG, "PhoneAutomationService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        _isConnected.value = false
        _workStatus.value = PhoneAutomationWorkStatus.Idle
        _activity.value = PhoneAutomationActivity()
        Log.i(TAG, "PhoneAutomationService destroyed")
    }

    /**
     * Inspects active window hierarchy and returns structured UI elements.
     */
    fun inspectScreen(): ScreenInspectionResult {
        val root = rootInActiveWindow ?: return ScreenInspectionResult(
            packageName = "",
            windowTitle = "",
            interactiveElements = emptyList(),
            textElements = emptyList(),
        )

        val packageName = root.packageName?.toString().orEmpty()
        val windowTitle = root.window?.title?.toString().orEmpty()

        val interactive = mutableListOf<ScreenNodeInfo>()
        val texts = mutableListOf<ScreenNodeInfo>()
        val rect = Rect()
        var currentId = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || !node.isVisibleToUser) return

            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0) {
                val text = node.text?.toString().orEmpty().trim()
                val desc = node.contentDescription?.toString().orEmpty().trim()
                val viewId = node.viewIdResourceName.orEmpty()
                val className = node.className?.toString().orEmpty().substringAfterLast('.')
                val isClickable = node.isClickable
                val isEditable = node.isEditable
                val isScrollable = node.isScrollable

                val hasContent = text.isNotEmpty() || desc.isNotEmpty() || viewId.isNotEmpty()
                val isActionable = isClickable || isEditable || isScrollable

                if (hasContent || isActionable) {
                    val info = ScreenNodeInfo(
                        id = currentId++,
                        text = text,
                        description = desc,
                        viewId = viewId,
                        className = className,
                        clickable = isClickable,
                        editable = isEditable,
                        scrollable = isScrollable,
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                        centerX = rect.centerX(),
                        centerY = rect.centerY(),
                    )

                    if (isActionable) {
                        interactive.add(info)
                    } else if (text.isNotEmpty() || desc.isNotEmpty()) {
                        texts.add(info)
                    }
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        try {
            traverse(root)
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing accessibility tree", e)
        }

        return ScreenInspectionResult(
            packageName = packageName,
            windowTitle = windowTitle,
            interactiveElements = interactive,
            textElements = texts,
        )
    }

    /**
     * Dispatches a tap gesture at the given screen coordinates.
     */
    suspend fun click(x: Float, y: Float): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
            null
        )

        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    /**
     * Dispatches a swipe gesture between start and end screen coordinates.
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(100L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
            null
        )

        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    /**
     * Enters text into the currently focused editable field.
     */
    fun typeText(text: String, clearFirst: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false

        val currentText = if (clearFirst) "" else focused.text?.toString().orEmpty()
        val newText = currentText + text

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Triggers a global system action (Back, Home, Recents, Notifications, Quick Settings).
     */
    fun pressKey(action: String): Boolean {
        val globalAction = when (action.lowercase().trim()) {
            "back" -> GLOBAL_ACTION_BACK
            "home" -> GLOBAL_ACTION_HOME
            "recents", "recent_apps" -> GLOBAL_ACTION_RECENTS
            "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> GLOBAL_ACTION_QUICK_SETTINGS
            else -> return false
        }
        return performGlobalAction(globalAction)
    }

    /**
     * Best-effort tap of an in-call Answer / End control. Returns false when no
     * matching visible control is found. Not reliable across OEM dialers.
     */
    fun performCallUiAction(action: PhoneCallUiAction): Boolean {
        val target = findCallControlNode(action) ?: return false
        if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        var parent = target.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    fun findCallControl(action: PhoneCallUiAction): CallControlPoint? {
        val target = findCallControlNode(action) ?: return null
        val rect = Rect()
        target.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return CallControlPoint(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun findCallControlNode(action: PhoneCallUiAction): AccessibilityNodeInfo? {
        val labels = when (action) {
            PhoneCallUiAction.Answer -> listOf("answer", "accept", "接听", "接聽", "jawab")
            PhoneCallUiAction.End -> listOf(
                "end call", "endcall", "hang up", "hangup", "disconnect",
                "挂断", "掛斷", "结束通话", "結束通話",
            )
        }
        val idHints = when (action) {
            PhoneCallUiAction.Answer -> listOf("answer", "accept")
            PhoneCallUiAction.End -> listOf("end_call", "endcall", "endbutton", "hangup", "disconnect")
        }
        val roots = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        windows?.forEach { window -> window.root?.let { roots.add(it) } }
        for (root in roots) {
            findMatchingCallNode(root, labels, idHints)?.let { return it }
        }
        return null
    }

    private fun findMatchingCallNode(
        node: AccessibilityNodeInfo?,
        labels: List<String>,
        idHints: List<String>,
    ): AccessibilityNodeInfo? {
        if (node == null || !node.isVisibleToUser) return null
        val text = node.text?.toString().orEmpty().trim().lowercase()
        val desc = node.contentDescription?.toString().orEmpty().trim().lowercase()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val labelHit = labels.any { label ->
            text == label || desc == label || text.contains(label) || desc.contains(label)
        }
        val idHit = idHints.any { hint -> viewId.contains(hint) }
        if ((labelHit || idHit) && (node.isClickable || node.isEnabled)) return node
        for (i in 0 until node.childCount) {
            findMatchingCallNode(node.getChild(i), labels, idHints)?.let { return it }
        }
        return null
    }

    /**
     * Takes a screenshot on Android 11+ (API 30+).
     */
    suspend fun takeScreenshot(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "takeScreenshot requires Android 11 (API 30)+")
            return null
        }

        return suspendCancellableCoroutine { continuation ->
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshotResult.hardwareBuffer
                            val colorSpace = screenshotResult.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                            // Convert to software bitmap for serialization/saving
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBuffer.close()
                            if (continuation.isActive) continuation.resume(softwareBitmap ?: bitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert screenshot buffer", e)
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Accessibility takeScreenshot failed with error code $errorCode")
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            )
        }
    }

    companion object {
        @Volatile
        var instance: PhoneAutomationService? = null
            private set

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _workStatus = MutableStateFlow(PhoneAutomationWorkStatus.Idle)
        val workStatus: StateFlow<PhoneAutomationWorkStatus> = _workStatus.asStateFlow()

        private val _activity = MutableStateFlow(PhoneAutomationActivity())
        val activity: StateFlow<PhoneAutomationActivity> = _activity.asStateFlow()

        /** Conversation to reopen when the mini indicator is tapped (best-effort). */
        @Volatile
        var lastConversationId: String? = null

        fun isRunning(): Boolean = instance != null

        /** No-arg starts [PhoneAutomationStep.Other]. Phone tools pass a real step. */
        fun reportWorkStarted(step: PhoneAutomationStep = PhoneAutomationStep.Other) {
            _activity.value = PhoneAutomationActivity(
                toolRunning = true,
                step = step,
                holdingForGeneration = true,
                failed = false,
            )
            _workStatus.value = PhoneAutomationWorkStatus.Running
        }

        /**
         * Tool returned. Keeps the reply "in progress" for the pill.
         * [workStatus] goes Idle so the gap is not a hard error; [activity.failed]
         * is only shown once generation ends.
         */
        fun reportWorkFinished(success: Boolean) {
            _activity.value = PhoneAutomationActivity(
                toolRunning = false,
                step = PhoneAutomationStep.None,
                holdingForGeneration = true,
                failed = !success,
            )
            _workStatus.value = PhoneAutomationWorkStatus.Idle
        }

        /**
         * Reply that used a phone tool has ended.
         * [aborted] is a non-cancellation failure of generation.
         * [cancelled] (user [kotlinx.coroutines.CancellationException]) returns to idle
         * and does not surface "Didn't finish", even if the last tool failed.
         *
         * @return true when this generation included phone automation (holding for the
         * reply, or a phone tool still running) and was not cancelled. The caller should
         * then bring Friendly back to that chat. A failed finish still returns true so
         * the user can see "Didn't finish". Cancelled generations and ordinary replies
         * return false and must not take the screen.
         */
        fun reportGenerationFinished(aborted: Boolean, cancelled: Boolean = false): Boolean {
            val previous = _activity.value
            if (!previous.holdingForGeneration && !previous.toolRunning) return false
            val failed = !cancelled && (previous.failed || aborted)
            _activity.value = PhoneAutomationActivity(
                toolRunning = false,
                step = PhoneAutomationStep.None,
                holdingForGeneration = false,
                failed = failed,
            )
            _workStatus.value = if (failed) {
                PhoneAutomationWorkStatus.Error
            } else {
                PhoneAutomationWorkStatus.Idle
            }
            return !cancelled
        }

        suspend fun <T> trackWork(conversationId: String? = null, block: suspend () -> T): T {
            if (conversationId != null) {
                lastConversationId = conversationId
            }
            _workStatus.value = PhoneAutomationWorkStatus.Running
            return try {
                val result = block()
                _workStatus.value = PhoneAutomationWorkStatus.Idle
                result
            } catch (e: Exception) {
                _workStatus.value = PhoneAutomationWorkStatus.Error
                throw e
            }
        }

        /**
         * Checks whether PhoneAutomationService is enabled in Android Accessibility settings.
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            val expected = ComponentName(context, PhoneAutomationService::class.java).flattenToString()

            while (colonSplitter.hasNext()) {
                val component = colonSplitter.next()
                if (component.equals(expected, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        /**
         * Launches an app by package id or home-screen name.
         * "Google Maps" matches the app labeled Maps, because every word has to
         * show up in the label or the package, and the closest label wins.
         */
        fun launchApp(context: Context, query: String): Pair<Boolean, String> {
            val pm = context.packageManager
            val trimmed = query.trim().lowercase()
            if (trimmed.isEmpty()) return false to "App not found matching '$query'"

            val directIntent = pm.getLaunchIntentForPackage(query.trim())
            if (directIntent != null) {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                return true to "Launched app with package: $query"
            }

            val words = trimmed.split(Regex("[^a-z0-9]+")).filter { it.length >= 2 }
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val matched = pm.queryIntentActivities(mainIntent, 0)
                .map { info ->
                    val label = info.loadLabel(pm).toString().lowercase()
                    val pkgWords = info.activityInfo.packageName.lowercase().replace('.', ' ')
                    val score = when {
                        label == trimmed -> 1000
                        trimmed.length >= 2 && label.contains(trimmed) -> 800
                        words.isNotEmpty() && words.all { label.contains(it) } -> 700
                        words.isNotEmpty() && words.all { word ->
                            label.contains(word) || pkgWords.contains(word)
                        } -> 600
                        else -> 0
                    }
                    Triple(score, label, info)
                }
                .filter { it.first > 0 }
                .maxWithOrNull(
                    compareBy<Triple<Int, String, android.content.pm.ResolveInfo>> { it.first }
                        .thenBy { if (words.any { word -> word == it.second }) 1 else 0 }
                        .thenByDescending { it.second.length },
                )
                ?.third

            if (matched != null) {
                val launchIntent = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    val label = matched.loadLabel(pm).toString()
                    return true to "Launched app: $label (${matched.activityInfo.packageName})"
                }
            }

            return false to "App not found matching '$query'"
        }
    }
}
