package app.friendly.assistant.ui.pages.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.friendly.assistant.data.datastore.Settings
import app.friendly.assistant.data.datastore.getCurrentAssistant
import app.friendly.assistant.data.model.Avatar
import app.friendly.assistant.data.model.Conversation
import app.friendly.assistant.data.model.Folder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/**
 * The native home page shown for an empty, non-folder conversation. Every conversation
 * with messages (and every folder chat) is drawn by the web chat instead.
 */
@Composable
internal fun ChatHome(
    innerPadding: PaddingValues,
    conversation: Conversation,
    settings: Settings,
    hazeState: HazeState,
    isInitializing: Boolean,
    folders: List<Folder>,
    onSelectFolder: (Folder) -> Unit,
    onSeeAllFolders: () -> Unit,
    onStarterClick: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenActivity: () -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenFavorite: () -> Unit,
    onQuickCreateFolder: (name: String, labelId: String) -> Unit,
    onConversationSystemPromptChange: (String?) -> Unit,
) {
    val assistant = settings.getCurrentAssistant()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
            .padding(top = innerPadding.calculateTopPadding()),
    ) {
        if (isInitializing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(32.dp))
            return@Box
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp + innerPadding.calculateBottomPadding()),
        ) {
            AssistantHome(
                userNickname = settings.displaySetting.userNickname,
                userAvatar = settings.displaySetting.userAvatar,
                assistantName = assistant.name,
                assistantAvatar = assistant.avatar ?: Avatar.Dummy,
                folders = folders,
                onSelectFolder = onSelectFolder,
                onSeeAllFolders = onSeeAllFolders,
                onStarterClick = onStarterClick,
                onOpenSearch = onOpenSearch,
                onOpenActivity = onOpenActivity,
                onOpenAssistant = onOpenAssistant,
                onOpenSettings = onOpenSettings,
                onNewFolder = onNewFolder,
                onOpenFavorite = onOpenFavorite,
                onQuickCreateFolder = onQuickCreateFolder,
                modifier = Modifier.fillMaxWidth(),
            )
            if (assistant.allowConversationSystemPrompt) {
                ConversationSystemPromptButton(
                    customSystemPrompt = conversation.customSystemPrompt,
                    onSystemPromptChange = onConversationSystemPromptChange,
                )
            }
        }
    }
}
