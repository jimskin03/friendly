package app.friendly.assistant.ui.components.openui

import app.friendly.assistant.data.datastore.Settings
import app.friendly.assistant.data.model.Assistant
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting

/** Everything the web composer pickers need, mirroring the old native ChatInput options. */
internal fun buildOpenUiPickers(
    settings: Settings,
    assistant: Assistant,
    model: Model?,
    localSearchEnabled: Boolean,
): OpenUiPickers {
    val provider = model?.let { m -> settings.providers.firstOrNull { p -> p.models.any { it.id == m.id } } }
    val builtInSearchOn = model?.tools?.contains(BuiltInTools.Search) == true
    val supportsBuiltIn = provider is ProviderSetting.Google || (provider is ProviderSetting.OpenAI && provider.useResponseApi)
    return OpenUiPickers(
        assistants = settings.assistants.map { OpenUiOption(it.id.toString(), it.name.ifBlank { "Default assistant" }) },
        assistantId = assistant.id.toString(),
        models = settings.providers.filter { it.enabled }.flatMap { p ->
            p.models.filter { it.type == ModelType.CHAT }.map { m ->
                OpenUiOption(m.id.toString(), m.displayName.ifBlank { m.modelId }, group = p.name)
            }
        },
        modelId = model?.id?.toString(),
        reasoning = if (model?.abilities?.contains(ModelAbility.REASONING) == true) assistant.reasoningLevel.name.lowercase() else null,
        reasoningLevels = ReasoningLevel.entries.map { it.name.lowercase() },
        mcp = settings.mcpServers.filter { it.commonOptions.enable }.map {
            OpenUiOption(it.id.toString(), it.commonOptions.name.ifBlank { "MCP server" }, enabled = it.id in assistant.mcpServers)
        },
        searchMode = when {
            builtInSearchOn -> "built_in"
            localSearchEnabled -> "local"
            else -> "off"
        },
        searchModes = buildList {
            add("off"); add("local")
            if (model != null && (supportsBuiltIn || builtInSearchOn)) add("built_in")
        },
        searchServices = settings.searchServices.map { OpenUiOption(it.id.toString(), it.displayName) },
        searchServiceId = settings.searchServices.getOrNull(settings.searchServiceSelected)?.id?.toString(),
    )
}
