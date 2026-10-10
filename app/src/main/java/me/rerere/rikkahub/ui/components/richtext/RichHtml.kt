package me.rerere.rikkahub.ui.components.richtext

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import me.rerere.rikkahub.ui.components.webview.WEB_VIEW_BASE_URL
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.utils.toCssHex
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Stylesheet text from the message that Compose cannot apply.
 * Empty when the message has no layout CSS.
 */
internal val LocalRichHtmlStyles = staticCompositionLocalOf { "" }

private val CONTAINER_TAGS = setOf(
    "div", "section", "article", "aside", "header", "footer", "nav", "figure", "main",
)

private val RICH_ROOT_TAGS = setOf(
    "button", "iframe", "video", "canvas", "svg", "form", "object", "embed",
    "input", "select", "textarea",
)

private val INLINE_TAGS = setOf(
    "span", "a", "b", "strong", "em", "i", "code", "mark", "small", "sub", "sup", "u", "s", "br",
)

private val STRUCTURE_CSS = Regex(
    """(?:display\s*:\s*(?:inline-)?(?:flex|grid)|border(?:-[a-z-]+)?\s*:|min-height\s*:|max-height\s*:|box-shadow\s*:|position\s*:\s*(?:absolute|fixed|sticky)|background-image\s*:|flex(?:-direction|-wrap)?\s*:|justify-content\s*:|align-items\s*:|place-items\s*:|gap\s*:|grid-template)""",
    RegexOption.IGNORE_CASE,
)

private val BLOCK_PAINT_CSS = Regex(
    """background(?:-color)?\s*:""",
    RegexOption.IGNORE_CASE,
)

private const val MAX_REPORTED_HEIGHT_PX = 20_000

internal fun layoutStylesFromHtml(html: String): String {
    return runCatching { layoutStylesFrom(Jsoup.parse(html)) }.getOrDefault("")
}

internal fun layoutStylesFrom(document: Document): String {
    val combined = document.select("style").joinToString("\n") { it.data() }
    return if (STRUCTURE_CSS.containsMatchIn(combined)) combined else ""
}

internal fun elementNeedsRichHtml(element: Element, layoutStyles: String): Boolean {
    val tag = element.tagName().lowercase()
    if (tag == "style" || tag == "script") return false
    if (elementOwnsRichHtml(element, layoutStyles)) return true
    return element.children().any { elementNeedsRichHtml(it, layoutStyles) }
}

/**
 * Html and svg fences stay collapsed unless the fence itself is a layout card.
 * A short snippet still uses the manual preview toggle.
 */
internal fun shouldAutoPreviewHtml(html: String): Boolean {
    val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return false
    val styles = layoutStylesFrom(document)
    if (styles.isNotEmpty() && document.body().children().any { elementNeedsRichHtml(it, styles) }) {
        return true
    }
    return document.body().allElements.any { candidate ->
        val tag = candidate.tagName().lowercase()
        tag in CONTAINER_TAGS && hasLayoutCss(candidate)
    }
}

internal fun prepareRichHtmlDocument(
    fragment: String,
    extraStyles: String = "",
    onBackground: String = "#FFFFFF",
    link: String = "#8AB4F8",
    fontSizePx: Int = 16,
    dark: Boolean = false,
): String {
    val document = Jsoup.parse(fragment)
    document.outputSettings().prettyPrint(false)
    val head = document.head()
    if (head.selectFirst("meta[name=viewport]") == null) {
        head.appendElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1")
    }
    val color = cssColor(onBackground, "#FFFFFF")
    val linkColor = cssColor(link, "#8AB4F8")
    val scheme = if (dark) "dark" else "light"
    val size = fontSizePx.coerceIn(10, 32)
    head.appendElement("style").appendText(
        """
        html, body { margin: 0; padding: 0; background: transparent; color: $color; color-scheme: $scheme;
            font: ${size}px/1.45 sans-serif; overflow-wrap: anywhere; height: auto; min-height: 0; }
        * { box-sizing: border-box; }
        img, video, svg, canvas { max-width: 100%; height: auto; }
        a { color: $linkColor; }
        """.trimIndent()
    )
    if (extraStyles.isNotBlank()) {
        head.appendElement("style").appendText(extraStyles)
    }
    document.body().appendElement("script").appendChild(DataNode(HEIGHT_SCRIPT.trim()))
    return document.outerHtml()
}

@Composable
fun RichHtmlBlock(
    html: String,
    modifier: Modifier = Modifier,
    extraStyles: String = "",
) {
    val context = LocalContext.current.applicationContext
    val bridge = remember(context) { RichHtmlBridge(context) }
    var heightPx by remember { mutableIntStateOf(0) }
    bridge.onHeightPx = { heightPx = it }
    val onBackground = MaterialTheme.colorScheme.onBackground.toCssHex()
    val link = MaterialTheme.colorScheme.primary.toCssHex()
    val fontSize = LocalTextStyle.current.fontSize
    val fontPx = if (fontSize.isSpecified) fontSize.value.toInt() else 16
    val dark = LocalDarkMode.current
    val document = remember(html, extraStyles, onBackground, link, fontPx, dark) {
        prepareRichHtmlDocument(
            fragment = html,
            extraStyles = extraStyles,
            onBackground = onBackground,
            link = link,
            fontSizePx = fontPx,
            dark = dark,
        )
    }
    LaunchedEffect(document) {
        heightPx = 0
    }
    val state = rememberWebViewState(
        data = document,
        baseUrl = WEB_VIEW_BASE_URL,
        mimeType = "text/html",
        encoding = "UTF-8",
        interfaces = mapOf("RichHtml" to bridge),
        settings = {
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
        },
    )
    val density = LocalDensity.current
    val height = if (heightPx <= 0) {
        32.dp
    } else {
        with(density) { heightPx.toDp() }.coerceIn(1.dp, 2400.dp)
    }
    WebView(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        onCreated = { view ->
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            view.isVerticalScrollBarEnabled = false
            view.isHorizontalScrollBarEnabled = false
            view.overScrollMode = android.view.View.OVER_SCROLL_NEVER
            view.isNestedScrollingEnabled = false
        },
    )
}

private fun elementOwnsRichHtml(element: Element, layoutStyles: String): Boolean {
    val tag = element.tagName().lowercase()
    if (tag in RICH_ROOT_TAGS) return true
    if (hasLayoutCss(element)) return true
    return stylesheetTargetsElement(layoutStyles, element)
}

private fun hasLayoutCss(element: Element): Boolean {
    val style = element.attr("style")
    if (style.isBlank()) return false
    if (STRUCTURE_CSS.containsMatchIn(style)) return true
    return element.tagName().lowercase() !in INLINE_TAGS && BLOCK_PAINT_CSS.containsMatchIn(style)
}

private fun stylesheetTargetsElement(styles: String, element: Element): Boolean {
    if (styles.isEmpty()) return false
    val tag = element.tagName().lowercase()
    if (tag in CONTAINER_TAGS || tag in RICH_ROOT_TAGS) {
        val tagSelector = Regex("(?:^|[^a-zA-Z0-9_-])${Regex.escape(tag)}(?:[^a-zA-Z0-9_-]|$)")
        if (tagSelector.containsMatchIn(styles)) return true
    }
    val id = element.id()
    if (id.isNotEmpty()) {
        val idSelector = Regex("#${Regex.escape(id)}(?:[^a-zA-Z0-9_-]|$)")
        if (idSelector.containsMatchIn(styles)) return true
    }
    return element.classNames().any { className ->
        Regex("\\.${Regex.escape(className)}(?:[^a-zA-Z0-9_-]|$)").containsMatchIn(styles)
    }
}

private fun cssColor(value: String, fallback: String): String {
    return if (value == "transparent" || value.matches(Regex("#[0-9A-Fa-f]{3,8}"))) value else fallback
}

private class RichHtmlBridge(
    private val context: Context,
) {
    var onHeightPx: (Int) -> Unit = {}
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun reportHeight(px: String?) {
        val value = px?.toFloatOrNull()?.toInt() ?: return
        val coerced = value.coerceIn(0, MAX_REPORTED_HEIGHT_PX)
        main.post { onHeightPx(coerced) }
    }

    @JavascriptInterface
    fun openLink(url: String?) {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val scheme = parsed.scheme?.lowercase() ?: return
        if (scheme !in ALLOWED_SCHEMES) return
        main.post {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, parsed).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private companion object {
        val ALLOWED_SCHEMES = setOf("http", "https", "mailto", "geo", "tel")
    }
}

private const val HEIGHT_SCRIPT = """
(function () {
  function contentHeight() {
    var body = document.body;
    if (!body) return 0;
    var doc = document.documentElement;
    return Math.ceil(Math.max(body.scrollHeight || 0, body.offsetHeight || 0, doc ? doc.scrollHeight : 0));
  }
  function report() {
    if (!window.RichHtml || !RichHtml.reportHeight) return;
    RichHtml.reportHeight(String(contentHeight()));
  }
  function bindImages() {
    var images = document.getElementsByTagName('img');
    for (var i = 0; i < images.length; i++) {
      if (images[i].getAttribute('data-rich-bound') === '1') continue;
      images[i].setAttribute('data-rich-bound', '1');
      images[i].addEventListener('error', function () {
        var label = this.alt || this.getAttribute('src') || '';
        if (label && this.parentNode) {
          var note = document.createElement('div');
          note.textContent = label;
          this.parentNode.replaceChild(note, this);
        }
        report();
      });
      images[i].addEventListener('load', report);
    }
  }
  document.addEventListener('click', function (event) {
    var node = event.target;
    while (node && node.tagName !== 'A') node = node.parentNode;
    if (!node || !node.href) return;
    event.preventDefault();
    if (window.RichHtml && RichHtml.openLink) RichHtml.openLink(node.href);
  }, true);
  window.addEventListener('load', function () {
    bindImages();
    report();
  });
  if (window.ResizeObserver && document.body) new ResizeObserver(report).observe(document.body);
  bindImages();
  setTimeout(report, 32);
  setTimeout(report, 240);
})();
"""
