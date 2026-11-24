package net.igng.core.updater

import android.content.Context

interface UpdatePrefsStore {
    fun getIncludePrerelease(): Boolean
    fun setIncludePrerelease(enabled: Boolean)

    fun getLastCheckedAtMs(): Long?
    fun setLastCheckedAtMs(epochMs: Long)

    fun getLastSilentCheckAtMs(): Long?
    fun setLastSilentCheckAtMs(epochMs: Long)

    fun getChannelEtag(channel: String): String?
    fun getCachedReleaseJson(channel: String): String?
    fun saveChannelCache(channel: String, etag: String?, releaseJson: String)
    fun clearChannelCache(channel: String)
}

class SharedPreferencesUpdatePrefsStore(
    context: Context,
) : UpdatePrefsStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getIncludePrerelease(): Boolean = prefs.getBoolean(KEY_INCLUDE_PRERELEASE, false)

    override fun setIncludePrerelease(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INCLUDE_PRERELEASE, enabled).apply()
    }

    override fun getLastCheckedAtMs(): Long? =
        prefs.getLong(KEY_LAST_CHECKED_AT, -1L).takeIf { it > 0L }

    override fun setLastCheckedAtMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_CHECKED_AT, epochMs).apply()
    }

    override fun getLastSilentCheckAtMs(): Long? =
        prefs.getLong(KEY_LAST_SILENT_CHECK_AT, -1L).takeIf { it > 0L }

    override fun setLastSilentCheckAtMs(epochMs: Long) {
        prefs.edit().putLong(KEY_LAST_SILENT_CHECK_AT, epochMs).apply()
    }

    override fun getChannelEtag(channel: String): String? =
        prefs.getString(etagKey(channel), null)?.takeIf { it.isNotBlank() }

    override fun getCachedReleaseJson(channel: String): String? =
        prefs.getString(cachedReleaseKey(channel), null)?.takeIf { it.isNotBlank() }

    override fun saveChannelCache(channel: String, etag: String?, releaseJson: String) {
        prefs.edit().apply {
            if (etag.isNullOrBlank()) {
                remove(etagKey(channel))
            } else {
                putString(etagKey(channel), etag)
            }
            putString(cachedReleaseKey(channel), releaseJson)
        }.apply()
    }

    override fun clearChannelCache(channel: String) {
        prefs.edit()
            .remove(etagKey(channel))
            .remove(cachedReleaseKey(channel))
            .apply()
    }

    private fun etagKey(channel: String): String = "etag_$channel"
    private fun cachedReleaseKey(channel: String): String = "cached_release_$channel"

    private companion object {
        const val PREFS_NAME = "igng_app_updater_prefs_v1"
        const val KEY_INCLUDE_PRERELEASE = "include_prerelease"
        const val KEY_LAST_CHECKED_AT = "last_checked_at_ms"
        const val KEY_LAST_SILENT_CHECK_AT = "last_silent_check_at_ms"
    }
}
