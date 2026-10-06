package app.friendly.assistant.service.phone.flow

import app.friendly.assistant.service.phone.ElementSelector
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

object MaestroFlowParser {

    /**
     * Parses a YAML or JSON string into a list of [MaestroCommand]s, applying [params] for `${KEY}` substitutions.
     */
    fun parse(content: String, params: Map<String, String> = emptyMap()): List<MaestroCommand> {
        val interpolated = interpolateVariables(content.trim(), params)
        val parts = interpolated.split(Regex("""(?m)^---\s*${'$'}"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (parts.isEmpty()) return emptyList()

        val yaml = createYaml()
        var headerAppId: String? = null
        val bodyYaml: String

        if (parts.size > 1) {
            val headerDoc = runCatching { yaml.load<Any?>(parts[0]) }.getOrNull()
            if (headerDoc is Map<*, *>) {
                headerAppId = (headerDoc["appId"] ?: headerDoc["app"])?.toString()
            }
            bodyYaml = parts.last()
        } else {
            bodyYaml = parts[0]
        }

        val loaded = runCatching { yaml.load<Any?>(bodyYaml) }.getOrNull() ?: return emptyList()
        val parsed = parseLoadedStructure(loaded)
        if (!headerAppId.isNullOrBlank() && parsed.none { it is MaestroCommand.LaunchApp }) {
            return listOf(MaestroCommand.LaunchApp(appName = headerAppId)) + parsed
        }
        return parsed
    }

    private fun parseLoadedStructure(loaded: Any?): List<MaestroCommand> {
        val commandList = when (loaded) {
            is List<*> -> loaded
            is Map<*, *> -> {
                // If it's a top-level map with a 'commands' or 'flow' key, extract that list
                val nested = loaded["commands"] ?: loaded["flow"] ?: loaded["steps"]
                if (nested is List<*>) nested else listOf(loaded)
            }
            else -> return emptyList()
        }

        return commandList.mapNotNull { item ->
            when (item) {
                is String -> parseSimpleStringCommand(item)
                is Map<*, *> -> parseMapCommand(item)
                else -> null
            }
        }
    }

    private fun parseSimpleStringCommand(name: String): MaestroCommand? {
        val clean = name.trim().lowercase()
        return when (clean) {
            "back" -> MaestroCommand.PressKey("back")
            "home" -> MaestroCommand.PressKey("home")
            "recents" -> MaestroCommand.PressKey("recents")
            "scroll" -> MaestroCommand.Scroll("down")
            else -> null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseMapCommand(map: Map<*, *>): MaestroCommand? {
        val entry = map.entries.firstOrNull() ?: return null
        val cmdName = entry.key?.toString()?.trim() ?: return null
        val value = entry.value

        return when (cmdName) {
            "launchApp" -> parseLaunchApp(value)
            "tapOn" -> parseTapOn(value, repeat = 1, longPress = false)
            "doubleTapOn" -> parseTapOn(value, repeat = 2, longPress = false)
            "longPressOn" -> parseTapOn(value, repeat = 1, longPress = true)
            "inputText" -> parseInputText(value)
            "eraseText" -> parseEraseText(value)
            "pressKey" -> parsePressKey(value)
            "back" -> MaestroCommand.PressKey("back")
            "home" -> MaestroCommand.PressKey("home")
            "recents" -> MaestroCommand.PressKey("recents")
            "scroll" -> parseScroll(value)
            "scrollUntilVisible" -> parseScrollUntilVisible(value)
            "assertVisible" -> parseAssertVisible(value, expectVisible = true)
            "assertNotVisible" -> parseAssertVisible(value, expectVisible = false)
            "sleep" -> parseSleep(value)
            "repeat" -> parseRepeat(value)
            else -> null
        }
    }

    private fun parseLaunchApp(value: Any?): MaestroCommand.LaunchApp {
        return when (value) {
            is String -> MaestroCommand.LaunchApp(appName = value)
            is Map<*, *> -> {
                val app = (value["appName"] ?: value["appId"] ?: value["app"])?.toString().orEmpty()
                val phone = value["phone"]?.toString()
                val query = value["query"]?.toString()
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                MaestroCommand.LaunchApp(appName = app, phone = phone, query = query, optional = optional)
            }
            else -> MaestroCommand.LaunchApp(appName = "")
        }
    }

    private fun parseTapOn(value: Any?, repeat: Int, longPress: Boolean): MaestroCommand.TapOn {
        return when (value) {
            is String -> {
                val selector = parseSelector(value)
                MaestroCommand.TapOn(selector = selector, repeat = repeat, longPress = longPress)
            }
            is Map<*, *> -> {
                val point = parsePoint(value["point"])
                val selector = if (point == null) parseSelectorFromMap(value) else null
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                val count = (value["repeat"] as? Number)?.toInt() ?: repeat
                MaestroCommand.TapOn(
                    selector = selector,
                    point = point,
                    repeat = count,
                    longPress = longPress || (value["longPress"]?.toString()?.toBooleanStrictOrNull() ?: false),
                    optional = optional,
                )
            }
            else -> MaestroCommand.TapOn(selector = null)
        }
    }

    private fun parseInputText(value: Any?): MaestroCommand.InputText {
        return when (value) {
            is String -> MaestroCommand.InputText(text = value)
            is Map<*, *> -> {
                val text = (value["text"] ?: value["value"])?.toString().orEmpty()
                val clearFirst = value["clearFirst"]?.toString()?.toBooleanStrictOrNull() ?: false
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                MaestroCommand.InputText(text = text, clearFirst = clearFirst, optional = optional)
            }
            else -> MaestroCommand.InputText(text = "")
        }
    }

    private fun parseEraseText(value: Any?): MaestroCommand.EraseText {
        return when (value) {
            is Number -> MaestroCommand.EraseText(characters = value.toInt())
            is Map<*, *> -> {
                val chars = (value["characters"] as? Number)?.toInt() ?: 50
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                MaestroCommand.EraseText(characters = chars, optional = optional)
            }
            else -> MaestroCommand.EraseText()
        }
    }

    private fun parsePressKey(value: Any?): MaestroCommand.PressKey {
        return when (value) {
            is String -> MaestroCommand.PressKey(key = value)
            is Map<*, *> -> {
                val key = (value["key"] ?: value["action"])?.toString().orEmpty()
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                MaestroCommand.PressKey(key = key, optional = optional)
            }
            else -> MaestroCommand.PressKey(key = "back")
        }
    }

    private fun parseScroll(value: Any?): MaestroCommand.Scroll {
        return when (value) {
            is String -> MaestroCommand.Scroll(direction = value)
            is Map<*, *> -> {
                val dir = value["direction"]?.toString() ?: "down"
                val duration = (value["durationMs"] as? Number)?.toLong() ?: 300L
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                MaestroCommand.Scroll(direction = dir, durationMs = duration, optional = optional)
            }
            else -> MaestroCommand.Scroll()
        }
    }

    private fun parseScrollUntilVisible(value: Any?): MaestroCommand.ScrollUntilVisible {
        val map = value as? Map<*, *> ?: emptyMap<Any, Any>()
        val selector = parseSelectorFromMap(map)
        val dir = (map["direction"] ?: "down").toString()
        val maxSwipes = (map["maxSwipes"] as? Number)?.toInt() ?: 5
        val optional = map["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
        return MaestroCommand.ScrollUntilVisible(
            selector = selector,
            direction = dir,
            maxSwipes = maxSwipes,
            optional = optional,
        )
    }

    private fun parseAssertVisible(value: Any?, expectVisible: Boolean): MaestroCommand {
        return when (value) {
            is String -> {
                val selector = parseSelector(value)
                if (expectVisible) {
                    MaestroCommand.AssertVisible(selector = selector)
                } else {
                    MaestroCommand.AssertNotVisible(selector = selector)
                }
            }
            is Map<*, *> -> {
                val selector = parseSelectorFromMap(value)
                val timeout = (value["timeout"] as? Number)?.toLong()
                    ?: (value["timeoutMs"] as? Number)?.toLong()
                    ?: 3000L
                val optional = value["optional"]?.toString()?.toBooleanStrictOrNull() ?: false
                if (expectVisible) {
                    MaestroCommand.AssertVisible(selector = selector, timeoutMs = timeout, optional = optional)
                } else {
                    MaestroCommand.AssertNotVisible(selector = selector, timeoutMs = timeout, optional = optional)
                }
            }
            else -> MaestroCommand.AssertVisible(selector = ElementSelector())
        }
    }

    private fun parseSleep(value: Any?): MaestroCommand.Sleep {
        return when (value) {
            is Number -> MaestroCommand.Sleep(durationMs = value.toLong())
            is Map<*, *> -> {
                val ms = (value["durationMs"] as? Number)?.toLong()
                    ?: (value["ms"] as? Number)?.toLong()
                    ?: 1000L
                MaestroCommand.Sleep(durationMs = ms)
            }
            else -> MaestroCommand.Sleep(durationMs = 1000L)
        }
    }

    private fun parseRepeat(value: Any?): MaestroCommand.Repeat {
        val map = value as? Map<*, *> ?: return MaestroCommand.Repeat(times = 1, commands = emptyList())
        val times = (map["times"] as? Number)?.toInt() ?: 1
        val commandsRaw = map["commands"] ?: map["flow"]
        val subCommands = parseLoadedStructure(commandsRaw)
        return MaestroCommand.Repeat(times = times, commands = subCommands)
    }

    private fun parseSelector(query: String): ElementSelector {
        val trimmed = query.trim()
        return if (trimmed.contains(":id/") || trimmed.startsWith("id:")) {
            ElementSelector(viewId = trimmed.removePrefix("id:").trim())
        } else {
            ElementSelector(query = trimmed)
        }
    }

    private fun parseSelectorFromMap(map: Map<*, *>): ElementSelector {
        val elementRaw = map["element"]
        if (elementRaw is Map<*, *>) {
            return parseSelectorFromMap(elementRaw)
        }
        val query = (elementRaw ?: map["query"] ?: map["text"])?.toString()
        val viewId = (map["id"] ?: map["viewId"])?.toString()
        val containsText = map["containsText"]?.toString()
        val regex = map["regex"]?.toString()
        val index = (map["index"] as? Number)?.toInt() ?: 0
        val clickableOnly = map["clickable"]?.toString()?.toBooleanStrictOrNull() ?: false
        val enabledOnly = map["enabled"]?.toString()?.toBooleanStrictOrNull() ?: false

        return ElementSelector(
            query = query,
            viewId = viewId,
            containsText = containsText,
            regex = regex,
            index = index,
            clickableOnly = clickableOnly,
            enabledOnly = enabledOnly,
        )
    }

    private fun parsePoint(value: Any?): MaestroCommand.Point? {
        return when (value) {
            is Map<*, *> -> {
                val x = (value["x"] as? Number)?.toFloat() ?: return null
                val y = (value["y"] as? Number)?.toFloat() ?: return null
                MaestroCommand.Point(x, y)
            }
            is String -> {
                val parts = value.split(",", "x")
                if (parts.size == 2) {
                    val x = parts[0].trim().toFloatOrNull() ?: return null
                    val y = parts[1].trim().toFloatOrNull() ?: return null
                    MaestroCommand.Point(x, y)
                } else null
            }
            else -> null
        }
    }

    private fun interpolateVariables(content: String, params: Map<String, String>): String {
        if (params.isEmpty()) return content
        var result = content
        for ((key, value) in params) {
            result = result.replace("\${$key}", value)
        }
        return result
    }

    private fun stripFrontmatter(content: String): String {
        if (!content.startsWith("---")) return content
        val end = content.indexOf("\n---", startIndex = 3)
        if (end != -1) {
            return content.substring(end + 4).trim()
        }
        return content.removePrefix("---").trim()
    }

    private fun createYaml(): Yaml {
        val options = LoaderOptions().apply {
            isAllowDuplicateKeys = false
            maxAliasesForCollections = 50
            nestingDepthLimit = 50
            codePointLimit = 1_000_000
        }
        return Yaml(SafeConstructor(options))
    }
}
