package net.igng.core.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SessionExpiredEvent(
    val url: String,
    val message: String,
)

class SessionExpiredNotifier {
    private val _events = MutableSharedFlow<SessionExpiredEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SessionExpiredEvent> = _events.asSharedFlow()

    fun notify(url: String, message: String) {
        _events.tryEmit(SessionExpiredEvent(url = url, message = message))
    }

    fun notifyExpired(reason: String = "登录状态已失效，请重新登录") {
        _events.tryEmit(SessionExpiredEvent(url = "", message = reason))
    }
}

sealed interface TokenRefreshResult {
    data class Refreshed(val accessToken: String) : TokenRefreshResult
    data object Invalidated : TokenRefreshResult
    data object TransientFailure : TokenRefreshResult
}

/**
 * Coordinates single-flight OAuth 2.1 refresh-token rotation across concurrent 401 responses.
 * Because `refresh_token` is single-use and replaying a rotated token revokes the entire token
 * family on IGNG SSO, only the first caller executes the refresh RPC while concurrent callers
 * reuse the freshly rotated `access_token`.
 */
class SingleFlightTokenRefresher(
    private val currentTokenProvider: () -> String?,
    private val isTokenInvalidated: (String) -> Boolean = { false },
    private val refreshBlock: suspend (failedAccessToken: String) -> TokenRefreshResult,
) {
    private val mutex = Mutex()

    suspend fun refreshIfNeeded(failedAccessToken: String): TokenRefreshResult = mutex.withLock {
        if (isTokenInvalidated(failedAccessToken)) {
            return@withLock TokenRefreshResult.Invalidated
        }
        val latest = currentTokenProvider()?.trim().orEmpty()
        if (latest.isNotEmpty() && latest != failedAccessToken && !isTokenInvalidated(latest)) {
            return@withLock TokenRefreshResult.Refreshed(latest)
        }
        refreshBlock(failedAccessToken)
    }
}
