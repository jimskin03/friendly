package me.rerere.rikkahub.service.phone.agentcall

import me.rerere.rikkahub.data.datastore.AgentCallSetting

interface AgentCallProvider {
    val providerId: String

    suspend fun startCall(
        setting: AgentCallSetting,
        request: AgentCallRequest,
    ): Result<String>

    suspend fun getCallStatus(
        setting: AgentCallSetting,
        callId: String,
    ): Result<AgentCallStatus>

    suspend fun endCall(
        setting: AgentCallSetting,
        callId: String,
    ): Result<Unit>

    suspend fun testConnection(
        setting: AgentCallSetting,
    ): Result<String>
}
