package app.friendly.assistant.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import app.friendly.assistant.data.ai.mcp.McpCommonOptions
import app.friendly.assistant.data.ai.mcp.McpOAuthState
import app.friendly.assistant.data.ai.mcp.McpServerConfig
import app.friendly.assistant.utils.JsonInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.uuid.Uuid

class McpOAuthSettingsRaceTest {
    @Test
    fun `stale full settings write keeps oauth committed by the token path`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("mcp-oauth-race").toFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            java.io.File(directory, "settings.preferences_pb")
        }
        try {
            val serverId = Uuid.random()
            val otherId = Uuid.random()
            val beforeToken = settings(
                developerMode = true,
                servers = listOf(
                    server(serverId, "disk-name", oauth = null),
                    server(otherId, "other-disk", oauth = null),
                ),
            )
            SettingsStore.persistSettings(dataStore, beforeToken, McpOAuthWritePolicy.Replace)
            val staleSnapshot = beforeToken.copy(
                developerMode = false,
                mcpServers = listOf(
                    server(serverId, "renamed", oauth = tokens("stale-access", "stale-refresh")),
                    server(otherId, "other-renamed", oauth = tokens("other-stale", "other-stale")),
                ),
            )

            val written = SettingsStore.writeMcpServerOAuth(
                dataStore = dataStore,
                serverId = serverId,
                oauth = tokens("fresh-access", "fresh-refresh"),
            )
            assertTrueWritten(written)
            SettingsStore.persistSettings(
                dataStore,
                staleSnapshot,
                McpOAuthWritePolicy.PreserveStored,
            )

            val stored = readServers(dataStore)
            val preferences = dataStore.data.first()
            assertEquals("fresh-access", stored.first { it.id == serverId }.commonOptions.oauth?.accessToken)
            assertEquals("fresh-refresh", stored.first { it.id == serverId }.commonOptions.oauth?.refreshToken)
            assertEquals("renamed", stored.first { it.id == serverId }.commonOptions.name)
            assertEquals("https://disk.example", (stored.first { it.id == serverId } as McpServerConfig.StreamableHTTPServer).url)
            assertNull(stored.first { it.id == otherId }.commonOptions.oauth)
            assertEquals("other-renamed", stored.first { it.id == otherId }.commonOptions.name)
            assertEquals(false, preferences[SettingsStore.DEVELOPER_MODE])
        } finally {
            scope.coroutineContext[Job]?.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `replace policy still writes snapshot oauth for restore`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("mcp-oauth-replace").toFile()
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            java.io.File(directory, "settings.preferences_pb")
        }
        try {
            val serverId = Uuid.random()
            SettingsStore.persistSettings(
                dataStore,
                settings(developerMode = true, servers = listOf(server(serverId, "disk-name", tokens("disk-access", "disk-refresh")))),
                McpOAuthWritePolicy.Replace,
            )
            SettingsStore.persistSettings(
                dataStore,
                settings(developerMode = false, servers = listOf(server(serverId, "restored", tokens("backup-access", "backup-refresh")))),
                McpOAuthWritePolicy.Replace,
            )
            val stored = readServers(dataStore).single()
            assertEquals("backup-access", stored.commonOptions.oauth?.accessToken)
            assertEquals("restored", stored.commonOptions.name)
            assertEquals(false, dataStore.data.first()[SettingsStore.DEVELOPER_MODE])
        } finally {
            scope.coroutineContext[Job]?.cancel()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `stale in memory settings snapshot racing a token update keeps the token`() {
        val serverId = Uuid.random()
        val flow = MutableStateFlow(
            settings(developerMode = true, servers = listOf(server(serverId, "memory-name", oauth = null))),
        )
        val staleSnapshot = flow.value.copy(
            developerMode = false,
            mcpServers = listOf(server(serverId, "renamed", oauth = null)),
        )
        val tokenEntered = CountDownLatch(1)
        val releaseToken = CountDownLatch(1)
        val token = Thread {
            flow.update { current ->
                tokenEntered.countDown()
                check(releaseToken.await(2, TimeUnit.SECONDS))
                current.copy(
                    mcpServers = current.mcpServers.map { server ->
                        if (server.id != serverId) {
                            server
                        } else {
                            server.clone(commonOptions = server.commonOptions.copy(oauth = tokens("fresh-access", "fresh-refresh")))
                        }
                    }
                )
            }
        }
        val full = Thread {
            flow.update { latest -> staleSnapshot.withLatestMcpOAuth(latest) }
        }
        token.start()
        check(tokenEntered.await(2, TimeUnit.SECONDS))
        full.start()
        releaseToken.countDown()
        token.join(2_000)
        full.join(2_000)
        assertEquals(false, token.isAlive)
        assertEquals(false, full.isAlive)

        val merged = flow.value
        assertEquals("fresh-access", merged.mcpServers.single().commonOptions.oauth?.accessToken)
        assertEquals("fresh-refresh", merged.mcpServers.single().commonOptions.oauth?.refreshToken)
        assertEquals(false, merged.developerMode)
        assertEquals("renamed", merged.mcpServers.single().commonOptions.name)
    }

    private suspend fun readServers(dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>): List<McpServerConfig> {
        val raw = withTimeout(2_000) { dataStore.data.first() }[SettingsStore.MCP_SERVERS]
        return JsonInstant.decodeFromString(requireNotNull(raw))
    }

    private fun assertTrueWritten(written: Boolean) {
        org.junit.Assert.assertTrue(written)
    }

    private fun settings(developerMode: Boolean, servers: List<McpServerConfig>) = Settings(
        developerMode = developerMode,
        mcpServers = servers,
    )

    private fun server(id: Uuid, name: String, oauth: McpOAuthState?) = McpServerConfig.StreamableHTTPServer(
        id = id,
        url = "https://disk.example",
        commonOptions = McpCommonOptions(name = name, oauth = oauth),
    )

    private fun tokens(access: String, refresh: String) = McpOAuthState(
        enabled = true,
        accessToken = access,
        refreshToken = refresh,
    )
}
