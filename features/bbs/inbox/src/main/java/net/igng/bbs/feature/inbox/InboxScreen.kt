package net.igng.bbs.feature.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.igng.bbs.core.data.BbsAppContainer
import net.igng.bbs.core.data.BbsCacheKeys
import net.igng.bbs.core.data.BbsCachePolicies
import net.igng.bbs.core.data.BbsCacheTags
import net.igng.bbs.core.model.BbsFormatters
import net.igng.bbs.core.model.InboxItemDto
import net.igng.bbs.core.model.InboxResponseDto
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.toKotlinResult
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngEmptyTile
import net.igng.core.designsystem.IgngErrorTile
import net.igng.core.designsystem.IgngHairlineDivider
import net.igng.core.designsystem.IgngLoadingCenter
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngTagChip
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTone
import net.igng.core.designsystem.IgngTopBar

private val INBOX_TABS = listOf(
    "all" to "全部",
    "mention" to "提及",
    "reply" to "回复",
    "system" to "系统",
    "column" to "专栏",
)

private class InboxUiHolder {
    val tabState = mutableStateOf("all")
    val unreadOnlyState = mutableStateOf(false)
    val queryState = mutableStateOf("")
    val submittedQueryState = mutableStateOf("")
}

@Composable
fun InboxScreen(
    container: BbsAppContainer,
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onRequireLogin: () -> Unit,
) {
    val colors = IgngTheme.colors
    val scope = rememberCoroutineScope()
    val activeAccount by container.sessionStore.activeAccount.collectAsState()

    val holder = rememberScreenHolder("bbs_inbox_ui") { InboxUiHolder() }
    var tab by holder.tabState
    var unreadOnly by holder.unreadOnlyState
    var query by holder.queryState
    var submittedQuery by holder.submittedQueryState

    val (inboxState, inboxHandle) = rememberSwrResource<InboxResponseDto>(
        cache = container.swrCache,
        cacheKey = BbsCacheKeys.inbox(tab, unreadOnly, submittedQuery),
        scope = IgngCacheScope.USER_ONLY,
        policy = BbsCachePolicies.Realtime,
        tags = setOf(BbsCacheTags.INBOX),
        enabled = activeAccount != null,
        pollIntervalMs = 60_000L,
        slotKey = "bbs_inbox_slot",
    ) {
        container.inboxRepository.listInbox(
            tab = tab,
            query = submittedQuery.ifBlank { null },
            unreadOnly = unreadOnly,
        ).toKotlinResult()
    }

    val items = inboxState.data?.data.orEmpty()
    val unreadTotal = inboxState.data?.unread?.total ?: 0
    var detailItem by remember { mutableStateOf<InboxItemDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding(),
    ) {
        IgngTopBar(
            title = "消息收件箱",
            subtitle = if (unreadTotal > 0) "$unreadTotal 条未读" else null,
            onBack = onBack,
            actions = {
                if (activeAccount != null && unreadTotal > 0) {
                    IconButton(onClick = {
                        scope.launch {
                            container.inboxRepository.markAllRead("all")
                            container.refreshBootstrap(force = true)
                            inboxHandle.silentRefresh()
                        }
                    }) {
                        Icon(Icons.Rounded.DoneAll, contentDescription = "全部标为已读", tint = colors.accent)
                    }
                }
            },
        )

        if (activeAccount == null) {
            Box(Modifier.padding(16.dp)) {
                IgngEmptyTile(
                    title = "登录后查看消息通知",
                    subtitle = "系统通知、评论回复、@提及与专栏动态都会汇集在这里。",
                    actionLabel = "登录 IGNG 账户",
                    onAction = onRequireLogin,
                )
            }
            return
        }

        val submitInboxSearch = {
            val trimmed = query.trim()
            if (trimmed == submittedQuery) {
                inboxHandle.refresh()
            } else {
                submittedQuery = trimmed
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IgngSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索通知正文或标题",
                onSubmit = submitInboxSearch,
                onClear = {
                    query = ""
                    submittedQuery = ""
                },
                modifier = Modifier.weight(1f),
            )
            IgngButton(
                text = "查找",
                style = IgngButtonStyle.Secondary,
                compact = true,
                onClick = submitInboxSearch,
            )
        }
        Spacer(Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(INBOX_TABS, key = { it.first }) { (key, label) ->
                IgngTagChip(text = label, selected = tab == key, onClick = { tab = key })
            }
            item {
                IgngTagChip(
                    text = if (unreadOnly) "仅未读 ✓" else "仅未读",
                    selected = unreadOnly,
                    tone = IgngTone.WARNING,
                    onClick = { unreadOnly = !unreadOnly },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        IgngPullToRefreshBox(
            isRefreshing = inboxState.isUserRefreshing,
            isRevalidating = inboxState.isRevalidating,
            onRefresh = { inboxHandle.refresh() },
            modifier = Modifier.weight(1f),
        ) {
            when {
                inboxState.isInitialLoading && items.isEmpty() -> IgngLoadingCenter(loading = true)
                inboxState.error != null && items.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngErrorTile(message = inboxState.error!!, onRetry = { inboxHandle.refresh() })
                }
                items.isEmpty() -> Box(Modifier.padding(16.dp)) {
                    IgngEmptyTile(
                        title = if (unreadOnly) "没有未读消息" else "收件箱很安静",
                        subtitle = "当有人回复或 @提到你，或者有新的系统通知时会立刻出现在这里。",
                    )
                }
                else -> {
                    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(top = 0.dp, bottom = navBottom + 24.dp),
                    ) {
                        item(key = "inbox_top_rule") { IgngHairlineDivider() }
                        itemsIndexed(items, key = { _, it -> it.id }) { idx, item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (item.isRead) colors.canvas else colors.tile)
                                    .clickable {
                                        if (!item.isRead) {
                                            container.inboxRepository.patchItemReadLocally(item.id)
                                            scope.launch {
                                                container.inboxRepository.markItemRead(item.id)
                                                container.refreshBootstrap(force = true)
                                            }
                                        }
                                        val targetPostId = item.postId
                                        if (!targetPostId.isNullOrBlank()) {
                                            onOpenPost(targetPostId)
                                        } else {
                                            detailItem = item
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!item.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(colors.accent),
                                        )
                                    }
                                    IgngBadge(
                                        text = when (item.kind) {
                                            "mention" -> "提及"
                                            "reply" -> "回复"
                                            "column" -> "专栏"
                                            else -> "系统"
                                        },
                                        tone = if (item.kind == "mention") IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                                    )
                                    Text(
                                        text = BbsFormatters.relativeTime(item.createdAt),
                                        style = IgngTheme.type.meta,
                                        color = colors.textMuted,
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = item.title,
                                    style = IgngTheme.type.bodyMedium.copy(
                                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                                    ),
                                    color = if (item.isRead) colors.textSecondary else colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (item.content.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = item.content,
                                        style = IgngTheme.type.caption,
                                        color = colors.textSecondary,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            if (idx < items.lastIndex) {
                                IgngHairlineDivider(startIndent = 16.dp, endIndent = 16.dp)
                            }
                        }
                        item(key = "inbox_bottom_rule") { IgngHairlineDivider() }
                    }
                }
            }
        }
    }

    detailItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailItem = null },
            title = { Text(item.title) },
            text = { Text(item.content.ifBlank { "无更多正文内容" }) },
            confirmButton = { TextButton(onClick = { detailItem = null }) { Text("知道了") } },
        )
    }
}
