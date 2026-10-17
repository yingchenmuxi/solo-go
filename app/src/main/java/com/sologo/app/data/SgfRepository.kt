package com.sologo.app.data

import android.content.Context
import android.net.Uri
import com.sologo.core.model.GameRecord
import com.sologo.sgf.AnalysisStore
import com.sologo.sgf.SgfCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SavedGame(
    val file: File,
    val title: String,
    val modifiedAt: Long,
    val hasAnalysis: Boolean
)

/**
 * 棋谱文件仓库。SGF 放私有目录；分析结果使用同名 analysis.json。
 * 通过 SAF 导入/导出，不申请存储权限。
 */
class SgfRepository(context: Context) {
    private val appContext = context.applicationContext
    private val gameDir = File(appContext.filesDir, "games")

    fun ensureDir() {
        gameDir.mkdirs()
    }

    suspend fun list(): List<SavedGame> = withContext(Dispatchers.IO) {
        ensureDir()
        gameDir.listFiles { file -> file.isFile && file.extension.equals("sgf", true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map { file ->
                val title = runCatching {
                    SgfCodec.decode(file.readText(Charsets.UTF_8))
                }.getOrNull()?.let { record ->
                    record.blackName.ifBlank { "黑" } + " vs " + record.whiteName.ifBlank { "白" }
                } ?: file.nameWithoutExtension
                SavedGame(
                    file = file,
                    title = title,
                    modifiedAt = file.lastModified(),
                    hasAnalysis = AnalysisStore.companionFile(file).exists()
                )
            }
    }

    suspend fun read(file: File): GameRecord = withContext(Dispatchers.IO) {
        SgfCodec.decode(file.readText(Charsets.UTF_8))
    }

    suspend fun save(record: GameRecord, title: String? = null): File = withContext(Dispatchers.IO) {
        ensureDir()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val base = (title ?: "game-$stamp").replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]"), "_")
        var target = File(gameDir, "$base.sgf")
        var suffix = 1
        while (target.exists()) {
            target = File(gameDir, "${base}_${suffix++}.sgf")
        }
        target.writeText(SgfCodec.encode(record), Charsets.UTF_8)
        target
    }

    suspend fun import(uri: Uri): File = withContext(Dispatchers.IO) {
        ensureDir()
        val text = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("无法读取选择的文件")
        // 先解析验证，避免把错误文件放进棋谱库。
        SgfCodec.decode(text)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(gameDir, "import-$stamp.sgf")
        target.writeText(text, Charsets.UTF_8)
        target
    }

    suspend fun export(file: File, uri: Uri) = withContext(Dispatchers.IO) {
        val output = appContext.contentResolver.openOutputStream(uri) ?: error("无法打开导出目标")
        output.use { it.write(file.readBytes()) }
    }

    suspend fun delete(file: File) = withContext(Dispatchers.IO) {
        AnalysisStore.delete(file)
        file.delete()
    }
}
