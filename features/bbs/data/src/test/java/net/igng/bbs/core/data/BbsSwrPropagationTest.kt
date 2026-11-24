package net.igng.bbs.core.data

import net.igng.bbs.core.model.BbsHomeDto
import net.igng.bbs.core.model.InboxItemDto
import net.igng.bbs.core.model.InboxResponseDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.PostDetailDto
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.bbs.core.model.PostViewerStateDto
import net.igng.bbs.core.model.RecommendationSlotDto
import net.igng.bbs.core.model.UnreadCountsDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.IgngSwrCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BbsSwrPropagationTest {

    @Test
    fun propagatePostLikeUpdatesDetailHomeAndFeeds() {
        val cache = IgngSwrCache()
        val postSummary = PostSummaryDto(id = "post_42", title = "Demo", likeCount = 10, commentCount = 3)
        cache.put(
            key = BbsCacheKeys.postDetail("post_42"),
            data = PostDetailDto(
                id = "post_42",
                title = "Demo",
                likeCount = 10,
                commentCount = 3,
                viewerState = PostViewerStateDto(isLiked = false),
            ),
            scope = IgngCacheScope.SESSION_AWARE,
            tags = setOf(BbsCacheTags.POSTS),
        )
        cache.put(
            key = BbsCacheKeys.HOME,
            data = BbsHomeDto(
                latestPosts = listOf(postSummary),
                recommendations = listOf(RecommendationSlotDto(post = postSummary)),
            ),
            scope = IgngCacheScope.SESSION_AWARE,
            tags = setOf(BbsCacheTags.HOME),
        )
        cache.put(
            key = BbsCacheKeys.feed(null, "latest"),
            data = PaginatedResponseDto(data = listOf(postSummary)),
            scope = IgngCacheScope.SESSION_AWARE,
            tags = setOf(BbsCacheTags.FEED, BbsCacheTags.POST_LISTS),
        )

        propagatePostLike(cache, "post_42", liked = true, likeCount = 11)

        val detail = cache.peek<PostDetailDto>(BbsCacheKeys.postDetail("post_42"))!!.data
        assertTrue(detail.viewerState.isLiked)
        assertEquals(11, detail.likeCount)

        val home = cache.peek<BbsHomeDto>(BbsCacheKeys.HOME)!!.data
        assertEquals(11, home.latestPosts.single().likeCount)
        assertEquals(11, home.recommendations.single().post.likeCount)

        val feed = cache.peek<PaginatedResponseDto<PostSummaryDto>>(BbsCacheKeys.feed(null, "latest"))!!.data
        assertEquals(11, feed.data.single().likeCount)
    }

    @Test
    fun propagatePostCommentCountDeltaUpdatesAndClampsAtZero() {
        val cache = IgngSwrCache()
        val summary = PostSummaryDto(id = "post_9", commentCount = 1)
        cache.put(
            key = BbsCacheKeys.postDetail("post_9"),
            data = PostDetailDto(id = "post_9", commentCount = 1),
            tags = setOf(BbsCacheTags.POSTS),
        )
        cache.put(
            key = BbsCacheKeys.feed("tech", "hot"),
            data = PaginatedResponseDto(data = listOf(summary)),
            tags = setOf(BbsCacheTags.FEED, BbsCacheTags.POST_LISTS),
        )

        propagatePostCommentCountDelta(cache, "post_9", +1)
        assertEquals(2, cache.peek<PostDetailDto>(BbsCacheKeys.postDetail("post_9"))!!.data.commentCount)
        assertEquals(2, cache.peek<PaginatedResponseDto<PostSummaryDto>>(BbsCacheKeys.feed("tech", "hot"))!!.data.data.single().commentCount)

        propagatePostCommentCountDelta(cache, "post_9", -5)
        assertEquals(0, cache.peek<PostDetailDto>(BbsCacheKeys.postDetail("post_9"))!!.data.commentCount)
        assertEquals(0, cache.peek<PaginatedResponseDto<PostSummaryDto>>(BbsCacheKeys.feed("tech", "hot"))!!.data.data.single().commentCount)
    }

    @Test
    fun propagateInboxItemReadDecrementsUnreadOnce() {
        val cache = IgngSwrCache()
        val key = BbsCacheKeys.inbox("all", unreadOnly = false, query = "")
        cache.put(
            key = key,
            data = InboxResponseDto(
                data = listOf(
                    InboxItemDto(id = "n1", title = "Unread", isRead = false),
                    InboxItemDto(id = "n2", title = "AlreadyRead", isRead = true),
                ),
                unread = UnreadCountsDto(total = 2),
            ),
            scope = IgngCacheScope.USER_ONLY,
            tags = setOf(BbsCacheTags.INBOX),
        )

        propagateInboxItemRead(cache, "n1")
        val afterFirst = cache.peek<InboxResponseDto>(key, IgngCacheScope.USER_ONLY)!!.data
        assertTrue(afterFirst.data.first { it.id == "n1" }.isRead)
        assertEquals(1, afterFirst.unread.total)

        // Calling again on already-read item must not decrement a second time
        propagateInboxItemRead(cache, "n1")
        val afterSecond = cache.peek<InboxResponseDto>(key, IgngCacheScope.USER_ONLY)!!.data
        assertEquals(1, afterSecond.unread.total)
    }
}
