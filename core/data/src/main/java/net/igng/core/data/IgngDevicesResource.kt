package net.igng.core.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.CorePaginatedResponseDto
import net.igng.core.model.CorePaginationDto
import net.igng.core.network.ApiResult

/**
 * Pure pagination & optimistic mutation helpers for `/api/v1/devices` SWR entries.
 */
object IgngDevicesPaginationLogic {
    const val DEFAULT_PAGE_SIZE: Int = 30

    fun identityKey(device: CoreDeviceSessionDto): String =
        device.id.ifBlank { device.sessionId }

    fun resolvedTotalCount(page: CorePaginatedResponseDto<CoreDeviceSessionDto>?): Int {
        if (page == null) return 0
        return maxOf(page.pagination.total, page.data.size)
    }

    fun hasMorePages(page: CorePaginatedResponseDto<CoreDeviceSessionDto>?): Boolean {
        if (page == null) return false
        val pagination = page.pagination
        if (pagination.totalPages > pagination.page) return true
        if (pagination.total > page.data.size) return true
        return false
    }

    fun mergeNextPage(
        current: CorePaginatedResponseDto<CoreDeviceSessionDto>,
        nextPage: CorePaginatedResponseDto<CoreDeviceSessionDto>,
    ): CorePaginatedResponseDto<CoreDeviceSessionDto> {
        val seen = HashSet<String>(current.data.size + nextPage.data.size)
        val merged = ArrayList<CoreDeviceSessionDto>(current.data.size + nextPage.data.size)
        for (item in current.data) {
            val key = identityKey(item)
            if (key.isEmpty() || seen.add(key)) {
                merged.add(item)
            }
        }
        var appended = 0
        for (item in nextPage.data) {
            val key = identityKey(item)
            if (key.isEmpty() || seen.add(key)) {
                merged.add(item)
                appended++
            }
        }
        val nextPageNum = maxOf(current.pagination.page, nextPage.pagination.page)
        val nextPageSize = nextPage.pagination.pageSize.takeIf { it > 0 } ?: current.pagination.pageSize
        val effectiveTotal = if (appended == 0 && nextPage.data.isEmpty()) {
            merged.size
        } else {
            maxOf(nextPage.pagination.total, merged.size)
        }
        val effectiveTotalPages = if (appended == 0 && nextPage.data.isEmpty()) {
            nextPageNum
        } else {
            maxOf(nextPage.pagination.totalPages, nextPageNum)
        }
        return CorePaginatedResponseDto(
            data = merged,
            pagination = CorePaginationDto(
                page = nextPageNum,
                pageSize = nextPageSize,
                total = effectiveTotal,
                totalPages = effectiveTotalPages,
            ),
        )
    }

    fun applyRenamedDevice(
        current: CorePaginatedResponseDto<CoreDeviceSessionDto>,
        deviceId: String,
        updated: CoreDeviceSessionDto,
    ): CorePaginatedResponseDto<CoreDeviceSessionDto> {
        val updatedList = current.data.map { existing ->
            if (existing.id == deviceId || (updated.id.isNotBlank() && existing.id == updated.id)) {
                updated
            } else {
                existing
            }
        }
        return current.copy(data = updatedList)
    }

    fun applyRevokedDevice(
        current: CorePaginatedResponseDto<CoreDeviceSessionDto>,
        deviceId: String,
    ): CorePaginatedResponseDto<CoreDeviceSessionDto> {
        val remaining = current.data.filterNot { it.id == deviceId }
        val removedCount = (current.data.size - remaining.size).coerceAtLeast(0)
        val nextTotal = (current.pagination.total - removedCount).coerceAtLeast(remaining.size)
        val pageSize = current.pagination.pageSize.takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE
        val computedTotalPages = if (nextTotal == 0) {
            1
        } else {
            ((nextTotal + pageSize - 1) / pageSize).coerceAtLeast(1)
        }
        return CorePaginatedResponseDto(
            data = remaining,
            pagination = current.pagination.copy(
                total = nextTotal,
                totalPages = minOf(current.pagination.totalPages.coerceAtLeast(1), computedTotalPages),
            ),
        )
    }
}

/**
 * Shared stateful controller for active login devices across Settings entry cards and the
 * dedicated Login Devices management secondary screen.
 *
 * Backing both screens with the same [IgngSwrCache] key guarantees Frame-0 navigation into the
 * secondary screen and instant count/name synchronization upon returning to Settings.
 */
@Stable
class IgngDevicesController(
    private val swrCache: IgngSwrCache,
    private val accountRepository: IgngAccountRepository,
    private val handle: SwrResourceHandle<CorePaginatedResponseDto<CoreDeviceSessionDto>>,
    private val holderScope: CoroutineScope,
    private val pageSize: Int = IgngDevicesPaginationLogic.DEFAULT_PAGE_SIZE,
) {
    var onShowMessage: (String) -> Unit = {}

    var uiState: SwrUiState<CorePaginatedResponseDto<CoreDeviceSessionDto>> by mutableStateOf(SwrUiState())
        internal set

    var loadingMore: Boolean by mutableStateOf(false)
        private set

    var busyDeviceIds: Set<String> by mutableStateOf(emptySet())
        private set

    private var loadMoreJob: Job? = null

    val devices: List<CoreDeviceSessionDto>
        get() = uiState.data?.data.orEmpty()

    val totalCount: Int
        get() = IgngDevicesPaginationLogic.resolvedTotalCount(uiState.data)

    val hasMore: Boolean
        get() = IgngDevicesPaginationLogic.hasMorePages(uiState.data)

    val isInitialLoading: Boolean
        get() = uiState.isInitialLoading

    val isUserRefreshing: Boolean
        get() = uiState.isUserRefreshing

    val isRevalidating: Boolean
        get() = uiState.isRevalidating

    val error: String?
        get() = uiState.error

    internal fun resetTransientState() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        loadingMore = false
        busyDeviceIds = emptySet()
    }

    fun refresh() {
        loadMoreJob?.cancel()
        loadMoreJob = null
        loadingMore = false
        handle.refresh()
    }

    fun loadMore() {
        val current = uiState.data ?: return
        if (loadingMore || !IgngDevicesPaginationLogic.hasMorePages(current)) return

        val nextPage = current.pagination.page.coerceAtLeast(1) + 1
        val stampAtStart = swrCache.sessionStamp.value
        val keyAtStart = handle.activeKey

        loadingMore = true
        loadMoreJob?.cancel()
        loadMoreJob = holderScope.launch {
            try {
                when (val res = accountRepository.listDevices(page = nextPage, pageSize = pageSize)) {
                    is ApiResult.Success -> {
                        if (swrCache.sessionStamp.value == stampAtStart && handle.activeKey == keyAtStart) {
                            handle.mutateLocal { latest ->
                                IgngDevicesPaginationLogic.mergeNextPage(latest, res.data)
                            }
                        }
                    }
                    is ApiResult.Failure -> {
                        onShowMessage(res.error.message)
                    }
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun renameDevice(deviceId: String, newName: String) {
        val trimmed = newName.trim()
        if (deviceId.isBlank() || trimmed.isEmpty() || deviceId in busyDeviceIds) return
        val loadedPages = uiState.data?.pagination?.page ?: 1

        busyDeviceIds = busyDeviceIds + deviceId
        holderScope.launch {
            try {
                when (val res = accountRepository.renameDevice(deviceId, trimmed)) {
                    is ApiResult.Success -> {
                        handle.mutateLocal { latest ->
                            IgngDevicesPaginationLogic.applyRenamedDevice(latest, deviceId, res.data)
                        }
                        onShowMessage("设备名称已更新")
                        if (loadedPages <= 1) {
                            handle.silentRefresh()
                        }
                    }
                    is ApiResult.Failure -> {
                        onShowMessage(res.error.message)
                    }
                }
            } finally {
                busyDeviceIds = busyDeviceIds - deviceId
            }
        }
    }

    fun revokeDevice(deviceId: String) {
        if (deviceId.isBlank() || deviceId in busyDeviceIds) return
        val loadedPages = uiState.data?.pagination?.page ?: 1

        busyDeviceIds = busyDeviceIds + deviceId
        holderScope.launch {
            try {
                when (val res = accountRepository.revokeDevice(deviceId)) {
                    is ApiResult.Success -> {
                        handle.mutateLocal { latest ->
                            IgngDevicesPaginationLogic.applyRevokedDevice(latest, deviceId)
                        }
                        onShowMessage("已吊销该设备会话")
                        if (loadedPages <= 1) {
                            handle.silentRefresh()
                        }
                    }
                    is ApiResult.Failure -> {
                        onShowMessage(res.error.message)
                    }
                }
            } finally {
                busyDeviceIds = busyDeviceIds - deviceId
            }
        }
    }
}

@Composable
fun rememberIgngDevicesResource(
    cache: IgngSwrCache,
    accountRepository: IgngAccountRepository,
    cacheKey: String,
    policy: IgngFreshnessPolicy = IgngFreshnessPolicy.Standard,
    tags: Set<String> = emptySet(),
    enabled: Boolean = true,
    slotKey: String = "igng_devices_slot",
    pageSize: Int = IgngDevicesPaginationLogic.DEFAULT_PAGE_SIZE,
    onShowMessage: (String) -> Unit = {},
): IgngDevicesController {
    val latestOnShowMessage by rememberUpdatedState(onShowMessage)
    val sessionStamp by cache.sessionStamp.collectAsState()

    val (state, handle) = rememberSwrResource<CorePaginatedResponseDto<CoreDeviceSessionDto>>(
        cache = cache,
        cacheKey = cacheKey,
        scope = IgngCacheScope.USER_ONLY,
        policy = policy,
        tags = tags,
        enabled = enabled,
        slotKey = slotKey,
    ) {
        accountRepository.listDevices(page = 1, pageSize = pageSize).toKotlinResult()
    }

    val controller = rememberScreenHolder(key = "${slotKey}_ctrl") { vmScope ->
        IgngDevicesController(
            swrCache = cache,
            accountRepository = accountRepository,
            handle = handle,
            holderScope = vmScope,
            pageSize = pageSize,
        )
    }

    controller.uiState = state
    controller.onShowMessage = remember { { msg -> latestOnShowMessage(msg) } }

    LaunchedEffect(enabled, sessionStamp, cacheKey) {
        if (!enabled) {
            controller.resetTransientState()
        }
    }

    return controller
}
