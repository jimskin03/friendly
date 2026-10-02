package me.rerere.rikkahub.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.VOICE_CAPTURE_NOTIFICATION_CHANNEL_ID

private const val TAG = "VoiceCaptureFgs"

/**
 * Microphone-type foreground service that keeps continuous voice STT alive while the
 * Phone Automation mini indicator session is active and Friendly is backgrounded.
 *
 * Capture itself stays owned by [me.rerere.rikkahub.ui.pages.chat.VoiceSessionController];
 * this service only provides the Android FGS lifetime required for mic access after ON_STOP.
 */
class VoiceCaptureForegroundService : Service() {
    companion object {
        private const val ACTION_ACQUIRE = "me.rerere.rikkahub.action.VOICE_CAPTURE_ACQUIRE"
        private const val ACTION_RELEASE = "me.rerere.rikkahub.action.VOICE_CAPTURE_RELEASE"

        const val NOTIFICATION_ID = 2004

        fun acquire(context: Context): Boolean {
            val intent = Intent(context, VoiceCaptureForegroundService::class.java).apply {
                action = ACTION_ACQUIRE
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.onFailure {
                Log.e(TAG, "Unable to start voice capture foreground service", it)
            }.getOrDefault(false)
        }

        fun release(context: Context) {
            val intent = Intent(context, VoiceCaptureForegroundService::class.java).apply {
                action = ACTION_RELEASE
            }
            runCatching {
                context.startService(intent)
            }.onFailure {
                Log.e(TAG, "Unable to release voice capture foreground service", it)
            }
        }

        /** Idempotent hold/release helper used by voice + mini coordination. */
        fun sync(context: Context, hold: Boolean) {
            if (hold) acquire(context) else release(context)
        }
    }

    private var isForeground = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ACQUIRE -> {
                enterForeground()
            }
            ACTION_RELEASE -> {
                stopService()
            }
            else -> stopService()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.e(TAG, "Voice capture foreground service timed out (type=$fgsType)")
        stopService()
    }

    private fun enterForeground() {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForeground = true
            Log.i(TAG, "Voice capture FGS entered foreground (microphone)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enter voice capture foreground", e)
            stopSelf()
        }
    }

    private fun stopService() {
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForeground = false
        }
        stopSelf()
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, VOICE_CAPTURE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rikkahub)
            .setContentTitle(getString(R.string.notification_voice_capture_title))
            .setContentText(getString(R.string.notification_voice_capture_content))
            .setContentIntent(openAppPendingIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
