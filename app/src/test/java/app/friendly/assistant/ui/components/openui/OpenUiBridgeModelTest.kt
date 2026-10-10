package app.friendly.assistant.ui.components.openui

import app.friendly.assistant.data.model.Conversation
import app.friendly.assistant.data.model.MessageNode
import kotlinx.datetime.LocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class OpenUiBridgeModelTest {
    private val user = UIMessage(role = MessageRole.USER, parts = listOf(UIMessagePart.Text("hi")))
    private val answerA = UIMessage(
        role = MessageRole.ASSISTANT,
        parts = listOf(
            UIMessagePart.Text("hello"),
            UIMessagePart.Tool(toolCallId = "t1", toolName = "shell", input = "{}", approvalState = ToolApprovalState.Pending),
        ),
        createdAt = LocalDateTime(2026, 10, 11, 1, 0, 0),
        finishedAt = LocalDateTime(2026, 10, 11, 1, 0, 9, 900_000_000),
        usage = TokenUsage(promptTokens = 17_012, completionTokens = 1_100),
    )
    private val answerB = answerA.copy(id = Uuid.random(), parts = listOf(UIMessagePart.Text("other")))
    private val conversation = Conversation(
        assistantId = Uuid.random(),
        messageNodes = listOf(
            MessageNode(messages = listOf(user)),
            MessageNode(messages = listOf(answerA, answerB), selectIndex = 0),
        ),
        chatSuggestions = listOf("More"),
    )

    private fun parse(json: String, loading: Boolean = false) = parseOpenUiAction(json, conversation, loading)

    @Test
    fun statsMatchTheNativeStatsLine() {
        val stats = answerA.bridgeStats()!!
        assertEquals(17_012, stats.inputTokens)
        assertEquals(1_100, stats.outputTokens)
        assertEquals(9_900L, stats.durationMs)
        assertEquals(111.1, stats.tokensPerSecond!!, 0.1)
    }

    @Test
    fun snapshotCarriesToolsBranchesAndPendingApproval() {
        val messages = conversation.toBridgeMessages(loading = false)
        assertEquals(2, messages[1].branchCount)
        assertEquals("pending", messages[1].tools.single().state)
        assertTrue(messages[0].canEdit)
        assertTrue(messages[1].canRegenerate)
    }

    @Test
    fun validActionsResolveToRealMessages() {
        assertEquals(OpenUiChatAction.SelectBranch(1, 1), parse("""{"type":"branch","messageId":"${answerA.id}","delta":1}"""))
        assertEquals(OpenUiChatAction.Speak(answerA), parse("""{"type":"speak","messageId":"${answerA.id}"}"""))
        assertEquals(OpenUiChatAction.BeginEdit(user), parse("""{"type":"edit","messageId":"${user.id}"}"""))
        assertEquals(OpenUiChatAction.ToolApproval("t1", true, ""), parse("""{"type":"toolApproval","toolCallId":"t1","approved":true}"""))
        assertEquals(OpenUiChatAction.Send("yo", answer = true), parse("""{"type":"send","text":" yo "}"""))
    }

    @Test
    fun invalidOrUnsafeActionsAreDropped() {
        assertNull(parse("""{"type":"branch","messageId":"${answerA.id}","delta":5}"""))
        assertNull(parse("""{"type":"edit","messageId":"${answerA.id}"}""")) // assistant messages are not editable
        assertNull(parse("""{"type":"regenerate","messageId":"${answerA.id}"}""", loading = true))
        assertNull(parse("""{"type":"toolApproval","toolCallId":"nope","approved":true}"""))
        assertNull(parse("""{"type":"suggestion","text":"not offered"}"""))
        assertNull(parse("""{"type":"delete","messageId":"${Uuid.random()}"}"""))
        assertNull(parse("""{"type":"link","url":"javascript:alert(1)"}"""))
        assertNull(parse("""{"type":"native"}"""))
        assertNull(parse("not json"))
        assertNull(parse("""{"type":"send","text":"${"x".repeat(MAX_MESSAGE_LENGTH + 1)}"}"""))
    }
}
