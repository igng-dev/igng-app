package net.igng.core.network

import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import net.igng.core.model.IgngCoreEndpoints
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object IgngJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
}

data class IgngClientConfig(
    val clientId: String,
    val clientVersion: String,
    val userAgent: String,
    val endpoints: IgngCoreEndpoints,
)

/**
 * Unified Client API v1 HTTP & media client shared across all IGNG Android apps.
 *
 * Enforces:
 * - Required gateway headers (`X-Client-Id`, `X-Client-Version`, `X-Device-Id`, `User-Agent`)
 * - Trusted-host Bearer token isolation (never leaks Bearer tokens to third-party hosts)
 * - Scoped 401 single-flight OAuth 2.1 refresh-token rotation ([ApiException.isSessionAuthFailure])
 * - Automatic `Idempotency-Key` on `/api/v1/...` POST requests
 * - Coil / binary media client (`mediaOkHttpClient`) wired to the exact same single-flight 401 refresher
 */
class IgngHttpClient(
    val config: IgngClientConfig,
    private val deviceIdProvider: () -> String,
    private val tokenProvider: () -> String?,
    private val tokenRefresher: SingleFlightTokenRefresher? = null,
    val sessionExpiredNotifier: SessionExpiredNotifier? = null,
    private val hasInvalidatedSession: () -> Boolean = { false },
    private val errorMessageMapper: ((status: Int, code: String, serverMessage: String) -> String)? = null,
    val json: Json = IgngJson.instance,
    extraInterceptor: okhttp3.Interceptor? = null,
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    val endpoints: IgngCoreEndpoints get() = config.endpoints

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val urlStr = original.url.toString()
            val skipAuth = original.header(SKIP_AUTH_HEADER) != null
            val builder = original.newBuilder()
                .removeHeader(SKIP_AUTH_HEADER)
                .header("User-Agent", config.userAgent)

            if (endpoints.isTrustedAuthHost(urlStr)) {
                builder
                    .header("X-Client-Id", config.clientId)
                    .header("X-Client-Version", config.clientVersion)
                    .header("X-Device-Id", deviceIdProvider())
                if (original.header("Accept").isNullOrBlank()) {
                    builder.header("Accept", "application/json")
                }
                if (!skipAuth && original.header("Authorization").isNullOrBlank()) {
                    tokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                        builder.header("Authorization", "Bearer $token")
                    }
                }
            }
            chain.proceed(builder.build())
        }
        .apply {
            if (extraInterceptor != null) addInterceptor(extraInterceptor)
        }
        .build()

    /**
     * Derived OkHttpClient for Coil image loading & binary media fetching.
     * Attaches IGNG gateway auth ONLY to trusted hosts and transparently handles 401 responses
     * via the shared [SingleFlightTokenRefresher], with safe anonymous fallback if the session
     * is invalidated so public images continue rendering.
     */
    val mediaOkHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .addInterceptor { chain ->
            val original = chain.request()
            val urlStr = original.url.toString()
            if (endpoints.isTrustedAuthHost(urlStr) && original.header("Accept").isNullOrBlank()) {
                chain.proceed(
                    original.newBuilder()
                        .header("Accept", "image/webp,image/*,*/*;q=0.8")
                        .build(),
                )
            } else {
                chain.proceed(original)
            }
        }
        .authenticator { _, response ->
            if (responseCount(response) >= 2) return@authenticator null
            val request = response.request
            val urlStr = request.url.toString()
            if (!endpoints.isTrustedAuthHost(urlStr)) return@authenticator null
            val sentBearer = extractBearer(request.header("Authorization")) ?: return@authenticator null
            val refresher = tokenRefresher ?: return@authenticator null

            when (val outcome = runBlocking { refresher.refreshIfNeeded(sentBearer) }) {
                is TokenRefreshResult.Refreshed -> request.newBuilder()
                    .header("Authorization", "Bearer ${outcome.accessToken}")
                    .build()
                TokenRefreshResult.Invalidated -> {
                    sessionExpiredNotifier?.notify(urlStr, "登录状态已失效，请重新登录")
                    if (request.header(MEDIA_ANON_RETRY_HEADER) != null) {
                        null
                    } else {
                        request.newBuilder()
                            .removeHeader("Authorization")
                            .header(SKIP_AUTH_HEADER, "1")
                            .header(MEDIA_ANON_RETRY_HEADER, "1")
                            .build()
                    }
                }
                TokenRefreshResult.TransientFailure -> null
            }
        }
        .build()

    fun buildUrl(path: String, query: Map<String, String?> = emptyMap()): String {
        val base = if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            "${endpoints.apiV1Base}/${path.trimStart('/')}"
        }
        val parsed = base.toHttpUrlOrNull() ?: return base
        val builder = parsed.newBuilder()
        query.forEach { (k, v) ->
            if (!v.isNullOrBlank()) builder.addQueryParameter(k, v)
        }
        return builder.build().toString()
    }

    // -------------------------------------------------------------------------
    // Typed ApiResult<T> API (used by BBS and new apps such as Ink)
    // -------------------------------------------------------------------------

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String?> = emptyMap(),
        requireAuth: Boolean = false,
    ): ApiResult<T> = executeJson(
        method = "GET",
        path = path,
        query = query,
        bodyJson = null,
        deserializer = json.serializersModule.serializer(),
        requireAuth = requireAuth,
    )

    suspend inline fun <reified Req, reified Res> post(
        path: String,
        body: Req,
        idempotencyKey: String? = UUID.randomUUID().toString(),
        requireAuth: Boolean = true,
    ): ApiResult<Res> = executeJson(
        method = "POST",
        path = path,
        query = emptyMap(),
        bodyJson = json.encodeToString(json.serializersModule.serializer<Req>(), body),
        deserializer = json.serializersModule.serializer(),
        idempotencyKey = idempotencyKey,
        requireAuth = requireAuth,
    )

    suspend inline fun <reified Res> postEmpty(
        path: String,
        idempotencyKey: String? = UUID.randomUUID().toString(),
        requireAuth: Boolean = true,
    ): ApiResult<Res> = executeJson(
        method = "POST",
        path = path,
        query = emptyMap(),
        bodyJson = "{}",
        deserializer = json.serializersModule.serializer(),
        idempotencyKey = idempotencyKey,
        requireAuth = requireAuth,
    )

    suspend inline fun <reified Req, reified Res> patch(
        path: String,
        body: Req,
        ifMatch: String? = null,
        requireAuth: Boolean = true,
    ): ApiResult<Res> = executeJson(
        method = "PATCH",
        path = path,
        query = emptyMap(),
        bodyJson = json.encodeToString(json.serializersModule.serializer<Req>(), body),
        deserializer = json.serializersModule.serializer(),
        ifMatch = ifMatch,
        requireAuth = requireAuth,
    )

    suspend inline fun <reified Req, reified Res> put(
        path: String,
        body: Req,
        requireAuth: Boolean = true,
    ): ApiResult<Res> = executeJson(
        method = "PUT",
        path = path,
        query = emptyMap(),
        bodyJson = json.encodeToString(json.serializersModule.serializer<Req>(), body),
        deserializer = json.serializersModule.serializer(),
        requireAuth = requireAuth,
    )

    suspend inline fun <reified Res> delete(
        path: String,
        query: Map<String, String?> = emptyMap(),
        requireAuth: Boolean = true,
    ): ApiResult<Res> = executeJson(
        method = "DELETE",
        path = path,
        query = query,
        bodyJson = null,
        deserializer = json.serializersModule.serializer(),
        requireAuth = requireAuth,
    )

    suspend fun deleteUnit(
        path: String,
        query: Map<String, String?> = emptyMap(),
        requireAuth: Boolean = true,
    ): ApiResult<Unit> = executeUnit("DELETE", path, query, null, null, requireAuth)

    suspend fun deleteNoContent(
        path: String,
        query: Map<String, String?> = emptyMap(),
        requireAuth: Boolean = true,
    ): ApiResult<Unit> = deleteUnit(path, query, requireAuth)

    suspend inline fun <reified T> uploadMultipart(
        path: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<T> = uploadMultipart(
        path = path,
        partName = fieldName,
        fileName = fileName,
        mimeType = mimeType,
        bytes = bytes,
        extraFields = extraFields,
        deserializer = json.serializersModule.serializer(),
        idempotencyKey = idempotencyKey,
    )

    suspend fun downloadToFile(
        url: String,
        targetFile: File,
        allowAnonymousFallback: Boolean = false,
    ): File = withContext(Dispatchers.IO) {
        val bytes = getBytes(url, allowAnonymousFallback = allowAnonymousFallback)
        targetFile.parentFile?.mkdirs()
        val tmp = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(targetFile)) {
            targetFile.writeBytes(bytes)
            tmp.delete()
        }
        targetFile
    }

    suspend fun postUnit(
        path: String,
        bodyJson: String = "{}",
        idempotencyKey: String? = UUID.randomUUID().toString(),
        requireAuth: Boolean = true,
    ): ApiResult<Unit> = executeUnit("POST", path, emptyMap(), bodyJson, idempotencyKey, requireAuth)

    suspend fun <T> executeJson(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        deserializer: DeserializationStrategy<T>,
        idempotencyKey: String? = null,
        ifMatch: String? = null,
        requireAuth: Boolean = false,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val url = buildUrl(path, query)
        val maxAttempts = if (method == "GET") 2 else 1
        var attempt = 0
        var lastError: ApiException? = null

        while (attempt < maxAttempts) {
            attempt++
            val tokenBefore = tokenProvider()
            if (requireAuth && tokenBefore.isNullOrBlank()) {
                return@withContext ApiResult.Failure(
                    createApiException(401, "auth.token_missing", "请先登录 IGNG 账户"),
                )
            }
            val effectiveIdem = idempotencyKey ?: autoIdempotencyKey(method, url)
            val outcome = executeWithSessionHandling(
                allowAnonymousFallback = !requireAuth && method == "GET",
                suppressAuthNotify = false,
            ) { overrideToken, skipAuth ->
                buildJsonRequest(method, url, bodyJson, ifMatch, effectiveIdem, overrideToken, skipAuth)
            }
            when (outcome) {
                is CallOutcome.Success -> {
                    return@withContext runCatching {
                        json.decodeFromString(deserializer, outcome.body)
                    }.fold(
                        onSuccess = { ApiResult.Success(it) },
                        onFailure = { parseErr ->
                            ApiResult.Failure(
                                createApiException(
                                    statusCode = 200,
                                    errorCode = "client.parse_error",
                                    serverMessage = "响应解析失败：${parseErr.message.orEmpty()}",
                                ),
                            )
                        },
                    )
                }
                is CallOutcome.Failure -> {
                    lastError = outcome.exception
                    if (method == "GET" && outcome.exception.isNetworkOrServer && attempt < maxAttempts) {
                        delay(350L * attempt)
                        continue
                    }
                    return@withContext ApiResult.Failure(outcome.exception)
                }
            }
        }
        ApiResult.Failure(lastError ?: createApiException(0, "network.offline", "网络请求失败"))
    }

    suspend fun executeUnit(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        bodyJson: String? = null,
        idempotencyKey: String? = null,
        requireAuth: Boolean = true,
    ): ApiResult<Unit> = withContext(Dispatchers.IO) {
        val url = buildUrl(path, query)
        val tokenBefore = tokenProvider()
        if (requireAuth && tokenBefore.isNullOrBlank()) {
            return@withContext ApiResult.Failure(
                createApiException(401, "auth.token_missing", "请先登录 IGNG 账户"),
            )
        }
        val effectiveIdem = idempotencyKey ?: autoIdempotencyKey(method, url)
        when (
            val outcome = executeWithSessionHandling(
                allowAnonymousFallback = false,
                suppressAuthNotify = false,
            ) { overrideToken, skipAuth ->
                buildJsonRequest(method, url, bodyJson, null, effectiveIdem, overrideToken, skipAuth)
            }
        ) {
            is CallOutcome.Success -> ApiResult.Success(Unit)
            is CallOutcome.Failure -> ApiResult.Failure(outcome.exception)
        }
    }

    suspend fun <T> uploadMultipart(
        path: String,
        partName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
        deserializer: KSerializer<T>,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        val url = buildUrl(path)
        if (tokenProvider().isNullOrBlank()) {
            return@withContext ApiResult.Failure(
                createApiException(401, "auth.token_missing", "请先登录 IGNG 账户"),
            )
        }
        val outcome = executeWithSessionHandling(
            allowAnonymousFallback = false,
            suppressAuthNotify = false,
        ) { overrideToken, skipAuth ->
            buildMultipartRequest(url, partName, fileName, mimeType, bytes, extraFields, idempotencyKey, overrideToken, skipAuth)
        }
        when (outcome) {
            is CallOutcome.Success -> runCatching {
                json.decodeFromString(deserializer, outcome.body)
            }.fold(
                onSuccess = { ApiResult.Success(it) },
                onFailure = { parseErr ->
                    ApiResult.Failure(
                        createApiException(200, "client.parse_error", "响应解析失败：${parseErr.message.orEmpty()}"),
                    )
                },
            )
            is CallOutcome.Failure -> ApiResult.Failure(outcome.exception)
        }
    }

    // -------------------------------------------------------------------------
    // Throwing Raw JSON API (used by MC repositories)
    // -------------------------------------------------------------------------

    suspend fun getRaw(
        url: String,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
        allowAnonymousFallback: Boolean = false,
    ): String = requestRaw(
        method = "GET",
        url = url,
        body = null,
        ifMatch = null,
        idempotencyKey = null,
        tokenOverride = tokenOverride,
        suppressAuthNotify = suppressAuthNotify,
        allowAnonymousFallback = allowAnonymousFallback,
    )

    suspend fun postRaw(
        url: String,
        body: String = "{}",
        idempotencyKey: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = requestRaw(
        method = "POST",
        url = url,
        body = body,
        ifMatch = null,
        idempotencyKey = idempotencyKey ?: autoIdempotencyKey("POST", url),
        tokenOverride = tokenOverride,
        suppressAuthNotify = suppressAuthNotify,
        allowAnonymousFallback = false,
    )

    suspend fun patchRaw(
        url: String,
        body: String = "{}",
        ifMatch: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = requestRaw(
        method = "PATCH",
        url = url,
        body = body,
        ifMatch = ifMatch,
        idempotencyKey = null,
        tokenOverride = tokenOverride,
        suppressAuthNotify = suppressAuthNotify,
        allowAnonymousFallback = false,
    )

    suspend fun deleteRaw(
        url: String,
        body: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = requestRaw(
        method = "DELETE",
        url = url,
        body = body,
        ifMatch = null,
        idempotencyKey = null,
        tokenOverride = tokenOverride,
        suppressAuthNotify = suppressAuthNotify,
        allowAnonymousFallback = false,
    )

    suspend fun uploadMultipartRaw(
        url: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String> = emptyMap(),
        idempotencyKey: String? = null,
        tokenOverride: String? = null,
        suppressAuthNotify: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val key = idempotencyKey ?: autoIdempotencyKey("POST", url)
        val outcome = executeWithSessionHandling(
            allowAnonymousFallback = false,
            suppressAuthNotify = suppressAuthNotify,
            allowRefresh = tokenOverride.isNullOrBlank(),
        ) { overrideToken, skipAuth ->
            buildMultipartRequest(
                url = url,
                partName = fieldName,
                fileName = fileName,
                mimeType = mimeType,
                bytes = bytes,
                extraFields = extraFields,
                idempotencyKey = key,
                tokenOverride = overrideToken ?: tokenOverride,
                skipAuth = skipAuth,
            )
        }
        when (outcome) {
            is CallOutcome.Success -> outcome.body
            is CallOutcome.Failure -> throw outcome.exception
        }
    }

    suspend fun getBytes(
        url: String,
        allowAnonymousFallback: Boolean = false,
    ): ByteArray = withContext(Dispatchers.IO) {
        fun buildReq(override: String?, skipAuth: Boolean): Request = Request.Builder()
            .url(url)
            .apply {
                if (skipAuth) {
                    header(SKIP_AUTH_HEADER, "1")
                } else if (!override.isNullOrBlank()) {
                    header("Authorization", "Bearer $override")
                }
            }
            .get()
            .build()

        fun runBytes(req: Request): Pair<ByteArray?, CallOutcome.Failure?> = try {
            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    null to buildFailure(response, text)
                } else {
                    (response.body?.bytes() ?: ByteArray(0)) to null
                }
            }
        } catch (e: IOException) {
            null to networkFailure(req.url.toString(), e)
        }

        val initialSkipAuth = allowAnonymousFallback && hasInvalidatedSession()
        val (bytes, err) = runBytes(buildReq(null, initialSkipAuth))
        if (bytes != null) return@withContext bytes
        val failure = err!!

        var sessionInvalidated = false
        val isSessionAuthError = failure.exception.isSessionAuthFailure
        val refresher = tokenRefresher
        if (isSessionAuthError && !failure.sentBearerToken.isNullOrBlank() && refresher != null) {
            when (val outcome = refresher.refreshIfNeeded(failure.sentBearerToken)) {
                is TokenRefreshResult.Refreshed -> {
                    val (retryBytes, retryErr) = runBytes(buildReq(outcome.accessToken, false))
                    if (retryBytes != null) return@withContext retryBytes
                    if (retryErr!!.exception.isSessionAuthFailure) {
                        sessionExpiredNotifier?.notify(retryErr.url, retryErr.exception.message)
                    }
                    throw retryErr.exception
                }
                TokenRefreshResult.Invalidated -> sessionInvalidated = true
                TokenRefreshResult.TransientFailure -> throw failure.exception
            }
        }
        if (isSessionAuthError && (!failure.sentBearerToken.isNullOrBlank() || hasInvalidatedSession())) {
            sessionExpiredNotifier?.notify(failure.url, failure.exception.message)
        }
        if (allowAnonymousFallback && isSessionAuthError && !failure.sentBearerToken.isNullOrBlank() &&
            (sessionInvalidated || hasInvalidatedSession())
        ) {
            val (anonBytes, anonErr) = runBytes(buildReq(null, true))
            if (anonBytes != null) return@withContext anonBytes
            throw anonErr!!.exception
        }
        throw failure.exception
    }

    private suspend fun requestRaw(
        method: String,
        url: String,
        body: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        tokenOverride: String?,
        suppressAuthNotify: Boolean,
        allowAnonymousFallback: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val maxAttempts = if (method == "GET") 2 else 1
        var attempt = 0
        var lastError: ApiException? = null
        while (attempt < maxAttempts) {
            attempt++
            val outcome = executeWithSessionHandling(
                allowAnonymousFallback = allowAnonymousFallback && tokenOverride.isNullOrBlank(),
                suppressAuthNotify = suppressAuthNotify,
                allowRefresh = tokenOverride.isNullOrBlank(),
            ) { overrideToken, skipAuth ->
                buildJsonRequest(
                    method = method,
                    url = url,
                    body = body,
                    ifMatch = ifMatch,
                    idempotencyKey = idempotencyKey,
                    tokenOverride = overrideToken ?: tokenOverride,
                    skipAuth = skipAuth,
                )
            }
            when (outcome) {
                is CallOutcome.Success -> return@withContext outcome.body
                is CallOutcome.Failure -> {
                    lastError = outcome.exception
                    if (method == "GET" && outcome.exception.isNetworkOrServer && attempt < maxAttempts) {
                        delay(350L * attempt)
                        continue
                    }
                    throw outcome.exception
                }
            }
        }
        throw lastError ?: createApiException(0, "network.offline", "网络请求失败")
    }

    // -------------------------------------------------------------------------
    // Core single-flight 401 refresh + anonymous fallback engine
    // -------------------------------------------------------------------------

    private suspend fun executeWithSessionHandling(
        allowAnonymousFallback: Boolean,
        suppressAuthNotify: Boolean,
        allowRefresh: Boolean = true,
        requestBuilder: (tokenOverride: String?, skipAuth: Boolean) -> Request,
    ): CallOutcome {
        val initialSkipAuth = allowAnonymousFallback && hasInvalidatedSession()
        val first = executeCall(requestBuilder(null, initialSkipAuth))
        if (first is CallOutcome.Success) return first
        val err = first as CallOutcome.Failure

        var sessionInvalidated = false
        val isSessionAuthError = err.exception.isSessionAuthFailure
        val canRefresh = isSessionAuthError &&
            allowRefresh &&
            !err.sentBearerToken.isNullOrBlank() &&
            tokenRefresher != null

        if (canRefresh) {
            when (val outcome = tokenRefresher!!.refreshIfNeeded(err.sentBearerToken!!)) {
                is TokenRefreshResult.Refreshed -> {
                    val retry = executeCall(requestBuilder(outcome.accessToken, false))
                    if (retry is CallOutcome.Success) return retry
                    val retryErr = retry as CallOutcome.Failure
                    if (retryErr.exception.isSessionAuthFailure && !suppressAuthNotify) {
                        sessionExpiredNotifier?.notify(retryErr.url, retryErr.exception.message)
                    }
                    return retryErr
                }
                TokenRefreshResult.Invalidated -> {
                    sessionInvalidated = true
                }
                TokenRefreshResult.TransientFailure -> {
                    return err
                }
            }
        }

        if (isSessionAuthError && !suppressAuthNotify &&
            (!err.sentBearerToken.isNullOrBlank() || hasInvalidatedSession())
        ) {
            sessionExpiredNotifier?.notify(err.url, err.exception.message)
        }

        if (allowAnonymousFallback &&
            isSessionAuthError &&
            !err.sentBearerToken.isNullOrBlank() &&
            (sessionInvalidated || hasInvalidatedSession())
        ) {
            return executeCall(requestBuilder(null, true))
        }

        return err
    }

    private fun buildJsonRequest(
        method: String,
        url: String,
        body: String?,
        ifMatch: String?,
        idempotencyKey: String?,
        tokenOverride: String?,
        skipAuth: Boolean = false,
    ): Request = Request.Builder()
        .url(url)
        .apply {
            if (!ifMatch.isNullOrBlank()) header("If-Match", ifMatch)
            if (!idempotencyKey.isNullOrBlank()) header("Idempotency-Key", idempotencyKey)
            if (skipAuth) {
                header(SKIP_AUTH_HEADER, "1")
            } else if (!tokenOverride.isNullOrBlank()) {
                header("Authorization", "Bearer $tokenOverride")
            }
        }
        .method(method, body?.toRequestBody(jsonMediaType))
        .build()

    private fun buildMultipartRequest(
        url: String,
        partName: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        extraFields: Map<String, String>,
        idempotencyKey: String?,
        tokenOverride: String?,
        skipAuth: Boolean,
    ): Request {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply {
                extraFields.forEach { (k, v) -> addFormDataPart(k, v) }
                addFormDataPart(partName, fileName, bytes.toRequestBody(mimeType.toMediaType()))
            }
            .build()
        return Request.Builder()
            .url(url)
            .apply {
                if (!idempotencyKey.isNullOrBlank()) header("Idempotency-Key", idempotencyKey)
                if (skipAuth) {
                    header(SKIP_AUTH_HEADER, "1")
                } else if (!tokenOverride.isNullOrBlank()) {
                    header("Authorization", "Bearer $tokenOverride")
                }
            }
            .post(multipart)
            .build()
    }

    private fun autoIdempotencyKey(method: String, url: String): String? =
        if (method == "POST" && url.contains("/api/v1/")) {
            UUID.randomUUID().toString().replace("-", "")
        } else {
            null
        }

    private sealed interface CallOutcome {
        data class Success(val body: String) : CallOutcome
        data class Failure(
            val url: String,
            val sentBearerToken: String?,
            val exception: ApiException,
        ) : CallOutcome
    }

    private fun executeCall(request: Request): CallOutcome = try {
        okHttpClient.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                buildFailure(response, text)
            } else {
                CallOutcome.Success(text)
            }
        }
    } catch (e: IOException) {
        networkFailure(request.url.toString(), e)
    }

    private fun buildFailure(response: okhttp3.Response, text: String): CallOutcome.Failure {
        val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val nestedError = parsed?.get("error")
        val rawMessage = when (nestedError) {
            is JsonObject -> nestedError["message"]?.jsonPrimitive?.contentOrNull
            else -> nestedError?.jsonPrimitive?.contentOrNull
        } ?: parsed?.get("message")?.jsonPrimitive?.contentOrNull ?: ""
        val code = when (nestedError) {
            is JsonObject -> nestedError["code"]?.jsonPrimitive?.contentOrNull
            else -> parsed?.get("code")?.jsonPrimitive?.contentOrNull
        }
        val requestId = parsed?.get("request_id")?.jsonPrimitive?.contentOrNull
            ?: response.header("X-Request-Id")
        val retryAfter = response.header("Retry-After")?.toLongOrNull()
        val bearer = extractBearer(response.request.header("Authorization"))
        return CallOutcome.Failure(
            url = response.request.url.toString(),
            sentBearerToken = bearer,
            exception = createApiException(
                statusCode = response.code,
                errorCode = code,
                serverMessage = rawMessage,
                requestId = requestId,
                retryAfterSeconds = retryAfter,
            ),
        )
    }

    private fun networkFailure(url: String, e: IOException): CallOutcome.Failure {
        val code = if (e is SocketTimeoutException) "network.timeout" else "network.offline"
        val msg = if (e is SocketTimeoutException) "请求超时，请稍后重试" else "网络连接失败，请检查网络后重试"
        return CallOutcome.Failure(
            url = url,
            sentBearerToken = null,
            exception = createApiException(0, code, msg),
        )
    }

    private fun createApiException(
        statusCode: Int,
        errorCode: String?,
        serverMessage: String,
        requestId: String? = null,
        retryAfterSeconds: Long? = null,
    ): ApiException {
        val mapped = errorMessageMapper?.invoke(statusCode, errorCode.orEmpty(), serverMessage)
            ?.takeIf { it.isNotBlank() }
            ?: serverMessage
        return ApiException(
            statusCode = statusCode,
            errorCode = errorCode,
            serverMessage = mapped,
            requestId = requestId,
            retryAfterSeconds = retryAfterSeconds,
        )
    }

    companion object {
        const val SKIP_AUTH_HEADER = "X-IGNG-Skip-Auth"
        private const val MEDIA_ANON_RETRY_HEADER = "X-IGNG-Media-Anon-Retry"

        private fun extractBearer(header: String?): String? =
            header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
                ?.substring(7)?.trim()?.takeIf { it.isNotBlank() }

        private fun responseCount(response: okhttp3.Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }
}
