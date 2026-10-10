package app.friendly.assistant.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

val JsonInstant by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}

val JsonInstantPretty by lazy {
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
}

val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private const val LEGACY_SERIAL_PACKAGE = "me.rerere.rikkahub."
private const val CURRENT_SERIAL_PACKAGE = "app.friendly.assistant."

/** Stored polymorphic JSON still names classes from before the package rename. */
fun migrateLegacySerialNames(json: String): String {
    if (!json.contains(LEGACY_SERIAL_PACKAGE)) return json
    return json.replace(LEGACY_SERIAL_PACKAGE, CURRENT_SERIAL_PACKAGE)
}

inline fun <reified T> Json.decodeStored(string: String): T =
    decodeFromString(migrateLegacySerialNames(string))
