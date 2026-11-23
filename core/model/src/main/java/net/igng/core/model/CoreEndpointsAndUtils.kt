package net.igng.core.model

import java.net.URI
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
 * Unified Client API v1 opaque prefixed ID helper (`usr_42`, `post_101`, `tkt_8`, `med_9`).
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

    fun post(numericId: Int): String = "post_$numericId"
    fun user(numericId: Int): String = "usr_$numericId"
    fun user(numericId: Long): String = "usr_$numericId"
    fun column(numericId: Int): String = "col_$numericId"
    fun series(numericId: Int): String = "ser_$numericId"
    fun comment(numericId: Int): String = "cmt_$numericId"
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
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .withZone(ZoneId.systemDefault())
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")
        .withZone(ZoneId.systemDefault())
    private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    private val shortDateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

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

    fun relativeTime(isoUtc: String?, now: Instant = Instant.now()): String {
        if (isoUtc.isNullOrBlank()) return ""
        val instant = parseInstant(isoUtc) ?: return isoUtc
        val seconds = Duration.between(instant, now).seconds
        return when {
            seconds < 60 -> "刚刚"
            seconds < 3600 -> "${seconds / 60} 分钟前"
            seconds < 86_400 -> "${seconds / 3600} 小时前"
            seconds < 86_400 * 7 -> "${seconds / 86_400} 天前"
            seconds < 86_400 * 300 -> monthDayFormatter.format(instant)
            else -> dateOnlyFormatter.format(instant)
        }
    }

    fun fullDateTime(isoUtc: String?): String {
        if (isoUtc.isNullOrBlank()) return ""
        val instant = parseInstant(isoUtc) ?: return isoUtc
        return fullDateTimeFormatter.format(instant)
    }

    fun shortDateTime(isoUtc: String?): String {
        if (isoUtc.isNullOrBlank()) return ""
        val instant = parseInstant(isoUtc) ?: return isoUtc
        return shortDateTimeFormatter.format(instant)
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
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
