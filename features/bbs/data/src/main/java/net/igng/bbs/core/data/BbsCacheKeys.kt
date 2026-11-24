package net.igng.bbs.core.data

import kotlinx.serialization.builtins.ListSerializer
import net.igng.bbs.core.model.BbsBootstrapDto
import net.igng.bbs.core.model.BbsHomeDto
import net.igng.bbs.core.model.BbsRulesDto
import net.igng.bbs.core.model.ColumnSummaryDto
import net.igng.bbs.core.model.ListWrapperDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.bbs.core.model.TagStatDto
import net.igng.core.data.IgngFreshnessPolicy
import net.igng.core.data.jsonDiskCodec

object BbsCacheTags {
    const val BOOTSTRAP = "bbs_bootstrap"
    const val HOME = "bbs_home"
    const val FEED = "bbs_feed"
    const val POSTS = "bbs_posts"
    const val POST_LISTS = "bbs_post_lists"
    const val COMMENTS = "bbs_comments"
    const val COLUMNS = "bbs_columns"
    const val INBOX = "bbs_inbox"
    const val CREATOR = "bbs_creator"
    const val FAVORITES = "bbs_favorites"
    const val FOLLOWING = "bbs_following"
    const val USER_PROFILES = "bbs_user_profiles"
    const val SETTINGS = "bbs_settings"
    const val DEVICES = "bbs_devices"
    const val ADMIN = "bbs_admin"
    const val TAGS = "bbs_tags"
    const val RULES = "bbs_rules"
}

object BbsCacheKeys {
    const val BOOTSTRAP = "bbs:bootstrap"
    const val HOME = "bbs:home"
    const val TAGS = "bbs:tags"
    const val RULES = "bbs:rules"

    fun feed(categorySlug: String?, sort: String, excludeSeries: Boolean = false): String {
        val scope = categorySlug.orEmpty().ifBlank { "all" }
        val suffix = if (excludeSeries) ":noser" else ""
        return "bbs:feed:$scope:$sort$suffix"
    }

    fun tagFeed(tag: String, sort: String): String =
        "bbs:tag:${tag.trim().lowercase()}:$sort"

    fun columns(mine: Boolean, categoryId: String?, query: String): String =
        "bbs:columns:${if (mine) "mine" else "all"}:${categoryId.orEmpty()}:${query.trim().lowercase()}"

    fun columnDetail(columnIdOrSlug: String): String = "bbs:column:$columnIdOrSlug"

    fun postDetail(postId: String): String = "bbs:post:$postId"

    fun postComments(postId: String, sortBy: String): String = "bbs:post:$postId:comments:$sortBy"

    fun inbox(tab: String, unreadOnly: Boolean, query: String): String =
        "bbs:inbox:$tab:$unreadOnly:${query.trim().lowercase()}"

    const val CREATOR_OVERVIEW = "bbs:creator:overview"
    fun creatorPosts(filter: String): String = "bbs:creator:posts:$filter"
    const val CREATOR_INVITES = "bbs:creator:invites"
    const val CREATOR_COLUMNS = "bbs:creator:columns"
    const val CREATOR_COLUMN_EVENTS = "bbs:creator:column_events"
    const val CREATOR_SERIES = "bbs:creator:series"
    const val CREATOR_COMMENTS = "bbs:creator:comments"
    fun creatorReviews(includePassed: Boolean): String = "bbs:creator:reviews:$includePassed"
    const val CREATOR_MEDIA = "bbs:creator:media"

    fun userProfile(userIdOrUsername: String): String = "bbs:user:$userIdOrUsername"
    const val FAVORITES = "bbs:favorites"
    fun following(tab: String): String = "bbs:following:$tab"
    const val MY_SETTINGS = "bbs:my_settings"
    const val DEVICES = "bbs:devices"

    fun adminPosts(query: String): String = "bbs:admin:posts:${query.trim().lowercase()}"
    const val ADMIN_REVIEWS = "bbs:admin:reviews"
    const val ADMIN_REPORTS = "bbs:admin:reports"
    const val ADMIN_RISK = "bbs:admin:risk"
    fun adminUsers(query: String): String = "bbs:admin:users:${query.trim().lowercase()}"
    fun adminCollections(query: String): String = "bbs:admin:collections:${query.trim().lowercase()}"
}

object BbsCachePolicies {
    val Realtime = IgngFreshnessPolicy(freshTtlMs = 20_000L)
    val Bootstrap = IgngFreshnessPolicy(freshTtlMs = 45_000L)
    val Standard = IgngFreshnessPolicy(freshTtlMs = 60_000L)
    val Settings = IgngFreshnessPolicy(freshTtlMs = 90_000L)
    val Detail = IgngFreshnessPolicy(freshTtlMs = 180_000L)
    val Static = IgngFreshnessPolicy(freshTtlMs = 900_000L)
    val Rules = IgngFreshnessPolicy(freshTtlMs = 1_800_000L)
}

object BbsDiskCodecs {
    val BOOTSTRAP = jsonDiskCodec("bbs_bootstrap", BbsBootstrapDto.serializer())
    val HOME = jsonDiskCodec("bbs_home_v2", BbsHomeDto.serializer())
    val DEFAULT_FEED = jsonDiskCodec(
        "bbs_feed_all_latest_noser",
        PaginatedResponseDto.serializer(PostSummaryDto.serializer()),
    )
    val DEFAULT_COLUMNS = jsonDiskCodec(
        "bbs_columns_all",
        PaginatedResponseDto.serializer(ColumnSummaryDto.serializer()),
    )
    val TAGS = jsonDiskCodec(
        "bbs_tags",
        ListWrapperDto.serializer(TagStatDto.serializer()),
    )
    val RULES = jsonDiskCodec("bbs_rules", BbsRulesDto.serializer())
}
