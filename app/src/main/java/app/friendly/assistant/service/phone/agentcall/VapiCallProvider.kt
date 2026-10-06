package app.friendly.assistant.service.phone.agentcall

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import app.friendly.assistant.data.datastore.AgentCallSetting
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "VapiCallProvider"
private const val BASE_URL = "https://api.vapi.ai"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class VapiCallProvider(
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : AgentCallProvider {

    override val providerId: String = "vapi"

    override suspend fun startCall(
        setting: AgentCallSetting,
        request: AgentCallRequest,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            val phoneId = setting.phoneNumberId.trim()
            require(apiKey.isNotBlank()) { "Vapi API Key is missing" }
            require(phoneId.isNotBlank()) { "Vapi Phone Number ID is missing" }
            require(request.toNumber.isNotBlank()) { "Destination phone number is missing" }

            val payload = buildJsonObject {
                put("phoneNumberId", phoneId)
                put("customer", buildJsonObject {
                    put("number", request.toNumber.trim())
                })
                put("maxDurationSeconds", request.maxDurationSeconds)

                if (setting.agentId.isNotBlank()) {
                    put("assistantId", setting.agentId.trim())
                    put("assistantOverrides", buildJsonObject {
                        put("variableValues", buildJsonObject {
                            put("goal", request.goal)
                            put("context", request.context)
                            if (request.ownerName.isNotBlank()) {
                                put("owner_name", request.ownerName)
                            }
                        })
                    })
                } else {
                    put("assistant", buildJsonObject {
                        val systemPrompt = buildString {
                            append("You are an autonomous AI phone agent calling on behalf of ${request.ownerName.ifBlank { "your user" }}.\n")
                            append("Primary Goal: ${request.goal}\n")
                            if (request.context.isNotBlank()) {
                                append("Context & Details: ${request.context}\n")
                            }
                            if (request.discloseAi) {
                                append("Identify yourself politely as an AI assistant calling on behalf of ${request.ownerName.ifBlank { "the user" }}.\n")
                            }
                            append("Be concise, polite, natural, and efficient. Once the goal is completed or the other party concludes the conversation, say goodbye politely and use the endCall tool to hang up.")
                        }

                        val firstMsg = request.firstMessage?.takeIf { it.isNotBlank() } ?: run {
                            val caller = request.ownerName.takeIf { it.isNotBlank() }?.let { " for $it" }.orEmpty()
                            if (request.discloseAi) {
                                "Hello! I am an AI assistant calling$caller. I'm calling regarding: ${request.goal.take(90)}."
                            } else {
                                "Hello! I am calling regarding: ${request.goal.take(90)}."
                            }
                        }

                        put("firstMessage", firstMsg)
                        put("model", buildJsonObject {
                            put("provider", "openai")
                            put("model", "gpt-4o-mini")
                            putJsonArray("messages") {
                                addJsonObject {
                                    put("role", "system")
                                    put("content", systemPrompt)
                                }
                            }
                            putJsonArray("tools") {
                                addJsonObject {
                                    put("type", "endCall")
                                }
                            }
                        })

                        if (setting.voiceId.isNotBlank()) {
                            put("voice", buildJsonObject {
                                put("provider", "11labs")
                                put("voiceId", setting.voiceId.trim())
                            })
                        }

                        put("analysisPlan", buildJsonObject {
                            put(
                                "summaryPrompt",
                                "Summarize this phone call clearly. Include what was agreed upon, key dates/times, and whether the goal was achieved."
                            )
                        })
                    })
                }
            }

            val httpRequest = Request.Builder()
                .url("$BASE_URL/call")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to start Vapi call: ${response.code} $responseBody")
                throw IllegalStateException("Vapi call failed (${response.code}): $responseBody")
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val callId = parsed["id"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalStateException("Vapi response missing call id: $responseBody")

            Log.i(TAG, "Vapi call started successfully. Call ID: $callId")
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
                .url("$BASE_URL/call/$callId")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to get Vapi call status (${response.code}): $responseBody")
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val statusStr = parsed["status"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
            val phase = when (statusStr) {
                "queued" -> AgentCallPhase.Queued
                "ringing" -> AgentCallPhase.Ringing
                "in-progress", "forwarding" -> AgentCallPhase.InProgress
                "ended" -> AgentCallPhase.Ended
                else -> if (statusStr.contains("fail") || statusStr.contains("error")) {
                    AgentCallPhase.Failed
                } else {
                    AgentCallPhase.InProgress
                }
            }

            val endedReason = parsed["endedReason"]?.jsonPrimitive?.contentOrNull
            val transcript = parsed["transcript"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: run {
                val messages = parsed["messages"]?.jsonArray
                messages?.mapNotNull { msg ->
                    val role = msg.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
                    val text = msg.jsonObject["message"]?.jsonPrimitive?.contentOrNull
                        ?: msg.jsonObject["content"]?.jsonPrimitive?.contentOrNull
                    text?.let { "$role: $it" }
                }?.joinToString("\n")
            }

            val analysis = parsed["analysis"]?.jsonObject
            val summary = analysis?.get("summary")?.jsonPrimitive?.contentOrNull
            val duration = parsed["duration"]?.jsonPrimitive?.doubleOrNull?.toInt()
                ?: parsed["durationSeconds"]?.jsonPrimitive?.intOrNull
                ?: 0
            val cost = parsed["cost"]?.jsonPrimitive?.doubleOrNull

            AgentCallStatus(
                callId = callId,
                phase = phase,
                endedReason = endedReason,
                transcript = transcript,
                summary = summary,
                durationSeconds = duration,
                cost = cost,
            )
        }
    }

    override suspend fun endCall(
        setting: AgentCallSetting,
        callId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            val httpRequest = Request.Builder()
                .url("$BASE_URL/call/$callId")
                .header("Authorization", "Bearer $apiKey")
                .delete()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            if (!response.isSuccessful && response.code != 404) {
                val body = response.body?.string().orEmpty()
                Log.w(TAG, "End call returned ${response.code}: $body")
            }
        }
    }

    override suspend fun testConnection(
        setting: AgentCallSetting,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val apiKey = setting.apiKey.trim()
            val phoneId = setting.phoneNumberId.trim()
            require(apiKey.isNotBlank()) { "API Key is required" }

            val url = if (phoneId.isNotBlank()) {
                "$BASE_URL/phone-number/$phoneId"
            } else {
                "$BASE_URL/phone-number"
            }

            val httpRequest = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = httpClient.newCall(httpRequest).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Vapi verification failed (${response.code}): $body")
            }

            if (phoneId.isNotBlank()) {
                val parsed = json.parseToJsonElement(body).jsonObject
                val num = parsed["number"]?.jsonPrimitive?.contentOrNull ?: phoneId
                "Connected to Vapi. Verified phone number: $num"
            } else {
                "Connected to Vapi successfully. (No phone number ID specified)"
            }
        }
    }
}
