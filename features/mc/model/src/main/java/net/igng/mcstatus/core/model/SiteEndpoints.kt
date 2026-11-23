package net.igng.mcstatus.core.model

import net.igng.core.model.IgngCoreEndpoints

/**
 * Helpers for IGNG Internal API v1 typed prefixed identifiers (`srv_3`, `tk_2048`, `usr_1024`, `land_4091`).
 */

data class SiteEndpoints(
    val apiBaseUrl: String = "https://www.igng.net",
    val mcBaseUrl: String = "https://mc.igng.net",
    val dashBaseUrl: String = "https://dash.igng.net",
    val ssoBaseUrl: String = "https://sso.igng.net",
    val chatBaseUrl: String = "https://chat.igng.net",
) : IgngCoreEndpoints {
    /** Unified first-party Client API v1 gateway (`https://www.igng.net`). */
    val api: String = apiBaseUrl.trimEnd('/')
    override val apiRoot: String get() = api
    /** Web-only origins (Custom Tabs / World Map WebView / static snapshots). Never send API requests here. */
    val mc: String = mcBaseUrl.trimEnd('/')
    val dash: String = dashBaseUrl.trimEnd('/')
    val sso: String = ssoBaseUrl.trimEnd('/')
    override val ssoRoot: String get() = sso
    val chat: String = chatBaseUrl.trimEnd('/')

    /** First-party cached Minecraft player head avatar proxy on the unified v1 gateway. */
    fun mcAvatarUrl(username: String, size: Int = 64): String {
        val encoded = java.net.URLEncoder.encode(username.trim(), "UTF-8").replace("+", "%20")
        val clamped = size.coerceIn(16, 256)
        return "$api/api/v1/mc/avatars/$encoded?size=$clamped"
    }

    fun snapshotMapUrls(seasonSlug: String): List<String> = listOf(
        "$mc/map-snapshots/$seasonSlug.webp",
        "$mc/map-snapshots/$seasonSlug.png",
    )

    fun liveMapWebUrl(familySlug: String, seasonSlug: String? = null): String =
        if (seasonSlug.isNullOrBlank()) "$mc/map/$familySlug" else "$mc/map/$familySlug/$seasonSlug"
}
