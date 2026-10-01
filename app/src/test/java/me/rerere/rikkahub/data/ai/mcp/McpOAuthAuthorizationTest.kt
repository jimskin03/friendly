package me.rerere.rikkahub.data.ai.mcp

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
}
