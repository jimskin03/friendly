package app.friendly.assistant.service.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
            ACTION_AGENT_CALL_END -> {
                runCatching {
                    val manager = GlobalContext.get().get<app.friendly.assistant.service.phone.agentcall.AgentCallManager>()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        manager.endCall()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_DIALER = "app.friendly.assistant.action.PHONE_CALL_OPEN_DIALER"
        const val ACTION_ANSWER = "app.friendly.assistant.action.PHONE_CALL_ANSWER"
        const val ACTION_END = "app.friendly.assistant.action.PHONE_CALL_END"
        const val ACTION_AGENT_CALL_END = "app.friendly.assistant.action.AGENT_CALL_END"
    }
}
