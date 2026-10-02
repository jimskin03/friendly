/**
 * Example stub for Friendly Android — DesktopControlClient
 *
 * Place conceptually under:
 *   app/src/main/java/.../data/remote/DesktopControlClient.kt
 *
 * Not compiled here (Friendly repo is not checked out). Wire into chat UI
 * "Open desktop" / "Stop desktop" actions in Phase 3 client work.
 */
package me.rerere.rikkahub.data.remote // adjust to actual package

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

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

class DesktopControlClient(
    private val http: HttpClient,
    private val baseUrl: String, // e.g. http://100.x.y.z:8787
    private val apiToken: String,
) {
    private fun HttpRequestBuilder.auth() {
        header(HttpHeaders.Authorization, "Bearer $apiToken")
        contentType(ContentType.Application.Json)
    }

    suspend fun startStream(mode: String = "view"): StreamStartResponse =
        http.post("$baseUrl/v1/stream/start") {
            auth()
            setBody(mapOf("mode" to mode))
        }.body()

    suspend fun stopStream(sessionId: String? = null): Map<String, Any?> =
        http.post("$baseUrl/v1/stream/stop") {
            auth()
            setBody(buildMap {
                if (sessionId != null) put("session_id", sessionId)
            })
        }.body()

    suspend fun status(): StreamStatusResponse =
        http.get("$baseUrl/v1/stream/status") { auth() }.body()
}

/*
 * UI sketch:
 *  - "Open desktop" → startStream() → open viewer_url in Custom Tabs / WebView
 *  - Poll status() or show "Desktop live" when active → "Stop" → stopStream()
 *  - Do NOT expose stream_start to the model without needsApproval; prefer this button.
 *
 * MCP remains for agent click/type/screenshot; stream is a human overlay.
 */
