package com.sologo.engine

/**
 * KataGo 资源与搜索参数。路径均相对 assets 根目录。
 *
 * 推荐打包方式（Android 10+ 必须这样，否则 exec 会被系统拒绝）：
 * app/src/main/jniLibs/arm64-v8a/libkatago.so
 * 权重和 cfg 仍可以放 assets/katago/。
 *
 * 兼容开发期的 assets/katago/katago 方式，但只能在较旧 Android 或
 * 特定设备/系统允许时执行，正式包请使用 jniLibs。
 */
data class EngineConfig(
    val assetDir: String = "katago",
    val binaryAsset: String = "katago/katago",
    val configAsset: String = "katago/gtp.cfg",
    // 资产文件故意不用 .gz 后缀：AAPT 会自动解压 .gz 并改写文件名。
    // 这里保存的是 gzip 压缩的文本权重，安装到 filesDir 时再改名为 model.txt.gz。
    val modelAsset: String = "katago/model.txt",
    val checksumsAsset: String = "katago/checksums.properties",
    // 默认偏速度：b6c96 小模型 + 4 线程下，60 visits 通常能在一两秒内落子。
    val defaultVisits: Int = 60,
    val maxVisits: Int = 4000,
    val commandTimeoutMs: Long = 30_000,
    val genmoveTimeoutMs: Long = 120_000,
    val analysisIntervalMs: Int = 300,
    val analysisDurationMs: Long = 700
)

data class InstalledEngine(
    val binary: java.io.File,
    val config: java.io.File,
    val model: java.io.File,
    val workDir: java.io.File,
    val usedNativeLibrary: Boolean
)

class KataGoException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GtpCommandException(
    val command: String,
    val serverMessage: String
) : Exception("GTP 命令失败: $command -> $serverMessage")

class EngineNotReadyException : IllegalStateException("KataGo 尚未初始化")
