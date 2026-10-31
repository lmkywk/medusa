package io.github.aw1y2z.sesame.ui.miuix

import android.os.Environment
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.aw1y2z.sesame.data.ViewAppInfo
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.idMap.UserIdMap
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份目录：Download/美杜莎备份/全量/ 与 /域/
 * 全量备份包含数据根目录设置 JSON 和 config/<uid>/（配置、计划、历史、统计）。
 */
internal object BackupManager {

    data class FullBackup(
        val dir: File,
        val label: String,
        val userId: String,
        val createdAt: Long,
        val version: String,
        val fileCount: Int,
        val bytes: Long
    )

    data class DomainExport(
        val file: File,
        val domainKey: String,
        val title: String,
        val createdAt: Long,
        val bytes: Long
    )

    private val mapper = ObjectMapper()

    val root: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "美杜莎备份"
        )
    val fullDir: File get() = File(root, "全量")
    val domainDir: File get() = File(root, "域")
    private val mainDir: File get() = FileUtil.MAIN_DIRECTORY_FILE

    fun ensureDirs() {
        fullDir.mkdirs()
        domainDir.mkdirs()
    }

    fun createFullBackup(userId: String?): Result<FullBackup> = runCatching {
        ensureDirs()
        val label = UserIdMap.getAccountLabel(userId)
            ?.takeIf { it.isNotBlank() } ?: userId?.takeIf { it.isNotBlank() } ?: "默认模板"
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val dir = File(fullDir, "${stamp}_${sanitize(label)}")
        if (!dir.mkdirs()) error("无法创建备份目录")

        val copied = ArrayList<File>()
        mainDir.listFiles()
            ?.filter {
                it.isFile && (
                    it.name.endsWith(".json") ||
                        it.name == "current_account.txt" ||
                        it.name == "http_token.txt"
                    )
            }
            ?.forEach { src ->
                val dst = File(dir, src.name)
                src.copyTo(dst, overwrite = true)
                copied.add(dst)
            }
        val configDir = File(mainDir, "config")
        if (configDir.exists()) {
            configDir.walkTopDown().filter { it.isFile }.forEach { src ->
                val rel = src.relativeTo(mainDir)
                val dst = File(dir, rel.path)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
                copied.add(dst)
            }
        }

        val manifest = mapper.createObjectNode()
        manifest.put("schema", 1)
        manifest.put("type", "full")
        manifest.put("label", label)
        manifest.put("userId", userId ?: "")
        manifest.put("createdAt", System.currentTimeMillis())
        manifest.put("version", ViewAppInfo.getAppVersion())
        manifest.put("fileCount", copied.size)
        manifest.put("bytes", copied.sumOf { it.length() })
        File(dir, "manifest.json").writeText(
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest)
        )
        FullBackup(
            dir = dir,
            label = label,
            userId = userId ?: "",
            createdAt = System.currentTimeMillis(),
            version = ViewAppInfo.getAppVersion(),
            fileCount = copied.size,
            bytes = copied.sumOf { it.length() }
        )
    }

    fun listFullBackups(): List<FullBackup> {
        ensureDirs()
        return fullDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir ->
                val manifest = File(dir, "manifest.json")
                if (!manifest.exists()) return@mapNotNull null
                runCatching {
                    val node = mapper.readTree(manifest)
                    FullBackup(
                        dir = dir,
                        label = node.path("label").asText("未知账号"),
                        userId = node.path("userId").asText(""),
                        createdAt = node.path("createdAt").asLong(0L),
                        version = node.path("version").asText(""),
                        fileCount = node.path("fileCount").asInt(0),
                        bytes = node.path("bytes").asLong(0L)
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    /** 恢复前会先自动做一份「恢复前」全量备份；只覆盖设置/计划/历史，不动日志 */
    fun restoreFull(backup: FullBackup, userId: String?): Result<Unit> = runCatching {
        createFullBackup(userId).getOrThrow()
        backup.dir.walkTopDown()
            .filter { it.isFile && it.name != "manifest.json" }
            .forEach { src ->
                val rel = src.relativeTo(backup.dir)
                val dst = File(mainDir, rel.path)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
            }
        Unit
    }

    fun saveDomainExport(json: String, domainKey: String, title: String): File {
        ensureDirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
        val file = File(domainDir, "${stamp}_${sanitize(title)}_${sanitize(domainKey)}.json")
        file.writeText(json)
        return file
    }

    fun listDomainExports(): List<DomainExport> {
        ensureDirs()
        return domainDir.listFiles()
            ?.filter { it.isFile && it.extension == "json" }
            ?.mapNotNull { file ->
                runCatching {
                    val node = mapper.readTree(file)
                    DomainExport(
                        file = file,
                        domainKey = node.path("domainKey").asText(""),
                        title = node.path("domainTitle").asText("域配置"),
                        createdAt = node.path("exportedAt").asLong(file.lastModified()),
                        bytes = file.length()
                    )
                }.getOrNull()
            }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(40)
}
