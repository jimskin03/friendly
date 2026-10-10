package app.friendly.assistant.ui.components.openui

import app.friendly.assistant.data.model.Conversation
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessageAnnotation
import me.rerere.ai.ui.UIMessagePart
import java.time.Duration
import kotlinx.datetime.toJavaLocalDateTime

internal const val MAX_MESSAGE_LENGTH = 16_000
internal const val MAX_TOOL_TEXT = 16_000
internal const val MAX_REASONING_TEXT = 20_000

@Serializable
internal data class OpenUiStats(
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int = 0,
    val durationMs: Long? = null,
    val tokensPerSecond: Double? = null,
)

@Serializable
internal data class OpenUiAttachment(
    val kind: String,
    val name: String,
    val size: Long? = null,
    /** Key into the asset map pushed separately (thumbnails as data: URIs). */
    val thumb: String? = null,
)

/** Stable, short key for a local file URL; the URL itself never reaches the page. */
internal fun openUiAssetKey(url: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
    return digest.take(10).joinToString("") { "%02x".format(it) }
}

internal fun UIMessagePart.toBridgeAttachment(sizeOf: (String) -> Long? = { null }): OpenUiAttachment? = when (this) {
    is UIMessagePart.Image -> OpenUiAttachment("image", url.substringAfterLast('/').take(60), sizeOf(url), openUiAssetKey(url))
    is UIMessagePart.Video -> OpenUiAttachment("video", url.substringAfterLast('/').take(60), sizeOf(url))
    is UIMessagePart.Audio -> OpenUiAttachment("audio", url.substringAfterLast('/').take(60), sizeOf(url))
    is UIMessagePart.Document -> OpenUiAttachment("file", fileName.take(60), sizeOf(url))
    else -> null
}

/** Ordered content of one reply, so reasoning, tools and text keep their real order. */
@Serializable
internal data class OpenUiBlock(val kind: String, val text: String? = null, val toolId: String? = null, val ms: Long? = null)

@Serializable
internal data class OpenUiOption(val id: String, val name: String, val group: String? = null, val enabled: Boolean = false)

@Serializable
internal data class OpenUiPickers(
    val assistants: List<OpenUiOption> = emptyList(),
    val assistantId: String? = null,
    val models: List<OpenUiOption> = emptyList(),
    val modelId: String? = null,
    /** null when the current model has no reasoning ability. */
    val reasoning: String? = null,
    val reasoningLevels: List<String> = emptyList(),
    val mcp: List<OpenUiOption> = emptyList(),
    val searchMode: String = "off",
    val searchModes: List<String> = listOf("off", "local"),
    val searchServices: List<OpenUiOption> = emptyList(),
    val searchServiceId: String? = null,
)

@Serializable
internal data class OpenUiTool(
    val id: String,
    val name: String,
    val input: String,
    val output: String? = null,
    /** auto | pending | approved | denied | answered */
    val state: String,
    val executed: Boolean,
    /** Asset keys of images the tool returned (screenshots). */
    val images: List<String> = emptyList(),
)

@Serializable
internal data class OpenUiCitation(val title: String, val url: String)

@Serializable
internal data class OpenUiMessage(
    val id: String,
    val role: String,
    val text: String,
    val openui: String? = null,
    val reasoning: String? = null,
    val reasoningMs: Long? = null,
    val blocks: List<OpenUiBlock> = emptyList(),
    val tools: List<OpenUiTool> = emptyList(),
    val attachments: List<OpenUiAttachment> = emptyList(),
    val citations: List<OpenUiCitation> = emptyList(),
    val stats: OpenUiStats? = null,
    val branchIndex: Int? = null,
    val branchCount: Int? = null,
    val favorite: Boolean = false,
    val canRegenerate: Boolean = false,
    val canEdit: Boolean = false,
    val canReport: Boolean = false,
)

@Serializable
internal data class OpenUiQueued(val id: String, val text: String, val editing: Boolean = false)

@Serializable
internal data class OpenUiError(val id: String, val title: String, val message: String, val solution: String? = null, val retryable: Boolean = false)

/** Native state that only the chat page knows; folded into the snapshot. */
@Serializable
internal data class OpenUiChatState(
    val editingMessageId: String? = null,
    val pendingAttachments: List<OpenUiAttachment> = emptyList(),
    /** off | connecting | listening | transcribing | speaking | error */
    val voice: String = "off",
    val voiceTranscript: String = "",
    val ttsSpeakingMessageId: String? = null,
    val ttsAvailable: Boolean = true,
    val modelName: String? = null,
    val assistantName: String? = null,
    val showStats: Boolean = true,
    val queue: List<OpenUiQueued> = emptyList(),
    val errors: List<OpenUiError> = emptyList(),
    val desktopAvailable: Boolean = true,
    val phoneAvailable: Boolean = false,
    val desktopStreaming: Boolean = false,
    val folderName: String? = null,
    /** Message to scroll to once (search results, notifications). */
    val focusMessageId: String? = null,
    val pickers: OpenUiPickers = OpenUiPickers(),
    /** Incremented by the native top bar to open the outline. */
    val outlineRequest: Int = 0,
)

internal fun MessageRole.toBridgeRole(): String = name.lowercase()

internal fun UIMessage.openUiProgram(): String? = parts.asSequence()
    .mapNotNull { part ->
        val metadata = part.metadata ?: return@mapNotNull null
        runCatching {
            metadata["friendly.openui"]?.jsonPrimitive?.contentOrNull
                ?: metadata["openui"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
    .firstOrNull { it.isNotBlank() }

internal fun UIMessage.bridgeStats(): OpenUiStats? {
    val usage = usage ?: return null
    val duration = finishedAt?.let {
        Duration.between(createdAt.toJavaLocalDateTime(), it.toJavaLocalDateTime()).toMillis()
    }?.takeIf { it > 0 }
    return OpenUiStats(
        inputTokens = usage.promptTokens,
        outputTokens = usage.completionTokens,
        cachedTokens = usage.cachedTokens,
        durationMs = duration,
        tokensPerSecond = duration?.let { usage.completionTokens * 1000.0 / it },
    )
}

private fun String.clip(max: Int) = if (length <= max) this else take(max) + "…"

private fun ToolApprovalState.bridgeName(): String = when (this) {
    is ToolApprovalState.Pending -> "pending"
    is ToolApprovalState.Approved -> "approved"
    is ToolApprovalState.Denied -> "denied"
    is ToolApprovalState.Answered -> "answered"
    else -> "auto"
}

internal fun UIMessage.toBridgeMessage(
    branchIndex: Int?,
    branchCount: Int?,
    favorite: Boolean,
    loading: Boolean,
    sizeOf: (String) -> Long? = { null },
): OpenUiMessage {
    val reasoningParts = parts.filterIsInstance<UIMessagePart.Reasoning>()
    return OpenUiMessage(
        id = id.toString(),
        role = role.toBridgeRole(),
        text = parts.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text },
        openui = openUiProgram(),
        reasoning = reasoningParts.joinToString("\n\n") { it.reasoning }.takeIf { it.isNotBlank() }?.clip(MAX_REASONING_TEXT),
        reasoningMs = reasoningParts.mapNotNull { r -> r.finishedAt?.let { (it - r.createdAt).inWholeMilliseconds } }.sum().takeIf { it > 0 },
        tools = parts.filterIsInstance<UIMessagePart.Tool>().map { tool ->
            OpenUiTool(
                id = tool.toolCallId,
                name = tool.toolName,
                input = tool.input.clip(MAX_TOOL_TEXT),
                output = tool.output.filterIsInstance<UIMessagePart.Text>().joinToString("\n") { it.text }
                    .takeIf { it.isNotBlank() }?.clip(MAX_TOOL_TEXT),
                state = tool.approvalState.bridgeName(),
                executed = tool.isExecuted,
                images = tool.output.filterIsInstance<UIMessagePart.Image>().map { openUiAssetKey(it.url) },
            )
        },
        blocks = parts.mapNotNull { part ->
            when (part) {
                is UIMessagePart.Text -> part.text.takeIf { it.isNotBlank() }?.let { OpenUiBlock("text", text = it) }
                is UIMessagePart.Reasoning -> part.reasoning.takeIf { it.isNotBlank() }?.let {
                    OpenUiBlock("reasoning", text = it.clip(MAX_REASONING_TEXT), ms = part.finishedAt?.let { f -> (f - part.createdAt).inWholeMilliseconds })
                }
                is UIMessagePart.Tool -> OpenUiBlock("tool", toolId = part.toolCallId)
                else -> null
            }
        },
        attachments = parts.mapNotNull { it.toBridgeAttachment(sizeOf) },
        citations = annotations.filterIsInstance<UIMessageAnnotation.UrlCitation>()
            .filter { openUiExternalLinkString(it.url) }
            .map { OpenUiCitation(it.title.clip(120), it.url) },
        stats = if (role == MessageRole.ASSISTANT) bridgeStats() else null,
        branchIndex = branchIndex,
        branchCount = branchCount,
        favorite = favorite,
        canRegenerate = role == MessageRole.ASSISTANT && !loading,
        canEdit = role == MessageRole.USER && !loading,
    )
}

internal fun Conversation.toBridgeMessages(loading: Boolean, sizeOf: (String) -> Long? = { null }): List<OpenUiMessage> =
    currentMessages.mapIndexed { index, message ->
        val node = messageNodes.getOrNull(index)
        message.toBridgeMessage(
            branchIndex = node?.selectIndex,
            branchCount = node?.messages?.size,
            favorite = node?.isFavorite == true,
            loading = loading,
            sizeOf = sizeOf,
        )
    }

/** Only plain web/mail links leave the renderer. Pure-string check usable in JVM tests. */
internal fun openUiExternalLinkString(url: String?): Boolean {
    if (url == null || url.length > 2048) return false
    val match = Regex("^(https?)://([^/?#\\s]+)", RegexOption.IGNORE_CASE).find(url)
    return match != null || url.startsWith("mailto:", ignoreCase = true)
}

/** A typed, validated action from the web renderer. */
internal sealed interface OpenUiChatAction {
    data class Send(val text: String, val answer: Boolean = true) : OpenUiChatAction
    data object Stop : OpenUiChatAction
    data class Suggestion(val text: String) : OpenUiChatAction
    data class Draft(val text: String) : OpenUiChatAction
    data class Regenerate(val message: UIMessage) : OpenUiChatAction
    data class BeginEdit(val message: UIMessage) : OpenUiChatAction
    data object CancelEdit : OpenUiChatAction
    data class CopyMessage(val message: UIMessage) : OpenUiChatAction
    data class CopyText(val text: String) : OpenUiChatAction
    data class Speak(val message: UIMessage) : OpenUiChatAction
    data object StopSpeaking : OpenUiChatAction
    data class SelectBranch(val nodeIndex: Int, val branchIndex: Int) : OpenUiChatAction
    data class Delete(val message: UIMessage) : OpenUiChatAction
    data class Fork(val message: UIMessage) : OpenUiChatAction
    data class Share(val message: UIMessage) : OpenUiChatAction
    data class ToggleFavorite(val nodeIndex: Int) : OpenUiChatAction
    data class ToolApproval(val toolCallId: String, val approved: Boolean, val reason: String) : OpenUiChatAction
    data class ToolAnswer(val toolCallId: String, val answer: String) : OpenUiChatAction
    data object OpenAttachments : OpenUiChatAction
    data class RemoveAttachment(val index: Int) : OpenUiChatAction
    data object StartVoice : OpenUiChatAction
    data object StopVoice : OpenUiChatAction
    data object InterruptVoice : OpenUiChatAction
    data object OpenModelPicker : OpenUiChatAction
    data object OpenDesktop : OpenUiChatAction
    data object OpenPhone : OpenUiChatAction
    data object ExportConversation : OpenUiChatAction
    data class DismissError(val id: String) : OpenUiChatAction
    data object ClearErrors : OpenUiChatAction
    data class RemoveQueued(val id: String) : OpenUiChatAction
    data object ResumeQueue : OpenUiChatAction
    data class OpenLink(val url: String) : OpenUiChatAction
    data class SelectAssistant(val id: String) : OpenUiChatAction
    data class SelectModel(val id: String) : OpenUiChatAction
    data class SetReasoning(val level: String) : OpenUiChatAction
    data class SetMcp(val id: String, val enabled: Boolean) : OpenUiChatAction
    data class SetSearchMode(val mode: String) : OpenUiChatAction
    data class SetSearchService(val id: String) : OpenUiChatAction
    data class EditQueued(val id: String, val text: String?) : OpenUiChatAction
    data class BeginEditQueued(val id: String) : OpenUiChatAction
    data class ErrorSolution(val id: String, val solution: String) : OpenUiChatAction
    data object Retry : OpenUiChatAction
    data object DesktopSnap : OpenUiChatAction
    data object DesktopStop : OpenUiChatAction
}

private val actionJson = Json { ignoreUnknownKeys = true }

/**
 * Parses and validates one raw bridge payload against the current conversation.
 * Anything malformed, unknown, or referring to messages that do not exist returns null.
 */
internal fun parseOpenUiAction(payload: JsonObject, conversation: Conversation, loading: Boolean): OpenUiChatAction? {
    fun str(key: String) = runCatching { payload[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    fun int(key: String) = runCatching { payload[key]?.jsonPrimitive?.intOrNull }.getOrNull()
    fun bool(key: String) = runCatching { payload[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
    fun text() = str("text")?.takeIf { it.length <= MAX_MESSAGE_LENGTH }
    val messages = conversation.currentMessages
    fun message() = str("messageId")?.let { id -> messages.firstOrNull { it.id.toString() == id } }
    fun nodeIndexOf(m: UIMessage) = messages.indexOf(m).takeIf { it >= 0 }

    return when (str("type")) {
        "send" -> text()?.trim()?.takeIf { it.isNotEmpty() && !loading }?.let { OpenUiChatAction.Send(it, bool("answer") ?: true) }
        "stop" -> OpenUiChatAction.Stop.takeIf { loading }
        "suggestion" -> text()?.takeIf { !loading && it in conversation.chatSuggestions }?.let { OpenUiChatAction.Suggestion(it) }
        "draft" -> text()?.let { OpenUiChatAction.Draft(it) }
        "regenerate" -> message()?.takeIf { !loading && it.role == MessageRole.ASSISTANT }?.let { OpenUiChatAction.Regenerate(it) }
        "edit" -> message()?.takeIf { !loading && it.role == MessageRole.USER }?.let { OpenUiChatAction.BeginEdit(it) }
        "cancelEdit" -> OpenUiChatAction.CancelEdit
        "copyMessage" -> message()?.let { OpenUiChatAction.CopyMessage(it) }
        "copy" -> str("text")?.takeIf { it.length <= 64_000 }?.let { OpenUiChatAction.CopyText(it) }
        "speak" -> message()?.takeIf { it.role == MessageRole.ASSISTANT }?.let { OpenUiChatAction.Speak(it) }
        "stopSpeaking" -> OpenUiChatAction.StopSpeaking
        "branch" -> {
            val m = message() ?: return null
            val nodeIndex = nodeIndexOf(m) ?: return null
            val node = conversation.messageNodes.getOrNull(nodeIndex) ?: return null
            val target = node.selectIndex + (int("delta") ?: return null)
            if (loading || target !in node.messages.indices) null else OpenUiChatAction.SelectBranch(nodeIndex, target)
        }
        "delete" -> message()?.takeIf { !loading }?.let { OpenUiChatAction.Delete(it) }
        "fork" -> message()?.let { OpenUiChatAction.Fork(it) }
        "share" -> message()?.let { OpenUiChatAction.Share(it) }
        "favorite" -> message()?.let(::nodeIndexOf)?.let { OpenUiChatAction.ToggleFavorite(it) }
        "toolApproval" -> {
            val id = str("toolCallId") ?: return null
            val pending = messages.any { m -> m.parts.any { it is UIMessagePart.Tool && it.toolCallId == id && it.isPending } }
            if (!pending) null else OpenUiChatAction.ToolApproval(id, bool("approved") ?: return null, (str("reason") ?: "").take(1000))
        }
        "toolAnswer" -> {
            val id = str("toolCallId") ?: return null
            val pending = messages.any { m -> m.parts.any { it is UIMessagePart.Tool && it.toolCallId == id && it.isPending } }
            text()?.takeIf { pending }?.let { OpenUiChatAction.ToolAnswer(id, it) }
        }
        "attachments" -> OpenUiChatAction.OpenAttachments
        "removeAttachment" -> int("index")?.takeIf { it >= 0 }?.let { OpenUiChatAction.RemoveAttachment(it) }
        "voice" -> OpenUiChatAction.StartVoice
        "voiceStop" -> OpenUiChatAction.StopVoice
        "voiceInterrupt" -> OpenUiChatAction.InterruptVoice
        "modelPicker" -> OpenUiChatAction.OpenModelPicker
        "desktop" -> OpenUiChatAction.OpenDesktop
        "phone" -> OpenUiChatAction.OpenPhone
        "export" -> OpenUiChatAction.ExportConversation
        "dismissError" -> str("id")?.let { OpenUiChatAction.DismissError(it) }
        "clearErrors" -> OpenUiChatAction.ClearErrors
        "removeQueued" -> str("id")?.let { OpenUiChatAction.RemoveQueued(it) }
        "resumeQueue" -> OpenUiChatAction.ResumeQueue
        "selectAssistant" -> str("id")?.let { OpenUiChatAction.SelectAssistant(it) }
        "selectModel" -> str("id")?.let { OpenUiChatAction.SelectModel(it) }
        "setReasoning" -> str("level")?.takeIf { it.length <= 16 }?.let { OpenUiChatAction.SetReasoning(it) }
        "setMcp" -> str("id")?.let { id -> bool("enabled")?.let { OpenUiChatAction.SetMcp(id, it) } }
        "setSearchMode" -> str("mode")?.takeIf { it in setOf("off", "local", "built_in") }?.let { OpenUiChatAction.SetSearchMode(it) }
        "setSearchService" -> str("id")?.let { OpenUiChatAction.SetSearchService(it) }
        "beginEditQueued" -> str("id")?.let { OpenUiChatAction.BeginEditQueued(it) }
        "editQueued" -> str("id")?.let { id -> OpenUiChatAction.EditQueued(id, str("text")?.takeIf { it.length <= MAX_MESSAGE_LENGTH }) }
        "errorSolution" -> str("id")?.let { id -> str("solution")?.let { OpenUiChatAction.ErrorSolution(id, it) } }
        "retry" -> OpenUiChatAction.Retry.takeIf { !loading }
        "desktopSnap" -> OpenUiChatAction.DesktopSnap
        "desktopStop" -> OpenUiChatAction.DesktopStop
        "link" -> str("url")?.takeIf { openUiExternalLinkString(it) }?.let { OpenUiChatAction.OpenLink(it) }
        else -> null
    }
}

internal fun parseOpenUiAction(raw: String, conversation: Conversation, loading: Boolean): OpenUiChatAction? =
    runCatching { actionJson.parseToJsonElement(raw).jsonObject }.getOrNull()?.let { parseOpenUiAction(it, conversation, loading) }

internal val OPEN_UI_ACTION_TYPES = setOf(
    "ready", "send", "stop", "suggestion", "draft", "regenerate", "edit", "cancelEdit", "copyMessage", "copy",
    "speak", "stopSpeaking", "branch", "delete", "fork", "share", "favorite", "toolApproval", "toolAnswer",
    "attachments", "removeAttachment", "voice", "voiceStop", "voiceInterrupt", "modelPicker", "desktop", "phone",
    "export", "dismissError", "clearErrors", "removeQueued", "resumeQueue", "link",
    "selectAssistant", "selectModel", "setReasoning", "setMcp", "setSearchMode", "setSearchService",
    "beginEditQueued", "editQueued", "errorSolution", "retry", "desktopSnap", "desktopStop",
)

