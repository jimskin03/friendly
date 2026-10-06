package app.friendly.assistant.ui.components.message.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Computer

object DesktopScreenshotToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_screenshot"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Capture Desktop"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val success = context.content?.let {
            it.jsonObject["success"]?.jsonPrimitive?.contentOrNull
        }
        val text = if (success == "true") "Captured remote Linux desktop" else "Desktop screenshot request"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopClickToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_click"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Desktop Click"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val x = context.content?.let { it.jsonObject["x"]?.jsonPrimitive?.contentOrNull }
        val y = context.content?.let { it.jsonObject["y"]?.jsonPrimitive?.contentOrNull }
        val button = context.content?.let { it.jsonObject["button"]?.jsonPrimitive?.contentOrNull } ?: "left"
        val text = if (x != null && y != null) "Clicked $button button at ($x, $y)" else "Dispatched desktop mouse click"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopTypeToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_type"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Type Keystrokes"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val chars = context.content?.let { it.jsonObject["characters_typed"]?.jsonPrimitive?.contentOrNull }
        val text = if (!chars.isNullOrBlank()) "Typed $chars characters into active window" else "Dispatched keystrokes to active window"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopHotkeyToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_hotkey"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Press Hotkey"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val keys = context.content?.let {
            it.jsonObject["keys"]?.jsonArray?.mapNotNull { el -> el.jsonPrimitive.contentOrNull }
        }
        val text = if (!keys.isNullOrEmpty()) "Pressed ${keys.joinToString(" + ")}" else "Executed keyboard shortcut"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopBrowserOpenToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_browser_open"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Launch Browser"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val url = context.content?.let { it.jsonObject["url"]?.jsonPrimitive?.contentOrNull }
        val text = if (!url.isNullOrBlank()) "Opened $url in Chromium" else "Launched Chromium browser"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopStreamStartToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_stream_start"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Start Desktop Stream"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val mode = context.content?.let { it.jsonObject["mode"]?.jsonPrimitive?.contentOrNull } ?: "view"
        Text(text = "Started live desktop stream (mode: $mode)", style = MaterialTheme.typography.bodySmall)
    }
}

object DesktopStreamStopToolUI : ToolUIRenderer {
    override val toolName: String = "desktop_stream_stop"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.Computer

    @Composable
    override fun title(context: ToolUIContext): String = "Stop Desktop Stream"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        Text(text = "Stopped live desktop stream", style = MaterialTheme.typography.bodySmall)
    }
}
