package net.igng.bbs.core.data

import net.igng.bbs.core.model.AdminReviewItemDto
import net.igng.bbs.core.model.AdminRiskEntryDto
import net.igng.bbs.core.model.AdminUserRowDto
import net.igng.bbs.core.model.AppealDto
import net.igng.bbs.core.model.AppealSubmitResponseDto
import net.igng.bbs.core.model.BbsBootstrapDto
import net.igng.bbs.core.model.BbsHomeDto
import net.igng.bbs.core.model.BbsRulesDto
import net.igng.bbs.core.model.BbsUserSettingsDto
import net.igng.bbs.core.model.BlockRuleDto
import net.igng.bbs.core.model.ColumnDetailDto
import net.igng.bbs.core.model.ColumnEventDto
import net.igng.bbs.core.model.ColumnMembershipResponseDto
import net.igng.bbs.core.model.ColumnSubscriptionResponseDto
import net.igng.bbs.core.model.ColumnSummaryDto
import net.igng.bbs.core.model.CommentCreateResponseDto
import net.igng.bbs.core.model.CommentLikeResponseDto
import net.igng.bbs.core.model.CommentsListResponseDto
import net.igng.bbs.core.model.CreatorCommentDto
import net.igng.bbs.core.model.CreatorInvitesDto
import net.igng.bbs.core.model.CreatorMediaFileDto
import net.igng.bbs.core.model.CreatorMediaListDto
import net.igng.bbs.core.model.CreatorOverviewDto
import net.igng.bbs.core.model.CreatorPostDetailDto
import net.igng.bbs.core.model.CreatorReviewsResponseDto
import net.igng.bbs.core.model.CreatorSeriesDetailDto
import net.igng.bbs.core.model.FavoriteItemDto
import net.igng.bbs.core.model.FollowResponseDto
import net.igng.bbs.core.model.FollowingItemDto
import net.igng.bbs.core.model.InboxResponseDto
import net.igng.bbs.core.model.ListWrapperDto
import net.igng.bbs.core.model.PaginatedResponseDto
import net.igng.bbs.core.model.PendingInviteDto
import net.igng.bbs.core.model.PostAnalyticsDto
import net.igng.bbs.core.model.PostCollaboratorDto
import net.igng.bbs.core.model.PostDetailDto
import net.igng.bbs.core.model.PostFavoriteResponseDto
import net.igng.bbs.core.model.PostLikeResponseDto
import net.igng.bbs.core.model.PostSubmitResponseDto
import net.igng.bbs.core.model.PostSummaryDto
import net.igng.bbs.core.model.PostVersionDto
import net.igng.bbs.core.model.PostViewResponseDto
import net.igng.bbs.core.model.ReportSubmitResponseDto
import net.igng.bbs.core.model.SearchResponseDto
import net.igng.bbs.core.model.SeriesSummaryDto
import net.igng.bbs.core.model.TagStatDto
import net.igng.bbs.core.model.UserBriefDto
import net.igng.bbs.core.model.UserProfileDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.IgngSwrCache
import net.igng.core.network.ApiResult
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.IgngJson

internal fun propagatePostLike(
    swrCache: IgngSwrCache,
    postId: String,
    liked: Boolean,
    likeCount: Int,
) {
    swrCache.patch<PostDetailDto>(BbsCacheKeys.postDetail(postId)) { cur ->
        cur.copy(
            likeCount = likeCount,
            viewerState = cur.viewerState.copy(isLiked = liked),
        )
    }
    swrCache.patch<BbsHomeDto>(BbsCacheKeys.HOME) { home ->
        home.copy(
            latestPosts = home.latestPosts.map { p ->
                if (p.id == postId) p.copy(likeCount = likeCount) else p
            },
            recommendations = home.recommendations.map { slot ->
                if (slot.post.id == postId) slot.copy(post = slot.post.copy(likeCount = likeCount)) else slot
            },
        )
    }
    @Suppress("UNCHECKED_CAST")
    swrCache.patchByTag(
        tag = BbsCacheTags.POST_LISTS,
        clazz = PaginatedResponseDto::class.java as Class<PaginatedResponseDto<PostSummaryDto>>,
    ) { page ->
        if (page.data.none { it.id == postId }) page
        else page.copy(
            data = page.data.map { p -> if (p.id == postId) p.copy(likeCount = likeCount) else p },
        )
    }
}

internal fun propagatePostCommentCountDelta(
    swrCache: IgngSwrCache,
    postId: String,
    delta: Int,
) {
    if (delta == 0) return
    swrCache.patch<PostDetailDto>(BbsCacheKeys.postDetail(postId)) { cur ->
        cur.copy(commentCount = (cur.commentCount + delta).coerceAtLeast(0))
    }
    swrCache.patch<BbsHomeDto>(BbsCacheKeys.HOME) { home ->
        home.copy(
            latestPosts = home.latestPosts.map { p ->
                if (p.id == postId) p.copy(commentCount = (p.commentCount + delta).coerceAtLeast(0)) else p
            },
            recommendations = home.recommendations.map { slot ->
                if (slot.post.id == postId) {
                    slot.copy(post = slot.post.copy(commentCount = (slot.post.commentCount + delta).coerceAtLeast(0)))
                } else {
                    slot
                }
            },
        )
    }
    @Suppress("UNCHECKED_CAST")
    swrCache.patchByTag(
        tag = BbsCacheTags.POST_LISTS,
        clazz = PaginatedResponseDto::class.java as Class<PaginatedResponseDto<PostSummaryDto>>,
    ) { page ->
        if (page.data.none { it.id == postId }) page
        else page.copy(
            data = page.data.map { p ->
                if (p.id == postId) p.copy(commentCount = (p.commentCount + delta).coerceAtLeast(0)) else p
            },
        )
    }
}

internal fun propagateInboxItemRead(
    swrCache: IgngSwrCache,
    itemId: String,
) {
    swrCache.patchByTag(BbsCacheTags.INBOX, InboxResponseDto::class.java) { inbox ->
        val target = inbox.data.firstOrNull { it.id == itemId }
        if (target == null || target.isRead) {
            inbox
        } else {
            inbox.copy(
                data = inbox.data.map { if (it.id == itemId) it.copy(isRead = true) else it },
                unread = inbox.unread.copy(total = (inbox.unread.total - 1).coerceAtLeast(0)),
            )
        }
    }
}

class FeedRepository(
    private val http: IgngHttpClient,
    private val settings: SettingsRepository,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun fetchBootstrap(): ApiResult<BbsBootstrapDto> {
        val result = http.get<BbsBootstrapDto>("/bbs/bootstrap")
        if (result is ApiResult.Success) {
            settings.writeCache("bootstrap", IgngJson.instance.encodeToString(BbsBootstrapDto.serializer(), result.data))
        }
        return result
    }

    suspend fun loadCachedBootstrap(): BbsBootstrapDto? = settings.readCache("bootstrap")?.let { raw ->
        runCatching { IgngJson.instance.decodeFromString(BbsBootstrapDto.serializer(), raw) }.getOrNull()
    }

    suspend fun fetchHome(): ApiResult<BbsHomeDto> {
        val result = http.get<BbsHomeDto>("/bbs/home")
        if (result is ApiResult.Success) {
            settings.writeCache("home", IgngJson.instance.encodeToString(BbsHomeDto.serializer(), result.data))
        }
        return result
    }

    suspend fun loadCachedHome(): BbsHomeDto? = settings.readCache("home")?.let { raw ->
        runCatching { IgngJson.instance.decodeFromString(BbsHomeDto.serializer(), raw) }.getOrNull()
    }

    suspend fun listPosts(
        page: Int = 1,
        pageSize: Int = 20,
        sort: String = "latest",
        category: String? = null,
        tag: String? = null,
        columnId: String? = null,
        authorId: String? = null,
        excludeSeries: Boolean = false,
    ): ApiResult<PaginatedResponseDto<PostSummaryDto>> {
        val baseQuery = mapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "sort_by" to sort,
            "category_slug" to category,
            "tag" to tag,
            "column_id" to columnId,
            "author_id" to authorId,
        )
        val primary = http.get<PaginatedResponseDto<PostSummaryDto>>(
            path = "/bbs/posts",
            query = if (excludeSeries) baseQuery + ("exclude_series" to "true") else baseQuery,
        )
        // Graceful rollout compatibility if backend has not yet deployed v1.17 exclude_series param
        if (excludeSeries && primary is ApiResult.Failure &&
            primary.error.httpStatus == 400 &&
            primary.error.errorCode == "validation.invalid_parameter"
        ) {
            return when (val fallback = http.get<PaginatedResponseDto<PostSummaryDto>>(path = "/bbs/posts", query = baseQuery)) {
                is ApiResult.Success -> ApiResult.Success(
                    fallback.data.copy(data = fallback.data.data.filter { it.series == null }),
                )
                is ApiResult.Failure -> fallback
            }
        }
        return primary
    }

    suspend fun search(
        query: String,
        page: Int = 1,
        pageSize: Int = 20,
        category: String? = null,
        tag: String? = null,
    ): ApiResult<SearchResponseDto> = http.get(
        path = "/bbs/search",
        query = mapOf(
            "q" to query,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "category_id" to category,
            "tag" to tag,
        ),
    )

    suspend fun listTags(limit: Int = 40): ApiResult<ListWrapperDto<TagStatDto>> =
        http.get("/bbs/tags", query = mapOf("limit" to limit.toString()))

    suspend fun fetchRules(): ApiResult<BbsRulesDto> = http.get("/bbs/rules")
}

class ColumnRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun listColumns(
        page: Int = 1,
        pageSize: Int = 20,
        query: String? = null,
        categoryId: String? = null,
        mine: Boolean = false,
    ): ApiResult<PaginatedResponseDto<ColumnSummaryDto>> = http.get(
        path = "/bbs/columns",
        query = mapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "q" to query,
            "category_id" to categoryId,
            "mine" to if (mine) "true" else null,
        ),
    )

    suspend fun getColumnDetail(columnIdOrSlug: String): ApiResult<ColumnDetailDto> =
        http.get("/bbs/columns/$columnIdOrSlug")

    suspend fun joinColumn(columnId: String, message: String? = null): ApiResult<ColumnMembershipResponseDto> {
        val res = http.post<ColumnMembershipRequest, ColumnMembershipResponseDto>(
            "/bbs/columns/$columnId/membership",
            ColumnMembershipRequest(message = message),
        )
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COLUMNS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.columnDetail(columnId))
        }
        return res
    }

    suspend fun leaveColumn(columnId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/columns/$columnId/membership")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COLUMNS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.columnDetail(columnId))
        }
        return res
    }

    suspend fun subscribeColumn(
        columnId: String,
        verification: String? = null,
        message: String? = null,
    ): ApiResult<ColumnSubscriptionResponseDto> {
        val res = http.post<ColumnSubscriptionRequest, ColumnSubscriptionResponseDto>(
            "/bbs/columns/$columnId/subscription",
            ColumnSubscriptionRequest(verification, message),
        )
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COLUMNS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.columnDetail(columnId))
        }
        return res
    }

    suspend fun unsubscribeColumn(columnId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/columns/$columnId/subscription")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COLUMNS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.columnDetail(columnId))
        }
        return res
    }
}

class PostRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun getPostDetail(postId: String): ApiResult<PostDetailDto> =
        http.get("/bbs/posts/$postId")

    suspend fun recordView(postId: String): ApiResult<PostViewResponseDto> =
        http.postEmpty("/bbs/posts/$postId/view", requireAuth = false)

    suspend fun toggleLike(postId: String): ApiResult<PostLikeResponseDto> {
        val res = http.postEmpty<PostLikeResponseDto>("/bbs/posts/$postId/like")
        if (res is ApiResult.Success) {
            propagatePostLike(swrCache, postId, res.data.liked, res.data.likeCount)
        }
        return res
    }

    suspend fun toggleFavorite(postId: String): ApiResult<PostFavoriteResponseDto> {
        val res = http.postEmpty<PostFavoriteResponseDto>("/bbs/posts/$postId/favorite")
        if (res is ApiResult.Success) {
            swrCache.patch<PostDetailDto>(BbsCacheKeys.postDetail(postId)) { cur ->
                cur.copy(viewerState = cur.viewerState.copy(isFavorited = res.data.favorited))
            }
            swrCache.markStale(BbsCacheTags.FAVORITES)
        }
        return res
    }

    suspend fun listComments(
        postId: String,
        page: Int = 1,
        pageSize: Int = 20,
        sortBy: String = "oldest",
        afterId: String? = null,
    ): ApiResult<CommentsListResponseDto> = http.get(
        path = "/bbs/posts/$postId/comments",
        query = mapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "sort_by" to sortBy,
            "after_id" to afterId,
        ),
    )

    suspend fun createComment(postId: String, request: CreateCommentRequest): ApiResult<CommentCreateResponseDto> {
        val res = http.post<CreateCommentRequest, CommentCreateResponseDto>("/bbs/posts/$postId/comments", request)
        if (res is ApiResult.Success) {
            if (!res.data.reviewPending && request.reEditCommentId == null) {
                propagatePostCommentCountDelta(swrCache, postId, +1)
            }
            swrCache.markStale(BbsCacheTags.COMMENTS, BbsCacheTags.CREATOR)
        }
        return res
    }

    suspend fun deleteComment(commentId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/comments/$commentId")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COMMENTS, BbsCacheTags.CREATOR)
        }
        return res
    }

    fun adjustCachedCommentCount(postId: String, delta: Int) {
        propagatePostCommentCountDelta(swrCache, postId, delta)
    }

    suspend fun toggleCommentLike(commentId: String): ApiResult<CommentLikeResponseDto> {
        val res = http.postEmpty<CommentLikeResponseDto>("/bbs/comments/$commentId/like")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.COMMENTS)
        }
        return res
    }

    suspend fun searchUsers(query: String, limit: Int = 10): ApiResult<ListWrapperDto<UserBriefDto>> =
        http.get("/bbs/users/search", query = mapOf("q" to query, "limit" to limit.toString()))

    suspend fun submitReport(request: CreateReportRequest): ApiResult<ReportSubmitResponseDto> =
        http.post("/bbs/reports", request)
}

class InboxRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun listInbox(
        tab: String = "all",
        page: Int = 1,
        pageSize: Int = 25,
        query: String? = null,
        unreadOnly: Boolean = false,
    ): ApiResult<InboxResponseDto> = http.get(
        path = "/bbs/inbox",
        query = mapOf(
            "tab" to tab,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "q" to query,
            "unread_only" to if (unreadOnly) "true" else null,
        ),
        requireAuth = true,
    )

    suspend fun markItemRead(itemId: String): ApiResult<MarkInboxReadResponseDto> {
        val res = http.post<MarkInboxReadRequest, MarkInboxReadResponseDto>(
            "/bbs/inbox/read",
            MarkInboxReadRequest(markAll = false, itemId = itemId),
        )
        if (res is ApiResult.Success) {
            patchItemReadLocally(itemId)
            swrCache.markStale(BbsCacheTags.INBOX)
        }
        return res
    }

    fun patchItemReadLocally(itemId: String) {
        propagateInboxItemRead(swrCache, itemId)
    }

    suspend fun markAllRead(scope: String = "all"): ApiResult<MarkInboxReadResponseDto> {
        val res = http.post<MarkInboxReadRequest, MarkInboxReadResponseDto>(
            "/bbs/inbox/read",
            MarkInboxReadRequest(markAll = true, scope = scope),
        )
        if (res is ApiResult.Success) {
            swrCache.patchByTag(BbsCacheTags.INBOX, InboxResponseDto::class.java) { inbox ->
                inbox.copy(
                    data = inbox.data.map { it.copy(isRead = true) },
                    unread = inbox.unread.copy(total = 0),
                )
            }
            swrCache.markStale(BbsCacheTags.INBOX)
        }
        return res
    }
}

class ProfileRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun getUserProfile(userIdOrUsername: String): ApiResult<UserProfileDto> =
        http.get("/bbs/users/$userIdOrUsername/profile")

    suspend fun toggleFollow(userId: String): ApiResult<FollowResponseDto> {
        val res = http.postEmpty<FollowResponseDto>("/bbs/users/$userId/follow")
        if (res is ApiResult.Success) {
            swrCache.patch<UserProfileDto>(BbsCacheKeys.userProfile(userId)) { cur ->
                cur.copy(
                    viewerState = cur.viewerState.copy(isFollowing = res.data.following),
                    stats = cur.stats.copy(followers = res.data.followerCount),
                )
            }
            swrCache.patchByTag(BbsCacheTags.POSTS, PostDetailDto::class.java) { post ->
                if (post.author?.id == userId) {
                    post.copy(viewerState = post.viewerState.copy(isFollowingAuthor = res.data.following))
                } else {
                    post
                }
            }
            swrCache.markStale(BbsCacheTags.FOLLOWING, BbsCacheTags.USER_PROFILES)
        }
        return res
    }

    suspend fun listFavorites(page: Int = 1, pageSize: Int = 20): ApiResult<PaginatedResponseDto<FavoriteItemDto>> =
        http.get("/bbs/me/favorites", mapOf("page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun listFollowing(
        tab: String = "following",
        page: Int = 1,
        pageSize: Int = 25,
    ): ApiResult<PaginatedResponseDto<FollowingItemDto>> = http.get(
        path = "/bbs/me/following",
        query = mapOf("tab" to tab, "page" to page.toString(), "page_size" to pageSize.toString()),
        requireAuth = true,
    )

    suspend fun getMySettings(): ApiResult<BbsUserSettingsDto> =
        http.get("/bbs/me/settings", requireAuth = true)

    suspend fun updateMySettings(request: UpdateMySettingsRequest): ApiResult<BbsUserSettingsDto> {
        val res = http.patch<UpdateMySettingsRequest, BbsUserSettingsDto>("/bbs/me/settings", request)
        if (res is ApiResult.Success) {
            swrCache.put(
                key = BbsCacheKeys.MY_SETTINGS,
                data = res.data,
                scope = IgngCacheScope.USER_ONLY,
                tags = setOf(BbsCacheTags.SETTINGS),
            )
            swrCache.markStale(BbsCacheTags.FEED, BbsCacheTags.HOME, BbsCacheTags.USER_PROFILES)
        }
        return res
    }

    suspend fun upsertBlockRule(request: UpsertBlockRuleRequest): ApiResult<BlockRuleDto> {
        val res = http.post<UpsertBlockRuleRequest, BlockRuleDto>("/bbs/me/block-rules", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.SETTINGS, BbsCacheTags.USER_PROFILES, BbsCacheTags.FEED, BbsCacheTags.HOME)
        }
        return res
    }

    suspend fun deleteBlockRule(targetUserId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/me/block-rules/$targetUserId")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.SETTINGS, BbsCacheTags.USER_PROFILES, BbsCacheTags.FEED, BbsCacheTags.HOME)
        }
        return res
    }
}

class CreatorRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun getOverview(): ApiResult<CreatorOverviewDto> =
        http.get("/bbs/creator/overview", requireAuth = true)

    suspend fun listPosts(
        filter: String = "all",
        page: Int = 1,
        pageSize: Int = 20,
        query: String? = null,
    ): ApiResult<PaginatedResponseDto<PostSummaryDto>> = http.get(
        path = "/bbs/creator/posts",
        query = mapOf("filter" to filter, "page" to page.toString(), "page_size" to pageSize.toString(), "q" to query),
        requireAuth = true,
    )

    suspend fun createPost(request: CreateCreatorPostRequest): ApiResult<CreatorPostDetailDto> {
        val res = http.post<CreateCreatorPostRequest, CreatorPostDetailDto>("/bbs/creator/posts", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS)
        }
        return res
    }

    suspend fun getPostDetail(postId: String): ApiResult<CreatorPostDetailDto> =
        http.get("/bbs/creator/posts/$postId", requireAuth = true)

    suspend fun updatePost(postId: String, request: UpdateCreatorPostRequest): ApiResult<CreatorPostDetailDto> {
        val res = http.patch<UpdateCreatorPostRequest, CreatorPostDetailDto>("/bbs/creator/posts/$postId", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun deletePost(postId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/creator/posts/$postId")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS, BbsCacheTags.FAVORITES)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun submitPost(postId: String, request: SubmitCreatorPostRequest = SubmitCreatorPostRequest()): ApiResult<PostSubmitResponseDto> {
        val res = http.post<SubmitCreatorPostRequest, PostSubmitResponseDto>("/bbs/creator/posts/$postId/submit", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun withdrawSubmission(postId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/creator/posts/$postId/submit")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun listVersions(postId: String): ApiResult<ListWrapperDto<PostVersionDto>> =
        http.get("/bbs/creator/posts/$postId/versions", requireAuth = true)

    suspend fun restoreVersion(postId: String, revisionNo: Int): ApiResult<CreatorPostDetailDto> {
        val res = http.post<RestorePostVersionRequest, CreatorPostDetailDto>(
            "/bbs/creator/posts/$postId/versions",
            RestorePostVersionRequest(revisionNo),
        )
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.CREATOR)
        }
        return res
    }

    suspend fun getPostAnalytics(postId: String, days: Int = 30): ApiResult<PostAnalyticsDto> =
        http.get("/bbs/creator/posts/$postId/analytics", mapOf("days" to days.toString()), requireAuth = true)

    suspend fun inviteCollaborator(postId: String, request: InviteCollaboratorRequest): ApiResult<PendingInviteDto> {
        val res = http.post<InviteCollaboratorRequest, PendingInviteDto>("/bbs/creator/posts/$postId/collaborators", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun updateCollaborator(postId: String, collaboratorId: String, request: UpdateCollaboratorRequest): ApiResult<PostCollaboratorDto> {
        val res = http.patch<UpdateCollaboratorRequest, PostCollaboratorDto>("/bbs/creator/posts/$postId/collaborators/$collaboratorId", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun removeCollaborator(postId: String, collaboratorId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/creator/posts/$postId/collaborators/$collaboratorId")
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun listInvites(): ApiResult<CreatorInvitesDto> =
        http.get("/bbs/creator/invites", requireAuth = true)

    suspend fun respondInvite(inviteId: String, action: String): ApiResult<RespondActionResultDto> {
        val res = http.post<RespondInviteRequest, RespondActionResultDto>("/bbs/creator/invites/$inviteId/respond", RespondInviteRequest(action))
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun listColumns(): ApiResult<ListWrapperDto<ColumnSummaryDto>> =
        http.get("/bbs/creator/columns", requireAuth = true)

    suspend fun createColumn(request: CreateCreatorColumnRequest): ApiResult<ColumnSummaryDto> {
        val res = http.post<CreateCreatorColumnRequest, ColumnSummaryDto>("/bbs/creator/columns", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun getColumnDetail(columnId: String): ApiResult<CreatorColumnDetailDto> =
        http.get("/bbs/creator/columns/$columnId", requireAuth = true)

    suspend fun updateColumn(columnId: String, request: UpdateCreatorColumnRequest): ApiResult<CreatorColumnDetailDto> {
        val res = http.patch<UpdateCreatorColumnRequest, CreatorColumnDetailDto>("/bbs/creator/columns/$columnId", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun deleteColumn(columnId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/creator/columns/$columnId")
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun performColumnAction(columnId: String, request: CreatorColumnActionRequest): ApiResult<RespondActionResultDto> {
        val res = http.post<CreatorColumnActionRequest, RespondActionResultDto>("/bbs/creator/columns/$columnId/actions", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun listColumnEvents(): ApiResult<ListWrapperDto<ColumnEventDto>> =
        http.get("/bbs/creator/column-events", requireAuth = true)

    suspend fun respondColumnEvent(eventId: String, action: String, message: String? = null): ApiResult<RespondActionResultDto> {
        val res = http.post<RespondColumnEventRequest, RespondActionResultDto>(
            "/bbs/creator/column-events/$eventId/respond",
            RespondColumnEventRequest(action, message),
        )
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun listSeries(): ApiResult<ListWrapperDto<SeriesSummaryDto>> =
        http.get("/bbs/creator/series", requireAuth = true)

    suspend fun createSeries(request: CreateSeriesRequest): ApiResult<SeriesSummaryDto> {
        val res = http.post<CreateSeriesRequest, SeriesSummaryDto>("/bbs/creator/series", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun getSeriesDetail(seriesId: String): ApiResult<CreatorSeriesDetailDto> =
        http.get("/bbs/creator/series/$seriesId", requireAuth = true)

    suspend fun updateSeries(seriesId: String, request: UpdateSeriesRequest): ApiResult<CreatorSeriesDetailDto> {
        val res = http.patch<UpdateSeriesRequest, CreatorSeriesDetailDto>("/bbs/creator/series/$seriesId", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.FEED, BbsCacheTags.POSTS)
        return res
    }

    suspend fun deleteSeries(seriesId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/creator/series/$seriesId")
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR, BbsCacheTags.FEED, BbsCacheTags.POSTS)
        return res
    }

    suspend fun listComments(postId: String? = null, page: Int = 1, pageSize: Int = 25): ApiResult<PaginatedResponseDto<CreatorCommentDto>> =
        http.get(
            path = "/bbs/creator/comments",
            query = mapOf("post_id" to postId, "page" to page.toString(), "page_size" to pageSize.toString()),
            requireAuth = true,
        )

    suspend fun listReviews(includePassed: Boolean = false): ApiResult<CreatorReviewsResponseDto> =
        http.get(
            path = "/bbs/creator/reviews",
            query = mapOf("include_passed" to if (includePassed) "true" else null),
            requireAuth = true,
        )

    suspend fun listAppeals(page: Int = 1, pageSize: Int = 25): ApiResult<PaginatedResponseDto<AppealDto>> =
        http.get("/bbs/appeals", mapOf("page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun submitAppeal(request: CreateAppealRequest): ApiResult<AppealSubmitResponseDto> {
        val res = http.post<CreateAppealRequest, AppealSubmitResponseDto>("/bbs/appeals", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun listMedia(page: Int = 1, pageSize: Int = 30): ApiResult<CreatorMediaListDto> =
        http.get("/bbs/creator/media", mapOf("page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun uploadMedia(fileName: String, mimeType: String, bytes: ByteArray): ApiResult<CreatorMediaFileDto> {
        val res = http.uploadMultipart<CreatorMediaFileDto>("/bbs/creator/media", fieldName = "file", fileName = fileName, mimeType = mimeType, bytes = bytes)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }

    suspend fun appealMedia(mediaId: String, reason: String): ApiResult<RespondActionResultDto> {
        val res = http.post<MediaAppealRequest, RespondActionResultDto>("/media/$mediaId/appeal", MediaAppealRequest(reason))
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.CREATOR)
        return res
    }
}

class AdminRepository(
    private val http: IgngHttpClient,
    val swrCache: IgngSwrCache = IgngSwrCache(),
) {
    suspend fun listPosts(
        query: String? = null,
        banStatus: Int? = null,
        moderationStatus: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): ApiResult<PaginatedResponseDto<PostSummaryDto>> = http.get(
        path = "/bbs/admin/posts",
        query = mapOf(
            "q" to query,
            "ban_status" to banStatus?.toString(),
            "moderation_status" to moderationStatus,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
        ),
        requireAuth = true,
    )

    suspend fun updatePost(postId: String, request: AdminUpdatePostRequest): ApiResult<PostSummaryDto> {
        val res = http.patch<AdminUpdatePostRequest, PostSummaryDto>("/bbs/admin/posts/$postId", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun deletePost(postId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/admin/posts/$postId")
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS, BbsCacheTags.CREATOR)
            swrCache.markKeyStale(BbsCacheKeys.postDetail(postId))
        }
        return res
    }

    suspend fun listReviews(
        status: String? = null,
        targetType: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): ApiResult<PaginatedResponseDto<AdminReviewItemDto>> = http.get(
        path = "/bbs/admin/reviews",
        query = mapOf(
            "status" to status,
            "target_type" to targetType,
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
        ),
        requireAuth = true,
    )

    suspend fun decideReview(reviewId: String, request: AdminDecideReviewRequest): ApiResult<RespondActionResultDto> {
        val res = http.post<AdminDecideReviewRequest, RespondActionResultDto>("/bbs/admin/reviews/$reviewId/decide", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.CREATOR, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS)
        }
        return res
    }

    suspend fun listReports(
        status: String? = null,
        page: Int = 1,
        pageSize: Int = 25,
    ): ApiResult<PaginatedResponseDto<AdminReviewItemDto>> = http.get(
        path = "/bbs/admin/reports",
        query = mapOf("status" to status, "page" to page.toString(), "page_size" to pageSize.toString()),
        requireAuth = true,
    )

    suspend fun decideReport(reportId: String, request: AdminDecideReportRequest): ApiResult<RespondActionResultDto> {
        val res = http.post<AdminDecideReportRequest, RespondActionResultDto>("/bbs/admin/reports/$reportId/decide", request)
        if (res is ApiResult.Success) {
            swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.HOME, BbsCacheTags.FEED, BbsCacheTags.POST_LISTS)
        }
        return res
    }

    suspend fun listRiskStates(scope: String? = null): ApiResult<ListWrapperDto<AdminRiskEntryDto>> =
        http.get("/bbs/admin/risk", query = mapOf("scope" to scope), requireAuth = true)

    suspend fun lookupUserRisk(userId: String): ApiResult<AdminUserRiskLookupDto> =
        http.get("/bbs/admin/risk", query = mapOf("user_id" to userId), requireAuth = true)

    suspend fun manageRisk(request: AdminManageRiskRequest): ApiResult<AdminRiskActionResponseDto> {
        val res = http.post<AdminManageRiskRequest, AdminRiskActionResponseDto>("/bbs/admin/risk", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.ADMIN)
        return res
    }

    suspend fun listUsers(query: String? = null, page: Int = 1, pageSize: Int = 25): ApiResult<PaginatedResponseDto<AdminUserRowDto>> =
        http.get("/bbs/admin/users", mapOf("q" to query, "page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun listColumns(query: String? = null, page: Int = 1, pageSize: Int = 25): ApiResult<PaginatedResponseDto<ColumnSummaryDto>> =
        http.get("/bbs/admin/columns", mapOf("q" to query, "page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun updateColumn(columnId: String, request: AdminUpdateColumnRequest): ApiResult<ColumnSummaryDto> {
        val res = http.patch<AdminUpdateColumnRequest, ColumnSummaryDto>("/bbs/admin/columns/$columnId", request)
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.COLUMNS)
        return res
    }

    suspend fun listSeries(query: String? = null, page: Int = 1, pageSize: Int = 25): ApiResult<PaginatedResponseDto<SeriesSummaryDto>> =
        http.get("/bbs/admin/series", mapOf("q" to query, "page" to page.toString(), "page_size" to pageSize.toString()), requireAuth = true)

    suspend fun deleteSeries(seriesId: String): ApiResult<Unit> {
        val res = http.deleteNoContent("/bbs/admin/series/$seriesId")
        if (res is ApiResult.Success) swrCache.markStale(BbsCacheTags.ADMIN, BbsCacheTags.CREATOR)
        return res
    }
}
