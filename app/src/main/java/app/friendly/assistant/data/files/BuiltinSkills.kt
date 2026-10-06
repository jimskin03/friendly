package app.friendly.assistant.data.files

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileNotFoundException


internal object BuiltinSkills {
    private const val ASSETS_ROOT = "builtin_skills"
    private const val VERSION_FILE = ".version"


    fun extractIfNeeded(context: Context, targetDir: File) {
        val stamp = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .lastUpdateTime
            .toString()
        val versionFile = targetDir.resolve(VERSION_FILE)
        if (versionFile.exists() && versionFile.readText() == stamp) return


        val staging = targetDir.resolveSibling(".${targetDir.name}.staging")
        staging.deleteRecursively()
        staging.mkdirs()
        val assets = context.assets
        assets.list(ASSETS_ROOT).orEmpty().forEach { name ->
            assets.copyTree("$ASSETS_ROOT/$name", staging.resolve(name))
        }
        staging.resolve(VERSION_FILE).writeText(stamp)

        targetDir.deleteRecursively()
        if (!staging.renameTo(targetDir)) {
            staging.deleteRecursively()
            error("Failed to move builtin skills into ${targetDir.absolutePath}")
        }
    }

    private fun AssetManager.copyTree(assetPath: String, target: File) {
        val children = list(assetPath).orEmpty()
        if (children.isNotEmpty()) {
            target.mkdirs()
            children.forEach { copyTree("$assetPath/$it", target.resolve(it)) }
            return
        }

        try {
            open(assetPath).use { input ->
                target.parentFile?.mkdirs()
                target.outputStream().use { input.copyTo(it) }
            }
        } catch (_: FileNotFoundException) {
            target.mkdirs()
            return
        }

        if (target.startsWithShebang()) {
            target.setExecutable(true, false)
        }
    }

    private fun File.startsWithShebang(): Boolean = inputStream().use { input ->
        input.read() == '#'.code && input.read() == '!'.code
    }
}


internal fun mergeWithBuiltinSkills(
    local: List<SkillMetadata>,
    builtin: List<SkillMetadata>,
): List<SkillMetadata> {
    val localNames = local.mapTo(HashSet()) { it.name }
    return local + builtin.filter { it.name !in localNames }
}
