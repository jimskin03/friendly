package me.rerere.rikkahub.service.phone

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Known app actions that can open straight from an intent, before the
 * name/package matcher. A missing handler falls back to that matcher.
 * Dialing a cellular call stays on [PhoneCallController.placeCall].
 */
internal object DirectAppIntents {
    private const val TAG = "DirectAppIntents"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val MAPS_PACKAGE = "com.google.android.apps.maps"

    private val WHATSAPP = Regex("\\bwhatsapp\\b", RegexOption.IGNORE_CASE)
    private val MAPS = Regex("\\bgoogle\\s+maps\\b|\\bmaps\\b", RegexOption.IGNORE_CASE)
    private val DIRECTIONS = Regex("\\b(directions?|navigate|navigation|route)\\b", RegexOption.IGNORE_CASE)
    private val CALL = Regex("\\b(call|dial)\\b", RegexOption.IGNORE_CASE)
    private val DIALER_APP = Regex("\\b(phone|dialer)\\b", RegexOption.IGNORE_CASE)
    private val PHONE_CANDIDATE = Regex("""\+?\d[\d\s().-]{1,}\d|\b\d{3,}\b""")

    sealed class Result {
        data class Launched(val message: String) : Result()
        data class Blocked(val message: String) : Result()
        data class Fallback(val query: String) : Result()
    }

    fun resolve(context: Context, appQuery: String, phone: String, placeQuery: String): Result? {
        val whatsAppBlob = listOf(appQuery, phone).filter { it.isNotBlank() }.joinToString(" ")
        if (WHATSAPP.containsMatchIn(whatsAppBlob)) {
            return whatsApp(context, whatsAppBlob, phone)
        }
        maps(context, appQuery, placeQuery)?.let { return it }
        return dialer(context, appQuery, phone)
    }

    private fun whatsApp(context: Context, blob: String, explicitPhone: String): Result {
        val rawPhone = explicitPhone.ifBlank { extractPhone(blob).orEmpty() }
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.length in 8..15) {
            val intent = view("whatsapp://send?phone=$digits")
            if (start(context, intent)) {
                return Result.Launched("Opened WhatsApp chat via whatsapp://send?phone=$digits")
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (start(context, launch)) {
                return Result.Launched("Launched WhatsApp ($WHATSAPP_PACKAGE)")
            }
        }
        return Result.Fallback("WhatsApp")
    }

    private fun maps(context: Context, appQuery: String, placeQuery: String): Result? {
        val flags = "$appQuery $placeQuery"
        val directions = DIRECTIONS.containsMatchIn(flags)
        val mentionsMaps = MAPS.containsMatchIn(flags)
        if (!mentionsMaps && !directions) return null
        val source = if (placeQuery.isNotBlank()) placeQuery else appQuery
        val q = stripMapsQuery(source)
        if (q.isBlank()) return null
        val uri = if (directions) {
            "google.navigation:q=${Uri.encode(q)}"
        } else {
            "geo:0,0?q=${Uri.encode(q)}"
        }
        val targeted = view(uri).apply { setPackage(MAPS_PACKAGE) }
        if (start(context, targeted)) {
            return Result.Launched(mapsMessage(directions, q))
        }
        if (start(context, view(uri))) {
            return Result.Launched(mapsMessage(directions, q))
        }
        return Result.Fallback("Google Maps")
    }

    private fun dialer(context: Context, appQuery: String, explicitPhone: String): Result? {
        val blob = listOf(appQuery, explicitPhone).filter { it.isNotBlank() }.joinToString(" ")
        val askedToCall = CALL.containsMatchIn(blob)
        val dialerNamed = DIALER_APP.containsMatchIn(appQuery)
        if (!askedToCall && !(dialerNamed && explicitPhone.isNotBlank())) return null
        val raw = explicitPhone.ifBlank { extractPhone(blob).orEmpty() }
        if (raw.isBlank()) return null
        return when (val parsed = PhoneCallController.parseNumber(raw)) {
            is ParsedNumber.Emergency -> Result.Blocked("Refusing to dial an emergency number.")
            is ParsedNumber.Invalid -> null
            is ParsedNumber.Ok -> {
                val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", parsed.number, null)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (start(context, intent)) {
                    Result.Launched(
                        "Opened the dialer with ${parsed.number}. The call was not placed.",
                    )
                } else {
                    Result.Fallback("Phone")
                }
            }
        }
    }

    private fun mapsMessage(directions: Boolean, query: String): String {
        return if (directions) {
            "Opened Google Maps directions to $query"
        } else {
            "Opened Google Maps search for $query"
        }
    }

    private fun stripMapsQuery(raw: String): String {
        var rest = raw
        val patterns = listOf(
            "(?i)google\\s+maps",
            "(?i)\\bmaps\\b",
            "(?i)\\b(open|launch|start|please|show|app)\\b",
            "(?i)\\b(search( for)?|look up|find)\\b",
            "(?i)\\b(directions?|navigate|navigation|route)\\b",
        )
        for (pattern in patterns) {
            rest = rest.replace(Regex(pattern), " ")
        }
        rest = rest.replace(Regex("\\s+"), " ").trim(' ', ',', ':', '-', '.')
        return rest.replace(Regex("(?i)^to\\s+"), "").trim()
    }

    private fun extractPhone(raw: String): String? {
        return PHONE_CANDIDATE.findAll(raw)
            .map { it.value }
            .maxByOrNull { candidate -> candidate.count { it.isDigit() } }
    }

    private fun view(uri: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Direct intent failed", e)
            false
        }
    }
}
