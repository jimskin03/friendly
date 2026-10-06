package app.friendly.assistant.data.ai.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpOAuthAuthorizationTest {
    @Test
    fun `stored access token is not treated as authorization required unless the server rejects it`() {
        val timeout = IllegalStateException("timeout talking to mcp")
        assertFalse(
            shouldRequestMcpAuthorization(
                hasAccessToken = true,
                oauthEnabled = true,
                hasManualAuthorizationHeader = false,
                error = timeout,
                protectedResourceDiscovered = true,
            )!!
        )
        assertTrue(
            shouldRequestMcpAuthorization(
                hasAccessToken = true,
                oauthEnabled = true,
                hasManualAuthorizationHeader = false,
                error = IllegalStateException("HTTP 401 Unauthorized"),
                protectedResourceDiscovered = false,
            )!!
        )
    }

    @Test
    fun `missing token still probes a protected resource`() {
        assertEquals(
            null,
            shouldRequestMcpAuthorization(
                hasAccessToken = false,
                oauthEnabled = false,
                hasManualAuthorizationHeader = false,
                error = IllegalStateException("connection reset"),
                protectedResourceDiscovered = false,
            )
        )
        assertTrue(
            shouldRequestMcpAuthorization(
                hasAccessToken = false,
                oauthEnabled = false,
                hasManualAuthorizationHeader = false,
                error = IllegalStateException("connection reset"),
                protectedResourceDiscovered = true,
            )!!
        )
    }

    @Test
    fun `manual authorization header skips the oauth probe`() {
        assertFalse(
            shouldRequestMcpAuthorization(
                hasAccessToken = false,
                oauthEnabled = false,
                hasManualAuthorizationHeader = true,
                error = IllegalStateException("connection reset"),
                protectedResourceDiscovered = true,
            )!!
        )
    }

    @Test
    fun `composio authkit rejection starts oauth even when a manual bearer is configured`() {
        val error = RuntimeException(
            "io.modelcontextprotocol.kotlin.sdk.types.McpException: Error while sending message: " +
                "Streamable HTTP error: {\"error\":\"Authorization required\"," +
                "\"reason\":\"Bearer token rejected: not a valid AuthKit JWT for this resource, " +
                "or no matching Composio account\"}",
        )
        assertTrue(looksLikeMcpAuthFailure(error))
        assertTrue(
            shouldRequestMcpAuthorization(
                hasAccessToken = false,
                oauthEnabled = false,
                hasManualAuthorizationHeader = true,
                error = error,
                protectedResourceDiscovered = false,
            )!!
        )
        assertTrue(
            shouldRequestMcpAuthorization(
                hasAccessToken = true,
                oauthEnabled = true,
                hasManualAuthorizationHeader = false,
                error = error,
                protectedResourceDiscovered = false,
            )!!
        )
    }

    @Test
    fun `oauth resource uses protected resource metadata instead of a typed url variant`() {
        assertEquals(
            "https://connect.composio.dev/mcp",
            McpOAuthDiscoveryClient.oauthResourceIndicator(
                serverUrl = "https://connect.composio.dev/mcp/?session=1#frag",
                metadataResource = "https://connect.composio.dev/mcp",
            ),
        )
        assertEquals(
            "https://connect.composio.dev/mcp",
            McpOAuthDiscoveryClient.oauthResourceIndicator(
                serverUrl = "https://connect.composio.dev/mcp#frag",
                metadataResource = null,
            ),
        )
    }
}
