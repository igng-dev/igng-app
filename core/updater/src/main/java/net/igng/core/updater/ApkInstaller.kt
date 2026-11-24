package net.igng.core.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.File

interface UpdateApkInstaller {
    fun canRequestPackageInstalls(): Boolean
    fun openUnknownSourcesSettings(): Result<Unit>
    fun launchInstall(apkFile: File): Result<Unit>
}

class AndroidApkInstaller(
    context: Context,
) : UpdateApkInstaller {
    private val appContext = context.applicationContext

    override fun canRequestPackageInstalls(): Boolean =
        runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(true)

    override fun openUnknownSourcesSettings(): Result<Unit> = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    override fun launchInstall(apkFile: File): Result<Unit> = runCatching {
        val contentUri = IgngUpdateFileProvider.uriForApk(appContext, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
