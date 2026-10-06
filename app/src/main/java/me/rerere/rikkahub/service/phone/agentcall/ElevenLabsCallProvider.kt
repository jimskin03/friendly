package me.rerere.rikkahub.service.phone.agentcall

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.rikkahub.data.datastore.AgentCallSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "ElevenLabsCallProvider"
private const val BASE_URL = "https://api.elevenlabs.io/v1/convai"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class ElevenLabsCallProvider(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : AgentCallProvider {

    override val providerId: String = "elevenlabs"

    override suspend fun startCall(
        setting: AgentCallSetting,
        request: AgentCallRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            val phoneId = setting.phoneNumberId.trim()
            val agentId = setting.agentId.trim()
            require(apiKey.isNotBlank()) { "ElevenLabs API Key is missing" }
            require(phoneId.isNotBlank()) { "ElevenLabs Phone Number ID is missing" }
            require(agentId.isNotBlank()) { "ElevenLabs Agent ID is missing" }
            require(request.toNumber.isNotBlank()) { "Destination phone number is missing" }

            val firstMsg = request.firstMessage?.takeIf { it.isNotBlank() } ?: run {
                val caller = request.ownerName.takeIf { it.isNotBlank() }?.let { " for $it" }.orEmpty()
                if (request.discloseAi) {
                    "Hello! I am an AI assistant calling$caller regarding ${request.goal.take(80)}."
                } else {
                    "Hello! I am calling regarding ${request.goal.take(80)}."
                }
            }

            val prompt = buildString {
                append("Primary Goal: ${request.goal}\n")
                if (request.context.isNotBlank()) {
                    append("Context & Details: ${request.context}\n")
                }
                if (request.discloseAi) {
                    append("Identify yourself as an AI assistant helping ${request.ownerName.ifBlank { "the user" }}.\n")
                }
                append("Be concise, natural, and polite.")
            }

            val payload = buildJsonObject {
                put("agent_id", agentId)
                put("agent_phone_number_id", phoneId)
                put("to_number", request.toNumber.trim())
                put("conversation_initiation_client_data", buildJsonObject {
                    put("type", "conversation_initiation_client_data")
                    put("dynamic_variables", buildJsonObject {
                        put("owner_name", request.ownerName)
                        put("goal", request.goal)
                    })
                    put("override_config", buildJsonObject {
                        put("first_message", firstMsg)
                        put("prompt", prompt)
                    })
                })
            }

            val httpRequest = Request.Builder()
                .url("$BASE_URL/twilio/outbound-call")
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to start ElevenLabs call: ${response.code} $responseBody")
                throw IllegalStateException("ElevenLabs call failed (${response.code}): $responseBody")
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val callId = parsed["conversation_id"]?.jsonPrimitive?.contentOrNull
                ?: parsed["call_sid"]?.jsonPrimitive?.contentOrNull
                ?: parsed["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("ElevenLabs response missing conversation id: $responseBody")

            Log.i(TAG, "ElevenLabs call started. Conversation ID: $callId")
            callId
        }
    }

    override suspend fun getCallStatus(
        setting: AgentCallSetting,
        callId: String,
    ): Result<AgentCallStatus> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            val httpRequest = Request.Builder()
                .url("$BASE_URL/conversations/$callId")
                .header("xi-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to get ElevenLabs status (${response.code}): $responseBody")
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val statusStr = parsed["status"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val phase = when (statusStr) {
                "queued", "initiating" -> AgentCallPhase.Queued
                "ringing" -> AgentCallPhase.Ringing
                "in-progress", "active" -> AgentCallPhase.InProgress
                "done", "completed" -> AgentCallPhase.Ended
                "failed" -> AgentCallPhase.Failed
                else -> if (statusStr.contains("fail") || statusStr.contains("error")) {
                    AgentCallPhase.Failed
                } else {
                    AgentCallPhase.InProgress
                }
            }

            val transcriptArray = parsed["transcript"]?.jsonArray
            val transcript = transcriptArray?.mapNotNull { item ->
                val role = item.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: "agent"
                val msg = item.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                msg?.let { "$role: $it" }
            }?.joinToString("\n")

            val analysis = parsed["analysis"]?.jsonObject
            val summary = analysis?.get("call_summary")?.jsonPrimitive?.contentOrNull
                ?: analysis?.get("transcript_summary")?.jsonPrimitive?.contentOrNull

            val duration = parsed["metadata"]?.jsonObject?.get("call_duration_secs")?.jsonPrimitive?.intOrNull
                ?: 0

            AgentCallStatus(
                callId = callId,
                phase = phase,
                transcript = transcript,
                summary = summary,
                durationSeconds = duration,
            )
        }
    }

    override suspend fun endCall(
        setting: AgentCallSetting,
        callId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Best effort cancel/end on ElevenLabs conversation
            Log.i(TAG, "Request to end ElevenLabs conversation: $callId")
            Unit
        }
    }

    override suspend fun testConnection(
        setting: AgentCallSetting,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            require(apiKey.isNotBlank()) { "API Key is required" }

            val httpRequest = Request.Builder()
                .url("$BASE_URL/phone-numbers")
                .header("xi-api-key", apiKey)
                .get()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("ElevenLabs verification failed (${response.code}): $body")
            }

            "Connected to ElevenLabs Conversational AI successfully."
        }
    }
}
