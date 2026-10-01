package me.rerere.rikkahub.data.ai.mcp

import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.rerere.rikkahub.data.datastore.Settings
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.uuid.Uuid

/**
 * Per-server OAuth attempt identity.
 *
 * [open] cancels the previous job immediately and does not block. It does not make the
 * replacement current while that job is inside [withCurrentCommit]. A per-server [Mutex]
 * serializes activation, close's publication update, and token commit. The mutex is not held
 * across the browser wait, and it is a suspending lock so the UI thread is never blocked.
 * The DataStore edit stays on the caller's cancellable coroutine.
 */
internal class McpOAuthAttemptFence {
    private class Slot {
        val gate = Mutex()
        var next = 0L
        var latest = 0L

        @Volatile
        var current = 0L
        var job: Job? = null
    }

    private val slots = ConcurrentHashMap<Uuid, Slot>()

    private fun slot(configId: Uuid): Slot = slots.computeIfAbsent(configId) { Slot() }

    /**
     * Reserves a new attempt and invalidates the previous one when no commit gate is held.
     * Returns without waiting. The attempt cannot publish until [activate].
     */
    fun open(configId: Uuid): Long = open(configId, attemptOut = null, job = null)

    /**
     * Same as [open], then stores the id in [attemptOut] and starts [job].
     * [attemptOut] is set before [job] runs. A replacement waits in [activate] until the
     * previous job leaves [withCurrentCommit].
     */
    fun open(configId: Uuid, attemptOut: AtomicLong, job: Job): Long =
        open(configId, attemptOut, job as Job?)

    private fun open(configId: Uuid, attemptOut: AtomicLong?, job: Job?): Long {
        val server = slot(configId)
        val previous = synchronized(server) {
            val attempt = ++server.next
            server.latest = attempt
            val previous = server.job
            server.job = job
            if (job != null) {
                job.invokeOnCompletion {
                    synchronized(server) {
                        if (server.job === job) server.job = null
                    }
                }
            }
            attemptOut?.set(attempt)
            attempt to previous
        }
        if (server.gate.tryLock()) {
            try {
                clearIfSuperseded(server)
            } finally {
                server.gate.unlock()
            }
        }
        previous.second?.cancel()
        job?.start()
        return previous.first
    }

    suspend fun activate(configId: Uuid, attempt: Long): Boolean {
        val server = slots[configId] ?: return false
        return server.gate.withLock {
            coroutineContext.ensureActive()
            val latest = synchronized(server) { server.latest }
            if (latest != attempt) {
                clearIfSuperseded(server)
                false
            } else {
                server.current = attempt
                true
            }
        }
    }

    fun close(configId: Uuid) {
        val server = slots[configId] ?: return
        val job = synchronized(server) {
            server.latest = ++server.next
            val job = server.job
            server.job = null
            job
        }
        job?.cancel()
        if (server.gate.tryLock()) {
            try {
                clearIfSuperseded(server)
            } finally {
                server.gate.unlock()
            }
        }
    }

    fun isActive(configId: Uuid): Boolean {
        val server = slots[configId] ?: return false
        return synchronized(server) { server.job?.isActive == true }
    }

    fun isCurrent(configId: Uuid, attempt: Long): Boolean {
        val server = slots[configId] ?: return false
        return server.current == attempt
    }

    /**
     * Runs [block] only while [attempt] is current. The per-server gate is held for the whole
     * block, including a cancellable DataStore edit. A replacement can cancel the job at once,
     * but it cannot activate until [block] returns or throws and the gate is released.
     * This does not make the preference transform atomic with the file write by itself.
     */
    suspend fun <T> withCurrentCommit(configId: Uuid, attempt: Long, block: suspend () -> T): T? {
        val server = slots[configId] ?: return null
        return server.gate.withLock {
            try {
                coroutineContext.ensureActive()
                if (server.current != attempt) return@withLock null
                block()
            } finally {
                clearIfSuperseded(server)
            }
        }
    }

    private fun clearIfSuperseded(server: Slot) {
        val latest = synchronized(server) { server.latest }
        if (server.current != latest) server.current = 0L
    }
}

/**
 * Writes OAuth state for one authorization attempt.
 *
 * Disk persistence and the in-memory patch both run inside [McpOAuthAttemptFence.withCurrentCommit].
 * The edit is cancellable. Callers must not treat the in-edit attempt check as atomic with the
 * file commit; the gate stays held until that edit call returns so a replacement cannot become
 * current or publish in between.
 */
internal class McpOAuthAttemptPublisher(
    private val fence: McpOAuthAttemptFence,
    private val readSettings: () -> Settings,
    private val assignSettings: (Settings) -> Boolean,
    private val persistMcpOAuth: suspend (Uuid, McpOAuthState?, () -> Boolean) -> Boolean,
) {
    suspend fun commit(
        configId: Uuid,
        attempt: Long,
        oauth: McpOAuthState?,
        afterPersist: (() -> Unit)? = null,
    ): Boolean {
        val published = fence.withCurrentCommit(configId, attempt) {
            if (readSettings().init) return@withCurrentCommit false
            val written = persistMcpOAuth(configId, oauth) {
                fence.isCurrent(configId, attempt) && coroutineContext.isActive
            }
            if (!written) return@withCurrentCommit false
            coroutineContext.ensureActive()
            if (!fence.isCurrent(configId, attempt)) return@withCurrentCommit false
            val patched = settingsWithOAuth(readSettings(), configId, oauth) ?: return@withCurrentCommit false
            if (!assignSettings(patched)) return@withCurrentCommit false
            if (afterPersist != null && fence.isCurrent(configId, attempt) && coroutineContext.isActive) {
                afterPersist()
            }
            true
        }
        return published == true
    }

    private fun settingsWithOAuth(current: Settings, configId: Uuid, oauth: McpOAuthState?): Settings? {
        if (current.init) return null
        return current.copy(
            mcpServers = current.mcpServers.map { server ->
                if (server.id != configId) {
                    server
                } else {
                    server.clone(commonOptions = server.commonOptions.copy(oauth = oauth))
                }
            }
        )
    }
}
