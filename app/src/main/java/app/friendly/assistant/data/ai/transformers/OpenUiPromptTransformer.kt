package app.friendly.assistant.data.ai.transformers

import app.friendly.assistant.data.datastore.ChatUiMode
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/** Only installed on the chat generation path, not title/suggestion/background tasks. */
class OpenUiPromptTransformer : InputMessageTransformer {
    private var prompt: String? = null

    override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
        if (ctx.settings.displaySetting.chatUiMode != ChatUiMode.OPEN_UI) return messages
        val presentation = prompt ?: ctx.context.assets.open("openui/system-prompt.txt")
            .bufferedReader().use { it.readText() }.also { prompt = it }
        return appendOpenUiPrompt(messages, presentation)
    }
}

internal fun appendOpenUiPrompt(messages: List<UIMessage>, prompt: String): List<UIMessage> {
    val systemIndex = messages.indexOfFirst { it.role == MessageRole.SYSTEM }
    if (systemIndex < 0) return listOf(UIMessage.system(prompt).copy(isSynthetic = true)) + messages
    return messages.mapIndexed { index, message ->
        if (index != systemIndex) message else message.copy(
            parts = message.parts + UIMessagePart.Text("\n\n$prompt"),
            isSynthetic = true,
        )
    }
}
