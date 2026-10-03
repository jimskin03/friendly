package me.rerere.rikkahub.ui.pages.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.InstalledWebApp
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

    suspend fun removeApp(id: Uuid) {
        settingsStore.update { settings ->
            settings.copy(
                installedWebAppsSeeded = true,
                installedWebApps = settings.installedWebApps.filterNot { it.id == id },
            )
        }
    }
}
