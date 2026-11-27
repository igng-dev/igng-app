package net.igng.mcstatus.core.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import net.igng.core.auth.AuthUiState
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
import net.igng.core.model.AppUpdateConfig
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderTypographyPrefs
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.model.ThemeMode
import net.igng.core.network.IgngClientConfig
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.IgngJson
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.core.updater.AppUpdateManager
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.core.model.BootstrapResponse
import net.igng.mcstatus.core.model.McAdminRole
import net.igng.mcstatus.core.model.SiteEndpoints
import net.igng.mcstatus.core.model.WikiReadingProgress

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

    val bookmarkedWikiIds: Flow<List<Int>> = context.igngPrefsDataStore.data.map { prefs ->
        prefs[KEY_BOOKMARKED_WIKI].orEmpty().split(",").mapNotNull { it.toIntOrNull() }
    }

    val wikiReadingProgress: Flow<Map<Int, WikiReadingProgress>> = context.igngPrefsDataStore.data.map { prefs ->
        decodeWikiProgressMap(prefs[KEY_WIKI_PROGRESS])
    }

    val lastSelectedServerKey: Flow<String?> = context.igngPrefsDataStore.data.map { prefs ->
        prefs[KEY_LAST_SELECTED_SERVER]?.takeIf { it.isNotBlank() }
    }

    suspend fun setLastSelectedServerKey(key: String) {
        val clean = key.trim()
        if (clean.isEmpty()) return
        context.igngPrefsDataStore.edit { it[KEY_LAST_SELECTED_SERVER] = clean }
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

    suspend fun toggleWikiBookmark(pageId: Int): Boolean {
        var added = false
        context.igngPrefsDataStore.edit { prefs ->
            val current = prefs[KEY_BOOKMARKED_WIKI].orEmpty().split(",").mapNotNull { it.toIntOrNull() }
            val next = if (pageId in current) {
                added = false
                current.filterNot { it == pageId }
            } else {
                added = true
                (listOf(pageId) + current.filterNot { it == pageId }).take(48)
            }
            prefs[KEY_BOOKMARKED_WIKI] = next.joinToString(",")
        }
        return added
    }

    suspend fun saveWikiReadingProgress(
        pageId: Int,
        progressRatio: Float,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        context.igngPrefsDataStore.edit { prefs ->
            val current = decodeWikiProgressMap(prefs[KEY_WIKI_PROGRESS]).toMutableMap()
            val clampedRatio = progressRatio.coerceIn(0f, 1f)
            current[pageId] = WikiReadingProgress(
                pageId = pageId,
                progressRatio = clampedRatio,
                firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
                firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
                updatedAtMs = System.currentTimeMillis(),
            )
            val trimmed = current.values
                .sortedByDescending { it.updatedAtMs }
                .take(32)
                .associate { it.pageId.toString() to it }
            prefs[KEY_WIKI_PROGRESS] = IgngJson.instance.encodeToString(
                MapSerializer(String.serializer(), WikiReadingProgress.serializer()),
                trimmed,
            )
        }
    }

    private fun decodeWikiProgressMap(raw: String?): Map<Int, WikiReadingProgress> = raw?.let {
        runCatching {
            IgngJson.instance.decodeFromString(
                MapSerializer(String.serializer(), WikiReadingProgress.serializer()),
                it,
            ).mapNotNull { (k, v) ->
                val id = k.toIntOrNull() ?: v.pageId.takeIf { pid -> pid > 0 } ?: return@mapNotNull null
                id to v.copy(pageId = id)
            }.toMap()
        }.getOrNull()
    }.orEmpty()

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_MOTION = stringPreferencesKey("motion_intensity")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_READER_PREFS = stringPreferencesKey("reader_typography_prefs_v1")
        val KEY_RECENT_WIKI = stringPreferencesKey("recent_wiki_ids")
        val KEY_BOOKMARKED_WIKI = stringPreferencesKey("bookmarked_wiki_ids_v1")
        val KEY_WIKI_PROGRESS = stringPreferencesKey("wiki_reading_progress_v1")
        val KEY_SESSION_EPOCH = intPreferencesKey("session_epoch")
        val KEY_LAST_SELECTED_SERVER = stringPreferencesKey("last_selected_server_key_v1")
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
    val clientVersionCode: Int = 0,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
            scopes = null,
            deviceName = "IGNGmc Android",
            endpoints = endpoints,
            clientVersion = clientVersion,
            userAgent = "IGNGmc-Android/$clientVersion",
        ),
        vaultConfig = AuthRepository.VAULT_CONFIG,
        appScope = appScope,
        enrichAccount = { baseAccount -> enrichSignedInAccount(baseAccount) },
    )

    val sessionStore: SecureSessionStore get() = sessionManager.sessionStore
    val oauthPendingStore = OAuthPendingStore(sessionStore)
    val sessionExpiredNotifier: SessionExpiredNotifier get() = sessionManager.sessionExpiredNotifier

    val activeAccount: StateFlow<SavedAccount?> get() = sessionManager.activeAccount
    val accounts: StateFlow<List<SavedAccount>> get() = sessionManager.accounts
    val sessionAuthStatus: StateFlow<SessionAuthStatus> get() = sessionManager.sessionAuthStatus
    val sessionEpoch: StateFlow<Int> get() = sessionManager.sessionEpoch
    val familyAccounts: StateFlow<List<FamilySharedAccountHint>> get() = sessionManager.familyAccounts
    val authUiState: StateFlow<AuthUiState> get() = sessionManager.authUiState
    val authEvents: SharedFlow<String> get() = sessionManager.authEvents
    val initialFamilySyncDone: StateFlow<Boolean> get() = sessionManager.initialFamilySyncDone

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
    val appUpdateManager = AppUpdateManager(
        context = appContext,
        config = AppUpdateConfig(
            appKey = "mc",
            appName = "IGNGmc",
            packageName = appContext.packageName,
            currentVersionName = clientVersion,
            currentVersionCode = clientVersionCode,
            assetPrefix = "igng-mc-v",
            userAgent = "IGNGmc-Android/$clientVersion",
        ),
        appScope = appScope,
    )
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
        sessionManager.bindFamilyHandoffIssuer { req -> accountRepository.requestFamilyHandoff(req) }
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
                launch {
                    appUpdateManager.cleanupObsoleteApks()
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

    fun syncFamilyAccountsAndAutoSignIn() {
        sessionManager.syncFamilyAccountsAndAutoSignIn()
    }

    fun handleIncomingOAuthCallback(uri: Uri?): Boolean =
        sessionManager.handleIncomingOAuthCallback(uri)

    fun signInWithFamilyAccount(context: Context, hint: FamilySharedAccountHint, isDark: Boolean = false) {
        sessionManager.signInWithFamilyAccount(context = context, hint = hint, isDark = isDark)
    }

    private suspend fun enrichSignedInAccount(baseAccount: SavedAccount): SavedAccount {
        val boot = runCatching { authRepository.fetchBootstrap(baseAccount.accessToken) }.getOrNull()
        if (boot != null) {
            _bootstrap.value = boot
        }
        val viewerUser = boot?.viewer?.user
        val adminRole = McAdminRole.fromWire(boot?.viewer?.adminRole)
        return baseAccount.copy(
            userId = viewerUser?.resolvedNumericId?.takeIf { it > 0 }?.toLong() ?: baseAccount.userId,
            username = viewerUser?.username?.takeIf { it.isNotBlank() } ?: baseAccount.username,
            nickname = viewerUser?.nickname ?: baseAccount.nickname,
            avatarUrl = viewerUser?.avatarUrl ?: baseAccount.avatarUrl,
            deviceName = "IGNGmc Android",
            adminRoleWire = adminRole?.name,
        )
    }

    suspend fun handleOAuthCallback(uri: Uri?): OAuthCallbackResult =
        sessionManager.handleCallbackUri(uri) { baseAccount -> enrichSignedInAccount(baseAccount) }

    suspend fun logoutActive() {
        sessionManager.logoutActive()
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

    fun saveAndActivateAccount(account: SavedAccount, bumpEpoch: Boolean = true) {
        sessionManager.saveAndActivateAccount(account, bumpEpoch = bumpEpoch)
        syncSessionStampNow()
    }

    fun switchAccount(userId: Long) {
        sessionManager.switchAccount(userId)
        syncSessionStampNow()
    }

    fun switchAccount(userId: Int) = switchAccount(userId.toLong())

    fun removeAccount(userId: Long) {
        sessionManager.removeAccount(userId)
        syncSessionStampNow()
    }

    fun removeActiveAccount() {
        activeAccount.value?.let { removeAccount(it.userId) }
    }

    fun updateBootstrap(response: BootstrapResponse?) {
        _bootstrap.value = response
    }

    suspend fun clearAllCaches() {
        exploreCache.clearAll()
        wikiCache.clearAll()
        swrCache.clearAll()
        IgngImageLoaderFactory.clearCaches(appContext, imageLoader)
        appUpdateManager.clearCachedApks()
    }

    suspend fun cacheSizeBytes(): Long =
        exploreCache.sizeBytes() +
            wikiCache.sizeBytes() +
            draftCache.sizeBytes() +
            swrCache.diskSizeBytes() +
            IgngImageLoaderFactory.diskCacheSizeBytes(appContext) +
            appUpdateManager.cacheSizeBytes()
}
