package me.rerere.oauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class OAuthHttpClientTest {
    private val client = OAuthHttpClient(OkHttpClient())

    @Test
    fun `authorization rejects protocol fields in extras or endpoint query`() {
        val request = OAuthHttpClient.AuthorizationRequest(
            authorizationEndpoint = "https://auth.example.com/authorize",
            clientId = "client",
            redirectUri = "http://127.0.0.1:1455/auth/callback",
            pkce = client.generatePkce(),
            state = "state",
        )
        assertThrows(IllegalArgumentException::class.java) {
            client.buildAuthorizationUrl(request.copy(additionalParameters = mapOf("client_id" to "duplicate")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            client.buildAuthorizationUrl(request.copy(authorizationEndpoint = "https://auth.example.com/authorize?client_id=duplicate"))
        }
    }

    @Test
    fun `error descriptions redact encoded credentials and bearer values`() {
        val request = codeRequest().copy(code = "secret+code/with=padding")
        val (oauth, _) = respondingClient(
            body = """{"error":"invalid_request","error_description":"Invalid code secret%2Bcode%2Fwith%3Dpadding; Bearer short-secret"}""",
            status = 400,
        )
        val error = assertThrows(OAuthRequestException::class.java) {
            runBlocking { oauth.exchangeAuthorizationCode(request) }
        }
        assertFalse(error.message.orEmpty().contains("secret%2Bcode"))
        assertFalse(error.message.orEmpty().contains("short-secret"))
        assertTrue(error.message.orEmpty().contains("Invalid code"))
    }

    @Test
    fun `code exchange sends each required field once and preserves special characters`() = runBlocking {
        val (oauth, captured) = respondingClient()
        val request = codeRequest().copy(code = "code+with/slash&equals=", codeVerifier = "verifier+with/slash=")
        oauth.exchangeAuthorizationCode(request)
        val sent = captured.get()
        assertEquals("POST", sent.method)
        assertEquals(request.tokenEndpoint, sent.url.toString())
        val form = sent.body as FormBody
        assertEquals("application/x-www-form-urlencoded", form.contentType().toString())
        val fields = (0 until form.size).map { form.name(it) to form.value(it) }
        assertEquals(listOf(
            "grant_type" to "authorization_code",
            "code" to request.code,
            "redirect_uri" to request.redirectUri,
            "client_id" to request.clientId,
            "code_verifier" to request.codeVerifier,
            "resource" to "https://api.openai.com/v1",
        ), fields)
    }

    @Test
    fun `reserved token parameters cannot be duplicated through extras`() {
        val (oauth, captured) = respondingClient()
        listOf("client_id", "grant_type", "code", "code_verifier", "redirect_uri", "resource", "client_secret").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { oauth.exchangeAuthorizationCode(codeRequest().copy(additionalParameters = mapOf(name to "override"))) }
            }
        }
        assertNull(captured.get())
    }

    @Test
    fun `refresh retains grant without sending scope and rejects duplicate client id`() {
        val (oauth, captured) = respondingClient()
        val request = OAuthHttpClient.RefreshTokenRequest(
            tokenEndpoint = codeRequest().tokenEndpoint,
            clientId = "oaiapp_test",
            refreshToken = "test-refresh-token",
            resources = listOf("https://api.openai.com/v1"),
        )
        runBlocking { oauth.refreshToken(request) }
        val form = captured.get().body as FormBody
        assertEquals(listOf("grant_type", "refresh_token", "client_id", "resource"), (0 until form.size).map(form::name))
        captured.set(null)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { oauth.refreshToken(request.copy(additionalParameters = mapOf("client_id" to request.clientId))) }
        }
        assertNull(captured.get())
    }

    @Test
    fun `token errors identify operation and preserve redacted explanation and request id`() {
        val request = codeRequest()
        val (oauth, _) = respondingClient(
            body = """{"error":"invalid_request","error_description":"redirect_uri mismatch; code=${request.code}; verifier=${request.codeVerifier}; user@example.com","access_token":"never-display-extra-fields"}""",
            status = 400,
        )
        val error = assertThrows(OAuthRequestException::class.java) {
            runBlocking { oauth.exchangeAuthorizationCode(request) }
        }
        assertEquals(400, error.statusCode)
        assertEquals("invalid_request", error.errorCode)
        val message = error.message.orEmpty()
        assertTrue(message.contains("OAuth code exchange failed (HTTP 400)"))
        assertTrue(message.contains("redirect_uri mismatch"))
        assertTrue(message.contains("request: req_test"))
        assertFalse(message.contains(request.code))
        assertFalse(message.contains(request.codeVerifier))
        assertFalse(message.contains("user@example.com"))
        assertFalse(message.contains("never-display-extra-fields"))
    }

    @Test
    fun `nested error and discovery errors remain distinguishable`() {
        val (oauth, _) = respondingClient(
            body = """{"error":{"code":"invalid_request","message":"Missing required parameter","param":"resource"}}""",
            status = 400,
        )
        val error = assertThrows(OAuthRequestException::class.java) {
            runBlocking { oauth.getJson("https://auth.example.com/.well-known/openid-configuration") }
        }
        assertEquals("invalid_request", error.errorCode)
        assertTrue(error.message.orEmpty().contains("OAuth discovery failed"))
        assertTrue(error.message.orEmpty().contains("parameter: resource"))
        assertTrue(error.message.orEmpty().contains("Missing required parameter"))
    }

    @Test
    fun `non-json error bodies are not exposed`() {
        val (oauth, _) = respondingClient(body = "<html>private upstream data</html>", status = 502)
        val error = assertThrows(OAuthRequestException::class.java) {
            runBlocking { oauth.exchangeAuthorizationCode(codeRequest()) }
        }
        assertNull(error.errorCode)
        assertFalse(error.message.orEmpty().contains("private upstream data"))
    }

    private fun codeRequest() = OAuthHttpClient.AuthorizationCodeTokenRequest(
        tokenEndpoint = "https://auth.example.com/api/accounts/oauth/token",
        clientId = "oaiapp_test",
        code = "test-authorization-code",
        codeVerifier = "test-pkce-verifier",
        redirectUri = "http://127.0.0.1:52134/auth/callback",
        resources = listOf("https://api.openai.com/v1"),
    )

    private fun respondingClient(
        body: String = """{"access_token":"test-access-token"}""",
        status: Int = 200,
    ): Pair<OAuthHttpClient, AtomicReference<Request>> {
        val captured = AtomicReference<Request>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            captured.set(request)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(status).message("Test response").header("x-request-id", "req_test")
                .body(body.toResponseBody()).build()
        }.build()
        return OAuthHttpClient(http) to captured
    }

    @Test
    fun `authorization url contains pkce state scope and resources`() {
        val url = client.buildAuthorizationUrl(
            OAuthHttpClient.AuthorizationRequest(
                authorizationEndpoint = "https://auth.example.com/authorize?audience=existing",
                clientId = "client id",
                redirectUri = "http://127.0.0.1:52134/oauth/callback",
                pkce = OAuthHttpClient.Pkce(
                    verifier = "verifier",
                    challenge = "challenge",
                ),
                state = "expected-state",
                scope = "openid profile",
                resources = listOf("https://mcp.example.com", "https://api.example.com"),
                additionalParameters = mapOf("prompt" to "consent"),
            )
        ).toHttpUrl()

        assertEquals("existing", url.queryParameter("audience"))
        assertEquals("code", url.queryParameter("response_type"))
        assertEquals("client id", url.queryParameter("client_id"))
        assertEquals(
            "http://127.0.0.1:52134/oauth/callback",
            url.queryParameter("redirect_uri"),
        )
        assertEquals("challenge", url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("expected-state", url.queryParameter("state"))
        assertEquals("openid profile", url.queryParameter("scope"))
        assertEquals(
            listOf("https://mcp.example.com", "https://api.example.com"),
            url.queryParameterValues("resource"),
        )
        assertEquals("consent", url.queryParameter("prompt"))
    }
}
