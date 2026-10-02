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
        assertEquals(listOf("OpenAI", "Gemini", "DeepSeek", "OpenRouter", "Ollama", "xAI"), names)
        assertEquals(6, DEFAULT_PROVIDERS.size)

        val ollama = DEFAULT_PROVIDERS.filterIsInstance<ProviderSetting.OpenAI>().first { it.name == "Ollama" }
        assertEquals(BUILTIN_OLLAMA_PROVIDER_ID, ollama.id)
        assertEquals(OLLAMA_LOCAL_BASE_URL, ollama.baseUrl)
        assertEquals("", ollama.apiKey)
        assertTrue(ollama.builtIn)
        assertFalse(ollama.balanceOption.enabled)
    }

    @Test
    fun `migrateLegacyBuiltInProvider remaps Vercel gateway defaults to Ollama`() {
        val legacy = ProviderSetting.OpenAI(
            id = BUILTIN_OLLAMA_PROVIDER_ID,
            name = "Vercel",
            baseUrl = "https://ai-gateway.vercel.sh/v1",
            apiKey = "keep-me",
            balanceOption = me.rerere.ai.provider.BalanceOption(
                enabled = true,
                apiPath = "/credits",
                resultPath = "balance",
            ),
        )

        val migrated = legacy.migrateLegacyBuiltInProvider() as ProviderSetting.OpenAI
        assertEquals("Ollama", migrated.name)
        assertEquals(OLLAMA_LOCAL_BASE_URL, migrated.baseUrl)
        assertEquals("keep-me", migrated.apiKey)
        assertFalse(migrated.balanceOption.enabled)
    }

    @Test
    fun `migrateLegacyBuiltInProvider leaves custom Ollama cloud config alone`() {
        val cloud = ProviderSetting.OpenAI(
            id = BUILTIN_OLLAMA_PROVIDER_ID,
            name = "Ollama",
            baseUrl = OLLAMA_CLOUD_BASE_URL,
            apiKey = "ollama-cloud-key",
        )

        val migrated = cloud.migrateLegacyBuiltInProvider() as ProviderSetting.OpenAI
        assertEquals("Ollama", migrated.name)
        assertEquals(OLLAMA_CLOUD_BASE_URL, migrated.baseUrl)
        assertEquals("ollama-cloud-key", migrated.apiKey)
    }
}
