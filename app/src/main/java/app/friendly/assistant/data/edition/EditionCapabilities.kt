package app.friendly.assistant.data.edition

import app.friendly.assistant.BuildConfig
import app.friendly.assistant.data.ai.tools.local.LocalToolOption

/**
 * Immutable compiled-edition policy. Importing Nightly settings cannot grant Play-only exclusions.
 * Play blocks only phone automation (with its overlay/call paths) and screen time (usage stats);
 * desktop control, JavaScript, workspace/terminal, skills and MCP stay available in both editions.
 * Actual tool/permission checks remain in each native execution path.
 */
data class EditionCapabilities(
    val edition: String,
    val phoneAutomation: Boolean,
    val overlaysAndCalls: Boolean,
    val usageStats: Boolean,
    val desktopViewer: Boolean,
    val desktopExecution: Boolean,
    val javascriptExecution: Boolean,
    val workspaceExecution: Boolean,
    val mcpExecution: Boolean,
    val githubUpdates: Boolean,
    val openUiActions: Boolean,
) {
    fun allowsLocalTool(option: LocalToolOption): Boolean = when (option) {
        LocalToolOption.PhoneAutomation -> phoneAutomation
        LocalToolOption.ScreenTime -> usageStats
        LocalToolOption.DesktopControl -> desktopExecution
        LocalToolOption.JavascriptEngine -> javascriptExecution
        else -> true
    }

    companion object {
        fun current() = forEdition(BuildConfig.IS_PLAY_BUILD)

        fun forEdition(isPlayBuild: Boolean) = EditionCapabilities(
            edition = if (isPlayBuild) "play" else "nightly",
            phoneAutomation = !isPlayBuild,
            overlaysAndCalls = !isPlayBuild,
            usageStats = !isPlayBuild,
            desktopViewer = true,
            desktopExecution = true,
            javascriptExecution = true,
            workspaceExecution = true,
            mcpExecution = true,
            githubUpdates = !isPlayBuild,
            openUiActions = true,
        )
    }
}
