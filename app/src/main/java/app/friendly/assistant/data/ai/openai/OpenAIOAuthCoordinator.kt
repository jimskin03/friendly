package app.friendly.assistant.data.ai.openai

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.oauth.CustomTabsOAuthAuthorizationLauncher
import me.rerere.oauth.OAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.oauth.OAuthRequestException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

class OpenAIOAuthCoordinator(
    private val store: OpenAIAccountStore,
    private val oauth: OAuthHttpClient,
    private val callbackServer: OAuthLoopbackCallbackServer,
    private val launcher: OAuthAuthorizationLauncher = CustomTabsOAuthAuthorizationLauncher,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()
    private val signInLock = Mutex()

    suspend fun connect(context: Context, accountId: String? = null): OpenAIAccountSummary = withContext(Dispatchers.IO) {
        check(signInLock.tryLock()) { "A ChatGPT sign-in is already in progress" }
        try {
            connectAccount(context, accountId)
        } finally {
            signInLock.unlock()
        }
    }

    private suspend fun connectAccount(context: Context, selectedAccountId: String?): OpenAIAccountSummary {
        // A null selection always starts a new registration, even when other accounts exist.
        val returning = selectedAccountId?.let { store.get(it) ?: error("The selected ChatGPT account is no longer available") }
        require(returning == null || (returning.clientId.isNotBlank() && returning.clientId != DYNAMIC_CLIENT_ID)) {
            "The saved account registration is invalid. Add the account again."
        }
        val pkce = oauth.generatePkce()
        val state = oauth.generateState()
        val nonce = oauth.generateState()
        val session = callbackServer.openSession(context.applicationContext, state)
        try {
            val clientId = returning?.clientId ?: DYNAMIC_CLIENT_ID
            val redirectUri = session.redirectUri
            val authUrl = oauth.buildAuthorizationUrl(
                OAuthHttpClient.AuthorizationRequest(
                    authorizationEndpoint = AUTHORIZATION_ENDPOINT,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    pkce = pkce,
                    state = state,
                    scope = SCOPES,
                    resources = listOf(OPENAI_RESOURCE),
                    additionalParameters = buildMap {
                        put("nonce", nonce)
                        put("ext_agent_host_id", store.hostId())
                        if (returning == null) {
                            put("agent_name_hint", "Friendly")
                        } else {
                            if (returning.idToken.isNotBlank()) put("id_token_hint", returning.idToken)
                            if (returning.email.isNotBlank()) put("login_hint", returning.email)
                        }
                    },
                ),
            )
            withContext(Dispatchers.Main) { launcher.launch(context.applicationContext, authUrl) }
            val callback = session.awaitCallback(5.minutes)
                ?: error("Sign-in expired. Please try again.")
            callback.error?.let { error(callback.errorDescription ?: it) }
            val code = callback.code ?: error("Authorization did not return a code")
            val issuedClientId = callback.clientId ?: returning?.clientId
                ?: error("The authorization server did not return the issued client ID")
            require(issuedClientId.isNotBlank() && issuedClientId != DYNAMIC_CLIENT_ID) {
                "The authorization server did not return a valid issued client ID"
            }
            if (returning != null && issuedClientId != returning.clientId) {
                error("The returned account registration does not match the saved registration")
            }
            val accountId = returning?.id ?: UUID.randomUUID().toString()
            if (returning == null) {
                require(store.list().none { it.clientId == issuedClientId }) { "This account registration is already saved. Reconnect it instead." }
                // Keep the issued registration if exchange fails so the user can retry it.
                store.save(OpenAIStoredAccount(id = accountId, clientId = issuedClientId))
            }
            val token = oauth.exchangeAuthorizationCode(
                OAuthHttpClient.AuthorizationCodeTokenRequest(
                    tokenEndpoint = TOKEN_ENDPOINT,
                    clientId = issuedClientId,
                    code = code,
                    codeVerifier = pkce.verifier,
                    redirectUri = redirectUri,
                    resources = listOf(OPENAI_RESOURCE),
                ),
            )
            val identity = validateIdToken(token.idToken ?: error("The identity token was not returned"), nonce, issuedClientId)
            require(returning == null || returning.subject.isBlank() || returning.subject == identity.subject) {
                "Sign in with the selected ChatGPT account, or use Add another account."
            }
            val grantedScopes = token.scope.orEmpty().split(' ').filter(String::isNotBlank)
            require(PLAN_SCOPE in grantedScopes) {
                "Sign-in succeeded, but ChatGPT plan access was not granted. Reconnect and allow plan access."
            }
            val account = OpenAIStoredAccount(
                id = accountId,
                clientId = issuedClientId,
                subject = identity.subject,
                email = identity.email,
                name = identity.name,
                accessToken = token.accessToken,
                refreshToken = token.refreshToken ?: returning?.refreshToken.orEmpty(),
                idToken = token.idToken.orEmpty(),
                scopes = grantedScopes,
                accessExpiresAt = System.currentTimeMillis() + (token.expiresIn ?: 3600L) * 1000,
                earliestRefreshAt = token.earliestRefreshAt,
            )
            require(account.refreshToken.isNotBlank()) { "OpenAI did not return a refresh token" }
            store.save(account)
            return OpenAIAccountSummary(account.id, account.subject, account.email, account.name, account.isConnected)
        } finally {
            withContext(NonCancellable) { session.close() }
        }
    }

    suspend fun accessToken(accountId: String): String {
        val mutex = refreshLocks.computeIfAbsent(accountId) { Mutex() }
        return mutex.withLock {
            val account = store.get(accountId) ?: error("Reconnect your ChatGPT account to continue")
            val now = System.currentTimeMillis()
            val refreshAt = maxOf(account.accessExpiresAt - EXPIRY_LEEWAY_MS, account.earliestRefreshAt ?: 0L)
            if (account.accessToken.isNotBlank() && now < account.accessExpiresAt && now < refreshAt) {
                return@withLock account.accessToken
            }
            require(account.refreshToken.isNotBlank()) { "Reconnect your ChatGPT account to continue" }
            if ((account.earliestRefreshAt ?: 0L) > now && account.accessExpiresAt <= now) {
                error("ChatGPT renewal is not available yet; retry shortly")
            }
            val refreshed = try {
                oauth.refreshToken(
                    OAuthHttpClient.RefreshTokenRequest(
                        tokenEndpoint = TOKEN_ENDPOINT,
                        clientId = account.clientId,
                        refreshToken = account.refreshToken,
                        resources = listOf(OPENAI_RESOURCE),
                    ),
                )
            } catch (error: Exception) {
                if (error is OAuthRequestException && error.errorCode in TERMINAL_REFRESH_ERRORS) {
                    store.save(account.copy(accessToken = "", refreshToken = "", idToken = "", accessExpiresAt = 0L))
                    throw IllegalStateException("ChatGPT session expired. Sign in again.", error)
                }
                throw error
            }
            val next = account.copy(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: account.refreshToken,
                idToken = refreshed.idToken ?: account.idToken,
                scopes = refreshed.scope?.split(' ')?.filter(String::isNotBlank) ?: account.scopes,
                accessExpiresAt = now + (refreshed.expiresIn ?: 3600L) * 1000,
                earliestRefreshAt = refreshed.earliestRefreshAt,
            )
            if (store.get(accountId)?.refreshToken != account.refreshToken) {
                error("ChatGPT account changed during token refresh")
            }
            store.save(next)
            next.accessToken
        }
    }

    suspend fun disconnect(accountId: String): Boolean {
        val mutex = refreshLocks.computeIfAbsent(accountId) { Mutex() }
        return mutex.withLock {
            val account = store.get(accountId) ?: return@withLock true
            // Local disconnect takes effect immediately while preserving the client registration.
            store.save(account.copy(
                accessToken = "",
                refreshToken = "",
                idToken = "",
                scopes = emptyList(),
                accessExpiresAt = 0L,
                earliestRefreshAt = null,
            ))
            runCatching {
                val metadata = json.parseToJsonElement(oauth.getJson(DISCOVERY_ENDPOINT)).jsonObject
                val revoke = metadata["revocation_endpoint"]?.jsonPrimitive?.contentOrNull
                    ?: REVOCATION_ENDPOINT
                oauth.revokeToken(OAuthHttpClient.RevocationRequest(revoke, account.clientId, account.refreshToken))
            }.isSuccess
        }
    }

    private suspend fun validateIdToken(jwt: String, nonce: String, clientId: String): Identity = withContext(Dispatchers.IO) {
        val parts = jwt.split('.')
        require(parts.size == 3) { "Invalid identity token" }
        val header = json.parseToJsonElement(decode(parts[0])).jsonObject
        val claims = json.parseToJsonElement(decode(parts[1])).jsonObject
        require(header["alg"]?.jsonPrimitive?.content == "RS256") { "Unsupported identity token signature" }
        val discovery = json.parseToJsonElement(oauth.getJson(DISCOVERY_ENDPOINT)).jsonObject
        val issuer = discovery["issuer"]?.jsonPrimitive?.content ?: ISSUER
        require(claims["iss"]?.jsonPrimitive?.content == issuer) { "Identity token issuer did not match" }
        val audience = claims["aud"]?.let { value ->
            runCatching { value.jsonArray.map { it.jsonPrimitive.content } }
                .getOrElse { listOf(value.jsonPrimitive.content) }
        }.orEmpty()
        require(clientId in audience) { "Identity token audience did not match" }
        if (audience.size > 1) {
            require(claims["azp"]?.jsonPrimitive?.content == clientId) { "Identity token authorized party did not match" }
        }
        require((claims["exp"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L) > System.currentTimeMillis() / 1000) {
            "Identity token has expired"
        }
        claims["nbf"]?.jsonPrimitive?.content?.toLongOrNull()?.let { notBefore ->
            require(notBefore <= System.currentTimeMillis() / 1000 + 30) { "Identity token is not yet valid" }
        }
        require(claims["nonce"]?.jsonPrimitive?.content == nonce) { "Identity token nonce did not match" }
        val jwksUri = discovery["jwks_uri"]?.jsonPrimitive?.content ?: "$ISSUER/.well-known/jwks.json"
        require(runCatching { java.net.URI(jwksUri).let { it.scheme == "https" && it.host == "auth.openai.com" } }.getOrDefault(false)) {
            "OpenAI signing key location was not trusted"
        }
        val keys = json.parseToJsonElement(oauth.getJson(jwksUri)).jsonObject["keys"]?.jsonArray
            ?: error("OpenAI signing keys are unavailable")
        val kid = header["kid"]?.jsonPrimitive?.content
        val jwk = keys.firstOrNull { it.jsonObject["kid"]?.jsonPrimitive?.content == kid }?.jsonObject
            ?: error("Identity token signing key was not found")
        require(jwk["kty"]?.jsonPrimitive?.content == "RSA") { "Unsupported identity token key" }
        val modulus = BigInteger(1, decodeBytes(jwk["n"]!!.jsonPrimitive.content))
        val exponent = BigInteger(1, decodeBytes(jwk["e"]!!.jsonPrimitive.content))
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, exponent))
        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(publicKey)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        require(verifier.verify(decodeBytes(parts[2]))) { "Identity token signature is invalid" }
        val subject = claims["sub"]?.jsonPrimitive?.contentOrNull.orEmpty()
        require(subject.isNotBlank()) { "Identity token has no subject" }
        Identity(subject, claims["email"]?.jsonPrimitive?.contentOrNull.orEmpty(), claims["name"]?.jsonPrimitive?.contentOrNull.orEmpty())
    }

    private data class Identity(val subject: String, val email: String, val name: String)

    private fun decode(value: String): String = decodeBytes(value).toString(Charsets.UTF_8)
    private fun decodeBytes(value: String): ByteArray = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private companion object {
        const val ISSUER = "https://auth.openai.com"
        const val AUTHORIZATION_ENDPOINT = "$ISSUER/api/accounts/authorize"
        const val TOKEN_ENDPOINT = "$ISSUER/api/accounts/oauth/token"
        const val REVOCATION_ENDPOINT = "$ISSUER/api/accounts/oauth/revoke"
        const val DISCOVERY_ENDPOINT = "$ISSUER/.well-known/openid-configuration"
        const val OPENAI_RESOURCE = "https://api.openai.com/v1"
        const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
        const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
        const val SCOPES = "openid profile email offline_access resource.invoke $PLAN_SCOPE"
        const val EXPIRY_LEEWAY_MS = 60_000L
        val TERMINAL_REFRESH_ERRORS = setOf(
            "invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
            "refresh_token_invalidated", "refresh_token_reused",
        )
    }
}
