package net.igng.bbs.core.model

import java.time.Instant
import kotlinx.serialization.Serializable
import net.igng.core.model.IgngCoreFormatters
import net.igng.core.model.MotionIntensity
import net.igng.core.model.ReaderTypographyPrefs
import net.igng.core.model.ThemeMode

@Serializable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val motionIntensity: MotionIntensity = MotionIntensity.FULL,
    val hapticsEnabled: Boolean = true,
    val collapseSeriesInFeed: Boolean = true,
    val backgroundNotificationsEnabled: Boolean = true,
    val reader: ReaderTypographyPrefs = ReaderTypographyPrefs(),
)

object BbsFormatters {
    private const val ESSAY_CATEGORY_SLUG = "essay"
    private const val ESSAY_DISPLAY_LABEL = "Ink随笔"

    fun compactCount(value: Int): String = IgngCoreFormatters.compactCount(value)
    fun relativeTime(isoUtc: String?, now: Instant = Instant.now()): String =
        IgngCoreFormatters.relativeTime(isoUtc, now)
    fun fullDateTime(isoUtc: String?): String = IgngCoreFormatters.fullDateTime(isoUtc)
    fun formatBytes(bytes: Long): String = IgngCoreFormatters.formatBytes(bytes)
    fun readingMinutesLabel(minutes: Int): String = "约 ${minutes.coerceAtLeast(1)} 分钟阅读"

    fun isEssayCategory(category: CategoryDto?): Boolean {
        if (category == null) return false
        if (category.slug.equals(ESSAY_CATEGORY_SLUG, ignoreCase = true)) return true
        return isEssayLabel(category.name)
    }

    fun isEssayLabel(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val normalized = raw.trim().removePrefix("#").replace(Regex("\\s+"), "").lowercase()
        return normalized == "ink随笔"
    }

    fun categoryDisplayName(category: CategoryDto): String =
        if (isEssayCategory(category)) ESSAY_DISPLAY_LABEL else category.name

    fun categoryDisplayName(slug: String?, name: String?): String {
        if (slug.equals(ESSAY_CATEGORY_SLUG, ignoreCase = true) || isEssayLabel(name)) {
            return ESSAY_DISPLAY_LABEL
        }
        return name?.trim().orEmpty().ifBlank { slug.orEmpty() }
    }

    fun tagDisplayLabel(tag: String): String {
        val clean = tag.trim().removePrefix("#")
        return if (isEssayLabel(clean)) ESSAY_DISPLAY_LABEL else "#$clean"
    }

    fun visiblePostTags(tags: List<String>, category: CategoryDto?): List<String> =
        if (isEssayCategory(category)) tags.filterNot(::isEssayLabel) else tags

    fun postStatusLabel(status: Int, banStatus: Int, moderationStatus: String? = null): String = when {
        status == 3 -> "已删除"
        status == 1 -> "草稿"
        banStatus == 2 -> "已禁止查看"
        banStatus == 4 -> "受限制"
        moderationStatus == "pending" -> "审核中"
        moderationStatus == "rejected" -> "审核未通过"
        banStatus == 1 -> "仅作者可见"
        else -> "已发布"
    }

    fun visibilityLabel(visibility: String): String = when (visibility.lowercase()) {
        "public" -> "公开"
        "followers" -> "仅粉丝可见"
        "following" -> "仅关注可见"
        "private" -> "私密"
        else -> visibility
    }
}
