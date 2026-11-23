package net.igng.core.auth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.network.IgngJson

@Serializable
internal data class PublicAccountDirectoryEntry(
    val userId: Long,
    val username: String,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val isActive: Boolean = false,
)

/**
 * Registry of official first-party IGNG Android packages allowed to query each other's
 * public (token-free) account identity directory for seamless Microsoft-style cross-app SSO.
 *
 * Loaded from Android XML resources (`R.array.igng_family_app_entries`) so `:core:auth` Kotlin
 * sources stay strictly domain-agnostic and available even before `Application.onCreate()`.
 */
object IgngFamilyApps {
    fun loadKnownPackages(context: Context): Map<String, String> = runCatching {
        context.applicationContext.resources
            .getStringArray(R.array.igng_family_app_entries)
            .mapNotNull { entry ->
                val parts = entry.split('|', limit = 2)
                val pkg = parts.getOrNull(0)?.trim().orEmpty()
                val label = parts.getOrNull(1)?.trim().orEmpty()
                if (pkg.isEmpty()) null else pkg to label.ifEmpty { "IGNG 应用" }
            }
            .toMap()
    }.getOrDefault(emptyMap())

    fun appNameFor(context: Context, packageName: String): String =
        loadKnownPackages(context)[packageName] ?: "IGNG 应用"

    fun authorityFor(packageName: String): String = "$packageName.igng.family.sso"

    fun accountsUriFor(packageName: String): Uri =
        Uri.parse("content://${authorityFor(packageName)}/accounts")
}

/**
 * Token-free public account index stored locally in private SharedPreferences so
 * [IgngFamilySsoProvider] can answer sibling IGNG queries instantaneously without touching
 * AndroidKeyStore or exposing any OAuth secrets.
 */
internal object IgngFamilyDirectoryStore {
    private const val PREFS_NAME = "igng_family_sso_directory"
    private const val KEY_ENTRIES = "public_accounts_v1"

    fun write(context: Context, accounts: List<SavedAccount>, activeUserId: Long?) {
        val entries = accounts.map { acc ->
            PublicAccountDirectoryEntry(
                userId = acc.userId,
                username = acc.username,
                nickname = acc.nickname,
                avatarUrl = acc.avatarUrl,
                isActive = acc.userId == activeUserId,
            )
        }
        val raw = IgngJson.instance.encodeToString(
            ListSerializer(PublicAccountDirectoryEntry.serializer()),
            entries,
        )
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ENTRIES, raw)
            .apply()
    }

    fun read(context: Context): List<PublicAccountDirectoryEntry> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ENTRIES, null)
            ?: return emptyList()
        return runCatching {
            IgngJson.instance.decodeFromString(
                ListSerializer(PublicAccountDirectoryEntry.serializer()),
                raw,
            )
        }.getOrDefault(emptyList())
    }
}

/**
 * Exported read-only ContentProvider that shares ONLY non-secret public IGNG account hints
 * with other first-party IGNG Android apps on the same device.
 *
 * Security invariants:
 * - Strictly allowlists [IgngFamilyApps.allowedPackages] via [getCallingPackage].
 * - Never exposes `access_token`, `refresh_token`, or `session_id`; each app mints its own
 *   scoped PKCE token pair via the shared `sso.igng.net` browser session cookie.
 * - Insert / update / delete are permanently forbidden.
 */
class IgngFamilySsoProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: return null
        val caller = callingPackage ?: return null
        val known = IgngFamilyApps.loadKnownPackages(ctx)
        if (caller !in known.keys) {
            return null
        }
        val entries = IgngFamilyDirectoryStore.read(ctx)
        val cursor = MatrixCursor(COLUMNS, entries.size)
        val selfPkg = ctx.packageName
        val selfName = known[selfPkg] ?: "IGNG 应用"
        entries.forEach { entry ->
            cursor.addRow(
                arrayOf<Any?>(
                    entry.userId,
                    entry.username,
                    entry.nickname,
                    entry.avatarUrl ?: "",
                    if (entry.isActive) 1 else 0,
                    selfPkg,
                    selfName,
                ),
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.net.igng.family.accounts"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val COL_USER_ID = "user_id"
        const val COL_USERNAME = "username"
        const val COL_NICKNAME = "nickname"
        const val COL_AVATAR_URL = "avatar_url"
        const val COL_IS_ACTIVE = "is_active"
        const val COL_SOURCE_PACKAGE = "source_package"
        const val COL_SOURCE_APP_NAME = "source_app_name"

        val COLUMNS = arrayOf(
            COL_USER_ID,
            COL_USERNAME,
            COL_NICKNAME,
            COL_AVATAR_URL,
            COL_IS_ACTIVE,
            COL_SOURCE_PACKAGE,
            COL_SOURCE_APP_NAME,
        )
    }
}

/**
 * Discovers IGNG accounts currently signed into sibling IGNG apps installed on the same device.
 */
object IgngFamilyAccountDiscovery {
    suspend fun discoverSiblingAccounts(context: Context): List<FamilySharedAccountHint> =
        withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val selfPackage = appCtx.packageName
            val knownPackages = IgngFamilyApps.loadKnownPackages(appCtx)
            val discovered = mutableListOf<FamilySharedAccountHint>()

            for ((pkg, fallbackAppName) in knownPackages) {
                if (pkg == selfPackage) continue
                runCatching {
                    val uri = IgngFamilyApps.accountsUriFor(pkg)
                    appCtx.contentResolver.query(uri, IgngFamilySsoProvider.COLUMNS, null, null, null)?.use { c ->
                        val idxUid = c.getColumnIndex(IgngFamilySsoProvider.COL_USER_ID)
                        val idxUser = c.getColumnIndex(IgngFamilySsoProvider.COL_USERNAME)
                        val idxNick = c.getColumnIndex(IgngFamilySsoProvider.COL_NICKNAME)
                        val idxAvatar = c.getColumnIndex(IgngFamilySsoProvider.COL_AVATAR_URL)
                        val idxActive = c.getColumnIndex(IgngFamilySsoProvider.COL_IS_ACTIVE)
                        val idxSrcPkg = c.getColumnIndex(IgngFamilySsoProvider.COL_SOURCE_PACKAGE)
                        val idxSrcName = c.getColumnIndex(IgngFamilySsoProvider.COL_SOURCE_APP_NAME)
                        while (c.moveToNext()) {
                            val uid = if (idxUid >= 0) c.getLong(idxUid) else 0L
                            val username = if (idxUser >= 0) c.getString(idxUser).orEmpty() else ""
                            if (uid <= 0L || username.isBlank()) continue
                            discovered += FamilySharedAccountHint(
                                userId = uid,
                                username = username,
                                nickname = if (idxNick >= 0) c.getString(idxNick).orEmpty() else "",
                                avatarUrl = if (idxAvatar >= 0) c.getString(idxAvatar)?.ifBlank { null } else null,
                                sourcePackage = if (idxSrcPkg >= 0) c.getString(idxSrcPkg).orEmpty().ifBlank { pkg } else pkg,
                                sourceAppName = if (idxSrcName >= 0) {
                                    c.getString(idxSrcName).orEmpty().ifBlank { fallbackAppName }
                                } else {
                                    fallbackAppName
                                },
                                isActiveInSource = idxActive >= 0 && c.getInt(idxActive) != 0,
                            )
                        }
                    }
                }
            }

            // Prefer active account first and deduplicate by userId across multiple sibling apps
            discovered
                .sortedByDescending { it.isActiveInSource }
                .distinctBy { it.userId }
        }
}
