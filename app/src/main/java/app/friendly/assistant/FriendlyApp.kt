package app.friendly.assistant

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.runtime.Composer
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import app.friendly.assistant.data.files.FileFolders
import app.friendly.assistant.data.files.SkillManager
import java.io.File
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.rerere.common.android.appTempFolder
import app.friendly.assistant.di.appModule
import app.friendly.assistant.di.dataSourceModule
import app.friendly.assistant.di.repositoryModule
import app.friendly.assistant.di.viewModelModule
import app.friendly.assistant.data.files.FilesManager
import app.friendly.assistant.data.datastore.SettingsStore
import app.friendly.assistant.data.sync.BackupManager
import app.friendly.assistant.data.sync.RestoreFailedException
import app.friendly.assistant.utils.JsonInstant
import app.friendly.assistant.utils.CrashHandler
import app.friendly.assistant.utils.DatabaseUtil
import app.friendly.assistant.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceManager
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import app.friendly.assistant.data.billing.PaidThemeStore

private const val TAG = "FriendlyApp"

const val CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID = "chat_completed"
const val CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID = "chat_live_update"
const val PHONE_AUTOMATION_NOTIFICATION_CHANNEL_ID = "phone_automation"
const val VOICE_CAPTURE_NOTIFICATION_CHANNEL_ID = "voice_capture"
const val PHONE_CALL_NOTIFICATION_CHANNEL_ID = "phone_call"

class FriendlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Restore files and settings before eager Koin singletons or workers can access them.
        try {
            val restored = runBlocking(Dispatchers.IO) {
                BackupManager.applyPendingRestore(this@FriendlyApp, JsonInstant)
            }
            if (restored) {
                Toast.makeText(this, R.string.backup_page_restore_success, Toast.LENGTH_LONG).show()
            }
        } catch (e: RestoreFailedException) {
            Log.e(TAG, "Backup restore rolled back", e)
            Toast.makeText(this, "Backup restore failed. Original data retained. Please re-import backup.", Toast.LENGTH_LONG).show()
        }
        startKoin {
            androidLogger()
            androidContext(this@FriendlyApp)
            workManagerFactory()
            modules(appModule, viewModelModule, dataSourceModule, repositoryModule)
        }
        this.createNotificationChannel()

        // set cursor window size to 32MB
        DatabaseUtil.setCursorWindowSize(32 * 1024 * 1024)

        // install crash handler
        CrashHandler.install(this)

        // delete temp files
        deleteTempFiles()

        // cleanup stale tool output files
        cleanupToolOutputs()

        // cleanup workspace temp dirs (proot + rootfs /tmp)
        cleanupWorkspaceTempDirs()

        // check workspace integrity (mark workspaces with missing files as broken after backup restore)
        checkWorkspaceIntegrity()

        // sync upload files to DB
        syncManagedFiles()

        // Extract builtin skills from assets after install/update
        extractBuiltinSkills()

        // Increment launch count
        incrementLaunchCount()

        // Restore paid theme purchases from Google Play (no-op on builds that unlock them)
        get<PaidThemeStore>().start()

        // Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.Auto)
    }

    private fun incrementLaunchCount() {
        get<AppScope>().launch {
            runCatching {
                val count = get<SettingsStore>().incrementLaunchCount()
                Log.i(TAG, "incrementLaunchCount: $count")
            }.onFailure {
                Log.e(TAG, "incrementLaunchCount failed", it)
            }
        }
    }

    private fun cleanupWorkspaceTempDirs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceManager>().cleanupAllTempDirs()
            }.onFailure {
                Log.e(TAG, "cleanupWorkspaceTempDirs failed", it)
            }
        }
    }

    private fun checkWorkspaceIntegrity() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<WorkspaceRepository>().checkIntegrity()
            }.onFailure {
                Log.e(TAG, "checkWorkspaceIntegrity failed", it)
            }
        }
    }

    private fun deleteTempFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            val dir = appTempFolder
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun cleanupToolOutputs() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                val dir = File(filesDir, FileFolders.TOOL_OUTPUTS)
                if (dir.exists()) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    private fun extractBuiltinSkills() {
        get<AppScope>().launch(Dispatchers.IO) {
            get<SkillManager>().ensureBuiltinSkillsExtracted()
        }
    }

    private fun syncManagedFiles() {
        get<AppScope>().launch(Dispatchers.IO) {
            runCatching {
                get<FilesManager>().syncFolder()
            }.onFailure {
                Log.e(TAG, "syncManagedFiles failed", it)
            }
        }
    }

    private fun createNotificationChannel() {
        val notificationManager = NotificationManagerCompat.from(this)
        val chatCompletedChannel = NotificationChannelCompat
            .Builder(
                CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_HIGH
            )
            .setName(getString(R.string.notification_channel_chat_completed))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(chatCompletedChannel)

        val chatLiveUpdateChannel = NotificationChannelCompat
            .Builder(
                CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW
            )
            .setName(getString(R.string.notification_channel_chat_live_update))
            .setVibrationEnabled(false)
            .build()
        notificationManager.createNotificationChannel(chatLiveUpdateChannel)

        val phoneAutomationChannel = NotificationChannelCompat
            .Builder(PHONE_AUTOMATION_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_phone_automation))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(phoneAutomationChannel)

        val voiceCaptureChannel = NotificationChannelCompat
            .Builder(VOICE_CAPTURE_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(getString(R.string.notification_channel_voice_capture))
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        notificationManager.createNotificationChannel(voiceCaptureChannel)

        val phoneCallChannel = NotificationChannelCompat
            .Builder(PHONE_CALL_NOTIFICATION_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName(getString(R.string.notification_channel_phone_call))
            .setVibrationEnabled(true)
            .build()
        notificationManager.createNotificationChannel(phoneCallChannel)
    }

    override fun onTerminate() {
        super.onTerminate()
        get<AppScope>().cancel()
    }
}

class AppScope : CoroutineScope by CoroutineScope(
    SupervisorJob()
        + Dispatchers.Main
        + CoroutineName("AppScope")
        + CoroutineExceptionHandler { _, e ->
        Log.e(TAG, "AppScope exception", e)
    }
)

@Deprecated("Use FriendlyApp instead")
typealias RikkaHubApp = FriendlyApp

