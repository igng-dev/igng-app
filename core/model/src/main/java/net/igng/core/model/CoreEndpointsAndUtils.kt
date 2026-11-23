package net.igng.core.model

import java.net.URI
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Unified Client API v1 opaque prefixed ID helper (`usr_42`, `med_9`, or domain prefixes via [format]).
 */
object V1Id {
    fun format(prefix: String, numericId: Int): String = "${prefix}_$numericId"
    fun format(prefix: String, numericId: Long): String = "${prefix}_$numericId"

    fun parseNumeric(raw: String?): Int? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        val tail = value.substringAfter('_', value)
        return tail.toIntOrNull()
    }

    fun numericOf(raw: String?): Int? = parseNumeric(raw)

    fun user(numericId: Int): String = "usr_$numericId"
    fun user(numericId: Long): String = "usr_$numericId"
    fun media(numericId: Int): String = "med_$numericId"
}

/**
 * Common endpoint contract consumed by `:core:network` and `:core:auth`.
 */
interface IgngCoreEndpoints {
    val apiRoot: String
    val ssoRoot: String

    val apiV1Base: String get() = "${apiRoot.trimEnd('/')}/api/v1"
    val oauthAuthorizeUrl: String get() = "${ssoRoot.trimEnd('/')}/oauth/authorize"
    val oauthTokenUrl: String get() = "${ssoRoot.trimEnd('/')}/api/oauth/token"

    fun trustedAuthHosts(): Set<String> = setOfNotNull(
        extractHost(apiRoot),
        extractHost(ssoRoot),
    )

    fun isTrustedAuthHost(url: String): Boolean {
        val host = extractHost(url) ?: return false
        return host in trustedAuthHosts()
    }

    fun apiHost(): String = extractHost(apiRoot).orEmpty()

    fun mediaContentUrl(mediaId: String): String =
        "${apiRoot.trimEnd('/')}/api/v1/media/${mediaId.trim()}/content"

    /**
     * Resolves relative `/api/v1/...` media paths against [apiRoot] and upgrades known cleartext
     * HTTP avatar CDN URLs (e.g. `qlogo.cn`) to HTTPS so Android 9+ cleartext policy never blocks them.
     */
    fun resolveApiMediaUrl(pathOrUrl: String?): String? {
        val trimmed = pathOrUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://", ignoreCase = true)) {
            val host = extractHost(trimmed).orEmpty()
            if (host == "qlogo.cn" || host.endsWith(".qlogo.cn")) {
                return "https://" + trimmed.substring(7)
            }
            return trimmed
        }
        if (trimmed.startsWith("https://", ignoreCase = true)) return trimmed
        return "${apiRoot.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    companion object {
        fun extractHost(url: String): String? = runCatching {
            URI(url).host?.lowercase(Locale.ROOT)
        }.getOrNull()
    }
}

/**
 * Domain-agnostic formatters shared by all IGNG Android apps.
 */
object IgngCoreFormatters {
    private val localZone: ZoneId get() = ZoneId.systemDefault()
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val shortDateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
    private val timeOnlyFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun formatNumber(value: Long?): String =
        if (value == null) "—" else NumberFormat.getIntegerInstance(Locale.US).format(value)

    fun formatNumber(value: Int?): String = formatNumber(value?.toLong())

    fun formatDecimal(value: Double?, digits: Int = 1, suffix: String = ""): String {
        if (value == null || !value.isFinite()) return "—"
        return String.format(Locale.US, "%.${digits}f%s", value, suffix)
    }

    fun compactCount(value: Int): String = when {
        value >= 100_000 -> "${value / 10_000}万"
        value >= 10_000 -> String.format(Locale.US, "%.1f万", value / 10_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> value.toString()
    }

    fun compactNumber(value: Long): String = when {
        value >= 100_000_000L -> String.format(Locale.US, "%.1f亿", value / 100_000_000.0)
        value >= 100_000L -> "${value / 10_000L}万"
        value >= 10_000L -> String.format(Locale.US, "%.1f万", value / 10_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> value.toString()
    }

    fun relativeTime(isoUtc: String?, now: Instant = Instant.now(), emptyFallback: String = ""): String {
        if (isoUtc.isNullOrBlank()) return emptyFallback
        val instant = parseInstant(isoUtc) ?: return isoUtc
        val seconds = Duration.between(instant, now).seconds
        return when {
            seconds < 45 -> "刚刚"
            seconds < 3600 -> "${seconds / 60} 分钟前"
            seconds < 86_400 -> "${seconds / 3600} 小时前"
            seconds < 86_400 * 7 -> "${seconds / 86_400} 天前"
            seconds < 86_400 * 300 -> monthDayFormatter.withZone(localZone).format(instant)
            else -> dateOnlyFormatter.withZone(localZone).format(instant)
        }
    }

    fun fullDateTime(isoUtc: String?, emptyFallback: String = ""): String {
        if (isoUtc.isNullOrBlank()) return emptyFallback
        val instant = parseInstant(isoUtc) ?: return isoUtc.take(16).replace('T', ' ')
        return fullDateTimeFormatter.withZone(localZone).format(instant)
    }

    fun shortDateTime(isoUtc: String?, emptyFallback: String = ""): String {
        if (isoUtc.isNullOrBlank()) return emptyFallback
        val instant = parseInstant(isoUtc) ?: return isoUtc.take(16).replace('T', ' ')
        return shortDateTimeFormatter.withZone(localZone).format(instant)
    }

    fun formatEpochMillis(millis: Long?): String {
        if (millis == null || millis <= 0L) return "—"
        return runCatching {
            fullDateTimeFormatter.withZone(localZone).format(Instant.ofEpochMilli(millis))
        }.getOrDefault("—")
    }

    fun formatTimeOnly(isoUtc: String?): String {
        if (isoUtc.isNullOrBlank()) return "—"
        val instant = parseInstant(isoUtc) ?: return isoUtc
        return timeOnlyFormatter.withZone(localZone).format(instant)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun formatBytesDetailed(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (index == 0) "$bytes B" else String.format(Locale.US, "%.2f %s", value, units[index])
    }

    fun parseInstant(raw: String?): Instant? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { Instant.parse(text) }.getOrElse {
            text.toLongOrNull()?.let { num ->
                if (num > 1_000_000_000_000L) Instant.ofEpochMilli(num) else Instant.ofEpochSecond(num)
            }
        }
    }
}

fun JsonElement?.asBooleanFlag(): Boolean {
    val prim = this as? JsonPrimitive ?: return false
    prim.booleanOrNull?.let { return it }
    prim.intOrNull?.let { return it != 0 }
    prim.doubleOrNull?.let { return it != 0.0 }
    return when (prim.content.trim().lowercase(Locale.ROOT)) {
        "true", "1", "yes", "on" -> true
        else -> false
    }
}
