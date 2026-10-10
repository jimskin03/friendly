package app.friendly.assistant.data.ai.transformers

import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.*
import org.junit.Test

class OpenUiPromptTransformerTest {
    @Test
    fun presentationPromptPreservesAssistantInstructionsAndHistory() {
        val system = UIMessage.system("Assistant instructions")
        val user = UIMessage.user("Make a chart")
        val result = appendOpenUiPrompt(listOf(system, user), "OpenUI schema")
        assertEquals(2, result.size)
        assertEquals(system.parts, result.first().parts.dropLast(1))
        assertTrue((result.first().parts.last() as UIMessagePart.Text).text.contains("OpenUI schema"))
        assertEquals(user, result.last())
    }

    @Test
    fun addsSyntheticSystemMessageWhenThereIsNoAssistantPrompt() {
        val user = UIMessage.user("Make a chart")
        val result = appendOpenUiPrompt(listOf(user), "OpenUI schema")
        assertTrue(result.first().isSynthetic)
        assertEquals(user, result.last())
    }
}
