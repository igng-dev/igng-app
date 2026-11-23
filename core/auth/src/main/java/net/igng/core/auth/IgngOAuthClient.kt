package net.igng.core.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.igng.core.model.IgngCoreEndpoints
import net.igng.core.model.OAuthPendingRequest
import net.igng.core.model.SavedAccount
import net.igng.core.network.ApiErrorEnvelopeDto
import net.igng.core.network.ApiException
import net.igng.core.network.IgngJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class OAuthTokenUser(
    val id: Long = 0L,
    val username: String = "",
    val nickname: String? = null,
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
    @SerialName("refresh_expires_at") val refreshExpiresAt: String? = null,
    @SerialName("session_id") val sessionId: String = "",
    val scope: String = "",
    val user: OAuthTokenUser? = null,
)

@Serializable
private data class OAuthExchangeBody(
    @SerialName("grant_type") val grantType: String,
    @SerialName("client_id") val clientId: String,
    val code: String? = null,
    @SerialName("redirect_uri") val redirectUri: String? = null,
    @SerialName("code_verifier") val codeVerifier: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

sealed interface OAuthCallbackResult {
    data class Success(val account: SavedAccount) : OAuthCallbackResult
    data class Error(val message: String) : OAuthCallbackResult
    data object Ignored : OAuthCallbackResult
}

data class OAuthClientConfig(
    val clientId: String,
    val redirectUri: String,
    val callbackScheme: String,
    val callbackHost: String = "auth",
    val callbackPath: String = "/callback",
    val scopes: String? = null,
    val deviceName: String,
    val endpoints: IgngCoreEndpoints,
    val clientVersion: String,
    val userAgent: String,
)

object CustomTabsLauncher {
    fun launchOAuth(context: Context, uri: Uri, isDark: Boolean = false) {
        val toolbarColor = if (isDark) 0xFF0B0F16.toInt() else 0xFFF4F6FA.toInt()
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        intent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        runCatching {
            intent.launchUrl(context, uri)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    fun openExternalUrl(context: Context, url: String, isDark: Boolean = false) {
        val uri = Uri.parse(url)
        val toolbarColor = if (isDark) 0xFF0B0F16.toInt() else 0xFFF4F6FA.toInt()
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        runCatching {
            intent.launchUrl(context, uri)
        }.onFailure {
            val fallback = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }
}

/**
 * Shared first-party OAuth 2.1 Authorization Code + PKCE S256 client for all IGNG Android apps.
 */
class IgngOAuthClient(
    val config: OAuthClientConfig,
    val sessionStore: SecureSessionStore,
    baseOkHttpClient: OkHttpClient? = null,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val oauthHttp: OkHttpClient = (baseOkHttpClient?.newBuilder() ?: OkHttpClient.Builder())
        .apply { interceptors().clear() }
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    fun buildAuthorizeUri(pkce: PkcePair, forceSwitchAccount: Boolean = false): Uri {
        val builder = Uri.parse(config.endpoints.oauthAuthorizeUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", config.clientId)
            .appendQueryParameter("redirect_uri", config.redirectUri)
            .appendQueryParameter("state", pkce.state)
            .appendQueryParameter("code_challenge", pkce.challenge)
            .appendQueryParameter("code_challenge_method", "S256")
        if (!config.scopes.isNullOrBlank()) {
            builder.appendQueryParameter("scope", config.scopes)
        }
        if (forceSwitchAccount) {
            builder.appendQueryParameter("prompt", "login")
        }
        return builder.build()
    }

    fun launchLogin(context: Context, isDark: Boolean = false, forceSwitchAccount: Boolean = false) {
        val pkce = PkceGenerator.generate()
        sessionStore.savePendingOAuth(
            OAuthPendingRequest(
                state = pkce.state,
                codeVerifier = pkce.verifier,
                createdAtEpochSec = Instant.now().epochSecond,
                forceSwitchAccount = forceSwitchAccount,
            ),
        )
        CustomTabsLauncher.launchOAuth(context, buildAuthorizeUri(pkce, forceSwitchAccount), isDark)
    }

    fun isMatchingCallback(uri: Uri?): Boolean {
        if (uri == null) return false
        return uri.scheme == config.callbackScheme &&
            uri.host == config.callbackHost &&
            uri.path == config.callbackPath
    }

    suspend fun handleCallbackUri(uri: Uri?): OAuthCallbackResult {
        if (!isMatchingCallback(uri)) return OAuthCallbackResult.Ignored
        val nonNullUri = uri!!
        val error = nonNullUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            return OAuthCallbackResult.Error(
                if (error == "access_denied") "你取消了登录授权" else "授权未完成（$error）",
            )
        }
        val code = nonNullUri.getQueryParameter("code")
        val state = nonNullUri.getQueryParameter("state")
        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            return OAuthCallbackResult.Error("授权回调参数不完整")
        }
        val pending = sessionStore.consumePendingOAuth(state)
            ?: return OAuthCallbackResult.Error("授权状态已过期或不匹配，请重新发起登录")

        return runCatching {
            exchangeAuthorizationCode(code = code, codeVerifier = pending.codeVerifier)
        }.fold(
            onSuccess = { OAuthCallbackResult.Success(it) },
            onFailure = { OAuthCallbackResult.Error(it.message ?: "交换令牌失败，请稍后重试") },
        )
    }

    suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String): SavedAccount =
        withContext(Dispatchers.IO) {
            val payload = OAuthExchangeBody(
                grantType = "authorization_code",
                clientId = config.clientId,
                code = code,
                redirectUri = config.redirectUri,
                codeVerifier = codeVerifier,
            )
            val resp = postTokenEndpoint(payload)
            val access = resp.accessToken.takeIf { it.isNotBlank() }
                ?: throw ApiException(500, "oauth.invalid_response", "授权交换未返回有效访问令牌")
            val user = resp.user
                ?: throw ApiException(500, "oauth.invalid_response", "授权交换未返回用户信息")
            val nowSec = Instant.now().epochSecond
            val accessExpSec = nowSec + resp.expiresIn.coerceAtLeast(60L)
            val accessExpIso = Instant.ofEpochSecond(accessExpSec).toString()
            val refreshExpSec = resp.refreshExpiresAt?.let {
                runCatching { Instant.parse(it).epochSecond }.getOrNull()
            } ?: (nowSec + 30L * 86_400L)

            SavedAccount(
                userId = user.id,
                username = user.username,
                nickname = user.nickname.orEmpty(),
                email = user.email,
                avatarUrl = user.avatarUrl,
                accessToken = access,
                refreshToken = resp.refreshToken?.takeIf { it.isNotBlank() },
                accessExpiresAtEpochSec = accessExpSec,
                refreshExpiresAtEpochSec = refreshExpSec,
                expiresAt = accessExpIso,
                refreshExpiresAt = resp.refreshExpiresAt,
                sessionId = resp.sessionId,
                grantedScope = resp.scope.ifBlank { config.scopes.orEmpty() },
                deviceName = config.deviceName,
            )
        }

    /**
     * Rotates the single-use OAuth 2.1 `refresh_token`.
     * Returns null when no refresh token is stored or server rejects the token.
     * Throws [ApiException] on HTTP errors so callers can distinguish permanent 400/401/403
     * invalidation from transient network/5xx errors.
     */
    suspend fun refreshSession(account: SavedAccount): SavedAccount? = withContext(Dispatchers.IO) {
        val currentRefresh = account.refreshToken?.takeIf { it.isNotBlank() } ?: return@withContext null
        val payload = OAuthExchangeBody(
            grantType = "refresh_token",
            clientId = config.clientId,
            refreshToken = currentRefresh,
        )
        val resp = postTokenEndpoint(payload)
        val newAccess = resp.accessToken.takeIf { it.isNotBlank() } ?: return@withContext null
        val newRefresh = resp.refreshToken?.takeIf { it.isNotBlank() } ?: return@withContext null
        val nowSec = Instant.now().epochSecond
        val accessExpSec = nowSec + resp.expiresIn.coerceAtLeast(60L)
        val accessExpIso = Instant.ofEpochSecond(accessExpSec).toString()
        val refreshExpSec = resp.refreshExpiresAt?.let {
            runCatching { Instant.parse(it).epochSecond }.getOrNull()
        } ?: account.refreshExpiresAtEpochSec

        account.copy(
            accessToken = newAccess,
            refreshToken = newRefresh,
            accessExpiresAtEpochSec = accessExpSec,
            refreshExpiresAtEpochSec = refreshExpSec,
            expiresAt = accessExpIso,
            refreshExpiresAt = resp.refreshExpiresAt ?: account.refreshExpiresAt,
            sessionId = resp.sessionId.ifBlank { account.sessionId },
            grantedScope = resp.scope.ifBlank { account.grantedScope },
            username = resp.user?.username?.takeIf { it.isNotBlank() } ?: account.username,
            nickname = resp.user?.nickname?.takeIf { it.isNotBlank() } ?: account.nickname,
            email = resp.user?.email ?: account.email,
            avatarUrl = resp.user?.avatarUrl ?: account.avatarUrl,
        )
    }

    suspend fun logout(accessToken: String? = sessionStore.currentAccessToken()) = withContext(Dispatchers.IO) {
        val token = accessToken?.takeIf { it.isNotBlank() } ?: return@withContext
        runCatching {
            val req = Request.Builder()
                .url("${config.endpoints.apiV1Base}/auth/logout")
                .header("User-Agent", config.userAgent)
                .header("X-Client-Id", config.clientId)
                .header("X-Client-Version", config.clientVersion)
                .header("X-Device-Id", sessionStore.getOrCreateDeviceId())
                .header("Accept", "application/json")
                .header("Idempotency-Key", UUID.randomUUID().toString().replace("-", ""))
                .header("Authorization", "Bearer $token")
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            oauthHttp.newCall(req).execute().close()
        }
    }

    private fun postTokenEndpoint(payload: OAuthExchangeBody): OAuthTokenResponse {
        val bodyJson = IgngJson.instance.encodeToString(OAuthExchangeBody.serializer(), payload)
        val request = Request.Builder()
            .url(config.endpoints.oauthTokenUrl)
            .header("User-Agent", config.userAgent)
            .header("X-Client-Id", config.clientId)
            .header("X-Client-Version", config.clientVersion)
            .header("X-Device-Id", sessionStore.getOrCreateDeviceId())
            .header("Accept", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
        oauthHttp.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val parsed = runCatching {
                    IgngJson.instance.decodeFromString(ApiErrorEnvelopeDto.serializer(), raw)
                }.getOrNull()
                val errCode = parsed?.error?.code
                val errMsg = parsed?.error?.message?.ifBlank { null } ?: "授权服务返回错误（HTTP ${resp.code}）"
                throw ApiException(resp.code, errCode, errMsg, parsed?.requestId)
            }
            return IgngJson.instance.decodeFromString(OAuthTokenResponse.serializer(), raw)
        }
    }
}
