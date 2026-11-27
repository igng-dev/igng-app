package net.igng.core.data

import net.igng.core.model.CoreAvatarUploadResponseDto
import net.igng.core.model.CoreDeviceSessionDto
import net.igng.core.model.CoreFamilyHandoffRequest
import net.igng.core.model.CoreFamilyHandoffResponse
import net.igng.core.model.CorePaginatedResponseDto
import net.igng.core.model.CoreRenameDeviceRequest
import net.igng.core.model.CoreUpdateProfileMeRequest
import net.igng.core.model.CoreUserMeDto
import net.igng.core.network.ApiResult
import net.igng.core.network.CryptoChallenge
import net.igng.core.network.IgngHttpClient

/**
 * Unified Client API v1 account, device security, cross-app family handoff, and credential-crypto
 * repository shared across all IGNG official Android apps.
 */
class IgngAccountRepository(
    private val http: IgngHttpClient,
) {
    suspend fun getMe(): ApiResult<CoreUserMeDto> =
        http.get("/users/me", requireAuth = true)

    suspend fun updateUsername(username: String): ApiResult<CoreUserMeDto> =
        http.patch("/users/me", CoreUpdateProfileMeRequest(username))

    suspend fun uploadAvatar(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ApiResult<CoreAvatarUploadResponseDto> = http.uploadMultipart(
        path = "/users/me/avatar",
        fieldName = "file",
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
    )

    suspend fun listDevices(
        page: Int = 1,
        pageSize: Int = 30,
    ): ApiResult<CorePaginatedResponseDto<CoreDeviceSessionDto>> = http.get(
        path = "/devices",
        query = mapOf("page" to page.toString(), "page_size" to pageSize.toString()),
        requireAuth = true,
    )

    suspend fun renameDevice(deviceId: String, newName: String): ApiResult<CoreDeviceSessionDto> =
        http.patch("/devices/$deviceId", CoreRenameDeviceRequest(newName))

    suspend fun revokeDevice(deviceId: String): ApiResult<Unit> =
        http.deleteNoContent("/devices/$deviceId")

    suspend fun fetchCryptoChallenge(): ApiResult<CryptoChallenge> =
        http.get("/auth/crypto", requireAuth = true)

    suspend fun requestFamilyHandoff(request: CoreFamilyHandoffRequest): ApiResult<CoreFamilyHandoffResponse> =
        http.post("/auth/family-handoff", request, idempotencyKey = null, requireAuth = true)
}
