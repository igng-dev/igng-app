package net.igng.core.auth

import android.content.Context
import android.net.Uri
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.igng.core.model.CoreFamilyHandoffRequest
import net.igng.core.model.CoreFamilyHandoffResponse
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiException
import net.igng.core.network.ApiResult
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.core.network.TokenRefreshResult
import okhttp3.OkHttpClient

/**
 * Shared UI state for app-scoped OAuth 2.1 callback exchange & native cross-app family handoff.
 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data class Busy(val message: String = "正在完成安全授权…") : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/**
 * Unified non-destructive OAuth 2.1 session manager & native cross-app family SSO coordinator
 * shared across all IGNG official Android apps.
 *
 * Key guarantees:
 * - Preserves each app's exact [VaultStorageConfig] prefs/Keystore alias for seamless in-place upgrades.
 * - Executes OAuth callback token exchange & native family handoff on [appScope] (Application lifetime),
 *   so Compose recomposition or Activity recreation can never cancel an in-flight token exchange.
 * - Never deletes saved account identity or user drafts on 401 refresh invalidation: transitions
 *   [sessionAuthStatus] to [SessionAuthStatus.EXPIRED] / [SessionAuthStatus.LEGACY_UPGRADE_REQUIRED]
 *   so public GETs fall back anonymously and UI offers 1-tap re-auth or native family sync.
 * - Exports ONLY active OAuth 2.1 accounts to [IgngFamilyDirectoryStore] and participates in
 *   certificate-verified browser-free PKCE handoff (`POST /api/v1/auth/family-handoff`).
 */
class IgngSessionManager(
    context: Context,
    val oauthConfig: OAuthClientConfig,
    vaultConfig: VaultStorageConfig,
    baseOkHttpClient: OkHttpClient? = null,
    private val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val enrichAccount: (suspend (SavedAccount) -> SavedAccount)? = null,
    private val onSessionInvalidated: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    val sessionStore = SecureSessionStore(appContext, vaultConfig)
    val oauthClient = IgngOAuthClient(oauthConfig, sessionStore, baseOkHttpClient)
    val sessionExpiredNotifier = SessionExpiredNotifier()

    private val invalidatedTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val handledCallbackKeys: MutableSet<String> = Collections.synchronizedSet(linkedSetOf())
    private val attemptedAutoSyncKeys: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val familySyncMutex = Mutex()

    val activeAccount: StateFlow<SavedAccount?> get() = sessionStore.activeAccount
    val accounts: StateFlow<List<SavedAccount>> get() = sessionStore.accounts

    private val _sessionAuthStatus = MutableStateFlow(computeStatus(sessionStore.activeAccountSnapshot()))
    val sessionAuthStatus: StateFlow<SessionAuthStatus> = _sessionAuthStatus.asStateFlow()

    private val _sessionEpoch = MutableStateFlow(0)
    val sessionEpoch: StateFlow<Int> = _sessionEpoch.asStateFlow()

    private val _familyAccounts = MutableStateFlow<List<FamilySharedAccountHint>>(emptyList())
    val familyAccounts: StateFlow<List<FamilySharedAccountHint>> = _familyAccounts.asStateFlow()

    private val _authUiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authUiState: StateFlow<AuthUiState> = _authUiState.asStateFlow()

    private val _authEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val authEvents: SharedFlow<String> = _authEvents.asSharedFlow()

    private val _initialFamilySyncDone = MutableStateFlow(false)
    val initialFamilySyncDone: StateFlow<Boolean> = _initialFamilySyncDone.asStateFlow()

    init {
        syncFamilyDirectory()
    }

    fun computeStatus(account: SavedAccount?): SessionAuthStatus = when {
        account == null -> SessionAuthStatus.SIGNED_OUT
        account.refreshToken.isNullOrBlank() -> SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
        invalidatedTokens.contains(account.accessToken) -> SessionAuthStatus.EXPIRED
        else -> SessionAuthStatus.ACTIVE
    }

    private fun syncStatus() {
        _sessionAuthStatus.value = computeStatus(sessionStore.activeAccountSnapshot())
        syncFamilyDirectory()
    }

    private fun syncFamilyDirectory() {
        val snapshotInvalidated = synchronized(invalidatedTokens) { invalidatedTokens.toSet() }
        runCatching {
            IgngFamilyDirectoryStore.write(
                context = appContext,
                accounts = sessionStore.accountsSnapshot(),
                activeUserId = sessionStore.activeAccountSnapshot()?.userId,
                invalidatedTokens = snapshotInvalidated,
            )
        }
    }

    fun isActiveSessionInvalidated(): Boolean = _sessionAuthStatus.value.requiresReauth

    fun activeUsableToken(): String? {
        val account = sessionStore.activeAccountSnapshot() ?: return null
        return account.accessToken.takeIf { !computeStatus(account).requiresReauth }
    }

    fun clearAuthError() {
        if (_authUiState.value is AuthUiState.Error) {
            _authUiState.value = AuthUiState.Idle
        }
    }

    /**
     * Binds this app's authenticated Client API v1 transport so [IgngFamilySsoProvider] can serve
     * incoming certificate-verified sibling requests for 60-second PKCE S256 handoff codes.
     */
    fun bindFamilyHandoffIssuer(
        issuer: suspend (CoreFamilyHandoffRequest) -> ApiResult<CoreFamilyHandoffResponse>,
    ) {
        IgngFamilyHandoffHost.register { targetClientId, redirectUri, codeChallenge, expectedUserId ->
            val current = sessionStore.activeAccountSnapshot()
                ?: return@register FamilyHandoffIssueResult.Failed("no_active_account")
            if (computeStatus(current) != SessionAuthStatus.ACTIVE) {
                return@register FamilyHandoffIssueResult.Failed("session_not_active")
            }
            if (expectedUserId != null && expectedUserId > 0L && current.userId != expectedUserId) {
                return@register FamilyHandoffIssueResult.Failed("account_mismatch")
            }
            when (
                val res = issuer(
                    CoreFamilyHandoffRequest(
                        targetClientId = targetClientId,
                        redirectUri = redirectUri,
                        codeChallenge = codeChallenge,
                        codeChallengeMethod = "S256",
                    ),
                )
            ) {
                is ApiResult.Success -> {
                    val code = res.data.code.trim()
                    if (code.isBlank()) {
                        FamilyHandoffIssueResult.Failed("empty_handoff_code")
                    } else {
                        FamilyHandoffIssueResult.Issued(
                            code = code,
                            expiresAt = res.data.expiresAt,
                            userId = current.userId,
                            username = current.username,
                        )
                    }
                }
                is ApiResult.Failure -> FamilyHandoffIssueResult.Failed(res.error.errorCode ?: "handoff_rejected")
            }
        }
    }

    val tokenRefresher = SingleFlightTokenRefresher(
        currentTokenProvider = { activeUsableToken() },
        isTokenInvalidated = { invalidatedTokens.contains(it) },
        refreshBlock = { failedAccessToken ->
            val current = sessionStore.activeAccountSnapshot()
            if (current == null) {
                syncStatus()
                return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            }
            if (current.accessToken != failedAccessToken && !invalidatedTokens.contains(current.accessToken)) {
                syncStatus()
                return@SingleFlightTokenRefresher TokenRefreshResult.Refreshed(current.accessToken)
            }
            if (current.refreshToken.isNullOrBlank()) {
                invalidatedTokens.add(failedAccessToken)
                syncStatus()
                onSessionInvalidated()
                return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            }
            try {
                val rotated = oauthClient.refreshSession(current)
                if (rotated == null) {
                    invalidatedTokens.add(failedAccessToken)
                    syncStatus()
                    onSessionInvalidated()
                    return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
                }
                invalidatedTokens.remove(rotated.accessToken)
                sessionStore.upsertAccount(rotated, makeActive = true)
                syncStatus()
                TokenRefreshResult.Refreshed(rotated.accessToken)
            } catch (error: ApiException) {
                if (error.statusCode in listOf(400, 401, 403)) {
                    invalidatedTokens.add(failedAccessToken)
                    syncStatus()
                    onSessionInvalidated()
                    TokenRefreshResult.Invalidated
                } else {
                    TokenRefreshResult.TransientFailure
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                TokenRefreshResult.TransientFailure
            }
        },
    )

    fun launchLogin(context: Context, isDark: Boolean = false, forceSwitchAccount: Boolean = false) {
        clearAuthError()
        oauthClient.launchLogin(context = context, isDark = isDark, forceSwitchAccount = forceSwitchAccount)
    }

    fun isMatchingCallback(uri: Uri?): Boolean = oauthClient.isMatchingCallback(uri)

    /**
     * Unified entry point for both `MainActivity.onCreate` and `MainActivity.onNewIntent`.
     * Runs PKCE token exchange on [appScope] so UI recomposition or Activity recreation can never
     * cancel the exchange, and deduplicates duplicate deliveries of the same callback URI.
     *
     * @return true if [uri] was an OAuth callback belonging to this app (even if deduplicated).
     */
    fun handleIncomingOAuthCallback(uri: Uri?): Boolean {
        if (!isMatchingCallback(uri)) return false
        val nonNullUri = uri ?: return false
        val dedupeKey = buildCallbackDedupeKey(nonNullUri)
        if (dedupeKey != null) {
            val added = synchronized(handledCallbackKeys) {
                if (handledCallbackKeys.contains(dedupeKey)) {
                    false
                } else {
                    if (handledCallbackKeys.size >= 32) {
                        handledCallbackKeys.iterator().let { it.next(); it.remove() }
                    }
                    handledCallbackKeys.add(dedupeKey)
                }
            }
            if (!added) return true
        }

        appScope.launch {
            _authUiState.value = AuthUiState.Busy("正在完成安全授权…")
            when (val result = handleCallbackUri(nonNullUri, enrichAccount)) {
                is OAuthCallbackResult.Success -> {
                    _authUiState.value = AuthUiState.Idle
                    _authEvents.tryEmit("欢迎回来，${result.account.displayName}")
                }
                is OAuthCallbackResult.Error -> {
                    _authUiState.value = AuthUiState.Error(result.message)
                }
                OAuthCallbackResult.Ignored -> {
                    _authUiState.value = AuthUiState.Idle
                }
            }
        }
        return true
    }

    suspend fun handleCallbackUri(
        uri: Uri?,
        enrichAccount: (suspend (SavedAccount) -> SavedAccount)? = this.enrichAccount,
    ): OAuthCallbackResult {
        val result = oauthClient.handleCallbackUri(uri)
        if (result is OAuthCallbackResult.Success) {
            val enriched = enrichSafely(result.account, enrichAccount)
            saveAndActivateAccount(enriched, bumpEpoch = true)
            return OAuthCallbackResult.Success(enriched)
        }
        return result
    }

    /**
     * Explicit user-initiated 1-tap family sync: tries browser-free native PKCE handoff first
     * (`IgngFamilySsoProvider.call` -> `POST /api/v1/auth/family-handoff` -> `POST /api/oauth/token`),
     * and smoothly falls back to Chrome Custom Tabs OAuth if the sibling app or server does not
     * support handoff yet.
     */
    fun signInWithFamilyAccount(
        context: Context,
        hint: FamilySharedAccountHint,
        isDark: Boolean = false,
    ) {
        clearAuthError()
        appScope.launch {
            _authUiState.value = AuthUiState.Busy("正在从 ${hint.sourceAppName} 同步安全授权…")
            when (val outcome = executeNativeFamilyHandoff(hint)) {
                is NativeHandoffOutcome.SignedIn -> {
                    _authUiState.value = AuthUiState.Idle
                    _authEvents.tryEmit("已从 ${hint.sourceAppName} 同步登录：${outcome.account.displayName}")
                }
                is NativeHandoffOutcome.Unavailable -> {
                    _authUiState.value = AuthUiState.Idle
                    withContext(Dispatchers.Main) {
                        oauthClient.launchLogin(
                            context = context,
                            isDark = isDark,
                            forceSwitchAccount = false,
                        )
                    }
                }
                is NativeHandoffOutcome.ExchangeFailed -> {
                    _authUiState.value = AuthUiState.Error(outcome.message)
                }
            }
        }
    }

    /**
     * Refreshes discovered sibling accounts and — when eligible — silently performs browser-free
     * native family handoff in the background (e.g. upgrading a legacy v1 session or seamlessly
     * syncing a newly installed sibling app unless the user explicitly signed out).
     */
    fun syncFamilyAccountsAndAutoSignIn() {
        appScope.launch {
            familySyncMutex.withLock {
                try {
                    val siblings = IgngFamilyAccountDiscovery.discoverSiblingAccounts(appContext)
                    _familyAccounts.value = siblings

                    if (_authUiState.value is AuthUiState.Busy) return@withLock

                    val active = sessionStore.activeAccountSnapshot()
                    val status = computeStatus(active)
                    val candidate = selectAutoSyncCandidate(
                        activeAccount = active,
                        status = status,
                        siblings = siblings,
                        autoSyncSuppressed = sessionStore.isAutoFamilySsoSuppressed(),
                    ) ?: return@withLock

                    val attemptKey = "${candidate.sourcePackage}:${candidate.userId}:${status.name}"
                    if (!attemptedAutoSyncKeys.add(attemptKey)) return@withLock

                    when (val outcome = executeNativeFamilyHandoff(candidate)) {
                        is NativeHandoffOutcome.SignedIn -> {
                            val actionLabel = if (status.requiresReauth) "同步更新授权" else "自动同步登录"
                            _authEvents.tryEmit("已从 ${candidate.sourceAppName} $actionLabel：${outcome.account.displayName}")
                        }
                        is NativeHandoffOutcome.Unavailable,
                        is NativeHandoffOutcome.ExchangeFailed -> {
                            // Silent fail-open: leave IgngFamilySsoBanner visible for manual 1-tap retry/fallback.
                        }
                    }
                } finally {
                    _initialFamilySyncDone.value = true
                }
            }
        }
    }

    private sealed interface NativeHandoffOutcome {
        data class SignedIn(val account: SavedAccount) : NativeHandoffOutcome
        data object Unavailable : NativeHandoffOutcome
        data class ExchangeFailed(val message: String) : NativeHandoffOutcome
    }

    private suspend fun executeNativeFamilyHandoff(hint: FamilySharedAccountHint): NativeHandoffOutcome {
        val pkce = PkceGenerator.generate()
        val grant = IgngFamilyAccountDiscovery.requestSiblingHandoff(
            context = appContext,
            sourcePackage = hint.sourcePackage,
            targetClientId = oauthConfig.clientId,
            redirectUri = oauthConfig.redirectUri,
            codeChallenge = pkce.challenge,
            expectedUserId = hint.userId,
        ) ?: return NativeHandoffOutcome.Unavailable

        return try {
            val baseAccount = oauthClient.exchangeAuthorizationCode(
                code = grant.code,
                codeVerifier = pkce.verifier,
            )
            val enriched = enrichSafely(baseAccount, enrichAccount)
            saveAndActivateAccount(enriched, bumpEpoch = true)
            NativeHandoffOutcome.SignedIn(enriched)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            NativeHandoffOutcome.ExchangeFailed(t.message ?: "跨应用授权交换失败，请稍后重试")
        }
    }

    private suspend fun enrichSafely(
        base: SavedAccount,
        enrich: (suspend (SavedAccount) -> SavedAccount)?,
    ): SavedAccount {
        if (enrich == null) return base
        return try {
            enrich(base)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            base
        }
    }

    fun saveAndActivateAccount(account: SavedAccount, bumpEpoch: Boolean = true) {
        invalidatedTokens.remove(account.accessToken)
        attemptedAutoSyncKeys.clear()
        sessionStore.setAutoFamilySsoSuppressed(false)
        sessionStore.upsertAccount(account, makeActive = true)
        syncStatus()
        if (bumpEpoch) {
            _sessionEpoch.value += 1
        }
    }

    fun updateActiveProfile(
        username: String? = null,
        nickname: String? = null,
        avatarUrl: String? = null,
        adminRoleWire: String? = null,
    ) {
        val current = sessionStore.activeAccountSnapshot() ?: return
        val updated = current.copy(
            username = username?.takeIf { it.isNotBlank() } ?: current.username,
            nickname = nickname?.takeIf { it.isNotBlank() } ?: current.nickname,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            adminRoleWire = adminRoleWire ?: current.adminRoleWire,
        )
        if (updated != current) {
            sessionStore.upsertAccount(updated, makeActive = true)
            syncStatus()
        }
    }

    fun switchAccount(userId: Long): SavedAccount? {
        val target = sessionStore.switchAccount(userId) ?: return null
        attemptedAutoSyncKeys.clear()
        syncStatus()
        _sessionEpoch.value += 1
        return target
    }

    fun removeAccount(userId: Long) {
        sessionStore.accountsSnapshot()
            .firstOrNull { it.userId == userId }
            ?.accessToken
            ?.let { invalidatedTokens.remove(it) }
        val wasActive = sessionStore.activeAccountSnapshot()?.userId == userId
        sessionStore.removeAccount(userId)
        attemptedAutoSyncKeys.clear()
        syncStatus()
        if (wasActive) {
            _sessionEpoch.value += 1
        }
    }

    suspend fun logoutActive() {
        val token = activeUsableToken()
        if (!token.isNullOrBlank()) {
            runCatching { oauthClient.logout(token) }
        }
        sessionStore.activeAccountSnapshot()?.accessToken?.let { invalidatedTokens.remove(it) }
        sessionStore.setAutoFamilySsoSuppressed(true)
        sessionStore.clearActiveAccount()
        attemptedAutoSyncKeys.clear()
        syncStatus()
        _sessionEpoch.value += 1
    }

    /**
     * Queries verified sibling IGNG apps on this device for signed-in accounts.
     */
    suspend fun refreshFamilyAccounts() {
        val siblings = IgngFamilyAccountDiscovery.discoverSiblingAccounts(appContext)
        _familyAccounts.value = siblings
    }

    companion object {
        internal fun buildCallbackDedupeKey(uri: Uri): String? {
            val state = uri.getQueryParameter("state").orEmpty()
            val code = uri.getQueryParameter("code").orEmpty()
            val error = uri.getQueryParameter("error").orEmpty()
            if (state.isBlank() && code.isBlank() && error.isBlank()) return null
            return "$state|$code|$error"
        }

        /**
         * Pure selection policy deciding whether a background silent family handoff should run.
         * - Already ACTIVE -> never auto-sync.
         * - LEGACY_UPGRADE_REQUIRED or EXPIRED -> auto-sync ONLY when a verified sibling holds an
         *   active OAuth 2.1 session for the EXACT same userId.
         * - SIGNED_OUT -> auto-sync ONLY when user has not explicitly logged out of this app.
         */
        internal fun selectAutoSyncCandidate(
            activeAccount: SavedAccount?,
            status: SessionAuthStatus,
            siblings: List<FamilySharedAccountHint>,
            autoSyncSuppressed: Boolean,
        ): FamilySharedAccountHint? {
            if (siblings.isEmpty()) return null
            return when (status) {
                SessionAuthStatus.ACTIVE -> null
                SessionAuthStatus.LEGACY_UPGRADE_REQUIRED,
                SessionAuthStatus.EXPIRED -> {
                    val uid = activeAccount?.userId ?: return null
                    siblings.firstOrNull { it.userId == uid }
                }
                SessionAuthStatus.SIGNED_OUT -> {
                    if (autoSyncSuppressed) null else siblings.firstOrNull()
                }
            }
        }
    }
}
