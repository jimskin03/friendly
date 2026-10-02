package me.rerere.rikkahub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.utils.JsonInstant

/**
 * Client for the Friendly Host Control API (stream/start, stream/stop, stream/status).
 *
 * Configure base URL + Bearer token via Settings → Preferences → Network
 * (desktopControlBaseUrl / desktopControlApiToken), or [DesktopControlDefaults].
 */
object DesktopControlDefaults {
    /** Emulator loopback → host machine. Use Tailscale/LAN IP on a real device. */
    const val BASE_URL = "http://10.0.2.2:8787"
}

@Serializable
data class StreamStartRequest(
    val mode: String = "view",
)

@Serializable
data class StreamStopRequest(
    val session_id: String? = null,
)

@Serializable
data class StreamStartResponse(
    val viewer_url: String,
    val session_id: String,
    val expires_at: String,
    val mode: String? = null,
    val local_viewer_url: String? = null,
)

@Serializable
data class StreamStatusResponse(
    val active: Boolean,
    val session_id: String? = null,
    val expires_at: String? = null,
    val viewer_url: String? = null,
    val mode: String? = null,
)

@Serializable
data class ScreenshotResponse(
    val image_b64: String,
    val mime: String = "image/png",
)

@Serializable
data class ClickRequest(
    val x: Int,
    val y: Int,
    val button: String = "left",
)

@Serializable
data class TypeRequest(
    val text: String,
)

@Serializable
data class HotkeyRequest(
    val keys: List<String>,
)

@Serializable
data class BrowserOpenRequest(
    val url: String,
)

@Serializable
data class LaunchAppRequest(
    val app: String,
)

@Serializable
data class KillAppRequest(
    val target: String = "focused",
)


@Serializable
data class BrowserOpenResponse(
    val ok: Boolean = true,
    val url: String = "",
    val pid: Int? = null,
)

@Serializable
data class HealthResponse(
    val ok: Boolean = false,
    val service: String? = null,
    val version: String? = null,
)

@Serializable
data class DesktopStatusResponse(
    val display: String = "",
    val display_available: Boolean = false,
    val width: Int = 1280,
    val height: Int = 720,
    val depth: Int = 24,
)

class DesktopControlException(
    message: String,
    val statusCode: Int? = null,
) : Exception(message)

class DesktopControlClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val apiToken: String,
) {
    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $apiToken")
        contentType(ContentType.Application.Json)
    }

    private fun normalizedBaseUrl(): String = baseUrl.trimEnd('/')

    suspend fun startStream(mode: String = "view"): StreamStartResponse {
        val response = http.post("${normalizedBaseUrl()}/v1/stream/start") {
            auth()
            setBody(JsonInstant.encodeToString(StreamStartRequest(mode = mode)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "stream/start failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return JsonInstant.decodeFromString(text)
    }

    suspend fun stopStream(sessionId: String? = null): String {
        val response = http.post("${normalizedBaseUrl()}/v1/stream/stop") {
            auth()
            setBody(JsonInstant.encodeToString(StreamStopRequest(session_id = sessionId)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "stream/stop failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return text
    }

    suspend fun status(): StreamStatusResponse {
        val response = http.get("${normalizedBaseUrl()}/v1/stream/status") { auth() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "stream/status failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return JsonInstant.decodeFromString(text)
    }

    suspend fun health(): Boolean {
        return try {
            val response = http.get("${normalizedBaseUrl()}/health")
            response.status.isSuccess()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun screenshot(): ScreenshotResponse {
        val response = http.post("${normalizedBaseUrl()}/v1/actions/screenshot") {
            auth()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "screenshot failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return JsonInstant.decodeFromString(text)
    }

    suspend fun click(x: Int, y: Int, button: String = "left"): Boolean {
        val response = http.post("${normalizedBaseUrl()}/v1/actions/click") {
            auth()
            setBody(JsonInstant.encodeToString(ClickRequest(x = x, y = y, button = button)))
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            throw DesktopControlException(
                message = "click failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return true
    }

    suspend fun typeText(text: String): Boolean {
        val response = http.post("${normalizedBaseUrl()}/v1/actions/type") {
            auth()
            setBody(JsonInstant.encodeToString(TypeRequest(text = text)))
        }
        if (!response.status.isSuccess()) {
            val err = response.bodyAsText()
            throw DesktopControlException(
                message = "type failed: ${response.status} $err",
                statusCode = response.status.value,
            )
        }
        return true
    }

    suspend fun hotkey(keys: List<String>): Boolean {
        val response = http.post("${normalizedBaseUrl()}/v1/actions/hotkey") {
            auth()
            setBody(JsonInstant.encodeToString(HotkeyRequest(keys = keys)))
        }
        if (!response.status.isSuccess()) {
            val err = response.bodyAsText()
            throw DesktopControlException(
                message = "hotkey failed: ${response.status} $err",
                statusCode = response.status.value,
            )
        }
        return true
    }

    suspend fun openBrowser(url: String): BrowserOpenResponse {
        val response = http.post("${normalizedBaseUrl()}/v1/browser/open") {
            auth()
            setBody(JsonInstant.encodeToString(BrowserOpenRequest(url = url)))
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "browser/open failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return JsonInstant.decodeFromString(text)
    }

    /**
     * Ask the host to show Chrome and a terminal side by side.
     * Returns false when the host does not have this endpoint yet.
     */
    suspend fun prepareDesktop(): Boolean {
        val response = http.post("${normalizedBaseUrl()}/v1/desktop/prepare") {
            auth()
        }
        if (response.status.value == 404) {
            return false
        }
        if (!response.status.isSuccess()) {
            val text = response.bodyAsText()
            throw DesktopControlException(
                message = "desktop/prepare failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return true
    }

    suspend fun launchApp(app: String): Boolean {
        val clean = app.trim().lowercase()
        return try {
            val response = http.post("${normalizedBaseUrl()}/v1/desktop/launch") {
                auth()
                setBody(JsonInstant.encodeToString(LaunchAppRequest(app = clean)))
            }
            if (response.status.isSuccess()) {
                true
            } else if (response.status.value == 404) {
                fallbackLaunchApp(clean)
            } else {
                val text = response.bodyAsText()
                throw DesktopControlException(
                    message = "desktop/launch failed: ${response.status} $text",
                    statusCode = response.status.value,
                )
            }
        } catch (e: DesktopControlException) {
            if (e.statusCode == 404) {
                fallbackLaunchApp(clean)
            } else {
                throw e
            }
        } catch (_: Exception) {
            fallbackLaunchApp(clean)
        }
    }

    private suspend fun fallbackLaunchApp(app: String): Boolean {
        return when (app) {
            "menu", "root-menu", "app-menu" -> {
                hotkey(listOf("Super"))
            }
            "browser", "chromium", "chrome" -> {
                openBrowser("https://www.google.com")
                true
            }
            "terminal", "xterm", "bash", "shell" -> {
                hotkey(listOf("Control", "Alt", "t"))
            }
            else -> false
        }
    }


    suspend fun closeWindow(): Boolean {
        return try {
            val response = http.post("${normalizedBaseUrl()}/v1/desktop/close") {
                auth()
            }
            if (response.status.isSuccess()) {
                true
            } else if (response.status.value == 404) {
                // Older hosts: emulate WM close with Alt+F4
                hotkey(listOf("Alt", "F4"))
            } else {
                val textBody = response.bodyAsText()
                throw DesktopControlException(
                    message = "desktop/close failed: ${response.status} $textBody",
                    statusCode = response.status.value,
                )
            }
        } catch (e: DesktopControlException) {
            if (e.statusCode == 404) {
                hotkey(listOf("Alt", "F4"))
            } else {
                throw e
            }
        } catch (_: Exception) {
            hotkey(listOf("Alt", "F4"))
        }
    }

    suspend fun killApp(target: String): Boolean {
        val clean = target.trim().lowercase().ifBlank { "focused" }
        return try {
            val response = http.post("${normalizedBaseUrl()}/v1/desktop/kill") {
                auth()
                setBody(JsonInstant.encodeToString(KillAppRequest(target = clean)))
            }
            if (response.status.isSuccess()) {
                true
            } else if (response.status.value == 404) {
                fallbackKillApp(clean)
            } else {
                val textBody = response.bodyAsText()
                throw DesktopControlException(
                    message = "desktop/kill failed: ${response.status} $textBody",
                    statusCode = response.status.value,
                )
            }
        } catch (e: DesktopControlException) {
            if (e.statusCode == 404) {
                fallbackKillApp(clean)
            } else {
                throw e
            }
        } catch (_: Exception) {
            fallbackKillApp(clean)
        }
    }

    private suspend fun fallbackKillApp(target: String): Boolean {
        return when (target) {
            "focused", "focus", "active", "window" -> hotkey(listOf("Alt", "F4"))
            else -> hotkey(listOf("Alt", "F4"))
        }
    }

    suspend fun desktopStatus(): DesktopStatusResponse {
        val response = http.get("${normalizedBaseUrl()}/v1/desktop/status") { auth() }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw DesktopControlException(
                message = "desktop/status failed: ${response.status} $text",
                statusCode = response.status.value,
            )
        }
        return JsonInstant.decodeFromString(text)
    }
}
