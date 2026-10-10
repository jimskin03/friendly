package app.friendly.assistant.ui.components.openui

/**
 * The web chat is the only transcript UI. Only an empty, non-folder conversation shows
 * the native home page (dashboard, folders and the home composer).
 */
internal fun shouldPresentOpenUi(messageCount: Int, isFolderChat: Boolean): Boolean {
    return messageCount > 0 || isFolderChat
}
