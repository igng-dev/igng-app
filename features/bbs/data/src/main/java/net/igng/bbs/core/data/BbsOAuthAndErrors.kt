package net.igng.bbs.core.data

import android.content.Context
import android.net.Uri
import net.igng.bbs.core.model.SiteEndpoints
import net.igng.core.auth.IgngOAuthClient
import net.igng.core.auth.OAuthCallbackResult
import net.igng.core.auth.OAuthClientConfig
import net.igng.core.auth.SecureSessionStore
import net.igng.core.auth.VaultStorageConfig
import net.igng.core.network.ApiException
import net.igng.core.network.SingleFlightTokenRefresher
import net.igng.core.network.TokenRefreshResult

object BbsErrorMapper {
    fun map(status: Int, code: String, serverMessage: String): String = when (code) {
        "bbs.post_not_found" -> serverMessage.ifBlank { "文章不存在或已删除" }
        "bbs.post_forbidden" -> serverMessage.ifBlank { "你暂时无权查看或操作这篇文章" }
        "bbs.post_invalid_state" -> serverMessage.ifBlank { "内容当前状态不允许此操作" }
        "bbs.comment_not_found" -> serverMessage.ifBlank { "评论不存在或已删除" }
        "bbs.comment_blocked" -> serverMessage.ifBlank { "内容触发社区审核规则，请修改后重试" }
        "bbs.column_not_found" -> serverMessage.ifBlank { "专栏不存在" }
        "bbs.column_forbidden" -> serverMessage.ifBlank { "没有该专栏的操作权限" }
        "bbs.column_conflict" -> serverMessage.ifBlank { "专栏标识或状态冲突，请检查后重试" }
        "bbs.series_not_found" -> serverMessage.ifBlank { "系列不存在" }
        "bbs.series_forbidden" -> serverMessage.ifBlank { "没有该系列的管理权限" }
        "bbs.appeal_pending" -> "该内容已有进行中的申诉，请等待复核结果"
        "bbs.appeal_not_allowed" -> serverMessage.ifBlank { "当前处置暂不支持再次申诉" }
        "bbs.report_duplicate" -> "你已举报过这条内容，请等待处理"
        "bbs.daily_limit_exceeded" -> serverMessage.ifBlank { "今日操作次数已达上限，请稍后再试" }
        else -> serverMessage
    }
}

/**
 * Thin BBS adapter over shared [IgngOAuthClient] + [SingleFlightTokenRefresher].
 * Preserves existing BBS v2.0.0/v2.0.1 SharedPreferences vault & Keystore alias for seamless
 * over-the-top upgrades.
 */
class BbsOAuthManager(
    endpoints: SiteEndpoints,
    appVersion: String,
    val sessionStore: SecureSessionStore,
) {
    private val oauthClient = IgngOAuthClient(
        config = OAuthClientConfig(
            clientId = SiteEndpoints.CLIENT_ID,
            redirectUri = SiteEndpoints.OAUTH_REDIRECT_URI,
            callbackScheme = "igngbbs",
            callbackHost = "auth",
            callbackPath = "/callback",
            scopes = null,
            deviceName = "IGNG BBS Android",
            endpoints = endpoints,
            clientVersion = appVersion,
            userAgent = "IGNGbbs-Android/$appVersion",
        ),
        sessionStore = sessionStore,
    )

    val tokenRefresher = SingleFlightTokenRefresher(
        currentTokenProvider = { sessionStore.currentAccessToken() },
        refreshBlock = { failedAccessToken ->
            val active = sessionStore.activeAccount.value ?: return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            if (active.accessToken != failedAccessToken && active.accessToken.isNotBlank()) {
                return@SingleFlightTokenRefresher TokenRefreshResult.Refreshed(active.accessToken)
            }
            if (active.refreshToken.isNullOrBlank()) {
                sessionStore.clearActiveAccount()
                return@SingleFlightTokenRefresher TokenRefreshResult.Invalidated
            }
            try {
                val rotated = oauthClient.refreshSession(active)
                if (rotated == null) {
                    sessionStore.clearActiveAccount()
                    TokenRefreshResult.Invalidated
                } else {
                    sessionStore.upsertAccount(rotated, makeActive = true)
                    TokenRefreshResult.Refreshed(rotated.accessToken)
                }
            } catch (e: ApiException) {
                if (e.statusCode in listOf(400, 401, 403)) {
                    sessionStore.clearActiveAccount()
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

    suspend fun handleCallbackUri(uri: Uri?): OAuthCallbackResult {
        val result = oauthClient.handleCallbackUri(uri)
        if (result is OAuthCallbackResult.Success) {
            sessionStore.upsertAccount(result.account, makeActive = true)
        }
        return result
    }

    suspend fun logoutActive() {
        oauthClient.logout(sessionStore.currentAccessToken())
        sessionStore.clearActiveAccount()
    }

    companion object {
        val VAULT_CONFIG = VaultStorageConfig(
            prefsName = "igngbbs_secure_vault",
            pendingPrefsName = "igngbbs_oauth_pending",
            keyAlias = "net.igng.bbs.vault.aes256",
            deviceIdKey = "device_id",
            legacyPerFieldCompat = false,
        )
    }
}
