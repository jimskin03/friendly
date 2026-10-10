package app.friendly.assistant.data.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import app.friendly.assistant.AppScope
import app.friendly.assistant.data.ai.mcp.McpOAuthState
import app.friendly.assistant.data.ai.mcp.McpServerConfig
import app.friendly.assistant.data.ai.prompts.DEFAULT_COMPRESS_PROMPT
import app.friendly.assistant.data.ai.prompts.DEFAULT_SUGGESTION_PROMPT
import app.friendly.assistant.data.ai.prompts.DEFAULT_TITLE_PROMPT
import app.friendly.assistant.data.ai.prompts.LEARNING_MODE_PROMPT
import me.rerere.asr.ASRProviderSetting
import me.rerere.asr.DEFAULT_SYSTEM_ASR_ID
import app.friendly.assistant.data.datastore.migration.PreferenceStoreV1Migration
import app.friendly.assistant.data.datastore.migration.PreferenceStoreV2Migration
import app.friendly.assistant.data.datastore.migration.PreferenceStoreV3Migration
import app.friendly.assistant.data.model.Assistant
import app.friendly.assistant.data.model.HomeAction
import app.friendly.assistant.data.model.InstalledWebApp
import app.friendly.assistant.data.model.defaultHomeActions
import app.friendly.assistant.data.model.defaultInstalledWebApps
import app.friendly.assistant.data.model.normalizeHomeActions
import app.friendly.assistant.data.model.Avatar
import app.friendly.assistant.data.model.InjectionPosition
import app.friendly.assistant.data.model.Lorebook
import app.friendly.assistant.data.model.PromptInjection
import app.friendly.assistant.data.model.QuickMessage
import app.friendly.assistant.data.model.Tag
import app.friendly.assistant.data.sync.s3.S3Config
import app.friendly.assistant.ui.theme.CustomTheme
import app.friendly.assistant.ui.theme.PresetThemes
import app.friendly.assistant.utils.JsonInstant
import app.friendly.assistant.utils.decodeStored
import app.friendly.assistant.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private const val SETTINGS_STORE_NAME = "settings"


private const val READ_MAX_RETRIES = 3

@Volatile
private var settingsDataStore: DataStore<Preferences>? = null


private val Context.settingsStore: DataStore<Preferences>
    get() = settingsDataStore ?: synchronized(SettingsStore::class) {
        settingsDataStore ?: createSettingsDataStore(applicationContext).also { settingsDataStore = it }
    }

private fun createSettingsDataStore(context: Context): DataStore<Preferences> {
    val file = context.preferencesDataStoreFile(SETTINGS_STORE_NAME)
    return PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { exception ->

            Log.e(TAG, "Settings datastore corrupted, resetting", exception)
            runCatching {
                file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"))
            }.onFailure {
                Log.e(TAG, "Failed to backup corrupted settings file", it)
            }
            emptyPreferences()
        },
        migrations = listOf(
            PreferenceStoreV1Migration(),
            PreferenceStoreV2Migration(),
            PreferenceStoreV3Migration()
        ),
        produceFile = { file },
    )
}

class SettingsStore(
    context: Context,
    scope: AppScope,
) : KoinComponent {
    companion object {

        val VERSION = intPreferencesKey("data_version")


        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_ID = stringPreferencesKey("theme_id")
        val CUSTOM_THEMES = stringPreferencesKey("custom_themes")
        val DISPLAY_SETTING = stringPreferencesKey("display_setting")
        val NETWORK_SETTING = stringPreferencesKey("network_setting")
        val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")


        val FAVORITE_MODELS = stringPreferencesKey("favorite_models")
        val SELECT_MODEL = stringPreferencesKey("chat_model")
        val ENABLE_SUGGESTION = booleanPreferencesKey("enable_suggestion")
        val IMAGE_GENERATION_MODEL = stringPreferencesKey("image_generation_model")
        val TITLE_PROMPT = stringPreferencesKey("title_prompt")
        val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")
        val COMPRESS_PROMPT = stringPreferencesKey("compress_prompt")


        val PROVIDERS = stringPreferencesKey("providers")


        val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
        val ASSISTANTS = stringPreferencesKey("assistants")
        val ASSISTANT_TAGS = stringPreferencesKey("assistant_tags")


        val SEARCH_SERVICES = stringPreferencesKey("search_services")
        val SEARCH_COMMON = stringPreferencesKey("search_common")
        val SEARCH_SELECTED = intPreferencesKey("search_selected")

        // MCP
        val MCP_SERVERS = stringPreferencesKey("mcp_servers")

        // WebDAV
        val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")

        // S3
        val S3_CONFIG = stringPreferencesKey("s3_config")

        // TTS
        val TTS_PROVIDERS = stringPreferencesKey("tts_providers")
        val SELECTED_TTS_PROVIDER = stringPreferencesKey("selected_tts_provider")
        val DEFAULT_TTS_PLAYBACK_SPEED = floatPreferencesKey("default_tts_playback_speed")

        // ASR
        val ASR_PROVIDERS = stringPreferencesKey("asr_providers")
        val SELECTED_ASR_PROVIDER = stringPreferencesKey("selected_asr_provider")

        // Web Server


        val MODE_INJECTIONS = stringPreferencesKey("mode_injections")
        val LOREBOOKS = stringPreferencesKey("lorebooks")
        val QUICK_MESSAGES = stringPreferencesKey("quick_messages")
        val INSTALLED_WEB_APPS = stringPreferencesKey("installed_web_apps")
        val INSTALLED_WEB_APPS_SEEDED = booleanPreferencesKey("installed_web_apps_seeded")
        val HOME_ACTIONS = stringPreferencesKey("home_actions")


        val BACKUP_REMINDER_CONFIG = stringPreferencesKey("backup_reminder_config")


        val LAUNCH_COUNT = intPreferencesKey("launch_count")


        val SPONSOR_ALERT_DISMISSED_AT = intPreferencesKey("sponsor_alert_dismissed_at")

        // Uses the same DataStore singleton without starting settings flows or requiring Koin.
        internal suspend fun restoreBeforeInitialization(context: Context, settings: Settings) {
            require(!settings.init) { "Cannot restore uninitialized settings" }
            persistSettings(context.settingsStore, settings)
        }

        internal suspend fun persistSettings(
            dataStore: DataStore<Preferences>,
            settings: Settings,
            mcpOAuthPolicy: McpOAuthWritePolicy = McpOAuthWritePolicy.Replace,
        ) {
            dataStore.edit { preferences ->
                preferences[DYNAMIC_COLOR] = settings.dynamicColor
                preferences[THEME_ID] = settings.themeId
                preferences[CUSTOM_THEMES] = JsonInstant.encodeToString(settings.customThemes)
                preferences[DEVELOPER_MODE] = settings.developerMode
                preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)
                preferences[NETWORK_SETTING] = JsonInstant.encodeToString(settings.networkSetting)

                preferences[FAVORITE_MODELS] = JsonInstant.encodeToString(settings.favoriteModels)
                preferences[SELECT_MODEL] = settings.chatModelId.toString()
                preferences[ENABLE_SUGGESTION] = settings.enableSuggestion
                preferences[IMAGE_GENERATION_MODEL] = settings.imageGenerationModelId.toString()
                preferences[TITLE_PROMPT] = settings.titlePrompt
                preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt
                preferences[COMPRESS_PROMPT] = settings.compressPrompt

                preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

                preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
                preferences[SELECT_ASSISTANT] = settings.assistantId.toString()
                preferences[ASSISTANT_TAGS] = JsonInstant.encodeToString(settings.assistantTags)

                preferences[SEARCH_SERVICES] = JsonInstant.encodeToString(settings.searchServices)
                preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)
                preferences[SEARCH_SELECTED] = settings.searchServiceSelected.coerceIn(0, (settings.searchServices.size - 1).coerceAtLeast(0))

                preferences[MCP_SERVERS] = JsonInstant.encodeToString(
                    mcpServersForFullWrite(preferences, settings, mcpOAuthPolicy)
                )
                preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
                preferences[S3_CONFIG] = JsonInstant.encodeToString(settings.s3Config)
                preferences[TTS_PROVIDERS] = JsonInstant.encodeToString(settings.ttsProviders)
                settings.selectedTTSProviderId?.let {
                    preferences[SELECTED_TTS_PROVIDER] = it.toString()
                } ?: preferences.remove(SELECTED_TTS_PROVIDER)
                preferences[DEFAULT_TTS_PLAYBACK_SPEED] = settings.defaultTTSPlaybackSpeed.coerceIn(0.5f, 2.0f)
                preferences[ASR_PROVIDERS] = JsonInstant.encodeToString(settings.asrProviders)
                settings.selectedASRProviderId?.let {
                    preferences[SELECTED_ASR_PROVIDER] = it.toString()
                } ?: preferences.remove(SELECTED_ASR_PROVIDER)
                preferences[MODE_INJECTIONS] = JsonInstant.encodeToString(settings.modeInjections)
                preferences[LOREBOOKS] = JsonInstant.encodeToString(settings.lorebooks)
                preferences[QUICK_MESSAGES] = JsonInstant.encodeToString(settings.quickMessages)
                preferences[INSTALLED_WEB_APPS] = JsonInstant.encodeToString(settings.installedWebApps)
                preferences[INSTALLED_WEB_APPS_SEEDED] = settings.installedWebAppsSeeded
                preferences[HOME_ACTIONS] = JsonInstant.encodeToString(settings.homeActions)
                preferences[BACKUP_REMINDER_CONFIG] = JsonInstant.encodeToString(settings.backupReminderConfig)
                preferences[LAUNCH_COUNT] = settings.launchCount
                preferences[SPONSOR_ALERT_DISMISSED_AT] = settings.sponsorAlertDismissedAt
            }
        }

        private fun mcpServersForFullWrite(
            preferences: Preferences,
            settings: Settings,
            mcpOAuthPolicy: McpOAuthWritePolicy,
        ): List<McpServerConfig> {
            if (mcpOAuthPolicy != McpOAuthWritePolicy.PreserveStored) return settings.mcpServers
            val stored = preferences[MCP_SERVERS]?.let { raw ->
                runCatching { JsonInstant.decodeStored<List<McpServerConfig>>(raw) }.getOrNull()
            } ?: return settings.mcpServers
            return settings.mcpServers.preservingStoredMcpOAuth(stored)
        }

        internal suspend fun writeMcpServerOAuth(
            dataStore: DataStore<Preferences>,
            serverId: Uuid,
            oauth: McpOAuthState?,
            shouldWrite: () -> Boolean = { true },
        ): Boolean {
            var written = false
            dataStore.edit { preferences ->
                written = false
                coroutineContext.ensureActive()
                if (!shouldWrite()) return@edit
                val stored = preferences[MCP_SERVERS] ?: return@edit
                val servers = JsonInstant.decodeStored<List<McpServerConfig>>(stored)
                var found = false
                val updated = servers.map { server ->
                    if (server.id != serverId) {
                        server
                    } else {
                        found = true
                        server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                    }
                }
                if (!found) return@edit
                preferences[MCP_SERVERS] = JsonInstant.encodeToString(updated)
                written = true
            }
            return written
        }
    }

    private val dataStore = context.settingsStore


    val settingsFlowRaw = dataStore.data
        .retryWhen { cause, attempt ->
            val shouldRetry = cause is IOException && cause !is CorruptionException && attempt < READ_MAX_RETRIES
            if (shouldRetry) {
                Log.w(TAG, "Failed to read settings, retrying (${attempt + 1}/$READ_MAX_RETRIES)", cause)
                delay((100L shl attempt.toInt()).milliseconds)
            }
            shouldRetry
        }.map { preferences ->
            Settings(
                favoriteModels = preferences[FAVORITE_MODELS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) }
                    ?: DEFAULT_AUTO_MODEL_ID,
                enableSuggestion = preferences[ENABLE_SUGGESTION] != false,
                imageGenerationModelId = preferences[IMAGE_GENERATION_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
                titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
                suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
                compressPrompt = preferences[COMPRESS_PROMPT] ?: DEFAULT_COMPRESS_PROMPT,
                assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
                    ?: DEFAULT_ASSISTANT_ID,
                assistantTags = preferences[ASSISTANT_TAGS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                providers = JsonInstant.decodeStored(preferences[PROVIDERS] ?: "[]"),
                assistants = JsonInstant.decodeStored(preferences[ASSISTANTS] ?: "[]"),
                dynamicColor = preferences[DYNAMIC_COLOR] != false,
                themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
                customThemes = preferences[CUSTOM_THEMES]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                developerMode = preferences[DEVELOPER_MODE] == true,
                displaySetting = JsonInstant.decodeStored(preferences[DISPLAY_SETTING] ?: "{}"),
                networkSetting = JsonInstant.decodeStored(preferences[NETWORK_SETTING] ?: "{}"),
                searchServices = preferences[SEARCH_SERVICES]?.let {
                    runCatching {
                        JsonInstant.decodeStored<List<SearchServiceOptions>>(it)
                    }.getOrNull()
                }?.ifEmpty { null } ?: listOf(SearchServiceOptions.DEFAULT),
                searchCommonOptions = preferences[SEARCH_COMMON]?.let {
                    JsonInstant.decodeStored(it)
                } ?: SearchCommonOptions(),
                searchServiceSelected = preferences[SEARCH_SELECTED] ?: 0,
                mcpServers = preferences[MCP_SERVERS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                webDavConfig = preferences[WEBDAV_CONFIG]?.let {
                    JsonInstant.decodeStored(it)
                } ?: WebDavConfig(),
                s3Config = preferences[S3_CONFIG]?.let {
                    JsonInstant.decodeStored(it)
                } ?: S3Config(),
                ttsProviders = preferences[TTS_PROVIDERS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                selectedTTSProviderId = preferences[SELECTED_TTS_PROVIDER]?.let { Uuid.parse(it) }
                    ?: DEFAULT_SYSTEM_TTS_ID,
                defaultTTSPlaybackSpeed = preferences[DEFAULT_TTS_PLAYBACK_SPEED]?.coerceIn(0.5f, 2.0f) ?: 1.0f,
                asrProviders = preferences[ASR_PROVIDERS]?.let {
                    runCatching {
                        JsonInstant.decodeStored<List<ASRProviderSetting>>(it)
                    }.getOrNull()
                } ?: DEFAULT_ASR_PROVIDERS,
                selectedASRProviderId = preferences[SELECTED_ASR_PROVIDER]?.let {
                    runCatching { Uuid.parse(it) }.getOrNull()
                } ?: DEFAULT_SYSTEM_ASR_ID,
                modeInjections = preferences[MODE_INJECTIONS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                lorebooks = preferences[LOREBOOKS]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                quickMessages = preferences[QUICK_MESSAGES]?.let {
                    JsonInstant.decodeStored(it)
                } ?: emptyList(),
                installedWebApps = preferences[INSTALLED_WEB_APPS]?.let { raw ->
                    runCatching { JsonInstant.decodeStored<List<InstalledWebApp>>(raw) }.getOrDefault(emptyList())
                } ?: emptyList(),
                installedWebAppsSeeded = preferences[INSTALLED_WEB_APPS_SEEDED] == true,
                homeActions = preferences[HOME_ACTIONS]?.let { raw ->
                    runCatching { normalizeHomeActions(JsonInstant.decodeStored<List<HomeAction>>(raw)) }
                        .getOrDefault(defaultHomeActions())
                } ?: defaultHomeActions(),
                backupReminderConfig = preferences[BACKUP_REMINDER_CONFIG]?.let {
                    JsonInstant.decodeStored(it)
                } ?: BackupReminderConfig(),
                launchCount = preferences[LAUNCH_COUNT] ?: 0,
                sponsorAlertDismissedAt = preferences[SPONSOR_ALERT_DISMISSED_AT] ?: 0,
            )
        }
        .map {
            var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }
                .filter { provider -> !provider.builtIn || DEFAULT_PROVIDERS.any { it.id == provider.id } }
                .toMutableList()
            DEFAULT_PROVIDERS.forEach { defaultProvider ->
                if (providers.none { it.id == defaultProvider.id }) {
                    providers.add(defaultProvider.copyProvider())
                }
            }
            providers = providers.map { provider ->
                val migratedProvider = provider.migrateLegacyBuiltInProvider()
                val defaultProvider = DEFAULT_PROVIDERS.find { it.id == migratedProvider.id }
                if (defaultProvider != null) {
                    migratedProvider.copyProvider(
                        builtIn = defaultProvider.builtIn,
                        description = defaultProvider.description,
                        shortDescription = defaultProvider.shortDescription,
                    )
                } else migratedProvider
            }.toMutableList()
            val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
            DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
                if (assistants.none { it.id == defaultAssistant.id }) {
                    assistants.add(defaultAssistant.copy())
                }
            }
            val ttsProviders = it.ttsProviders.ifEmpty { DEFAULT_TTS_PROVIDERS }.toMutableList()
            DEFAULT_TTS_PROVIDERS.forEach { defaultTTSProvider ->
                if (ttsProviders.none { provider -> provider.id == defaultTTSProvider.id }) {
                    ttsProviders.add(defaultTTSProvider.copyProvider())
                }
            }
            it.copy(
                providers = providers,
                assistants = assistants,
                ttsProviders = ttsProviders,
            )
        }
        .map { settings ->

            val validMcpServerIds = settings.mcpServers.map { it.id }.toSet()
            val validModeInjectionIds = settings.modeInjections.map { it.id }.toSet()
            val validLorebookIds = settings.lorebooks.map { it.id }.toSet()
            val validQuickMessageIds = settings.quickMessages.map { it.id }.toSet()
            val rawAsrProviders = settings.asrProviders.ifEmpty { DEFAULT_ASR_PROVIDERS }
            val asrProviders = if (rawAsrProviders.none { it.id == DEFAULT_SYSTEM_ASR_ID }) {
                listOf(ASRProviderSetting.System(id = DEFAULT_SYSTEM_ASR_ID, name = "System ASR")) + rawAsrProviders
            } else {
                rawAsrProviders
            }.distinctBy { it.id }
            settings.copy(
                providers = settings.providers.distinctBy { it.id }.map { provider ->
                    when (provider) {
                        is ProviderSetting.OpenAI -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Google -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )

                        is ProviderSetting.Claude -> provider.copy(
                            models = provider.models.distinctBy { model -> model.id }
                        )
                    }
                },
                assistants = settings.assistants.distinctBy { it.id }.map { assistant ->
                    assistant.copy(

                        mcpServers = assistant.mcpServers.filter { serverId ->
                            serverId in validMcpServerIds
                        }.toSet(),

                        modeInjectionIds = assistant.modeInjectionIds.filter { id ->
                            id in validModeInjectionIds
                        }.toSet(),

                        lorebookIds = assistant.lorebookIds.filter { id ->
                            id in validLorebookIds
                        }.toSet(),

                        quickMessageIds = assistant.quickMessageIds.filter { id ->
                            id in validQuickMessageIds
                        }.toSet()
                    )
                },
                ttsProviders = settings.ttsProviders.distinctBy { it.id },
                asrProviders = asrProviders,
                selectedASRProviderId = settings.selectedASRProviderId
                    ?.takeIf { id -> asrProviders.any { provider -> provider.id == id } }
                    ?: asrProviders.firstOrNull()?.id
                    ?: DEFAULT_SYSTEM_ASR_ID,
                favoriteModels = settings.favoriteModels.filter { uuid ->
                    settings.providers.flatMap { it.models }.any { it.id == uuid }
                },
                modeInjections = settings.modeInjections.distinctBy { it.id },
                lorebooks = settings.lorebooks.distinctBy { it.id },
                quickMessages = settings.quickMessages.distinctBy { it.id },
                installedWebApps = settings.installedWebApps.distinctBy { it.id },
                homeActions = normalizeHomeActions(settings.homeActions),
            )
        }
        .onEach {
            get<PebbleEngine>().templateCache.invalidateAll()
        }

    val settingsFlow = settingsFlowRaw
        .distinctUntilChanged()
        .toMutableStateFlow(scope, Settings.dummy())

    init {
        scope.launch {
            settingsFlow.first { !it.init }
            update { latest ->
                if (latest.init || latest.installedWebAppsSeeded) {
                    latest
                } else {
                    latest.copy(
                        installedWebApps = latest.installedWebApps.ifEmpty { defaultInstalledWebApps() },
                        installedWebAppsSeeded = true,
                    )
                }
            }
        }
    }

    suspend fun update(settings: Settings) {
        if(settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return
        }
        var persisted = settings
        settingsFlow.update { latest ->
            settings.withLatestMcpOAuth(latest).also { persisted = it }
        }
        persistSettings(dataStore, persisted, McpOAuthWritePolicy.PreserveStored)
    }

    suspend fun update(fn: (Settings) -> Settings) {
        update(fn(settingsFlow.value))
    }

    /**
     * Publishes [settings] to the in-memory flow. OAuth attempt publication calls this
     * while its per-server commit gate is held, after the cancellable DataStore edit returns.
     */
    internal fun assignSettingsInMemory(settings: Settings): Boolean {
        if (settings.init) {
            Log.w(TAG, "Cannot update dummy settings")
            return false
        }
        settingsFlow.value = settings
        return true
    }

    /**
     * Writes OAuth state for one stored MCP server and leaves every other preference alone.
     * [shouldWrite] runs inside the DataStore edit, before this call mutates preferences.
     * That check is not atomic with the file commit. Callers that must drop a superseded
     * attempt keep their per-server commit gate held until this function returns. The edit
     * stays cancellable. Clear, refresh, and authorization token changes use this path so a
     * full [Settings] snapshot cannot replace them.
     *
     * @return true when [serverId] was present in the latest stored list and its OAuth state was written.
     */
    internal suspend fun persistMcpServerOAuth(
        serverId: Uuid,
        oauth: McpOAuthState?,
        shouldWrite: () -> Boolean = { true },
    ): Boolean = writeMcpServerOAuth(dataStore, serverId, oauth, shouldWrite)

    /**
     * Memory and disk update for one server's OAuth state. Other preferences are left alone.
     */
    internal suspend fun updateMcpServerOAuth(serverId: Uuid, oauth: McpOAuthState?): Boolean {
        val remembered = settingsFlow.value.mcpServers.any { it.id == serverId }
        val written = persistMcpServerOAuth(serverId, oauth)
        if (!written && !remembered) return false
        settingsFlow.update { current ->
            if (current.init || current.mcpServers.none { it.id == serverId }) {
                current
            } else {
                current.copy(
                    mcpServers = current.mcpServers.map { server ->
                        if (server.id != serverId) {
                            server
                        } else {
                            server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                        }
                    }
                )
            }
        }
        return true
    }

    suspend fun incrementLaunchCount(): Int {
        var count = 0
        dataStore.edit { preferences ->
            count = (preferences[LAUNCH_COUNT] ?: 0) + 1
            preferences[LAUNCH_COUNT] = count
        }
        return count
    }

    suspend fun updateAssistant(assistantId: Uuid) {
        dataStore.edit { preferences ->
            preferences[SELECT_ASSISTANT] = assistantId.toString()
        }
    }

    suspend fun updateAssistantModel(assistantId: Uuid, modelId: Uuid) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(chatModelId = modelId)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantReasoningLevel(assistantId: Uuid, reasoningLevel: ReasoningLevel) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(reasoningLevel = reasoningLevel)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantWebSearch(assistantId: Uuid, enabled: Boolean) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(enableWebSearch = enabled)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantMcpServers(assistantId: Uuid, mcpServers: Set<Uuid>) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(mcpServers = mcpServers)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    suspend fun updateAssistantInjections(
        assistantId: Uuid,
        modeInjectionIds: Set<Uuid>,
        lorebookIds: Set<Uuid>,
        quickMessageIds: Set<Uuid> = emptySet(),
    ) {
        update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.id == assistantId) {
                        assistant.copy(
                            modeInjectionIds = modeInjectionIds,
                            lorebookIds = lorebookIds,
                            quickMessageIds = quickMessageIds,
                        )
                    } else {
                        assistant
                    }
                }
            )
        }
    }
}

@Serializable
data class Settings(
    @Transient
    val init: Boolean = false,
    val dynamicColor: Boolean = true,
    val themeId: String = PresetThemes[0].id,
    val customThemes: List<CustomTheme> = emptyList(),
    val developerMode: Boolean = false,
    val displaySetting: DisplaySetting = DisplaySetting(),
    val networkSetting: NetworkSetting = NetworkSetting(),
    val favoriteModels: List<Uuid> = emptyList(),
    val chatModelId: Uuid = Uuid.random(),
    val imageGenerationModelId: Uuid = Uuid.random(),
    val titlePrompt: String = DEFAULT_TITLE_PROMPT,
    val enableSuggestion: Boolean = true,
    val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
    val compressPrompt: String = DEFAULT_COMPRESS_PROMPT,
    val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
    val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
    val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
    val assistantTags: List<Tag> = emptyList(),
    val searchServices: List<SearchServiceOptions> = listOf(SearchServiceOptions.DEFAULT),
    val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
    val searchServiceSelected: Int = 0,
    val mcpServers: List<McpServerConfig> = emptyList(),
    val webDavConfig: WebDavConfig = WebDavConfig(),
    val s3Config: S3Config = S3Config(),
    val ttsProviders: List<TTSProviderSetting> = DEFAULT_TTS_PROVIDERS,
    val selectedTTSProviderId: Uuid = DEFAULT_SYSTEM_TTS_ID,
    val defaultTTSPlaybackSpeed: Float = 1.0f,
    val asrProviders: List<ASRProviderSetting> = DEFAULT_ASR_PROVIDERS,
    val selectedASRProviderId: Uuid? = DEFAULT_SYSTEM_ASR_ID,
    val modeInjections: List<PromptInjection.ModeInjection> = DEFAULT_MODE_INJECTIONS,
    val lorebooks: List<Lorebook> = emptyList(),
    val quickMessages: List<QuickMessage> = emptyList(),
    val installedWebApps: List<InstalledWebApp> = emptyList(),
    val installedWebAppsSeeded: Boolean = false,
    val homeActions: List<HomeAction> = defaultHomeActions(),
    val backupReminderConfig: BackupReminderConfig = BackupReminderConfig(),
    val launchCount: Int = 0,
    val sponsorAlertDismissedAt: Int = 0,
) {
    companion object {

        fun dummy() = Settings(init = true)
    }
}

internal enum class McpOAuthWritePolicy {
    /** Backup restore writes the snapshot's OAuth state. */
    Replace,

    /** Generic settings writes keep the latest stored OAuth state for servers that remain. */
    PreserveStored,
}

internal fun Settings.withLatestMcpOAuth(latest: Settings): Settings {
    if (latest.init) return this
    return copy(mcpServers = mcpServers.preservingStoredMcpOAuth(latest.mcpServers))
}

internal fun List<McpServerConfig>.preservingStoredMcpOAuth(
    stored: List<McpServerConfig>,
): List<McpServerConfig> {
    if (stored.isEmpty()) return this
    val storedById = stored.associateBy { it.id }
    return map { incoming ->
        val previous = storedById[incoming.id] ?: return@map incoming
        val storedOAuth = previous.commonOptions.oauth
        if (incoming.commonOptions.oauth == storedOAuth) {
            incoming
        } else {
            incoming.clone(commonOptions = incoming.commonOptions.copy(oauth = storedOAuth))
        }
    }
}

@Serializable
data class NetworkSetting(
    val userAgent: String = "",
    val proxyUrl: String = "",
    val proxyUsername: String = "",
    val proxyPassword: String = "",
    val enableAutoRetry: Boolean = true,
    /** Friendly Host Control API base URL (e.g. http://100.x.y.z:8787). */
    val desktopControlBaseUrl: String = "http://10.0.2.2:8787",
    /** Bearer token matching host API_TOKEN. Never commit real tokens. */
    val desktopControlApiToken: String = "",
)

@Serializable
enum class ChatFontFamily {
    @SerialName("default")
    DEFAULT,
    @SerialName("serif")
    SERIF,
    @SerialName("monospace")
    MONOSPACE,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class PhoneAutomationWindowMode {
    @SerialName("off")
    OFF,

    @SerialName("split")
    SPLIT,

    @SerialName("popup")
    POPUP,
}

@Serializable
enum class BackgroundEffectType {
    @SerialName("blur")
    BLUR,

    @SerialName("glass")
    GLASS,
}

@Serializable
enum class ChatUiMode {
    @SerialName("openui")
    OPEN_UI,

    @SerialName("native")
    NATIVE,
}

@Serializable
data class DisplaySetting(
    val userAvatar: Avatar = Avatar.Dummy,
    val userNickname: String = "",
    val useAppIconStyleLoadingIndicator: Boolean = true,
    val showUserAvatar: Boolean = true,
    val showAssistantBubble: Boolean = false,
    val bubbleOpacity: Float = 1.0f,
    val showModelIcon: Boolean = true,
    val showModelName: Boolean = true,
    val showDateTimeInMessage: Boolean = false,
    val showTokenUsage: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCloseThinking: Boolean = true,
    val updateCheckDisabledUntilEpochMillis: Long = 0L,
    val showMessageJumper: Boolean = true,
    val messageJumperOnLeft: Boolean = false,
    val fontSizeRatio: Float = 1.0f,
    val enableMessageGenerationHapticEffect: Boolean = false,
    val skipCropImage: Boolean = true,
    val enableNotificationOnMessageGeneration: Boolean = false,
    val enableLiveUpdateNotification: Boolean = false,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showLineNumbers: Boolean = false,
    val ttsOnlyReadQuoted: Boolean = false,
    val ttsOnlyReadOutsideBrackets: Boolean = false,
    val autoPlayTTSAfterGeneration: Boolean = false,
    val replyWithVoice: Boolean = true,
    val pasteLongTextAsFile: Boolean = false,
    val pasteLongTextThreshold: Int = 1000,
    val sendOnEnter: Boolean = false,
    val enableAutoScroll: Boolean = true,
    val enableLatexRendering: Boolean = true,
    val enableBlurEffect: Boolean = true,
    val backgroundEffectType: BackgroundEffectType = BackgroundEffectType.GLASS,
    val chatSurfaceTransparency: Int = 30,
    val chatFontFamily: ChatFontFamily = ChatFontFamily.DEFAULT,
    val chatCustomFontPath: String = "",
    val chatCustomFontName: String = "",
    // Preserve the full native chat until the experimental renderer reaches feature parity.
    val chatUiMode: ChatUiMode = ChatUiMode.NATIVE,
    val enableVolumeKeyScroll: Boolean = false,
    val volumeKeyScrollRatio: Float = 1.0f,
    /** Show ongoing notification (+ optional overlay bubble) while Phone Automation is active and Friendly is backgrounded. Default ON. */
    val enablePhoneAutomationMiniIndicator: Boolean = true,
    /** Cellular place/end/state tools are always on. Kept so older settings still decode. Does not include WhatsApp. */
    val enablePhoneCallAccess: Boolean = true,
    /**
     * When on, a ringing cellular call tries Telecom accept and an accessibility Answer tap.
     * Not reliable on Android 10+ unless this app is the default dialer. Default off.
     */
    val enablePhoneCallAutoAnswerAttempt: Boolean = false,
    /**
     * Legacy toggle kept for backward compatibility with existing persisted settings.
     */
    val enableAgentSpeakOnCalls: Boolean = false,
    /**
     * Autonomous AI phone calling configuration (Vapi / ElevenLabs cloud telephony).
     */
    val agentCallSetting: AgentCallSetting = AgentCallSetting(),
    /**
     * Experimental. Off keeps a normal full-screen launch.
     * Split or Popup asks the system to open Phone Automation app launches
     * in that window mode, and the mini indicator stays off until this is Off again.
     */
    val phoneAutomationWindowMode: PhoneAutomationWindowMode = PhoneAutomationWindowMode.OFF,
)

val DisplaySetting.isAgentCallActive: Boolean
    get() = agentCallSetting.enabled || enableAgentSpeakOnCalls

@Serializable
data class AgentCallSetting(
    val enabled: Boolean = false,
    val provider: String = "vapi", // "vapi" or "elevenlabs"
    val apiKey: String = "",
    val phoneNumberId: String = "",
    val agentId: String = "",
    val voiceId: String = "",
    val ownerName: String = "",
    val discloseAi: Boolean = true,
    val maxDurationSec: Int = 600,
)

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val path: String = "rikkahub_backups",
    val items: List<BackupItem> = listOf(
        BackupItem.DATABASE,
        BackupItem.FILES
    ),
) {
    @Serializable
    enum class BackupItem {
        DATABASE,
        FILES,
    }
}

@Serializable
data class BackupReminderConfig(
    val enabled: Boolean = false,
    val intervalDays: Int = 7,
    val lastBackupTime: Long = 0L,
)

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid?, fallback: Uuid? = null): Model? {
    if (uuid == null && fallback == null) return null
    return uuid?.let { this.providers.findModelById(it) }
        ?: fallback?.let { this.providers.findModelById(it) }
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
    this.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == uuid) {
                return model
            }
        }
    }
    return null
}

fun Settings.getCurrentChatModel(): Model? {
    return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
    return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Settings.getAssistantById(id: Uuid): Assistant? {
    return this.assistants.find { it.id == id }
}

fun Settings.getQuickMessagesOfAssistant(assistant: Assistant) =
    quickMessages.filter { it.id in assistant.quickMessageIds }

fun Settings.getSelectedTTSProvider(): TTSProviderSetting? {
    return selectedTTSProviderId?.let { id ->
        ttsProviders.find { it.id == id }
    } ?: ttsProviders.firstOrNull()
}

fun Settings.getSelectedASRProvider(): ASRProviderSetting? {
    val selected = selectedASRProviderId?.let { id ->
        asrProviders.find { it.id == id }
    } ?: asrProviders.firstOrNull()
    if (selected == null || selected.hasCredentials) return selected
    return asrProviders.firstOrNull { it is ASRProviderSetting.System } ?: selected
}

fun Model.findProvider(providers: List<ProviderSetting>, checkOverwrite: Boolean = true): ProviderSetting? {
    val provider = findModelProviderFromList(providers) ?: return null
    val providerOverwrite = this.providerOverwrite
    if (checkOverwrite && providerOverwrite != null) {
        return providerOverwrite.copyProvider(models = emptyList())
    }
    return provider
}

private fun Model.findModelProviderFromList(providers: List<ProviderSetting>): ProviderSetting? {
    providers.forEach { setting ->
        setting.models.forEach { model ->
            if (model.id == this.id) {
                return setting
            }
        }
    }
    return null
}

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
    Assistant(
        id = DEFAULT_ASSISTANT_ID,
        name = "",
        systemPrompt = ""
    ),
    Assistant(
        id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
        name = "",
        systemPrompt = """
            You are a helpful assistant, called {{char}}, based on model {{model_name}}.

            ## Info
            - Date: {{cur_date}}
            - Locale: {{locale}}
            - Timezone: {{timezone}}
            - Device Info: {{device_info}}
            - System Version: {{system_version}}
            - User Nickname: {{user}}

            ## Hint
            - If the user does not specify a language, reply in the user's primary language.
            - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
        """.trimIndent()
    ),
)

val DEFAULT_SYSTEM_TTS_ID = Uuid.parse("026a01a2-c3a0-4fd5-8075-80e03bdef200")
private val DEFAULT_TTS_PROVIDERS = listOf(
    TTSProviderSetting.SystemTTS(
        id = DEFAULT_SYSTEM_TTS_ID,
        name = "",
    ),
    TTSProviderSetting.OpenAI(
        id = Uuid.parse("e36b22ef-ca82-40ab-9e70-60cad861911c"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "tts-1",
        voice = "alloy",
    )
)

val DEFAULT_ASR_PROVIDERS = listOf(
    ASRProviderSetting.System(
        id = DEFAULT_SYSTEM_ASR_ID,
        name = "System ASR",
    )
)

internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

val DEFAULT_MODE_INJECTIONS = listOf(
    PromptInjection.ModeInjection(
        id = Uuid.parse("b87eaf16-f5cd-4ac1-9e4f-b11ae3a61d74"),
        content = LEARNING_MODE_PROMPT,
        position = InjectionPosition.AFTER_SYSTEM_PROMPT,
        name = "Learning Mode"
    )
)
