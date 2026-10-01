package me.rerere.rikkahub.data.ai.tools

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.local.LocalTools
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.workspace.WorkspaceShellStatus

private const val TAG = "ChatToolFactory"

internal fun shouldUseExternalWebSearch(assistant: Assistant, model: Model): Boolean {
    return assistant.enableWebSearch && BuiltInTools.Search !in model.tools
}

internal fun sanitizeMcpToolName(serverName: String, toolName: String): String {
    val safeServer = serverName.replace(Regex("[^a-zA-Z0-9]"), "_").trim('_').ifBlank { "server" }
    val safeTool = toolName.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_').ifBlank { "tool" }
    val combined = "mcp__${safeServer}__${safeTool}"
    if (combined.length <= 64) {
        return combined
    }
    val hash = (safeServer + safeTool).hashCode().toUInt().toString(16)
    val prefix = "mcp__"
    val maxLen = 64 - prefix.length - hash.length - 1
    val truncated = "${safeServer}_${safeTool}".take(maxLen).trimEnd('_')
    return "${prefix}${truncated}_${hash}"
}

class InvalidMcpServerNamesException(val names: List<String>) :
    IllegalStateException("Invalid MCP server names: ${names.joinToString(", ")}")

/** Creates the complete tool set for one generation run, including approval resumption. */
class ChatToolFactory(
    private val json: Json,
    private val memoryRepository: MemoryRepository,
    private val conversationRepository: ConversationRepository,
    private val localTools: LocalTools,
    private val mcpManager: McpManager,
    private val skillManager: SkillManager,
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend fun createTools(
        settings: Settings,
        assistant: Assistant,
        model: Model,
        workspaceCwd: String? = null,
    ): List<Tool> = buildList {
        if (assistant.enableMemory) {
            val memoryAssistantId = if (assistant.useGlobalMemory) {
                MemoryRepository.GLOBAL_MEMORY_ID
            } else {
                assistant.id.toString()
            }
            addAll(
                buildMemoryTools(
                    json = json,
                    onCreation = { content -> memoryRepository.addMemory(memoryAssistantId, content) },
                    onUpdate = { id, content -> memoryRepository.updateContent(id, content) },
                    onDelete = { id -> memoryRepository.deleteMemory(id) },
                )
            )
        }
        if (shouldUseExternalWebSearch(assistant, model)) {
            addAll(createSearchTools(settings))
        }
        addAll(localTools.getTools(assistant.localTools))
        if (assistant.enableRecentChatsReference) {
            addAll(createConversationTools(conversationRepository, assistant.id))
        }
        addAll(createWorkspaceToolsIfReady(assistant.workspaceId?.toString(), workspaceCwd))
        if (assistant.enabledSkills.isNotEmpty()) {
            addAll(
                createSkillTools(
                    enabledSkills = assistant.enabledSkills,
                    allSkills = skillManager.listSkills(),
                )
            )
        }

        val mcpTools = mcpManager.getAllAvailableTools(assistant)
        mcpTools.forEach { (serverId, serverName, tool) ->
            val toolDefName = sanitizeMcpToolName(serverName, tool.name)
            add(
                Tool(
                    name = toolDefName,
                    description = tool.description ?: "",
                    parameters = { tool.inputSchema },
                    needsApproval = { tool.needsApproval },
                    execute = { mcpManager.callTool(serverId, tool.name, it.jsonObject) },
                )
            )
        }
    }

    private suspend fun createWorkspaceToolsIfReady(workspaceId: String?, cwd: String?): List<Tool> {
        if (workspaceId.isNullOrBlank()) return emptyList()
        val workspace = workspaceRepository.getById(workspaceId) ?: return emptyList()
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) {
            Log.d(
                TAG,
                "createWorkspaceToolsIfReady: skip workspace tools, workspace=$workspaceId, status=${workspace.shellStatus}"
            )
            return emptyList()
        }
        return createWorkspaceTools(workspaceId, workspaceRepository, cwd)
    }
}
