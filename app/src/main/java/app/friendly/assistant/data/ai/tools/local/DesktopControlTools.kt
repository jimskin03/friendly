package app.friendly.assistant.data.ai.tools.local

import android.util.Base64
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import app.friendly.assistant.data.files.FilesManager
import app.friendly.assistant.data.remote.DesktopControlClient

internal fun buildDesktopScreenshotTool(
    getClient: () -> DesktopControlClient?,
    filesManager: FilesManager,
): Tool = Tool(
    name = "desktop_screenshot",
    description = """
        Capture a high-resolution screenshot of the remote Linux desktop.
        Returns the captured desktop screen image to the conversation for multimodal visual analysis.
        Use this to observe the current state of apps, desktop windows, or browser contents.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList()
        )
    },
    execute = {
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        try {
            val response = client.screenshot()
            val bytes = Base64.decode(response.image_b64, Base64.DEFAULT)
            val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
            listOf(
                UIMessagePart.Image(url = uris.first().toString()),
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("description", "Remote Linux desktop screenshot captured successfully")
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to capture remote desktop screenshot")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopClickTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_click",
    description = """
        Click at specific (x, y) coordinates on the remote Linux desktop.
        Button can be 'left' (default), 'right', or 'middle'.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("x", buildJsonObject {
                    put("type", "number")
                    put("description", "Horizontal X coordinate in pixels (0 to screen width)")
                })
                put("y", buildJsonObject {
                    put("type", "number")
                    put("description", "Vertical Y coordinate in pixels (0 to screen height)")
                })
                put("button", buildJsonObject {
                    put("type", "string")
                    put("description", "Mouse button to click: 'left', 'right', or 'middle'")
                    put("enum", buildJsonArray {
                        add("left")
                        add("right")
                        add("middle")
                    })
                })
            },
            required = listOf("x", "y")
        )
    },
    execute = { args ->
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val x = params["x"]?.jsonPrimitive?.intOrNull ?: error("x is required")
        val y = params["y"]?.jsonPrimitive?.intOrNull ?: error("y is required")
        val button = params["button"]?.jsonPrimitive?.contentOrNull ?: "left"

        try {
            client.click(x, y, button)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("action", "click")
                        put("x", x)
                        put("y", y)
                        put("button", button)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to perform click")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopTypeTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_type",
    description = """
        Type text into the currently focused window on the remote Linux desktop.
        Sends keystrokes directly into the active X11 application.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "The text to type into the focused input")
                })
            },
            required = listOf("text")
        )
    },
    execute = { args ->
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val text = params["text"]?.jsonPrimitive?.contentOrNull ?: error("text is required")

        try {
            client.typeText(text)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("action", "type")
                        put("characters_typed", text.length)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to type text")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopHotkeyTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_hotkey",
    description = """
        Press keyboard shortcut keys on the remote Linux desktop.
        Example key combinations: ["ctrl", "t"] (open tab), ["ctrl", "w"] (close tab),
        ["ctrl", "c"] (copy), ["ctrl", "v"] (paste), ["super"] (app menu),
        ["Return"] (press enter), ["BackSpace"], ["Tab"], ["Escape"], ["alt", "F4"].
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("keys", buildJsonObject {
                    put("type", "array")
                    put("description", "List of key names to press in combination, e.g. ['ctrl', 't']")
                    put("items", buildJsonObject {
                        put("type", "string")
                    })
                })
            },
            required = listOf("keys")
        )
    },
    execute = { args ->
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val keys = params["keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: error("keys is required")

        try {
            client.hotkey(keys)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("action", "hotkey")
                        put("keys", buildJsonArray { keys.forEach { add(it) } })
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to press hotkey")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopBrowserOpenTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_browser_open",
    description = """
        Open a URL in Chromium browser on the remote Linux desktop.
        Launches or navigates to the specified web page.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("url", buildJsonObject {
                    put("type", "string")
                    put("description", "Web URL to open, e.g. 'https://github.com'")
                })
            },
            required = listOf("url")
        )
    },
    execute = { args ->
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val params = args.jsonObject
        val url = params["url"]?.jsonPrimitive?.contentOrNull ?: error("url is required")

        try {
            val res = client.openBrowser(url)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("action", "browser_open")
                        put("url", res.url)
                        if (res.pid != null) put("pid", res.pid)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to open browser URL")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopStreamStartTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_stream_start",
    description = """
        Start the on-demand live desktop stream (x11vnc + noVNC) on the remote Linux host.
        Returns the stream session status and viewer URL.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("mode", buildJsonObject {
                    put("type", "string")
                    put("description", "Stream mode: 'view' (read-only) or 'interactive'")
                    put("enum", buildJsonArray {
                        add("view")
                        add("interactive")
                    })
                })
            },
            required = emptyList()
        )
    },
    execute = { args ->
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        val mode = args.jsonObject["mode"]?.jsonPrimitive?.contentOrNull ?: "view"

        try {
            val res = client.startStream(mode)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("session_id", res.session_id)
                        put("viewer_url", res.viewer_url)
                        put("mode", res.mode ?: mode)
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to start desktop stream")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)

internal fun buildDesktopStreamStopTool(
    getClient: () -> DesktopControlClient?,
): Tool = Tool(
    name = "desktop_stream_stop",
    description = """
        Stop the active desktop live stream to conserve host resources and network bandwidth.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {},
            required = emptyList()
        )
    },
    execute = {
        val client = getClient()
        if (client == null) {
            val payload = buildJsonObject {
                put("error", "Desktop Control is not configured. Set API token in Settings -> Preferences -> Network.")
                put("success", false)
            }
            return@Tool listOf(UIMessagePart.Text(payload.toString()))
        }

        try {
            client.stopStream()
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("success", true)
                        put("action", "stream_stop")
                        put("message", "Desktop stream stopped successfully")
                    }.toString()
                )
            )
        } catch (e: Exception) {
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("error", e.message ?: "Failed to stop desktop stream")
                        put("success", false)
                    }.toString()
                )
            )
        }
    }
)
