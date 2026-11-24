package net.igng.core.data

import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.igng.core.network.ApiException
import net.igng.core.network.ApiResult
import net.igng.core.network.IgngJson

/**
 * Cancellation-safe suspend wrapper around [runCatching] that always rethrows [CancellationException].
 */
suspend inline fun <T> runSuspendCatching(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        Result.failure(t)
    }

/**
 * Bridges typed [ApiResult] into Kotlin [Result] for [IgngSwrCache].
 */
fun <T> ApiResult<T>.toKotlinResult(): Result<T> = when (this) {
    is ApiResult.Success -> Result.success(data)
    is ApiResult.Failure -> Result.failure(error)
}

/**
 * Session isolation tier for entries stored in [IgngSwrCache].
 */
enum class IgngCacheScope {
    /** Identical for signed-out and signed-in viewers (e.g., public docs, server catalog, rules). */
    PUBLIC,

    /** Available anonymously, but includes viewer-specific fields when authenticated. */
    SESSION_AWARE,

    /** Strictly private to the currently signed-in account; cleared on switch or logout. */
    USER_ONLY,
}

/**
 * Freshness & Stale-While-Revalidate policy for a cached resource.
 *
 * - Within [freshTtlMs] (and not marked dirty): return memory/disk data with ZERO network traffic.
 * - Beyond [freshTtlMs] (or marked dirty): render existing data immediately on Frame 0 while
 *   silently revalidating in the background.
 */
data class IgngFreshnessPolicy(
    val freshTtlMs: Long,
) {
    companion object {
        /** T1: live metrics, server status, chat streams, inbox, comments. */
        val Realtime = IgngFreshnessPolicy(freshTtlMs = 15_000L)

        /** T2: community feeds, columns, creator dashboard, tickets, player resources. */
        val Standard = IgngFreshnessPolicy(freshTtlMs = 60_000L)

        /** T3: article details, user profiles, passports, stats, medals, permissions. */
        val Detail = IgngFreshnessPolicy(freshTtlMs = 180_000L)

        /** T4: wiki catalog & articles, server directory, rules, hot tags. */
        val Static = IgngFreshnessPolicy(freshTtlMs = 900_000L)
    }
}

/**
 * Immutable snapshot stored in [IgngSwrCache].
 */
data class IgngCacheEntry<T>(
    val data: T,
    val fetchedAtMs: Long,
    val scope: IgngCacheScope,
    val sessionStamp: String,
    val tags: Set<String> = emptySet(),
    val isFromDisk: Boolean = false,
    val dirty: Boolean = false,
) {
    fun isFresh(nowMs: Long, policy: IgngFreshnessPolicy): Boolean {
        if (dirty) return false
        if (fetchedAtMs <= 0L || fetchedAtMs > nowMs) return false
        return (nowMs - fetchedAtMs) < policy.freshTtlMs
    }
}

/**
 * Optional JSON serializer bridge for persisting SWR entries via [IgngLocalJsonCache].
 */
interface IgngSwrDiskCodec<T> {
    val diskKey: String
    fun encode(value: T): String
    fun decode(raw: String): T?
}

fun <T> jsonDiskCodec(
    diskKey: String,
    serializer: KSerializer<T>,
    json: Json = IgngJson.instance,
): IgngSwrDiskCodec<T> = object : IgngSwrDiskCodec<T> {
    override val diskKey: String = diskKey
    override fun encode(value: T): String = json.encodeToString(serializer, value)
    override fun decode(raw: String): T? = runCatching { json.decodeFromString(serializer, raw) }.getOrNull()
}

/**
 * Shared domain-agnostic two-level Stale-While-Revalidate (SWR) cache engine for IGNG Android apps.
 *
 * Key guarantees:
 * - **Frame-0 synchronous [peek]**: zero-flash rendering during tab switching and predictive back.
 * - **Reactive per-key [StateFlow]**: mutations or refreshes in one screen propagate immediately to others.
 * - **Single-flight deduplication**: concurrent callers for the same `(key, session)` share one in-flight call.
 * - **Application-scoped completion**: network fetches finish in [cacheScope] even if a route leaves composition.
 * - **Soft invalidation ([markStale])**: forces silent background refresh on next visit without blanking UI.
 * - **Session isolation**: switching or logging out evicts all [IgngCacheScope.SESSION_AWARE] and
 *   [IgngCacheScope.USER_ONLY] entries and guards against stale-session responses writing back.
 */
class IgngSwrCache(
    private val diskCache: IgngLocalJsonCache? = null,
    private val maxEntries: Int = 200,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val cacheScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private class CacheSlot(
        var scope: IgngCacheScope,
        val flow: MutableStateFlow<IgngCacheEntry<Any>?> = MutableStateFlow(null),
    )

    private val lock = Any()
    private val slots = LinkedHashMap<String, CacheSlot>(64, 0.75f, true)
    private val inFlight = ConcurrentHashMap<String, Deferred<Result<Any>>>()

    private val _sessionStamp = MutableStateFlow(DEFAULT_SESSION_STAMP)
    val sessionStamp: StateFlow<String> = _sessionStamp.asStateFlow()

    @Volatile
    private var rateLimitBackoffUntilMs: Long = 0L

    /**
     * Updates the active session stamp (e.g., `"u:1024:e:2:ACTIVE"` or `"guest:e:0"`).
     * When the stamp changes, all non-[IgngCacheScope.PUBLIC] memory entries are evicted immediately.
     */
    fun updateSessionStamp(newStamp: String) {
        val normalized = newStamp.ifBlank { DEFAULT_SESSION_STAMP }
        synchronized(lock) {
            if (_sessionStamp.value == normalized) return
            _sessionStamp.value = normalized
            val iter = slots.entries.iterator()
            while (iter.hasNext()) {
                val (_, slot) = iter.next()
                val current = slot.flow.value
                val effectiveScope = current?.scope ?: slot.scope
                if (effectiveScope != IgngCacheScope.PUBLIC) {
                    slot.flow.value = null
                    if (slot.flow.subscriptionCount.value == 0) {
                        iter.remove()
                    }
                }
            }
        }
    }

    /**
     * Synchronous O(1) memory lookup safe to call directly on Frame 0 during Compose composition.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> peek(
        key: String,
        scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
    ): IgngCacheEntry<T>? {
        val stamp = _sessionStamp.value
        synchronized(lock) {
            val entry = slots[key]?.flow?.value ?: return null
            if (!isEntryValidForSession(entry, scope, stamp)) return null
            return entry as IgngCacheEntry<T>
        }
    }

    /**
     * Returns a reactive [StateFlow] of the cached entry for [key].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observe(
        key: String,
        scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
    ): StateFlow<IgngCacheEntry<T>?> {
        val slot = synchronized(lock) {
            val existing = slots[key]
            if (existing != null) {
                existing.scope = scope
                existing
            } else {
                CacheSlot(scope = scope).also {
                    slots[key] = it
                    evictOverflowLocked()
                }
            }
        }
        return slot.flow.asStateFlow() as StateFlow<IgngCacheEntry<T>?>
    }

    /**
     * Writes [data] directly into memory (notifying all observers) and optionally persists to disk.
     */
    fun <T : Any> put(
        key: String,
        data: T,
        scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
        tags: Set<String> = emptySet(),
        fetchedAtMs: Long = clockMs(),
        isFromDisk: Boolean = false,
        diskCodec: IgngSwrDiskCodec<T>? = null,
    ) {
        val stamp = _sessionStamp.value
        val entry = IgngCacheEntry<Any>(
            data = data,
            fetchedAtMs = fetchedAtMs,
            scope = scope,
            sessionStamp = if (scope == IgngCacheScope.PUBLIC) PUBLIC_SESSION_STAMP else stamp,
            tags = tags,
            isFromDisk = isFromDisk,
            dirty = false,
        )
        synchronized(lock) {
            val slot = slots.getOrPut(key) { CacheSlot(scope = scope) }
            slot.scope = scope
            slot.flow.value = entry
            evictOverflowLocked()
        }
        if (diskCodec != null && diskCache != null && !isFromDisk && scope != IgngCacheScope.USER_ONLY) {
            val diskKey = scopedDiskKey(diskCodec.diskKey, scope, stamp)
            cacheScope.launch {
                runCatching { diskCache.write(diskKey, diskCodec.encode(data)) }
            }
        }
    }

    /**
     * Hydrates [key] from [diskCache] into memory if no valid in-memory entry exists yet.
     * Preserves the real file modification timestamp so [IgngFreshnessPolicy] stays accurate across restarts.
     */
    suspend fun <T : Any> hydrateFromDisk(
        key: String,
        scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
        tags: Set<String> = emptySet(),
        diskCodec: IgngSwrDiskCodec<T>,
        legacyRawProvider: (suspend () -> String?)? = null,
    ): IgngCacheEntry<T>? {
        peek<T>(key, scope)?.let { return it }
        if (scope == IgngCacheScope.USER_ONLY) return null

        val stampAtStart = _sessionStamp.value
        val diskKey = scopedDiskKey(diskCodec.diskKey, scope, stampAtStart)
        val rawPair = diskCache?.readWithTimestamp(diskKey)
            ?: legacyRawProvider?.invoke()?.let { it to 0L }
            ?: return null

        val decoded = diskCodec.decode(rawPair.first) ?: return null
        if (scope != IgngCacheScope.PUBLIC && _sessionStamp.value != stampAtStart) {
            return null
        }

        val now = clockMs()
        val mtime = rawPair.second.takeIf { it in 1L..now } ?: 0L
        val entry = IgngCacheEntry<Any>(
            data = decoded,
            fetchedAtMs = mtime,
            scope = scope,
            sessionStamp = if (scope == IgngCacheScope.PUBLIC) PUBLIC_SESSION_STAMP else stampAtStart,
            tags = tags,
            isFromDisk = true,
            dirty = false,
        )
        synchronized(lock) {
            val slot = slots.getOrPut(key) { CacheSlot(scope = scope) }
            if (slot.flow.value == null || !isEntryValidForSession(slot.flow.value!!, scope, stampAtStart)) {
                slot.scope = scope
                slot.flow.value = entry
                evictOverflowLocked()
            }
        }
        @Suppress("UNCHECKED_CAST")
        return entry as IgngCacheEntry<T>
    }

    /**
     * Core SWR fetch with TTL gating, optional disk hydration/persistence, and single-flight deduplication.
     *
     * Never wipes existing cached data on failure.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> fetch(
        key: String,
        scope: IgngCacheScope = IgngCacheScope.SESSION_AWARE,
        policy: IgngFreshnessPolicy = IgngFreshnessPolicy.Standard,
        tags: Set<String> = emptySet(),
        force: Boolean = false,
        diskCodec: IgngSwrDiskCodec<T>? = null,
        legacyRawProvider: (suspend () -> String?)? = null,
        fetcher: suspend () -> Result<T>,
    ): Result<T> {
        val stampAtStart = _sessionStamp.value
        if (!force) {
            val current = peek<T>(key, scope)
                ?: if (diskCodec != null) {
                    hydrateFromDisk(key, scope, tags, diskCodec, legacyRawProvider)
                } else {
                    null
                }
            if (current != null && current.isFresh(clockMs(), policy)) {
                return Result.success(current.data)
            }
        }

        val flightKey = if (scope == IgngCacheScope.PUBLIC) {
            "$key|public"
        } else {
            "$key|$stampAtStart"
        }

        val existing = inFlight[flightKey]
        if (existing != null) {
            return existing.await() as Result<T>
        }

        val deferred = cacheScope.async {
            try {
                val res = try {
                    fetcher()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Result.failure(t)
                }

                res.onSuccess { value ->
                    val currentStamp = _sessionStamp.value
                    val stillValid = scope == IgngCacheScope.PUBLIC || currentStamp == stampAtStart
                    if (stillValid) {
                        val now = clockMs()
                        val entry = IgngCacheEntry<Any>(
                            data = value,
                            fetchedAtMs = now,
                            scope = scope,
                            sessionStamp = if (scope == IgngCacheScope.PUBLIC) PUBLIC_SESSION_STAMP else stampAtStart,
                            tags = tags,
                            isFromDisk = false,
                            dirty = false,
                        )
                        synchronized(lock) {
                            val slot = slots.getOrPut(key) { CacheSlot(scope = scope) }
                            slot.scope = scope
                            slot.flow.value = entry
                            evictOverflowLocked()
                        }
                        if (diskCodec != null && diskCache != null && scope != IgngCacheScope.USER_ONLY) {
                            val diskKey = scopedDiskKey(diskCodec.diskKey, scope, stampAtStart)
                            runCatching { diskCache.write(diskKey, diskCodec.encode(value)) }
                        }
                    }
                }.onFailure { err ->
                    if ((err as? ApiException)?.isRateLimited == true) {
                        recordRateLimited()
                    }
                }
                res as Result<Any>
            } finally {
                inFlight.remove(flightKey)
            }
        }

        val raced = inFlight.putIfAbsent(flightKey, deferred)
        if (raced != null) {
            deferred.cancel()
            return raced.await() as Result<T>
        }
        return deferred.await() as Result<T>
    }

    /**
     * Softly marks all cached entries carrying any of [tags] as dirty WITHOUT deleting their data.
     * Active/returning screens render the previous snapshot on Frame 0 and silently revalidate on ON_RESUME.
     */
    fun markStale(vararg tags: String) {
        if (tags.isEmpty()) return
        val tagSet = tags.toSet()
        synchronized(lock) {
            slots.values.forEach { slot ->
                val cur = slot.flow.value ?: return@forEach
                if (!cur.dirty && cur.tags.any { it in tagSet }) {
                    slot.flow.value = cur.copy(dirty = true)
                }
            }
        }
    }

    /**
     * Softly marks a single [key] as dirty without removing its cached value.
     */
    fun markKeyStale(key: String) {
        synchronized(lock) {
            val slot = slots[key] ?: return
            val cur = slot.flow.value ?: return
            if (!cur.dirty) {
                slot.flow.value = cur.copy(dirty = true)
            }
        }
    }

    /**
     * Optimistically transforms a cached value in place for instant cross-screen UI propagation.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> patch(key: String, transform: (T) -> T) {
        synchronized(lock) {
            val slot = slots[key] ?: return
            val cur = slot.flow.value ?: return
            val updated = runCatching { transform(cur.data as T) }.getOrNull() ?: return
            if (updated !== cur.data && updated != cur.data) {
                slot.flow.value = cur.copy(data = updated)
            }
        }
    }

    /**
     * Optimistically transforms every cached entry carrying [tag] whose payload matches [clazz].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> patchByTag(tag: String, clazz: Class<T>, transform: (T) -> T) {
        synchronized(lock) {
            slots.values.forEach { slot ->
                val cur = slot.flow.value ?: return@forEach
                if (tag in cur.tags && clazz.isInstance(cur.data)) {
                    val updated = runCatching { transform(cur.data as T) }.getOrNull() ?: return@forEach
                    if (updated !== cur.data && updated != cur.data) {
                        slot.flow.value = cur.copy(data = updated)
                    }
                }
            }
        }
    }

    /**
     * Removes a single key from memory immediately (use sparingly; prefer [markKeyStale] for smooth UX).
     */
    fun evict(key: String) {
        synchronized(lock) {
            val slot = slots[key] ?: return
            slot.flow.value = null
            if (slot.flow.subscriptionCount.value == 0) {
                slots.remove(key)
            }
        }
    }

    /**
     * Releases unsubscribed cold entries under OS memory pressure (`Application.onTrimMemory`).
     */
    fun trimMemory(aggressive: Boolean = false) {
        synchronized(lock) {
            val targetSize = if (aggressive) 0 else (maxEntries / 2).coerceAtLeast(16)
            val iter = slots.entries.iterator()
            while (iter.hasNext() && slots.size > targetSize) {
                val (_, slot) = iter.next()
                if (slot.flow.subscriptionCount.value == 0) {
                    slot.flow.value = null
                    iter.remove()
                }
            }
        }
    }

    fun recordRateLimited(backoffMs: Long = 60_000L) {
        rateLimitBackoffUntilMs = maxOf(rateLimitBackoffUntilMs, clockMs() + backoffMs)
    }

    fun isPrefetchAllowed(): Boolean = clockMs() >= rateLimitBackoffUntilMs

    suspend fun clearAll() {
        synchronized(lock) {
            slots.values.forEach { it.flow.value = null }
            val iter = slots.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().value.flow.subscriptionCount.value == 0) {
                    iter.remove()
                }
            }
        }
        diskCache?.clearAll()
    }

    suspend fun clearSessionDiskCache() {
        diskCache?.deleteByPrefix("s_")
    }

    suspend fun diskSizeBytes(): Long = diskCache?.sizeBytes() ?: 0L

    private fun isEntryValidForSession(
        entry: IgngCacheEntry<*>,
        requestedScope: IgngCacheScope,
        currentStamp: String,
    ): Boolean {
        if (requestedScope == IgngCacheScope.PUBLIC && entry.scope == IgngCacheScope.PUBLIC) {
            return true
        }
        return entry.sessionStamp == currentStamp
    }

    private fun scopedDiskKey(baseKey: String, scope: IgngCacheScope, stamp: String): String =
        if (scope == IgngCacheScope.PUBLIC) {
            "p_$baseKey"
        } else {
            val userBucket = stamp.substringBefore(":e:").ifBlank { "guest" }
            "s_${userBucket}_$baseKey"
        }

    private fun evictOverflowLocked() {
        if (slots.size <= maxEntries) return
        val iter = slots.entries.iterator()
        while (iter.hasNext() && slots.size > maxEntries) {
            val (_, slot) = iter.next()
            if (slot.flow.subscriptionCount.value == 0) {
                slot.flow.value = null
                iter.remove()
            }
        }
    }

    companion object {
        private const val DEFAULT_SESSION_STAMP = "guest:e:0"
        private const val PUBLIC_SESSION_STAMP = "public"

        fun buildSessionStamp(
            userId: Long?,
            sessionEpoch: Int,
            authStatusName: String,
        ): String = if (userId == null) {
            "guest:e:$sessionEpoch:$authStatusName"
        } else {
            "u$userId:e:$sessionEpoch:$authStatusName"
        }
    }
}
