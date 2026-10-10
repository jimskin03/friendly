package me.rerere.ai.util

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.sse.EventSourceListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SSEEventSourceTest {
    @Test
    fun `event stream content type delivers events`() {
        val events = readEvents(
            "data: hello\n\n",
            "text/event-stream".toMediaType(),
        )
        assertEquals(listOf("hello"), events.data)
        assertNull(events.failure)
    }

    @Test
    fun `chatgpt stream without content type still delivers events`() {
        val events = readEvents(
            "event: response.output_text.delta\ndata: {\"delta\":\"hi\"}\n\n",
            null,
        )
        assertEquals(listOf("{\"delta\":\"hi\"}"), events.data)
        assertNull(events.failure)
    }

    @Test
    fun `missing content type on json is rejected`() {
        val events = readEvents("""{"error":"invalid_request"}""", null)
        assertTrue(events.data.isEmpty())
        assertTrue(events.failure?.message.orEmpty().contains("Invalid content-type: null"))
    }

    private fun readEvents(body: String, mediaType: okhttp3.MediaType?): Captured {
        val captured = Captured()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody(mediaType))
            .build()
        SSEEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(
                    eventSource: okhttp3.sse.EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    captured.data += data
                }

                override fun onFailure(
                    eventSource: okhttp3.sse.EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    captured.failure = t
                }
            },
        ).processResponse(response)
        return captured
    }

    private class Captured {
        val data = mutableListOf<String>()
        var failure: Throwable? = null
    }
}
