package app.friendly.assistant.ui.components.richtext

import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichHtmlTest {
    @Test
    fun `plain markdown html stays on the compose renderer`() {
        val html = """
            <p>Hello <br> there</p>
            <p>Hello <span style="background-color:#ffff00">hi</span></p>
            <div><p>hello</p></div>
            <details><summary>Thought</summary><p>text</p></details>
            <b>hi</b>
        """.trimIndent()
        val styles = layoutStylesFromHtml(html)

        assertTrue(styles.isEmpty())
        Jsoup.parse(html).body().children().forEach { child ->
            assertFalse(child.tagName(), elementNeedsRichHtml(child, styles))
        }
        assertFalse(shouldAutoPreviewHtml(html))
        assertFalse(shouldAutoPreviewHtml("<svg viewBox=\"0 0 10 10\"><circle r=\"4\"/></svg>"))
    }

    @Test
    fun `layout cards are rendered as rich html`() {
        val stylesheet = """
            <style>
            .card { display: flex; border-radius: 16px; border: 1px solid #333; min-height: 120px; }
            </style>
            <div class="card"><div class="title">Quickest</div></div>
            <div><p>plain wrapper</p></div>
        """.trimIndent()
        val styles = layoutStylesFromHtml(stylesheet)
        val body = Jsoup.parse(stylesheet).body()
        val card = body.selectFirst("div.card")!!
        val plain = body.select("div").first { it.className().isEmpty() }

        assertTrue(styles.contains("display: flex"))
        assertTrue(elementNeedsRichHtml(card, styles))
        assertFalse(elementNeedsRichHtml(plain, styles))
        assertTrue(shouldAutoPreviewHtml(stylesheet))

        val inline = Jsoup.parse("""<div style="display:flex;gap:8px"><b>Quickest</b></div>""")
            .body()
            .child(0)
        assertTrue(elementNeedsRichHtml(inline, ""))
        assertTrue(shouldAutoPreviewHtml("""<div style="background:#111;min-height:180px"></div>"""))

        val button = Jsoup.parse("<button>Search on Maps</button>").body().child(0)
        assertTrue(elementNeedsRichHtml(button, ""))
        assertFalse(shouldAutoPreviewHtml("<button>Search on Maps</button>"))
    }

    @Test
    fun `document wrapper keeps the card text and blocks style breakout`() {
        val html = prepareRichHtmlDocument(
            fragment = "<div class=\"card\">Quickest</div>",
            extraStyles = "body{}</style><script>alert(1)</script>",
            onBackground = "red</style><script>alert(2)</script>",
            link = "#8AB4F8",
            fontSizePx = 16,
            dark = true,
        )

        val document = Jsoup.parse(html)
        val scripts = document.select("script").joinToString("\n") { it.data() }

        assertTrue(document.text().contains("Quickest"))
        assertTrue(scripts.contains("RichHtml.reportHeight"))
        assertTrue(scripts.contains("i < images.length"))
        assertTrue(html.contains("max-width: 100%"))
        assertTrue(html.contains("color-scheme: dark"))
        assertFalse(scripts.contains("alert"))
        assertFalse(html.contains("alert(2)"))
    }
}
