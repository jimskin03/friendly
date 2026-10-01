package me.rerere.rikkahub.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import me.rerere.rikkahub.ui.components.ui.GlassSurface
import me.rerere.rikkahub.ui.components.ui.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSurfaceTest {
    @Test
    fun darkVeilKeepsLightTextReadableOverAWhiteBackdrop() {
        val veil = GlassSurface.veil(Color(0xFF1B2023), Color.White, darkTheme = true)
        assertTrue(veil.alpha < 1f)
        assertTrue(veil.alpha <= GlassSurface.MaximumAlpha)
        val effective = veil.compositeOver(Color.White)
        assertTrue(contrastRatio(Color.White, effective) >= GlassSurface.MinimumContrast)
        assertTrue(effective.luminance() < 0.35f)
    }

    @Test
    fun lightVeilKeepsDarkTextReadableOverABlackBackdrop() {
        val content = Color(0xFF1A1C1E)
        val veil = GlassSurface.veil(Color(0xFFF0F4F8), content, darkTheme = false)
        assertTrue(veil.alpha < 1f)
        assertTrue(veil.alpha <= GlassSurface.MaximumAlpha)
        val effective = veil.compositeOver(Color.Black)
        assertTrue(contrastRatio(content, effective) >= GlassSurface.MinimumContrast)
    }

    @Test
    fun brightSurfaceInDarkThemeStaysATranslucentDarkVeil() {
        val veil = GlassSurface.veil(Color(0xFFFFE082), Color.White, darkTheme = true)
        assertTrue(veil.alpha <= GlassSurface.MaximumAlpha)
        val effective = veil.compositeOver(Color.White)
        assertTrue(contrastRatio(Color.White, effective) >= GlassSurface.MinimumContrast)
        assertTrue(effective.luminance() < 0.4f)
    }

    @Test
    fun transparencyPercentSetsVeilAlphaFromSolidToClear() {
        val surface = Color(0xFF1B2023)
        val solid = GlassSurface.veil(surface, Color.White, darkTheme = true, transparencyPercent = 0)
        val mid = GlassSurface.veil(surface, Color.White, darkTheme = true, transparencyPercent = 40)
        val clear = GlassSurface.veil(surface, Color.White, darkTheme = true, transparencyPercent = 100)
        assertEquals(1f, solid.alpha, 0.001f)
        assertEquals(0.6f, mid.alpha, 0.001f)
        assertEquals(0f, clear.alpha, 0.001f)
    }

    @Test
    fun edgeUsesASubtleContentHighlight() {
        val edge = GlassSurface.edge(Color.White)
        assertTrue(edge.alpha > 0.1f)
        assertTrue(edge.alpha < 0.35f)
    }
}
