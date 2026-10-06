package app.friendly.assistant.ui.pages.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.friendly.assistant.data.datastore.Settings
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.model.InstalledWebApp
import app.friendly.assistant.data.model.WebAppLaunchMode
import app.friendly.assistant.data.model.WebAppPermissionPolicy
import app.friendly.assistant.data.model.WebAppZoomMode
import app.friendly.assistant.data.model.canFavoriteWebApp
import app.friendly.assistant.data.model.installedWebAppNameFromUrl
import app.friendly.assistant.data.model.normalizeHttpsStartUrl
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

    fun updateCameraPermission(id: Uuid, policy: WebAppPermissionPolicy) {
        updateApp(id) { it.copy(cameraPermission = policy) }
    }

    fun updateMicrophonePermission(id: Uuid, policy: WebAppPermissionPolicy) {
        updateApp(id) { it.copy(microphonePermission = policy) }
    }

    fun updateLocationPermission(id: Uuid, policy: WebAppPermissionPolicy) {
        updateApp(id) { it.copy(locationPermission = policy) }
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
