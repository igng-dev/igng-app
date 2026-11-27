package net.igng.mcstatus.core.data

import android.net.Uri
import java.io.File
import kotlinx.serialization.json.Json
import net.igng.core.auth.IgngOAuthClient
import net.igng.core.auth.PkcePair
import net.igng.core.auth.SecureSessionStore
import net.igng.core.auth.VaultStorageConfig
import net.igng.core.model.OAuthPendingRequest
import net.igng.core.model.SavedAccount
import net.igng.core.network.CryptoChallenge
import net.igng.core.network.IgngHttpClient
import net.igng.mcstatus.core.model.BootstrapResponse
import net.igng.mcstatus.core.model.McAdminRole
import net.igng.mcstatus.core.model.SiteEndpoints

/**
 * Thin MC facade over shared [IgngHttpClient] keeping MC repositories concise while running on
 * the single unified Client API v1 transport and 401 single-flight refresh engine.
 */
class ApiClient(
    private val httpClient: IgngHttpClient,
) {
    val json: Json get() = httpClient.json

    suspend fun get(
        url: String,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
        allowAnonymousFallback: Boolean = false,
    ): String = httpClient.getRaw(url, tokenOverride, suppressAuthNotify, allowAnonymousFallback)

    suspend fun post(
        url: String,
        body: String = "{}",
        idempotencyKey: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = httpClient.postRaw(url, body, idempotencyKey, tokenOverride, suppressAuthNotify)

    suspend fun patch(
        url: String,
        body: String = "{}",
        ifMatch: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = httpClient.patchRaw(url, body, ifMatch, tokenOverride, suppressAuthNotify)

    suspend fun delete(
        url: String,
        body: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = httpClient.deleteRaw(url, body, tokenOverride, suppressAuthNotify)

    suspend fun uploadMultipart(
        url: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
        idempotencyKey: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = httpClient.uploadMultipartRaw(
        url = url,
        fieldName = fieldName,
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        extraFields = extraFields,
        idempotencyKey = idempotencyKey,
        tokenOverride = tokenOverride,
        suppressAuthNotify = suppressAuthNotify,
    )

    suspend fun getBytes(
        url: String,
        allowAnonymousFallback: Boolean = false,
    ): ByteArray = httpClient.getBytes(url, allowAnonymousFallback)

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        allowAnonymousFallback: Boolean = false,
    ): File = httpClient.downloadToFile(url, targetFile, allowAnonymousFallback)

    suspend fun uploadImage(
        url: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
        idempotencyKey: String? = null,
    ): String = uploadMultipart(
        url = url,
        fieldName = "file",
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        extraFields = extraFields,
        idempotencyKey = idempotencyKey,
    )

    companion object {
        val DefaultJson: Json get() = net.igng.core.network.IgngJson.instance
    }
}

class OAuthPendingStore(private val sessionStore: SecureSessionStore) {
    fun save(pair: PkcePair) {
        sessionStore.savePendingOAuth(
            OAuthPendingRequest(
                state = pair.state,
                codeVerifier = pair.verifier,
                createdAtEpochSec = System.currentTimeMillis() / 1000L,
            ),
        )
    }

    fun consume(expectedState: String): String? =
        sessionStore.consumePendingOAuth(expectedState)?.codeVerifier

    fun clear() {
        sessionStore.clearPendingOAuth()
    }
}

class AuthRepository(
    private val endpoints: SiteEndpoints,
    private val apiClient: ApiClient,
    private val oauthClient: IgngOAuthClient,
) {
    private val json: Json get() = apiClient.json

    fun buildAuthorizeUri(pkce: PkcePair, forceLogin: Boolean = false): Uri =
        oauthClient.buildAuthorizeUri(pkce, forceSwitchAccount = forceLogin)

    suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String): SavedAccount {
        val baseAccount = oauthClient.exchangeAuthorizationCode(code, codeVerifier)
        val bootstrap = runCatching { fetchBootstrap(baseAccount.accessToken) }.getOrNull()
        val viewerUser = bootstrap?.viewer?.user
        val adminRole = McAdminRole.fromWire(bootstrap?.viewer?.adminRole)
        return baseAccount.copy(
            userId = viewerUser?.resolvedNumericId?.takeIf { it > 0 }?.toLong() ?: baseAccount.userId,
            username = viewerUser?.username?.takeIf { it.isNotBlank() } ?: baseAccount.username,
            nickname = viewerUser?.nickname ?: baseAccount.nickname,
            avatarUrl = viewerUser?.avatarUrl ?: baseAccount.avatarUrl,
            deviceName = "IGNGmc Android",
            adminRoleWire = adminRole?.name,
        )
    }

    suspend fun refreshSession(account: SavedAccount): SavedAccount? =
        oauthClient.refreshSession(account)

    suspend fun fetchBootstrap(token: String? = null): BootstrapResponse =
        json.decodeFromString(
            apiClient.get(
                url = "${endpoints.api}/api/v1/mc/bootstrap",
                tokenOverride = token,
                suppressAuthNotify = !token.isNullOrBlank(),
            ),
        )

    suspend fun logout(token: String) {
        oauthClient.logout(token)
    }

    suspend fun fetchCryptoChallenge(token: String? = null): CryptoChallenge =
        json.decodeFromString(
            apiClient.get(
                url = "${endpoints.api}/api/v1/auth/crypto",
                tokenOverride = token,
                suppressAuthNotify = true,
            ),
        )

    companion object {
        const val CLIENT_ID = "igng_mc_android"
        const val REDIRECT_URI = "igngmc://auth/callback"
        const val CALLBACK_SCHEME = "igngmc"
        const val CALLBACK_HOST = "auth"
        const val CALLBACK_PATH = "/callback"

        val VAULT_CONFIG = VaultStorageConfig(
            prefsName = "igng_mc_auth",
            pendingPrefsName = "igng_mc_oauth_pending",
            keyAlias = "igng_mc_session_key",
            deviceIdKey = "v1_device_id",
            legacyPerFieldCompat = true,
        )
    }
}
