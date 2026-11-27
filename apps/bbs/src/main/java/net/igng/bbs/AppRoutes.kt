package net.igng.bbs

import android.net.Uri
import kotlinx.serialization.Serializable
import net.igng.bbs.core.model.BbsV1Id

/**
 * Type-safe `@Serializable` Navigation routes for IGNG BBS (aligned with IGNGmc `AppRoute`).
 */
sealed interface BbsRoute {
    // 4 primary floating dock tabs
    @Serializable data object Feed : BbsRoute
    @Serializable data object Columns : BbsRoute
    @Serializable data object Creator : BbsRoute
    @Serializable data object Profile : BbsRoute

    // Discovery & Reading
    @Serializable data object Search : BbsRoute
    @Serializable data object Rules : BbsRoute
    @Serializable data object Inbox : BbsRoute
    @Serializable data class Category(val slug: String, val title: String) : BbsRoute
    @Serializable data class Tag(val tag: String) : BbsRoute
    @Serializable data class ColumnDetail(val columnId: String) : BbsRoute
    @Serializable data class PostDetail(val postId: String) : BbsRoute
    @Serializable data class UserProfile(val userId: String) : BbsRoute

    // Creator studio
    @Serializable data class CreatorEditor(val postId: String? = null, val columnId: String? = null) : BbsRoute
    @Serializable data object CreatorPosts : BbsRoute
    @Serializable data object CreatorColumns : BbsRoute
    @Serializable data object CreatorSeries : BbsRoute
    @Serializable data object CreatorComments : BbsRoute
    @Serializable data object CreatorReviews : BbsRoute
    @Serializable data object CreatorMedia : BbsRoute

    // Profile, Account & Settings
    @Serializable data object Favorites : BbsRoute
    @Serializable data object Following : BbsRoute
    @Serializable data object History : BbsRoute
    @Serializable data object AccountSecurity : BbsRoute
    @Serializable data object PrivacyPreferences : BbsRoute
    @Serializable data object AppSettings : BbsRoute
    @Serializable data object LoginDevices : BbsRoute
    @Serializable data object AdminConsole : BbsRoute
}

object AppRoutes {
    /**
     * Maps Android App Links (`https://www.igngbbs.net/...`) and app shortcuts (`igngbbs://open/...`)
     * into type-safe [BbsRoute] destinations. Returns null for unrecognized URLs or OAuth callbacks.
     */
    fun resolveDeepLink(uri: Uri?): BbsRoute? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val segments = uri.pathSegments.orEmpty()

        if (scheme == "igngbbs") {
            if (host == "auth") return null // handled by IgngSessionManager
            if (host == "open") {
                return when (segments.firstOrNull()?.lowercase()) {
                    "search" -> BbsRoute.Search
                    "editor" -> BbsRoute.CreatorEditor()
                    "inbox" -> BbsRoute.Inbox
                    "rules" -> BbsRoute.Rules
                    else -> null
                }
            }
            if (host == "post" && segments.isNotEmpty()) {
                val id = segments.first().toIntOrNull()?.let { BbsV1Id.post(it) } ?: segments.first()
                return BbsRoute.PostDetail(id)
            }
            return null
        }

        if ((scheme == "https" || scheme == "http") && (host == "www.igngbbs.net" || host == "igngbbs.net")) {
            val first = segments.getOrNull(0)?.lowercase() ?: return null
            val second = segments.getOrNull(1)
            return when (first) {
                "post" -> second?.let { raw ->
                    val normalized = raw.toIntOrNull()?.let { BbsV1Id.post(it) } ?: raw
                    BbsRoute.PostDetail(normalized)
                }
                "user" -> second?.let { BbsRoute.UserProfile(it) }
                "columns" -> if (second != null) BbsRoute.ColumnDetail(second) else BbsRoute.Columns
                "tags" -> second?.let { BbsRoute.Tag(it) }
                "pages" -> if (second?.lowercase() == "rules") BbsRoute.Rules else null
                else -> null
            }
        }
        return null
    }
}
