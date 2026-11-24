package net.igng.core.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Unified UI loading projection for SWR-backed screens.
 *
 * Golden invariant:
 * - [isInitialLoading] is ONLY `true` when [data] is `null` and a cold load is in progress.
 * - Background silent revalidation sets [isRevalidating] = `true` while keeping [data] untouched
 *   and rendering completely unobtrusively (zero skeletons, zero dashes, zero layout shifts).
 * - Explicit Pull-to-Refresh sets [isUserRefreshing] = `true` while keeping [data] visible.
 */
@Immutable
data class SwrUiState<T>(
    val data: T? = null,
    val isInitialLoading: Boolean = false,
    val isRevalidating: Boolean = false,
    val isUserRefreshing: Boolean = false,
    val error: String? = null,
    val lastUpdatedAtMs: Long? = null,
) {
    val hasData: Boolean get() = data != null
}

/**
 * Lightweight, reflection-free [ViewModel] automatically scoped to the enclosing `NavBackStackEntry`.
 * Survives push/pop, predictive back gestures, top-level tab `saveState/restoreState`, and
 * configuration changes, while clearing cleanly when the destination is popped off the back stack.
 */
class SwrScreenHostViewModel : ViewModel() {
    private val holders = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: String, factory: (CoroutineScope) -> T): T =
        holders.getOrPut(key) { factory(viewModelScope) } as T

    override fun onCleared() {
        holders.clear()
        super.onCleared()
    }
}

/**
 * Retains an arbitrary screen state holder inside the current `NavBackStackEntry`'s [ViewModelStore].
 * Zero reflection (uses `CreationExtras` initializer lambda), 100% R8-safe.
 */
@Composable
fun <T : Any> rememberScreenHolder(
    key: String = "default",
    factory: (CoroutineScope) -> T,
): T {
    val host: SwrScreenHostViewModel = viewModel { SwrScreenHostViewModel() }
    return remember(host, key) { host.getOrPut(key, factory) }
}

/**
 * Stateful controller backing [rememberSwrResource], retained across composition restarts inside
 * [SwrScreenHostViewModel] so even active screens retain Frame-0 data during predictive back.
 */
@Stable
class SwrResourceHandle<T : Any>(
    private val cache: IgngSwrCache,
    private val holderScope: CoroutineScope,
) {
    var activeKey: String by mutableStateOf("")
        private set
    var activeScope: IgngCacheScope by mutableStateOf(IgngCacheScope.SESSION_AWARE)
        private set
    var activePolicy: IgngFreshnessPolicy by mutableStateOf(IgngFreshnessPolicy.Standard)
        private set
    var activeTags: Set<String> by mutableStateOf(emptySet())
        private set
    var activeDiskCodec: IgngSwrDiskCodec<T>? by mutableStateOf(null)
        private set
    var activeLegacyRawProvider: (suspend () -> String?)? by mutableStateOf(null)
        private set
    var activeFetcher: (suspend () -> Result<T>)? = null

    private var lastKnownSessionStamp: String = cache.sessionStamp.value
    private var retainedData: T? = null
    private var retainedUpdatedAtMs: Long? = null
    private var initialLoading by mutableStateOf(false)
    private var revalidating by mutableStateOf(false)
    private var userRefreshing by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var activeJob: Job? = null

    fun syncConfig(
        key: String,
        scope: IgngCacheScope,
        policy: IgngFreshnessPolicy,
        tags: Set<String>,
        diskCodec: IgngSwrDiskCodec<T>?,
        legacyRawProvider: (suspend () -> String?)?,
        fetcher: suspend () -> Result<T>,
        enabled: Boolean,
        sessionStamp: String,
    ) {
        val keyChanged = activeKey != key
        val sessionChanged = scope != IgngCacheScope.PUBLIC && lastKnownSessionStamp != sessionStamp
        activeKey = key
        activeScope = scope
        activePolicy = policy
        activeTags = tags
        activeDiskCodec = diskCodec
        activeLegacyRawProvider = legacyRawProvider
        activeFetcher = fetcher
        lastKnownSessionStamp = sessionStamp

        if (!enabled) {
            if (sessionChanged || keyChanged) {
                retainedData = null
                retainedUpdatedAtMs = null
                errorMessage = null
            }
            initialLoading = false
            revalidating = false
            userRefreshing = false
            return
        }

        if (keyChanged || sessionChanged) {
            activeJob?.cancel()
            val peeked = cache.peek<T>(key, scope)
            retainedData = peeked?.data
            retainedUpdatedAtMs = peeked?.fetchedAtMs
            errorMessage = null
            initialLoading = peeked == null
            revalidating = false
            userRefreshing = false
        }
    }

    fun snapshot(observedEntry: IgngCacheEntry<T>?, enabled: Boolean): SwrUiState<T> {
        if (!enabled) {
            return SwrUiState(data = null, isInitialLoading = false)
        }
        val effectiveData = observedEntry?.data ?: retainedData
        val effectiveUpdatedAt = observedEntry?.fetchedAtMs ?: retainedUpdatedAtMs
        if (observedEntry != null) {
            retainedData = observedEntry.data
            retainedUpdatedAtMs = observedEntry.fetchedAtMs
        }
        return SwrUiState(
            data = effectiveData,
            isInitialLoading = effectiveData == null && initialLoading,
            isRevalidating = effectiveData != null && revalidating,
            isUserRefreshing = userRefreshing,
            error = if (effectiveData == null) errorMessage else null,
            lastUpdatedAtMs = effectiveUpdatedAt,
        )
    }

    /** Called immediately on cold entry (`data == null`) so fetch runs in parallel with enter animation. */
    fun loadInitialIfNeeded() {
        val key = activeKey.ifBlank { return }
        val fetcher = activeFetcher ?: return
        val existing = cache.peek<T>(key, activeScope)?.data ?: retainedData
        if (existing != null) {
            initialLoading = false
            return
        }
        launchFetch(key = key, force = false, userInitiated = false, fetcher = fetcher)
    }

    /** Called strictly on `ON_RESUME` (never during predictive-back peeking). */
    fun revalidateIfStale() {
        val key = activeKey.ifBlank { return }
        val fetcher = activeFetcher ?: return
        launchFetch(key = key, force = false, userInitiated = false, fetcher = fetcher)
    }

    /** Explicit pull-to-refresh or manual retry: bypasses TTL while preserving current content. */
    fun refresh() {
        val key = activeKey.ifBlank { return }
        val fetcher = activeFetcher ?: return
        launchFetch(key = key, force = true, userInitiated = true, fetcher = fetcher)
    }

    /** Silent forced refresh (used by foreground polling or post-mutation local refresh). */
    fun silentRefresh() {
        val key = activeKey.ifBlank { return }
        val fetcher = activeFetcher ?: return
        launchFetch(key = key, force = true, userInitiated = false, fetcher = fetcher)
    }

    /** Optimistically mutates both the local retained snapshot and the shared [IgngSwrCache] entry. */
    fun mutateLocal(transform: (T) -> T) {
        val key = activeKey.ifBlank { return }
        retainedData?.let { cur -> retainedData = transform(cur) }
        cache.patch(key, transform)
    }

    private fun launchFetch(
        key: String,
        force: Boolean,
        userInitiated: Boolean,
        fetcher: suspend () -> Result<T>,
    ) {
        if (userInitiated) {
            activeJob?.cancel()
        } else if (activeJob?.isActive == true) {
            return
        }

        val scope = activeScope
        val policy = activePolicy
        val tags = activeTags
        val diskCodec = activeDiskCodec
        val legacyRawProvider = activeLegacyRawProvider

        activeJob = holderScope.launch {
            val cachedBefore = cache.peek<T>(key, scope)
                ?: if (diskCodec != null) {
                    cache.hydrateFromDisk(key, scope, tags, diskCodec, legacyRawProvider)
                } else {
                    null
                }

            if (cachedBefore != null) {
                retainedData = cachedBefore.data
                retainedUpdatedAtMs = cachedBefore.fetchedAtMs
                initialLoading = false
            }

            if (!force && cachedBefore != null && cachedBefore.isFresh(System.currentTimeMillis(), policy)) {
                revalidating = false
                userRefreshing = false
                return@launch
            }

            val hasExisting = (cachedBefore?.data ?: retainedData) != null
            if (userInitiated) {
                userRefreshing = true
            } else if (hasExisting) {
                revalidating = true
            } else {
                initialLoading = true
            }
            if (!hasExisting) {
                errorMessage = null
            }

            val result = cache.fetch(
                key = key,
                scope = scope,
                policy = policy,
                tags = tags,
                force = force,
                diskCodec = diskCodec,
                legacyRawProvider = legacyRawProvider,
                fetcher = fetcher,
            )

            if (activeKey == key) {
                result.onSuccess { value ->
                    retainedData = value
                    retainedUpdatedAtMs = System.currentTimeMillis()
                    errorMessage = null
                }.onFailure { err ->
                    if (retainedData == null) {
                        errorMessage = err.message ?: "数据暂时无法读取"
                    }
                }
                initialLoading = false
                revalidating = false
                userRefreshing = false
            }
        }
    }
}

/**
 * Composable bridge connecting a screen to [IgngSwrCache] + [SwrScreenHostViewModel] +
 * predictive-back-safe [LifecycleResumeEffect].
 */
@Composable
fun <T : Any> rememberSwrResource(
    cache: IgngSwrCache,
    cacheKey: String,
    scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
    policy: IgngFreshnessPolicy = IgngFreshnessPolicy.Standard,
    tags: Set<String> = emptySet(),
    diskCodec: IgngSwrDiskCodec<T>? = null,
    legacyRawProvider: (suspend () -> String?)? = null,
    enabled: Boolean = true,
    pollIntervalMs: Long = 0L,
    slotKey: String = "swr_default",
    fetcher: suspend () -> Result<T>,
): Pair<SwrUiState<T>, SwrResourceHandle<T>> {
    val sessionStamp by cache.sessionStamp.collectAsState()
    val handle = rememberScreenHolder(key = slotKey) { vmScope ->
        SwrResourceHandle<T>(cache = cache, holderScope = vmScope)
    }

    handle.syncConfig(
        key = cacheKey,
        scope = scope,
        policy = policy,
        tags = tags,
        diskCodec = diskCodec,
        legacyRawProvider = legacyRawProvider,
        fetcher = fetcher,
        enabled = enabled,
        sessionStamp = sessionStamp,
    )

    val observed by remember(cache, cacheKey, scope) {
        cache.observe<T>(cacheKey, scope)
    }.collectAsState()

    val validObserved = observed?.takeIf {
        scope == IgngCacheScope.PUBLIC || it.sessionStamp == sessionStamp
    }
    val uiState = handle.snapshot(validObserved, enabled)

    // Cold initial load starts immediately during push animation (does nothing if data != null).
    LaunchedEffect(cacheKey, enabled, sessionStamp) {
        if (enabled) {
            handle.loadInitialIfNeeded()
        }
    }

    // Revalidation ONLY fires when NavBackStackEntry actually reaches RESUMED (never during predictive-back drag).
    LifecycleResumeEffect(cacheKey, enabled, sessionStamp) {
        if (enabled) {
            handle.revalidateIfStale()
        }
        onPauseOrDispose { }
    }

    // Optional foreground silent polling for realtime screens (auto-pauses when backgrounded).
    if (pollIntervalMs > 0L) {
        LaunchedForegroundPollingEffect(
            intervalMs = pollIntervalMs,
            key1 = cacheKey,
            key2 = sessionStamp,
            enabled = enabled,
        ) {
            handle.silentRefresh()
        }
    }

    return uiState to handle
}

/**
 * Triggers [onForegroundResume] when the host Activity returns from background after at least
 * [minBackgroundMs] milliseconds.
 */
@Composable
fun LaunchedAppForegroundResumeEffect(
    minBackgroundMs: Long = 30_000L,
    onForegroundResume: suspend () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestCallback by rememberUpdatedState(onForegroundResume)
    var backgroundedAtMs by remember { mutableStateOf<Long?>(null) }
    var resumeTrigger by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner, minBackgroundMs) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    backgroundedAtMs = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_START -> {
                    val awaySince = backgroundedAtMs
                    if (awaySince != null && (System.currentTimeMillis() - awaySince) >= minBackgroundMs) {
                        backgroundedAtMs = null
                        resumeTrigger++
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger > 0) {
            latestCallback()
        }
    }
}
