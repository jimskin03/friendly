package me.rerere.rikkahub.ui.pages.apps

import android.webkit.WebSettings

/**
 * WebView settings for installed web apps (Apps tab / Favorites).
 *
 * Installed apps should be laid out like a phone browser tab (Chrome / Brave on Android):
 * - Layout width tracks the WebView width. Pages with `<meta name="viewport"
 *   content="width=device-width">` get the device width in CSS px; legacy pages without a
 *   viewport meta still get the zoomed-out overview instead of a cramped 1:1 render.
 * - The user agent always advertises a phone (`Mobile`) so UA-sniffing sites serve their
 *   mobile layout even on OEM / large-screen WebView builds that drop the token (issue #7).
 * - Pinch zoom stays available; the on-screen +/- buttons stay hidden.
 *
 * Kept separate from the general [me.rerere.rikkahub.ui.pages.webview.WebViewPage] browser so
 * changes here only affect installed web apps.
 */
internal fun WebSettings.applyInstalledWebAppSettings() {
    builtInZoomControls = true
    displayZoomControls = false
    useWideViewPort = true
    loadWithOverviewMode = true
    mobileUserAgent(userAgentString)?.let { userAgentString = it }
}

/**
 * Returns [current] with a phone `Mobile` token, or `null` when it already has one (or is blank)
 * so the WebView default is left untouched.
 */
internal fun mobileUserAgent(current: String?): String? {
    if (current.isNullOrBlank()) return null
    if (MOBILE_TOKEN.containsMatchIn(current)) return null
    return if (current.contains(" Safari/")) {
        current.replaceFirst(" Safari/", " Mobile Safari/")
    } else {
        "$current Mobile"
    }
}

private val MOBILE_TOKEN = Regex("""\bMobile\b""")
