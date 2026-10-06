package app.friendly.assistant.ui.pages.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import app.friendly.assistant.data.datastore.DEFAULT_ASSISTANT_ID
import app.friendly.assistant.data.datastore.Settings
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.model.Assistant
import app.friendly.assistant.data.model.Conversation
import app.friendly.assistant.data.model.MessageNode
import app.friendly.assistant.data.repository.ConversationRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.random.Random
import kotlin.uuid.Uuid

class DebugVM(
    private val settingsStore: SettingsStore,
    private val conversationRepository: ConversationRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    private val _conversationCount = MutableStateFlow<Int?>(null)
    val conversationCount: StateFlow<Int?> = _conversationCount.asStateFlow()


    private val _conversationAssistants = MutableStateFlow<Map<Uuid, Int>?>(null)
    val conversationAssistants: StateFlow<Map<Uuid, Int>?> = _conversationAssistants.asStateFlow()

    init {
        refreshConversationCount()
        scanConversationAssistants()
    }

    fun refreshConversationCount() {
        viewModelScope.launch {
            _conversationCount.value = conversationRepository.countConversations()
        }
    }

    fun scanConversationAssistants() {
        viewModelScope.launch {
            _conversationAssistants.value = conversationRepository.countConversationsByAssistant()
        }
    }


    suspend fun recoverAssistantsFromConversations(): Int? {
        val settings = settingsStore.settingsFlow.value
        if (settings.init) return null
        val conversationAssistants = conversationRepository.countConversationsByAssistant()
        _conversationAssistants.value = conversationAssistants
        val existingIds = settings.assistants.map { it.id }.toSet()
        val missing = conversationAssistants
            .filterKeys { it !in existingIds }
            .entries
            .sortedByDescending { it.value }
        if (missing.isEmpty()) return 0
        val recovered = missing.mapIndexed { index, (id, _) ->
            Assistant(id = id, name = "Restored Assistant ${index + 1}")
        }
        settingsStore.update(settings.copy(assistants = settings.assistants + recovered))
        return recovered.size
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }


    fun createOversizedConversation(sizeMB: Int = 3) {
        viewModelScope.launch {
            val targetSize = sizeMB * 1024 * 1024
            val messageNodes = mutableListOf<MessageNode>()
            var currentSize = 0


            var index = 0
            while (currentSize < targetSize) {

                val largeText = buildString {
                    repeat(100) {
                        append("This is a long test text used to test CursorWindow size limits.")
                        append("Row too big to fit into CursorWindow error usually occurs when single row exceeds 2MB.")
                        append("Lorem ipsum dolor sit amet, consectetur adipiscing elit. ")
                        append("Index: $index, Block: $it. ")
                    }
                }

                val userMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(largeText)),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )
                val assistantMessage = UIMessage(
                    id = Uuid.random(),
                    role = MessageRole.ASSISTANT,
                    parts = listOf(UIMessagePart.Text("Reply: $largeText")),
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                )

                messageNodes.add(MessageNode.of(userMessage))
                messageNodes.add(MessageNode.of(assistantMessage))

                currentSize += largeText.length * 2 * 2
                index++
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "Large conversation test (${sizeMB}MB)",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    fun createConversationWithMessages(messageCount: Int = 1024) {
        viewModelScope.launch {
            val messageNodes = ArrayList<MessageNode>(messageCount)
            val timeZone = TimeZone.currentSystemDefault()
            repeat(messageCount) { index ->
                val role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT
                val message = UIMessage(
                    id = Uuid.random(),
                    role = role,
                    parts = listOf(UIMessagePart.Text(randomMessageText(index, role))),
                    createdAt = Clock.System.now().toLocalDateTime(timeZone),
                )
                messageNodes.add(MessageNode.of(message))
            }

            val conversation = Conversation(
                id = Uuid.random(),
                assistantId = DEFAULT_ASSISTANT_ID,
                title = "${messageCount} messages test",
                messageNodes = messageNodes,
            )

            conversationRepository.insertConversation(conversation)
        }
    }

    private fun randomMessageText(index: Int, role: MessageRole): String {
        val fragments = listOf(
            "quick", "random", "message", "sample", "for", "test", "list", "render", "scroll", "performance",
            "chat", "conversation", "content", "structure", "verify", "pagination", "order", "stable", "system",
        )
        val wordCount = Random.nextInt(6, 14)
        val prefix = if (role == MessageRole.USER) "User" else "Assistant"
        val body = List(wordCount) { fragments.random() }.joinToString(" ")
        return "$prefix#${index + 1}: $body"
    }
}
