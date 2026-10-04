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
import me.rerere.rikkahub.service.phone.PhoneAutomationService
import me.rerere.rikkahub.service.phone.PhoneAutomationStep
import me.rerere.rikkahub.service.phone.PhoneCallController
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
        Provide (x, y) coordinates, or provide 'query' (text/label of the button to click).
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
                put("node_id", buildJsonObject {
                    put("type", "integer")
                    put("description", "Element ID from phone_inspect_screen")
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

        if (targetX == null || targetY == null) {
            val query = params["query"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val nodeId = params["node_id"]?.jsonPrimitive?.intOrNull

            val inspection = service.inspectScreen()
            val target = inspection.interactiveElements.firstOrNull { node ->
                if (nodeId != null && node.id == nodeId) return@firstOrNull true
                if (!query.isNullOrBlank()) {
                    node.text.lowercase().contains(query) ||
                        node.description.lowercase().contains(query) ||
                        node.viewId.lowercase().contains(query)
                } else false
            }

            if (target != null) {
                targetX = target.centerX.toFloat()
                targetY = target.centerY.toFloat()
            }
        }

        if (targetX == null || targetY == null) {
            val payload = buildJsonObject {
                put("error", "Target coordinates not found. Specify (x, y) or run phone_inspect_screen first.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val clicked = service.click(targetX, targetY)
        val payload = buildJsonObject {
            put("success", clicked)
            put("x", targetX)
            put("y", targetY)
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
                    put("clickable", node.clickable)
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
                result
            } catch (e: Exception) {
                PhoneAutomationService.reportWorkFinished(success = false)
                throw e
            }
        }
    )
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
        "place_call" -> PhoneAutomationStep.PlaceCall
        "end_call" -> PhoneAutomationStep.EndCall
        "read_call_state" -> PhoneAutomationStep.ReadCall
        else -> PhoneAutomationStep.Other
    }
}
