package app.friendly.assistant.ui.context

import androidx.compose.runtime.compositionLocalOf
import app.friendly.assistant.ui.hooks.CustomTtsState

val LocalTTSState = compositionLocalOf<CustomTtsState> { error("Not provided yet") }
