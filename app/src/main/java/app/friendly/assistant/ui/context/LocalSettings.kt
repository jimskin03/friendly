package app.friendly.assistant.ui.context

import androidx.compose.runtime.staticCompositionLocalOf
import app.friendly.assistant.data.datastore.Settings

val LocalSettings = staticCompositionLocalOf<Settings> {
    error("No SettingsStore provided")
}
