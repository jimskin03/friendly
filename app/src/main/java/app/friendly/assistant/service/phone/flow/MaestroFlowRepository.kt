package app.friendly.assistant.service.phone.flow

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SavedFlowInfo(
    val name: String,
    val description: String,
    val stepCount: Int,
    val lastModified: Long,
    val content: String,
)

class MaestroFlowRepository(
    private val context: Context? = null,
    baseDir: File? = null,
) {

    private val flowDir: File by lazy {
        baseDir ?: File(requireNotNull(context) { "Context or baseDir must be provided" }.filesDir, "phone_flows").apply {
            if (!exists()) mkdirs()
        }
    }

    suspend fun listFlows(): List<SavedFlowInfo> = withContext(Dispatchers.IO) {
        val files = flowDir.listFiles { f -> f.isFile && (f.extension == "yaml" || f.extension == "yml" || f.extension == "json") }
            ?: return@withContext emptyList()

        files.map { file ->
            val content = runCatching { file.readText() }.getOrDefault("")
            val name = file.nameWithoutExtension
            val commands = MaestroFlowParser.parse(content)
            val desc = extractDescription(content)
            SavedFlowInfo(
                name = name,
                description = desc,
                stepCount = commands.size,
                lastModified = file.lastModified(),
                content = content,
            )
        }.sortedByDescending { it.lastModified }
    }

    suspend fun getFlow(name: String): SavedFlowInfo? = withContext(Dispatchers.IO) {
        val file = resolveFile(name) ?: return@withContext null
        val content = runCatching { file.readText() }.getOrNull() ?: return@withContext null
        val commands = MaestroFlowParser.parse(content)
        val desc = extractDescription(content)
        SavedFlowInfo(
            name = file.nameWithoutExtension,
            description = desc,
            stepCount = commands.size,
            lastModified = file.lastModified(),
            content = content,
        )
    }

    suspend fun saveFlow(name: String, content: String, description: String = ""): Boolean = withContext(Dispatchers.IO) {
        val sanitized = sanitizeName(name)
        if (sanitized.isBlank()) return@withContext false
        val file = File(flowDir, "$sanitized.yaml")
        runCatching {
            val finalContent = if (description.isNotBlank() && !content.contains("# description:")) {
                "# description: $description\n$content"
            } else {
                content
            }
            file.writeText(finalContent)
            true
        }.getOrDefault(false)
    }

    suspend fun deleteFlow(name: String): Boolean = withContext(Dispatchers.IO) {
        val file = resolveFile(name) ?: return@withContext false
        file.delete()
    }

    private fun resolveFile(name: String): File? {
        val sanitized = sanitizeName(name)
        val yaml = File(flowDir, "$sanitized.yaml")
        if (yaml.exists()) return yaml
        val yml = File(flowDir, "$sanitized.yml")
        if (yml.exists()) return yml
        val json = File(flowDir, "$sanitized.json")
        if (json.exists()) return json
        return null
    }

    private fun sanitizeName(name: String): String {
        return name.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
    }

    private fun extractDescription(content: String): String {
        for (line in content.lineSequence().take(10)) {
            val trimmed = line.trim()
            if (trimmed.startsWith("# description:", ignoreCase = true)) {
                return trimmed.substringAfter(":").trim()
            }
            if (trimmed.startsWith("#") && trimmed.length > 2 && !trimmed.startsWith("---")) {
                return trimmed.removePrefix("#").trim()
            }
        }
        return ""
    }
}
