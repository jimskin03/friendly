package app.friendly.assistant.di

import app.friendly.assistant.ui.pages.apps.AppsVM
import app.friendly.assistant.ui.pages.assistant.AssistantVM
import app.friendly.assistant.ui.pages.assistant.detail.AssistantDetailVM
import app.friendly.assistant.ui.pages.backup.BackupVM
import app.friendly.assistant.ui.pages.chat.ChatDrawerVM
import app.friendly.assistant.ui.pages.chat.ChatVM
import app.friendly.assistant.ui.pages.debug.DebugVM
import app.friendly.assistant.ui.pages.favorite.FavoriteVM
import app.friendly.assistant.ui.pages.search.SearchVM
import app.friendly.assistant.ui.pages.history.HistoryVM
import app.friendly.assistant.ui.pages.stats.StatsVM
import app.friendly.assistant.ui.pages.imggen.ImgGenVM
import app.friendly.assistant.ui.pages.extensions.PromptVM
import app.friendly.assistant.ui.pages.extensions.QuickMessagesVM
import app.friendly.assistant.ui.pages.extensions.skills.SkillDetailVM
import app.friendly.assistant.ui.pages.extensions.skills.SkillsVM
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceDetailVM
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceVM
import app.friendly.assistant.ui.pages.setting.SettingVM
import app.friendly.assistant.ui.pages.share.handler.ShareHandlerVM
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModel<ChatVM> { params ->
        ChatVM(
            id = params.get(),
            context = get(),
            settingsStore = get(),
            conversationRepo = get(),
            chatService = get(),
            updateChecker = get(),
            analytics = get(),
            filesManager = get(),
            favoriteRepository = get(),
        )
    }
    viewModelOf(::ChatDrawerVM)
    viewModelOf(::SettingVM)
    viewModelOf(::AppsVM)
    viewModelOf(::DebugVM)
    viewModelOf(::HistoryVM)
    viewModelOf(::AssistantVM)
    viewModel<AssistantDetailVM> {
        AssistantDetailVM(
            id = it.get(),
            settingsStore = get(),
            memoryRepository = get(),
            filesManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }
    viewModel<ShareHandlerVM> {
        ShareHandlerVM(
            text = it.get(),
            settingsStore = get(),
        )
    }
    viewModelOf(::BackupVM)
    viewModelOf(::ImgGenVM)
    viewModelOf(::PromptVM)
    viewModelOf(::QuickMessagesVM)
    viewModelOf(::SkillsVM)
    viewModelOf(::SkillDetailVM)
    viewModelOf(::WorkspaceVM)
    viewModel<WorkspaceDetailVM> {
        WorkspaceDetailVM(
            id = it.get(),
            repository = get(),
            terminalSessionManager = get(),
        )
    }
    viewModelOf(::FavoriteVM)
    viewModelOf(::SearchVM)
    viewModelOf(::StatsVM)
}
