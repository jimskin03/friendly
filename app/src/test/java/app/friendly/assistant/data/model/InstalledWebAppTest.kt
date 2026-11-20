package app.friendly.assistant.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class InstalledWebAppTest {
    @Test
    fun `https urls are accepted`() {
        assertEquals(
            "https://finance.cryptgregresearch.org/",
            normalizeHttpsStartUrl("  https://finance.cryptgregresearch.org/  "),
        )
    }

    @Test
    fun `http urls are rejected`() {
        assertNull(normalizeHttpsStartUrl("http://finance.cryptgregresearch.org/"))
        assertNull(normalizeHttpsStartUrl("HTTP://example.com"))
    }

    @Test
    fun `non-https schemes and bare hosts are rejected`() {
        assertNull(normalizeHttpsStartUrl("example.com"))
        assertNull(normalizeHttpsStartUrl("javascript:alert(1)"))
        assertNull(normalizeHttpsStartUrl("https://"))
    }

    @Test
    fun `defaults are finance and bookmarks`() {
        val apps = defaultInstalledWebApps()
        assertEquals(
            listOf(
                "https://finance.cryptgregresearch.org/",
                "https://bookmarks.cryptgregresearch.org/",
            ),
            apps.map { it.startUrl },
        )
        assertEquals(listOf("finance", "productivity"), apps.map { it.category })
    }

    @Test
    fun `legacy finance url moves to finance host`() {
        val defaults = defaultInstalledWebApps()
        val finance = defaults.first()
        val bookmarks = defaults[1]
        val other = InstalledWebApp(startUrl = "https://expensetracker.cryptgregresearch.org/")
        val legacy = listOf(
            finance.copy(startUrl = "https://expensetracker.cryptgregresearch.org"),
            bookmarks,
            other,
        )
        val rewritten = rewriteLegacyDefaultFinanceUrl(legacy)
        assertEquals(DEFAULT_FINANCE_START_URL, rewritten[0].startUrl)
        assertEquals(bookmarks, rewritten[1])
        assertEquals(other, rewritten[2])
        assertEquals(rewritten, rewriteLegacyDefaultFinanceUrl(rewritten))
    }

    @Test
    fun `user edited finance url is kept`() {
        val finance = defaultInstalledWebApps().first().copy(startUrl = "https://example.com/ledger")
        val result = rewriteLegacyDefaultFinanceUrl(listOf(finance))
        assertEquals(listOf(finance), result)
    }

    @Test
    fun `startup seeds once and rewrites only the legacy finance url`() {
        assertEquals(
            listOf(DEFAULT_FINANCE_START_URL, "https://bookmarks.cryptgregresearch.org/"),
            installedWebAppsAfterStartup(seeded = false, apps = emptyList())?.map { it.startUrl },
        )
        val custom = defaultInstalledWebApps().first().copy(startUrl = "https://example.com/ledger")
        assertNull(installedWebAppsAfterStartup(seeded = true, apps = listOf(custom)))
        val legacy = custom.copy(startUrl = "https://expensetracker.cryptgregresearch.org/")
        assertEquals(
            DEFAULT_FINANCE_START_URL,
            installedWebAppsAfterStartup(seeded = true, apps = listOf(legacy))?.single()?.startUrl,
        )
    }

    @Test
    fun `home actions stay four slots`() {
        val actions = normalizeHomeActions(listOf(defaultHomeActions().first()))
        assertEquals(4, actions.size)
        assertEquals("Plan my day", actions[0].label)
        assertEquals("Brainstorm", actions[3].label)
        assertEquals(HomeActionKind.PROMPT, actions[0].kind)
    }

    @Test
    fun `fifth favorite is rejected`() {
        val apps = List(4) { InstalledWebApp(id = Uuid.random(), favorite = true) } +
            InstalledWebApp(name = "Extra")
        assertFalse(canFavoriteWebApp(apps, apps.last().id, favorite = true))
        assertTrue(canFavoriteWebApp(apps, apps.first().id, favorite = true))
        assertTrue(canFavoriteWebApp(apps, apps.last().id, favorite = false))
    }

    @Test
    fun `zoomMode defaults to Auto`() {
        assertEquals(WebAppZoomMode.AUTO, InstalledWebApp().zoomMode)
        assertTrue(defaultInstalledWebApps().all { it.zoomMode == WebAppZoomMode.AUTO })
    }

    @Test
    fun `zoom mode scale and overview mapping`() {
        assertEquals(0, WebAppZoomMode.AUTO.initialScalePercent)
        assertEquals(0, WebAppZoomMode.FIT_WIDTH.initialScalePercent)
        assertTrue(WebAppZoomMode.AUTO.usesOverviewMode)
        assertTrue(WebAppZoomMode.FIT_WIDTH.usesOverviewMode)
        assertEquals(75, WebAppZoomMode.PERCENT_75.initialScalePercent)
        assertEquals(90, WebAppZoomMode.PERCENT_90.initialScalePercent)
        assertEquals(100, WebAppZoomMode.PERCENT_100.initialScalePercent)
        assertEquals(110, WebAppZoomMode.PERCENT_110.initialScalePercent)
        assertEquals(125, WebAppZoomMode.PERCENT_125.initialScalePercent)
        assertFalse(WebAppZoomMode.PERCENT_100.usesOverviewMode)
    }
}
