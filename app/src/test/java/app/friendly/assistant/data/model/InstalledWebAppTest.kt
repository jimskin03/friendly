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
            "https://expensetracker.cryptgregresearch.org/",
            normalizeHttpsStartUrl("  https://expensetracker.cryptgregresearch.org/  "),
        )
    }

    @Test
    fun `http urls are rejected`() {
        assertNull(normalizeHttpsStartUrl("http://expensetracker.cryptgregresearch.org/"))
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
                "https://expensetracker.cryptgregresearch.org/",
                "https://bookmarks.cryptgregresearch.org/",
            ),
            apps.map { it.startUrl },
        )
        assertEquals(listOf("finance", "productivity"), apps.map { it.category })
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
