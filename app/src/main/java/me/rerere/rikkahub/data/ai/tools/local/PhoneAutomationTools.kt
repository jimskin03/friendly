package me.rerere.rikkahub.data.ai.tools.local

import android.content.Context
import android.graphics.Bitmap
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.service.phone.ElementSelector
import me.rerere.rikkahub.service.phone.PhoneAutomationLoopGuard
import me.rerere.rikkahub.service.phone.PhoneAutomationService
import me.rerere.rikkahub.service.phone.PhoneAutomationStep
import me.rerere.rikkahub.service.phone.PhoneCallController
import me.rerere.rikkahub.service.phone.PhoneElementMatcher
import me.rerere.rikkahub.service.phone.ScreenNodeInfo
import me.rerere.rikkahub.service.phone.flow.MaestroFlowExecutor
import me.rerere.rikkahub.service.phone.flow.MaestroFlowParser
import me.rerere.rikkahub.service.phone.flow.MaestroFlowRepository
import java.io.ByteArrayOutputStream

internal fun buildPhoneInspectScreenTool(): Tool = Tool(
    name = "phone_inspect_screen",
    description = """
        Read the phone screen as text. This is the default inspect path and does not take a screenshot.
        Returns the visible accessibility tree: text, content description, clickable, and bounds,
        plus the active package and window title. A huge tree is truncated with a node count.
        Use phone_screenshot with screenshot=true only when a bitmap is required.
        Requires the Phone Automation Accessibility service to be active.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList()
        )
    },
    execute = {
        val service = PhoneAutomationService.instance
        if (service == null) {
            return@Tool listOf(UIMessagePart.Text(accessibilityInactivePayload()))
        }
        listOf(screenTextMessage(service))
    }
)

internal fun buildPhoneClickTool(): Tool = Tool(
    name = "phone_click",
    description = """
        Tap or click on the phone screen at specific (x, y) coordinates or on an interactive UI element.
        Provide (x, y) coordinates, or provide 'query' (text/label of the button to click),
        or 'view_id' (resource ID like 'search_button'), or 'node_id' from phone_inspect_screen.
        Optional 'index' (default 0) selects among multiple matches.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("x", buildJsonObject {
                    put("type", "number")
                    put("description", "X coordinate on screen in pixels")
                })
                put("y", buildJsonObject {
                    put("type", "number")
                    put("description", "Y coordinate on screen in pixels")
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Text or description of the UI element to click if (x, y) is not specified")
                })
                put("view_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Resource ID or View ID of the element to click")
                })
                put("node_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Element ID from phone_inspect_screen")
                })
                put("index", buildJsonObject {
                    put("type", "integer")
                    put("description", "0-based index if multiple elements match query (default: 0)")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            val payload = buildJsonObject {
                put("error", "Accessibility service is not running. Please enable Friendly Phone Automation in Android Accessibility settings.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        var targetX = params["x"]?.jsonPrimitive?.doubleOrNull?.toFloat()
        var targetY = params["y"]?.jsonPrimitive?.doubleOrNull?.toFloat()
        var matchedLabel = ""

        if (targetX == null || targetY == null) {
            val query = params["query"]?.jsonPrimitive?.contentOrNull
            val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull
            val nodeId = params["node_id"]?.jsonPrimitive?.intOrNull
            val index = params["index"]?.jsonPrimitive?.intOrNull ?: 0

            val inspection = service.inspectScreen()
            val allNodes = inspection.interactiveElements + inspection.textElements
            val selector = ElementSelector(
                query = query,
                viewId = viewId,
                nodeId = nodeId,
                index = index,
            )
            val target = PhoneElementMatcher.findBestMatch(allNodes, selector)

            if (target != null) {
                targetX = target.centerX.toFloat()
                targetY = target.centerY.toFloat()
                matchedLabel = target.text.ifBlank { target.description }.ifBlank { target.viewId }
            }
        }

        if (targetX == null || targetY == null) {
            val payload = buildJsonObject {
                put("error", "Target coordinates not found. Specify (x, y) or run phone_inspect_screen first.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        service.refuseIfActingOnFriendly(targetX, targetY)?.let { reason ->
            val payload = buildJsonObject {
                put("error", reason)
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val clicked = service.click(targetX, targetY)
        service.awaitIdle(250L)
        val payload = buildJsonObject {
            put("success", clicked)
            put("x", targetX)
            put("y", targetY)
            if (matchedLabel.isNotBlank()) put("matched", matchedLabel)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneSwipeTool(context: Context): Tool = Tool(
    name = "phone_swipe",
    description = """
        Swipe or scroll on the phone screen.
        Provide 'direction' ('up', 'down', 'left', 'right') or custom start and end coordinates.
        'up' scrolls down through content; 'down' scrolls up.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("direction", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("up")
                        add("down")
                        add("left")
                        add("right")
                    })
                    put("description", "Direction to swipe: 'up', 'down', 'left', 'right'")
                })
                put("start_x", buildJsonObject { put("type", "number") })
                put("start_y", buildJsonObject { put("type", "number") })
                put("end_x", buildJsonObject { put("type", "number") })
                put("end_y", buildJsonObject { put("type", "number") })
                put("duration_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Duration of swipe gesture in milliseconds (default: 300)")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            val payload = buildJsonObject {
                put("error", "Accessibility service is not running. Please enable Friendly Phone Automation in Android Accessibility settings.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val duration = params["duration_ms"]?.jsonPrimitive?.longOrNull ?: 300L

        val direction = params["direction"]?.jsonPrimitive?.contentOrNull?.lowercase()
        val (startX, startY, endX, endY) = when (direction) {
            "up" -> listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
            "down" -> listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            "left" -> listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
            "right" -> listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
            else -> {
                val sx = params["start_x"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: (width * 0.5f)
                val sy = params["start_y"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: (height * 0.75f)
                val ex = params["end_x"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: (width * 0.5f)
                val ey = params["end_y"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: (height * 0.25f)
                listOf(sx, sy, ex, ey)
            }
        }

        service.refuseIfActingOnFriendly(startX, startY)?.let { reason ->
            val payload = buildJsonObject {
                put("error", reason)
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val swiped = service.swipe(startX, startY, endX, endY, duration)
        val payload = buildJsonObject {
            put("success", swiped)
            if (direction != null) put("direction", direction)
            put("start_x", startX)
            put("start_y", startY)
            put("end_x", endX)
            put("end_y", endY)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneTypeTextTool(): Tool = Tool(
    name = "phone_type_text",
    description = """
        Type text into the currently focused editable input field on the phone screen.
        Optionally set 'clear_first' to true to replace existing text.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "Text to type into the focused input")
                })
                put("clear_first", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to clear existing text before typing")
                })
            },
            required = listOf("text")
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            val payload = buildJsonObject {
                put("error", "Accessibility service is not running. Please enable Friendly Phone Automation in Android Accessibility settings.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")
        val clearFirst = params["clear_first"]?.jsonPrimitive?.booleanOrNull ?: false

        service.refuseIfActingOnFriendly()?.let { reason ->
            val payload = buildJsonObject {
                put("error", reason)
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val typed = service.typeText(text, clearFirst)
        val payload = buildJsonObject {
            put("success", typed)
            put("typed", text)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhonePressKeyTool(): Tool = Tool(
    name = "phone_press_key",
    description = """
        Press a system navigation key on the phone.
        Accepted keys: 'back', 'home', 'recents', 'notifications', 'quick_settings'.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("key", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray {
                        add("back")
                        add("home")
                        add("recents")
                        add("notifications")
                        add("quick_settings")
                    })
                    put("description", "System key action to perform")
                })
            },
            required = listOf("key")
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            val payload = buildJsonObject {
                put("error", "Accessibility service is not running. Please enable Friendly Phone Automation in Android Accessibility settings.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val key = params["key"]?.jsonPrimitive?.contentOrNull ?: error("key is required")
        val success = service.pressKey(key)

        val payload = buildJsonObject {
            put("success", success)
            put("key", key)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneLaunchAppTool(context: Context): Tool = Tool(
    name = "phone_launch_app",
    description = """
        Launch an application on the user's phone by application name or package name, without a screenshot.
        Known actions open by deep link first: WhatsApp plus a phone number uses whatsapp://send?phone=<digits>;
        WhatsApp alone opens com.whatsapp; Google Maps with a query uses geo:0,0?q= or google.navigation for directions;
        call or dial plus a phone number opens the dialer with tel: and does not place the call (use place_call to dial).
        Emergency numbers are refused. Unknown apps still match by name or package. If a deep link has no handler, the app is launched by name.
        Examples: 'YouTube', 'Settings', 'Chrome', 'WhatsApp', 'Google Maps'.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("app_name", buildJsonObject {
                    put("type", "string")
                    put("description", "Name or package ID of the application to open. May include the place or the words call/dial.")
                })
                put("phone", buildJsonObject {
                    put("type", "string")
                    put("description", "Phone number for a WhatsApp chat or for opening the dialer. Digits, optional leading +.")
                })
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Google Maps search or directions query, when app_name is Maps.")
                })
            },
            required = listOf("app_name")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val appName = params["app_name"]?.jsonPrimitive?.contentOrNull ?: error("app_name is required")
        val phone = params["phone"]?.jsonPrimitive?.contentOrNull
        val placeQuery = params["query"]?.jsonPrimitive?.contentOrNull
        val (success, message) = PhoneAutomationService.launchApp(context, appName, phone, placeQuery)

        val payload = buildJsonObject {
            put("success", success)
            put("message", message)
            put("app_name", appName)
            if (!phone.isNullOrBlank()) put("phone", phone)
            if (!placeQuery.isNullOrBlank()) put("query", placeQuery)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneScreenshotTool(filesManager: FilesManager): Tool = Tool(
    name = "phone_screenshot",
    description = """
        Read the phone screen. The default is the visible text tree (text, content description, clickable, bounds)
        and does not take a bitmap. Set screenshot to true only when a bitmap is required.
        Prefer phone_inspect_screen for the same text result. Requires the Phone Automation Accessibility service.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("screenshot", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Set true to capture a bitmap. Omit or false to read the screen as text. Default false.")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            return@Tool listOf(UIMessagePart.Text(accessibilityInactivePayload()))
        }
        val screenshotFlag = args.jsonObject["screenshot"]?.jsonPrimitive
        val wantBitmap = screenshotFlag?.booleanOrNull
            ?: screenshotFlag?.contentOrNull.equals("true", ignoreCase = true)
        if (!wantBitmap) {
            return@Tool listOf(screenTextMessage(service))
        }

        val bitmap = service.takeScreenshot()
        if (bitmap == null) {
            val payload = buildJsonObject {
                put("error", "Failed to capture screenshot (requires Android 11+ and accessibility screenshot permission).")
                put("success", false)
                put("mode", "image")
                put("screenshot", true)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
        val bytes = outputStream.toByteArray()

        val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
        listOf(
            UIMessagePart.Image(url = uris.first().toString()),
            UIMessagePart.Text(
                buildJsonObject {
                    put("success", true)
                    put("mode", "image")
                    put("screenshot", true)
                    put("description", "Phone screenshot captured successfully")
                }.toString()
            )
        )
    }
)

internal fun buildPhoneAssertVisibleTool(): Tool = Tool(
    name = "phone_assert_visible",
    description = """
        Assert that a specific UI element is visible on the phone screen.
        Waits up to 'timeout_ms' (default 3000ms) for the element to appear.
        Provide 'query' (text or content description) and/or 'view_id' (resource ID).
        Set 'optional' to true to check visibility without throwing a failure.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Text or description of the element to assert visible")
                })
                put("view_id", buildJsonObject {
                    put("type", "string")
                    put("description", "Resource ID / View ID of the element (e.g. 'search_button')")
                })
                put("timeout_ms", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum time in ms to wait for the element (default 3000)")
                })
                put("optional", buildJsonObject {
                    put("type", "boolean")
                    put("description", "If true, does not fail if element is not found. Default false.")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            return@Tool listOf(UIMessagePart.Text(accessibilityInactivePayload()))
        }
        val params = args.jsonObject
        val query = params["query"]?.jsonPrimitive?.contentOrNull
        val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull
        val timeoutMs = params["timeout_ms"]?.jsonPrimitive?.longOrNull ?: 3000L
        val optional = params["optional"]?.jsonPrimitive?.booleanOrNull ?: false

        val selector = ElementSelector(query = query, viewId = viewId)
        val startTime = System.currentTimeMillis()
        val node = service.waitForNode(timeoutMs = timeoutMs) { candidate ->
            PhoneElementMatcher.findBestMatch(listOf(candidate), selector) != null
        }
        val elapsed = System.currentTimeMillis() - startTime
        val found = node != null

        val payload = buildJsonObject {
            put("success", found || optional)
            put("visible", found)
            put("elapsed_ms", elapsed)
            if (node != null) {
                put("matched_node", buildJsonObject {
                    put("id", node.id)
                    put("text", node.text)
                    put("view_id", node.viewId)
                    put("x", node.centerX)
                    put("y", node.centerY)
                })
            } else {
                put("message", "Element not visible within ${timeoutMs}ms: ${query ?: viewId}")
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneScrollUntilVisibleTool(context: Context): Tool = Tool(
    name = "phone_scroll_until_visible",
    description = """
        Scroll on the phone screen repeatedly until a specific element is found and visible.
        Provide 'query' (text or description) and/or 'view_id'.
        Direction can be 'down' (default) or 'up'.
        Default max_swipes is 5.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "Text or description of the target element")
                })
                put("view_id", buildJsonObject {
                    put("type", "string")
                    put("description", "View ID of the target element")
                })
                put("direction", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("up"); add("down"); add("left"); add("right") })
                    put("description", "Direction to swipe: 'down' (scrolls down) or 'up'")
                })
                put("max_swipes", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum scroll attempts (default 5)")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            return@Tool listOf(UIMessagePart.Text(accessibilityInactivePayload()))
        }
        val params = args.jsonObject
        val query = params["query"]?.jsonPrimitive?.contentOrNull
        val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull
        val direction = params["direction"]?.jsonPrimitive?.contentOrNull ?: "down"
        val maxSwipes = (params["max_swipes"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 15)

        val selector = ElementSelector(query = query, viewId = viewId)
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()
        val (sx, sy, ex, ey) = when (direction.lowercase()) {
            "up" -> listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            "left" -> listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
            "right" -> listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
            else -> listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
        }

        var matchedNode: ScreenNodeInfo? = null
        var swipesDone = 0

        for (attempt in 0..maxSwipes) {
            val inspection = service.inspectScreen()
            val all = inspection.interactiveElements + inspection.textElements
            matchedNode = PhoneElementMatcher.findBestMatch(all, selector)
            if (matchedNode != null) break
            if (attempt < maxSwipes) {
                service.swipe(sx, sy, ex, ey, 300L)
                swipesDone++
                service.awaitIdle(400L)
            }
        }

        val found = matchedNode != null
        val payload = buildJsonObject {
            put("success", found)
            put("found", found)
            put("swipes_performed", swipesDone)
            put("direction", direction)
            if (matchedNode != null) {
                put("matched_node", buildJsonObject {
                    put("id", matchedNode.id)
                    put("text", matchedNode.text)
                    put("view_id", matchedNode.viewId)
                    put("x", matchedNode.centerX)
                    put("y", matchedNode.centerY)
                })
            } else {
                put("error", "Element not found after $swipesDone swipes")
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneRunFlowTool(context: Context): Tool = Tool(
    name = "phone_run_flow",
    description = """
        Execute a Maestro-style automation flow on the phone in a single batch.
        Accepts declarative YAML or JSON commands (launchApp, tapOn, inputText, pressKey, scrollUntilVisible, assertVisible, sleep, repeat).
        Provide 'flow' (inline YAML/JSON string) OR 'name' (name of a previously saved flow).
        Optional 'params' map replaces '${'$'}{KEY}' placeholders in the flow.
        Optional 'save_as' saves this flow under that name for future reuse.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("flow", buildJsonObject {
                    put("type", "string")
                    put("description", "YAML or JSON string of Maestro commands")
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Name of a saved flow to run")
                })
                put("params", buildJsonObject {
                    put("type", "object")
                    put("description", "Key-value map to replace \${KEY} in the flow")
                })
                put("save_as", buildJsonObject {
                    put("type", "string")
                    put("description", "Optional name to save this flow after execution")
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val service = PhoneAutomationService.instance
        if (service == null) {
            return@Tool listOf(UIMessagePart.Text(accessibilityInactivePayload()))
        }
        val paramsObj = args.jsonObject
        var flowContent = paramsObj["flow"]?.jsonPrimitive?.contentOrNull
        val flowName = paramsObj["name"]?.jsonPrimitive?.contentOrNull
        val saveAs = paramsObj["save_as"]?.jsonPrimitive?.contentOrNull

        val bindings = mutableMapOf<String, String>()
        paramsObj["params"]?.jsonObject?.forEach { (k, v) ->
            bindings[k] = v.jsonPrimitive.contentOrNull ?: v.toString()
        }

        val repository = MaestroFlowRepository(context)
        if (flowContent.isNullOrBlank() && !flowName.isNullOrBlank()) {
            val saved = repository.getFlow(flowName)
            if (saved == null) {
                val err = buildJsonObject {
                    put("success", false)
                    put("error", "Saved flow \"$flowName\" not found")
                }
                return@Tool listOf(UIMessagePart.Text(err.toString()))
            }
            flowContent = saved.content
        }

        if (flowContent.isNullOrBlank()) {
            val err = buildJsonObject {
                put("success", false)
                put("error", "Neither 'flow' content nor valid 'name' was provided")
            }
            return@Tool listOf(UIMessagePart.Text(err.toString()))
        }

        val commands = MaestroFlowParser.parse(flowContent, bindings)
        if (commands.isEmpty()) {
            val err = buildJsonObject {
                put("success", false)
                put("error", "Failed to parse any valid commands from flow")
            }
            return@Tool listOf(UIMessagePart.Text(err.toString()))
        }

        val executor = MaestroFlowExecutor(service, context)
        val result = executor.execute(commands)

        if (!saveAs.isNullOrBlank() && result.success) {
            repository.saveFlow(saveAs, flowContent)
        }

        val payload = buildJsonObject {
            put("success", result.success)
            put("executed_steps", result.executedSteps)
            put("total_steps", result.totalSteps)
            put("duration_ms", result.durationMs)
            if (result.failedStepIndex != null) put("failed_step_index", result.failedStepIndex)
            if (result.error != null) put("error", result.error)
            if (result.packageName.isNotBlank()) put("package_name", result.packageName)
            put("steps", buildJsonArray {
                result.stepResults.forEach { step ->
                    add(buildJsonObject {
                        put("step", step.stepIndex)
                        put("command", step.commandName)
                        put("detail", step.detail)
                        put("success", step.success)
                        put("elapsed_ms", step.elapsedMs)
                        if (step.optionalSkipped) put("optional_skipped", true)
                    })
                }
            })
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)

internal fun buildPhoneManageFlowsTool(context: Context): Tool = Tool(
    name = "phone_manage_flows",
    description = """
        Manage saved Maestro-style automation flows on the phone.
        'action' can be 'list', 'get', 'save', or 'delete'.
        Allows teaching and remembering multi-step routines across sessions.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("action", buildJsonObject {
                    put("type", "string")
                    put("enum", buildJsonArray { add("list"); add("get"); add("save"); add("delete") })
                    put("description", "Action to perform: 'list', 'get', 'save', 'delete'")
                })
                put("name", buildJsonObject {
                    put("type", "string")
                    put("description", "Flow name (required for get, save, delete)")
                })
                put("content", buildJsonObject {
                    put("type", "string")
                    put("description", "YAML/JSON content when saving a flow")
                })
                put("description", buildJsonObject {
                    put("type", "string")
                    put("description", "Brief description of what the flow does (for save)")
                })
            },
            required = listOf("action")
        )
    },
    execute = { args ->
        val params = args.jsonObject
        val action = params["action"]?.jsonPrimitive?.contentOrNull ?: "list"
        val name = params["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val content = params["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val desc = params["description"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val repository = MaestroFlowRepository(context)
        val payload = when (action.lowercase()) {
            "list" -> {
                val list = repository.listFlows()
                buildJsonObject {
                    put("success", true)
                    put("count", list.size)
                    put("flows", buildJsonArray {
                        list.forEach { item ->
                            add(buildJsonObject {
                                put("name", item.name)
                                put("description", item.description)
                                put("step_count", item.stepCount)
                                put("last_modified", item.lastModified)
                            })
                        }
                    })
                }
            }
            "get" -> {
                val flow = repository.getFlow(name)
                if (flow != null) {
                    buildJsonObject {
                        put("success", true)
                        put("name", flow.name)
                        put("description", flow.description)
                        put("step_count", flow.stepCount)
                        put("content", flow.content)
                    }
                } else {
                    buildJsonObject {
                        put("success", false)
                        put("error", "Flow \"$name\" not found")
                    }
                }
            }
            "save" -> {
                if (name.isBlank() || content.isBlank()) {
                    buildJsonObject {
                        put("success", false)
                        put("error", "'name' and 'content' are required to save a flow")
                    }
                } else {
                    val saved = repository.saveFlow(name, content, desc)
                    buildJsonObject {
                        put("success", saved)
                        put("name", name)
                        put("message", if (saved) "Flow saved successfully" else "Failed to save flow")
                    }
                }
            }
            "delete" -> {
                val deleted = repository.deleteFlow(name)
                buildJsonObject {
                    put("success", deleted)
                    put("name", name)
                    put("message", if (deleted) "Flow deleted successfully" else "Failed to delete flow")
                }
            }
            else -> buildJsonObject {
                put("success", false)
                put("error", "Unknown action \"$action\"")
            }
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)



internal fun buildPlaceCallTool(phoneCallController: PhoneCallController): Tool = Tool(
    name = "place_call",
    description = """
        Place a cellular phone call to a phone number using the system Phone app.
        Uses the system Phone app and CALL_PHONE. Does not call WhatsApp or other chat apps.
        Refuses emergency numbers. Voice speech-to-text stays on during the call.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("number", buildJsonObject {
                    put("type", "string")
                    put("description", "Phone number to call, digits with optional leading +")
                })
            },
            required = listOf("number")
        )
    },
    execute = { args ->
        val number = args.jsonObject["number"]?.jsonPrimitive?.contentOrNull ?: error("number is required")
        val result = phoneCallController.placeCall(number)
        listOf(UIMessagePart.Text(callActionJson(result)))
    }
)

internal fun buildEndCallTool(phoneCallController: PhoneCallController): Tool = Tool(
    name = "end_call",
    description = """
        Try to hang up the current cellular call. Silent hang-up usually fails unless this app is the default dialer.
        Falls back to tapping End in the phone UI via accessibility, then opens the dialer. Not for WhatsApp.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
    },
    execute = {
        val result = phoneCallController.endCall()
        listOf(UIMessagePart.Text(callActionJson(result)))
    }
)

internal fun buildReadCallStateTool(phoneCallController: PhoneCallController): Tool = Tool(
    name = "read_call_state",
    description = """
        Read the cellular call state (idle, ringing, offhook) and whether call permissions are granted.
        The phone number is often hidden by Android. Does not report WhatsApp or other VoIP calls.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(properties = buildJsonObject {}, required = emptyList())
    },
    execute = {
        val snap = phoneCallController.currentSnapshot()
        val payload = buildJsonObject {
            put("access_enabled", snap.accessEnabled)
            put("auto_answer_attempt", snap.autoAnswerAttempt)
            put("status", snap.status.name.lowercase())
            if (!snap.number.isNullOrBlank()) put("number", snap.number)
            put("call_phone_granted", snap.callPhoneGranted)
            put("read_phone_state_granted", snap.readPhoneStateGranted)
            put("answer_phone_calls_granted", snap.answerPhoneCallsGranted)
            put("accessibility_active", snap.accessibilityActive)
            put("silent_answer_reliable", snap.silentAnswerReliable)
            put("silent_hangup_reliable", snap.silentHangupReliable)
            put("voice_stt", "Mini-indicator voice mode stays running. STT pauses only while the cellular call is off-hook, then resumes.")
            put("limitation", snap.limitation)
        }
        listOf(UIMessagePart.Text(payload.toString()))
    }
)


private const val SCREEN_TEXT_NODE_LIMIT = 100
private const val SCREEN_TEXT_CHAR_LIMIT = 200

private fun clipScreenText(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= SCREEN_TEXT_CHAR_LIMIT) return trimmed
    return trimmed.take(SCREEN_TEXT_CHAR_LIMIT) + "…"
}

private fun accessibilityInactivePayload(): String {
    return buildJsonObject {
        put("error", "Accessibility service is not running. Please enable Friendly Phone Automation in Android Accessibility settings.")
        put("service_active", false)
        put("success", false)
    }.toString()
}

private fun screenTextMessage(service: PhoneAutomationService): UIMessagePart.Text {
    val result = service.inspectScreen()
    val nodes = (result.interactiveElements + result.textElements).sortedBy { it.id }
    val returned = nodes.take(SCREEN_TEXT_NODE_LIMIT)
    val payload = buildJsonObject {
        put("mode", "text")
        put("screenshot", false)
        put("service_active", true)
        put("success", true)
        put("package_name", result.packageName)
        put("window_title", result.windowTitle)
        put("node_limit", SCREEN_TEXT_NODE_LIMIT)
        put("returned_nodes", returned.size)
        put("total_nodes", nodes.size)
        put("truncated", nodes.size > returned.size)
        put("omitted_count", (nodes.size - returned.size).coerceAtLeast(0))
        put("nodes", buildJsonArray {
            returned.forEach { node ->
                add(buildJsonObject {
                    put("id", node.id)
                    put("text", clipScreenText(node.text))
                    put("content_description", clipScreenText(node.description))
                    if (node.viewId.isNotBlank()) put("view_id", node.viewId)
                    if (node.className.isNotBlank()) put("class_name", node.className)
                    put("clickable", node.clickable)
                    if (node.editable) put("editable", true)
                    if (node.scrollable) put("scrollable", true)
                    if (!node.enabled) put("enabled", false)
                    if (node.checked) put("checked", true)
                    if (node.focused) put("focused", true)
                    put("bounds", buildJsonObject {
                        put("left", node.left)
                        put("top", node.top)
                        put("right", node.right)
                        put("bottom", node.bottom)
                    })
                })
            }
        })
    }
    return UIMessagePart.Text(payload.toString())
}

private fun callActionJson(result: me.rerere.rikkahub.service.phone.PhoneCallActionResult): String {
    return buildJsonObject {
        put("success", result.success)
        put("action", result.action)
        put("detail", result.detail)
        if (result.mode != null) put("mode", result.mode)
        if (result.attempts.isNotEmpty()) {
            put("attempts", buildJsonArray { result.attempts.forEach { add(it) } })
        }
        put("whatsapp", "not_supported")
        put("silent_answer_reliable", false)
        put("silent_hangup_reliable", false)
    }.toString()
}

internal fun Tool.withPhoneAutomationTracking(): Tool {
    val originalExecute = execute
    return copy(
        execute = { args ->
            val step = phoneAutomationStepFor(name, args)
            PhoneAutomationService.reportWorkStarted(step)
            try {
                val result = originalExecute(args)
                val text = result.filterIsInstance<UIMessagePart.Text>().joinToString(separator = "") { it.text }
                val failed = Regex("\"success\"\\s*:\\s*false").containsMatchIn(text) ||
                    Regex("\"error\"\\s*:").containsMatchIn(text)
                PhoneAutomationService.reportWorkFinished(success = !failed)
                // Only track successful actions; refused/errored calls must not inflate the loop counter.
                if (failed) result else maybeAnnotateLoopWarning(name, args, result)
            } catch (e: Exception) {
                PhoneAutomationService.reportWorkFinished(success = false)
                throw e
            }
        }
    )
}

/**
 * After a phone tool returns, fingerprint the screen and warn when the same
 * action has been repeated on an unchanged UI. Does not press BACK.
 */
private fun maybeAnnotateLoopWarning(
    toolName: String,
    args: kotlinx.serialization.json.JsonElement,
    result: List<UIMessagePart>,
): List<UIMessagePart> {
    val service = PhoneAutomationService.instance ?: return result
    val resultText = result.filterIsInstance<UIMessagePart.Text>().joinToString(separator = "") { it.text }
    val fingerprint = runCatching {
        PhoneAutomationLoopGuard.fingerprint(service.inspectScreen())
    }.getOrDefault("")
    val target = normalizeLoopTarget(toolName, args, resultText)
    val warning = PhoneAutomationLoopGuard.observe(toolName, target, fingerprint) ?: return result
    return appendToolWarning(result, warning)
}

private fun normalizeLoopTarget(
    toolName: String,
    args: kotlinx.serialization.json.JsonElement,
    resultText: String,
): String {
    val params = runCatching { args.jsonObject }.getOrNull() ?: return toolName
    return when (toolName) {
        "phone_click" -> {
            val matched = Regex("\"matched\"\\s*:\\s*\"([^\"]*)\"").find(resultText)?.groupValues?.getOrNull(1)
            val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull
            val query = params["query"]?.jsonPrimitive?.contentOrNull
            val x = params["x"]?.jsonPrimitive?.doubleOrNull?.toInt()?.div(40)
            val y = params["y"]?.jsonPrimitive?.doubleOrNull?.toInt()?.div(40)
            when {
                !matched.isNullOrBlank() -> "label:${matched.trim().lowercase()}"
                !viewId.isNullOrBlank() -> "view:${viewId.trim()}"
                !query.isNullOrBlank() -> "query:${query.trim().lowercase()}"
                x != null && y != null -> "xy:$x,$y"
                else -> "click"
            }
        }
        "phone_swipe" -> {
            val direction = params["direction"]?.jsonPrimitive?.contentOrNull?.lowercase()
            if (!direction.isNullOrBlank()) "dir:$direction" else "swipe"
        }
        "phone_type_text" -> {
            val clearFirst = params["clear_first"]?.jsonPrimitive?.booleanOrNull ?: false
            "type:clear=$clearFirst"
        }
        "phone_press_key" -> {
            val key = params["key"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            "key:$key"
        }
        "phone_assert_visible", "phone_scroll_until_visible" -> {
            val viewId = params["view_id"]?.jsonPrimitive?.contentOrNull
            val query = params["query"]?.jsonPrimitive?.contentOrNull
            when {
                !viewId.isNullOrBlank() -> "view:${viewId.trim()}"
                !query.isNullOrBlank() -> "query:${query.trim().lowercase()}"
                else -> toolName
            }
        }
        "phone_run_flow" -> {
            val name = params["name"]?.jsonPrimitive?.contentOrNull
            val saveAs = params["save_as"]?.jsonPrimitive?.contentOrNull
            when {
                !name.isNullOrBlank() -> "flow:$name"
                !saveAs.isNullOrBlank() -> "flow:$saveAs"
                else -> "flow:inline"
            }
        }
        else -> toolName
    }
}

private fun appendToolWarning(parts: List<UIMessagePart>, warning: String): List<UIMessagePart> {
    var applied = false
    val updated = parts.map { part ->
        if (applied || part !is UIMessagePart.Text) return@map part
        val trimmed = part.text.trim()
        if (!trimmed.startsWith("{")) return@map part
        val annotated = runCatching {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(trimmed).jsonObject
            buildJsonObject {
                obj.forEach { (key, value) -> put(key, value) }
                put("warning", warning)
            }.toString()
        }.getOrElse {
            buildJsonObject {
                put("warning", warning)
                put("raw", trimmed)
            }.toString()
        }
        applied = true
        part.copy(text = annotated)
    }
    return if (applied) {
        updated
    } else {
        parts + UIMessagePart.Text(buildJsonObject { put("warning", warning) }.toString())
    }
}

private fun phoneAutomationStepFor(name: String, args: kotlinx.serialization.json.JsonElement): PhoneAutomationStep {
    if (name == "phone_screenshot") {
        val flag = args.jsonObject["screenshot"]?.jsonPrimitive
        val wantBitmap = flag?.booleanOrNull
            ?: flag?.contentOrNull.equals("true", ignoreCase = true)
        if (!wantBitmap) return PhoneAutomationStep.Inspect
    }
    return when (name) {
        "phone_launch_app" -> PhoneAutomationStep.LaunchApp
        "phone_screenshot" -> PhoneAutomationStep.Screenshot
        "phone_inspect_screen" -> PhoneAutomationStep.Inspect
        "phone_click" -> PhoneAutomationStep.Click
        "phone_swipe" -> PhoneAutomationStep.Swipe
        "phone_type_text" -> PhoneAutomationStep.Type
        "phone_press_key" -> PhoneAutomationStep.PressKey
        "phone_assert_visible" -> PhoneAutomationStep.AssertVisible
        "phone_scroll_until_visible" -> PhoneAutomationStep.ScrollUntilVisible
        "phone_run_flow" -> PhoneAutomationStep.RunFlow
        "phone_manage_flows" -> PhoneAutomationStep.ManageFlows
        "place_call" -> PhoneAutomationStep.PlaceCall
        "end_call" -> PhoneAutomationStep.EndCall
        "read_call_state" -> PhoneAutomationStep.ReadCall
        else -> PhoneAutomationStep.Other
    }
}
