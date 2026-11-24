package net.igng.mcstatus.core.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.igng.core.auth.IgngOAuthClient
import net.igng.core.auth.IgngSessionManager
import net.igng.core.auth.OAuthCallbackResult
import net.igng.core.auth.OAuthClientConfig
import net.igng.core.auth.SecureSessionStore
import net.igng.core.data.IgngAccountRepository
import net.igng.core.data.IgngCacheScope
import net.igng.core.data.IgngImageLoaderFactory
import net.igng.core.data.IgngLocalJsonCache
import net.igng.core.data.IgngSwrCache
import net.igng.core.data.runSuspendCatching
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderTypographyPrefs
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.model.ThemeMode
import net.igng.core.network.ApiResult
import net.igng.core.network.IgngClientConfig
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.IgngJson
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.core.model.BootstrapResponse
import net.igng.mcstatus.core.model.DailyExpStatus
import net.igng.mcstatus.core.model.McAdminRole
import net.igng.mcstatus.core.model.SiteEndpoints

private val Context.igngPrefsDataStore by preferencesDataStore(name = "igng_mc_v2_prefs")

class SettingsRepository(private val context: Context) {
    val preferences: Flow<AppPreferences> = context.igngPrefsDataStore.data.map { prefs ->
        val reader = prefs[KEY_READER_PREFS]?.let { raw ->
            runCatching { IgngJson.instance.decodeFromString(ReaderTypographyPrefs.serializer(), raw) }.getOrNull()
        } ?: ReaderTypographyPrefs()
        AppPreferences(
            themeMode = ThemeMode.entries.firstOrNull { it.id == prefs[KEY_THEME] } ?: ThemeMode.SYSTEM,
            motionIntensity = MotionIntensity.entries.firstOrNull { it.id == prefs[KEY_MOTION] } ?: MotionIntensity.FULL,
            hapticsEnabled = prefs[KEY_HAPTICS] ?: true,
            reader = reader,
        )
    }

    val recentWikiIds: Flow<List<Int>> = context.igngPrefsDataStore.data.map { prefs ->
        prefs[KEY_RECENT_WIKI].orEmpty().split(",").mapNotNull { it.toIntOrNull() }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.igngPrefsDataStore.edit { it[KEY_THEME] = mode.id }
    }

    suspend fun setMotionIntensity(intensity: MotionIntensity) {
        context.igngPrefsDataStore.edit { it[KEY_MOTION] = intensity.id }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.igngPrefsDataStore.edit { it[KEY_HAPTICS] = enabled }
    }

    suspend fun updateReaderPrefs(transform: (ReaderTypographyPrefs) -> ReaderTypographyPrefs) {
        context.igngPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_READER_PREFS]?.let { raw ->
                runCatching { IgngJson.instance.decodeFromString(ReaderTypographyPrefs.serializer(), raw) }.getOrNull()
            } ?: ReaderTypographyPrefs()
            prefs[KEY_READER_PREFS] = IgngJson.instance.encodeToString(
                ReaderTypographyPrefs.serializer(),
                transform(current),
            )
        }
    }

    suspend fun recordRecentWiki(pageId: Int) {
        context.igngPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_RECENT_WIKI].orEmpty().split(",").mapNotNull { it.toIntOrNull() }
            val next = (listOf(pageId) + current.filterNot { it == pageId }).take(12)
            prefs[KEY_RECENT_WIKI] = next.joinToString(",")
        }
    }

    suspend fun getCachedDailyExp(userId: Long, periodUtc: String): DailyExpStatus? {
        val raw = context.igngPrefsDataStore.data.first()[KEY_DAILY_EXP_CACHE] ?: return null
        val map = decodeDailyMap(raw)
        return map[userId.toString()]?.takeIf { it.period == periodUtc }
    }

    suspend fun saveCachedDailyExp(userId: Long, status: DailyExpStatus) {
        context.igngPrefsDataStore.edit { prefs ->
            val map = decodeDailyMap(prefs[KEY_DAILY_EXP_CACHE]).toMutableMap()
            map[userId.toString()] = status
            prefs[KEY_DAILY_EXP_CACHE] = IgngJson.instance.encodeToString(
                MapSerializer(String.serializer(), DailyExpStatus.serializer()),
                map,
            )
        }
    }

    private fun decodeDailyMap(raw: String?): Map<String, DailyExpStatus> = raw?.let {
        runCatching {
            IgngJson.instance.decodeFromString(
                MapSerializer(String.serializer(), DailyExpStatus.serializer()),
                it,
            )
        }.getOrNull()
    }.orEmpty()

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_MOTION = stringPreferencesKey("motion_intensity")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_READER_PREFS = stringPreferencesKey("reader_typography_prefs_v1")
        val KEY_RECENT_WIKI = stringPreferencesKey("recent_wiki_ids")
        val KEY_DAILY_EXP_CACHE = stringPreferencesKey("daily_exp_cache_v1")
        val KEY_SESSION_EPOCH = intPreferencesKey("session_epoch")
    }
}

/**
 * Central application dependency container unified on [IgngSessionManager], [IgngAccountRepository],
 * and [IgngImageLoaderFactory].
 */
class IgngAppContainer(
    val appContext: Context,
    val endpoints: SiteEndpoints,
    val clientVersion: String,
) {
    private val _dailyExp = MutableStateFlow<DailyExpStatus?>(null)
    val dailyExp: StateFlow<DailyExpStatus?> = _dailyExp.asStateFlow()

    private val _bootstrap = MutableStateFlow<BootstrapResponse?>(null)
    val bootstrap: StateFlow<BootstrapResponse?> = _bootstrap.asStateFlow()

    val sessionManager = IgngSessionManager(
        context = appContext,
        oauthConfig = OAuthClientConfig(
            clientId = AuthRepository.CLIENT_ID,
            redirectUri = AuthRepository.REDIRECT_URI,
            callbackScheme = AuthRepository.CALLBACK_SCHEME,
            callbackHost = AuthRepository.CALLBACK_HOST,
            callbackPath = AuthRepository.CALLBACK_PATH,
            scopes = AuthRepository.DEFAULT_SCOPES,
            deviceName = "IGNGmc Android",
            endpoints = endpoints,
            clientVersion = clientVersion,
            userAgent = "IGNGmc-Android/$clientVersion",
        ),
        vaultConfig = AuthRepository.VAULT_CONFIG,
        onSessionInvalidated = { _dailyExp.value = null },
    )

    val sessionStore: SecureSessionStore get() = sessionManager.sessionStore
    val oauthPendingStore = OAuthPendingStore(sessionStore)
    val sessionExpiredNotifier: SessionExpiredNotifier get() = sessionManager.sessionExpiredNotifier

    val activeAccount: StateFlow<SavedAccount?> get() = sessionManager.activeAccount
    val accounts: StateFlow<List<SavedAccount>> get() = sessionManager.accounts
    val sessionAuthStatus: StateFlow<SessionAuthStatus> get() = sessionManager.sessionAuthStatus
    val sessionEpoch: StateFlow<Int> get() = sessionManager.sessionEpoch
    val familyAccounts: StateFlow<List<FamilySharedAccountHint>> get() = sessionManager.familyAccounts

    val tokenRefresher: SingleFlightTokenRefresher get() = sessionManager.tokenRefresher
    private val oauthClient: IgngOAuthClient get() = sessionManager.oauthClient

    fun isActiveSessionInvalidated(): Boolean = sessionManager.isActiveSessionInvalidated()
    fun activeUsableToken(): String? = sessionManager.activeUsableToken()

    val httpClient = IgngHttpClient(
        config = IgngClientConfig(
            clientId = AuthRepository.CLIENT_ID,
            clientVersion = clientVersion,
            userAgent = "IGNGmc-Android/$clientVersion",
            endpoints = endpoints,
        ),
        deviceIdProvider = { sessionStore.getOrCreateDeviceId() },
        tokenProvider = { sessionManager.activeUsableToken() },
        tokenRefresher = sessionManager.tokenRefresher,
        sessionExpiredNotifier = sessionExpiredNotifier,
        hasInvalidatedSession = { sessionManager.isActiveSessionInvalidated() },
    )

    val apiClient = ApiClient(httpClient)
    val mediaOkHttpClient = httpClient.mediaOkHttpClient

    val imageLoader: ImageLoader = IgngImageLoaderFactory.create(
        context = appContext,
        mediaOkHttpClient = mediaOkHttpClient,
    )

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val exploreCache = LocalJsonCache(appContext, "igng_explore_cache")
    val wikiCache = LocalJsonCache(appContext, "igng_wiki_cache")
    val draftCache = LocalJsonCache(appContext, "igng_draft_cache")
    val swrDiskCache = IgngLocalJsonCache(appContext, "igng_mc_swr_cache", useCacheDir = true)
    val swrCache = IgngSwrCache(
        diskCache = swrDiskCache,
        maxEntries = 200,
        cacheScope = appScope,
    )

    val settingsRepository = SettingsRepository(appContext)
    val accountRepository = IgngAccountRepository(httpClient)
    val authRepository = AuthRepository(endpoints, apiClient, oauthClient)
    val exploreRepository = ExploreRepository(endpoints, apiClient, exploreCache, swrCache = swrCache)
    val statusRepository = StatusRepository(endpoints, apiClient, swrCache = swrCache)
    val chatRepository = ChatRepository(endpoints, apiClient, swrCache = swrCache)
    val wikiRepository = WikiRepository(endpoints, apiClient, wikiCache, swrCache = swrCache)
    val playerRepository = PlayerRepository(endpoints, apiClient, authRepository, swrCache = swrCache)
    val ticketRepository = TicketRepository(endpoints, apiClient, draftCache, swrCache = swrCache)
    val adminRepository = AdminRepository(endpoints, apiClient, swrCache = swrCache)

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

    private fun syncSessionStampNow() {
        swrCache.updateSessionStamp(
            IgngSwrCache.buildSessionStamp(
                userId = activeAccount.value?.userId,
                sessionEpoch = sessionEpoch.value,
                authStatusName = sessionAuthStatus.value.name,
            ),
        )
    }

    /**
     * Pre-warms core disk snapshots into [swrCache] RAM on app launch so Frame 0 renders
     * instantaneously with zero skeletons or dash (`—`) placeholders.
     */
    fun warmCoreDiskCaches() {
        appScope.launch {
            coroutineScope {
                launch {
                    val boot = swrCache.hydrateFromDisk(
                        key = McCacheKeys.BOOTSTRAP,
                        scope = IgngCacheScope.SESSION_AWARE,
                        tags = setOf(McCacheTags.BOOTSTRAP),
                        diskCodec = McDiskCodecs.bootstrap,
                    )?.data
                    if (boot != null && _bootstrap.value == null) {
                        _bootstrap.value = boot
                    }
                }
                launch {
                    swrCache.hydrateFromDisk(
                        key = McCacheKeys.SERVERS,
                        scope = IgngCacheScope.PUBLIC,
                        tags = setOf(McCacheTags.SERVERS),
                        diskCodec = McDiskCodecs.servers,
                        legacyRawProvider = {
                            val legacy = exploreRepository.readCachedFamilies()
                            if (legacy.isNotEmpty()) {
                                McDiskCodecs.servers.encode(legacy)
                            } else {
                                null
                            }
                        },
                    )
                }
                launch {
                    swrCache.hydrateFromDisk(
                        key = McCacheKeys.STATUS,
                        scope = IgngCacheScope.PUBLIC,
                        tags = setOf(McCacheTags.STATUS),
                        diskCodec = McDiskCodecs.status,
                    )
                }
                launch {
                    swrCache.hydrateFromDisk(
                        key = McCacheKeys.wikiCatalog(),
                        scope = IgngCacheScope.PUBLIC,
                        tags = setOf(McCacheTags.WIKI),
                        diskCodec = McDiskCodecs.wikiCatalog(),
                        legacyRawProvider = {
                            wikiRepository.readCachedCatalog()?.let { McDiskCodecs.wikiCatalog().encode(it) }
                        },
                    )
                }
                launch {
                    swrCache.hydrateFromDisk(
                        key = McCacheKeys.CHAT_SOURCES,
                        scope = IgngCacheScope.SESSION_AWARE,
                        tags = setOf(McCacheTags.CHAT_SOURCES),
                        diskCodec = McDiskCodecs.chatSources,
                    )
                }
            }
        }
    }

    /**
     * Low-priority idle prefetch triggered shortly after first frame settles.
     * Deduplicated by [IgngSwrCache] TTL & single-flight and automatically suppressed during HTTP 429 backoff.
     */
    suspend fun prefetchIdleTabs() {
        if (!swrCache.isPrefetchAllowed()) return
        val isAuthed = activeAccount.value != null && sessionAuthStatus.value == SessionAuthStatus.ACTIVE
        coroutineScope {
            async {
                swrCache.fetch(
                    key = McCacheKeys.SERVERS,
                    scope = IgngCacheScope.PUBLIC,
                    policy = McCachePolicies.Static,
                    tags = setOf(McCacheTags.SERVERS),
                    diskCodec = McDiskCodecs.servers,
                ) { runSuspendCatching { exploreRepository.fetchHomeFamilies() } }
            }
            async {
                swrCache.fetch(
                    key = McCacheKeys.STATUS,
                    scope = IgngCacheScope.PUBLIC,
                    policy = McCachePolicies.Status,
                    tags = setOf(McCacheTags.STATUS),
                    diskCodec = McDiskCodecs.status,
                ) { runSuspendCatching { statusRepository.fetchPublicStatus() } }
            }
            async {
                swrCache.fetch(
                    key = McCacheKeys.wikiCatalog(),
                    scope = IgngCacheScope.PUBLIC,
                    policy = McCachePolicies.Static,
                    tags = setOf(McCacheTags.WIKI),
                    diskCodec = McDiskCodecs.wikiCatalog(),
                ) { runSuspendCatching { wikiRepository.fetchCatalog() } }
            }
            async {
                swrCache.fetch(
                    key = McCacheKeys.CHAT_SOURCES,
                    scope = IgngCacheScope.SESSION_AWARE,
                    policy = McCachePolicies.Chat,
                    tags = setOf(McCacheTags.CHAT_SOURCES),
                    diskCodec = McDiskCodecs.chatSources,
                ) { runSuspendCatching { chatRepository.fetchSources() } }
            }
            if (isAuthed) {
                async {
                    swrCache.fetch(
                        key = McCacheKeys.PLAYER_ACCOUNTS,
                        scope = IgngCacheScope.USER_ONLY,
                        policy = McCachePolicies.Standard,
                        tags = setOf(McCacheTags.PLAYER_ACCOUNTS),
                    ) { runSuspendCatching { playerRepository.fetchBoundAccounts() } }
                }
                async {
                    swrCache.fetch(
                        key = McCacheKeys.ticketsList(relation = "all", includeClosed = false, query = ""),
                        scope = IgngCacheScope.USER_ONLY,
                        policy = McCachePolicies.Standard,
                        tags = setOf(McCacheTags.TICKETS),
                    ) { runSuspendCatching { ticketRepository.fetchList(relation = "all", includeClosed = false, page = 1) } }
                }
            }
        }
    }

    fun trimMemory(aggressive: Boolean = false) {
        swrCache.trimMemory(aggressive)
    }

    suspend fun refreshFamilyAccounts() {
        sessionManager.refreshFamilyAccounts()
    }

    suspend fun handleOAuthCallback(uri: Uri?): OAuthCallbackResult {
        val result = sessionManager.handleCallbackUri(uri) { baseAccount ->
            val boot = runCatching { authRepository.fetchBootstrap(baseAccount.accessToken) }.getOrNull()
            val viewerUser = boot?.viewer?.user
            val adminRole = McAdminRole.fromWire(boot?.viewer?.adminRole)
            baseAccount.copy(
                userId = viewerUser?.resolvedNumericId?.takeIf { it > 0 }?.toLong() ?: baseAccount.userId,
                username = viewerUser?.username?.takeIf { it.isNotBlank() } ?: baseAccount.username,
                nickname = viewerUser?.nickname ?: baseAccount.nickname,
                avatarUrl = viewerUser?.avatarUrl ?: baseAccount.avatarUrl,
                deviceName = "IGNGmc Android",
                adminRoleWire = adminRole?.name,
            )
        }
        if (result is OAuthCallbackResult.Success) {
            _dailyExp.value = null
        }
        return result
    }

    suspend fun logoutActive() {
        sessionManager.logoutActive()
        _dailyExp.value = null
        syncSessionStampNow()
        swrCache.clearSessionDiskCache()
    }

    suspend fun fetchBootstrapForCurrentSession(): BootstrapResponse {
        val boot = apiClient.json.decodeFromString<BootstrapResponse>(
            apiClient.get(
                url = "${endpoints.api}/api/v1/mc/bootstrap",
                allowAnonymousFallback = true,
            ),
        )
        swrCache.put(
            key = McCacheKeys.BOOTSTRAP,
            data = boot,
            scope = IgngCacheScope.SESSION_AWARE,
            tags = setOf(McCacheTags.BOOTSTRAP),
            diskCodec = McDiskCodecs.bootstrap,
        )
        return boot
    }

    suspend fun settleDailyLoginForCurrentSession(): DailyExpStatus? {
        val active = activeAccount.value ?: return null
        if (activeUsableToken().isNullOrBlank()) return null
        val todayUtc = LocalDate.now(ZoneOffset.UTC).toString()
        settingsRepository.getCachedDailyExp(active.userId, todayUtc)?.let { cached ->
            return cached
        }
        return when (val res = accountRepository.settleDailyLogin()) {
            is ApiResult.Success -> {
                val period = res.data.period.ifBlank { todayUtc }
                val status = DailyExpStatus(
                    loggedIn = true,
                    awarded = res.data.awarded,
                    exp = res.data.exp,
                    baseExp = res.data.baseExp,
                    level = res.data.level,
                    multiplier = res.data.multiplier,
                    period = period,
                )
                settingsRepository.saveCachedDailyExp(active.userId, status)
                status
            }
            is ApiResult.Failure -> null
        }
    }

    fun saveAndActivateAccount(account: SavedAccount, resetDailyExp: Boolean = true) {
        sessionManager.saveAndActivateAccount(account, bumpEpoch = resetDailyExp)
        if (resetDailyExp) {
            _dailyExp.value = null
        }
        syncSessionStampNow()
    }

    fun switchAccount(userId: Long) {
        sessionManager.switchAccount(userId)
        _dailyExp.value = null
        syncSessionStampNow()
    }

    fun switchAccount(userId: Int) = switchAccount(userId.toLong())

    fun removeAccount(userId: Long) {
        val wasActive = activeAccount.value?.userId == userId
        sessionManager.removeAccount(userId)
        if (wasActive) {
            _dailyExp.value = null
        }
        syncSessionStampNow()
    }

    fun removeActiveAccount() {
        activeAccount.value?.let { removeAccount(it.userId) }
    }

    fun updateDailyExp(status: DailyExpStatus?) {
        _dailyExp.value = status
    }

    fun updateBootstrap(response: BootstrapResponse?) {
        _bootstrap.value = response
    }

    suspend fun clearAllCaches() {
        exploreCache.clearAll()
        wikiCache.clearAll()
        swrCache.clearAll()
        IgngImageLoaderFactory.clearCaches(appContext, imageLoader)
    }

    suspend fun cacheSizeBytes(): Long =
        exploreCache.sizeBytes() +
            wikiCache.sizeBytes() +
            draftCache.sizeBytes() +
            swrCache.diskSizeBytes() +
            IgngImageLoaderFactory.diskCacheSizeBytes(appContext)
}
