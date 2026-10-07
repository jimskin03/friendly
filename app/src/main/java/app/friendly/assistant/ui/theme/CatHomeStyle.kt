package app.friendly.assistant.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.friendly.assistant.R
import app.friendly.assistant.ui.context.LocalSettings

/**
 * Visual chrome for the assistant home/dashboard when a paid cat theme is active.
 * MaterialTheme colorScheme still drives the rest of the app + web CSS tokens.
 */
enum class CatHomeStyleId {
    NONE,
    CUTE_MINIMAL,
    COZY_NIGHT,
    PLAYFUL_DOODLE,
    GLASS_FROST,
}

fun catHomeStyleId(themeId: String): CatHomeStyleId = when (themeId) {
    "cute_minimal" -> CatHomeStyleId.CUTE_MINIMAL
    "cozy_night" -> CatHomeStyleId.COZY_NIGHT
    "playful_doodle" -> CatHomeStyleId.PLAYFUL_DOODLE
    "glass_frost" -> CatHomeStyleId.GLASS_FROST
    else -> CatHomeStyleId.NONE
}

@Immutable
data class HomeChrome(
    val styleId: CatHomeStyleId,
    val title: Color,
    val subtitle: Color,
    val muted: Color,
    val card: Color,
    val cardBorder: Color,
    val icon: Color,
    val accent: Color,
    val online: Color = Color(0xFF10B981),
    val settingsBg: Color,
    val settingsBorder: Color,
    val settingsIcon: Color,
    val avatarRing: Color,
    val avatarFill: Color,
    val chevron: Color,
    val time: Color,
    val useSoftShadow: Boolean = false,
    val useGlass: Boolean = false,
    val usePastelActions: Boolean = false,
    val useDoodleBorder: Boolean = false,
    val darkChrome: Boolean = true,
    @DrawableRes val peekRes: Int? = null,
    @DrawableRes val headerImageRes: Int? = null,
    @DrawableRes val backgroundRes: Int? = null,
    val actionPastels: List<Color> = emptyList(),
)

val DefaultHomeChrome = HomeChrome(
    styleId = CatHomeStyleId.NONE,
    title = Color.White,
    subtitle = Color(0xFF94A3B8),
    muted = Color(0xFF94A3B8),
    card = Color(0x221E293B),
    cardBorder = Color(0x1FFFFFFF),
    icon = Color(0xFFCBD5E1),
    accent = Color(0xFFF97316),
    settingsBg = Color(0x281E293B),
    settingsBorder = Color(0x2294A3B8),
    settingsIcon = Color(0xFFCBD5E1),
    avatarRing = Color(0x33FFFFFF),
    avatarFill = Color(0xFFF97316),
    chevron = Color(0xFF64748B),
    time = Color(0xFF64748B),
    darkChrome = true,
)

fun homeChromeFor(styleId: CatHomeStyleId): HomeChrome = when (styleId) {
    CatHomeStyleId.NONE -> DefaultHomeChrome

    CatHomeStyleId.CUTE_MINIMAL -> HomeChrome(
        styleId = styleId,
        title = Color(0xFF2C2118),
        subtitle = Color(0xFF7A6A5A),
        muted = Color(0xFF9A8A78),
        card = Color(0xFFFFFFF8),
        cardBorder = Color(0x33E8A070),
        icon = Color(0xFF6B5344),
        accent = Color(0xFFE67A3A),
        settingsBg = Color(0xFFFFF8F0),
        settingsBorder = Color(0x55E8A070),
        settingsIcon = Color(0xFF3D2E24),
        avatarRing = Color(0x55E67A3A),
        avatarFill = Color(0xFFE67A3A),
        chevron = Color(0xFFB0A090),
        time = Color(0xFFB0A090),
        useSoftShadow = true,
        darkChrome = false,
        peekRes = R.drawable.theme_cat_cute_peek,
        actionPastels = listOf(
            Color(0xFFFFF4E8),
            Color(0xFFF3EEFF),
            Color(0xFFE8F7EF),
            Color(0xFFFFF0F3),
        ),
    )

    CatHomeStyleId.COZY_NIGHT -> HomeChrome(
        styleId = styleId,
        title = Color(0xFFFFF7F0),
        subtitle = Color(0xFFD4C4B4),
        muted = Color(0xFFB8A898),
        card = Color(0xCC1A1210),
        cardBorder = Color(0x44E8A060),
        icon = Color(0xFFFFB070),
        accent = Color(0xFFFF9A4A),
        settingsBg = Color(0x55201814),
        settingsBorder = Color(0x55FFFFFF),
        settingsIcon = Color(0xFFFFF0E4),
        avatarRing = Color(0x66FFFFFF),
        avatarFill = Color(0xFFE67A3A),
        chevron = Color(0xFFA89888),
        time = Color(0xFFA89888),
        darkChrome = true,
        backgroundRes = R.drawable.theme_cat_cozy_bg,
    )

    CatHomeStyleId.PLAYFUL_DOODLE -> HomeChrome(
        styleId = styleId,
        title = Color(0xFF2A2218),
        subtitle = Color(0xFF7A6A58),
        muted = Color(0xFF9A8A78),
        card = Color(0xFFFFFFF8),
        cardBorder = Color(0xFF5C4030),
        icon = Color(0xFF5C4030),
        accent = Color(0xFFE67A3A),
        settingsBg = Color(0xFFFFFBF5),
        settingsBorder = Color(0xFF5C4030),
        settingsIcon = Color(0xFF3D2E24),
        avatarRing = Color(0xFFE67A3A),
        avatarFill = Color(0xFFE67A3A),
        chevron = Color(0xFFE67A3A),
        time = Color(0xFFB0A090),
        useSoftShadow = true,
        usePastelActions = true,
        useDoodleBorder = true,
        darkChrome = false,
        peekRes = R.drawable.theme_cat_playful_peek,
        actionPastels = listOf(
            Color(0xFFD8F3E4), // mint
            Color(0xFFD6EAF8), // sky
            Color(0xFFFFF0C8), // peach/yellow
            Color(0xFFFFD6E0), // pink
        ),
    )

    CatHomeStyleId.GLASS_FROST -> HomeChrome(
        styleId = styleId,
        title = Color(0xFFFFFFF8),
        subtitle = Color(0xEEFFE8D8),
        muted = Color(0xCCFFE0CC),
        card = Color(0x33FFFFFF),
        cardBorder = Color(0x66FFFFFF),
        icon = Color(0xFFFFFFF0),
        accent = Color(0xFFFF9A4A),
        settingsBg = Color(0x28FFFFFF),
        settingsBorder = Color(0x55FFFFFF),
        settingsIcon = Color(0xFFFFFFF0),
        avatarRing = Color(0x88FFFFFF),
        avatarFill = Color(0xFFE67A3A),
        chevron = Color(0xDDFFFFFF),
        time = Color(0xCCFFE8D8),
        useGlass = true,
        darkChrome = true,
        backgroundRes = R.drawable.theme_cat_glass_bg,
    )
}

@Composable
fun rememberHomeChrome(): HomeChrome {
    val settings = LocalSettings.current
    val themeId = if (settings.init) "sakura" else settings.themeId
    return remember(themeId) { homeChromeFor(catHomeStyleId(themeId)) }
}
