package net.igng.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Unified multi-account OAuth 2.1 session model shared by all IGNG Android apps.
 * Field defaults preserve backward-compatible JSON deserialization for existing BBS vaults
 * while also carrying MC/Ink optional metadata (`expiresAt`, `deviceName`, `adminRoleWire`).
 */
@Serializable
data class SavedAccount(
    val userId: Long,
    val username: String,
    val nickname: String = "",
    val email: String? = null,
    val avatarUrl: String? = null,
    val accessToken: String,
    val refreshToken: String? = null,
    val accessExpiresAtEpochSec: Long = 0L,
    val refreshExpiresAtEpochSec: Long = 0L,
    val sessionId: String = "",
    val grantedScope: String = "",
    val expiresAt: String? = null,
    val refreshExpiresAt: String? = null,
    val deviceName: String? = null,
    val adminRoleWire: String? = null,
) {
    val displayName: String get() = nickname.ifBlank { username }
    val publicUserId: String get() = "usr_$userId"
    val userIdInt: Int get() = userId.toInt()

    /** Alias used by MC features migrating to unified SavedAccount. */
    val token: String get() = accessToken
    val scope: String? get() = grantedScope.ifBlank { null }

    fun isAccessTokenExpiringSoon(
        nowEpochSec: Long = Instant.now().epochSecond,
        leewaySec: Long = 90L,
    ): Boolean {
        val exp = resolvedAccessExpiresAtEpochSec() ?: return false
        return exp <= nowEpochSec + leewaySec
    }

    fun isRefreshExpired(nowEpochSec: Long = Instant.now().epochSecond): Boolean {
        val exp = resolvedRefreshExpiresAtEpochSec() ?: return false
        return exp <= nowEpochSec
    }

    private fun resolvedAccessExpiresAtEpochSec(): Long? =
        accessExpiresAtEpochSec.takeIf { it > 0L }
            ?: expiresAt?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }

    private fun resolvedRefreshExpiresAtEpochSec(): Long? =
        refreshExpiresAtEpochSec.takeIf { it > 0L }
            ?: refreshExpiresAt?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
}

enum class SessionAuthStatus {
    /** No local account saved. */
    SIGNED_OUT,

    /** Active OAuth 2.1 session with a usable access token and rotating refresh token. */
    ACTIVE,

    /** Saved OAuth 2.1 session was rejected/revoked by the server and requires re-authorization. */
    EXPIRED,

    /** Pre-OAuth 2.1 legacy session token without a refresh token; must re-authorize once. */
    LEGACY_UPGRADE_REQUIRED,
    ;

    val requiresReauth: Boolean
        get() = this == EXPIRED || this == LEGACY_UPGRADE_REQUIRED
}

@Serializable
data class OAuthPendingRequest(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochSec: Long,
    val forceSwitchAccount: Boolean = false,
) {
    fun isExpired(nowEpochSec: Long = Instant.now().epochSecond, ttlSec: Long = 900L): Boolean =
        nowEpochSec - createdAtEpochSec > ttlSec
}
