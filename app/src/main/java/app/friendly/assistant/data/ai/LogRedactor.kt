package app.friendly.assistant.data.ai

import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.Sink
import okio.Timeout
import okio.buffer

/**
 * Shared credential redaction for every network logging path (the OkHttp
 * [HttpLoggingInterceptor] and [RequestLoggingInterceptor]).
 *
 * Only log output is rewritten. Outbound requests are never modified.
 */
object LogRedactor {
    const val REDACTED = "██"

    /** URL-safe marker, so redacted URLs stay readable instead of percent-encoded. */
    const val REDACTED_URL = "REDACTED"

    /** Bytes of a request body kept for the in-app request log. */
    const val MAX_CAPTURED_BODY_BYTES = 32 * 1024

    private val SENSITIVE_HEADERS = setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "api-key",
        "x-goog-api-key",
        "anthropic-api-key",
        "x-anthropic-api-key",
        "ocp-apim-subscription-key",
    )

    private val SENSITIVE_HEADER_PARTS = listOf("key", "token", "secret", "auth", "password", "cookie", "session")

    /** Normalized (lowercase, no `_`/`-`) JSON field, query parameter and form field names. */
    private val SENSITIVE_NAMES = setOf(
        "key", "apikey", "xapikey", "xgoogapikey", "token", "accesstoken", "refreshtoken", "idtoken",
        "authtoken", "apitoken", "sessiontoken", "bearer", "secret", "clientsecret", "password", "passwd",
        "authorization", "auth", "privatekey", "credential", "credentials",
    )

    /** Extra names that are secrets in URLs and form bodies only (OAuth codes, signed URLs). */
    private val SENSITIVE_QUERY_NAMES = setOf("code", "codeverifier", "sig", "signature", "xamzsignature", "xamzsecuritytoken")

    private val SENSITIVE_NAME_SUFFIXES = listOf("apikey", "token", "secret", "password", "privatekey")

    fun isSensitiveHeader(name: String): Boolean {
        val lower = name.lowercase()
        return lower in SENSITIVE_HEADERS || SENSITIVE_HEADER_PARTS.any { lower.contains(it) }
    }

    private fun normalize(name: String) = name.lowercase().replace("_", "").replace("-", "")

    fun isSensitiveName(name: String): Boolean {
        val normalized = normalize(name)
        return normalized in SENSITIVE_NAMES || SENSITIVE_NAME_SUFFIXES.any { normalized.endsWith(it) }
    }

    fun isSensitiveQueryName(name: String): Boolean =
        isSensitiveName(name) || normalize(name) in SENSITIVE_QUERY_NAMES

    /** Header map for logs; keeps the existing "last value wins" shape. */
    fun redactHeaders(headers: Headers): Map<String, String> =
        headers.names().associateWith { name ->
            if (isSensitiveHeader(name)) REDACTED else headers[name] ?: ""
        }

    fun redactUrl(url: HttpUrl): String {
        val hasSecretQuery = (0 until url.querySize).any { isSensitiveQueryName(url.queryParameterName(it)) }
        if (!hasSecretQuery && url.username.isEmpty() && url.password.isEmpty()) return url.toString()
        val builder = url.newBuilder()
        if (url.username.isNotEmpty()) builder.username(REDACTED_URL)
        if (url.password.isNotEmpty()) builder.password(REDACTED_URL)
        if (hasSecretQuery) {
            builder.query(null)
            for (i in 0 until url.querySize) {
                val name = url.queryParameterName(i)
                val value = url.queryParameterValue(i)
                builder.addQueryParameter(name, if (value != null && isSensitiveQueryName(name)) REDACTED_URL else value)
            }
        }
        return builder.build().toString()
    }

    fun redactUrl(url: String): String = url.toHttpUrlOrNull()?.let(::redactUrl) ?: redactQueryText(url)

    private val QUERY_PARAM = Regex("""([?&;])([^=&?#\s"']+)=([^&#\s"']*)""")

    /** Redacts `name=value` pairs with a secret name inside free text (URLs in messages, form bodies). */
    fun redactQueryText(text: String): String =
        QUERY_PARAM.replace(text) { m ->
            if (isSensitiveQueryName(urlDecode(m.groupValues[2]))) "${m.groupValues[1]}${m.groupValues[2]}=$REDACTED_URL" else m.value
        }

    // A JSON string field, also when the capture was truncated in the middle of the value.
    private val JSON_STRING_FIELD = Regex(""""((?:[^"\\]|\\.){1,64})"(\s*:\s*)"[^"\\]*+(?:\\.[^"\\]*+)*+("|$)""")

    /** Redacts sensitive string fields in JSON (or JSON-like) text. */
    fun redactJsonText(text: String): String =
        JSON_STRING_FIELD.replace(text) { m ->
            if (isSensitiveName(m.groupValues[1])) {
                "\"${m.groupValues[1]}\"${m.groupValues[2]}\"$REDACTED\""
            } else {
                m.value
            }
        }

    private val FORM_PAIR = Regex("""(^|&)([^=&]+)=([^&]*)""")

    fun redactFormText(text: String): String =
        FORM_PAIR.replace(text) { m ->
            if (isSensitiveQueryName(urlDecode(m.groupValues[2]))) "${m.groupValues[1]}${m.groupValues[2]}=$REDACTED" else m.value
        }

    fun redactBodyText(text: String, contentType: MediaType?): String {
        val subtype = contentType?.subtype?.lowercase()
        return if (subtype == "x-www-form-urlencoded") redactFormText(text) else redactJsonText(text)
    }

    private val HEADER_LINE = Regex("""^([A-Za-z0-9!#$%&'*+.^_`|~-]+): (.*)$""")

    /** Redacts one line printed by [HttpLoggingInterceptor] (request line, header line, or message). */
    fun redactLogLine(line: String): String {
        HEADER_LINE.matchEntire(line)?.let { m ->
            if (isSensitiveHeader(m.groupValues[1])) return "${m.groupValues[1]}: $REDACTED"
        }
        return redactQueryText(line)
    }

    /** HttpLoggingInterceptor whose every printed line goes through [redactLogLine]. */
    fun httpLoggingInterceptor(
        sink: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT,
    ): HttpLoggingInterceptor =
        HttpLoggingInterceptor { message -> sink.log(redactLogLine(message)) }.apply {
            SENSITIVE_HEADERS.forEach { redactHeader(it) }
        }

    /**
     * Captures a request body for the in-app log without changing what is sent:
     * one-shot and duplex bodies are never read, non-text bodies are not buffered,
     * and at most [MAX_CAPTURED_BODY_BYTES] bytes are kept.
     */
    fun captureRequestBody(body: RequestBody?): String? {
        if (body == null) return null
        if (body.isOneShot() || body.isDuplex()) return "[body not captured: one-shot/streaming]"
        val type = body.contentType()
        val length = runCatching { body.contentLength() }.getOrDefault(-1L)
        if (!isTextual(type)) return "[body not captured: ${type ?: "unknown type"}, ${lengthLabel(length)}]"

        val capped = CappedSink(MAX_CAPTURED_BODY_BYTES.toLong())
        val sink = capped.buffer()
        body.writeTo(sink)
        sink.flush()
        var text = capped.kept.readUtf8()
        text = redactBodyText(text, type)
        if (capped.total > MAX_CAPTURED_BODY_BYTES) {
            text += "\n…[truncated, ${capped.total} bytes]"
        }
        return text
    }

    private fun isTextual(type: MediaType?): Boolean {
        if (type == null) return false
        val t = type.type.lowercase()
        val s = type.subtype.lowercase()
        if (t == "text") return true
        if (t != "application") return false
        return s == "json" || s.endsWith("+json") || s == "x-ndjson" || s == "xml" || s.endsWith("+xml") ||
            s == "x-www-form-urlencoded" || s == "graphql" || s == "javascript"
    }

    private fun lengthLabel(length: Long) = if (length >= 0) "$length bytes" else "unknown length"

    private fun urlDecode(s: String): String =
        runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}

/** Keeps the first [limit] bytes and only counts the rest. */
private class CappedSink(limit: Long) : Sink {
    private val maxBytes = limit
    val kept = Buffer()
    var total = 0L

    override fun write(source: Buffer, byteCount: Long) {
        val keep = minOf(byteCount, (maxBytes - kept.size).coerceAtLeast(0))
        if (keep > 0) source.read(kept, keep)
        if (byteCount > keep) source.skip(byteCount - keep)
        total += byteCount
    }

    override fun flush() = Unit
    override fun timeout(): Timeout = Timeout.NONE
    override fun close() = Unit
}
