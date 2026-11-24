package net.igng.core.model

import kotlinx.serialization.Serializable

/**
 * Scope describing how a unified IGNG family release impacts a specific application.
 */
@Serializable
enum class AppUpdateChangeScope(
    val wire: String,
    val badgeLabel: String,
    val affectsApp: Boolean,
) {
    INITIAL("initial", "首个统一版本", true),
    APP_AND_CORE("app_and_core", "本应用与底座更新", true),
    APP_ONLY("app_only", "本应用更新", true),
    CORE_ONLY("core_only", "共享底座更新", true),
    UNCHANGED("unchanged", "家族同步版本", false),
    UNKNOWN("unknown", "常规更新", true),
    ;

    companion object {
        fun fromWire(raw: String?): AppUpdateChangeScope {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.wire == normalized } ?: UNKNOWN
        }
    }
}

/**
 * Per-app immutable updater configuration injected from the application shell so `:core:updater`
 * stays strictly domain-agnostic.
 */
data class AppUpdateConfig(
    val appKey: String,
    val appName: String,
    val packageName: String,
    val currentVersionName: String,
    val currentVersionCode: Int,
    val assetPrefix: String,
    val userAgent: String,
    val githubOwner: String = DEFAULT_GITHUB_OWNER,
    val githubRepo: String = DEFAULT_GITHUB_REPO,
) {
    val releasesPageUrl: String
        get() = "https://github.com/$githubOwner/$githubRepo/releases"

    companion object {
        const val DEFAULT_GITHUB_OWNER = "igng-dev"
        const val DEFAULT_GITHUB_REPO = "igng-app"
    }
}

/**
 * Normalized release descriptor for a single target IGNG Android app.
 */
@Serializable
data class AppUpdateRelease(
    val tagName: String,
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val changelog: String,
    val publishedAt: String? = null,
    val isPrerelease: Boolean = false,
    val assetName: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val sizeBytes: Long = 0L,
    val sha256: String? = null,
    val certSha256: String? = null,
    val changeScope: AppUpdateChangeScope = AppUpdateChangeScope.UNKNOWN,
) {
    val shouldNotifySilently: Boolean
        get() = changeScope.affectsApp
}

enum class AppUpdateErrorPhase {
    CHECK,
    DOWNLOAD,
    VERIFY,
    INSTALL,
}

sealed interface AppUpdateState {
    data object Idle : AppUpdateState

    data class Checking(val manual: Boolean) : AppUpdateState

    data class UpToDate(
        val checkedAtMs: Long,
        val currentVersionName: String,
        val currentVersionCode: Int,
        val latestTag: String? = null,
        val skippedSyncTag: String? = null,
    ) : AppUpdateState

    data class Available(
        val release: AppUpdateRelease,
        val hasVerifiedCachedApk: Boolean = false,
        val cachedApkPath: String? = null,
        val isSilentDiscovery: Boolean = false,
    ) : AppUpdateState

    data class Downloading(
        val release: AppUpdateRelease,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0L,
    ) : AppUpdateState {
        val progress: Float?
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class Verifying(
        val release: AppUpdateRelease,
    ) : AppUpdateState

    data class PermissionRequired(
        val release: AppUpdateRelease,
        val apkPath: String,
    ) : AppUpdateState

    data class ReadyToInstall(
        val release: AppUpdateRelease,
        val apkPath: String,
    ) : AppUpdateState

    data class Failed(
        val phase: AppUpdateErrorPhase,
        val message: String,
        val release: AppUpdateRelease? = null,
        val fallbackUrl: String? = null,
        val isSignatureMismatch: Boolean = false,
    ) : AppUpdateState
}

/**
 * UI-ready snapshot observed by both Settings screens and subtle entry-point update badges.
 */
data class AppUpdateUiSnapshot(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val state: AppUpdateState = AppUpdateState.Idle,
    val includePrerelease: Boolean = false,
    val lastCheckedAtMs: Long? = null,
    val releasesPageUrl: String = "https://github.com/${AppUpdateConfig.DEFAULT_GITHUB_OWNER}/${AppUpdateConfig.DEFAULT_GITHUB_REPO}/releases",
) {
    val activeRelease: AppUpdateRelease?
        get() = when (val s = state) {
            is AppUpdateState.Available -> s.release
            is AppUpdateState.Downloading -> s.release
            is AppUpdateState.Verifying -> s.release
            is AppUpdateState.PermissionRequired -> s.release
            is AppUpdateState.ReadyToInstall -> s.release
            is AppUpdateState.Failed -> s.release
            else -> null
        }

    /**
     * Lightweight badge indicator for entry points (Settings button / row).
     * Never alerts when a release is family-sync-only (`changeScope == UNCHANGED`).
     */
    val hasBadgeAlert: Boolean
        get() = when (val s = state) {
            is AppUpdateState.Available -> s.release.shouldNotifySilently
            is AppUpdateState.Downloading -> true
            is AppUpdateState.Verifying -> true
            is AppUpdateState.PermissionRequired -> true
            is AppUpdateState.ReadyToInstall -> true
            else -> false
        }
}

/**
 * Strict SemVer & unified `major * 10000 + minor * 100 + patch` version helper aligned with
 * root `gradle.properties` and `scripts/release_utils.py`.
 */
object IgngSemVer {
    private val VERSION_REGEX = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?$""")

    data class Parsed(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val prerelease: List<String> = emptyList(),
    ) : Comparable<Parsed> {
        val isPrerelease: Boolean get() = prerelease.isNotEmpty()

        val canonicalName: String
            get() = buildString {
                append(major).append('.').append(minor).append('.').append(patch)
                if (prerelease.isNotEmpty()) {
                    append('-').append(prerelease.joinToString("."))
                }
            }

        /**
         * Stable versionCode formula (`major * 10000 + minor * 100 + patch`), or `null` if
         * `minor >= 100` / `patch >= 100`.
         */
        val inferredStableVersionCode: Int?
            get() = if (minor in 0..99 && patch in 0..99) {
                major * 10_000 + minor * 100 + patch
            } else {
                null
            }

        override fun compareTo(other: Parsed): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            if (patch != other.patch) return patch.compareTo(other.patch)

            if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
            if (prerelease.isEmpty()) return 1 // stable > prerelease
            if (other.prerelease.isEmpty()) return -1

            val maxLen = maxOf(prerelease.size, other.prerelease.size)
            for (i in 0 until maxLen) {
                val left = prerelease.getOrNull(i) ?: return -1
                val right = other.prerelease.getOrNull(i) ?: return 1
                if (left == right) continue
                val leftNum = left.toLongOrNull()
                val rightNum = right.toLongOrNull()
                return when {
                    leftNum != null && rightNum != null -> leftNum.compareTo(rightNum)
                    leftNum != null -> -1 // numeric identifiers have lower precedence than non-numeric
                    rightNum != null -> 1
                    else -> left.compareTo(right)
                }
            }
            return 0
        }
    }

    fun parse(raw: String?): Parsed? {
        val trimmed = raw?.trim().orEmpty()
        val match = VERSION_REGEX.matchEntire(trimmed) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].toIntOrNull() ?: return null
        val preRaw = match.groupValues[4]
        val pre = if (preRaw.isEmpty()) emptyList() else preRaw.split('.').filter { it.isNotEmpty() }
        return Parsed(major = major, minor = minor, patch = patch, prerelease = pre)
    }

    fun inferVersionCode(versionOrTag: String?): Int? = parse(versionOrTag)?.inferredStableVersionCode

    fun compareNames(a: String?, b: String?): Int {
        val pa = parse(a)
        val pb = parse(b)
        if (pa != null && pb != null) return pa.compareTo(pb)
        return a.orEmpty().compareTo(b.orEmpty())
    }

    /**
     * Determines whether a candidate release is strictly newer than the installed build while
     * respecting Android's monotonic `versionCode` upgrade rule (no downgrades).
     */
    fun isNewerRelease(
        candidateVersionCode: Int,
        candidateVersionName: String,
        currentVersionCode: Int,
        currentVersionName: String,
    ): Boolean {
        if (candidateVersionCode > 0 && currentVersionCode > 0) {
            if (candidateVersionCode > currentVersionCode) return true
            if (candidateVersionCode < currentVersionCode) return false
            // Same versionCode (e.g., rc -> final sharing target stable code): fall back to SemVer
            return compareNames(candidateVersionName, currentVersionName) > 0
        }
        return compareNames(candidateVersionName, currentVersionName) > 0
    }
}
