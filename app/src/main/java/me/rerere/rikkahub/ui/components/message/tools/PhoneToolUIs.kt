package me.rerere.rikkahub.ui.components.message.tools

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.SmartPhone01

object PhoneInspectScreenToolUI : ToolUIRenderer {
    override val toolName: String = "phone_inspect_screen"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Inspect Screen"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val pkg = context.content?.let {
            it.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull
        }
        val text = if (!pkg.isNullOrBlank()) "App: $pkg" else "Inspected active phone UI"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneClickToolUI : ToolUIRenderer {
    override val toolName: String = "phone_click"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Tap Screen"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val x = context.content?.let { it.jsonObject["x"]?.jsonPrimitive?.contentOrNull }
        val y = context.content?.let { it.jsonObject["y"]?.jsonPrimitive?.contentOrNull }
        val text = if (x != null && y != null) "Tapped at ($x, $y)" else "Dispatched screen tap"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneSwipeToolUI : ToolUIRenderer {
    override val toolName: String = "phone_swipe"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Swipe Screen"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val dir = context.content?.let { it.jsonObject["direction"]?.jsonPrimitive?.contentOrNull }
        val text = if (!dir.isNullOrBlank()) "Swiped $dir" else "Dispatched screen swipe"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneTypeTextToolUI : ToolUIRenderer {
    override val toolName: String = "phone_type_text"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Type Text"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val typed = context.content?.let { it.jsonObject["typed"]?.jsonPrimitive?.contentOrNull }
        val text = if (!typed.isNullOrBlank()) "Typed: \"$typed\"" else "Input text into focused field"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhonePressKeyToolUI : ToolUIRenderer {
    override val toolName: String = "phone_press_key"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Press System Button"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val key = context.content?.let { it.jsonObject["key"]?.jsonPrimitive?.contentOrNull }
        val text = if (!key.isNullOrBlank()) "Pressed $key" else "Pressed system navigation key"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneLaunchAppToolUI : ToolUIRenderer {
    override val toolName: String = "phone_launch_app"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Launch App"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val msg = context.content?.let { it.jsonObject["message"]?.jsonPrimitive?.contentOrNull }
        val app = context.content?.let { it.jsonObject["app_name"]?.jsonPrimitive?.contentOrNull }
        val text = msg ?: if (!app.isNullOrBlank()) "Opened $app" else "Launched application"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneScreenshotToolUI : ToolUIRenderer {
    override val toolName: String = "phone_screenshot"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Capture Screen"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        Text(text = "Phone screenshot captured", style = MaterialTheme.typography.bodySmall)
    }
}
