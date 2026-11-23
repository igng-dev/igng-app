package net.igng.core.data

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atomic JSON file cache shared across IGNG apps (`.tmp` -> `renameTo` prevents torn writes).
 */
class IgngLocalJsonCache(
    private val dir: File,
) {
    init {
        dir.mkdirs()
    }

    constructor(
        context: Context,
        folderName: String,
        useCacheDir: Boolean = false,
    ) : this(
        File(
            if (useCacheDir) context.applicationContext.cacheDir else context.applicationContext.filesDir,
            folderName,
        ),
    )

    suspend fun write(key: String, raw: String) = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.exists()) dir.mkdirs()
            val safe = sanitize(key)
            val tmp = File(dir, "$safe.tmp")
            val target = File(dir, "$safe.json")
            tmp.writeText(raw, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                target.writeText(raw, Charsets.UTF_8)
                tmp.delete()
            }
        }
        Unit
    }

    suspend fun read(key: String): String? = withContext(Dispatchers.IO) {
        val file = File(dir, "${sanitize(key)}.json")
        if (file.exists()) runCatching { file.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        File(dir, "${sanitize(key)}.json").delete()
        Unit
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    private fun sanitize(key: String): String = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
