package me.rerere.rikkahub.ui.pages.apps

import android.webkit.WebSettings
import android.webkit.WebView
import me.rerere.rikkahub.data.model.WebAppZoomMode

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
 * - [WebAppZoomMode] chooses overview (Auto / Fit to width) or a fixed [WebView.setInitialScale].
 *
 * Kept separate from the general [me.rerere.rikkahub.ui.pages.webview.WebViewPage] browser so
 * changes here only affect installed web apps.
 */
internal fun WebSettings.applyInstalledWebAppSettings(zoom: WebAppZoomMode = WebAppZoomMode.AUTO) {
    builtInZoomControls = true
    displayZoomControls = false
    useWideViewPort = true
    loadWithOverviewMode = zoom.usesOverviewMode
    mobileUserAgent(userAgentString)?.let { userAgentString = it }
}

/**
 * Applies the forced initial scale for fixed zoom levels. Call on the [WebView] (not
 * [WebSettings]) before the first load. `0` leaves the WebView default for Auto / Fit.
 */
internal fun WebView.applyInstalledWebAppZoom(zoom: WebAppZoomMode = WebAppZoomMode.AUTO) {
    setInitialScale(zoom.initialScalePercent)
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
