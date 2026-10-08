package app.friendly.assistant.ui.pages.setting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.friendly.assistant.R
import app.friendly.assistant.data.billing.PaidThemeProducts
import app.friendly.assistant.data.billing.PaidThemeStoreState
import app.friendly.assistant.data.billing.ProductOffer
import app.friendly.assistant.data.billing.StoreStatus
import app.friendly.assistant.ui.theme.presets.CozyNightThemePreset
import app.friendly.assistant.ui.theme.presets.PaidThemes
import app.friendly.assistant.ui.theme.presets.SakuraThemePreset

// Render-only previews for design review (debug builds only).

private fun offer(id: String, price: String, micros: Long) =
    ProductOffer(productId = id, formattedPrice = price, priceMicros = micros, currencyCode = "MYR")

private val sampleOffers = PaidThemes.associate {
    PaidThemeProducts.forTheme(it.id) to offer(PaidThemeProducts.forTheme(it.id), "RM 4.99", 4_990_000)
} + (PaidThemeProducts.BUNDLE to offer(PaidThemeProducts.BUNDLE, "RM 14.99", 14_990_000))

@Composable
private fun PaidThemesPage(dark: Boolean, themeId: String, store: PaidThemeStoreState) {
    val scheme = if (dark) {
        CozyNightThemePreset.standardDark.copy(background = Color(0xFF140E0C), surface = Color(0xFF1A1210))
    } else {
        SakuraThemePreset.standardLight
    }
    MaterialTheme(colorScheme = scheme) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_theme_page_paid_themes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceBright),
            ) {
                PaidThemeStoreSection(
                    themeId = themeId,
                    themes = PaidThemes,
                    store = store,
                    onSelectTheme = {},
                    onLockedClick = {},
                    onBuyBundle = {},
                    onRetry = {},
                    onRestore = {},
                )
            }
        }
    }
}

@Preview(name = "Play: locked with prices")
@Composable
fun PreviewPaidThemesLocked() {
    PaidThemesPage(dark = false, themeId = "sakura", store = PaidThemeStoreState(status = StoreStatus.Ready, offers = sampleOffers))
}

@Preview(name = "Play: locked with prices, dark")
@Composable
fun PreviewPaidThemesLockedDark() {
    PaidThemesPage(dark = true, themeId = "sakura", store = PaidThemeStoreState(status = StoreStatus.Ready, offers = sampleOffers))
}

@Preview(name = "Play: one owned, one pending")
@Composable
fun PreviewPaidThemesOwned() {
    PaidThemesPage(
        dark = true,
        themeId = "cozy_night",
        store = PaidThemeStoreState(
            status = StoreStatus.Ready,
            offers = sampleOffers,
            owned = setOf(PaidThemeProducts.forTheme("cozy_night")),
            pending = setOf(PaidThemeProducts.forTheme("glass_frost")),
        ),
    )
}

@Preview(name = "Play: purchase sheet open")
@Composable
fun PreviewPaidThemesPurchasing() {
    PaidThemesPage(
        dark = false,
        themeId = "sakura",
        store = PaidThemeStoreState(
            status = StoreStatus.Ready,
            offers = sampleOffers,
            purchasing = PaidThemeProducts.forTheme("playful_doodle"),
        ),
    )
}

@Preview(name = "No Play billing")
@Composable
fun PreviewPaidThemesUnavailable() {
    PaidThemesPage(dark = false, themeId = "sakura", store = PaidThemeStoreState(status = StoreStatus.Unavailable))
}

@Preview(name = "Play offline")
@Composable
fun PreviewPaidThemesOffline() {
    PaidThemesPage(dark = true, themeId = "sakura", store = PaidThemeStoreState(status = StoreStatus.Offline))
}

@Preview(name = "Nightly: unlocked")
@Composable
fun PreviewPaidThemesNightly() {
    PaidThemesPage(dark = false, themeId = "cute_minimal", store = PaidThemeStoreState(status = StoreStatus.Unlocked))
}
