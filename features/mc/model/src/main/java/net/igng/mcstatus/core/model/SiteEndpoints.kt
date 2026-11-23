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

    /** Bearer tokens are ONLY attached to the unified v1 gateway and SSO origin. */
    override fun trustedAuthHosts(): Set<String> = setOf(
        hostOf(api),
        hostOf(sso),
    ).filter { it.isNotBlank() }.toSet()

    override fun apiHost(): String = hostOf(api)

    /** Authenticated v1 binary endpoint for ticket / user media attachments. */
    override fun mediaContentUrl(mediaId: String): String = "$api/api/v1/media/${mediaId.trim()}/content"

    /** First-party cached Minecraft player head avatar proxy on the unified v1 gateway. */
    fun mcAvatarUrl(username: String, size: Int = 64): String {
        val encoded = java.net.URLEncoder.encode(username.trim(), "UTF-8").replace("+", "%20")
        val clamped = size.coerceIn(16, 256)
        return "$api/api/v1/mc/avatars/$encoded?size=$clamped"
    }

    /**
     * Resolves relative `/api/v1/...` media URLs against the unified v1 gateway, and upgrades
     * known cleartext HTTP Tencent avatar URLs (e.g. `qlogo.cn` subdomains) to HTTPS so Android 9+
     * cleartext restrictions never block WeChat/QQ avatars.
     */
    fun resolveApiMediaUrl(pathOrUrl: String?): String? {
        val trimmed = pathOrUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val host = hostOf(trimmed)
            if (host == "qlogo.cn" || host.endsWith(".qlogo.cn")) {
                return "https://" + trimmed.substring(7)
            }
            return trimmed
        }
        if (trimmed.startsWith("https://", ignoreCase = true)) return trimmed
        return "$api/${trimmed.trimStart('/')}"
    }

    fun snapshotMapUrls(seasonSlug: String): List<String> = listOf(
        "$mc/map-snapshots/$seasonSlug.webp",
        "$mc/map-snapshots/$seasonSlug.png",
    )

    fun liveMapWebUrl(familySlug: String, seasonSlug: String? = null): String =
        if (seasonSlug.isNullOrBlank()) "$mc/map/$familySlug" else "$mc/map/$familySlug/$seasonSlug"

    private fun hostOf(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore('/').substringBefore(':').lowercase()
}
