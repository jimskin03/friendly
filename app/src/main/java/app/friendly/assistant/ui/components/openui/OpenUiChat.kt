package app.friendly.assistant.ui.components.openui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.friendly.assistant.BuildConfig
import app.friendly.assistant.data.edition.EditionCapabilities
import app.friendly.assistant.data.model.Conversation
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val OPEN_UI_HOST = "openui.friendly.local"
private const val OPEN_UI_URL = "https://$OPEN_UI_HOST/index.html"
private const val MAX_ACTION_BYTES = 32 * 1024
private const val MAX_LINK_LENGTH = 2048
private val bridgeJson = Json { encodeDefaults = true }

@Serializable
data class OpenUiCapabilities(
    val edition: String,
    val phoneAutomation: Boolean,
    val desktopControl: Boolean,
    val contentReporting: Boolean,
    val openUiActions: Boolean,
) {
    companion object {
        fun current() = forEdition(BuildConfig.IS_PLAY_BUILD)

        fun forEdition(isPlayBuild: Boolean): OpenUiCapabilities {
            val policy = EditionCapabilities.forEdition(isPlayBuild)
            return OpenUiCapabilities(
            edition = if (isPlayBuild) "play" else "nightly",
            phoneAutomation = policy.phoneAutomation,
            desktopControl = policy.desktopExecution,
            contentReporting = false,
            openUiActions = policy.openUiActions,
        )
        }
    }
}

@Serializable
private data class OpenUiSnapshot(
    val protocolVersion: Int = 1,
    val sessionId: String,
    val revision: Long,
    val conversationId: String,
    val title: String,
    val loading: Boolean,
    val processingStatus: String? = null,
    val darkMode: Boolean,
    val draft: String,
    val attachmentCount: Int,
    val modelAvailable: Boolean,
    val chat: OpenUiChatState = OpenUiChatState(),
    val capabilities: OpenUiCapabilities,
    val messages: List<OpenUiMessage>,
    val suggestions: List<String>,
    val theme: OpenUiTheme? = null,
)

/** Active native Material colours (#AARRGGBB) so the web renderer follows the app theme. */
@Serializable
internal data class OpenUiTheme(
    val primary: String,
    val onPrimary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val background: String,
    val surface: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val onSurface: String,
    val onSurfaceVariant: String,
    val outlineVariant: String,
)

private fun androidx.compose.ui.graphics.Color.hex(): String = "#%08X".format(toArgb())

/** Only plain web/mail links leave the renderer; everything else is dropped. */
internal fun openUiExternalLink(url: String?): Uri? {
    if (url == null || url.length > MAX_LINK_LENGTH) return null
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "https", "http" -> uri.takeIf { !it.host.isNullOrBlank() }
        "mailto" -> uri
        else -> null
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun OpenUiChat(
    conversation: Conversation,
    loading: Boolean,
    processingStatus: String?,
    darkMode: Boolean,
    draft: String,
    chatState: OpenUiChatState,
    modelAvailable: Boolean,
    modifier: Modifier = Modifier,
    onAction: (OpenUiChatAction) -> Unit,
    onRendererFailure: () -> Unit,
) {
    val context = LocalContext.current
    val sessionId = remember(conversation.id) { java.util.UUID.randomUUID().toString() }
    var webView by remember(sessionId) { mutableStateOf<WebView?>(null) }
    var pageReady by remember(sessionId) { mutableStateOf(false) }
    val alive = remember(sessionId) { AtomicBoolean(true) }
    val revisionCounter = remember(sessionId) { AtomicLong(0L) }

    val actionState = rememberUpdatedState(onAction)
    val conversationState = rememberUpdatedState(conversation)
    val loadingState = rememberUpdatedState(loading)
    val failureState = rememberUpdatedState(onRendererFailure)
    val modelAvailableState = rememberUpdatedState(modelAvailable)
    val colors = MaterialTheme.colorScheme
    val theme = OpenUiTheme(
        primary = colors.primary.hex(),
        onPrimary = colors.onPrimary.hex(),
        secondaryContainer = colors.secondaryContainer.hex(),
        onSecondaryContainer = colors.onSecondaryContainer.hex(),
        background = colors.background.hex(),
        surface = colors.surface.hex(),
        surfaceContainer = colors.surfaceContainer.hex(),
        surfaceContainerHigh = colors.surfaceContainerHigh.hex(),
        onSurface = colors.onSurface.hex(),
        onSurfaceVariant = colors.onSurfaceVariant.hex(),
        outlineVariant = colors.outlineVariant.hex(),
    )

    val snapshotContents = OpenUiSnapshot(
        sessionId = sessionId,
        revision = 0L,
        conversationId = conversation.id.toString(),
        title = conversation.title,
        loading = loading,
        processingStatus = processingStatus,
        darkMode = darkMode,
        draft = draft,
        attachmentCount = chatState.pendingAttachments.size,
        chat = chatState,
        modelAvailable = modelAvailable,
        capabilities = OpenUiCapabilities.current(),
        messages = conversation.toBridgeMessages(loading),
        suggestions = conversation.chatSuggestions,
        theme = theme,
    )
    // The web client rejects older revisions. Content hashes are NOT monotonic,
    // particularly when a streaming assistant response changes.
    val snapshotJson = remember(snapshotContents) {
        bridgeJson.encodeToString(snapshotContents.copy(revision = revisionCounter.incrementAndGet()))
    }
    val snapshotState = rememberUpdatedState(snapshotJson)

    fun pushSnapshot(target: WebView?) {
        if (!pageReady || target == null) return
        val argument = JSONObject.quote(snapshotState.value)
        target.evaluateJavascript("window.friendlyOpenUI?.pushSnapshot($argument)", null)
    }

    val bridge = remember(sessionId) {
        OpenUiBridge(sessionId) { raw, isReady ->
            if (!alive.get()) return@OpenUiBridge
            if (isReady) {
                pageReady = true
                return@OpenUiBridge
            }
            val action = parseOpenUiAction(raw, conversationState.value, loadingState.value) ?: return@OpenUiBridge
            when (action) {
                is OpenUiChatAction.OpenLink -> openUiExternalLink(action.url)?.let { uri ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                is OpenUiChatAction.CopyText -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("code", action.text))
                }
                is OpenUiChatAction.Send -> if (modelAvailableState.value) actionState.value(action)
                else -> actionState.value(action)
            }
        }
    }

    LaunchedEffect(snapshotJson, pageReady) {
        pushSnapshot(webView)
    }

    key(sessionId) { AndroidView(
        modifier = modifier,
        factory = {
            WebView(context).apply {
                // Match the parent exactly. Wrap content lets the document collapse
                // to the header and composer, leaving the chat wallpaper underneath.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(colors.background.toArgb())
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setGeolocationEnabled(false)
                settings.mediaPlaybackRequiresUserGesture = true
                addJavascriptInterface(bridge, "FriendlyOpenUI")
                webViewClient = OpenUiWebViewClient { failureState.value() }
                webView = this
                loadUrl(OPEN_UI_URL)
            }
        },
        update = { current ->
            current.setBackgroundColor(colors.background.toArgb())
            webView = current
            pushSnapshot(current)
        },
    ) }

    DisposableEffect(sessionId) {
        onDispose {
            alive.set(false)
            webView?.apply {
                evaluateJavascript("window.friendlyOpenUI?.retire()", null)
                removeJavascriptInterface("FriendlyOpenUI")
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

private class OpenUiBridge(
    private val sessionId: String,
    private val dispatch: (raw: String, isReady: Boolean) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionId = 0L

    @JavascriptInterface
    fun postMessage(payload: String) {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_ACTION_BYTES) return
        val actionId = runCatching {
            val value = JSONObject(payload)
            val type = value.getString("type")
            if (type !in OPEN_UI_ACTION_TYPES) return
            if (type == "ready") return@runCatching 0L
            if (value.optString("sessionId") != sessionId) return
            value.getLong("actionId").takeIf { it > 0L } ?: return
        }.getOrNull() ?: return
        mainHandler.post {
            if (actionId != 0L) {
                // Drop duplicate/replayed/stale messages, even across WebView reloads.
                if (actionId <= lastActionId) return@post
                lastActionId = actionId
            }
            dispatch(payload, actionId == 0L)
        }
    }
}

private class OpenUiWebViewClient(private val onFailure: () -> Unit) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return true
        return uri.scheme != "https" || uri.host != OPEN_UI_HOST || uri.port != -1
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        if (uri.scheme != "https" || uri.host != OPEN_UI_HOST || uri.port != -1) return blockedResponse()
        val relativePath = uri.path.orEmpty().removePrefix("/").ifEmpty { "index.html" }
        if (relativePath.contains("..") || Uri.decode(relativePath).contains("..")) return blockedResponse()
        return runCatching {
            val mime = mimeTypeOf(relativePath)
            WebResourceResponse(
                mime,
                if (mime.startsWith("text/") || mime == "application/json") "UTF-8" else null,
                view.context.assets.open("openui/$relativePath"),
            )
        }.getOrElse { blockedResponse() }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) view.post(onFailure)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        view.post(onFailure)
        // We handle recovery by leaving WebView mode, instead of crashing the app.
        return true
    }

    private fun blockedResponse() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), "".byteInputStream())
}



private fun mimeTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
    "html" -> "text/html"
    "js", "mjs" -> "text/javascript"
    "css" -> "text/css"
    "json" -> "application/json"
    "svg" -> "image/svg+xml"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "woff2" -> "font/woff2"
    "woff" -> "font/woff"
    "ttf" -> "font/ttf"
    else -> "application/octet-stream"
}