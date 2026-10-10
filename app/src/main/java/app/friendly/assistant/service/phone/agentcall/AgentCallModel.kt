package app.friendly.assistant.service.phone.agentcall

import kotlinx.serialization.Serializable

@Serializable
enum class AgentCallPhase {
    Queued,
    Ringing,
    InProgress,
    Ended,
    Failed,
}

data class AgentCallRequest(
    val toNumber: String,
    val goal: String,
    val context: String = "",
    val language: String? = null,
    val firstMessage: String? = null,
    val discloseAi: Boolean = true,
    val ownerName: String = "",
    val maxDurationSeconds: Int = 600,
)

data class AgentCallStatus(
    val callId: String,
    val phase: AgentCallPhase,
    val toNumber: String = "",
    val endedReason: String? = null,
    val transcript: String? = null,
    val summary: String? = null,
    val durationSeconds: Int = 0,
    val cost: Double? = null,
    val error: String? = null,
)

data class AgentCallSnapshot(
    val callId: String,
    val phase: AgentCallPhase,
    val toNumber: String,
    val durationSeconds: Int = 0,
    val endedReason: String? = null,
)
