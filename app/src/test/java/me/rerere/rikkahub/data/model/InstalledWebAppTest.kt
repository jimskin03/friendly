package me.rerere.rikkahub.data.model

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
}
