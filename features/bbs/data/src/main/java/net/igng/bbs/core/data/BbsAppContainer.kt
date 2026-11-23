package net.igng.bbs.core.data

import android.content.Context
import coil3.ImageLoader
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import net.igng.bbs.core.model.BbsBootstrapDto
import net.igng.bbs.core.model.DailyLoginDto
import net.igng.bbs.core.model.SiteEndpoints
import net.igng.core.auth.IgngSessionManager
import net.igng.core.auth.SecureSessionStore
import net.igng.core.data.IgngAccountRepository
import net.igng.core.data.IgngImageLoaderFactory
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiResult
import net.igng.core.network.IgngClientConfig
import net.igng.core.network.IgngHttpClient
import net.igng.core.network.SessionExpiredNotifier

class BbsAppContainer(
    context: Context,
    val endpoints: SiteEndpoints,
    val appVersion: String,
) {
    val appContext: Context = context.applicationContext

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
    val accountRepository = IgngAccountRepository(httpClient)

    val feedRepository = FeedRepository(httpClient, settingsRepository)
    val columnRepository = ColumnRepository(httpClient)
    val postRepository = PostRepository(httpClient)
    val inboxRepository = InboxRepository(httpClient)
    val profileRepository = ProfileRepository(httpClient)
    val creatorRepository = CreatorRepository(httpClient)
    val adminRepository = AdminRepository(httpClient)

    private val _bootstrap = MutableStateFlow<BbsBootstrapDto?>(null)
    val bootstrap: StateFlow<BbsBootstrapDto?> = _bootstrap.asStateFlow()

    private val _dailyLoginReward = MutableSharedFlow<DailyLoginDto>(extraBufferCapacity = 1)
    val dailyLoginReward: SharedFlow<DailyLoginDto> = _dailyLoginReward.asSharedFlow()

    suspend fun hydrateCachedBootstrap() {
        if (_bootstrap.value == null) {
            _bootstrap.value = feedRepository.loadCachedBootstrap()
        }
    }

    suspend fun refreshFamilyAccounts() {
        sessionManager.refreshFamilyAccounts()
    }

    suspend fun refreshBootstrap(): ApiResult<BbsBootstrapDto> {
        val res = feedRepository.fetchBootstrap()
        if (res is ApiResult.Success) {
            _bootstrap.value = res.data
            res.data.viewer.user?.let { viewer ->
                sessionManager.updateActiveProfile(
                    username = viewer.username.takeIf { it.isNotBlank() },
                    nickname = viewer.nickname.takeIf { it.isNotBlank() },
                    avatarUrl = viewer.avatarUrl,
                )
            }
        }
        return res
    }

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
        _bootstrap.value = null
        refreshBootstrap()
        settleDailyLoginIfNeeded()
    }

    fun removeAccount(userId: Long) {
        sessionManager.removeAccount(userId)
    }

    suspend fun logoutActiveAccount() {
        sessionManager.logoutActive()
        _bootstrap.value = null
        refreshBootstrap()
    }

    suspend fun logoutActive() = logoutActiveAccount()

    suspend fun cacheSizeBytes(): Long =
        settingsRepository.cacheSizeBytes() + IgngImageLoaderFactory.diskCacheSizeBytes(appContext)

    suspend fun clearAllCaches() {
        settingsRepository.clearLocalCache()
        IgngImageLoaderFactory.clearCaches(appContext, imageLoader)
    }
}
