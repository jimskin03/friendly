package me.rerere.rikkahub.ui.pages.chat.desktop

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.SystemClock
import android.util.Base64
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dokar.sonner.ToastType
import io.ktor.client.HttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.Bash
import me.rerere.hugeicons.stroke.Browser
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Computer
import me.rerere.hugeicons.stroke.Keyboard
import me.rerere.hugeicons.stroke.KeyboardOff
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.MouseRightClick01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.hugeicons.stroke.Stop
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.tools.local.LocalToolOption
import me.rerere.rikkahub.data.datastore.NetworkSetting
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.remote.DesktopControlClient
import me.rerere.rikkahub.data.remote.DesktopControlDefaults
import me.rerere.rikkahub.ui.context.LocalToaster
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Whole-screen trackpad. Finger travel is in physical pixels; noVNC mouse
 * coordinates are CSS pixels, so deltas are converted with [density] and then
 * boosted so one swipe can cross the remote desktop.
 */
private fun trackpadDelta(physicalDelta: Float, density: Float): Float {
    val css = physicalDelta / density.coerceAtLeast(1f)
    val mag = abs(css)
    val scale = 1.35f + (mag / 18f).coerceAtMost(1.8f)
    return css * scale
}

private fun WebView?.trackpad(call: String) {
    this?.evaluateJavascript(
        "window.FriendlyTrackpad&&FriendlyTrackpad.$call;",
        null,
    )
}

private suspend fun PointerInputScope.trackpadGestures(
    density: Float,
    onMove: (Float, Float) -> Unit,
    onClick: (button: Int) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onScroll: (Float, Float) -> Unit,
    onZoom: (factor: Float, focusX: Float, focusY: Float) -> Unit,
    onInteraction: () -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val first = awaitFirstDown(requireUnconsumed = false)
        onInteraction()
        val origin = first.position
        val downAt = first.uptimeMillis
        var primaryId = first.id
        var moved = false
        var twoFinger = false
        var pinch = false
        var scrollGesture = false
        var didTwoFinger = false
        var baselineSpan = -1f
        var baseMidX = 0f
        var baseMidY = 0f
        var dragging = false
        var longFired = false
        var scrollX = 0f
        var scrollY = 0f

        while (true) {
            val elapsed = SystemClock.uptimeMillis() - downAt
            val waitForLongPress = !longFired && !moved && !twoFinger && elapsed < longPressMs
            val event = if (waitForLongPress) {
                withTimeoutOrNull(longPressMs - elapsed) {
                    awaitPointerEvent(PointerEventPass.Main)
                }
            } else {
                awaitPointerEvent(PointerEventPass.Main)
            }
            if (event == null) {
                longFired = true
                continue
            }

            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break

            if (pressed.size >= 2) {
                twoFinger = true
                if (dragging) {
                    dragging = false
                    onDragEnd()
                }
                val a = pressed[0]
                val b = pressed[1]
                val span = hypot(
                    a.position.x - b.position.x,
                    a.position.y - b.position.y,
                ).coerceAtLeast(1f)
                val prevSpan = hypot(
                    a.previousPosition.x - b.previousPosition.x,
                    a.previousPosition.y - b.previousPosition.y,
                ).coerceAtLeast(1f)
                val midX = (a.position.x + b.position.x) / 2f
                val midY = (a.position.y + b.position.y) / 2f
                if (baselineSpan < 0f) {
                    baselineSpan = span
                    baseMidX = midX
                    baseMidY = midY
                } else if (!pinch && !scrollGesture) {
                    val grew = abs(span - baselineSpan)
                    val slid = hypot(midX - baseMidX, midY - baseMidY)
                    if (grew > slop * 2f && grew > slid) {
                        pinch = true
                    } else if (slid > slop) {
                        scrollGesture = true
                    }
                }
                if (pinch) {
                    val factor = (span / prevSpan).coerceIn(0.8f, 1.25f)
                    if (abs(factor - 1f) > 0.01f) {
                        didTwoFinger = true
                        onZoom(factor, midX, midY)
                    }
                } else if (scrollGesture) {
                    var dx = 0f
                    var dy = 0f
                    pressed.forEach { change ->
                        dx += change.position.x - change.previousPosition.x
                        dy += change.position.y - change.previousPosition.y
                    }
                    val count = pressed.size.coerceAtLeast(1)
                    scrollX += dx / count
                    scrollY += dy / count
                    val step = 28f * density
                    if (abs(scrollX) >= step || abs(scrollY) >= step) {
                        didTwoFinger = true
                        onScroll(scrollX, scrollY)
                        scrollX = 0f
                        scrollY = 0f
                    }
                }
                pressed.forEach { it.consume() }
                continue
            }

            val primary = pressed.firstOrNull { it.id == primaryId } ?: pressed.first().also {
                primaryId = it.id
            }
            val travel = hypot(
                primary.position.x - origin.x,
                primary.position.y - origin.y,
            )
            if (!moved && travel > slop) moved = true
            if (!longFired && !moved && SystemClock.uptimeMillis() - downAt >= longPressMs) {
                longFired = true
            }
            if (!twoFinger && moved) {
                if (longFired && !dragging) {
                    dragging = true
                    onDragStart()
                }
                val dx = primary.position.x - primary.previousPosition.x
                val dy = primary.position.y - primary.previousPosition.y
                if (dx != 0f || dy != 0f) {
                    onMove(trackpadDelta(dx, density), trackpadDelta(dy, density))
                }
            }
            primary.consume()
        }

        when {
            twoFinger && !didTwoFinger -> onClick(2)
            twoFinger -> Unit
            dragging -> onDragEnd()
            !moved && longFired -> onClick(2)
            !moved -> onClick(0)
        }
    }
}

private suspend fun DesktopControlClient.applyRemoteEdit(previous: String, next: String) {
    var index = 0
    val limit = minOf(previous.length, next.length)
    while (index < limit && previous[index] == next[index]) index++
    repeat(previous.length - index) { hotkey(listOf("BackSpace")) }
    val insert = next.substring(index)
    if (insert.isNotEmpty()) typeText(insert)
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun DesktopControlSheet(
    assistant: Assistant,
    onUpdateAssistant: (Assistant) -> Unit,
    networkSetting: NetworkSetting,
    onUpdateNetworkSetting: (NetworkSetting) -> Unit,
    onDismissRequest: () -> Unit,
    onAppendPrompt: (String) -> Unit,
    onAttachScreenshot: (ByteArray) -> Unit,
    httpClient: HttpClient,
    isStreaming: Boolean,
    currentViewerUrl: String?,
    onStreamStarted: (String) -> Unit,
    onStreamStopped: () -> Unit,
) {
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current.density

    var showConfig by remember {
        mutableStateOf(networkSetting.desktopControlApiToken.isBlank())
    }
    var configBaseUrl by remember(networkSetting.desktopControlBaseUrl) {
        mutableStateOf(networkSetting.desktopControlBaseUrl.ifBlank { DesktopControlDefaults.BASE_URL })
    }
    var configApiToken by remember(networkSetting.desktopControlApiToken) {
        mutableStateOf(networkSetting.desktopControlApiToken)
    }
    var tokenVisible by remember { mutableStateOf(false) }
    var testingConnection by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var keyboardOpen by remember { mutableStateOf(false) }
    var ctrlArmed by remember { mutableStateOf(false) }
    var showHint by remember { mutableStateOf(true) }

    var activeViewerUrl by remember(currentViewerUrl) { mutableStateOf(currentViewerUrl) }
    var lastLoadedViewerUrl by remember { mutableStateOf<String?>(null) }
    var isStartingStream by remember { mutableStateOf(false) }
    var isTakingSnapshot by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    var draft by remember { mutableStateOf("") }
    var sentDraft by remember { mutableStateOf("") }
    var pendingType by remember { mutableStateOf<Job?>(null) }
    val typeMutex = remember { Mutex() }
    val focusRequester = remember { FocusRequester() }

    val hasDesktopTools = assistant.localTools.contains(LocalToolOption.DesktopControl)
    val streamLive = activeViewerUrl != null

    fun createClient(baseUrl: String = configBaseUrl, token: String = configApiToken): DesktopControlClient {
        return DesktopControlClient(
            http = httpClient,
            baseUrl = baseUrl.ifBlank { DesktopControlDefaults.BASE_URL },
            apiToken = token,
        )
    }

    fun moveCursor(dx: Float, dy: Float) {
        webViewInstance.trackpad("moveBy(${dx.jsNum()},${dy.jsNum()})")
    }

    fun clickMouse(button: Int) {
        webViewInstance.trackpad("click($button)")
    }

    fun launchDesktopApp(app: String) {
        scope.launch {
            try {
                val opened = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                ).launchApp(app)
                if (!opened) {
                    toaster.show("Couldn't open $app", ToastType.Error)
                }
            } catch (e: Exception) {
                toaster.show(e.message ?: "Couldn't open $app", ToastType.Error)
            }
        }
    }

    fun sendHotkey(keys: List<String>, label: String? = null) {
        scope.launch {
            try {
                createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                ).hotkey(keys)
            } catch (e: Exception) {
                toaster.show(e.message ?: "Couldn't send ${label ?: keys.joinToString("+")}", ToastType.Error)
            }
        }
    }

    fun sendKeyboardKey(keys: List<String>, label: String? = null) {
        val effectiveKeys = if (ctrlArmed) {
            ctrlArmed = false
            listOf("Control_L") + keys
        } else {
            keys
        }
        sendHotkey(effectiveKeys, label)
    }

    fun xdotoolKeyForTypedCharacter(character: Char): String? = when (character) {
        ' ' -> "space"
        '\n', '\r' -> "Return"
        '\t' -> "Tab"
        '!', '1' -> "1"
        '@', '2' -> "2"
        '#', '3' -> "3"
        '$', '4' -> "4"
        '%', '5' -> "5"
        '^', '6' -> "6"
        '&', '7' -> "7"
        '*', '8' -> "8"
        '(', '9' -> "9"
        ')', '0' -> "0"
        '-' -> "minus"
        '_' -> "underscore"
        '=' -> "equal"
        '+' -> "plus"
        '[' -> "bracketleft"
        '{' -> "braceleft"
        ']' -> "bracketright"
        '}' -> "braceright"
        '\\' -> "backslash"
        '|' -> "bar"
        ';' -> "semicolon"
        ':' -> "colon"
        '\'' -> "apostrophe"
        '"' -> "quotedbl"
        ',' -> "comma"
        '<' -> "less"
        '.' -> "period"
        '>' -> "greater"
        '/' -> "slash"
        '?' -> "question"
        '`' -> "grave"
        '~' -> "asciitilde"
        else -> character.takeIf { it.isLetter() }?.toString()
    }

    fun closeFocusedWindow() {
        scope.launch {
            try {
                createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                ).closeWindow()
            } catch (e: Exception) {
                toaster.show(e.message ?: "Couldn't close window", ToastType.Error)
            }
        }
    }

    fun killDesktopApp(target: String, label: String) {
        scope.launch {
            try {
                createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                ).killApp(target)
                toaster.show("$label closed", ToastType.Info)
            } catch (e: Exception) {
                toaster.show(e.message ?: "Couldn't kill $label", ToastType.Error)
            }
        }
    }

    LaunchedEffect(activeViewerUrl, webViewInstance) {
        val target = activeViewerUrl
        val wv = webViewInstance
        if (!target.isNullOrBlank() && wv != null && target != lastLoadedViewerUrl) {
            lastLoadedViewerUrl = target
            wv.loadUrl(target.fittedViewerUrl())
        }
    }

    LaunchedEffect(streamLive) {
        if (streamLive) {
            showHint = true
            delay(4500)
            showHint = false
        }
    }

    LaunchedEffect(keyboardOpen) {
        if (keyboardOpen) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            ctrlArmed = false
            keyboardController?.hide()
        }
    }

    LaunchedEffect(networkSetting.desktopControlApiToken) {
        if (networkSetting.desktopControlApiToken.isNotBlank() && !isStartingStream) {
            isStartingStream = true
            try {
                val client = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                )
                val status = try {
                    client.status()
                } catch (_: Exception) {
                    null
                }
                if (status != null && status.active && !status.viewer_url.isNullOrBlank()) {
                    activeViewerUrl = status.viewer_url
                    onStreamStarted(status.viewer_url)
                } else {
                    val started = client.startStream(mode = "interactive")
                    activeViewerUrl = started.viewer_url
                    onStreamStarted(started.viewer_url)
                }
            } catch (e: Exception) {
                toaster.show(e.message ?: "Failed to start desktop stream", ToastType.Error)
            } finally {
                isStartingStream = false
            }
        }
    }

    LaunchedEffect(activeViewerUrl) {
        if (activeViewerUrl.isNullOrBlank() || networkSetting.desktopControlApiToken.isBlank()) {
            return@LaunchedEffect
        }
        try {
            createClient(
                baseUrl = networkSetting.desktopControlBaseUrl,
                token = networkSetting.desktopControlApiToken,
            ).prepareDesktop()
        } catch (e: Exception) {
            android.util.Log.w("DesktopControl", "prepare desktop failed: ${e.message}")
        }
    }

    fun flushTyping(pressEnter: Boolean) {
        pendingType?.cancel()
        val target = draft
        scope.launch {
            typeMutex.withLock {
                try {
                    val client = createClient(
                        baseUrl = networkSetting.desktopControlBaseUrl,
                        token = networkSetting.desktopControlApiToken,
                    )
                    if (target != sentDraft) {
                        client.applyRemoteEdit(sentDraft, target)
                        sentDraft = target
                    }
                    if (pressEnter) {
                        val enterKeys = if (ctrlArmed) {
                            ctrlArmed = false
                            listOf("Control_L", "Return")
                        } else {
                            listOf("Return")
                        }
                        client.hotkey(enterKeys)
                        sentDraft = ""
                        draft = ""
                    }
                } catch (e: Exception) {
                    toaster.show(e.message ?: "Failed to type", ToastType.Error)
                }
            }
        }
    }

    fun scheduleTyping(next: String) {
        if (ctrlArmed) {
            val previous = draft
            val key = when {
                next.length == previous.length + 1 && next.startsWith(previous) -> {
                    xdotoolKeyForTypedCharacter(next.last())
                }
                next.length == previous.length - 1 && previous.startsWith(next) -> "BackSpace"
                else -> null
            }
            if (key != null) {
                draft = previous
                pendingType?.cancel()
                ctrlArmed = false
                sendHotkey(listOf("Control_L", key), "Ctrl+$key")
                return
            }
        }

        draft = next
        pendingType?.cancel()
        pendingType = scope.launch {
            delay(220)
            typeMutex.withLock {
                val target = draft
                val base = sentDraft
                if (target == base) return@withLock
                try {
                    createClient(
                        baseUrl = networkSetting.desktopControlBaseUrl,
                        token = networkSetting.desktopControlApiToken,
                    ).applyRemoteEdit(base, target)
                    sentDraft = target
                } catch (e: Exception) {
                    toaster.show(e.message ?: "Failed to type", ToastType.Error)
                }
            }
        }
    }

    fun toggleAssistantTools(enable: Boolean) {
        val currentTools = assistant.localTools.toMutableList()
        if (enable) {
            if (!currentTools.contains(LocalToolOption.DesktopControl)) {
                currentTools.add(LocalToolOption.DesktopControl)
            }
        } else {
            currentTools.remove(LocalToolOption.DesktopControl)
        }
        onUpdateAssistant(assistant.copy(localTools = currentTools))
    }

    fun reconnect() {
        scope.launch {
            try {
                val client = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                )
                val started = client.startStream(mode = "interactive")
                lastLoadedViewerUrl = null
                activeViewerUrl = started.viewer_url
                onStreamStarted(started.viewer_url)
                toaster.show("Reconnected to desktop", ToastType.Success)
            } catch (e: Exception) {
                lastLoadedViewerUrl = null
                webViewInstance?.reload()
                toaster.show(e.message ?: "Failed to reconnect", ToastType.Error)
            }
        }
    }

    fun stopStream() {
        scope.launch {
            try {
                val client = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                )
                client.stopStream()
                activeViewerUrl = null
                onStreamStopped()
                toaster.show("Desktop stream stopped", ToastType.Info)
            } catch (e: Exception) {
                toaster.show(e.message ?: "Failed to stop stream", ToastType.Error)
            }
        }
    }

    fun snapToChat() {
        if (isTakingSnapshot) return
        isTakingSnapshot = true
        scope.launch {
            try {
                val client = createClient(
                    baseUrl = networkSetting.desktopControlBaseUrl,
                    token = networkSetting.desktopControlApiToken,
                )
                val response = client.screenshot()
                val bytes = Base64.decode(response.image_b64, Base64.DEFAULT)
                onAttachScreenshot(bytes)
                toaster.show("Desktop screenshot attached to chat", ToastType.Success)
            } catch (e: Exception) {
                toaster.show(e.message ?: "Failed to capture snapshot", ToastType.Error)
            } finally {
                isTakingSnapshot = false
            }
        }
    }

    BackHandler(onBack = onDismissRequest)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(4f)
            .background(Color.Black),
    ) {
            if (streamLive) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            setBackgroundColor(android.graphics.Color.BLACK)
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = android.view.View.OVER_SCROLL_NEVER
                            WebView.setWebContentsDebuggingEnabled(true)
                            @SuppressLint("SetJavaScriptEnabled")
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.allowContentAccess = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.setSupportZoom(false)
                            settings.builtInZoomControls = false
                            settings.displayZoomControls = false
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = false

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): WebResourceResponse? {
                                    return request?.url?.toString()?.let(::viewerScriptWithHook)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.evaluateJavascript(TRACKPAD_INSTALL_JS, null)
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?,
                                ) {
                                    handler?.proceed()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    android.util.Log.w(
                                        "DesktopControl",
                                        "WebView error: ${error?.errorCode} ${error?.description}",
                                    )
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    android.util.Log.d(
                                        "DesktopControl_noVNC",
                                        "${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()} (${consoleMessage?.sourceId()})",
                                    )
                                    return true
                                }
                            }

                            setOnLongClickListener { true }
                            isLongClickable = false

                            if (!activeViewerUrl.isNullOrBlank()) {
                                lastLoadedViewerUrl = activeViewerUrl
                                loadUrl(activeViewerUrl!!.fittedViewerUrl())
                            }
                        }
                    },
                    update = { webViewInstance = it },
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(density) {
                            trackpadGestures(
                                density = density,
                                onMove = ::moveCursor,
                                onClick = ::clickMouse,
                                onDragStart = { webViewInstance.trackpad("down(0)") },
                                onDragEnd = { webViewInstance.trackpad("up(0)") },
                                onScroll = { dx, dy ->
                                    // Natural scroll: the page follows the fingers.
                                    // Pass CSS pixels; the page turns each 50px into one wheel notch.
                                    webViewInstance.trackpad(
                                        "wheel(${(-dx / density).jsNum()},${(-dy / density).jsNum()})",
                                    )
                                },
                                onZoom = { factor, x, y ->
                                    webViewInstance.trackpad(
                                        "zoomAt(${factor.jsNum()},${(x / density).jsNum()},${(y / density).jsNum()})",
                                    )
                                },
                                onInteraction = { showHint = false },
                            )
                        },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isStartingStream) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.desktop_status_connecting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    } else {
                        Icon(
                            imageVector = HugeIcons.Computer,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.Gray,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.desktop_status_offline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isStartingStream = true
                                    try {
                                        val client = createClient(
                                            baseUrl = networkSetting.desktopControlBaseUrl,
                                            token = networkSetting.desktopControlApiToken,
                                        )
                                        val started = client.startStream(mode = "interactive")
                                        activeViewerUrl = started.viewer_url
                                        onStreamStarted(started.viewer_url)
                                    } catch (e: Exception) {
                                        toaster.show(e.message ?: "Failed to start stream", ToastType.Error)
                                    } finally {
                                        isStartingStream = false
                                    }
                                }
                            },
                            enabled = !isStartingStream && networkSetting.desktopControlApiToken.isNotBlank(),
                        ) {
                            Text(stringResource(R.string.desktop_action_start))
                        }
                    }
                }
            }

            // Only buttons/chip capture touches so titlebar close clicks can
            // still reach the trackpad through empty areas of the top strip.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                FrostedIconButton(
                    icon = HugeIcons.ArrowLeft01,
                    contentDescription = stringResource(R.string.desktop_action_close),
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                StatusChip(
                    connecting = isStartingStream,
                    live = streamLive,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    FrostedIconButton(
                        icon = HugeIcons.MoreVertical,
                        contentDescription = stringResource(R.string.desktop_action_menu),
                        onClick = { menuOpen = true },
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.desktop_action_snap)) },
                            leadingIcon = { Icon(HugeIcons.Camera01, contentDescription = null) },
                            enabled = !isTakingSnapshot && networkSetting.desktopControlApiToken.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                snapToChat()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.desktop_action_reconnect)) },
                            leadingIcon = { Icon(HugeIcons.Refresh01, contentDescription = null) },
                            enabled = networkSetting.desktopControlApiToken.isNotBlank(),
                            onClick = {
                                menuOpen = false
                                reconnect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.desktop_menu_settings)) },
                            leadingIcon = { Icon(HugeIcons.Settings03, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                showConfig = true
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (hasDesktopTools) {
                                        stringResource(R.string.desktop_menu_assistant_on)
                                    } else {
                                        stringResource(R.string.desktop_menu_assistant_off)
                                    },
                                )
                            },
                            leadingIcon = { Icon(HugeIcons.Computer, contentDescription = null) },
                            onClick = {
                                toggleAssistantTools(!hasDesktopTools)
                                menuOpen = false
                            },
                        )
                        if (streamLive) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_action_close_window)) },
                                leadingIcon = { Icon(HugeIcons.Cancel01, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    closeFocusedWindow()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_action_kill_browser)) },
                                leadingIcon = { Icon(HugeIcons.Browser, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    killDesktopApp("browser", "Chromium")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.desktop_action_kill_terminal)) },
                                leadingIcon = { Icon(HugeIcons.Bash, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    killDesktopApp("terminal", "Terminal")
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.desktop_action_stop),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        HugeIcons.Stop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    stopStream()
                                },
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = showHint && streamLive && !keyboardOpen && !showConfig,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Text(
                        text = stringResource(R.string.desktop_trackpad_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }

                if (streamLive && keyboardOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_esc),
                            onClick = { sendKeyboardKey(listOf("Escape"), "Esc") },
                        )
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_ctrl),
                            selected = ctrlArmed,
                            onClick = { ctrlArmed = !ctrlArmed },
                        )
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_pgup),
                            onClick = { sendKeyboardKey(listOf("Page_Up"), "PgUp") },
                        )
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_pgdn),
                            onClick = { sendKeyboardKey(listOf("Page_Down"), "PgDn") },
                        )
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_up_arrow),
                            onClick = { sendKeyboardKey(listOf("Up"), "↑") },
                        )
                        SoftKeyChip(
                            label = stringResource(R.string.desktop_keyboard_down_arrow),
                            onClick = { sendKeyboardKey(listOf("Down"), "↓") },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = ::scheduleTyping,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.desktop_keyboard_placeholder)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { flushTyping(pressEnter = true) }),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Black.copy(alpha = 0.72f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.72f),
                                cursorColor = Color.White,
                            ),
                        )
                        TextButton(onClick = { flushTyping(pressEnter = true) }) {
                            Text(stringResource(R.string.desktop_keyboard_enter))
                        }
                        FrostedIconButton(
                            icon = HugeIcons.KeyboardOff,
                            contentDescription = stringResource(R.string.desktop_action_keyboard),
                            onClick = { keyboardOpen = false },
                        )
                    }
                } else if (streamLive) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FrostedIconButton(
                            icon = HugeIcons.MouseRightClick01,
                            contentDescription = stringResource(R.string.desktop_action_right_click),
                            onClick = { clickMouse(2) },
                        )
                        FrostedIconButton(
                            icon = HugeIcons.Keyboard,
                            contentDescription = stringResource(R.string.desktop_action_keyboard),
                            onClick = { keyboardOpen = true },
                        )
                        FrostedIconButton(
                            icon = HugeIcons.Browser,
                            contentDescription = stringResource(R.string.desktop_action_launch_browser),
                            onClick = { launchDesktopApp("browser") },
                        )
                        FrostedIconButton(
                            icon = HugeIcons.Bash,
                            contentDescription = stringResource(R.string.desktop_action_terminal),
                            onClick = { launchDesktopApp("terminal") },
                        )
                        FrostedIconButton(
                            icon = HugeIcons.Cancel01,
                            contentDescription = stringResource(R.string.desktop_action_close_window),
                            onClick = { closeFocusedWindow() },
                        )
                    }
                }
            }

            if (showConfig) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (networkSetting.desktopControlApiToken.isNotBlank()) {
                                    showConfig = false
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ConnectionCard(
                        baseUrl = configBaseUrl,
                        onBaseUrlChange = { configBaseUrl = it },
                        apiToken = configApiToken,
                        onApiTokenChange = { configApiToken = it },
                        tokenVisible = tokenVisible,
                        onToggleTokenVisible = { tokenVisible = !tokenVisible },
                        testing = testingConnection,
                        onTest = {
                            testingConnection = true
                            scope.launch {
                                try {
                                    val client = createClient(baseUrl = configBaseUrl, token = configApiToken)
                                    val ok = client.health()
                                    toaster.show(
                                        context.getString(
                                            if (ok) R.string.desktop_config_success else R.string.desktop_config_failed,
                                        ),
                                        if (ok) ToastType.Success else ToastType.Error,
                                    )
                                } catch (e: Exception) {
                                    toaster.show(e.message ?: "Connection test failed", ToastType.Error)
                                } finally {
                                    testingConnection = false
                                }
                            }
                        },
                        onSave = {
                            onUpdateNetworkSetting(
                                networkSetting.copy(
                                    desktopControlBaseUrl = configBaseUrl.trim(),
                                    desktopControlApiToken = configApiToken.trim(),
                                ),
                            )
                            showConfig = false
                            scope.launch {
                                isStartingStream = true
                                try {
                                    val client = createClient(baseUrl = configBaseUrl, token = configApiToken)
                                    val started = client.startStream(mode = "interactive")
                                    lastLoadedViewerUrl = null
                                    activeViewerUrl = started.viewer_url
                                    onStreamStarted(started.viewer_url)
                                    toaster.show("Connected to desktop", ToastType.Success)
                                } catch (e: Exception) {
                                    toaster.show(e.message ?: "Failed to connect", ToastType.Error)
                                } finally {
                                    isStartingStream = false
                                }
                            }
                        },
                    )
                }
            }
    }
}

@Composable
private fun StatusChip(
    connecting: Boolean,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        connecting -> Color(0xFFFFB300)
                        live -> Color(0xFF4CAF50)
                        else -> Color(0xFF9E9E9E)
                    },
                ),
        )
        Text(
            text = when {
                connecting -> stringResource(R.string.desktop_status_connecting)
                live -> stringResource(R.string.desktop_screen_title)
                else -> stringResource(R.string.desktop_status_offline)
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SoftKeyChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f),
            ),
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FrostedIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConnectionCard(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    tokenVisible: Boolean,
    onToggleTokenVisible: () -> Unit,
    testing: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.desktop_config_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Control API Base URL") },
                placeholder = { Text(DesktopControlDefaults.BASE_URL) },
                supportingText = {
                    Text("e.g. Tailscale IP: http://100.x.y.z:8787 or http://10.0.2.2:8787 (emulator)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = apiToken,
                onValueChange = onApiTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Bearer Token") },
                placeholder = { Text("Generated during setup") },
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleTokenVisible) {
                        Icon(
                            imageVector = if (tokenVisible) HugeIcons.ViewOff else HugeIcons.View,
                            contentDescription = null,
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !testing && apiToken.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    if (testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.desktop_config_test))
                }
                Button(
                    onClick = onSave,
                    enabled = apiToken.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & Connect")
                }
            }
        }
    }
}

private fun Float.jsNum(): String = if (isFinite()) toString() else "0"

/**
 * noVNC keeps its session inside an ES module. Append a hook so the page can
 * turn scaling back on. The viewer certificate is already accepted by the WebView.
 */
private fun viewerScriptWithHook(url: String): WebResourceResponse? {
    if (!url.contains("/app/ui.js")) return null
    return try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            if (this is HttpsURLConnection) {
                sslSocketFactory = viewerTlsSocketFactory()
                hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
        }
        connection.inputStream.use { input ->
            val hooked = input.bufferedReader().readText() + "\nglobalThis.__novncUI = UI;\n"
            WebResourceResponse("text/javascript", "utf-8", hooked.byteInputStream(Charsets.UTF_8))
        }
    } catch (e: Exception) {
        android.util.Log.w("DesktopControl", "ui.js hook skipped: ${e.message}")
        null
    }
}

private fun viewerTlsSocketFactory() = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }), SecureRandom())
}.socketFactory

/** Fit the whole remote desktop in the phone. resize=remote makes one remote pixel one CSS pixel, which crops it. */
private fun String.fittedViewerUrl(): String {
    val withResize = replace(Regex("([?&])resize=[^&]*")) { match ->
        match.groupValues[1] + "resize=scale"
    }
    return when {
        withResize.contains("resize=") -> withResize
        withResize.contains("?") -> "$withResize&resize=scale"
        else -> "$withResize?resize=scale"
    }
}

private const val TRACKPAD_INSTALL_JS = """
(function() {
  if (window.FriendlyTrackpad && window.FriendlyTrackpad.zoomAt) return;
  function canvas() {
    var list = document.querySelectorAll('canvas');
    var best = null;
    var bestArea = 0;
    for (var i = 0; i < list.length; i++) {
      var r = list[i].getBoundingClientRect();
      var area = r.width * r.height;
      if (area > bestArea) { bestArea = area; best = list[i]; }
    }
    return best;
  }
  function hideChrome() {
    var style = document.getElementById('friendly-trackpad-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'friendly-trackpad-style';
      style.textContent = 'html, body { position: fixed !important; inset: 0; width: 100% !important; height: 100% !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background: #000; border-radius: 0 !important; } #noVNC_container, #noVNC_screen { position: absolute !important; inset: 0; width: 100% !important; height: 100% !important; overflow: hidden !important; border-radius: 0 !important; } canvas { border-radius: 0 !important; } #noVNC_control_bar, #noVNC_control_bar_anchor { display: none !important; }';
      (document.head || document.documentElement).appendChild(style);
    }
  }
  function cursorEl() {
    var el = document.getElementById('friendly-cursor');
    if (!el) {
      el = document.createElement('div');
      el.id = 'friendly-cursor';
      el.style.cssText = 'position:fixed;width:16px;height:16px;margin:-8px 0 0 -8px;border:2px solid #fff;border-radius:50%;box-shadow:0 0 0 1px rgba(0,0,0,.7);pointer-events:none;z-index:99999;';
      (document.body || document.documentElement).appendChild(el);
    }
    return el;
  }
  window.FriendlyTrackpad = {
    x: null,
    y: null,
    zoom: 1,
    cover: 1,
    panX: 0,
    panY: 0,
    viewScale: function() {
      return (this.cover || 1) * (this.zoom || 1);
    },
    updateCover: function() {
      // noVNC already fits the framebuffer. Magnifying it here crops the desktop.
      this.cover = 1;
    },
    applyView: function(c) {
      if (!c) c = canvas();
      if (!c) return;
      this.updateCover(c);
      c.style.transformOrigin = 'center center';
      var z = this.viewScale();
      if (z <= 1.01) {
        this.panX = 0;
        this.panY = 0;
        c.style.transform = '';
      } else {
        c.style.transform = 'translate(' + this.panX + 'px,' + this.panY + 'px) scale(' + z + ')';
      }
      var frame = document.getElementById('noVNC_container');
      if (frame) {
        frame.style.overflow = 'hidden';
        // The hosted noVNC page clips the bottom-right with an 800px by 600px radius.
        frame.style.setProperty('border-radius', '0', 'important');
      }
      var list = document.querySelectorAll('canvas');
      for (var i = 0; i < list.length; i++) {
        if (list[i] !== c) list[i].style.visibility = 'hidden';
      }
    },
    layoutBox: function(c) {
      var visual = c.getBoundingClientRect();
      var z = this.viewScale();
      if (z <= 1.01) {
        return {left: visual.left, top: visual.top, width: visual.width, height: visual.height};
      }
      var lw = visual.width / z;
      var lh = visual.height / z;
      return {
        left: visual.left - this.panX - visual.width * (1 - z) / (2 * z),
        top: visual.top - this.panY - visual.height * (1 - z) / (2 * z),
        width: lw,
        height: lh
      };
    },
    clampPan: function(box) {
      var container = document.getElementById('noVNC_container') || document.documentElement;
      var cr = container.getBoundingClientRect();
      var z = this.viewScale();
      var visW = box.width * z;
      var visH = box.height * z;
      var visLeft = box.left + this.panX + box.width / 2 * (1 - z);
      if (visW >= cr.width - 1) {
        if (visLeft > cr.left) this.panX -= visLeft - cr.left;
        visLeft = box.left + this.panX + box.width / 2 * (1 - z);
        if (visLeft + visW < cr.right) this.panX += cr.right - (visLeft + visW);
      } else {
        if (visLeft < cr.left) this.panX += cr.left - visLeft;
        visLeft = box.left + this.panX + box.width / 2 * (1 - z);
        if (visLeft + visW > cr.right) this.panX -= (visLeft + visW) - cr.right;
      }
      var visTop = box.top + this.panY + box.height / 2 * (1 - z);
      if (visH >= cr.height - 1) {
        if (visTop > cr.top) this.panY -= visTop - cr.top;
        visTop = box.top + this.panY + box.height / 2 * (1 - z);
        if (visTop + visH < cr.bottom) this.panY += cr.bottom - (visTop + visH);
      } else {
        if (visTop < cr.top) this.panY += cr.top - visTop;
        visTop = box.top + this.panY + box.height / 2 * (1 - z);
        if (visTop + visH > cr.bottom) this.panY -= (visTop + visH) - cr.bottom;
      }
    },
    revealCursor: function(box) {
      var container = document.getElementById('noVNC_container') || document.documentElement;
      var cr = container.getBoundingClientRect();
      var z = this.viewScale();
      var vx = box.left + this.panX + box.width / 2 + (this.x - box.width / 2) * z;
      var vy = box.top + this.panY + box.height / 2 + (this.y - box.height / 2) * z;
      var m = 36;
      if (vx < cr.left + m) this.panX += (cr.left + m) - vx;
      if (vx > cr.right - m) this.panX -= vx - (cr.right - m);
      if (vy < cr.top + m) this.panY += (cr.top + m) - vy;
      if (vy > cr.bottom - m) this.panY -= vy - (cr.bottom - m);
      this.clampPan(box);
    },
    place: function(opts) {
      var c = canvas();
      if (!c) return null;
      this.applyView(c);
      var box = this.layoutBox(c);
      if (box.width < 2 || box.height < 2) return null;
      if (this.x == null || this.y == null) {
        this.x = box.width / 2;
        this.y = box.height / 2;
      }
      this.x = Math.max(1, Math.min(box.width - 2, this.x));
      this.y = Math.max(1, Math.min(box.height - 2, this.y));
      if (opts && opts.reveal && this.viewScale() > 1.01) {
        this.revealCursor(box);
        this.applyView(c);
        box = this.layoutBox(c);
      }
      var visual = c.getBoundingClientRect();
      var scale = this.viewScale();
      var vx = box.left + this.panX + box.width / 2 + (this.x - box.width / 2) * scale;
      var vy = box.top + this.panY + box.height / 2 + (this.y - box.height / 2) * scale;
      var mark = cursorEl();
      mark.style.left = vx + 'px';
      mark.style.top = vy + 'px';
      return { canvas: c, rect: visual, box: box };
    },
    dispatch: function(type, button, buttons) {
      var placed = this.place();
      if (!placed) return false;
      var ev = new MouseEvent(type, {
        bubbles: true,
        cancelable: true,
        view: window,
        clientX: placed.rect.left + this.x,
        clientY: placed.rect.top + this.y,
        button: button || 0,
        buttons: buttons || 0
      });
      placed.canvas.dispatchEvent(ev);
      return true;
    },
    moveBy: function(dx, dy) {
      if (!this.place()) return false;
      this.x += dx;
      this.y += dy;
      this.place({reveal: true});
      var buttons = this.held === 4 ? 2 : (this.held ? 1 : 0);
      return this.dispatch('mousemove', 0, buttons);
    },
    zoomAt: function(factor, sx, sy) {
      var c = canvas();
      if (!c) return false;
      this.applyView(c);
      var box = this.layoutBox(c);
      if (box.width < 2 || box.height < 2) return false;
      if (this.x == null || this.y == null) {
        this.x = box.width / 2;
        this.y = box.height / 2;
      }
      var old = this.viewScale();
      var cover = this.cover || 1;
      var pinch = (this.zoom || 1) * factor;
      var minPinch = 1 / cover;
      if (pinch < minPinch) pinch = minPinch;
      if (pinch > 4) pinch = 4;
      var next = (this.cover || 1) * pinch;
      if (Math.abs(next - old) < 0.001) return true;
      var centerX = box.left + box.width / 2 + this.panX;
      var centerY = box.top + box.height / 2 + this.panY;
      var lx = box.width / 2 + (sx - centerX) / old;
      var ly = box.height / 2 + (sy - centerY) / old;
      this.zoom = pinch;
      if (next <= 1.01) {
        this.panX = 0;
        this.panY = 0;
      } else {
        this.panX = sx - (box.left + box.width / 2) - (lx - box.width / 2) * next;
        this.panY = sy - (box.top + box.height / 2) - (ly - box.height / 2) * next;
        this.clampPan(box);
      }
      this.place();
      return true;
    },
    down: function(button) {
      var mask = button === 2 ? 2 : 1;
      this.held = button === 2 ? 4 : 1;
      return this.dispatch('mousedown', button || 0, mask);
    },
    up: function(button) {
      var which = button || 0;
      this.held = 0;
      return this.dispatch('mouseup', which, 0);
    },
    click: function(button) {
      var self = this;
      this.down(button || 0);
      setTimeout(function() { self.up(button || 0); }, 40);
    },
    wheel: function(dx, dy) {
      var placed = this.place();
      if (!placed) return false;
      var steps = Math.round(Math.max(Math.abs(dx), Math.abs(dy)) / 50);
      if (steps < 1) steps = 1;
      if (steps > 8) steps = 8;
      var sx = dx === 0 ? 0 : (dx < 0 ? -50 : 50);
      var sy = dy === 0 ? 0 : (dy < 0 ? -50 : 50);
      for (var i = 0; i < steps; i++) {
        placed.canvas.dispatchEvent(new WheelEvent('wheel', {
          bubbles: true,
          cancelable: true,
          view: window,
          clientX: placed.rect.left + this.x,
          clientY: placed.rect.top + this.y,
          deltaX: sx,
          deltaY: sy,
          deltaMode: 0
        }));
      }
      return true;
    }
  };
  function fitDesktop() {
    var ui = window.__novncUI;
    var rfb = ui && ui.rfb;
    if (!rfb || !rfb._sock) return false;
    try {
      rfb.resizeSession = false;
      rfb.clipViewport = false;
      rfb.scaleViewport = true;
      if (rfb._fbWidth < 1000 && rfb._supportsSetDesktopSize) {
        var messages = rfb.constructor && rfb.constructor.messages;
        if (messages && messages.setDesktopSize) {
          messages.setDesktopSize(rfb._sock, 1280, 720, rfb._screenID, rfb._screenFlags);
        }
      }
    } catch (e) {}
    return true;
  }
  hideChrome();
  fitDesktop();
  setTimeout(function() {
    hideChrome();
    fitDesktop();
    if (window.FriendlyTrackpad) window.FriendlyTrackpad.moveBy(0, 0);
  }, 600);
  setTimeout(fitDesktop, 1500);
})();
"""
