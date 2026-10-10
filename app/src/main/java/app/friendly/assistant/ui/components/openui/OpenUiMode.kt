package app.friendly.assistant.ui.components.openui

import app.friendly.assistant.data.datastore.ChatUiMode

/**
 * OpenUI draws the transcript. An empty conversation stays on the native main
 * page (home, wallpaper, and input bar).
 */
internal fun shouldPresentOpenUi(mode: ChatUiMode, messageCount: Int): Boolean {
    return mode == ChatUiMode.OPEN_UI && messageCount > 0
}
