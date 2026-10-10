package app.friendly.assistant.data.ai.transformers

import me.rerere.ai.provider.Modality
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

/**
 * Friendly uses the chat session's model for everything (no separate OCR model). When that
 * model can't read images, attached images are replaced with a short note instead of being
 * sent to an endpoint that would reject them.
 */
object OcrTransformer : InputMessageTransformer {
    private const val NO_VISION_NOTE =
        "[The user attached an image, but the current model can't read images. " +
            "Tell the user to switch to a model with image input if the image matters.]"

    override suspend fun transform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        if (ctx.model.inputModalities.contains(Modality.IMAGE)) return messages
        val hasImages = messages.any { message -> message.parts.any { it is UIMessagePart.Image } }
        if (!hasImages) return messages
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Image) UIMessagePart.Text(NO_VISION_NOTE) else part
                }
            )
        }
    }
}
