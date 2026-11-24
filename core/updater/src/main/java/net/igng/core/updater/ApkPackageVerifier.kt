package net.igng.core.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest
import net.igng.core.model.AppUpdateRelease
import net.igng.core.model.IgngSemVer

sealed interface ApkVerifyResult {
    data object Valid : ApkVerifyResult

    data class Failed(
        val message: String,
        val isSignatureMismatch: Boolean = false,
    ) : ApkVerifyResult
}

interface UpdateApkVerifier {
    fun verify(
        apkFile: File,
        release: AppUpdateRelease,
        currentVersionCode: Int,
        currentVersionName: String,
        computedSha256: String? = null,
    ): ApkVerifyResult
}

/**
 * Four-stage pre-install APK safety verifier:
 * 1. File integrity (SHA-256 digest vs release metadata)
 * 2. Package identity (`archive.packageName == context.packageName`)
 * 3. Monotonic upgrade check (prevents accidental downgrade)
 * 4. Signing certificate consistency (official cert check + local installed cert compatibility)
 */
class AndroidApkPackageVerifier(
    context: Context,
) : UpdateApkVerifier {
    private val appContext = context.applicationContext

    override fun verify(
        apkFile: File,
        release: AppUpdateRelease,
        currentVersionCode: Int,
        currentVersionName: String,
        computedSha256: String?,
    ): ApkVerifyResult {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return ApkVerifyResult.Failed("安装包文件不存在或已损坏，请重新下载。")
        }

        // 1. SHA-256 integrity check
        val expectedSha = GitHubReleaseParser.normalizeSha256(release.sha256)
        if (expectedSha != null) {
            val actualSha = computedSha256?.lowercase() ?: runCatching {
                OkHttpUpdateDownloader.computeFileSha256(apkFile)
            }.getOrNull()
            if (actualSha == null || actualSha != expectedSha) {
                return ApkVerifyResult.Failed("安装包 SHA-256 校验未通过，文件可能不完整，请重新下载。")
            }
        }

        // 2. Parse APK archive metadata & signing certificates
        val archiveInfo = readArchivePackageInfo(apkFile)
            ?: return ApkVerifyResult.Failed("无法解析安装包内容，文件可能已损坏，请重新下载。")

        val expectedPackage = appContext.packageName
        if (archiveInfo.packageName != expectedPackage) {
            return ApkVerifyResult.Failed(
                "安装包应用标识不匹配（预期 $expectedPackage，实际 ${archiveInfo.packageName}），已阻止安装。",
            )
        }

        // 3. Monotonic upgrade validation
        val archiveVersionCode = PackageInfoCompat.getLongVersionCode(archiveInfo).toInt()
        val archiveVersionName = archiveInfo.versionName.orEmpty().ifBlank { release.versionName }
        if (!IgngSemVer.isNewerRelease(
                candidateVersionCode = archiveVersionCode,
                candidateVersionName = archiveVersionName,
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
            )
        ) {
            return ApkVerifyResult.Failed(
                "下载的安装包版本（v$archiveVersionName / $archiveVersionCode）不高于当前已安装版本，无需重复安装。",
            )
        }

        // 4. Signing certificate verification
        val archiveCerts = extractCertSha256Set(archiveInfo)
        val expectedCert = GitHubReleaseParser.normalizeSha256(release.certSha256)
        if (expectedCert != null && archiveCerts.isNotEmpty() && expectedCert !in archiveCerts) {
            return ApkVerifyResult.Failed("安装包签名证书与官方发布指纹不一致，已阻止安装以保护安全。")
        }

        val installedCerts = readInstalledCertSha256Set()
        if (installedCerts.isNotEmpty() && archiveCerts.isNotEmpty() && installedCerts.intersect(archiveCerts).isEmpty()) {
            return ApkVerifyResult.Failed(
                message = "当前安装的版本（如开发测试版）签名与官方正式版不一致，系统无法直接覆盖安装。请先卸载当前版本后再从发布页安装正式版。",
                isSignatureMismatch = true,
            )
        }

        return ApkVerifyResult.Valid
    }

    @Suppress("DEPRECATION")
    private fun readArchivePackageInfo(apkFile: File): PackageInfo? = runCatching {
        val pm = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun readInstalledCertSha256Set(): Set<String> = runCatching {
        val pm = appContext.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(appContext.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(appContext.packageName, flags)
        }
        extractCertSha256Set(info)
    }.getOrDefault(emptySet())

    @Suppress("DEPRECATION")
    private fun extractCertSha256Set(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners.orEmpty()
                else -> signingInfo.signingCertificateHistory.orEmpty()
            }
        } else {
            info.signatures.orEmpty()
        }
        if (signatures.isEmpty()) return emptySet()
        return signatures.mapNotNull { sig ->
            runCatching {
                MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).toLowerHex()
            }.getOrNull()
        }.toSet()
    }
}
