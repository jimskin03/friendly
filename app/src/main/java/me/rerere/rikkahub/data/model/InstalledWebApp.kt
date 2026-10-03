package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import java.net.URI
import kotlin.uuid.Uuid

@Serializable
enum class WebAppLaunchMode {
    COMPACT,
    FULLSIZE,
    FULLSCREEN,
}

const val MAX_FAVORITE_WEB_APPS = 4

@Serializable
data class InstalledWebApp(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val startUrl: String = "",
    val createdAt: Long = 0L,
    val description: String = "",
    val category: String = "",
    val launchMode: WebAppLaunchMode = WebAppLaunchMode.COMPACT,
    /** FolderLabel id. Empty until the user assigns one of the folder icons. */
    val iconId: String = "",
    val favorite: Boolean = false,
)

fun canFavoriteWebApp(apps: List<InstalledWebApp>, id: Uuid, favorite: Boolean): Boolean {
    if (!favorite) return true
    val current = apps.firstOrNull { it.id == id } ?: return false
    if (current.favorite) return true
    return apps.count { it.favorite } < MAX_FAVORITE_WEB_APPS
}

private val FINANCE_APP_ID = Uuid.parse("6f0c1a2e-7b14-4d21-9c55-1a0e7b2d4f61")
private val BOOKMARKS_APP_ID = Uuid.parse("2c9e5b71-0a34-4f88-b6d1-7e4a9c0d3b52")

fun defaultInstalledWebApps(createdAt: Long = 0L): List<InstalledWebApp> = listOf(
    InstalledWebApp(
        id = FINANCE_APP_ID,
        name = "CryptGreg Finance",
        startUrl = "https://expensetracker.cryptgregresearch.org/",
        createdAt = createdAt,
        description = "Personal finance",
        category = "finance",
    ),
    InstalledWebApp(
        id = BOOKMARKS_APP_ID,
        name = "Bookmarks",
        startUrl = "https://bookmarks.cryptgregresearch.org/",
        createdAt = createdAt,
        description = "Personal knowledge vault",
        category = "productivity",
    ),
)

/**
 * Accepts only absolute https URLs. http and every other scheme are rejected.
 * Returns the trimmed URL, or null when it cannot be installed.
 */
fun normalizeHttpsStartUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    if (!uri.isAbsolute) return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (uri.host.isNullOrBlank()) return null
    return trimmed
}

fun installedWebAppNameFromUrl(startUrl: String): String {
    val host = runCatching { URI(startUrl).host }.getOrNull().orEmpty().removePrefix("www.")
    return host.ifBlank { startUrl }
}
