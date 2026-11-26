package net.igng.bbs.feature.post

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.igng.bbs.common.BbsFloatingArticleBar
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.data.CreateReportRequest
import net.igng.bbs.core.model.AppPreferences
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.LocalReadingHistoryEntry
import net.igng.bbs.core.model.PostDetailDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngAvatar
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngImageGalleryLightbox
import net.igng.core.designsystem.IgngInfoBanner
import net.igng.core.designsystem.IgngLazyColumnScrollbar
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPrimaryButton
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngReaderTypographySheet
import net.igng.core.designsystem.IgngSecondaryButton
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar
import net.igng.core.designsystem.IgngTopRefreshBar
import net.igng.core.designsystem.ProvideIgngReaderSurface
import net.igng.core.designsystem.readerSurfaceColors
import net.igng.core.designsystem.rememberIgngReadingProgress
import net.igng.core.designsystem.rememberIgngScrollBarVisibility
import net.igng.core.markdown.IgngMarkdownBlockView
import net.igng.core.markdown.MarkdownParser
import net.igng.core.network.ApiResult

private class PostReaderUiHolder {
    val acknowledgedRestrictionState = mutableStateOf(false)
    var viewRecorded by mutableStateOf(false)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PostReaderScreen(
    container: BbsAppContainer,
    postId: String,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenColumn: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenLink: (String) -> Unit,
    onEditPost: (String) -> Unit,
    onRequireLogin: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = IgngTheme.colors
    val prefs by container.settingsRepository.preferences.collectAsState(initial = AppPreferences())
    val bootstrap by container.bootstrap.collectAsState()
    val activeAccount by container.sessionStore.activeAccount.collectAsState()

    val (_, readerPrimary, readerSecondary) = colors.readerSurfaceColors(prefs.reader.canvasTone)

    val holder = rememberScreenHolder("bbs_post_reader_$postId") { PostReaderUiHolder() }
    var acknowledgedRestriction by holder.acknowledgedRestrictionState

    val (detailState, detailHandle) = rememberSwrResource<PostDetailDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.postDetail(postId),
        scope = IgngCacheScope.SESSION_AWARE,
        policy = BbsCachePolicies.Detail,
        tags = setOf(BbsCacheTags.POSTS),
        slotKey = "bbs_post_detail_$postId",
    ) {
        container.postRepository.getPostDetail(postId).toKotlinResult()
    }

    val detail = detailState.data
    val liked = detail?.viewerState?.isLiked ?: false
    val likeCount = detail?.likeCount ?: 0
    val favorited = detail?.viewerState?.isFavorited ?: false
    val followingAuthor = detail?.viewerState?.isFollowingAuthor ?: false

    var commentsRefreshSignal by remember { mutableIntStateOf(0) }
    var showTocSheet by remember { mutableStateOf(false) }
    var showTypoSheet by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lightboxIndex by remember { mutableStateOf<Int?>(null) }
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    // Record view once per reader visit after brief dwell (never re-fires on predictive back)
    LaunchedEffect(detail?.id) {
        val current = detail ?: return@LaunchedEffect
        if (holder.viewRecorded) return@LaunchedEffect
        delay(1200L)
        holder.viewRecorded = true
        container.postRepository.recordView(current.id)
    }

    val document = remember(detail?.content, detail?.format, detail?.id) {
        val d = detail ?: return@remember null
        MarkdownParser.parse(
            source = d.content,
            format = d.format,
            resolveUrl = { raw ->
                d.mediaUrls[raw] ?: container.endpoints.resolveMediaUrl(raw, d.id) ?: raw
            },
        )
    }

    val listState = rememberLazyListState()
    val headerItemsCount = 2 // meta header + optional summary/column
    val totalBlocks = document?.blocks?.size ?: 0
    val commentsAnchorIndex = headerItemsCount + totalBlocks + 1

    val readingProgress by rememberIgngReadingProgress(listState)

    val barVisibility = rememberIgngScrollBarVisibility(hideThreshold = 32.dp, showThreshold = 20.dp)
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 1) {
            barVisibility.forceShow()
        }
    }

    // Save local reading history with debounce to prevent frame drops during active scrolling
    LaunchedEffect(detail?.id, listState.firstVisibleItemIndex) {
        val d = detail ?: return@LaunchedEffect
        delay(500L)
        container.settingsRepository.recordReadingProgress(
            LocalReadingHistoryEntry(
                postId = d.id,
                numericId = d.numericId,
                title = d.title,
                authorName = d.author?.displayName ?: "匿名用户",
                categoryName = d.category?.let { BbsFormatters.categoryDisplayName(it) },
                progressRatio = readingProgress,
                scrollIndex = listState.firstVisibleItemIndex,
            ),
        )
    }

    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    ProvideIgngReaderSurface(tone = prefs.reader.canvasTone) {
        val surfaceColors = IgngTheme.colors
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColors.canvas)
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                IgngTopBar(
                    title = detail?.category?.let { BbsFormatters.categoryDisplayName(it) } ?: "文章阅读",
                    onBack = onBack,
                    actions = {
                        if (document?.toc?.isNotEmpty() == true) {
                            IconButton(onClick = { showTocSheet = true }) {
                                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = "文章目录", tint = readerPrimary)
                            }
                        }
                        IconButton(onClick = { showTypoSheet = true }) {
                            Icon(Icons.Rounded.TextFields, contentDescription = "阅读排版", tint = readerPrimary)
                        }
                        if (detail?.viewerState?.canEdit == true) {
                            IconButton(onClick = { onEditPost(detail.id) }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "编辑文章", tint = colors.accent)
                            }
                        }
                        if (detail != null) {
                            IconButton(onClick = {
                                if (activeAccount == null) {
                                    onRequireLogin()
                                } else {
                                    reportTarget = "post" to detail.id
                                    showReportDialog = true
                                }
                            }) {
                                Icon(Icons.Rounded.Flag, contentDescription = "举报", tint = readerSecondary)
                            }
                        }
                    },
                )

                val showArticlePullRefresh = detail != null && (!detail.restrictedWarning || acknowledgedRestriction)
                if (!showArticlePullRefresh) {
                    IgngTopRefreshBar(
                        active = detailState.isInitialLoading || detailState.isRevalidating || detailState.isUserRefreshing,
                    )
                }

                when {
                    detailState.isInitialLoading && detail == null -> IgngLoadingCenter(loading = true)
                    detailState.error != null && detail == null -> Box(Modifier.padding(16.dp)) {
                        IgngErrorTile(message = detailState.error!!, onRetry = { detailHandle.refresh() })
                    }
                    detail != null -> {
                        val d = detail
                        // Restricted warning gate (ban_status = 4)
                        if (d.restrictedWarning && !acknowledgedRestriction) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                IgngTile {
                                    Text("内容受限提示", style = IgngTheme.type.sectionTitle, color = colors.warning)
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = d.restrictedReason?.ifBlank { null }
                                            ?: "本文被标记为受限制内容，仅限通过链接访问并确认后浏览。",
                                        style = IgngTheme.type.body,
                                        color = colors.textSecondary,
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        IgngSecondaryButton(text = "返回", onClick = onBack, modifier = Modifier.weight(1f))
                                        IgngPrimaryButton(
                                            text = "确认继续阅读",
                                            onClick = { acknowledgedRestriction = true },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        } else {
                            IgngPullToRefreshBox(
                                isRefreshing = detailState.isUserRefreshing,
                                isRevalidating = detailState.isRevalidating,
                                onRefresh = {
                                    detailHandle.refresh()
                                    commentsRefreshSignal++
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(barVisibility.nestedScrollConnection),
                                    contentPadding = PaddingValues(
                                        start = 18.dp,
                                        end = 18.dp,
                                        top = 12.dp,
                                        bottom = navBottomPadding + 96.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    // Item 0: Title, Author, Collaborators, Meta
                                    item(key = "post_header") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            feedbackBanner?.let { msg ->
                                                IgngInfoBanner(title = "提示", message = msg, tone = IgngTone.ACCENT)
                                            }
                                            if (d.moderationStatus == "pending" || d.moderationStatus == "rejected") {
                                                IgngInfoBanner(
                                                    title = if (d.moderationStatus == "pending") "审核处理中" else "审核未通过",
                                                    message = d.rejectionReason?.ifBlank { null }
                                                        ?: "当前版本状态为：${BbsFormatters.postStatusLabel(d.status, d.banStatus, d.moderationStatus)}",
                                                    tone = if (d.moderationStatus == "rejected") IgngTone.DANGER else IgngTone.WARNING,
                                                )
                                            }
                                            Text(
                                                text = d.title,
                                                style = IgngTheme.type.pageTitle.copy(fontWeight = FontWeight.Bold),
                                                color = readerPrimary,
                                            )
                                            // Author row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            ) {
                                                IgngAvatar(
                                                    avatarUrl = d.author?.avatarUrl,
                                                    displayName = d.author?.displayName ?: "作者",
                                                    size = 38.dp,
                                                    onClick = d.author?.id?.let { uid -> { onOpenUser(uid) } },
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable { d.author?.id?.let(onOpenUser) },
                                                ) {
                                                    Text(
                                                        text = d.author?.displayName ?: "匿名用户",
                                                        style = IgngTheme.type.bodyMedium,
                                                        color = readerPrimary,
                                                    )
                                                    Text(
                                                        text = "${BbsFormatters.relativeTime(d.createdAt)} · ${BbsFormatters.readingMinutesLabel(d.readingMinutes)} · ${BbsFormatters.compactCount(d.views)} 阅读",
                                                        style = IgngTheme.type.meta,
                                                        color = readerSecondary,
                                                    )
                                                }
                                                val author = d.author
                                                if (!d.viewerState.isOwner && author != null) {
                                                    IgngTagChip(
                                                        text = if (followingAuthor) "已关注" else "+ 关注",
                                                        selected = followingAuthor,
                                                        tone = IgngTone.ACCENT,
                                                        onClick = {
                                                            if (activeAccount == null) { onRequireLogin(); return@IgngTagChip }
                                                            val prevFollowing = followingAuthor
                                                            detailHandle.mutateLocal { cur ->
                                                                cur.copy(viewerState = cur.viewerState.copy(isFollowingAuthor = !prevFollowing))
                                                            }
                                                            scope.launch {
                                                                when (val r = container.profileRepository.toggleFollow(author.id)) {
                                                                    is ApiResult.Success -> Unit
                                                                    is ApiResult.Failure -> {
                                                                        detailHandle.mutateLocal { cur ->
                                                                            cur.copy(viewerState = cur.viewerState.copy(isFollowingAuthor = prevFollowing))
                                                                        }
                                                                        feedbackBanner = r.error.message
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    )
                                                }
                                            }
                                            // Collaborators bylines
                                            if (d.collaborators.isNotEmpty()) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    d.collaborators.forEach { c ->
                                                        IgngTagChip(
                                                            text = "${c.roleTitle}：${c.user?.displayName ?: "协作者"}",
                                                            tone = IgngTone.NEUTRAL,
                                                            onClick = c.user?.id?.let { uid -> { onOpenUser(uid) } },
                                                        )
                                                    }
                                                }
                                            }
                                            // Tags
                                            val displayTags = BbsFormatters.visiblePostTags(d.tags, d.category)
                                            if (displayTags.isNotEmpty()) {
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                ) {
                                                    displayTags.forEach { tag ->
                                                        IgngTagChip(
                                                            text = BbsFormatters.tagDisplayLabel(tag),
                                                            onClick = { onOpenTag(tag) },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Item 1: Summary + Column card
                                    item(key = "post_context_cards") {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            d.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                                                IgngTile(soft = true, padding = PaddingValues(14.dp)) {
                                                    Text("摘要", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.accent)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(summary, style = IgngTheme.type.body, color = colors.textSecondary)
                                                }
                                            }
                                            d.column?.let { col ->
                                                IgngTile(
                                                    soft = true,
                                                    padding = PaddingValues(14.dp),
                                                    onClick = { onOpenColumn(col.id) },
                                                ) {
                                                    Text("收录于专栏 · ${col.name}", style = IgngTheme.type.bodyMedium, color = colors.accent)
                                                    col.description?.takeIf { it.isNotBlank() }?.let {
                                                        Spacer(Modifier.height(2.dp))
                                                        Text(it, style = IgngTheme.type.caption, color = colors.textSecondary, maxLines = 1)
                                                    }
                                                }
                                            }
                                            IgngHairlineDivider()
                                        }
                                    }

                                    // Native Markdown / HTML AST Blocks
                                    val blocks = document?.blocks.orEmpty()
                                    itemsIndexed(blocks, key = { idx, _ -> "blk_$idx" }) { _, block ->
                                        IgngMarkdownBlockView(
                                            block = block,
                                            typography = prefs.reader,
                                            onImageClick = { url ->
                                                val idx = document?.imageUrls?.indexOf(url) ?: -1
                                                if (idx >= 0) lightboxIndex = idx
                                            },
                                            onLinkClick = onOpenLink,
                                            onMentionClick = onOpenUser,
                                        )
                                    }

                                    // Series Navigation Card
                                    item(key = "series_nav") {
                                        d.series?.let { ser ->
                                            IgngTile(soft = true) {
                                                Text(
                                                    text = "系列合集 · ${ser.name}（第 ${ser.currentIndex + 1} / ${ser.totalChapters} 篇）",
                                                    style = IgngTheme.type.bodyMedium,
                                                    color = colors.accent,
                                                )
                                                Spacer(Modifier.height(10.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                    ser.prevPost?.let { prev ->
                                                        IgngSecondaryButton(
                                                            text = "上一篇",
                                                            onClick = { onOpenPost(prev.id) },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                    ser.nextPost?.let { next ->
                                                        IgngPrimaryButton(
                                                            text = "下一篇",
                                                            onClick = { onOpenPost(next.id) },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Threaded Comments Section
                                    item(key = "comments_section") {
                                        PostCommentsSection(
                                            container = container,
                                            postId = d.id,
                                            refreshSignal = commentsRefreshSignal,
                                            onOpenUser = onOpenUser,
                                            onReportComment = { cmtId ->
                                                if (activeAccount == null) {
                                                    onRequireLogin()
                                                } else {
                                                    reportTarget = "comment" to cmtId
                                                    showReportDialog = true
                                                }
                                            },
                                            onRequireLogin = onRequireLogin,
                                        )
                                    }
                                    }

                                    IgngLazyColumnScrollbar(
                                        listState = listState,
                                        thumbColor = readerSecondary,
                                        activeThumbColor = colors.accent,
                                        bottomPadding = navBottomPadding + 84.dp,
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Auto-hiding Floating Article Action Bar
            if (detail != null && (!detail.restrictedWarning || acknowledgedRestriction)) {
                BbsFloatingArticleBar(
                    visible = barVisibility.visible,
                    liked = liked,
                    likeCount = likeCount,
                    favorited = favorited,
                    commentCount = detail.commentCount,
                    onLike = {
                        if (activeAccount == null) { onRequireLogin(); return@BbsFloatingArticleBar }
                        val prevLiked = liked
                        val prevCount = likeCount
                        val nextLiked = !prevLiked
                        val nextCount = (prevCount + if (nextLiked) 1 else -1).coerceAtLeast(0)
                        detailHandle.mutateLocal { cur ->
                            cur.copy(
                                likeCount = nextCount,
                                viewerState = cur.viewerState.copy(isLiked = nextLiked),
                            )
                        }
                        scope.launch {
                            when (val r = container.postRepository.toggleLike(detail.id)) {
                                is ApiResult.Success -> Unit
                                is ApiResult.Failure -> {
                                    detailHandle.mutateLocal { cur ->
                                        cur.copy(
                                            likeCount = prevCount,
                                            viewerState = cur.viewerState.copy(isLiked = prevLiked),
                                        )
                                    }
                                    feedbackBanner = r.error.message
                                }
                            }
                        }
                    },
                    onFavorite = {
                        if (activeAccount == null) { onRequireLogin(); return@BbsFloatingArticleBar }
                        val prevFav = favorited
                        detailHandle.mutateLocal { cur ->
                            cur.copy(viewerState = cur.viewerState.copy(isFavorited = !prevFav))
                        }
                        scope.launch {
                            when (val r = container.postRepository.toggleFavorite(detail.id)) {
                                is ApiResult.Success -> Unit
                                is ApiResult.Failure -> {
                                    detailHandle.mutateLocal { cur ->
                                        cur.copy(viewerState = cur.viewerState.copy(isFavorited = prevFav))
                                    }
                                    feedbackBanner = r.error.message
                                }
                            }
                        }
                    },
                    onComment = {
                        scope.launch {
                            listState.animateScrollToItem(commentsAnchorIndex.coerceAtMost(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1))
                        }
                    },
                    onShare = {
                        val url = detail.webUrl ?: container.endpoints.postWebUrl(detail.numericId)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, detail.title)
                            putExtra(Intent.EXTRA_TEXT, "${detail.title}\n$url")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享文章"))
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    // Image Lightbox
    lightboxIndex?.let { idx ->
        IgngImageGalleryLightbox(
            imageUrls = document?.imageUrls.orEmpty(),
            initialIndex = idx,
            onDismiss = { lightboxIndex = null },
        )
    }

    // TOC Bottom Sheet
    if (showTocSheet && document != null) {
        IgngDetailSheet(
            onDismiss = { showTocSheet = false },
            title = "文章目录",
            scrollable = true,
        ) {
            document.toc.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(IgngTheme.radii.sm)
                        .clickable {
                            showTocSheet = false
                            scope.launch { listState.animateScrollToItem(headerItemsCount + entry.blockIndex) }
                        }
                        .padding(start = ((entry.level - 1) * 14).dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
                ) {
                    Text(entry.title, style = IgngTheme.type.bodyMedium, color = colors.textPrimary)
                }
            }
        }
    }

    // Typography Bottom Sheet
    if (showTypoSheet) {
        IgngReaderTypographySheet(
            prefs = prefs.reader,
            onUpdate = { transform -> scope.launch { container.settingsRepository.updateReaderPrefs(transform) } },
            onDismiss = { showTypoSheet = false },
        )
    }

    // Report Modal with 12-code taxonomy
    if (showReportDialog && reportTarget != null) {
        val reasons = bootstrap?.reportReasons.orEmpty()
        var selectedCode by remember { mutableStateOf(reasons.firstOrNull()?.code ?: "SPAM_AD") }
        var reasonText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("举报违规内容") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        reasons.forEach { r ->
                            IgngTagChip(
                                text = r.title,
                                selected = selectedCode == r.code,
                                onClick = { selectedCode = r.code },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("补充说明（选填）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val (tType, tId) = reportTarget!!
                    showReportDialog = false
                    scope.launch {
                        when (
                            val r = container.postRepository.submitReport(
                                CreateReportRequest(
                                    targetType = tType,
                                    targetId = tId,
                                    reasonCode = selectedCode,
                                    reason = reasonText.trim().ifBlank { null },
                                ),
                            )
                        ) {
                            is ApiResult.Success -> feedbackBanner = "举报已受理，感谢你协助维护社区秩序。"
                            is ApiResult.Failure -> feedbackBanner = r.error.message
                        }
                    }
                }) { Text("提交举报") }
            },
            dismissButton = { TextButton(onClick = { showReportDialog = false }) { Text("取消") } },
        )
    }
}
