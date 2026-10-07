package app.friendly.assistant.ui.theme.presets

import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import app.friendly.assistant.R
import app.friendly.assistant.ui.theme.CustomTheme
import app.friendly.assistant.ui.theme.PresetTheme

/**
 * Paid theme pack — circular swatch accents: blue, green, yellow/olive, pink/rose.
 * Color schemes are Material tonal-spot generated from seed hues so they stay
 * consistent with free presets and flow into Compose + WebView/markdown theming.
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

val AzureSoftThemePreset by lazy {
    paidThemeFromSeed(
        id = "azure_soft",
        nameRes = R.string.theme_name_azure_soft,
        primaryArgb = 0xFF4A90D9,
        secondaryArgb = 0xFF6B8FA8,
        tertiaryArgb = 0xFF7B6BB0,
    )
}

val MatchaLeafThemePreset by lazy {
    paidThemeFromSeed(
        id = "matcha_leaf",
        nameRes = R.string.theme_name_matcha_leaf,
        primaryArgb = 0xFF6B9F7A,
        secondaryArgb = 0xFF8FA37A,
        tertiaryArgb = 0xFF5A8F8A,
    )
}

val OliveHoneyThemePreset by lazy {
    paidThemeFromSeed(
        id = "olive_honey",
        nameRes = R.string.theme_name_olive_honey,
        primaryArgb = 0xFFA89B3D,
        secondaryArgb = 0xFF8B7355,
        tertiaryArgb = 0xFF6B7A3D,
    )
}

val RoseQuartzThemePreset by lazy {
    paidThemeFromSeed(
        id = "rose_quartz",
        nameRes = R.string.theme_name_rose_quartz,
        primaryArgb = 0xFFC97B8A,
        secondaryArgb = 0xFFB08A8F,
        tertiaryArgb = 0xFFD4A08A,
    )
}

val PaidThemes by lazy {
    listOf(
        AzureSoftThemePreset,
        MatchaLeafThemePreset,
        OliveHoneyThemePreset,
        RoseQuartzThemePreset,
    )
}
