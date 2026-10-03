package me.rerere.rikkahub.data.ai.mcp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.rerere.oauth.OAuthAuthorizationLauncher
import me.rerere.oauth.OAuthHttpClient
import me.rerere.oauth.OAuthLoopbackCallbackServer
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.datastore.SettingsStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

private const val TAG = "McpOAuthCoordinator"
private const val TOKEN_REFRESH_LEEWAY_MS = 60_000L
internal const val MCP_OAUTH_CALLBACK_PORT = 52_134
internal const val MCP_OAUTH_CALLBACK_PATH = "/oauth/callback"
internal const val MCP_OAUTH_REDIRECT_URI =
    "http://127.0.0.1:$MCP_OAUTH_CALLBACK_PORT$MCP_OAUTH_CALLBACK_PATH"
private val OAUTH_CALLBACK_TIMEOUT = 5.minutes


internal class McpOAuthCoordinator(
    private val settingsStore: SettingsStore,
    private val appScope: AppScope,
    private val oauthClient: OAuthHttpClient,
    private val discoveryClient: McpOAuthDiscoveryClient,
    private val callbackServer: OAuthLoopbackCallbackServer,
    private val authorizationLauncher: OAuthAuthorizationLauncher,
    private val updateStatus: (Uuid, McpStatus) -> Unit,
    private val requestReconnect: suspend (Uuid) -> Unit = {},
) {
    private val attempts = McpOAuthAttemptFence()
    private val refreshLocks = ConcurrentHashMap<Uuid, Mutex>()
    private val attemptPublisher = McpOAuthAttemptPublisher(
        fence = attempts,
        readSettings = { settingsStore.settingsFlow.value },
        assignSettings = settingsStore::assignSettingsInMemory,
        persistMcpOAuth = settingsStore::persistMcpServerOAuth,
    )

    fun startAuthorization(config: McpServerConfig, context: Context) {
        val appContext = context.applicationContext
        val attemptOut = AtomicLong()
        lateinit var job: Job
        job = appScope.launch(start = CoroutineStart.LAZY) {
            val attempt = attemptOut.get()
            if (!attempts.activate(config.id, attempt)) return@launch
            updateStatus(config.id, McpStatus.Authorizing)
            try {
                authorize(config, appContext, attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "OAuth authorization failed for ${config.commonOptions.name}", e)
                updateStatus(config.id, McpStatus.Error.from(e, fallbackMessage = "OAuth authorization failed"))
            }
        }
        attempts.open(config.id, attemptOut, job)
    }

    fun cancelAuthorization(configId: Uuid) {
        attempts.close(configId)
        updateStatus(configId, McpStatus.NeedsAuthorization)
    }

    fun isAuthorizationInProgress(configId: Uuid): Boolean = attempts.isActive(configId)

    fun forget(configId: Uuid) {
        attempts.close(configId)
        refreshLocks.remove(configId)
    }

    suspend fun clearAuthorization(config: McpServerConfig): McpServerConfig {
        persistOAuthState(config.id, null)
        return settingsStore.settingsFlow.value.mcpServers.find { it.id == config.id }
            ?: config.clone(commonOptions = config.commonOptions.copy(oauth = null))
    }


    suspend fun ensureFreshToken(configInput: McpServerConfig): McpServerConfig {
        val lock = refreshLocks.computeIfAbsent(configInput.id) { Mutex() }
        return lock.withLock {
            val config = settingsStore.settingsFlow.value.mcpServers.find { it.id == configInput.id }
                ?: configInput
            val oauth = config.commonOptions.oauth ?: return@withLock config
            if (!oauth.enabled || oauth.refreshToken.isNullOrBlank()) return@withLock config

            val expired = oauth.expiresAt > 0 &&
                System.currentTimeMillis() >= oauth.expiresAt - TOKEN_REFRESH_LEEWAY_MS
            if (!oauth.accessToken.isNullOrBlank() && !expired) return@withLock config

            val tokenEndpoint = oauth.tokenEndpoint ?: return@withLock config
            val clientId = oauth.clientId ?: return@withLock config
            runCatching {
                val token = oauthClient.refreshToken(
                    OAuthHttpClient.RefreshTokenRequest(
                        tokenEndpoint = tokenEndpoint,
                        clientId = clientId,
                        clientSecret = oauth.clientSecret,
                        refreshToken = oauth.refreshToken,
                        resources = listOf(
                            McpOAuthDiscoveryClient.oauthResourceIndicator(
                                config.serverUrl,
                                runCatching { discoveryClient.discoverProtectedResource(config.serverUrl).resource }
                                    .getOrNull(),
                            ),
                        ),
                        scope = oauth.scope,
                    )
                )
                val updated = oauth.copy(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken ?: oauth.refreshToken,
                    expiresAt = computeExpiry(token.expiresIn),
                    scope = token.scope ?: oauth.scope,
                )
                persistOAuthState(config.id, updated)
                config.clone(commonOptions = config.commonOptions.copy(oauth = updated))
            }.getOrElse {
                Log.w(TAG, "Token refresh failed for ${config.commonOptions.name}: ${it.message}")
                config
            }
        }
    }

    suspend fun needsAuthorization(config: McpServerConfig, error: Throwable): Boolean {
        val oauth = config.commonOptions.oauth
        val decision = shouldRequestMcpAuthorization(
            hasAccessToken = !oauth?.accessToken.isNullOrBlank(),
            oauthEnabled = oauth?.enabled == true,
            hasManualAuthorizationHeader = config.commonOptions.headers.any {
                it.first.equals("Authorization", ignoreCase = true)
            },
            error = error,
            protectedResourceDiscovered = false,
        )
        if (decision != null) return decision
        return runCatching { discoveryClient.discoverProtectedResource(config.serverUrl) }
            .onFailure {
                Log.i(TAG, "OAuth probe failed for ${config.commonOptions.name}: ${it.message}")
            }
            .isSuccess
    }

    private suspend fun authorize(config: McpServerConfig, context: Context, attempt: Long) = withContext(Dispatchers.IO) {
        val serverUrl = config.serverUrl
        require(serverUrl.isNotBlank()) { "Server URL is empty, unable to authorize" }

        val protectedResource = discoveryClient.discoverProtectedResource(serverUrl)
        val issuer = protectedResource.authorizationServers.firstOrNull()
            ?: error("Protected resource does not declare an authorization server")
        val metadata = discoveryClient.discoverAuthorizationServer(issuer)
        val authorizationEndpoint = metadata.authorizationEndpoint
            ?: error("Authorization server missing authorization_endpoint")
        val tokenEndpoint = metadata.tokenEndpoint
            ?: error("Authorization server missing token_endpoint")
        val scope = config.commonOptions.oauth?.scope
            ?: protectedResource.scopesSupported?.joinToString(" ")
            ?: metadata.scopesSupported?.joinToString(" ")

        val pkce = oauthClient.generatePkce()
        val state = oauthClient.generateState()
        val resource = McpOAuthDiscoveryClient.oauthResourceIndicator(
            serverUrl,
            protectedResource.resource,
        )
        val callbackSession = callbackServer.openSession(context, state)
        try {
            val redirectUri = callbackSession.redirectUri
            check(redirectUri == MCP_OAUTH_REDIRECT_URI) {
                "OAuth callback server address mismatch: $redirectUri"
            }
            val existing = config.commonOptions.oauth
            val canReuseClient = existing?.redirectUri == redirectUri && !existing.clientId.isNullOrBlank()
            var clientId = existing?.clientId.takeIf { canReuseClient }
            var clientSecret = existing?.clientSecret.takeIf { canReuseClient }
            if (clientId.isNullOrBlank()) {
                val registrationEndpoint = metadata.registrationEndpoint
                    ?: error("Authorization server does not support dynamic registration, and client_id is not preconfigured")
                val registration = oauthClient.registerClient(
                    registrationEndpoint = registrationEndpoint,
                    request = OAuthHttpClient.ClientRegistrationRequest(
                        clientName = config.commonOptions.name.ifBlank { "Friendly" },
                        redirectUris = listOf(redirectUri),
                        scope = scope,
                    ),
                )
                clientId = registration.clientId
                clientSecret = registration.clientSecret
            }

            val persistedClient = attemptPublisher.commit(
                configId = config.id,
                attempt = attempt,
                oauth = (existing ?: McpOAuthState()).copy(
                    enabled = true,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authorizationEndpoint = authorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    registrationEndpoint = metadata.registrationEndpoint,
                    redirectUri = redirectUri,
                    scope = scope,
                ),
            )
            if (!persistedClient) return@withContext

            val authorizationUrl = oauthClient.buildAuthorizationUrl(
                OAuthHttpClient.AuthorizationRequest(
                    authorizationEndpoint = authorizationEndpoint,
                    clientId = clientId,
                    redirectUri = redirectUri,
                    pkce = pkce,
                    state = state,
                    scope = scope,
                    resources = listOf(resource),
                )
            )
            withContext(Dispatchers.Main) {
                authorizationLauncher.launch(context, authorizationUrl)
            }

            val callback = callbackSession.awaitCallback(OAUTH_CALLBACK_TIMEOUT)
                ?: error("OAuth authorization timeout")
            callback.error?.let { error(buildAuthorizationError(it, callback.errorDescription)) }
            val code = callback.code ?: error("Authorization failed: No authorization code returned")

            val token = oauthClient.exchangeAuthorizationCode(
                OAuthHttpClient.AuthorizationCodeTokenRequest(
                    tokenEndpoint = tokenEndpoint,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    codeVerifier = pkce.verifier,
                    redirectUri = redirectUri,
                    resources = listOf(resource),
                )
            )
            val reconnectContext = coroutineContext
            var reconnectJob: Job? = null
            val persistedTokens = attemptPublisher.commit(
                configId = config.id,
                attempt = attempt,
                oauth = McpOAuthState(
                    enabled = true,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    authorizationEndpoint = authorizationEndpoint,
                    tokenEndpoint = tokenEndpoint,
                    registrationEndpoint = metadata.registrationEndpoint,
                    redirectUri = redirectUri,
                    scope = token.scope ?: scope,
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    expiresAt = computeExpiry(token.expiresIn),
                ),
            ) {
                // Scheduled only while this attempt is still current. Main keeps reconnect
                // off the commit caller, which is holding the per-server commit gate.
                reconnectJob = CoroutineScope(reconnectContext).launch(Dispatchers.Main) {
                    requestReconnect(config.id)
                }
            }
            if (!persistedTokens) return@withContext
            reconnectJob?.join()
        } finally {
            withContext(NonCancellable) {
                callbackSession.close()
            }
        }
    }

    private fun buildAuthorizationError(error: String, description: String?): String =
        if (description.isNullOrBlank()) "Authorization failed: $error" else "Authorization failed: $error ($description)"

    private suspend fun persistOAuthState(configId: Uuid, oauth: McpOAuthState?) {
        settingsStore.updateMcpServerOAuth(configId, oauth)
    }

    private fun computeExpiry(expiresIn: Long?): Long =
        if (expiresIn != null && expiresIn > 0) {
            System.currentTimeMillis() + expiresIn * 1000
        } else {
            0L
        }

}

internal fun looksLikeMcpAuthFailure(error: Throwable): Boolean {
    val message = mcpAuthFailureText(error)
    return message.contains("401") ||
        message.contains("403") ||
        message.contains("unauthorized") ||
        message.contains("forbidden") ||
        message.contains("insufficient_scope") ||
        message.contains("invalid_token") ||
        message.contains("invalid access token") ||
        message.contains("missing or invalid") ||
        message.contains("authorization required") ||
        message.contains("bearer token rejected") ||
        message.contains("not a valid authkit jwt") ||
        message.contains("not a valid jwt")
}

/**
 * Composio Connect rejects API keys and other non-JWT bearers with this shape.
 * A manual Authorization header must not hide that rejection.
 */
internal fun bearerRejectedAsNonOauthToken(error: Throwable): Boolean {
    val message = mcpAuthFailureText(error)
    return message.contains("bearer token rejected") ||
        message.contains("not a valid authkit jwt") ||
        message.contains("not a valid jwt") ||
        message.contains("not a valid oauth")
}

private fun mcpAuthFailureText(error: Throwable): String =
    generateSequence(error) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
        .lowercase()

/**
 * @return true or false when the decision does not need a metadata probe; null to probe.
 * A stored access token is a completed OAuth handoff. Only an auth failure may ask again.
 */
internal fun shouldRequestMcpAuthorization(
    hasAccessToken: Boolean,
    oauthEnabled: Boolean,
    hasManualAuthorizationHeader: Boolean,
    error: Throwable,
    protectedResourceDiscovered: Boolean,
): Boolean? {
    if (bearerRejectedAsNonOauthToken(error)) return true
    if (hasAccessToken) return looksLikeMcpAuthFailure(error)
    if (looksLikeMcpAuthFailure(error) && oauthEnabled) return true
    if (hasManualAuthorizationHeader) return false
    return if (protectedResourceDiscovered) true else null
}
