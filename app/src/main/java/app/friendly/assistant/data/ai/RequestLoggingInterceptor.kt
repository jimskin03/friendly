package app.friendly.assistant.data.ai

import me.rerere.common.android.LogEntry
import me.rerere.common.android.Logging
import okhttp3.Interceptor
import okhttp3.Response

class RequestLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!Logging.isRequestLoggingEnabled()) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        val requestHeaders = LogRedactor.redactHeaders(request.headers)
        val requestBody = runCatching { LogRedactor.captureRequestBody(request.body) }
            .getOrElse { "[body not captured: ${it.javaClass.simpleName}]" }
        val loggedUrl = LogRedactor.redactUrl(request.url)

        val response: Response
        var error: String? = null

        try {
            response = chain.proceed(request)
        } catch (e: Exception) {
            error = e.message?.let(LogRedactor::redactQueryText)
            Logging.logRequest(
                LogEntry.RequestLog(
                    tag = "HTTP",
                    url = loggedUrl,
                    method = request.method,
                    requestHeaders = requestHeaders,
                    requestBody = requestBody,
                    error = error
                )
            )
            throw e
        }

        val durationMs = System.currentTimeMillis() - startTime
        val responseHeaders = LogRedactor.redactHeaders(response.headers)

        Logging.logRequest(
            LogEntry.RequestLog(
                tag = "HTTP",
                url = loggedUrl,
                method = request.method,
                requestHeaders = requestHeaders,
                requestBody = requestBody,
                responseCode = response.code,
                responseHeaders = responseHeaders,
                durationMs = durationMs,
                error = error
            )
        )

        return response
    }
}
