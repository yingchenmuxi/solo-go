package com.sologo.sgf

import com.sologo.core.model.GameAnalysis
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * AI 分析的配套 JSON 存储。
 *
 * 命名规则：foo.sgf -> foo.analysis.json，方便棋谱管理页找到并删除。
 */
object AnalysisStore {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun companionFile(sgfFile: File): File =
        File(sgfFile.parentFile, "${sgfFile.nameWithoutExtension}.analysis.json")

    fun save(sgfFile: File, analysis: GameAnalysis) {
        val file = companionFile(sgfFile)
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(GameAnalysis.serializer(), analysis), Charsets.UTF_8)
    }

    fun load(sgfFile: File): GameAnalysis? {
        val file = companionFile(sgfFile)
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString(GameAnalysis.serializer(), file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun delete(sgfFile: File) {
        companionFile(sgfFile).delete()
    }

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
