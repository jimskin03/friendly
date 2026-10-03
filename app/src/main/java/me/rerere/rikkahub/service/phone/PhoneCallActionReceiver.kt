package me.rerere.rikkahub.service.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

/**
 * Explicit notification actions for incoming / place-call fallbacks.
 * Not exported for implicit broadcasts.
 */
class PhoneCallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val controller = runCatching {
            GlobalContext.get().get<PhoneCallController>()
        }.getOrNull() ?: return
        when (intent?.action) {
            ACTION_OPEN_DIALER -> controller.openDialer()
            ACTION_ANSWER -> controller.attemptAnswerFromNotification()
            ACTION_END -> controller.attemptEndFromNotification()
        }
    }

    companion object {
        const val ACTION_OPEN_DIALER = "me.rerere.rikkahub.action.PHONE_CALL_OPEN_DIALER"
        const val ACTION_ANSWER = "me.rerere.rikkahub.action.PHONE_CALL_ANSWER"
        const val ACTION_END = "me.rerere.rikkahub.action.PHONE_CALL_END"
    }
}
