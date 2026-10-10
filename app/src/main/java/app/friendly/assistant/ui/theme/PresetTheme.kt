package app.friendly.assistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import app.friendly.assistant.ui.theme.presets.AutumnThemePreset
import app.friendly.assistant.ui.theme.presets.BlackThemePreset
import app.friendly.assistant.ui.theme.presets.ClaudeThemePreset
import app.friendly.assistant.ui.theme.presets.MinimalThemePreset
import app.friendly.assistant.ui.theme.presets.OceanThemePreset
import app.friendly.assistant.ui.theme.presets.PaidThemes
import app.friendly.assistant.ui.theme.presets.SakuraThemePreset
import app.friendly.assistant.ui.theme.presets.SpringThemePreset

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
    val paid: Boolean = false,
) {
    fun getColorScheme(dark: Boolean): ColorScheme {
        return if (dark) standardDark else standardLight
    }
}

val PresetThemes by lazy {
    listOf(
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
        MinimalThemePreset,
        ClaudeThemePreset,
    )
}

val AllBuiltinThemes by lazy {
    PresetThemes + PaidThemes
}

fun findPresetTheme(id: String): PresetTheme {
    return AllBuiltinThemes.find { it.id == id } ?: SakuraThemePreset
}

fun findThemeById(id: String, customThemes: List<CustomTheme>): PresetTheme? {
    AllBuiltinThemes.find { it.id == id }?.let { return it }
    val custom = customThemes.find { it.id == id } ?: return null
    return PresetTheme(
        id = custom.id,
        name = { androidx.compose.material3.Text(custom.name) },
        standardLight = custom.generateColorScheme(dark = false),
        standardDark = custom.generateColorScheme(dark = true),
    )
}

fun isPaidThemeId(id: String): Boolean {
    return PaidThemes.any { it.id == id }
}
