package app.friendly.assistant.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import app.friendly.assistant.R
import app.friendly.assistant.ui.theme.CustomTheme
import app.friendly.assistant.ui.theme.PresetTheme

/**
 * Paid cat home themes — each drives MaterialTheme (app menus + web CSS tokens)
 * and a matching home/dashboard chrome via [app.friendly.assistant.ui.theme.CatHomeStyleId].
 */
private fun paidThemeFromSeed(
    id: String,
    nameRes: Int,
    primaryArgb: Long,
    secondaryArgb: Long? = null,
    tertiaryArgb: Long? = null,
): PresetTheme {
    val seed = CustomTheme(
        id = id,
        name = "",
        primaryColorArgb = primaryArgb,
        secondaryColorArgb = secondaryArgb,
        tertiaryColorArgb = tertiaryArgb,
    )
    return PresetTheme(
        id = id,
        name = { Text(stringResource(id = nameRes)) },
        standardLight = seed.generateColorScheme(dark = false),
        standardDark = seed.generateColorScheme(dark = true),
        paid = true,
    )
}

/** Warm cream + ginger orange — peeking tabby home. */
val CuteMinimalThemePreset by lazy {
    paidThemeFromSeed(
        id = "cute_minimal",
        nameRes = R.string.theme_name_cute_minimal,
        primaryArgb = 0xFFE67A3A,
        secondaryArgb = 0xFFC4A484,
        tertiaryArgb = 0xFFD4A574,
    )
}

/** Immersive night — sleeping tabby photo header. */
val CozyNightThemePreset by lazy {
    paidThemeFromSeed(
        id = "cozy_night",
        nameRes = R.string.theme_name_cozy_night,
        primaryArgb = 0xFFFF9A4A,
        secondaryArgb = 0xFF8B5A3C,
        tertiaryArgb = 0xFFC4784A,
    )
}

/** Hand-drawn doodle — cartoon tabby + pastel accents. */
val PlayfulDoodleThemePreset by lazy {
    paidThemeFromSeed(
        id = "playful_doodle",
        nameRes = R.string.theme_name_playful_doodle,
        primaryArgb = 0xFFE67A3A,
        secondaryArgb = 0xFF7EB8A0,
        tertiaryArgb = 0xFFE8A0B0,
    )
}

/** Frosted glass over blurred tabby photo. */
val GlassFrostThemePreset by lazy {
    paidThemeFromSeed(
        id = "glass_frost",
        nameRes = R.string.theme_name_glass_frost,
        primaryArgb = 0xFFFF9A4A,
        secondaryArgb = 0xFFD4A574,
        tertiaryArgb = 0xFFB88860,
    )
}

val PaidThemes by lazy {
    listOf(
        CuteMinimalThemePreset,
        CozyNightThemePreset,
        PlayfulDoodleThemePreset,
        GlassFrostThemePreset,
    )
}
