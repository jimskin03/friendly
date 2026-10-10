package app.friendly.assistant.service.phone

import android.content.Context
import android.os.Build
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Isolated so [TelephonyCallback] (API 31) is not resolved on older devices
 * until call-state listening actually starts.
 */
@RequiresApi(Build.VERSION_CODES.S)
internal object PhoneCallTelephony31 {
    fun register(
        telephony: TelephonyManager,
        context: Context,
        onState: (Int) -> Unit,
    ): TelephonyCallback {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                onState(state)
            }
        }
        telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
        return callback
    }

    fun unregister(telephony: TelephonyManager, callback: Any?) {
        val typed = callback as? TelephonyCallback ?: return
        telephony.unregisterTelephonyCallback(typed)
    }
}
