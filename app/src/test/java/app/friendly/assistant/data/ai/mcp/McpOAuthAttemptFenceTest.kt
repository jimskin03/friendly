package app.friendly.assistant.data.ai.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import app.friendly.assistant.data.datastore.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.uuid.Uuid

class McpOAuthAttemptFenceTest {
    @Test
    fun `superseded attempt cannot publish tokens or request reconnect`() = runBlocking {
        val harness = Harness()
        val stale = harness.openCurrent()
        harness.fence.open(harness.configId)
        var reconnects = 0

        val published = harness.publisher.commit(
            configId = harness.configId,
            attempt = stale,
            oauth = tokens("stale-access", "stale-refresh"),
        ) { reconnects += 1 }

        assertFalse(harness.fence.isCurrent(harness.configId, stale))
        assertFalse(published)
        assertEquals(0, harness.assigns.get())
        assertEquals(0, harness.persistCalls.get())
        assertEquals(0, reconnects)
        assertNull(harness.accessToken())
        assertNull(harness.diskAccessToken())
        harness.assertUnrelatedPreferencesUnchanged()
    }

    @Test
    fun `new authorization returns immediately and cannot publish before the current commit exits`() = runBlocking {
        val harness = Harness(pauseAssign = true)
        val staleOut = AtomicLong()
        val staleJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val attempt = staleOut.get()
            check(harness.fence.activate(harness.configId, attempt))
            harness.publisher.commit(
                configId = harness.configId,
                attempt = attempt,
                oauth = tokens("stale-access", "stale-refresh"),
            ) { harness.reconnects.incrementAndGet() }
        }
        harness.fence.open(harness.configId, staleOut, staleJob)
        assertTrue(harness.assignEntered.await(2, TimeUnit.SECONDS))
        assertNull(harness.accessToken())
        assertTrue(harness.fence.isCurrent(harness.configId, staleOut.get()))

        val freshOut = AtomicLong()
        val freshActivated = CompletableDeferred<Boolean>()
        val freshJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            freshActivated.complete(harness.fence.activate(harness.configId, freshOut.get()))
        }
        val openStarted = System.nanoTime()
        harness.fence.open(harness.configId, freshOut, freshJob)
        val openWaitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - openStarted)
        assertTrue(openWaitedMs < 300)
        assertFalse(harness.fence.isCurrent(harness.configId, freshOut.get()))
        assertTrue(harness.fence.isCurrent(harness.configId, staleOut.get()))
        assertNull(withTimeoutOrNull(200) { freshActivated.await() })
        assertNull(harness.accessToken())

        harness.releaseAssign.countDown()
        try {
            withTimeout(2_000) {
                staleJob.join()
                freshJob.join()
            }
        } finally {
            staleJob.cancel()
            freshJob.cancel()
        }
        assertEquals(true, freshActivated.getCompleted())
        assertTrue(harness.fence.isCurrent(harness.configId, freshOut.get()))
        assertFalse(harness.fence.isCurrent(harness.configId, staleOut.get()))
        assertEquals("stale-access", harness.accessToken())

        val latePublished = harness.publisher.commit(
            configId = harness.configId,
            attempt = staleOut.get(),
            oauth = tokens("late-access", "late-refresh"),
        ) { harness.reconnects.incrementAndGet() }
        assertFalse(latePublished)
        assertEquals("stale-access", harness.accessToken())
        assertEquals("stale-refresh", harness.refreshToken())
        assertEquals("stale-access", harness.diskAccessToken())
        assertEquals("stale-refresh", harness.diskRefreshToken())
        harness.assertUnrelatedPreferencesUnchanged()
    }

    @Test
    fun `attempt superseded during settings persistence cannot become current or write the stale token`() = runBlocking {
        val harness = Harness(pausePersist = true)
        val staleOut = AtomicLong()
        val staleReconnects = AtomicInteger()
        val staleJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val attempt = staleOut.get()
            check(harness.fence.activate(harness.configId, attempt))
            harness.publisher.commit(
                configId = harness.configId,
                attempt = attempt,
                oauth = tokens("stale-access", "stale-refresh"),
            ) { staleReconnects.incrementAndGet() }
        }
        harness.fence.open(harness.configId, staleOut, staleJob)
        harness.persistEntered.await()
        assertTrue(harness.fence.isCurrent(harness.configId, staleOut.get()))
        assertNull(harness.accessToken())
        assertNull(harness.diskAccessToken())
        assertEquals(0, harness.assigns.get())
        assertEquals(0, staleReconnects.get())
        assertTrue(harness.diskWrites.get().isEmpty())

        val freshOut = AtomicLong()
        val freshReconnects = AtomicInteger()
        val freshResult = AtomicReference<Boolean?>(null)
        val freshActivated = CompletableDeferred<Boolean>()
        val freshJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val attempt = freshOut.get()
            val activated = harness.fence.activate(harness.configId, attempt)
            freshActivated.complete(activated)
            if (!activated) {
                freshResult.set(false)
                return@launch
            }
            freshResult.set(
                harness.publisher.commit(
                    configId = harness.configId,
                    attempt = attempt,
                    oauth = tokens("fresh-access", "fresh-refresh"),
                ) { freshReconnects.incrementAndGet() }
            )
        }
        harness.fence.open(harness.configId, freshOut, freshJob)
        assertFalse(harness.fence.isCurrent(harness.configId, freshOut.get()))
        assertNull(withTimeoutOrNull(200) { freshActivated.await() })
        assertNull(harness.diskAccessToken())
        assertEquals(0, freshReconnects.get())
        assertTrue(harness.diskWrites.get().isEmpty())

        try {
            withTimeout(2_000) {
                freshJob.join()
                staleJob.join()
            }
        } finally {
            freshJob.cancel()
            staleJob.cancel()
        }

        assertEquals(true, freshActivated.getCompleted())
        assertEquals(true, freshResult.get())
        assertFalse(harness.diskWrites.get().contains("stale-access"))
        assertEquals(listOf("fresh-access"), harness.diskWrites.get())
        assertEquals("fresh-access", harness.accessToken())
        assertEquals("fresh-refresh", harness.refreshToken())
        assertEquals("fresh-access", harness.diskAccessToken())
        assertEquals("fresh-refresh", harness.diskRefreshToken())
        assertEquals(1, harness.assigns.get())
        assertEquals(0, staleReconnects.get())
        assertEquals(1, freshReconnects.get())
        assertTrue(harness.fence.isCurrent(harness.configId, freshOut.get()))
        assertFalse(harness.fence.isCurrent(harness.configId, staleOut.get()))
        harness.assertUnrelatedPreferencesUnchanged()
    }

    @Test
    fun `one server commit gate does not block another server`() = runBlocking {
        val harness = Harness(pausePersist = true)
        val otherOut = AtomicLong()
        val otherResult = AtomicReference<Boolean?>(null)
        val staleOut = AtomicLong()
        val staleJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val attempt = staleOut.get()
            check(harness.fence.activate(harness.configId, attempt))
            harness.publisher.commit(
                configId = harness.configId,
                attempt = attempt,
                oauth = tokens("stale-access", "stale-refresh"),
            )
        }
        harness.fence.open(harness.configId, staleOut, staleJob)
        harness.persistEntered.await()

        val otherJob = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            val attempt = otherOut.get()
            check(harness.fence.activate(harness.otherId, attempt))
            otherResult.set(
                harness.publisher.commit(
                    configId = harness.otherId,
                    attempt = attempt,
                    oauth = tokens("other-access", "other-refresh"),
                )
            )
        }
        harness.fence.open(harness.otherId, otherOut, otherJob)
        try {
            withTimeout(2_000) { otherJob.join() }
            assertEquals(true, otherResult.get())
            assertEquals("other-access", harness.diskOther().commonOptions.oauth?.accessToken)
            assertNull(harness.diskAccessToken())
            staleJob.cancel()
            withTimeout(2_000) { staleJob.join() }
            assertFalse(harness.diskWrites.get().contains("stale-access"))
        } finally {
            otherJob.cancel()
            staleJob.cancel()
        }
    }

    @Test
    fun `oauth commit keeps unrelated preferences and patches only the matching server`() = runBlocking {
        val harness = Harness(pausePersist = true)
        val attempt = harness.openCurrent()
        val commit = launch(Dispatchers.Default) {
            harness.publisher.commit(
                configId = harness.configId,
                attempt = attempt,
                oauth = tokens("access", "refresh"),
            ) { harness.reconnects.incrementAndGet() }
        }
        harness.persistEntered.await()
        assertNull(harness.accessToken())
        harness.memory.set(harness.memory.get().copy(developerMode = true))
        harness.diskMcp.set(
            harness.diskMcp.get().map { server ->
                if (server.id != harness.otherId) {
                    server
                } else {
                    server.clone(commonOptions = server.commonOptions.copy(name = "renamed-during-pause"))
                }
            }
        )

        harness.releasePersist.complete(Unit)
        commit.join()

        assertEquals("access", harness.accessToken())
        assertEquals("refresh", harness.refreshToken())
        assertEquals("access", harness.diskAccessToken())
        assertEquals("refresh", harness.diskRefreshToken())
        assertTrue(harness.memory.get().developerMode)
        assertEquals(true, harness.diskDeveloperMode.get())
        assertEquals("memory-name", harness.memory.get().mcpServers.single().commonOptions.name)
        assertEquals("disk-name", harness.diskServer().commonOptions.name)
        assertEquals("https://disk.example", harness.diskServer().serverUrl)
        assertEquals("renamed-during-pause", harness.diskOther().commonOptions.name)
        assertNull(harness.diskOther().commonOptions.oauth)
        assertEquals(1, harness.reconnects.get())
        assertEquals(1, harness.memory.get().mcpServers.size)
        assertEquals(2, harness.diskMcp.get().size)
    }

    @Test
    fun `oauth commit does not create a missing disk server from the memory snapshot`() = runBlocking {
        val harness = Harness(targetOnDisk = false)
        val attempt = harness.openCurrent()

        val published = harness.publisher.commit(
            configId = harness.configId,
            attempt = attempt,
            oauth = tokens("access", "refresh"),
        ) { harness.reconnects.incrementAndGet() }

        assertFalse(published)
        assertEquals(0, harness.assigns.get())
        assertEquals(0, harness.reconnects.get())
        assertNull(harness.accessToken())
        assertEquals(1, harness.diskMcp.get().size)
        assertEquals(harness.otherId, harness.diskMcp.get().single().id)
        harness.assertUnrelatedPreferencesUnchanged()
    }

    private class Harness(
        pauseAssign: Boolean = false,
        pausePersist: Boolean = false,
        targetOnDisk: Boolean = true,
    ) {
        val configId: Uuid = Uuid.random()
        val otherId: Uuid = Uuid.random()
        val fence = McpOAuthAttemptFence()
        val memory = AtomicReference(
            Settings(
                developerMode = false,
                mcpServers = listOf(
                    McpServerConfig.StreamableHTTPServer(
                        id = configId,
                        url = "https://memory.example",
                        commonOptions = McpCommonOptions(name = "memory-name"),
                    )
                )
            )
        )
        val diskMcp = AtomicReference(diskServers(targetOnDisk))
        val diskDeveloperMode = AtomicReference(true)
        val assigns = AtomicInteger()
        val persistCalls = AtomicInteger()
        val reconnects = AtomicInteger()
        val diskWrites = AtomicReference(listOf<String>())
        private val diskLock = Any()
        val assignEntered = CountDownLatch(1)
        val releaseAssign = CountDownLatch(1)
        val persistEntered = CompletableDeferred<Unit>()
        val releasePersist = CompletableDeferred<Unit>()
        private val gatedPersist = AtomicBoolean(false)

        val publisher = McpOAuthAttemptPublisher(
            fence = fence,
            readSettings = { memory.get() },
            assignSettings = { updated ->
                assigns.incrementAndGet()
                if (pauseAssign) {
                    assignEntered.countDown()
                    check(releaseAssign.await(2, TimeUnit.SECONDS))
                }
                memory.set(updated)
                true
            },
            persistMcpOAuth = persist@{ id, oauth, shouldWrite ->
                persistCalls.incrementAndGet()
                if (!shouldWrite()) return@persist false
                if (pausePersist && gatedPersist.compareAndSet(false, true)) {
                    persistEntered.complete(Unit)
                    releasePersist.await()
                }
                coroutineContext.ensureActive()
                if (!shouldWrite()) return@persist false
                synchronized(diskLock) {
                    val servers = diskMcp.get()
                    var found = false
                    val updated = servers.map { server ->
                        if (server.id != id) {
                            server
                        } else {
                            found = true
                            server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                        }
                    }
                    if (!found) return@persist false
                    diskMcp.set(updated)
                    if (oauth?.accessToken != null) {
                        diskWrites.set(diskWrites.get() + oauth.accessToken)
                    }
                }
                true
            },
        )

        suspend fun openCurrent(): Long {
            val attempt = fence.open(configId)
            check(fence.activate(configId, attempt))
            return attempt
        }

        fun accessToken(): String? = memory.get().mcpServers.first().commonOptions.oauth?.accessToken

        fun refreshToken(): String? = memory.get().mcpServers.first().commonOptions.oauth?.refreshToken

        fun diskAccessToken(): String? = diskServer().commonOptions.oauth?.accessToken

        fun diskRefreshToken(): String? = diskServer().commonOptions.oauth?.refreshToken

        fun diskServer(): McpServerConfig = diskMcp.get().first { it.id == configId }

        fun diskOther(): McpServerConfig = diskMcp.get().first { it.id == otherId }

        fun assertUnrelatedPreferencesUnchanged() {
            assertEquals(false, memory.get().developerMode)
            assertEquals(true, diskDeveloperMode.get())
            assertEquals("memory-name", memory.get().mcpServers.single().commonOptions.name)
            assertEquals("https://memory.example", memory.get().mcpServers.single().serverUrl)
            if (diskMcp.get().any { it.id == configId }) {
                assertEquals("disk-name", diskServer().commonOptions.name)
                assertEquals("https://disk.example", diskServer().serverUrl)
            }
            assertEquals("other-disk", diskOther().commonOptions.name)
            assertEquals("https://other.example", diskOther().serverUrl)
        }

        private fun diskServers(includeTarget: Boolean): List<McpServerConfig> {
            val other = McpServerConfig.SseTransportServer(
                id = otherId,
                url = "https://other.example",
                commonOptions = McpCommonOptions(name = "other-disk"),
            )
            if (!includeTarget) return listOf(other)
            return listOf(
                McpServerConfig.StreamableHTTPServer(
                    id = configId,
                    url = "https://disk.example",
                    commonOptions = McpCommonOptions(name = "disk-name"),
                ),
                other,
            )
        }
    }

    private fun tokens(access: String, refresh: String) = McpOAuthState(
        enabled = true,
        accessToken = access,
        refreshToken = refresh,
    )
}
