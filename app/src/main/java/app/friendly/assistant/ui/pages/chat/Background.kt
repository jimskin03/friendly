package app.friendly.assistant.ui.pages.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import app.friendly.assistant.data.datastore.Settings
import app.friendly.assistant.data.datastore.getCurrentAssistant
import app.friendly.assistant.ui.theme.CatHomeStyleId
import app.friendly.assistant.ui.theme.catHomeStyleId
import app.friendly.assistant.ui.theme.homeChromeFor

@Composable
fun AssistantBackground(setting: Settings, modifier: Modifier) {
    // Photo wallpapers apply on the chat/home canvas only — Settings uses solid MaterialTheme colors.

    val chrome = homeChromeFor(catHomeStyleId(setting.themeId))
    if (chrome.backgroundRes != null) {
        Box(modifier = modifier.fillMaxSize()) {
            Image(
                painter = painterResource(chrome.backgroundRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Gradient scrim: darker only behind the header/top cards and the input bar,
            // nearly clear through the middle so the cat photo stays crisp and visible.
            val scrim = catPhotoScrim(chrome.styleId)
            if (scrim != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrim)
                )
            }
        }
        return
    }

    // Cream wash for light cat themes so menus/home feel warm even without a photo bg
    if (chrome.styleId == CatHomeStyleId.CUTE_MINIMAL || chrome.styleId == CatHomeStyleId.PLAYFUL_DOODLE) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF7F1E6))
        )
        return
    }

    val assistant = setting.getCurrentAssistant()
    if (assistant.useGradientBackground) {
        MeshGradientBackground(modifier = modifier)
        return
    }
    if (assistant.background != null) {
        val backgroundColor = MaterialTheme.colorScheme.background
        val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0f, 1f)
        Box(modifier = modifier) {
            AsyncImage(
                model = assistant.background,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(backgroundOpacity)
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.2f),
                                backgroundColor.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
        }
    }
}

internal fun catPhotoScrim(styleId: CatHomeStyleId): Brush? = when (styleId) {
    CatHomeStyleId.COZY_NIGHT -> Brush.verticalGradient(
        0.00f to Color(0xD90C0705),
        0.20f to Color(0xA60C0705),
        0.42f to Color(0x590C0705),
        0.55f to Color(0x1A0C0705),
        0.74f to Color(0x140C0705),
        0.88f to Color(0x800C0705),
        1.00f to Color(0xCC0C0705),
    )

    CatHomeStyleId.GLASS_FROST -> Brush.verticalGradient(
        0.00f to Color(0x8C1A110B),
        0.20f to Color(0x591A110B),
        0.45f to Color(0x261A110B),
        0.55f to Color(0x0D1A110B),
        0.76f to Color(0x0D1A110B),
        1.00f to Color(0x731A110B),
    )

    else -> null
}
