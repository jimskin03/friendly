package app.friendly.assistant.ui.components.message.tools

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
        val returned = context.content?.let {
            it.jsonObject["returned_nodes"]?.jsonPrimitive?.contentOrNull
        }
        val text = when {
            !pkg.isNullOrBlank() && !returned.isNullOrBlank() -> "Read $pkg ($returned text nodes)"
            !pkg.isNullOrBlank() -> "App: $pkg"
            else -> "Read the screen as text"
        }
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


object PhoneBringAppToFrontToolUI : ToolUIRenderer {
    override val toolName: String = "phone_bring_app_to_front"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Bring App To Front"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val msg = context.content?.let { it.jsonObject["message"]?.jsonPrimitive?.contentOrNull }
        val app = context.content?.let { it.jsonObject["app_name"]?.jsonPrimitive?.contentOrNull }
            ?: context.content?.let { it.jsonObject["package_name"]?.jsonPrimitive?.contentOrNull }
        val text = msg ?: if (!app.isNullOrBlank()) "Brought $app to front" else "Brought app to front"
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
        val mode = context.content?.let {
            it.jsonObject["mode"]?.jsonPrimitive?.contentOrNull
        }
        val text = if (mode == "text") "Read the screen as text" else "Phone screenshot captured"
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneAssertVisibleToolUI : ToolUIRenderer {
    override val toolName: String = "phone_assert_visible"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Assert Visible"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val visible = context.content?.let { it.jsonObject["visible"]?.jsonPrimitive?.contentOrNull }
        val query = context.arguments.let { it.jsonObject["query"]?.jsonPrimitive?.contentOrNull }
            ?: context.arguments.let { it.jsonObject["view_id"]?.jsonPrimitive?.contentOrNull }
        val text = if (visible == "true") {
            if (!query.isNullOrBlank()) "Visible: \"$query\"" else "Element is visible"
        } else {
            if (!query.isNullOrBlank()) "Not visible: \"$query\"" else "Element not visible"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneScrollUntilVisibleToolUI : ToolUIRenderer {
    override val toolName: String = "phone_scroll_until_visible"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Scroll Until Visible"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val found = context.content?.let { it.jsonObject["found"]?.jsonPrimitive?.contentOrNull }
        val swipes = context.content?.let { it.jsonObject["swipes_performed"]?.jsonPrimitive?.contentOrNull } ?: "0"
        val dir = context.content?.let { it.jsonObject["direction"]?.jsonPrimitive?.contentOrNull } ?: "down"
        val text = if (found == "true") {
            "Found element after $swipes scroll(s) $dir"
        } else {
            "Not found after $swipes scroll(s) $dir"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneRunFlowToolUI : ToolUIRenderer {
    override val toolName: String = "phone_run_flow"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Run Automation Flow"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val success = context.content?.let { it.jsonObject["success"]?.jsonPrimitive?.contentOrNull }
        val executed = context.content?.let { it.jsonObject["executed_steps"]?.jsonPrimitive?.contentOrNull }
        val total = context.content?.let { it.jsonObject["total_steps"]?.jsonPrimitive?.contentOrNull }
        val err = context.content?.let { it.jsonObject["error"]?.jsonPrimitive?.contentOrNull }
        val text = if (success == "true") {
            if (executed != null && total != null) "Completed $executed/$total flow steps" else "Flow completed successfully"
        } else {
            err ?: "Flow execution failed"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

object PhoneManageFlowsToolUI : ToolUIRenderer {
    override val toolName: String = "phone_manage_flows"
    override fun icon(context: ToolUIContext): ImageVector = HugeIcons.SmartPhone01

    @Composable
    override fun title(context: ToolUIContext): String = "Manage Flows"

    override fun hasSummary(context: ToolUIContext): Boolean = true

    @Composable
    override fun Summary(context: ToolUIContext) {
        val action = context.arguments.let { it.jsonObject["action"]?.jsonPrimitive?.contentOrNull } ?: "list"
        val name = context.arguments.let { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        val count = context.content?.let { it.jsonObject["count"]?.jsonPrimitive?.contentOrNull }
        val text = when (action.lowercase()) {
            "list" -> if (count != null) "Saved flows: $count" else "Listed saved flows"
            "get" -> if (!name.isNullOrBlank()) "Loaded flow: $name" else "Loaded flow"
            "save" -> if (!name.isNullOrBlank()) "Saved flow: $name" else "Saved flow"
            "delete" -> if (!name.isNullOrBlank()) "Deleted flow: $name" else "Deleted flow"
            else -> "Managed flow: $action"
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
