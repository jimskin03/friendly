package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import me.rerere.ai.core.Tool
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.event.AppEventBus
import me.rerere.tts.provider.TTSManager

class LocalTools(
    private val context: Context,
    private val eventBus: AppEventBus,
    private val ttsManager: TTSManager,
    private val settingsStore: SettingsStore,
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
    val phoneScreenshotTool by lazy { buildPhoneScreenshotTool(org.koin.java.KoinJavaComponent.getKoin().get<me.rerere.rikkahub.data.files.FilesManager>()) }

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
            tools.add(phoneInspectScreenTool)
            tools.add(phoneClickTool)
            tools.add(phoneSwipeTool)
            tools.add(phoneTypeTextTool)
            tools.add(phonePressKeyTool)
            tools.add(phoneLaunchAppTool)
            tools.add(phoneScreenshotTool)
        }
        return tools
    }
}
