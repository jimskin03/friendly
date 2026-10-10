package app.friendly.assistant.data.ai

import me.rerere.common.android.Logging
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.BufferedSink
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogRedactorTest {
    private val sentinels = listOf(
        "SENTINEL-AUTH-1111", "SENTINEL-PROXY-2222", "SENTINEL-COOKIE-3333", "SENTINEL-SETCOOKIE-4444",
        "SENTINEL-XAPIKEY-5555", "SENTINEL-APIKEY-6666", "SENTINEL-GOOG-7777", "SENTINEL-ANTHROPIC-8888",
        "SENTINEL-CUSTOM-9999", "SENTINEL-QKEY-AAAA", "SENTINEL-QTOKEN-BBBB", "SENTINEL-QACCESS-CCCC",
        "SENTINEL-JSONKEY-DDDD", "SENTINEL-JSONCAMEL-EEEE", "SENTINEL-JSONTOKEN-FFFF", "SENTINEL-JSONSECRET-GGGG",
        "SENTINEL-JSONPW-HHHH", "SENTINEL-JSONAUTH-IIII",
    )

    private val jsonBody = """
        {"model":"gpt-x","api_key":"SENTINEL-JSONKEY-DDDD","nested":{"apiKey":"SENTINEL-JSONCAMEL-EEEE",
        "token" : "SENTINEL-JSONTOKEN-FFFF"},"client_secret":"SENTINEL-JSONSECRET-GGGG",
        "password":"SENTINEL-JSONPW-HHHH","authorization":"Bearer SENTINEL-JSONAUTH-IIII","max_tokens":512,
        "messages":[{"role":"user","content":"hello"}]}
    """.trimIndent()

    private fun sentinelRequest(body: RequestBody? = jsonBody.toRequestBody("application/json".toMediaType())): Request =
        Request.Builder()
            .url(
                "https://api.example.com/v1/chat?key=SENTINEL-QKEY-AAAA&token=SENTINEL-QTOKEN-BBBB" +
                    "&access_token=SENTINEL-QACCESS-CCCC&alt=sse"
            )
            .header("Authorization", "Bearer SENTINEL-AUTH-1111")
            .header("Proxy-Authorization", "Basic SENTINEL-PROXY-2222")
            .header("Cookie", "sid=SENTINEL-COOKIE-3333")
            .header("x-api-key", "SENTINEL-XAPIKEY-5555")
            .header("api-key", "SENTINEL-APIKEY-6666")
            .header("x-goog-api-key", "SENTINEL-GOOG-7777")
            .header("anthropic-api-key", "SENTINEL-ANTHROPIC-8888")
            .header("X-My-Custom-Token", "SENTINEL-CUSTOM-9999")
            .header("anthropic-version", "2023-06-01")
            .apply { if (body != null) post(body) }
            .build()

    /** Terminal interceptor: records exactly what would go on the wire, then answers locally. */
    private class Recorder : Interceptor {
        var sentHeaders: List<Pair<String, String>>? = null
        var sentUrl: String? = null
        var sentBody: ByteArray? = null

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            sentHeaders = request.headers.toList()
            sentUrl = request.url.toString()
            sentBody = request.body?.let { b -> Buffer().also { b.writeTo(it) }.readByteArray() }
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Set-Cookie", "session=SENTINEL-SETCOOKIE-4444")
                .header("x-request-id", "req-123")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private fun client(recorder: Recorder, vararg interceptors: Interceptor) =
        OkHttpClient.Builder().apply {
            interceptors.forEach { addInterceptor(it) }
            addInterceptor(recorder)
        }.build()

    @Before
    fun setUp() {
        Logging.clear()
        Logging.setRequestLoggingEnabled(true)
    }

    @After
    fun tearDown() {
        Logging.setRequestLoggingEnabled(false)
        Logging.clear()
    }

    private fun assertNoSentinel(text: String) {
        sentinels.forEach { assertFalse("leaked $it in:\n$text", text.contains(it)) }
    }

    @Test
    fun requestLogNeverContainsCredentials() {
        val recorder = Recorder()
        client(recorder, RequestLoggingInterceptor()).newCall(sentinelRequest()).execute().close()

        val log = Logging.getRequestLogs().single()
        val dump = log.toString()
        assertNoSentinel(dump)
        // Diagnostics stay useful.
        assertEquals("POST", log.method)
        assertEquals(200, log.responseCode)
        assertEquals("2023-06-01", log.requestHeaders["anthropic-version"])
        assertEquals("req-123", log.responseHeaders["x-request-id"])
        assertTrue(log.url.contains("alt=sse"))
        assertTrue(log.requestBody!!.contains("\"max_tokens\":512"))
        assertTrue(log.requestBody!!.contains("\"content\":\"hello\""))
    }

    @Test
    fun outboundRequestIsByteIdentical() {
        val expected = sentinelRequest()
        val expectedBody = Buffer().also { expected.body!!.writeTo(it) }.readByteArray()

        val plain = Recorder()
        client(plain).newCall(sentinelRequest()).execute().close()

        val logged = Recorder()
        client(logged, RequestLoggingInterceptor(), LogRedactor.httpLoggingInterceptor { }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }).newCall(sentinelRequest()).execute().close()

        assertEquals(plain.sentUrl, logged.sentUrl)
        assertEquals(expected.url.toString(), logged.sentUrl)
        assertEquals(plain.sentHeaders, logged.sentHeaders)
        assertTrue(logged.sentHeaders!!.contains("Authorization" to "Bearer SENTINEL-AUTH-1111"))
        assertArrayEquals(expectedBody, logged.sentBody)
        assertArrayEquals(plain.sentBody, logged.sentBody)
    }

    @Test
    fun httpLoggingInterceptorLinesNeverContainCredentials() {
        val lines = mutableListOf<String>()
        val logger = LogRedactor.httpLoggingInterceptor { lines += it }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        client(Recorder(), logger).newCall(sentinelRequest()).execute().close()

        val text = lines.joinToString("\n")
        assertNoSentinel(text)
        assertTrue(text.contains("--> POST https://api.example.com/v1/chat"))
        assertTrue(text.contains("anthropic-version: 2023-06-01"))
        assertTrue(text.contains("<-- 200"))
    }

    @Test
    fun oneShotAndDuplexBodiesAreNotConsumed() {
        class OneShot(private val duplex: Boolean) : RequestBody() {
            var writes = 0
            override fun contentType(): MediaType = "application/json".toMediaType()
            override fun isOneShot() = !duplex
            override fun isDuplex() = duplex
            override fun writeTo(sink: BufferedSink) {
                writes++
                check(writes == 1) { "one-shot body written twice" }
                sink.writeUtf8("""{"api_key":"SENTINEL-JSONKEY-DDDD"}""")
            }
        }
        for (duplex in listOf(false, true)) {
            Logging.clear()
            val body = OneShot(duplex)
            val recorder = Recorder()
            client(recorder, RequestLoggingInterceptor()).newCall(sentinelRequest(body)).execute().close()
            assertEquals(1, body.writes)
            assertEquals("""{"api_key":"SENTINEL-JSONKEY-DDDD"}""", recorder.sentBody!!.decodeToString())
            val log = Logging.getRequestLogs().single()
            assertTrue(log.requestBody!!.startsWith("[body not captured"))
        }
    }

    @Test
    fun binaryUploadsAreNotBuffered() {
        var writes = 0
        val payload = ByteArray(4096) { it.toByte() }
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = payload.size.toLong()
            override fun writeTo(sink: BufferedSink) {
                writes++
                sink.write(payload)
            }
        }
        val recorder = Recorder()
        client(recorder, RequestLoggingInterceptor()).newCall(sentinelRequest(body)).execute().close()
        assertEquals("only the real send reads the body", 1, writes)
        assertArrayEquals(payload, recorder.sentBody)
        assertEquals(
            "[body not captured: application/octet-stream, 4096 bytes]",
            Logging.getRequestLogs().single().requestBody,
        )
    }

    @Test
    fun capturedBodyIsCappedAndTruncatedSecretsStayHidden() {
        val filler = "x".repeat(LogRedactor.MAX_CAPTURED_BODY_BYTES - 20)
        // The cap cuts through the secret value; its prefix must not leak.
        val big = """{"a":"$filler","api_key":"SENTINEL-JSONKEY-DDDD${"y".repeat(100)}"}"""
        val text = LogRedactor.captureRequestBody(big.toRequestBody("application/json".toMediaType()))!!
        assertNoSentinel(text)
        assertFalse(text.contains("SENTINEL"))
        assertTrue(text.contains("[truncated, ${big.length} bytes]"))
        assertTrue(text.length < LogRedactor.MAX_CAPTURED_BODY_BYTES + 200)
    }

    @Test
    fun formBodiesRedactOAuthSecrets() {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", "SENTINEL-QTOKEN-BBBB")
            .add("client_secret", "SENTINEL-JSONSECRET-GGGG")
            .add("code", "SENTINEL-CUSTOM-9999")
            .build()
        val text = LogRedactor.captureRequestBody(form)!!
        assertNoSentinel(text)
        assertTrue(text.contains("grant_type=refresh_token"))
    }

    @Test
    fun errorMessagesAndUrlsRedactSecretQueryParams() {
        assertEquals(
            "https://h.example/p?key=REDACTED&alt=sse",
            LogRedactor.redactUrl("https://h.example/p?key=SENTINEL&alt=sse"),
        )
        assertEquals(
            "failed to connect to https://h.example/p?api_key=REDACTED",
            LogRedactor.redactQueryText("failed to connect to https://h.example/p?api_key=SENTINEL"),
        )
        assertEquals("https://h.example/p?alt=sse", LogRedactor.redactUrl("https://h.example/p?alt=sse"))
    }

    @Test
    fun headerNamePolicy() {
        listOf(
            "Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "x-api-key", "api-key",
            "x-goog-api-key", "anthropic-api-key", "X-Custom-Token", "x-client-secret", "X-Auth-User",
        ).forEach { assertTrue(it, LogRedactor.isSensitiveHeader(it)) }
        listOf("Content-Type", "Accept", "User-Agent", "anthropic-version", "x-request-id", "Content-Length")
            .forEach { assertFalse(it, LogRedactor.isSensitiveHeader(it)) }
    }
}
