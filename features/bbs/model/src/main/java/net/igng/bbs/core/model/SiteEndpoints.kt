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

    fun resolvePostMediaUrl(
        rawUrl: String,
        postId: String? = null,
        mediaUrls: Map<String, String> = emptyMap(),
    ): String {
        val direct = mediaUrls[rawUrl]
        if (!direct.isNullOrBlank()) return direct
        val hex = extractMediaHexId(rawUrl)
        if (hex != null) {
            val mapped = mediaUrls[hex]
            if (!mapped.isNullOrBlank()) return mapped
        }
        return resolveMediaUrl(rawUrl, postId) ?: rawUrl
    }

    fun resolvePostMediaAspectRatio(
        rawUrl: String?,
        mediaMeta: Map<String, MediaMetaDto>,
    ): Float? {
        if (rawUrl.isNullOrBlank() || mediaMeta.isEmpty()) return null
        mediaMeta[rawUrl]?.aspectRatio?.let { return it }
        val hex = extractMediaHexId(rawUrl) ?: return null
        return mediaMeta[hex]?.aspectRatio
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
        private val RAW_HEX_ID_RE = Regex("^[a-fA-F0-9]{32}$")
        private val MANAGED_MEDIA_HEX_RE = Regex(
            "(?:^|/)(?:i/|p/|api/media/posts/\\d+/|api/media/files/|api/v1/media/med_)([a-fA-F0-9]{32})(?:\\.(?:webp|png|jpe?g|gif|avif)|/content)?(?=[?/#]|$)",
            RegexOption.IGNORE_CASE,
        )

        fun extractMediaHexId(rawUrl: String?): String? {
            val value = rawUrl?.trim().orEmpty()
            if (value.isEmpty()) return null
            if (RAW_HEX_ID_RE.matches(value)) return value.lowercase()
            return MANAGED_MEDIA_HEX_RE.find(value)?.groupValues?.getOrNull(1)?.lowercase()
        }

        fun extractHost(url: String): String? = IgngCoreEndpoints.extractHost(url)
    }
}

object BbsV1Id {
    fun post(numericId: Int): String = net.igng.core.model.V1Id.format("post", numericId)
    fun column(numericId: Int): String = net.igng.core.model.V1Id.format("col", numericId)
    fun series(numericId: Int): String = net.igng.core.model.V1Id.format("ser", numericId)
    fun comment(numericId: Int): String = net.igng.core.model.V1Id.format("cmt", numericId)
}
