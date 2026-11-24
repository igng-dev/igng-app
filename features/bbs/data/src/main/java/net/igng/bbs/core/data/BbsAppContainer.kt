package net.igng.bbs.core.data

import android.content.Context
import coil3.ImageLoader
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import net.igng.bbs.core.model.BbsBootstrapDto
import net.igng.bbs.core.model.DailyLoginDto
import net.igng.bbs.core.model.SiteEndpoints
import net.igng.core.auth.IgngSessionManager
import net.igng.core.auth.SecureSessionStore
import net.igng.core.data.IgngAccountRepository
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.IgngImageLoaderFactory
import net.igng.core.data.IgngLocalJsonCache
import net.igng.core.data.IgngSwrCache
import net.igng.core.data.toKotlinResult
import net.igng.core.model.AppUpdateConfig
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiException
import net.igng.core.network.ApiResult
import net.igng.core.network.IgngClientConfig
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.updater.AppUpdateManager

class BbsAppContainer(
    context: Context,
    val endpoints: SiteEndpoints,
    val appVersion: String,
    val appVersionCode: Int = 0,
) {
    val appContext: Context = context.applicationContext
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val sessionManager = IgngSessionManager(
        context = appContext,
        oauthConfig = BbsOAuthManager.oauthConfig(endpoints, appVersion),
        vaultConfig = BbsOAuthManager.VAULT_CONFIG,
    )
    val sessionStore: SecureSessionStore get() = sessionManager.sessionStore
    val oauthManager = BbsOAuthManager(sessionManager)
    val sessionExpiredNotifier: SessionExpiredNotifier get() = sessionManager.sessionExpiredNotifier

    val activeAccount: StateFlow<SavedAccount?> get() = sessionManager.activeAccount
    val accounts: StateFlow<List<SavedAccount>> get() = sessionManager.accounts
    val sessionAuthStatus: StateFlow<SessionAuthStatus> get() = sessionManager.sessionAuthStatus
    val sessionEpoch: StateFlow<Int> get() = sessionManager.sessionEpoch
    val familyAccounts: StateFlow<List<FamilySharedAccountHint>> get() = sessionManager.familyAccounts

    val httpClient = IgngHttpClient(
        config = IgngClientConfig(
            clientId = SiteEndpoints.CLIENT_ID,
            clientVersion = appVersion,
            userAgent = "IGNGbbs-Android/$appVersion",
            endpoints = endpoints,
        ),
        deviceIdProvider = { sessionStore.getOrCreateDeviceId() },
        tokenProvider = { sessionManager.activeUsableToken() },
        tokenRefresher = sessionManager.tokenRefresher,
        sessionExpiredNotifier = sessionExpiredNotifier,
        hasInvalidatedSession = { sessionManager.isActiveSessionInvalidated() },
        errorMessageMapper = BbsErrorMapper::map,
    )

    val imageLoader: ImageLoader = IgngImageLoaderFactory.create(
        context = appContext,
        mediaOkHttpClient = httpClient.mediaOkHttpClient,
    )

    val settingsRepository = SettingsRepository(appContext)
    val swrDiskCache = IgngLocalJsonCache(appContext, "igngbbs_swr_v2_cache", useCacheDir = true)
    val swrCache = IgngSwrCache(diskCache = swrDiskCache, cacheScope = appScope)
    val accountRepository = IgngAccountRepository(httpClient)
    val appUpdateManager = AppUpdateManager(
        context = appContext,
        config = AppUpdateConfig(
            appKey = "bbs",
            appName = "IGNG BBS",
            packageName = appContext.packageName,
            currentVersionName = appVersion,
            currentVersionCode = appVersionCode,
            assetPrefix = "igng-bbs-v",
            userAgent = "IGNGbbs-Android/$appVersion",
        ),
        appScope = appScope,
    )

    val feedRepository = FeedRepository(httpClient, settingsRepository, swrCache)
    val columnRepository = ColumnRepository(httpClient, swrCache)
    val postRepository = PostRepository(httpClient, swrCache)
    val inboxRepository = InboxRepository(httpClient, swrCache)
    val profileRepository = ProfileRepository(httpClient, swrCache)
    val creatorRepository = CreatorRepository(httpClient, swrCache)
    val adminRepository = AdminRepository(httpClient, swrCache)

    private val _bootstrap = MutableStateFlow<BbsBootstrapDto?>(null)
    val bootstrap: StateFlow<BbsBootstrapDto?> = _bootstrap.asStateFlow()

    private val _dailyLoginReward = MutableSharedFlow<DailyLoginDto>(extraBufferCapacity = 1)
    val dailyLoginReward: SharedFlow<DailyLoginDto> = _dailyLoginReward.asSharedFlow()

    init {
        syncSessionStampNow()
        appScope.launch {
            combine(activeAccount, sessionEpoch, sessionAuthStatus) { acc, epoch, status ->
                IgngSwrCache.buildSessionStamp(acc?.userId, epoch, status.name)
            }.collect { stamp ->
                swrCache.updateSessionStamp(stamp)
            }
        }
    }

    fun syncSessionStampNow() {
        val stamp = IgngSwrCache.buildSessionStamp(
            userId = activeAccount.value?.userId,
            sessionEpoch = sessionEpoch.value,
            authStatusName = sessionAuthStatus.value.name,
        )
        swrCache.updateSessionStamp(stamp)
    }

    /**
     * Hydrates top-level disk snapshots into memory at Application.onCreate so Frame 0 renders
     * immediately without waiting for any disk or network round-trip.
     */
    fun warmCoreDiskCaches() {
        appScope.launch {
            syncSessionStampNow()
            val hydratedBootstrap = swrCache.hydrateFromDisk(
                key = BbsCacheKeys.BOOTSTRAP,
                scope = IgngCacheScope.SESSION_AWARE,
                tags = setOf(BbsCacheTags.BOOTSTRAP),
                diskCodec = BbsDiskCodecs.BOOTSTRAP,
                legacyRawProvider = { settingsRepository.readCache("bootstrap") },
            )
            if (hydratedBootstrap != null && _bootstrap.value == null) {
                _bootstrap.value = hydratedBootstrap.data
            }
            swrCache.hydrateFromDisk(
                key = BbsCacheKeys.HOME,
                scope = IgngCacheScope.SESSION_AWARE,
                tags = setOf(BbsCacheTags.HOME),
                diskCodec = BbsDiskCodecs.HOME,
            )
            swrCache.hydrateFromDisk(
                key = BbsCacheKeys.feed(categorySlug = null, sort = "latest", excludeSeries = true),
                scope = IgngCacheScope.SESSION_AWARE,
                tags = setOf(BbsCacheTags.FEED, BbsCacheTags.POST_LISTS),
                diskCodec = BbsDiskCodecs.DEFAULT_FEED,
            )
            swrCache.hydrateFromDisk(
                key = BbsCacheKeys.columns(mine = false, categoryId = null, query = ""),
                scope = IgngCacheScope.SESSION_AWARE,
                tags = setOf(BbsCacheTags.COLUMNS),
                diskCodec = BbsDiskCodecs.DEFAULT_COLUMNS,
            )
            swrCache.hydrateFromDisk(
                key = BbsCacheKeys.TAGS,
                scope = IgngCacheScope.PUBLIC,
                tags = setOf(BbsCacheTags.TAGS),
                diskCodec = BbsDiskCodecs.TAGS,
            )
            swrCache.hydrateFromDisk(
                key = BbsCacheKeys.RULES,
                scope = IgngCacheScope.PUBLIC,
                tags = setOf(BbsCacheTags.RULES),
                diskCodec = BbsDiskCodecs.RULES,
            )
            appUpdateManager.cleanupObsoleteApks()
        }
    }

    /**
     * Idle cold-start prefetch for sibling top-level tabs after first frame settles.
     * Respects TTL, single-flight deduplication, and HTTP 429 backoff.
     */
    suspend fun prefetchIdleTabs() {
        if (!swrCache.isPrefetchAllowed()) return
        val authed = activeAccount.value != null && !sessionAuthStatus.value.requiresReauth
        coroutineScope {
            val colsDeferred = async(Dispatchers.IO) {
                swrCache.fetch(
                    key = BbsCacheKeys.columns(mine = false, categoryId = null, query = ""),
                    scope = IgngCacheScope.SESSION_AWARE,
                    policy = BbsCachePolicies.Standard,
                    tags = setOf(BbsCacheTags.COLUMNS),
                    force = false,
                    diskCodec = BbsDiskCodecs.DEFAULT_COLUMNS,
                ) {
                    columnRepository.listColumns().toKotlinResult()
                }
            }
            val tagsDeferred = async(Dispatchers.IO) {
                swrCache.fetch(
                    key = BbsCacheKeys.TAGS,
                    scope = IgngCacheScope.PUBLIC,
                    policy = BbsCachePolicies.Static,
                    tags = setOf(BbsCacheTags.TAGS),
                    force = false,
                    diskCodec = BbsDiskCodecs.TAGS,
                ) {
                    feedRepository.listTags(30).toKotlinResult()
                }
            }
            val creatorDeferred = if (authed) {
                async(Dispatchers.IO) {
                    swrCache.fetch(
                        key = BbsCacheKeys.CREATOR_OVERVIEW,
                        scope = IgngCacheScope.USER_ONLY,
                        policy = BbsCachePolicies.Standard,
                        tags = setOf(BbsCacheTags.CREATOR),
                        force = false,
                    ) {
                        creatorRepository.getOverview().toKotlinResult()
                    }
                }
            } else {
                null
            }
            colsDeferred.await()
            tagsDeferred.await()
            creatorDeferred?.await()
        }
    }

    fun trimMemory(aggressive: Boolean = false) {
        swrCache.trimMemory(aggressive)
    }

    suspend fun hydrateCachedBootstrap() {
        if (_bootstrap.value == null) {
            val hydrated = swrCache.hydrateFromDisk(
                key = BbsCacheKeys.BOOTSTRAP,
                scope = IgngCacheScope.SESSION_AWARE,
                tags = setOf(BbsCacheTags.BOOTSTRAP),
                diskCodec = BbsDiskCodecs.BOOTSTRAP,
                legacyRawProvider = { settingsRepository.readCache("bootstrap") },
            )
            _bootstrap.value = hydrated?.data ?: feedRepository.loadCachedBootstrap()
        }
    }

    suspend fun refreshFamilyAccounts() {
        sessionManager.refreshFamilyAccounts()
    }

    suspend fun refreshBootstrap(force: Boolean = true): ApiResult<BbsBootstrapDto> {
        val result = swrCache.fetch(
            key = BbsCacheKeys.BOOTSTRAP,
            scope = IgngCacheScope.SESSION_AWARE,
            policy = BbsCachePolicies.Bootstrap,
            tags = setOf(BbsCacheTags.BOOTSTRAP),
            force = force,
            diskCodec = BbsDiskCodecs.BOOTSTRAP,
            legacyRawProvider = { settingsRepository.readCache("bootstrap") },
        ) {
            feedRepository.fetchBootstrap().toKotlinResult()
        }
        return result.fold(
            onSuccess = { data ->
                _bootstrap.value = data
                data.viewer.user?.let { viewer ->
                    sessionManager.updateActiveProfile(
                        username = viewer.username.takeIf { it.isNotBlank() },
                        nickname = viewer.nickname.takeIf { it.isNotBlank() },
                        avatarUrl = viewer.avatarUrl,
                    )
                }
                ApiResult.Success(data)
            },
            onFailure = { err ->
                val apiErr = (err as? ApiException)
                    ?: ApiException(-1, "network_error", err.message ?: "无法连接到 IGNG 服务")
                ApiResult.Failure(apiErr)
            },
        )
    }

    suspend fun refreshBootstrapIfStale(): ApiResult<BbsBootstrapDto> = refreshBootstrap(force = false)

    suspend fun settleDailyLoginIfNeeded() {
        val active = sessionManager.activeAccount.value ?: return
        if (sessionManager.activeUsableToken().isNullOrBlank()) return
        val todayUtc = LocalDate.now(ZoneOffset.UTC).toString()
        val lastPeriod = settingsRepository.getLastDailyLoginPeriod(active.userId)
        if (lastPeriod == todayUtc) return
        when (val res = accountRepository.settleDailyLogin()) {
            is ApiResult.Success -> {
                val period = res.data.period.ifBlank { todayUtc }
                settingsRepository.markDailyLoginPeriod(active.userId, period)
                if (res.data.awarded) {
                    _dailyLoginReward.tryEmit(
                        DailyLoginDto(
                            awarded = res.data.awarded,
                            exp = res.data.exp,
                            baseExp = res.data.baseExp,
                            multiplier = res.data.multiplier,
                            level = res.data.level,
                            period = period,
                        ),
                    )
                }
            }
            is ApiResult.Failure -> Unit
        }
    }

    suspend fun switchAccount(userId: Long) {
        sessionManager.switchAccount(userId) ?: return
        syncSessionStampNow()
        _bootstrap.value = null
        refreshBootstrap(force = true)
        settleDailyLoginIfNeeded()
    }

    fun removeAccount(userId: Long) {
        sessionManager.removeAccount(userId)
        syncSessionStampNow()
    }

    suspend fun logoutActiveAccount() {
        sessionManager.logoutActive()
        syncSessionStampNow()
        swrCache.clearSessionDiskCache()
        _bootstrap.value = null
        refreshBootstrap(force = true)
    }

    suspend fun logoutActive() = logoutActiveAccount()

    suspend fun cacheSizeBytes(): Long =
        settingsRepository.cacheSizeBytes() +
            swrCache.diskSizeBytes() +
            IgngImageLoaderFactory.diskCacheSizeBytes(appContext) +
            appUpdateManager.cacheSizeBytes()

    suspend fun clearAllCaches() {
        settingsRepository.clearLocalCache()
        swrCache.clearAll()
        IgngImageLoaderFactory.clearCaches(appContext, imageLoader)
        appUpdateManager.clearCachedApks()
    }
}
