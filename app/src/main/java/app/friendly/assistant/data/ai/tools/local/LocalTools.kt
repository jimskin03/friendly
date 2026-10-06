package app.friendly.assistant.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
    private val phoneCallController: app.friendly.assistant.service.phone.PhoneCallController,
    private val agentCallManager: app.friendly.assistant.service.phone.agentcall.AgentCallManager,
) {
    val javascriptTool by lazy { buildJavascriptTool() }

    val timeTool by lazy { buildTimeInfoTool() }

    val clipboardTool by lazy { buildClipboardTool(context) }

    val ttsTool by lazy { buildTextToSpeechTool(eventBus, ttsManager, settingsStore) }

    val askUserTool by lazy { buildAskUserTool() }

    val screenTimeTool by lazy { buildScreenTimeTool(context, eventBus) }

    val calendarQueryTool by lazy { buildCalendarQueryTool(context) }

    val calendarCreateTool by lazy { buildCalendarCreateTool(context) }

    val chartDisplayTool by lazy { buildChartDisplayTool() }

    val phoneInspectScreenTool by lazy { buildPhoneInspectScreenTool() }
    val phoneClickTool by lazy { buildPhoneClickTool() }
    val phoneSwipeTool by lazy { buildPhoneSwipeTool(context) }
    val phoneTypeTextTool by lazy { buildPhoneTypeTextTool() }
    val phonePressKeyTool by lazy { buildPhonePressKeyTool() }
    val phoneLaunchAppTool by lazy { buildPhoneLaunchAppTool(context) }
    val phoneBringAppToFrontTool by lazy { buildPhoneBringAppToFrontTool(context) }
    val phoneScreenshotTool by lazy { buildPhoneScreenshotTool(org.koin.java.KoinJavaComponent.getKoin().get<app.friendly.assistant.data.files.FilesManager>()) }
    val agentCallTool by lazy { buildAgentCallTool(agentCallManager) }
    val agentCallStatusTool by lazy { buildAgentCallStatusTool(agentCallManager) }
    val agentCallEndTool by lazy { buildAgentCallEndTool(agentCallManager) }
    val placeCallTool by lazy { buildPlaceCallTool(phoneCallController) }
    val endCallTool by lazy { buildEndCallTool(phoneCallController) }
    val readCallStateTool by lazy { buildReadCallStateTool(phoneCallController, settingsStore, agentCallManager) }
    val phoneAssertVisibleTool by lazy { buildPhoneAssertVisibleTool() }
    val phoneScrollUntilVisibleTool by lazy { buildPhoneScrollUntilVisibleTool(context) }
    val phoneRunFlowTool by lazy { buildPhoneRunFlowTool(context) }
    val phoneManageFlowsTool by lazy { buildPhoneManageFlowsTool(context) }

    private fun getDesktopClient(): app.friendly.assistant.data.remote.DesktopControlClient? {
        val settings = settingsStore.settingsFlow.value
        val token = settings.networkSetting.desktopControlApiToken
        if (token.isBlank()) return null
        val baseUrl = settings.networkSetting.desktopControlBaseUrl.ifBlank {
            app.friendly.assistant.data.remote.DesktopControlDefaults.BASE_URL
        }
        val http = org.koin.java.KoinJavaComponent.getKoin().get<io.ktor.client.HttpClient>()
        return app.friendly.assistant.data.remote.DesktopControlClient(http, baseUrl, token)
    }

    val desktopScreenshotTool by lazy {
        buildDesktopScreenshotTool(
            ::getDesktopClient,
            org.koin.java.KoinJavaComponent.getKoin().get<app.friendly.assistant.data.files.FilesManager>()
        )
    }
    val desktopClickTool by lazy { buildDesktopClickTool(::getDesktopClient) }
    val desktopTypeTool by lazy { buildDesktopTypeTool(::getDesktopClient) }
    val desktopHotkeyTool by lazy { buildDesktopHotkeyTool(::getDesktopClient) }
    val desktopBrowserOpenTool by lazy { buildDesktopBrowserOpenTool(::getDesktopClient) }
    val desktopStreamStartTool by lazy { buildDesktopStreamStartTool(::getDesktopClient) }
    val desktopStreamStopTool by lazy { buildDesktopStreamStopTool(::getDesktopClient) }

    fun getTools(options: List<LocalToolOption>): List<Tool> {
        val tools = mutableListOf<Tool>()
        if (options.contains(LocalToolOption.JavascriptEngine)) {
            tools.add(javascriptTool)
        }
        if (options.contains(LocalToolOption.TimeInfo)) {
            tools.add(timeTool)
        }
        if (options.contains(LocalToolOption.Clipboard)) {
            tools.add(clipboardTool)
        }
        if (options.contains(LocalToolOption.Tts)) {
            tools.add(ttsTool)
        }
        if (options.contains(LocalToolOption.AskUser)) {
            tools.add(askUserTool)
        }
        if (options.contains(LocalToolOption.ScreenTime)) {
            tools.add(screenTimeTool)
        }
        if (options.contains(LocalToolOption.Calendar)) {
            tools.add(calendarQueryTool)
            tools.add(calendarCreateTool)
        }
        if (options.contains(LocalToolOption.ChartDisplay)) {
            tools.add(chartDisplayTool)
        }
        if (options.contains(LocalToolOption.PhoneAutomation)) {
            tools.add(phoneInspectScreenTool.withPhoneAutomationTracking())
            tools.add(phoneClickTool.withPhoneAutomationTracking())
            tools.add(phoneSwipeTool.withPhoneAutomationTracking())
            tools.add(phoneTypeTextTool.withPhoneAutomationTracking())
            tools.add(phonePressKeyTool.withPhoneAutomationTracking())
            tools.add(phoneLaunchAppTool.withPhoneAutomationTracking())
            tools.add(phoneBringAppToFrontTool.withPhoneAutomationTracking())
            tools.add(phoneScreenshotTool.withPhoneAutomationTracking())
            tools.add(agentCallTool.withPhoneAutomationTracking())
            tools.add(agentCallStatusTool.withPhoneAutomationTracking())
            tools.add(agentCallEndTool.withPhoneAutomationTracking())
            tools.add(placeCallTool.withPhoneAutomationTracking())
            tools.add(endCallTool.withPhoneAutomationTracking())
            tools.add(readCallStateTool.withPhoneAutomationTracking())
            tools.add(phoneAssertVisibleTool.withPhoneAutomationTracking())
            tools.add(phoneScrollUntilVisibleTool.withPhoneAutomationTracking())
            tools.add(phoneRunFlowTool.withPhoneAutomationTracking())
            tools.add(phoneManageFlowsTool.withPhoneAutomationTracking())
        }
        if (options.contains(LocalToolOption.DesktopControl)) {
            tools.add(desktopScreenshotTool)
            tools.add(desktopClickTool)
            tools.add(desktopTypeTool)
            tools.add(desktopHotkeyTool)
            tools.add(desktopBrowserOpenTool)
            tools.add(desktopStreamStartTool)
            tools.add(desktopStreamStopTool)
        }
        return tools
    }
}
