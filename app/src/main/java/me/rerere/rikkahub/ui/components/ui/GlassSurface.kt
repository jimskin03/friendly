package me.rerere.rikkahub.ui.components.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import kotlin.math.max
import kotlin.math.min

/**
 * Translucent dark (or light) veil for the chat composer and conversation drawer.
 *
 * The alpha stays below [MaximumAlpha] so the surface stays see-through, and is raised
 * only enough for [contentColor] to keep [MinimumContrast] against the worst-case backdrop
 * (white behind light text, black behind dark text). Blur is applied separately when the
 * display effect is enabled; this color is the legible fallback.
 */
internal object GlassSurface {
    const val MinimumContrast = 4.5f
    const val MaximumAlpha = 0.90f
    const val EdgeAlpha = 0.22f
    private const val DarkBaseMix = 0.28f
    private const val LightBaseMix = 0.40f
    private const val DarkStartAlpha = 0.66f
    private const val LightStartAlpha = 0.74f

    fun veil(
        surface: Color,
        contentColor: Color,
        darkTheme: Boolean,
        minimumContrast: Float = MinimumContrast,
    ): Color {
        val opaqueSurface = surface.copy(alpha = 1f)
        val base = if (darkTheme) {
            lerp(Color.Black, opaqueSurface, DarkBaseMix)
        } else {
            lerp(Color.White, opaqueSurface, LightBaseMix)
        }
        val content = contentColor.copy(alpha = 1f)
        val worstBackdrop = if (content.luminance() >= 0.5f) Color.White else Color.Black
        val start = if (darkTheme) DarkStartAlpha else LightStartAlpha
        var alpha = start
        var candidate = start
        while (candidate <= MaximumAlpha + 0.001f) {
            alpha = min(candidate, MaximumAlpha)
            val effective = base.copy(alpha = alpha).compositeOver(worstBackdrop)
            if (contrastRatio(content, effective) >= minimumContrast) break
            if (alpha >= MaximumAlpha) break
            candidate += 0.02f
        }
        return base.copy(alpha = alpha)
    }

    fun edge(contentColor: Color): Color = contentColor.copy(alpha = EdgeAlpha)

    fun highlight(darkTheme: Boolean): Color =
        Color.White.copy(alpha = if (darkTheme) 0.10f else 0.18f)
}

internal fun contrastRatio(foreground: Color, background: Color): Float {
    val lighter = max(foreground.luminance(), background.luminance())
    val darker = min(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@Composable
@ReadOnlyComposable
fun glassVeilColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return GlassSurface.veil(
        surface = scheme.surface,
        contentColor = scheme.onSurface,
        darkTheme = LocalDarkMode.current,
    )
}

@Composable
@ReadOnlyComposable
fun glassEdgeColor(): Color = GlassSurface.edge(MaterialTheme.colorScheme.onSurface)

/**
 * Backdrop blur or glass for a single surface. No-op when [enabled] is false so the caller can
 * keep the translucent veil without sampling content behind the keyboard or unrelated chrome.
 */
@Composable
fun Modifier.chatBackdrop(
    hazeState: HazeState,
    enabled: Boolean,
    effect: BackgroundEffectType,
    shape: RoundedCornerShape,
    veil: Color,
    backdropBlur: Dp,
): Modifier {
    val highlight = GlassSurface.highlight(LocalDarkMode.current)
    return if (!enabled) {
        this
    } else {
        when (effect) {
            BackgroundEffectType.BLUR -> hazeBlur(
                input = HazeInput.Sources(hazeState),
                style = HazeBlurStyle.Material3(containerColor = veil) {
                    blurRadius(backdropBlur)
                },
            )
            BackgroundEffectType.GLASS -> hazeGlass(
                input = HazeInput.Sources(hazeState),
                style = GlassStyle.Material3(
                    containerColor = veil,
                    tint = highlight,
                ) {
                    shape(shape)
                },
            )
        }
    }
}
