package net.igng.mcstatus.feature.ticket

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.rememberScreenHolder
import net.igng.core.data.rememberSwrResource
import net.igng.core.data.runSuspendCatching
import net.igng.mcstatus.core.data.CreateTicketPayload
import net.igng.mcstatus.core.data.McCacheKeys
import net.igng.mcstatus.core.data.McCachePolicies
import net.igng.mcstatus.core.data.McCacheTags
import net.igng.mcstatus.core.data.TicketAccessPolicy
import net.igng.mcstatus.core.data.TicketDetailDto
import net.igng.mcstatus.core.data.TicketDraft
import net.igng.mcstatus.core.data.TicketListResponse
import net.igng.mcstatus.core.data.TicketOptionsResponse
import net.igng.mcstatus.core.data.TicketPlayerOption
import net.igng.mcstatus.core.data.TicketRepository
import net.igng.mcstatus.core.data.TicketSummaryDto
import net.igng.mcstatus.core.data.UpdateTicketPayload
import net.igng.mcstatus.core.data.UploadedMediaDto
import net.igng.core.designsystem.IgngAmbientCanvas
import net.igng.core.designsystem.IgngBadge
import net.igng.core.designsystem.IgngBadgeTone
import net.igng.core.designsystem.IgngButton
import net.igng.core.designsystem.IgngButtonStyle
import net.igng.core.designsystem.IgngSwitch
import net.igng.core.designsystem.rememberIgngPageContentPadding
import net.igng.core.designsystem.IgngConfirmDialog
import net.igng.core.designsystem.IgngDetailSheet
import net.igng.core.designsystem.IgngImageLightbox
import net.igng.core.designsystem.IgngInteractiveTile
import net.igng.core.designsystem.IgngLoadingBlock
import net.igng.core.designsystem.IgngPullToRefreshBox
import net.igng.core.designsystem.IgngSearchField
import net.igng.core.designsystem.IgngSegmentedTabs
import net.igng.core.designsystem.IgngSkeletonRows
import net.igng.core.designsystem.IgngStateBlock
import net.igng.core.designsystem.IgngTextField
import net.igng.core.designsystem.IgngTheme
import net.igng.core.designsystem.IgngTile
import net.igng.core.designsystem.IgngTopBar
import net.igng.mcstatus.core.model.IgngFormatters
import net.igng.mcstatus.core.model.SiteEndpoints
import net.igng.core.model.V1Id

private enum class TicketRelationFilter(val wire: String, val label: String) {
    ALL("all", "全部"),
    MINE("mine", "我发起的"),
    PARTICIPATED("participated", "我参与的"),
}

private class TicketsListUiHolder {
    var relation by mutableStateOf(TicketRelationFilter.ALL)
    var includeClosed by mutableStateOf(false)
    var query by mutableStateOf("")
    var submittedQuery by mutableStateOf("")
}

@Composable
fun TicketsScreen(
    ticketRepository: TicketRepository,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onCreateTicket: () -> Unit,
) {
    val colors = IgngTheme.colors
    val uiHolder = rememberScreenHolder("tickets_list_ui") { TicketsListUiHolder() }

    val (listState, listHandle) = rememberSwrResource<TicketListResponse>(
        cache = ticketRepository.swrCache,
        cacheKey = McCacheKeys.ticketsList(
            relation = uiHolder.relation.wire,
            includeClosed = uiHolder.includeClosed,
            query = uiHolder.submittedQuery,
        ),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.TICKETS),
        slotKey = "tickets_list",
    ) {
        runSuspendCatching {
            ticketRepository.fetchList(
                relation = uiHolder.relation.wire,
                includeClosed = uiHolder.includeClosed,
                query = uiHolder.submittedQuery,
                page = 1,
            )
        }
    }

    val tickets = listState.data?.resolvedTickets.orEmpty()
    val loading = listState.isInitialLoading
    val refreshing = listState.isUserRefreshing
    val error = listState.error

    IgngAmbientCanvas {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IgngTopBar(
                title = "我的工单",
                subtitle = "经由 www.igng.net/api/v1 工单服务",
                onBack = onBack,
                actions = {
                    IgngButton(
                        text = "创建工单",
                        onClick = onCreateTicket,
                        style = IgngButtonStyle.Primary,
                        icon = Icons.Rounded.Add,
                        compact = true,
                    )
                },
            )

            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IgngSegmentedTabs(TicketRelationFilter.entries, uiHolder.relation, { it.label }, { uiHolder.relation = it })
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IgngSearchField(
                        value = uiHolder.query,
                        onValueChange = {
                            uiHolder.query = it
                            if (it.isBlank()) uiHolder.submittedQuery = ""
                        },
                        placeholder = "搜索工单标题或编号…",
                        onSubmit = {
                            val next = uiHolder.query.trim()
                            if (next == uiHolder.submittedQuery) {
                                listHandle.refresh()
                            } else {
                                uiHolder.submittedQuery = next
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("含已结单", style = IgngTheme.type.caption, color = colors.textSecondary)
                        Spacer(Modifier.width(4.dp))
                        IgngSwitch(checked = uiHolder.includeClosed, onCheckedChange = { uiHolder.includeClosed = it })
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            IgngPullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { listHandle.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    loading && tickets.isEmpty() -> Box(Modifier.padding(16.dp)) { IgngSkeletonRows(4) }
                    error != null && tickets.isEmpty() -> Box(Modifier.padding(16.dp)) {
                        IgngStateBlock(title = "读取失败", description = error, isError = true, actionLabel = "重试", onAction = { listHandle.refresh() })
                    }
                    tickets.isEmpty() -> Box(Modifier.padding(16.dp)) {
                        IgngStateBlock(
                            title = "还没有工单",
                            description = "遇到游戏问题、举报或申诉时，可以创建一张工单。",
                            actionLabel = "创建工单",
                            onAction = onCreateTicket,
                        )
                    }
                    else -> LazyColumn(contentPadding = rememberIgngPageContentPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(tickets, key = { _, t -> t.id }) { _, t ->
                            val isClosed = t.status.equals("CLOSED", ignoreCase = true)
                            IgngInteractiveTile(
                                onClick = { onOpenDetail(t.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("#${t.id} ${t.title}", style = IgngTheme.type.cardTitle, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Spacer(Modifier.width(8.dp))
                                    IgngBadge(
                                        text = t.statusLabel.ifBlank { if (isClosed) "已结单" else "处理中" },
                                        tone = if (isClosed) IgngBadgeTone.Neutral else IgngBadgeTone.Success,
                                        leadingDot = !isClosed,
                                        pulse = false,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${t.typeLabel.ifBlank { t.type }} · ${t.serverName ?: "全服"} · 更新于 ${IgngFormatters.formatRelativeTime(t.updatedAt)}",
                                    style = IgngTheme.type.caption,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class TicketDetailDraftHolder {
    val replyTextState = mutableStateOf("")
}

@Composable
fun TicketDetailScreen(
    ticketId: Int,
    endpoints: SiteEndpoints,
    ticketRepository: TicketRepository,
    onBack: () -> Unit,
) {
    val colors = IgngTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draftHolder = rememberScreenHolder("ticket_detail_draft_$ticketId") { TicketDetailDraftHolder() }

    val (detailState, detailHandle) = rememberSwrResource<TicketDetailDto>(
        cache = ticketRepository.swrCache,
        cacheKey = McCacheKeys.ticketDetail(ticketId),
        scope = IgngCacheScope.USER_ONLY,
        policy = McCachePolicies.Standard,
        tags = setOf(McCacheTags.TICKETS),
        slotKey = "ticket_detail_$ticketId",
    ) {
        runSuspendCatching { ticketRepository.fetchDetail(ticketId) }
    }

    val detail = detailState.data
    val loading = detailState.isInitialLoading
    val error = detailState.error
    var replyText by draftHolder.replyTextState
    var sending by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    val pendingUploads = remember { mutableStateListOf<UploadedMediaDto>() }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf("") }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    fun reload() {
        detailHandle.silentRefresh()
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                actionFeedback = null
                runCatching {
                    val (name, mime, bytes) = readUriBytes(context, uri)
                    ticketRepository.uploadAttachment(name, mime, bytes)
                }.onSuccess { pendingUploads.add(it) }
                    .onFailure { actionFeedback = it.message ?: "图片上传失败" }
                uploading = false
            }
        }
    }

    IgngAmbientCanvas {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            IgngTopBar(
                title = detail?.let { "#${it.id}" } ?: "工单详情",
                subtitle = detail?.title,
                onBack = onBack,
                actions = {
                    detail?.let { t ->
                        if (t.effectiveCanEdit) {
                            IgngButton(
                                text = "编辑",
                                onClick = { editTitle = t.title; showEditSheet = true },
                                style = IgngButtonStyle.Secondary,
                                icon = Icons.Rounded.Edit,
                                compact = true,
                            )
                        }
                        if (t.effectiveCanClose && !t.status.equals("CLOSED", true)) {
                            IgngButton(
                                text = "结单",
                                onClick = { showCloseConfirm = true },
                                style = IgngButtonStyle.Danger,
                                icon = Icons.Rounded.CheckCircle,
                                compact = true,
                            )
                        } else if (t.effectiveCanReopen && t.status.equals("CLOSED", true)) {
                            IgngButton(
                                text = "重新打开",
                                onClick = {
                                    scope.launch {
                                        runCatching { ticketRepository.reopen(ticketId) }
                                            .onSuccess { reload() }
                                            .onFailure { actionFeedback = it.message }
                                    }
                                },
                                style = IgngButtonStyle.Primary,
                                icon = Icons.Rounded.Replay,
                                compact = true,
                            )
                        }
                    }
                },
            )

            actionFeedback?.let { msg ->
                Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    IgngBadge(msg, tone = IgngBadgeTone.Warning)
                }
            }

            when {
                loading -> IgngLoadingBlock(fill = true)
                error != null -> Box(Modifier.padding(16.dp)) {
                    IgngStateBlock(title = "读取失败", description = error, isError = true, actionLabel = "重试", onAction = { detailHandle.refresh() })
                }
                detail != null -> {
                    val ticket = detail!!
                    IgngPullToRefreshBox(
                        isRefreshing = detailState.isUserRefreshing,
                        onRefresh = { detailHandle.refresh() },
                        modifier = Modifier.weight(1f),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                        item(key = "meta") {
                            IgngTile(modifier = Modifier.fillMaxWidth()) {
                                Text(ticket.title, style = IgngTheme.type.sectionTitle, color = colors.textPrimary)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val closed = ticket.status.equals("CLOSED", true)
                                    IgngBadge(ticket.statusLabel.ifBlank { if (closed) "已结单" else "处理中" }, tone = if (closed) IgngBadgeTone.Neutral else IgngBadgeTone.Success)
                                    IgngBadge(ticket.typeLabel.ifBlank { ticket.type })
                                    IgngBadge(ticket.serverName ?: "全服")
                                }
                                if (ticket.targets.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("涉及玩家：${ticket.targets.joinToString("、") { it.mcUsername }}", style = IgngTheme.type.caption, color = colors.textSecondary)
                                }
                            }
                        }

                        itemsIndexed(ticket.messages, key = { _, m -> m.id }) { _, msg ->
                            IgngTile(
                                soft = !msg.isInitial,
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(14.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(msg.authorName, style = IgngTheme.type.bodyMedium.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary)
                                        if (msg.isAdmin) {
                                            IgngBadge("管理员", tone = IgngBadgeTone.Accent)
                                        }
                                    }
                                    Text(IgngFormatters.formatIsoDateTime(msg.createdAt, short = true), style = IgngTheme.type.badge, color = colors.textMuted)
                                }
                                if (msg.content.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(msg.content, style = IgngTheme.type.body, color = colors.textSecondary)
                                }
                                if (msg.attachments.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        msg.attachments.forEach { att ->
                                            val resolved = endpoints.mediaContentUrl(att.resolvedMediaId)
                                            AsyncImage(
                                                model = resolved,
                                                contentDescription = "工单图片附件",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(96.dp)
                                                    .clip(IgngTheme.radii.sm)
                                                    .border(1.dp, colors.line, IgngTheme.radii.sm)
                                                    .clickable { previewUrl = resolved },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }

                    // Reply composer bar
                    if (ticket.effectiveCanReply) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.tile)
                                .border(1.dp, colors.line)
                                .navigationBarsPadding()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (pendingUploads.isNotEmpty()) {
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    pendingUploads.forEach { media ->
                                        Box(Modifier.size(56.dp)) {
                                            AsyncImage(
                                                model = endpoints.mediaContentUrl(media.id),
                                                contentDescription = "待发送图片",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(IgngTheme.radii.sm),
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(20.dp)
                                                    .clip(IgngTheme.radii.pill)
                                                    .background(colors.danger)
                                                    .clickable { pendingUploads.remove(media) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Icon(Icons.Rounded.Close, "移除", tint = colors.onAccent, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IgngButton(
                                    text = if (uploading) "上传中" else "附图",
                                    onClick = {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    style = IgngButtonStyle.Secondary,
                                    icon = Icons.Rounded.AddPhotoAlternate,
                                    loading = uploading,
                                    compact = true,
                                )
                                IgngTextField(
                                    value = replyText,
                                    onValueChange = { replyText = it },
                                    label = "",
                                    placeholder = "输入回复内容…",
                                    singleLine = false,
                                    minLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                IgngButton(
                                    text = "发送",
                                    loading = sending,
                                    enabled = replyText.isNotBlank() || pendingUploads.isNotEmpty(),
                                    icon = Icons.AutoMirrored.Rounded.Send,
                                    compact = true,
                                    onClick = {
                                        scope.launch {
                                            sending = true
                                            actionFeedback = null
                                            runCatching {
                                                ticketRepository.reply(
                                                    id = ticketId,
                                                    content = replyText.trim(),
                                                    attachmentMediaIds = pendingUploads.map { it.id },
                                                )
                                            }.onSuccess {
                                                replyText = ""
                                                pendingUploads.clear()
                                                reload()
                                            }.onFailure { actionFeedback = it.message ?: "发送失败" }
                                            sending = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewUrl?.let { url ->
        IgngImageLightbox(imageUrl = url, title = "工单附件", onDismiss = { previewUrl = null })
    }

    if (showCloseConfirm) {
        IgngConfirmDialog(
            title = "结单 #$ticketId",
            message = "结单后该工单将标记为已完成；若之后需要补充，可以再次重新打开。",
            confirmLabel = "确认结单",
            danger = true,
            onDismiss = { showCloseConfirm = false },
            onConfirm = {
                showCloseConfirm = false
                scope.launch {
                    runCatching { ticketRepository.close(ticketId) }
                        .onSuccess { reload() }
                        .onFailure { actionFeedback = it.message }
                }
            },
        )
    }

    if (showEditSheet && detail != null) {
        IgngDetailSheet(onDismiss = { showEditSheet = false }, title = "编辑工单标题") {
            IgngTextField(value = editTitle, onValueChange = { editTitle = it }, label = "工单标题")
            Spacer(Modifier.height(12.dp))
            IgngButton(
                text = "保存修改",
                enabled = editTitle.trim().isNotEmpty(),
                onClick = {
                    val t = detail!!
                    scope.launch {
                        runCatching {
                            ticketRepository.updateTicket(
                                id = t.id,
                                etag = t.etag,
                                payload = UpdateTicketPayload(
                                    title = editTitle.trim(),
                                ),
                            )
                        }.onSuccess {
                            ticketRepository.swrCache.put(
                                key = McCacheKeys.ticketDetail(ticketId),
                                data = it,
                                scope = IgngCacheScope.USER_ONLY,
                                tags = setOf(McCacheTags.TICKETS),
                            )
                            showEditSheet = false
                        }.onFailure { actionFeedback = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateTicketScreen(
    endpoints: SiteEndpoints,
    ticketRepository: TicketRepository,
    initialServerId: Int? = null,
    onBack: () -> Unit,
    onCreated: (Int) -> Unit,
) {
    val colors = IgngTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var options by remember { mutableStateOf<TicketOptionsResponse?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var selectedServerId by remember(initialServerId) { mutableStateOf<Int?>(initialServerId) }
    var targetSearch by remember { mutableStateOf("") }
    var playerMatches by remember { mutableStateOf<List<TicketPlayerOption>>(emptyList()) }
    val selectedTargets = remember { mutableStateListOf<String>() }
    val uploadedImages = remember { mutableStateListOf<UploadedMediaDto>() }
    var uploading by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ticketRepository.readDraft()?.let { d ->
            if (title.isEmpty()) title = d.title
            if (content.isEmpty()) content = d.content
            if (selectedType.isEmpty()) selectedType = d.type
        }
        runCatching { ticketRepository.fetchOptions() }.onSuccess { opt ->
            options = opt
            if (selectedType.isBlank()) selectedType = opt.types.firstOrNull()?.value.orEmpty()
        }.onFailure { error = it.message ?: "无法读取工单配置" }
    }

    // Auto-save local draft so user never loses work
    LaunchedEffect(title, content, selectedType) {
        delay(600L)
        if (title.isNotBlank() || content.isNotBlank()) {
            ticketRepository.saveDraft(TicketDraft(title, content, selectedType, System.currentTimeMillis()))
        }
    }

    LaunchedEffect(targetSearch) {
        if (targetSearch.trim().length < 2) {
            playerMatches = emptyList()
            return@LaunchedEffect
        }
        delay(280L)
        playerMatches = runCatching { ticketRepository.searchPlayers(targetSearch) }.getOrDefault(emptyList())
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                uploading = true
                runCatching {
                    val (name, mime, bytes) = readUriBytes(context, uri)
                    ticketRepository.uploadAttachment(name, mime, bytes)
                }.onSuccess { uploadedImages.add(it) }
                    .onFailure { error = it.message ?: "图片上传失败" }
                uploading = false
            }
        }
    }

    IgngAmbientCanvas {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            IgngTopBar(title = "创建工单", subtitle = "草稿自动保存在本机", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                error?.let { IgngBadge(it, tone = IgngBadgeTone.Danger) }

                IgngTextField(value = title, onValueChange = { title = it }, label = "工单标题", placeholder = "用一句话概括你的问题或诉求")

                options?.types?.takeIf { it.isNotEmpty() }?.let { types ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("工单类型", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            types.forEach { opt ->
                                IgngBadge(
                                    text = opt.label,
                                    tone = if (selectedType == opt.value) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                                    modifier = Modifier.clickable { selectedType = opt.value },
                                )
                            }
                        }
                    }
                }

                options?.servers?.takeIf { it.isNotEmpty() }?.let { srvs ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("涉及服务器", style = IgngTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IgngBadge(
                                text = "全服通用",
                                tone = if (selectedServerId == null) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                                modifier = Modifier.clickable { selectedServerId = null },
                            )
                            srvs.forEach { srv ->
                                IgngBadge(
                                    text = srv.name,
                                    tone = if (selectedServerId == srv.id) IgngBadgeTone.Accent else IgngBadgeTone.Neutral,
                                    modifier = Modifier.clickable { selectedServerId = srv.id },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    IgngTextField(
                        value = targetSearch,
                        onValueChange = { targetSearch = it },
                        label = "涉及玩家（可选）",
                        placeholder = "输入至少 2 个字符检索 MC 玩家名",
                    )
                    if (playerMatches.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            playerMatches.take(8).forEach { p ->
                                IgngBadge(
                                    text = "+ ${p.username}",
                                    tone = IgngBadgeTone.Accent,
                                    modifier = Modifier.clickable {
                                        if (p.username !in selectedTargets) selectedTargets.add(p.username)
                                        targetSearch = ""
                                        playerMatches = emptyList()
                                    },
                                )
                            }
                        }
                    }
                    if (selectedTargets.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedTargets.forEach { name ->
                                IgngBadge(
                                    text = "$name ×",
                                    tone = IgngBadgeTone.Success,
                                    modifier = Modifier.clickable { selectedTargets.remove(name) },
                                )
                            }
                        }
                    }
                }

                IgngTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = "详细说明",
                    placeholder = "写清发生时间、服务器、坐标或处理诉求…",
                    singleLine = false,
                    minLines = 5,
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IgngButton(
                        text = if (uploading) "正在上传…" else "添加图片证据",
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        style = IgngButtonStyle.Secondary,
                        icon = Icons.Rounded.AddPhotoAlternate,
                        loading = uploading,
                        compact = true,
                    )
                    if (uploadedImages.isNotEmpty()) {
                        Text("已附 ${uploadedImages.size} 张", style = IgngTheme.type.caption, color = colors.textSecondary)
                    }
                }
                if (uploadedImages.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uploadedImages.forEach { img ->
                            AsyncImage(
                                model = endpoints.mediaContentUrl(img.id),
                                contentDescription = "附件预览",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp).clip(IgngTheme.radii.sm),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                IgngButton(
                    text = "提交工单",
                    loading = submitting,
                    enabled = title.trim().isNotEmpty() && content.trim().isNotEmpty() && selectedType.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            submitting = true
                            error = null
                            runCatching {
                                ticketRepository.createTicket(
                                    CreateTicketPayload(
                                        title = title.trim(),
                                        content = content.trim(),
                                        ticketType = selectedType,
                                        serverScope = if (selectedServerId == null) "all" else "specific",
                                        serverId = selectedServerId?.let { V1Id.format("srv", it) },
                                        targets = selectedTargets.toList(),
                                        attachmentIds = uploadedImages.map { it.id },
                                        accessPolicy = null,
                                    ),
                                )
                            }.onSuccess { id ->
                                if (id > 0) onCreated(id) else onBack()
                            }.onFailure { error = it.message ?: "提交工单失败" }
                            submitting = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

private suspend fun readUriBytes(context: Context, uri: Uri): Triple<String, String, ByteArray> =
    withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            else -> "jpg"
        }
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选图片")
        Triple("attachment_${System.currentTimeMillis()}.$ext", mime, bytes)
    }
