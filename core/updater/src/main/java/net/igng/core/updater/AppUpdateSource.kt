package net.igng.core.updater

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.igng.core.model.AppUpdateChangeScope
import net.igng.core.model.AppUpdateConfig
import net.igng.core.model.AppUpdateRelease
import net.igng.core.model.IgngSemVer
import net.igng.core.network.IgngJson
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateSourceOutcome {
    data class Success(
        val release: AppUpdateRelease,
        val etag: String? = null,
    ) : UpdateSourceOutcome

    data object NotModified : UpdateSourceOutcome

    data object NoRelease : UpdateSourceOutcome

    data class Failure(
        val message: String,
        val isRateLimited: Boolean = false,
    ) : UpdateSourceOutcome
}

/**
 * Pluggable release metadata source abstraction.
 * Defaults to [GitHubReleaseUpdateSource], allowing future swap/fallback to a first-party
 * Client API v1 endpoint without touching download, verification, installation, or UI code.
 */
interface AppUpdateSource {
    suspend fun fetchLatestRelease(
        config: AppUpdateConfig,
        includePrerelease: Boolean,
        cachedEtag: String?,
    ): UpdateSourceOutcome
}

@Serializable
internal data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)

@Serializable
internal data class GitHubAssetDto(
    val name: String = "",
    val size: Long = 0L,
    val digest: String? = null,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String? = null,
)

@Serializable
internal data class UpdateManifestDto(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    val tag: String = "",
    @SerialName("version_name") val versionName: String = "",
    @SerialName("version_code") val versionCode: Int = 0,
    val commit: String? = null,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val changelog: String? = null,
    val apps: Map<String, UpdateManifestAppDto> = emptyMap(),
)

@Serializable
internal data class UpdateManifestAppDto(
    @SerialName("package_name") val packageName: String = "",
    @SerialName("asset_name") val assetName: String = "",
    @SerialName("size_bytes") val sizeBytes: Long = 0L,
    val sha256: String? = null,
    @SerialName("cert_sha256") val certSha256: String? = null,
    @SerialName("change_scope") val changeScope: String? = null,
)

internal object GitHubReleaseParser {
    const val MANIFEST_ASSET_NAME = "update-manifest.json"
    const val SHA256SUMS_ASSET_NAME = "SHA256SUMS.txt"

    private val SHA256_HEX_REGEX = Regex("""^[0-9a-f]{64}$""")
    private val BODY_VERSION_CODE_REGEX = Regex("""versionCode[`:\s*]+(\d+)""", RegexOption.IGNORE_CASE)
    private val SHA256_LINE_REGEX = Regex("""^([0-9a-fA-F]{64})\s+\*?(.+?)\s*$""")
    private val ALLOWED_HOSTS = setOf(
        "github.com",
        "api.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com",
    )

    fun normalizeSha256(raw: String?): String? {
        val cleaned = raw?.trim()
            ?.removePrefix("sha256:")
            ?.removePrefix("SHA256:")
            ?.replace(":", "")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        return cleaned.takeIf { SHA256_HEX_REGEX.matches(it) }
    }

    fun parseSha256Sums(rawText: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        rawText.lineSequence().forEach { line ->
            val match = SHA256_LINE_REGEX.matchEntire(line.trim()) ?: return@forEach
            val digest = match.groupValues[1].lowercase(Locale.ROOT)
            val fileName = match.groupValues[2].trim().substringAfterLast('/')
            if (fileName.isNotEmpty()) {
                map[fileName] = digest
            }
        }
        return map
    }

    fun isAllowedDownloadUrl(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        if (!trimmed.startsWith("https://", ignoreCase = true)) return false
        val host = runCatching { URI(trimmed).host?.lowercase(Locale.ROOT) }.getOrNull() ?: return false
        return host in ALLOWED_HOSTS || host.endsWith(".githubusercontent.com")
    }

    fun findTargetApkAsset(release: GitHubReleaseDto, config: AppUpdateConfig): GitHubAssetDto? {
        val prefix = config.assetPrefix
        return release.assets.firstOrNull { asset ->
            asset.name.startsWith(prefix, ignoreCase = true) &&
                asset.name.endsWith(".apk", ignoreCase = true) &&
                isAllowedDownloadUrl(asset.browserDownloadUrl)
        }
    }

    fun selectBestRelease(
        releases: List<GitHubReleaseDto>,
        includePrerelease: Boolean,
        config: AppUpdateConfig,
    ): GitHubReleaseDto? {
        val candidates = releases.filter { rel ->
            !rel.draft &&
                (includePrerelease || !rel.prerelease) &&
                findTargetApkAsset(rel, config) != null
        }
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull { a, b ->
            val verCmp = IgngSemVer.compareNames(a.tagName, b.tagName)
            if (verCmp != 0) verCmp else a.publishedAt.orEmpty().compareTo(b.publishedAt.orEmpty())
        }
    }

    fun extractVersionCodeFromBody(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        return BODY_VERSION_CODE_REGEX.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    fun extractChangeScopeFromBody(
        body: String?,
        assetName: String,
        packageName: String,
    ): AppUpdateChangeScope {
        if (body.isNullOrBlank()) return AppUpdateChangeScope.UNKNOWN
        val row = body.lineSequence().firstOrNull { line ->
            line.contains('|') && (line.contains(assetName, ignoreCase = true) || line.contains(packageName, ignoreCase = true))
        } ?: return AppUpdateChangeScope.UNKNOWN

        return when {
            row.contains("本应用变更 + 共享底座") -> AppUpdateChangeScope.APP_AND_CORE
            row.contains("本应用功能/修复更新") -> AppUpdateChangeScope.APP_ONLY
            row.contains("共享底座") -> AppUpdateChangeScope.CORE_ONLY
            row.contains("本版本无独立业务改动") -> AppUpdateChangeScope.UNCHANGED
            row.contains("首个统一家族版本") -> AppUpdateChangeScope.INITIAL
            else -> AppUpdateChangeScope.UNKNOWN
        }
    }

    /**
     * Strips verbose verification/checksum tables from GitHub Release markdown so users see a
     * concise, readable changelog inside the Settings update sheet.
     */
    fun cleanReleaseChangelog(rawBody: String?): String {
        if (rawBody.isNullOrBlank()) return ""
        val kept = mutableListOf<String>()
        var skippingBoilerplate = false
        for (rawLine in rawBody.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("## IGNG Android Apps ")) continue
            if (trimmed.startsWith("### 安装与升级说明") ||
                trimmed.startsWith("### 发布校验信息") ||
                trimmed.startsWith("### 应用更新范围")
            ) {
                skippingBoilerplate = true
                continue
            }
            if (skippingBoilerplate) {
                if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                    skippingBoilerplate = false
                } else {
                    continue
                }
            }
            if (trimmed.startsWith("**Full Changelog**:")) continue
            kept += rawLine
        }
        return kept.joinToString("\n").trim()
    }

    fun resolveRelease(
        dto: GitHubReleaseDto,
        manifest: UpdateManifestDto?,
        sha256Sums: Map<String, String>,
        config: AppUpdateConfig,
    ): AppUpdateRelease? {
        val manifestApp = manifest?.apps?.get(config.appKey)
        val apkAsset = if (!manifestApp?.assetName.isNullOrBlank()) {
            dto.assets.firstOrNull {
                it.name.equals(manifestApp!!.assetName, ignoreCase = true) &&
                    isAllowedDownloadUrl(it.browserDownloadUrl)
            } ?: findTargetApkAsset(dto, config)
        } else {
            findTargetApkAsset(dto, config)
        } ?: return null

        val rawTag = manifest?.tag?.takeIf { it.isNotBlank() } ?: dto.tagName.trim()
        val parsedVer = IgngSemVer.parse(manifest?.versionName?.takeIf { it.isNotBlank() } ?: rawTag)
        val versionName = manifest?.versionName?.takeIf { it.isNotBlank() }
            ?: parsedVer?.canonicalName
            ?: rawTag.removePrefix("v")

        val versionCode = manifest?.versionCode?.takeIf { it > 0 }
            ?: extractVersionCodeFromBody(dto.body)
            ?: parsedVer?.inferredStableVersionCode
            ?: 0

        val sha256 = normalizeSha256(manifestApp?.sha256)
            ?: normalizeSha256(apkAsset.digest)
            ?: normalizeSha256(sha256Sums[apkAsset.name])

        val certSha256 = normalizeSha256(manifestApp?.certSha256)

        val changeScope = if (!manifestApp?.changeScope.isNullOrBlank()) {
            AppUpdateChangeScope.fromWire(manifestApp?.changeScope)
        } else {
            extractChangeScopeFromBody(dto.body, apkAsset.name, config.packageName)
        }

        val changelog = manifest?.changelog?.takeIf { it.isNotBlank() }
            ?: cleanReleaseChangelog(dto.body)

        val sizeBytes = when {
            apkAsset.size > 0L -> apkAsset.size
            (manifestApp?.sizeBytes ?: 0L) > 0L -> manifestApp!!.sizeBytes
            else -> 0L
        }

        val pageUrl = dto.htmlUrl.takeIf { it.startsWith("https://", ignoreCase = true) }
            ?: config.releasesPageUrl

        return AppUpdateRelease(
            tagName = rawTag,
            versionName = versionName,
            versionCode = versionCode,
            title = dto.name?.takeIf { it.isNotBlank() } ?: "v$versionName",
            changelog = changelog,
            publishedAt = manifest?.publishedAt?.takeIf { it.isNotBlank() } ?: dto.publishedAt,
            isPrerelease = dto.prerelease || (manifest?.prerelease == true) || (parsedVer?.isPrerelease == true),
            assetName = apkAsset.name,
            downloadUrl = apkAsset.browserDownloadUrl,
            releasePageUrl = pageUrl,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            certSha256 = certSha256,
            changeScope = changeScope,
        )
    }
}

/**
 * Unauthenticated GitHub Releases update source backed by ETag conditional requests (`304 Not Modified`
 * does not consume GitHub's anonymous rate limit) and optional `update-manifest.json` metadata.
 */
class GitHubReleaseUpdateSource(
    private val httpClient: OkHttpClient = defaultClient(),
) : AppUpdateSource {

    override suspend fun fetchLatestRelease(
        config: AppUpdateConfig,
        includePrerelease: Boolean,
        cachedEtag: String?,
    ): UpdateSourceOutcome = withContext(Dispatchers.IO) {
        val url = if (includePrerelease) {
            "https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases?per_page=10"
        } else {
            "https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases/latest"
        }

        val reqBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", config.userAgent)
        if (!cachedEtag.isNullOrBlank()) {
            reqBuilder.header("If-None-Match", cachedEtag)
        }

        val response = try {
            httpClient.newCall(reqBuilder.build()).execute()
        } catch (_: SocketTimeoutException) {
            return@withContext UpdateSourceOutcome.Failure("连接 GitHub 更新服务超时，请稍后重试或点击在浏览器查看。")
        } catch (e: IOException) {
            return@withContext UpdateSourceOutcome.Failure("无法连接到 GitHub 更新服务：${e.localizedMessage ?: "网络不可用"}")
        }

        response.use { res ->
            if (res.code == 304) {
                return@withContext UpdateSourceOutcome.NotModified
            }
            if (res.code == 404) {
                return@withContext UpdateSourceOutcome.NoRelease
            }
            if (res.code == 403 || res.code == 429) {
                val remaining = res.header("X-RateLimit-Remaining")
                if (remaining == "0" || res.code == 429) {
                    return@withContext UpdateSourceOutcome.Failure(
                        message = "当前网络请求 GitHub 更新接口较频繁，已被临时限流，请稍后再试或通过浏览器查看发布页。",
                        isRateLimited = true,
                    )
                }
            }
            if (!res.isSuccessful) {
                return@withContext UpdateSourceOutcome.Failure("更新服务返回异常状态（HTTP ${res.code}）。")
            }

            val etag = res.header("ETag")
            val bodyStr = res.body?.string().orEmpty()
            if (bodyStr.isBlank()) {
                return@withContext UpdateSourceOutcome.Failure("更新服务返回了空响应。")
            }

            val selectedDto = runCatching {
                if (includePrerelease) {
                    val list = IgngJson.instance.decodeFromString<List<GitHubReleaseDto>>(bodyStr)
                    GitHubReleaseParser.selectBestRelease(list, includePrerelease = true, config = config)
                } else {
                    val single = IgngJson.instance.decodeFromString<GitHubReleaseDto>(bodyStr)
                    if (!single.draft && !single.prerelease && GitHubReleaseParser.findTargetApkAsset(single, config) != null) {
                        single
                    } else {
                        null
                    }
                }
            }.getOrElse {
                return@withContext UpdateSourceOutcome.Failure("解析版本信息失败，请稍后重试。")
            } ?: return@withContext UpdateSourceOutcome.NoRelease

            // Optional enrichment: fetch machine-readable update-manifest.json if attached
            val manifest = selectedDto.assets
                .firstOrNull {
                    it.name.equals(GitHubReleaseParser.MANIFEST_ASSET_NAME, ignoreCase = true) &&
                        GitHubReleaseParser.isAllowedDownloadUrl(it.browserDownloadUrl)
                }
                ?.let { fetchSmallText(it.browserDownloadUrl, config.userAgent, maxBytes = 128 * 1024) }
                ?.let { raw -> runCatching { IgngJson.instance.decodeFromString<UpdateManifestDto>(raw) }.getOrNull() }

            // Fallback checksum enrichment: fetch SHA256SUMS.txt only if neither manifest nor asset digest has sha256
            val targetApk = GitHubReleaseParser.findTargetApkAsset(selectedDto, config)
            val alreadyHasSha = GitHubReleaseParser.normalizeSha256(manifest?.apps?.get(config.appKey)?.sha256) != null ||
                GitHubReleaseParser.normalizeSha256(targetApk?.digest) != null
            val shaSums = if (!alreadyHasSha) {
                selectedDto.assets
                    .firstOrNull {
                        it.name.equals(GitHubReleaseParser.SHA256SUMS_ASSET_NAME, ignoreCase = true) &&
                            GitHubReleaseParser.isAllowedDownloadUrl(it.browserDownloadUrl)
                    }
                    ?.let { fetchSmallText(it.browserDownloadUrl, config.userAgent, maxBytes = 32 * 1024) }
                    ?.let(GitHubReleaseParser::parseSha256Sums)
                    .orEmpty()
            } else {
                emptyMap()
            }

            val resolved = GitHubReleaseParser.resolveRelease(
                dto = selectedDto,
                manifest = manifest,
                sha256Sums = shaSums,
                config = config,
            ) ?: return@withContext UpdateSourceOutcome.NoRelease

            UpdateSourceOutcome.Success(release = resolved, etag = etag)
        }
    }

    private fun fetchSmallText(url: String, userAgent: String, maxBytes: Int): String? {
        if (!GitHubReleaseParser.isAllowedDownloadUrl(url)) return null
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body ?: return@use null
                if (body.contentLength() > maxBytes) return@use null
                body.string().takeIf { it.length <= maxBytes }
            }
        }.getOrNull()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
