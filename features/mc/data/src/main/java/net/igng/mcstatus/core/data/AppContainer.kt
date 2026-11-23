package net.igng.mcstatus.core.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.igng.core.auth.IgngOAuthClient
import net.igng.core.auth.OAuthClientConfig
import net.igng.core.auth.SecureSessionStore
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderTypographyPrefs
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.model.ThemeMode
import net.igng.core.network.ApiException
import net.igng.core.network.IgngClientConfig
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.IgngJson
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.core.network.TokenRefreshResult
import net.igng.mcstatus.core.model.AppPreferences
import net.igng.mcstatus.core.model.BootstrapResponse
import net.igng.mcstatus.core.model.DailyExpStatus
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

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_MOTION = stringPreferencesKey("motion_intensity")
        val KEY_HAPTICS = booleanPreferencesKey("haptics_enabled")
        val KEY_READER_PREFS = stringPreferencesKey("reader_typography_prefs_v1")
        val KEY_RECENT_WIKI = stringPreferencesKey("recent_wiki_ids")
        val KEY_SESSION_EPOCH = intPreferencesKey("session_epoch")
    }
}

/**
 * Central application dependency container (zero annotation-processor overhead).
 */

class IgngAppContainer(
    val appContext: Context,
    val endpoints: SiteEndpoints,
    val clientVersion: String,
) {
    val sessionStore = SecureSessionStore(appContext, AuthRepository.VAULT_CONFIG)
    val oauthPendingStore = OAuthPendingStore(sessionStore)
    val sessionExpiredNotifier = SessionExpiredNotifier()

    private val invalidatedTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val _activeAccount = MutableStateFlow(sessionStore.activeAccountSnapshot())
    val activeAccount: StateFlow<SavedAccount?> = _activeAccount.asStateFlow()

    private val _accounts = MutableStateFlow(sessionStore.accountsSnapshot())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _sessionAuthStatus = MutableStateFlow(computeSessionAuthStatus(_activeAccount.value))
    val sessionAuthStatus: StateFlow<SessionAuthStatus> = _sessionAuthStatus.asStateFlow()

    private val _dailyExp = MutableStateFlow<DailyExpStatus?>(null)
    val dailyExp: StateFlow<DailyExpStatus?> = _dailyExp.asStateFlow()

    private val _bootstrap = MutableStateFlow<BootstrapResponse?>(null)
    val bootstrap: StateFlow<BootstrapResponse?> = _bootstrap.asStateFlow()

    private val _sessionEpoch = MutableStateFlow(0)
    val sessionEpoch: StateFlow<Int> = _sessionEpoch.asStateFlow()

    private fun computeSessionAuthStatus(account: SavedAccount?): SessionAuthStatus = when {
        account == null -> SessionAuthStatus.SIGNED_OUT
        account.refreshToken.isNullOrBlank() -> SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
        invalidatedTokens.contains(account.accessToken) -> SessionAuthStatus.EXPIRED
        else -> SessionAuthStatus.ACTIVE
    }

    private fun syncSessionAuthStatus() {
        _sessionAuthStatus.value = computeSessionAuthStatus(_activeAccount.value)
    }

    fun isActiveSessionInvalidated(): Boolean = _sessionAuthStatus.value.requiresReauth

    fun activeUsableToken(): String? {
        val account = _activeAccount.value ?: return null
        return account.accessToken.takeIf { !computeSessionAuthStatus(account).requiresReauth }
    }

    private val oauthClient = IgngOAuthClient(
        config = OAuthClientConfig(
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
        sessionStore = sessionStore,
    )

    private val authHttpClient = IgngHttpClient(
        config = IgngClientConfig(
            clientId = AuthRepository.CLIENT_ID,
            clientVersion = clientVersion,
            userAgent = "IGNGmc-Android/$clientVersion",
            endpoints = endpoints,
        ),
        deviceIdProvider = { sessionStore.getOrCreateDeviceId() },
        tokenProvider = { activeUsableToken() },
    )

    val authRepository = AuthRepository(endpoints, ApiClient(authHttpClient), oauthClient)

    val tokenRefresher = SingleFlightTokenRefresher(
        currentTokenProvider = { activeUsableToken() },
        isTokenInvalidated = { invalidatedTokens.contains(it) },
        refreshBlock = { failedAccessToken ->
            val current = sessionStore.activeAccountSnapshot()
            if (current == null) {
                syncSessionAuthStatus()
                return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            }
            if (current.accessToken != failedAccessToken && !invalidatedTokens.contains(current.accessToken)) {
                _activeAccount.value = current
                syncSessionAuthStatus()
                return@SingleFlightTokenRefresher TokenRefreshResult.Refreshed(current.accessToken)
            }
            if (current.refreshToken.isNullOrBlank()) {
                invalidatedTokens.add(failedAccessToken)
                _dailyExp.value = null
                syncSessionAuthStatus()
                return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            }
            try {
                val rotated = authRepository.refreshSession(current)
                if (rotated == null) {
                    invalidatedTokens.add(failedAccessToken)
                    _dailyExp.value = null
                    syncSessionAuthStatus()
                    return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
                }
                invalidatedTokens.remove(rotated.accessToken)
                sessionStore.upsertAccount(rotated, makeActive = true)
                _activeAccount.value = sessionStore.activeAccountSnapshot()
                _accounts.value = sessionStore.accountsSnapshot()
                syncSessionAuthStatus()
                TokenRefreshResult.Refreshed(rotated.accessToken)
            } catch (error: ApiException) {
                if (error.statusCode in listOf(400, 401, 403)) {
                    invalidatedTokens.add(failedAccessToken)
                    _dailyExp.value = null
                    syncSessionAuthStatus()
                    TokenRefreshResult.Invalidated
                } else {
                    TokenRefreshResult.TransientFailure
                }
            } catch (_: Exception) {
                TokenRefreshResult.TransientFailure
            }
        },
    )

    val httpClient = IgngHttpClient(
        config = IgngClientConfig(
            clientId = AuthRepository.CLIENT_ID,
            clientVersion = clientVersion,
            userAgent = "IGNGmc-Android/$clientVersion",
            endpoints = endpoints,
        ),
        deviceIdProvider = { sessionStore.getOrCreateDeviceId() },
        tokenProvider = { activeUsableToken() },
        tokenRefresher = tokenRefresher,
        sessionExpiredNotifier = sessionExpiredNotifier,
        hasInvalidatedSession = { isActiveSessionInvalidated() },
    )

    val apiClient = ApiClient(httpClient)
    val mediaOkHttpClient = httpClient.mediaOkHttpClient

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { mediaOkHttpClient }))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AnimatedImageDecoder.Factory())
            } else {
                add(GifDecoder.Factory())
            }
        }
        .build()

    val exploreCache = LocalJsonCache(appContext, "igng_explore_cache")
    val wikiCache = LocalJsonCache(appContext, "igng_wiki_cache")
    val draftCache = LocalJsonCache(appContext, "igng_draft_cache")

    val settingsRepository = SettingsRepository(appContext)
    val exploreRepository = ExploreRepository(endpoints, apiClient, exploreCache)
    val statusRepository = StatusRepository(endpoints, apiClient)
    val chatRepository = ChatRepository(endpoints, apiClient)
    val wikiRepository = WikiRepository(endpoints, apiClient, wikiCache)
    val playerRepository = PlayerRepository(endpoints, apiClient, authRepository)
    val ticketRepository = TicketRepository(endpoints, apiClient, draftCache)
    val adminRepository = AdminRepository(endpoints, apiClient)

    suspend fun fetchBootstrapForCurrentSession(): BootstrapResponse =
        apiClient.json.decodeFromString(
            apiClient.get(
                url = "${endpoints.api}/api/v1/mc/bootstrap",
                allowAnonymousFallback = true,
            ),
        )

    suspend fun settleDailyLoginForCurrentSession(): DailyExpStatus? {
        val token = activeUsableToken() ?: return null
        return authRepository.settleDailyLogin(token)
    }

    fun saveAndActivateAccount(account: SavedAccount, resetDailyExp: Boolean = true) {
        invalidatedTokens.remove(account.accessToken)
        sessionStore.upsertAccount(account, makeActive = true)
        _activeAccount.value = sessionStore.activeAccountSnapshot()
        _accounts.value = sessionStore.accountsSnapshot()
        syncSessionAuthStatus()
        if (resetDailyExp) {
            _dailyExp.value = null
            _sessionEpoch.value += 1
        }
    }

    fun switchAccount(userId: Long) {
        sessionStore.switchAccount(userId)
        _activeAccount.value = sessionStore.activeAccountSnapshot()
        _accounts.value = sessionStore.accountsSnapshot()
        syncSessionAuthStatus()
        _dailyExp.value = null
        _sessionEpoch.value += 1
    }

    fun switchAccount(userId: Int) = switchAccount(userId.toLong())

    fun removeActiveAccount() {
        _activeAccount.value?.accessToken?.let { invalidatedTokens.remove(it) }
        sessionStore.clearActiveAccount()
        _activeAccount.value = sessionStore.activeAccountSnapshot()
        _accounts.value = sessionStore.accountsSnapshot()
        syncSessionAuthStatus()
        _dailyExp.value = null
        _sessionEpoch.value += 1
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
    }

    suspend fun cacheSizeBytes(): Long =
        exploreCache.sizeBytes() + wikiCache.sizeBytes() + draftCache.sizeBytes()
}
