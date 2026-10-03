package me.rerere.rikkahub.service.phone

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide signal so voice STT can pause while a cellular call owns the mic
 * without stopping the mini-indicator session.
 *
 * True only for an outgoing place-call claim or TelephonyManager OFFHOOK.
 * Incoming RINGING stays false so the user can still speak "answer" / "ignore".
 */
object PhoneCallSignals {
    const val CALL_AUDIO_HELD = "cellular_call_owns_microphone"

    val micHold = MutableStateFlow(false)

    fun shouldYieldMic(): Boolean = micHold.value
}
