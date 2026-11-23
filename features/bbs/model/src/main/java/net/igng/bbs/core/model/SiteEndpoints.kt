package net.igng.bbs.core.model

import net.igng.core.model.IgngCoreEndpoints

data class SiteEndpoints(
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val ssoBaseUrl: String = DEFAULT_SSO_BASE_URL,
    val bbsWebBaseUrl: String = DEFAULT_BBS_BASE_URL,
) : IgngCoreEndpoints {
    override val apiRoot: String get() = apiBaseUrl.trimEnd('/')
    override val ssoRoot: String get() = ssoBaseUrl.trimEnd('/')
    val bbsWebRoot: String get() = bbsWebBaseUrl.trimEnd('/')

    fun resolveMediaUrl(rawUrl: String?, postId: String? = null): String? {
        val value = rawUrl?.trim().orEmpty()
        if (value.isEmpty()) return null
        val legacyMatch = LEGACY_MEDIA_RE.find(value)
        if (legacyMatch != null) {
            val hex = legacyMatch.groupValues[1].lowercase()
            val query = if (!postId.isNullOrBlank()) "?post_id=$postId" else ""
            return "$apiV1Base/media/med_$hex/content$query"
        }
        if (value.startsWith("https://") || value.startsWith("http://")) {
            return if (postId != null && value.contains("/api/v1/media/") && !value.contains("post_id=")) {
                val sep = if ('?' in value) '&' else '?'
                "$value${sep}post_id=$postId"
            } else {
                value
            }
        }
        if (value.startsWith("/api/v1/")) return "$apiRoot$value"
        if (value.startsWith("/")) return "$bbsWebRoot$value"
        return value
    }

    fun postWebUrl(numericId: Int): String = "$bbsWebRoot/post/$numericId"
    fun columnWebUrl(slugOrId: String): String = "$bbsWebRoot/columns/$slugOrId"
    fun userWebUrl(idOrUsername: String): String = "$bbsWebRoot/user/$idOrUsername"

    companion object {
        const val DEFAULT_API_BASE_URL = "https://www.igng.net"
        const val DEFAULT_SSO_BASE_URL = "https://sso.igng.net"
        const val DEFAULT_BBS_WEB_BASE_URL = "https://www.igngbbs.net"
        const val DEFAULT_BBS_BASE_URL = DEFAULT_BBS_WEB_BASE_URL
        const val CLIENT_ID = "igng_bbs_android"
        const val OAUTH_REDIRECT_URI = "igngbbs://auth/callback"
        private val LEGACY_MEDIA_RE = Regex("/api/media/files/([a-fA-F0-9]{32})/content")

        fun extractHost(url: String): String? = IgngCoreEndpoints.extractHost(url)
    }
}
