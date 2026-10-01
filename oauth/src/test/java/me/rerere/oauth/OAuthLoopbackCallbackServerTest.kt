package me.rerere.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class OAuthLoopbackCallbackServerTest {
    private val httpClient = OkHttpClient()

    @Test
    fun `callback is delivered only to matching state`() = runBlocking {
        val server = OAuthLoopbackCallbackServer()
        val session = server.openSession("expected-state")
        try {
            execute("${session.redirectUri}?code=wrong-code&state=wrong-state").use { response ->
                assertEquals(400, response.code)
            }

            val callbackJob = async(Dispatchers.IO) {
                session.awaitCallback(2.seconds)
            }
            withContext(Dispatchers.IO) {
                execute("${session.redirectUri}?code=auth-code&state=expected-state").use { response ->
                    assertEquals(200, response.code)
                    val body = response.body.string()
                    assertTrue(body.contains("Authorization complete"))
                    assertTrue(body.contains("Friendly"))
                    assertFalse(body.contains("RikkaHub"))
                }
            }

            val callback = callbackJob.await()
            assertEquals("auth-code", callback?.code)
            assertEquals("expected-state", callback?.state)
            assertFalse(callback?.error != null)
        } finally {
            session.close()
        }
    }

    @Test
    fun `callback is delivered on the ipv4 loopback address before the response is closed`() = runBlocking {
        val server = OAuthLoopbackCallbackServer()
        val session = server.openSession("state-1")
        try {
            val callbackJob = async(Dispatchers.IO) { session.awaitCallback(3.seconds) }
            withContext(Dispatchers.IO) {
                execute("${session.redirectUri}?code=from-browser&state=state-1").use { response ->
                    assertEquals(200, response.code)
                }
            }
            assertEquals("from-browser", callbackJob.await()?.code)
        } finally {
            session.close()
        }
    }

    private fun execute(url: String) = httpClient.newCall(
        Request.Builder()
            .url(url)
            .build()
    ).execute()
}
