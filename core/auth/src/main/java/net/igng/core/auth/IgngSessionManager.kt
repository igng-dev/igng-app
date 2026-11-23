package net.igng.core.auth

import android.content.Context
import android.net.Uri
import java.util.Collections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import net.igng.core.network.ApiException
import net.igng.core.network.SessionExpiredNotifier
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.core.network.TokenRefreshResult
import okhttp3.OkHttpClient

/**
 * Unified non-destructive OAuth 2.1 session manager & cross-app family SSO coordinator shared by
 * IGNG BBS, IGNGmc, and future IGNG Ink.
 *
 * Key guarantees:
 * - Preserves each app's exact [VaultStorageConfig] prefs/Keystore alias for seamless in-place upgrades.
 * - Never deletes saved account identity or user drafts on 401 refresh invalidation: transitions
 *   [sessionAuthStatus] to [SessionAuthStatus.EXPIRED] / [SessionAuthStatus.LEGACY_UPGRADE_REQUIRED]
 *   so public GETs fall back anonymously and UI offers 1-tap re-auth.
 * - Exposes [familyAccounts] discovered from sibling IGNG apps installed on the same device
 *   (Microsoft MSAL / Account Discovery pattern) so users can continue across apps in 1 tap.
 */
class IgngSessionManager(
    context: Context,
    val oauthConfig: OAuthClientConfig,
    vaultConfig: VaultStorageConfig,
    baseOkHttpClient: OkHttpClient? = null,
    private val onSessionInvalidated: () -> Unit = {},
) {
    private val appContext = context.applicationContext
    val sessionStore = SecureSessionStore(appContext, vaultConfig)
    val oauthClient = IgngOAuthClient(oauthConfig, sessionStore, baseOkHttpClient)
    val sessionExpiredNotifier = SessionExpiredNotifier()

    private val invalidatedTokens: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    val activeAccount: StateFlow<SavedAccount?> get() = sessionStore.activeAccount
    val accounts: StateFlow<List<SavedAccount>> get() = sessionStore.accounts

    private val _sessionAuthStatus = MutableStateFlow(computeStatus(sessionStore.activeAccountSnapshot()))
    val sessionAuthStatus: StateFlow<SessionAuthStatus> = _sessionAuthStatus.asStateFlow()

    private val _sessionEpoch = MutableStateFlow(0)
    val sessionEpoch: StateFlow<Int> = _sessionEpoch.asStateFlow()

    private val _familyAccounts = MutableStateFlow<List<FamilySharedAccountHint>>(emptyList())
    val familyAccounts: StateFlow<List<FamilySharedAccountHint>> = _familyAccounts.asStateFlow()

    fun computeStatus(account: SavedAccount?): SessionAuthStatus = when {
        account == null -> SessionAuthStatus.SIGNED_OUT
        account.refreshToken.isNullOrBlank() -> SessionAuthStatus.LEGACY_UPGRADE_REQUIRED
        invalidatedTokens.contains(account.accessToken) -> SessionAuthStatus.EXPIRED
        else -> SessionAuthStatus.ACTIVE
    }

    private fun syncStatus() {
        _sessionAuthStatus.value = computeStatus(sessionStore.activeAccountSnapshot())
    }

    fun isActiveSessionInvalidated(): Boolean = _sessionAuthStatus.value.requiresReauth

    fun activeUsableToken(): String? {
        val account = sessionStore.activeAccountSnapshot() ?: return null
        return account.accessToken.takeIf { !computeStatus(account).requiresReauth }
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
            } catch (_: Exception) {
                TokenRefreshResult.TransientFailure
            }
        },
    )

    fun launchLogin(context: Context, isDark: Boolean = false, forceSwitchAccount: Boolean = false) {
        oauthClient.launchLogin(context = context, isDark = isDark, forceSwitchAccount = forceSwitchAccount)
    }

    fun isMatchingCallback(uri: Uri?): Boolean = oauthClient.isMatchingCallback(uri)

    suspend fun handleCallbackUri(
        uri: Uri?,
        enrichAccount: (suspend (SavedAccount) -> SavedAccount)? = null,
    ): OAuthCallbackResult {
        val result = oauthClient.handleCallbackUri(uri)
        if (result is OAuthCallbackResult.Success) {
            val enriched = if (enrichAccount != null) {
                runCatching { enrichAccount(result.account) }.getOrDefault(result.account)
            } else {
                result.account
            }
            saveAndActivateAccount(enriched, bumpEpoch = true)
            return OAuthCallbackResult.Success(enriched)
        }
        return result
    }

    fun saveAndActivateAccount(account: SavedAccount, bumpEpoch: Boolean = true) {
        invalidatedTokens.remove(account.accessToken)
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
        sessionStore.clearActiveAccount()
        syncStatus()
        _sessionEpoch.value += 1
    }

    /**
     * Queries sibling IGNG apps on this device for signed-in accounts so UI can offer
     * 1-tap family SSO sign-in / account linking.
     */
    suspend fun refreshFamilyAccounts() {
        val siblings = IgngFamilyAccountDiscovery.discoverSiblingAccounts(appContext)
        _familyAccounts.value = siblings
    }
}
