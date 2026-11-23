package net.igng.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unified Client API v1 pagination envelope shared across all IGNG Android apps.
 */
@Serializable
data class CorePaginationDto(
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 20,
    val total: Int = 0,
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
data class CorePaginatedResponseDto<T>(
    val data: List<T> = emptyList(),
    val pagination: CorePaginationDto = CorePaginationDto(),
)

@Serializable
data class CoreListWrapperDto<T>(
    val data: List<T> = emptyList(),
)

/**
 * Unified `POST /api/v1/users/me/daily-login` response DTO matching OpenAPI v1 (`multiplier` is float).
 */
@Serializable
data class CoreDailyLoginDto(
    val awarded: Boolean = false,
    val exp: Int? = null,
    @SerialName("base_exp") val baseExp: Int? = null,
    val multiplier: Double = 1.0,
    val level: Int? = null,
    val period: String = "",
)

/**
 * Unified `GET/PATCH /api/v1/users/me` response DTO.
 */
@Serializable
data class CoreUserMeDto(
    val id: String = "",
    val username: String = "",
    val nickname: String? = null,
    val email: String? = null,
    val status: String = "normal",
    @SerialName("avatar_media_id") val avatarMediaId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * Unified `POST /api/v1/users/me/avatar` response DTO.
 */
@Serializable
data class CoreAvatarUploadResponseDto(
    @SerialName("avatar_media_id") val avatarMediaId: String = "",
    @SerialName("avatar_url") val avatarUrl: String = "",
)

/**
 * Unified `GET/PATCH/DELETE /api/v1/devices` session DTO.
 */
@Serializable
data class CoreDeviceSessionDto(
    val id: String = "",
    @SerialName("session_id") val sessionId: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("client_label") val clientLabel: String? = null,
    @SerialName("ip_address") val ipAddress: String? = null,
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class CoreUpdateProfileMeRequest(
    val username: String,
)

@Serializable
data class CoreRenameDeviceRequest(
    @SerialName("device_name") val deviceName: String,
)

/**
 * Non-secret public account identity discovered from a sibling IGNG family app on the same device
 * (Microsoft MSAL / Account Discovery pattern). Never contains access or refresh tokens.
 */
@Serializable
data class FamilySharedAccountHint(
    val userId: Long,
    val username: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val sourcePackage: String = "",
    val sourceAppName: String = "",
    val isActiveInSource: Boolean = false,
) {
    val displayName: String get() = nickname.ifBlank { username }
}
