package net.igng.core.network

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDetailDto(
    val field: String? = null,
    val issue: String? = null,
    val expected: String? = null,
)

@Serializable
data class ApiErrorBodyDto(
    val code: String = "server.internal_error",
    val message: String = "",
    val details: List<ApiErrorDetailDto> = emptyList(),
)

@Serializable
data class ApiErrorEnvelopeDto(
    val error: ApiErrorBodyDto = ApiErrorBodyDto(),
    @SerialName("request_id") val requestId: String? = null,
    val timestamp: String? = null,
)

/**
 * Unified Client API v1 exception shared by all IGNG Android apps.
 * Distinguishes OAuth session failures ([isSessionAuthFailure]) from business-level HTTP 401
 * responses (e.g. `auth.credential_invalid` or domain credential checks) so single-flight refresh
 * never mistakenly rotates or revokes an active session on credential-check errors.
 */
class ApiException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String,
    val requestId: String? = null,
    val retryAfterSeconds: Long? = null,
) : IOException(friendlyMessage(statusCode, errorCode.orEmpty(), serverMessage)) {

    val httpStatus: Int get() = statusCode
    override val message: String get() = super.message ?: friendlyMessage(statusCode, errorCode.orEmpty(), serverMessage)

    val isUnauthorized: Boolean get() = statusCode == 401

    val isSessionAuthFailure: Boolean
        get() = statusCode == 401 && (
            errorCode.isNullOrBlank() || (
                errorCode.startsWith("auth.", ignoreCase = true) &&
                    !errorCode.equals("auth.credential_invalid", ignoreCase = true)
                )
            )

    val isForbidden: Boolean get() = statusCode == 403
    val isNotFound: Boolean get() = statusCode == 404
    val isRateLimited: Boolean
        get() = statusCode == 429 ||
            errorCode == "rate_limit.exceeded" ||
            errorCode?.endsWith(".daily_limit_exceeded", ignoreCase = true) == true
    val isNetworkOrServer: Boolean get() = statusCode == 0 || statusCode >= 500
    val isServerTransient: Boolean get() = statusCode in 502..504

    companion object {
        fun friendlyMessage(status: Int, code: String, serverMessage: String): String = when (code) {
            "auth.token_missing", "auth.token_expired", "auth.token_invalid", "auth.session_revoked" ->
                "登录状态已失效，请重新登录"
            "auth.insufficient_scope", "auth.insufficient_role" ->
                serverMessage.ifBlank { "当前账户没有执行该操作的权限" }
            "rate_limit.exceeded" -> "请求过于频繁，请稍后再试"
            "media.too_large" -> "图片体积超出上限，请压缩后再上传"
            "media.invalid_format" -> "不支持的图片格式，请换一张再试"
            "media.quota_exceeded" -> "云端媒体空间已满，请先清理无用图片"
            "network.offline" -> "网络连接失败，请检查网络后重试"
            "network.timeout" -> "请求超时，请稍后重试"
            else -> serverMessage.ifBlank {
                when {
                    status == 0 -> "网络连接异常，请稍后重试"
                    status >= 500 -> "服务暂时不可用，请稍后重试"
                    else -> "操作未能完成，请稍后重试"
                }
            }
        }
    }
}

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiException) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (ApiException) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}
