package com.sologo.engine

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Properties

/**
 * 资源安装器。
 *
 * 1. 优先使用 APK 的 nativeLibraryDir/libkatago.so（Android 10+ 可执行）；
 * 2. 同时支持 assets 中的 katago 二进制（复制到 filesDir + chmod）；
 * 3. 权重/cfg 复制到 filesDir，流式处理，避免一次读入大文件导致 OOM；
 * 4. 如果 assets/katago/checksums.properties 存在，则做严格 MD5 校验。
 *
 * checksums.properties 示例：
 * katago.md5=...
 * model.md5=...
 * gtp-cfg.md5=...
 */
class KataGoInstaller(
    context: Context,
    private val config: EngineConfig
) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "katago")

    suspend fun install(): InstalledEngine = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            throw KataGoException(
                "当前设备不是 arm64-v8a，无法运行内置 KataGo。支持的 ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
            )
        }
        rootDir.mkdirs()
        val expected = readChecksums()
        val configFile = installAsset(
            assetName = findAsset(config.configAsset, listOf("gtp.cfg", "*.cfg")),
            target = File(rootDir, "gtp.cfg"),
            expectedMd5 = expected["gtp-cfg.md5"]
        )
        // KataGo 同时支持文本模型 .txt.gz 和二进制模型 .bin.gz。
        // 安装后的文件名保留扩展名，避免加载器按扩展名选择错误解析路径。
        val modelAsset = findAsset(
            config.modelAsset,
            listOf("model.bin", "model.bin.gz", "model.txt.gz", "model.txt", "*.bin", "*.bin.gz", "*.txt.gz", "*.txt")
        )
        val modelTargetName = when {
            modelAsset.endsWith(".txt.gz", ignoreCase = true) -> "model.txt.gz"
            // 我们的 text 权重在 assets 中刻意去掉了 .gz 后缀以绕开 AAPT 自动解压，
            // 但安装到 filesDir 后恢复 .txt.gz，KataGo 会按文本权重加载。
            modelAsset.endsWith(".txt", ignoreCase = true) -> "model.txt.gz"
            else -> "model.bin.gz"
        }
        val modelFile = installAsset(
            assetName = modelAsset,
            target = File(rootDir, modelTargetName),
            expectedMd5 = expected["model.md5"],
            largeFile = true
        )

        val native = File(appContext.applicationInfo.nativeLibraryDir, "libkatago.so")
        val binary: File
        val usedNative: Boolean
        if (native.exists()) {
            binary = native
            usedNative = true
            expected["katago.md5"]?.trim()?.lowercase()?.takeIf { it.isNotBlank() }?.let { expectedMd5 ->
                val actual = md5(native)
                if (!actual.equals(expectedMd5, ignoreCase = true)) {
                    throw KataGoException("libkatago.so MD5 校验失败，期望 $expectedMd5，实际 $actual")
                }
            }
            Log.i(TAG, "使用 nativeLibraryDir: ${native.absolutePath}")
        } else {
            binary = installAsset(
                assetName = findAsset(config.binaryAsset, listOf("katago", "katago-*")),
                target = File(rootDir, "katago"),
                expectedMd5 = expected["katago.md5"]
            )
            makeExecutable(binary)
            usedNative = false
            Log.w(
                TAG,
                "未找到 libkatago.so，回退到 assets 二进制。Android 10+ 正式包通常无法执行 filesDir 内的程序，" +
                    "请在 app/src/main/jniLibs/arm64-v8a/ 放入 libkatago.so。"
            )
        }
        InstalledEngine(binary, configFile, modelFile, rootDir, usedNative)
    }

    private fun readChecksums(): Map<String, String> {
        return runCatching {
            appContext.assets.open(config.checksumsAsset).use { input ->
                val properties = Properties().apply { load(input) }
                properties.stringPropertyNames().associateWith { properties.getProperty(it) }
            }
        }.getOrElse {
            Log.w(TAG, "没有找到 ${config.checksumsAsset}，跳过严格 MD5 校验（开发模式）")
            emptyMap()
        }
    }

    private fun findAsset(preferred: String, fallbackPatterns: List<String>): String {
        if (assetExists(preferred)) return preferred
        val files = appContext.assets.list(config.assetDir).orEmpty()
        for (pattern in fallbackPatterns) {
            val candidate = files.firstOrNull { name ->
                when {
                    pattern.startsWith("*.") -> name.endsWith(pattern.removePrefix("*"), ignoreCase = true)
                    pattern.endsWith("*") -> name.startsWith(pattern.removePrefix("*"))
                    else -> name.equals(pattern, ignoreCase = true)
                }
            }
            if (candidate != null) return "${config.assetDir}/$candidate"
        }
        throw KataGoException(
            "assets 中缺少 ${config.assetDir}/$preferred。请把 KataGo 可执行文件、权重和 gtp.cfg 放入 assets/${config.assetDir}/。"
        )
    }

    private fun assetExists(path: String): Boolean = runCatching {
        appContext.assets.open(path).close()
        true
    }.getOrDefault(false)

    private fun installAsset(
        assetName: String,
        target: File,
        expectedMd5: String?,
        largeFile: Boolean = false
    ): File {
        val expected = expectedMd5?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (target.exists() && (expected == null || md5(target) == expected)) {
            return target
        }
        target.parentFile?.mkdirs()
        if (largeFile) {
            val available = target.parentFile?.usableSpace ?: 0L
            val estimated = runCatching {
                appContext.assets.openFd(assetName).use { it.length }
            }.getOrDefault(0L)
            if (estimated > 0 && available < estimated + 32L * 1024 * 1024) {
                throw KataGoException(
                    "存储空间不足：模型约 ${estimated / 1024 / 1024} MB，可用 ${available / 1024 / 1024} MB"
                )
            }
        }
        Log.i(TAG, "复制 $assetName -> ${target.absolutePath} (${if (largeFile) "大文件流式复制" else "普通复制"})")
        appContext.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
        if (expected != null) {
            val actual = md5(target)
            if (!actual.equals(expected, ignoreCase = true)) {
                target.delete()
                throw KataGoException("MD5 校验失败: $assetName，期望 $expected，实际 $actual")
            }
        }
        return target
    }

    private fun makeExecutable(file: File) {
        runCatching {
            Os.chmod(file.absolutePath, 493) // 0755
        }.onFailure {
            file.setExecutable(true, false)
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "KataGoInstaller"
    }
}
