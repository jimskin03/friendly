package me.rerere.rikkahub.ui.pages.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InstalledWebApp
import me.rerere.rikkahub.data.model.WebAppLaunchMode
import me.rerere.rikkahub.data.model.WebAppZoomMode
import me.rerere.rikkahub.data.model.canFavoriteWebApp
import me.rerere.rikkahub.data.model.installedWebAppNameFromUrl
import me.rerere.rikkahub.data.model.normalizeHttpsStartUrl
import kotlin.uuid.Uuid

class AppsVM(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun addApp(name: String, startUrl: String): Boolean {
        val url = normalizeHttpsStartUrl(startUrl) ?: return false
        val resolvedName = name.trim().ifBlank { installedWebAppNameFromUrl(url) }
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    installedWebAppsSeeded = true,
                    installedWebApps = settings.installedWebApps + InstalledWebApp(
                        name = resolvedName,
                        startUrl = url,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
        return true
    }

    fun updateLaunchMode(id: Uuid, mode: WebAppLaunchMode) {
        updateApp(id) { it.copy(launchMode = mode) }
    }

    fun updateZoomMode(id: Uuid, mode: WebAppZoomMode) {
        updateApp(id) { it.copy(zoomMode = mode) }
    }

    fun updateIcon(id: Uuid, iconId: String) {
        updateApp(id) { it.copy(iconId = iconId) }
    }

    /**
     * Returns false when a fifth favorite would be added. The list is left unchanged.
     */
    fun setFavorite(id: Uuid, favorite: Boolean): Boolean {
        val apps = settings.value.installedWebApps
        if (!canFavoriteWebApp(apps, id, favorite)) return false
        viewModelScope.launch {
            settingsStore.update { settings ->
                if (!canFavoriteWebApp(settings.installedWebApps, id, favorite)) {
                    settings
                } else {
                    settings.copy(
                        installedWebAppsSeeded = true,
                        installedWebApps = settings.installedWebApps.map { app ->
                            if (app.id == id) app.copy(favorite = favorite) else app
                        },
                    )
                }
            }
        }
        return true
    }

    suspend fun removeApp(id: Uuid) {
        settingsStore.update { settings ->
            settings.copy(
                installedWebAppsSeeded = true,
                installedWebApps = settings.installedWebApps.filterNot { it.id == id },
            )
        }
    }

    private fun updateApp(id: Uuid, transform: (InstalledWebApp) -> InstalledWebApp) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    installedWebAppsSeeded = true,
                    installedWebApps = settings.installedWebApps.map { app ->
                        if (app.id == id) transform(app) else app
                    },
                )
            }
        }
    }
}
