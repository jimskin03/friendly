package me.rerere.rikkahub.data.datastore

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultProvidersTest {
    @Test
    fun `default providers should strictly contain 6 providers`() {
        val names = DEFAULT_PROVIDERS.map { it.name }
        assertEquals(listOf("OpenAI", "Gemini", "DeepSeek", "OpenRouter", "Vercel", "xAI"), names)
        assertEquals(6, DEFAULT_PROVIDERS.size)

        val vercel = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>().first { it.name == "Vercel" }
        assertEquals("https://ai-gateway.vercel.sh/v1", vercel.baseUrl)
        assertTrue(vercel.builtIn)
        assertTrue(vercel.balanceOption.enabled)
        assertEquals("/credits", vercel.balanceOption.apiPath)
        assertEquals("balance", vercel.balanceOption.resultPath)
    }
}
