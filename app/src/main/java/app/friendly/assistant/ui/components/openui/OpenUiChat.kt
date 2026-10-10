package app.friendly.assistant.ui.components.openui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
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
private const val MAX_MESSAGE_LENGTH = 16_000
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
private data class OpenUiMessage(
    val id: String,
    val role: String,
    val text: String,
    val openui: String? = null,
    val branchIndex: Int? = null,
    val branchCount: Int? = null,
    val canRegenerate: Boolean = false,
    val canEdit: Boolean = false,
    val canReport: Boolean = false,
)

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
fun OpenUiChat(
    conversation: Conversation,
    loading: Boolean,
    processingStatus: String?,
    darkMode: Boolean,
    draft: String,
    attachmentCount: Int,
    modelAvailable: Boolean,
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onRegenerate: (UIMessage) -> Unit,
    onSuggestion: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onOpenAttachments: () -> Unit,
    onStartVoice: () -> Unit,
    onEdit: (UIMessage) -> Unit,
    onOpenNative: () -> Unit,
    onRendererFailure: () -> Unit,
) {
    val context = LocalContext.current
    val sessionId = remember(conversation.id) { java.util.UUID.randomUUID().toString() }
    var webView by remember(sessionId) { mutableStateOf<WebView?>(null) }
    var pageReady by remember(sessionId) { mutableStateOf(false) }
    val alive = remember(sessionId) { AtomicBoolean(true) }
    val revisionCounter = remember(sessionId) { AtomicLong(0L) }

    val sendState = rememberUpdatedState(onSend)
    val stopState = rememberUpdatedState(onStop)
    val regenerateState = rememberUpdatedState(onRegenerate)
    val suggestionState = rememberUpdatedState(onSuggestion)
    val conversationState = rememberUpdatedState(conversation)
    val loadingState = rememberUpdatedState(loading)
    val draftChangeState = rememberUpdatedState(onDraftChange)
    val attachmentsState = rememberUpdatedState(onOpenAttachments)
    val voiceState = rememberUpdatedState(onStartVoice)
    val editState = rememberUpdatedState(onEdit)
    val nativeState = rememberUpdatedState(onOpenNative)
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
        attachmentCount = attachmentCount,
        modelAvailable = modelAvailable,
        capabilities = OpenUiCapabilities.current(),
        messages = conversation.currentMessages.mapIndexed { index, message ->
            val node = conversation.messageNodes.getOrNull(index)
            OpenUiMessage(
                id = message.id.toString(),
                role = message.role.toBridgeRole(),
                text = message.parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text },
                openui = message.openUiProgram(),
                branchIndex = node?.selectIndex,
                branchCount = node?.messages?.size,
                canRegenerate = message.role == MessageRole.ASSISTANT && !loading,
                canEdit = message.role == MessageRole.USER && !loading,
                canReport = false,
            )
        },
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
        OpenUiBridge(sessionId) { action ->
            if (!alive.get()) return@OpenUiBridge
            when (action.type) {
                "ready" -> {
                    pageReady = true
                }
                "send" -> if (!loadingState.value && modelAvailableState.value) {
                    action.text?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE_LENGTH }?.let(sendState.value)
                }
                "stop" -> if (loadingState.value) stopState.value()
                "suggestion" -> if (!loadingState.value && action.text in conversationState.value.chatSuggestions) {
                    action.text?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_MESSAGE_LENGTH }?.let(suggestionState.value)
                }
                "regenerate" -> {
                    if (loadingState.value) return@OpenUiBridge
                    val id = action.messageId ?: return@OpenUiBridge
                    conversationState.value.currentMessages.firstOrNull { it.id.toString() == id }
                        ?.takeIf { it.role == MessageRole.ASSISTANT }
                        ?.let(regenerateState.value)
                }
                "draft" -> action.text?.takeIf { it.length <= MAX_MESSAGE_LENGTH }?.let(draftChangeState.value)
                "attachments" -> attachmentsState.value()
                "voice" -> voiceState.value()
                "native" -> nativeState.value()
                "link" -> openUiExternalLink(action.url)?.let { uri ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                "copy" -> action.text?.let { text ->
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("code", text))
                }
                "edit" -> {
                    if (loadingState.value) return@OpenUiBridge
                    conversationState.value.currentMessages.firstOrNull { it.id.toString() == action.messageId }
                        ?.takeIf { it.role == MessageRole.USER }
                        ?.let(editState.value)
                }
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
                setBackgroundColor(Color.TRANSPARENT)
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

private data class OpenUiAction(
    val type: String,
    val text: String? = null,
    val messageId: String? = null,
    val url: String? = null,
)

private class OpenUiBridge(
    private val sessionId: String,
    private val dispatch: (OpenUiAction) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionId = 0L

    @JavascriptInterface
    fun postMessage(payload: String) {
        if (payload.toByteArray(Charsets.UTF_8).size > MAX_ACTION_BYTES) return
        val parsed = runCatching {
            val value = JSONObject(payload)
            val type = value.getString("type")
            if (type !in setOf("ready", "send", "stop", "suggestion", "regenerate", "draft", "attachments", "voice", "native", "edit", "link", "copy")) return
            val actionId = if (type == "ready") 0L else value.getLong("actionId")
            if (type != "ready" && (value.optString("sessionId") != sessionId || actionId <= 0L)) return
            Pair(actionId, OpenUiAction(
                type = type,
                text = value.optString("text").takeIf { value.has("text") },
                messageId = value.optString("messageId").takeIf { value.has("messageId") },
                url = value.optString("url").takeIf { value.has("url") },
            ))
        }.getOrNull() ?: return
        mainHandler.post {
            val (actionId, action) = parsed
            if (action.type != "ready") {
                // Drop duplicate/replayed/stale messages, even across WebView reloads.
                if (actionId <= lastActionId) return@post
                lastActionId = actionId
            }
            dispatch(action)
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

private fun MessageRole.toBridgeRole(): String = when (this) {
    MessageRole.USER -> "user"
    MessageRole.ASSISTANT -> "assistant"
    MessageRole.SYSTEM -> "system"
    else -> "tool"
}

private fun UIMessage.openUiProgram(): String? {
    return parts.asSequence()
        .mapNotNull { part ->
            val metadata = part.metadata ?: return@mapNotNull null
            runCatching {
                metadata["friendly.openui"]?.jsonPrimitive?.contentOrNull
                    ?: metadata["openui"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
        .firstOrNull { it.isNotBlank() }
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