package net.igng.core.updater

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Dedicated [FileProvider] subclass for sharing verified self-update APKs with the Android
 * package installer without conflicting with any other FileProvider declarations.
 */
class IgngUpdateFileProvider : FileProvider() {
    companion object {
        fun authorityFor(context: Context): String = "${context.packageName}.igng.update.fileprovider"

        fun uriForApk(context: Context, apkFile: File): Uri =
            getUriForFile(context, authorityFor(context), apkFile)
    }
}
