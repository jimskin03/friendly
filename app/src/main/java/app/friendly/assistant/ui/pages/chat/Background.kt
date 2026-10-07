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
            // Soft veil so chrome cards stay readable
            val veil = when (chrome.styleId) {
                CatHomeStyleId.GLASS_FROST -> Color(0x33000000)
                CatHomeStyleId.COZY_NIGHT -> Color(0x66000000)
                else -> Color.Transparent
            }
            if (veil.alpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(veil)
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
