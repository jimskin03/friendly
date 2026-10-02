package me.rerere.rikkahub.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpRequestBuilder
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
}
