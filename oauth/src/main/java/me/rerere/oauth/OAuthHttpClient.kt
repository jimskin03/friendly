package me.rerere.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import me.rerere.common.http.SensitiveRequest
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class OAuthHttpClient(
    private val httpClient: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }


    @Serializable
    data class ClientRegistrationRequest(
        @SerialName("client_name") val clientName: String,
        @SerialName("redirect_uris") val redirectUris: List<String>,
        @SerialName("grant_types") val grantTypes: List<String> =
            listOf("authorization_code", "refresh_token"),
        @SerialName("response_types") val responseTypes: List<String> = listOf("code"),
        @SerialName("token_endpoint_auth_method") val tokenEndpointAuthMethod: String = "none",
        @SerialName("scope") val scope: String? = null,
    )

    @Serializable
    data class ClientRegistrationResponse(
        @SerialName("client_id") val clientId: String,
        @SerialName("client_secret") val clientSecret: String? = null,
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("token_type") val tokenType: String = "Bearer",
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("earliest_refresh_at") val earliestRefreshAt: Long? = null,
        val scope: String? = null,
    )

    data class Pkce(val verifier: String, val challenge: String)

    data class AuthorizationRequest(
        val authorizationEndpoint: String,
        val clientId: String,
        val redirectUri: String,
        val pkce: Pkce,
        val state: String,
        val scope: String? = null,
        val resources: List<String> = emptyList(),
        val additionalParameters: Map<String, String> = emptyMap(),
    )

    data class RevocationRequest(
        val revocationEndpoint: String,
        val clientId: String,
        val refreshToken: String,
    )

    data class AuthorizationCodeTokenRequest(
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String? = null,
        val code: String,
        val codeVerifier: String,
        val redirectUri: String,
        val resources: List<String> = emptyList(),
        val additionalParameters: Map<String, String> = emptyMap(),
    )

    data class RefreshTokenRequest(
        val tokenEndpoint: String,
        val clientId: String,
        val clientSecret: String? = null,
        val refreshToken: String,
        val scope: String? = null,
        val resources: List<String> = emptyList(),
        val additionalParameters: Map<String, String> = emptyMap(),
    )

    suspend fun registerClient(
        registrationEndpoint: String,
        request: ClientRegistrationRequest,
    ): ClientRegistrationResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(ClientRegistrationRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url(registrationEndpoint)
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .tag(SensitiveRequest::class.java, SensitiveRequest)
            .build()
        val text = execute(httpRequest, "OAuth client registration")
        json.decodeFromString(ClientRegistrationResponse.serializer(), text)
    }

    fun generatePkce(): Pkce {
        val verifierBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val verifier = base64Url(verifierBytes)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Pkce(verifier = verifier, challenge = base64Url(digest))
    }

    fun generateState(): String {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return base64Url(bytes)
    }

    fun buildAuthorizationUrl(request: AuthorizationRequest): String {
        val base = request.authorizationEndpoint.toHttpUrlOrNull()
            ?: error("Invalid authorization endpoint: ${request.authorizationEndpoint}")
        requireNoReservedParameters(request.additionalParameters.keys, AUTHORIZATION_PARAMETERS)
        requireNoReservedParameters(base.queryParameterNames, AUTHORIZATION_PARAMETERS + request.additionalParameters.keys)
        return base.newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", request.clientId)
            .addQueryParameter("redirect_uri", request.redirectUri)
            .addQueryParameter("code_challenge", request.pkce.challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", request.state)
            .apply {
                if (!request.scope.isNullOrBlank()) addQueryParameter("scope", request.scope)
                request.resources.forEach { addQueryParameter("resource", it) }
                request.additionalParameters.forEach { (name, value) -> addQueryParameter(name, value) }
            }
            .build()
            .toString()
    }

    suspend fun exchangeAuthorizationCode(
        request: AuthorizationCodeTokenRequest,
    ): TokenResponse = withContext(Dispatchers.IO) {
        requireNoReservedParameters(request.additionalParameters.keys, TOKEN_PARAMETERS)
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", request.code)
            .add("redirect_uri", request.redirectUri)
            .add("client_id", request.clientId)
            .add("code_verifier", request.codeVerifier)
            .apply {
                if (!request.clientSecret.isNullOrBlank()) add("client_secret", request.clientSecret)
                request.resources.forEach { add("resource", it) }
                request.additionalParameters.forEach { (name, value) -> add(name, value) }
            }
            .build()
        postToken(request.tokenEndpoint, form)
    }

    suspend fun refreshToken(request: RefreshTokenRequest): TokenResponse =
        withContext(Dispatchers.IO) {
            requireNoReservedParameters(request.additionalParameters.keys, TOKEN_PARAMETERS)
            val form = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", request.refreshToken)
                .add("client_id", request.clientId)
                .apply {
                    if (!request.clientSecret.isNullOrBlank()) add("client_secret", request.clientSecret)
                    if (!request.scope.isNullOrBlank()) add("scope", request.scope)
                    request.resources.forEach { add("resource", it) }
                    request.additionalParameters.forEach { (name, value) -> add(name, value) }
                }
                .build()
            postToken(request.tokenEndpoint, form)
        }

    suspend fun revokeToken(request: RevocationRequest) = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("token", request.refreshToken)
            .add("token_type_hint", "refresh_token")
            .add("client_id", request.clientId)
            .build()
        val httpRequest = Request.Builder()
            .url(request.revocationEndpoint)
            .header("Accept", "application/json")
            .post(form)
            .tag(SensitiveRequest::class.java, SensitiveRequest)
            .build()
        executeRaw(httpRequest).use { response ->
            if (!response.isSuccessful) throw IOException("OAuth revocation failed (HTTP ${response.code})")
        }
    }

    suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .tag(SensitiveRequest::class.java, SensitiveRequest)
            .build()
        val operation = when (request.url.encodedPath) {
            "/.well-known/openid-configuration" -> "OAuth discovery"
            "/.well-known/jwks.json" -> "OAuth signing key retrieval"
            else -> "OAuth metadata retrieval"
        }
        execute(request, operation)
    }

    private suspend fun postToken(tokenEndpoint: String, form: FormBody): TokenResponse {
        val request = Request.Builder()
            .url(tokenEndpoint)
            .header("Accept", "application/json")
            .post(form)
            .tag(SensitiveRequest::class.java, SensitiveRequest)
            .build()
        val operation = if ((0 until form.size).any { form.name(it) == "grant_type" && form.value(it) == "refresh_token" }) {
            "OAuth token refresh"
        } else {
            "OAuth code exchange"
        }
        val text = execute(request, operation)
        return json.decodeFromString(TokenResponse.serializer(), text)
    }

    private suspend fun execute(request: Request, operation: String): String {
        executeRaw(request).use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val payload = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                val nested = payload?.get("error") as? JsonObject
                fun textField(obj: JsonObject?, name: String): String? = runCatching {
                    obj?.get(name)?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                fun identifier(value: String?): String? = value?.takeIf {
                    it.matches(Regex("[A-Za-z0-9_.-]{1,120}"))
                }
                val errorCode = identifier(textField(nested, "code") ?: textField(payload, "error"))
                val parameter = identifier(textField(nested, "param") ?: textField(payload, "error_param"))
                val description = textField(payload, "error_description") ?: textField(nested, "message")
                    ?: textField(payload, "message") ?: textField(payload, "detail")
                val requestId = identifier(response.header("x-request-id") ?: response.header("openai-request-id"))
                throw OAuthRequestException(
                    statusCode = response.code,
                    errorCode = errorCode,
                    message = buildString {
                        append("$operation failed (HTTP ${response.code})")
                        errorCode?.let { append(" ($it)") }
                        parameter?.let { append(" [parameter: $it]") }
                        description?.let { append(": ${safeErrorDescription(it, request)}") }
                        requestId?.let { append(" [request: $it]") }
                    },
                )
            }
            return body
        }
    }

    private suspend fun executeRaw(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(response) else response.close()
                }
            })
        }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // Never surface raw response bodies or allow echoed credentials into the UI/logs.
    private fun safeErrorDescription(description: String, request: Request): String {
        val form = request.body as? FormBody
        val secrets = buildList {
            if (form != null) {
                (0 until form.size).filter { form.name(it) in SENSITIVE_PARAMETERS }.forEach { add(form.value(it)) }
            }
            (0 until request.url.querySize).filter { request.url.queryParameterName(it) in SENSITIVE_PARAMETERS }
                .forEach { request.url.queryParameterValue(it)?.let(::add) }
        }.filter(String::isNotBlank).sortedByDescending(String::length)
        var safe = description
        secrets.forEach { secret ->
            safe = safe.replace(secret, "[redacted]")
                .replace(URLEncoder.encode(secret, "UTF-8"), "[redacted]")
        }
        safe = Regex("(?i)Bearer\\s+\\S+").replace(safe, "Bearer [redacted]")
        safe = Regex("[A-Za-z0-9_+/.=-]{40,}").replace(safe, "[redacted]")
        safe = Regex("[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").replace(safe, "[redacted]")
        return safe.replace(Regex("[\\p{Cc}\\p{Cf}\\s]+"), " ").trim().take(500)
    }

    private fun requireNoReservedParameters(names: Set<String>, reserved: Set<String>) {
        require(names.none { it in reserved }) { "OAuth parameters must be supplied through their dedicated request fields" }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val AUTHORIZATION_PARAMETERS = setOf(
            "response_type", "client_id", "redirect_uri", "code_challenge",
            "code_challenge_method", "state", "scope", "resource",
        )
        val TOKEN_PARAMETERS = setOf(
            "grant_type", "client_id", "client_secret", "code", "code_verifier",
            "redirect_uri", "refresh_token", "scope", "resource",
        )
        val SENSITIVE_PARAMETERS = setOf(
            "code", "code_verifier", "client_secret", "refresh_token", "token",
            "access_token", "id_token", "id_token_hint", "login_hint",
        )
    }
}

class OAuthRequestException(
    val statusCode: Int,
    val errorCode: String?,
    message: String,
) : IOException(message)
