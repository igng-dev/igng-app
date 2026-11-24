package net.igng.core.updater

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadResult {
    data class Success(
        val file: File,
        val sha256: String,
        val sizeBytes: Long,
    ) : DownloadResult

    data class Failure(
        val message: String,
    ) : DownloadResult
}

interface UpdateFileDownloader {
    suspend fun download(
        url: String,
        userAgent: String,
        targetFile: File,
        expectedSizeBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit,
    ): DownloadResult
}

/**
 * Streaming APK downloader with on-the-fly SHA-256 hashing, instant coroutine cancellation cleanup,
 * atomic file promotion (`.part` -> `.apk`), and zero IGNG credential attachment.
 */
class OkHttpUpdateDownloader(
    private val httpClient: OkHttpClient = defaultClient(),
) : UpdateFileDownloader {

    override suspend fun download(
        url: String,
        userAgent: String,
        targetFile: File,
        expectedSizeBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long, bytesPerSecond: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        if (!GitHubReleaseParser.isAllowedDownloadUrl(url)) {
            return@withContext DownloadResult.Failure("下载地址不在受信任的官方发布域名范围内，已中止下载。")
        }

        targetFile.parentFile?.mkdirs()
        val partFile = File(targetFile.parentFile, "${targetFile.name}.part")
        if (partFile.exists()) {
            partFile.delete()
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/octet-stream, application/vnd.android.package-archive, */*")
            .build()

        val call = httpClient.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext DownloadResult.Failure("下载安装包失败（HTTP ${response.code}），可重试或改用浏览器下载。")
                }
                val body = response.body
                    ?: return@withContext DownloadResult.Failure("下载响应为空，请重试。")

                val contentLength = body.contentLength().takeIf { it > 0L } ?: expectedSizeBytes
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)

                var downloaded = 0L
                val startedAtMs = System.currentTimeMillis()
                var lastEmitAtMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastEmitAtMs >= PROGRESS_EMIT_INTERVAL_MS) {
                                val elapsedSec = ((now - startedAtMs).coerceAtLeast(1L)) / 1000.0
                                val speed = (downloaded / elapsedSec).toLong().coerceAtLeast(0L)
                                onProgress(downloaded, contentLength, speed)
                                lastEmitAtMs = now
                            }
                        }
                        output.flush()
                    }
                }

                currentCoroutineContext().ensureActive()
                val elapsedSec = ((System.currentTimeMillis() - startedAtMs).coerceAtLeast(1L)) / 1000.0
                val finalSpeed = (downloaded / elapsedSec).toLong().coerceAtLeast(0L)
                onProgress(downloaded, maxOf(contentLength, downloaded), finalSpeed)

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                val promoted = partFile.renameTo(targetFile)
                if (!promoted) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }

                val sha256 = digest.digest().toLowerHex()
                DownloadResult.Success(
                    file = targetFile,
                    sha256 = sha256,
                    sizeBytes = downloaded,
                )
            }
        } catch (ce: CancellationException) {
            call.cancel()
            partFile.delete()
            throw ce
        } catch (_: SocketTimeoutException) {
            partFile.delete()
            DownloadResult.Failure("下载超时，当前网络访问 GitHub 较慢，可重试或点击通过浏览器下载。")
        } catch (io: IOException) {
            partFile.delete()
            DownloadResult.Failure("下载中断：${io.localizedMessage ?: "网络连接异常"}")
        } catch (t: Throwable) {
            partFile.delete()
            DownloadResult.Failure("下载安装包时发生错误：${t.localizedMessage ?: t.javaClass.simpleName}")
        }
    }

    companion object {
        private const val PROGRESS_EMIT_INTERVAL_MS = 150L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun computeFileSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(16 * 1024)
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            return digest.digest().toLowerHex()
        }
    }
}

internal fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    for (b in this@toLowerHex) {
        val v = b.toInt() and 0xFF
        append(HEX_CHARS[v ushr 4])
        append(HEX_CHARS[v and 0x0F])
    }
}

private val HEX_CHARS = "0123456789abcdef".toCharArray()
