package me.rerere.rikkahub.data.datastore

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.R
import kotlin.uuid.Uuid

val DEFAULT_AUTO_MODEL_ID = Uuid.parse("b7055fb4-39f9-4042-a88a-0d80ed76cf08")

/** Built-in provider slot formerly used for Vercel AI Gateway. */
val BUILTIN_OLLAMA_PROVIDER_ID = Uuid.parse("386e0f29-8228-4512-affe-8fd8add82d88")

const val OLLAMA_LOCAL_BASE_URL = "http://localhost:11434/v1"
const val OLLAMA_CLOUD_BASE_URL = "https://ollama.com/v1"
private const val LEGACY_VERCEL_AI_GATEWAY_BASE_URL = "https://ai-gateway.vercel.sh/v1"

val DEFAULT_PROVIDERS = listOf(
    ProviderSetting.OpenAI(
        id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        apiKey = "",
        enabled = true,
        builtIn = true
    ),
    ProviderSetting.Google(
        id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
        name = "Gemini",
        apiKey = "",
        enabled = true,
        builtIn = true
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b"),
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/user/balance",
            resultPath = "balance_infos[0].total_balance"
        )
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        balanceOption = BalanceOption(
            enabled = true,
            apiPath = "/credits",
            resultPath = "data.total_credits - data.total_usage",
        )
    ),
    ProviderSetting.OpenAI(
        id = BUILTIN_OLLAMA_PROVIDER_ID,
        name = "Ollama",
        baseUrl = OLLAMA_LOCAL_BASE_URL,
        apiKey = "",
        enabled = true,
        builtIn = true,
        description = {
            Text(
                text = stringResource(R.string.setting_provider_ollama_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shortDescription = {
            Text(
                text = stringResource(R.string.setting_provider_ollama_short_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ),
    ProviderSetting.OpenAI(
        id = Uuid.parse("ff3cde7e-0f65-43d7-8fb2-6475c99f5990"),
        name = "xAI",
        baseUrl = "https://api.x.ai/v1",
        apiKey = "",
        enabled = true,
        builtIn = true,
        useResponseApi = true,
    ),
)

/**
 * Migrates the built-in Vercel AI Gateway slot to Ollama for users who still
 * have the old name and/or gateway URL. Preserves API keys and custom models.
 */
fun ProviderSetting.migrateLegacyBuiltInProvider(): ProviderSetting {
    if (id != BUILTIN_OLLAMA_PROVIDER_ID || this !is ProviderSetting.OpenAI) {
        return this
    }
    val defaultOllama = DEFAULT_PROVIDERS
        .filterIsInstance<ProviderSetting.OpenAI>()
        .first { it.id == BUILTIN_OLLAMA_PROVIDER_ID }

    val legacyName = name.equals("Vercel", ignoreCase = true)
    val legacyBaseUrl = baseUrl.trimEnd('/').equals(
        LEGACY_VERCEL_AI_GATEWAY_BASE_URL.trimEnd('/'),
        ignoreCase = true,
    )
    if (!legacyName && !legacyBaseUrl) {
        return this
    }

    return copy(
        name = if (legacyName) defaultOllama.name else name,
        baseUrl = if (legacyBaseUrl) defaultOllama.baseUrl else baseUrl,
        balanceOption = if (legacyBaseUrl) defaultOllama.balanceOption else balanceOption,
    )
}
