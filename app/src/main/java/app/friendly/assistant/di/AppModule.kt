package app.friendly.assistant.di

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import kotlinx.serialization.json.Json
import app.friendly.assistant.AppScope
import app.friendly.assistant.data.ai.tools.local.LocalTools
import app.friendly.assistant.data.ai.tools.ChatToolFactory
import app.friendly.assistant.data.event.AppEventBus
import app.friendly.assistant.service.ChatNotificationManager
import app.friendly.assistant.service.phone.PhoneAutomationMiniIndicatorManager
import app.friendly.assistant.service.ChatService
import app.friendly.assistant.ui.pages.extensions.workspace.WorkspaceTerminalSessionManager
import app.friendly.assistant.utils.AppAnalytics
import app.friendly.assistant.utils.EmojiData
import app.friendly.assistant.utils.EmojiUtils
import app.friendly.assistant.utils.JsonInstant
import app.friendly.assistant.utils.SoundEffectPlayer
import app.friendly.assistant.utils.UpdateChecker
import app.friendly.assistant.web.WebServerManager
import me.rerere.tts.provider.TTSManager
import org.koin.dsl.module

val appModule = module {
    single<Json> { JsonInstant }

    single {
        AppEventBus()
    }

    single {
        LocalTools(get(), get(), get(), get(), get(), get())
    }

    single {
        UpdateChecker(
            client = get(),
            appScope = get(),
        )
    }

    single {
        AppScope()
    }

    single<EmojiData> {
        EmojiUtils.loadEmoji(get())
    }

    single {
        TTSManager(get())
    }

    single<FirebaseCrashlytics> {
        val context: Context = get()
        check(FirebaseApp.initializeApp(context) != null) {
            "Firebase is not configured. Add app/google-services.json."
        }
        Firebase.crashlytics
    }

    single<AppAnalytics> {
        val context: Context = get()
        if (FirebaseApp.initializeApp(context) == null) {
            AppAnalytics { }
        } else {
            val analytics = Firebase.analytics
            AppAnalytics { name -> analytics.logEvent(name, null) }
        }
    }

    single {
        SoundEffectPlayer(get())
    }

    single {
        WorkspaceTerminalSessionManager(get(), get())
    }


    single(createdAtStart = true) {
        ChatNotificationManager(
            context = get(),
            appScope = get(),
            eventBus = get(),
            settingsStore = get(),
        )
    }

    single(createdAtStart = true) {
        app.friendly.assistant.service.phone.PhoneCallController(
            app = get(),
            appScope = get(),
            settingsStore = get(),
        )
    }

    single(createdAtStart = true) {
        app.friendly.assistant.service.phone.agentcall.AgentCallManager(
            app = get(),
            appScope = get(),
            settingsStore = get(),
            httpClient = get(),
        )
    }

    single(createdAtStart = true) {
        PhoneAutomationMiniIndicatorManager(
            app = get(),
            appScope = get(),
            settingsStore = get(),
            phoneCallController = get(),
            agentCallManager = get(),
        )
    }

    single {
        ChatToolFactory(
            json = get(),
            memoryRepository = get(),
            conversationRepository = get(),
            localTools = get(),
            mcpManager = get(),
            skillManager = get(),
            workspaceRepository = get(),
        )
    }

    single {
        ChatService(
            context = get(),
            appScope = get(),
            appEventBus = get(),
            settingsStore = get(),
            conversationRepo = get(),
            memoryRepository = get(),
            generationLoop = get(),
            templateTransformer = get(),
            providerManager = get(),
            chatToolFactory = get(),
            mcpManager = get(),
            filesManager = get(),
            workspaceRepository = get(),
            folderRepository = get(),
            phoneMiniIndicator = get(),
        )
    }

    single {
        WebServerManager(
            context = get(),
            appScope = get(),
            chatService = get(),
            conversationRepo = get(),
            folderRepo = get(),
            settingsStore = get(),
            filesManager = get()
        )
    }
}
