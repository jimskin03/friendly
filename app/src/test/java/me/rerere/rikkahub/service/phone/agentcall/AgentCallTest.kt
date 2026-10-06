package me.rerere.rikkahub.service.phone.agentcall

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.data.datastore.AgentCallSetting
import me.rerere.rikkahub.data.datastore.DisplaySetting
import me.rerere.rikkahub.data.datastore.isAgentCallActive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCallTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testIsAgentCallActive() {
        val defaultDisplay = DisplaySetting()
        assertFalse(defaultDisplay.isAgentCallActive)

        val legacyActive = DisplaySetting(enableAgentSpeakOnCalls = true)
        assertTrue(legacyActive.isAgentCallActive)

        val modernActive = DisplaySetting(agentCallSetting = AgentCallSetting(enabled = true))
        assertTrue(modernActive.isAgentCallActive)

        val bothActive = DisplaySetting(
            enableAgentSpeakOnCalls = true,
            agentCallSetting = AgentCallSetting(enabled = true),
        )
        assertTrue(bothActive.isAgentCallActive)
    }

    @Test
    fun testAgentCallPromptConstruction() {
        val settingWithDisclosure = AgentCallSetting(
            enabled = true,
            ownerName = "Greg",
            discloseAi = true,
        )

        val prompt = buildString {
            if (settingWithDisclosure.discloseAi) {
                append("You are an autonomous AI voice assistant placing a phone call.")
            } else {
                append("You are placing a phone call.")
            }
            if (settingWithDisclosure.ownerName.isNotBlank()) {
                append(" You are calling on behalf of ${settingWithDisclosure.ownerName}.")
            }
        }

        assertTrue(prompt.contains("autonomous AI voice assistant"))
        assertTrue(prompt.contains("on behalf of Greg"))

        val settingNoDisclosure = AgentCallSetting(
            enabled = true,
            ownerName = "Alice",
            discloseAi = false,
        )

        val promptNoDisclosure = buildString {
            if (settingNoDisclosure.discloseAi) {
                append("You are an autonomous AI voice assistant placing a phone call.")
            } else {
                append("You are placing a phone call.")
            }
            if (settingNoDisclosure.ownerName.isNotBlank()) {
                append(" You are calling on behalf of ${settingNoDisclosure.ownerName}.")
            }
        }

        assertFalse(promptNoDisclosure.contains("autonomous AI voice assistant"))
        assertTrue(promptNoDisclosure.contains("on behalf of Alice"))
    }

    @Test
    fun testVapiStatusParsing() {
        val sampleVapiResponse = """
            {
              "id": "vapi-call-123",
              "status": "ended",
              "endedReason": "customer-ended-call",
              "messages": [
                { "role": "assistant", "message": "Hi, this is Greg's assistant." },
                { "role": "user", "message": "Hello, how can I help?" }
              ],
              "analysis": {
                "summary": "Agent called regarding reservation."
              },
              "duration": 45.2,
              "cost": 0.05
            }
        """.trimIndent()

        val parsed = json.parseToJsonElement(sampleVapiResponse).jsonObject
        val callId = parsed["id"]?.jsonPrimitive?.contentOrNull
        val statusStr = parsed["status"]?.jsonPrimitive?.contentOrNull
        val phase = when (statusStr) {
            "queued" -> AgentCallPhase.Queued
            "ringing" -> AgentCallPhase.Ringing
            "in-progress", "forwarding" -> AgentCallPhase.InProgress
            "ended" -> AgentCallPhase.Ended
            else -> AgentCallPhase.Failed
        }

        val messages = parsed["messages"]?.jsonArray
        val transcript = messages?.mapNotNull { msg ->
            val role = msg.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: "assistant"
            val text = msg.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            text?.let { "$role: $it" }
        }?.joinToString("\n")

        val summary = parsed["analysis"]?.jsonObject?.get("summary")?.jsonPrimitive?.contentOrNull
        val duration = parsed["duration"]?.jsonPrimitive?.doubleOrNull?.toInt() ?: 0

        assertEquals("vapi-call-123", callId)
        assertEquals(AgentCallPhase.Ended, phase)
        assertEquals(45, duration)
        assertEquals("Agent called regarding reservation.", summary)
        assertNotNull(transcript)
        assertTrue(transcript!!.contains("Hi, this is Greg's assistant."))
        assertTrue(transcript.contains("Hello, how can I help?"))
    }

    @Test
    fun testElevenLabsStatusParsing() {
        val sampleElevenLabsResponse = """
            {
              "conversation_id": "conv-456",
              "status": "completed",
              "transcript": [
                { "role": "agent", "message": "Good morning." },
                { "role": "user", "message": "Good morning." }
              ],
              "analysis": {
                "call_summary": "Confirmed appointment for tomorrow."
              },
              "metadata": {
                "call_duration_secs": 62
              }
            }
        """.trimIndent()

        val parsed = json.parseToJsonElement(sampleElevenLabsResponse).jsonObject
        val callId = parsed["conversation_id"]?.jsonPrimitive?.contentOrNull
        val statusStr = parsed["status"]?.jsonPrimitive?.contentOrNull
        val phase = when (statusStr) {
            "processing", "initiating" -> AgentCallPhase.Queued
            "ringing" -> AgentCallPhase.Ringing
            "in-progress", "active" -> AgentCallPhase.InProgress
            "done", "completed" -> AgentCallPhase.Ended
            "failed" -> AgentCallPhase.Failed
            else -> AgentCallPhase.InProgress
        }

        val transcriptArray = parsed["transcript"]?.jsonArray
        val transcript = transcriptArray?.mapNotNull { item ->
            val role = item.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: "agent"
            val msg = item.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            msg?.let { "$role: $it" }
        }?.joinToString("\n")

        val summary = parsed["analysis"]?.jsonObject?.get("call_summary")?.jsonPrimitive?.contentOrNull
        val duration = parsed["metadata"]?.jsonObject?.get("call_duration_secs")?.jsonPrimitive?.intOrNull ?: 0

        assertEquals("conv-456", callId)
        assertEquals(AgentCallPhase.Ended, phase)
        assertEquals(62, duration)
        assertEquals("Confirmed appointment for tomorrow.", summary)
        assertNotNull(transcript)
        assertTrue(transcript!!.contains("agent: Good morning."))
    }
}
