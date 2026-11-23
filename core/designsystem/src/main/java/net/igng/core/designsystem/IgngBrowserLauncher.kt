package net.igng.core.designsystem

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Unified Chrome Custom Tabs launcher for external links across all IGNG Android apps.
 * Lives in `:core:designsystem` so feature UI modules do not need to depend on `:core:auth`.
 */
object IgngBrowserLauncher {
    private const val TOOLBAR_DARK = 0xFF0B0F16.toInt()
    private const val TOOLBAR_LIGHT = 0xFFF4F6FA.toInt()

    fun openExternalUrl(context: Context, url: String, isDark: Boolean = false) {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https" && scheme != "http") return
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(if (isDark) TOOLBAR_DARK else TOOLBAR_LIGHT)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        runCatching {
            intent.launchUrl(context, uri)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }
}
