package me.rerere.ai.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.internal.http.RealResponseBody

fun List<CustomHeader>.toHeaders(): Headers {
    return Headers.Builder().apply {
        this@toHeaders
            .filter { it.name.isNotBlank() }
            .forEach {
                add(it.name, it.value)
            }
    }.build()
}

fun Request.Builder.configureReferHeaders(url: String): Request.Builder {
    val httpUrl = url.toHttpUrl()
    return when (httpUrl.host) {
        "openrouter.ai" -> {
            this
                .addHeader("X-Title", "Friendly")
                .addHeader("HTTP-Referer", "https://friendly.app")
        }

        else -> this
    }
}

fun Request.Builder.configureSessionHeaders(url: String, sessionId: String?): Request.Builder = apply {
    if (sessionId != null) {
        header("X-Session-ID", sessionId)
        if (url.toHttpUrl().host == "opencode.ai") {
            header("x-opencode-session", sessionId)
        }
    }
}

fun ResponseBody.stringSafe(): String? {
    return when (this) {
        is RealResponseBody -> string()
        else -> null
    }
}

fun JsonObject.mergeCustomBody(bodies: List<CustomBody>): JsonObject {
    if (bodies.isEmpty()) return this

    val content = toMutableMap()
    bodies.forEach { body ->
        if (body.key.isNotBlank()) {

            val existingValue = content[body.key]
            val newValue = body.value

            if (existingValue is JsonObject && newValue is JsonObject) {

                content[body.key] = mergeJsonObjects(existingValue, newValue)
            } else {

                content[body.key] = newValue
            }
        }
    }
    return JsonObject(content)
}


private fun mergeJsonObjects(base: JsonObject, overlay: JsonObject): JsonObject {
    val result = base.toMutableMap()

    for ((key, value) in overlay) {
        val baseValue = result[key]

        result[key] = if (baseValue is JsonObject && value is JsonObject) {

            mergeJsonObjects(baseValue, value)
        } else {

            value
        }
    }

    return JsonObject(result)
}


fun JsonElement.removeElements(keys: List<String>, keepOnly: Boolean = false): JsonElement {
    return when (this) {
        is JsonObject -> {
            val newContent = if (keepOnly) {

                keys.mapNotNull { key ->
                    get(key)?.let { key to it }
                }.toMap()
            } else {

                toMap().filterKeys { key -> key !in keys }
            }


            JsonObject(newContent.mapValues { (_, value) ->
                value.removeElements(keys, keepOnly)
            })
        }

        is JsonArray -> {
            JsonArray(map { it.removeElements(keys, keepOnly) })
        }

        else -> this
    }
}
