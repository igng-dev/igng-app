package net.igng.core.data

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

/**
 * Unified Coil 3 ImageLoader factory shared across all IGNG Android apps:
 * - Shares [IgngHttpClient.mediaOkHttpClient] with single-flight 401 refresh
 * - Enables GIF / animated WebP decoding on all API levels (minSdk 26+)
 * - Configures a deterministic disk cache directory so Settings reports and clears real image cache
 */
object IgngImageLoaderFactory {
    private const val DISK_CACHE_DIR_NAME = "igng_image_cache"
    private const val MAX_DISK_CACHE_BYTES = 128L * 1024L * 1024L

    fun create(
        context: Context,
        mediaOkHttpClient: OkHttpClient,
    ): ImageLoader {
        val appContext = context.applicationContext
        val diskDir = File(appContext.cacheDir, DISK_CACHE_DIR_NAME)
        return ImageLoader.Builder(appContext)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { mediaOkHttpClient }))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.22)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(diskDir.toOkioPath())
                    .maxSizeBytes(MAX_DISK_CACHE_BYTES)
                    .build()
            }
            .build()
    }

    suspend fun diskCacheSizeBytes(context: Context): Long = withContext(Dispatchers.IO) {
        val dir = File(context.applicationContext.cacheDir, DISK_CACHE_DIR_NAME)
        directorySizeBytes(dir)
    }

    suspend fun clearCaches(context: Context, imageLoader: ImageLoader) = withContext(Dispatchers.IO) {
        runCatching { imageLoader.memoryCache?.clear() }
        runCatching { imageLoader.diskCache?.clear() }
        runCatching {
            val dir = File(context.applicationContext.cacheDir, DISK_CACHE_DIR_NAME)
            dir.listFiles()?.forEach { it.deleteRecursively() }
        }
        Unit
    }

    private fun directorySizeBytes(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { directorySizeBytes(it) } ?: 0L
    }
}
