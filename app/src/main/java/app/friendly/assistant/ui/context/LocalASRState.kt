package app.friendly.assistant.ui.context

import androidx.compose.runtime.compositionLocalOf
import app.friendly.assistant.ui.hooks.CustomAsrState

val LocalASRState = compositionLocalOf<CustomAsrState> { error("Not provided yet") }

