package net.igng.core.auth

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
 * Metadata describing an official IGNG first-party Android package loaded from XML resources.
 */
data class FamilyAppSpec(
    val packageName: String,
    val displayName: String,
    val clientId: String = "",
    val redirectUri: String = "",
    val releaseCertSha256: String = "",
)

/**
 * Result returned by the sibling app's in-process handoff delegate after requesting a
 * short-lived PKCE S256 handoff code from `POST /api/v1/auth/family-handoff`.
 */
sealed interface FamilyHandoffIssueResult {
    data class Issued(
        val code: String,
        val expiresAt: String,
        val userId: Long,
        val username: String,
    ) : FamilyHandoffIssueResult

    data class Failed(val reason: String) : FamilyHandoffIssueResult
}

fun interface IgngFamilyHandoffDelegate {
    suspend fun issueHandoffCode(
        targetClientId: String,
        redirectUri: String,
        codeChallenge: String,
        expectedUserId: Long?,
    ): FamilyHandoffIssueResult
}

/**
 * Process-local bridge connecting [IgngSessionManager] (initialized in `Application.onCreate`)
 * with [IgngFamilySsoProvider] so incoming verified sibling IPC calls can mint 60-second
 * PKCE S256 handoff codes using this app's live OAuth 2.1 transport (with single-flight 401 refresh).
 */
object IgngFamilyHandoffHost {
    @Volatile
    private var delegate: IgngFamilyHandoffDelegate? = null

    fun register(delegate: IgngFamilyHandoffDelegate) {
        this.delegate = delegate
    }

    internal fun current(): IgngFamilyHandoffDelegate? = delegate
}

/**
 * Short-lived authorization code grant received from a verified sibling IGNG app.
 */
data class FamilyHandoffGrant(
    val code: String,
    val expiresAt: String,
    val userId: Long,
    val username: String,
)

/**
 * Registry of official first-party IGNG Android packages allowed to discover each other's
 * public (token-free) account identity directory and perform certificate-pinned PKCE handoff.
 *
 * Loaded from Android XML resources (`R.array.igng_family_app_entries`) so `:core:auth` Kotlin
 * sources stay strictly domain-agnostic and available even before `Application.onCreate()`.
 */
object IgngFamilyApps {
    internal fun parseEntry(raw: String): FamilyAppSpec? {
        val parts = raw.split('|')
        val pkg = parts.getOrNull(0)?.trim().orEmpty()
        if (pkg.isEmpty()) return null
        val label = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "IGNG 应用" }
        val clientId = parts.getOrNull(2)?.trim().orEmpty()
        val redirectUri = parts.getOrNull(3)?.trim().orEmpty()
        val certSha256 = normalizeFingerprint(parts.getOrNull(4).orEmpty())
        return FamilyAppSpec(
            packageName = pkg,
            displayName = label,
            clientId = clientId,
            redirectUri = redirectUri,
            releaseCertSha256 = certSha256,
        )
    }

    internal fun normalizeFingerprint(raw: String): String =
        raw.replace(":", "").replace(" ", "").trim().lowercase()

    fun loadSpecs(context: Context): Map<String, FamilyAppSpec> = runCatching {
        context.applicationContext.resources
            .getStringArray(R.array.igng_family_app_entries)
            .mapNotNull(::parseEntry)
            .associateBy { it.packageName }
    }.getOrDefault(emptyMap())

    fun loadKnownPackages(context: Context): Map<String, String> =
        loadSpecs(context).mapValues { it.value.displayName }

    fun appNameFor(context: Context, packageName: String): String =
        loadSpecs(context)[packageName]?.displayName ?: "IGNG 应用"

    fun authorityFor(packageName: String): String = "$packageName.igng.family.sso"

    fun accountsUriFor(packageName: String): Uri =
        Uri.parse("content://${authorityFor(packageName)}/accounts")
}

/**
 * Bidirectional APK signing-certificate verifier for IGNG family cross-app IPC.
 *
 * Trust rule:
 * - Production builds: sibling package MUST match its pinned `releaseCertSha256` from
 *   `igng_family_app_entries` (each official IGNG app maintains its own independent release keystore).
 * - Local debug builds: when BOTH apps are marked `FLAG_DEBUGGABLE` and signed with the exact
 *   same debug certificate, cross-app IPC is also permitted for local testing.
 */
object IgngFamilySignatureVerifier {
    internal fun isCertDigestTrusted(
        expectedReleaseSha256: String,
        actualCerts: Set<String>,
        bothDebuggable: Boolean,
        selfCerts: Set<String>,
    ): Boolean {
        if (actualCerts.isEmpty()) return false
        val normalizedExpected = IgngFamilyApps.normalizeFingerprint(expectedReleaseSha256)
        if (normalizedExpected.isNotEmpty() && normalizedExpected in actualCerts) {
            return true
        }
        if (bothDebuggable && selfCerts.isNotEmpty() && actualCerts.any { it in selfCerts }) {
            return true
        }
        return false
    }

    fun isTrustedSibling(context: Context, siblingPackage: String): Boolean {
        val appCtx = context.applicationContext
        val selfPkg = appCtx.packageName
        if (siblingPackage.isBlank() || siblingPackage == selfPkg) return false
        val spec = IgngFamilyApps.loadSpecs(appCtx)[siblingPackage] ?: return false
        val actualCerts = signingCertSha256s(appCtx, siblingPackage)
        if (actualCerts.isEmpty()) return false
        val bothDebuggable = isPackageDebuggable(appCtx, selfPkg) && isPackageDebuggable(appCtx, siblingPackage)
        val selfCerts = if (bothDebuggable) signingCertSha256s(appCtx, selfPkg) else emptySet()
        return isCertDigestTrusted(
            expectedReleaseSha256 = spec.releaseCertSha256,
            actualCerts = actualCerts,
            bothDebuggable = bothDebuggable,
            selfCerts = selfCerts,
        )
    }

    private fun isPackageDebuggable(context: Context, packageName: String): Boolean = runCatching {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun signingCertSha256s(context: Context, packageName: String): Set<String> = runCatching {
        val pm = context.packageManager
        val rawCerts: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = pkgInfo.signingInfo ?: return@runCatching emptySet()
            val signers = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signers?.map { it.toByteArray() }.orEmpty()
        } else {
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            pkgInfo.signatures?.map { it.toByteArray() }.orEmpty()
        }
        val md = MessageDigest.getInstance("SHA-256")
        rawCerts.map { bytes ->
            md.reset()
            md.digest(bytes).joinToString("") { "%02x".format(it) }
        }.toSet()
    }.getOrDefault(emptySet())
}

/**
 * Token-free public account index stored locally in private SharedPreferences so
 * [IgngFamilySsoProvider] can answer sibling IGNG queries instantaneously without touching
 * AndroidKeyStore or exposing any OAuth secrets.
 *
 * Only active OAuth 2.1 accounts (with a non-blank rotating `refreshToken` and not locally
 * invalidated) may be exported so legacy or expired sessions never mislead sibling apps.
 */
internal object IgngFamilyDirectoryStore {
    private const val PREFS_NAME = "igng_family_sso_directory"
    private const val KEY_ENTRIES = "public_accounts_v1"

    internal fun filterExportableAccounts(
        accounts: List<SavedAccount>,
        invalidatedTokens: Set<String> = emptySet(),
    ): List<SavedAccount> = accounts.filter { acc ->
        !acc.refreshToken.isNullOrBlank() &&
            acc.accessToken.isNotBlank() &&
            acc.accessToken !in invalidatedTokens
    }

    fun write(
        context: Context,
        accounts: List<SavedAccount>,
        activeUserId: Long?,
        invalidatedTokens: Set<String> = emptySet(),
    ) {
        val exportable = filterExportableAccounts(accounts, invalidatedTokens)
        val entries = exportable.map { acc ->
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
 * Exported ContentProvider that:
 * 1. Shares non-secret public IGNG account hints (`query`) with certificate-verified sibling IGNG apps.
 * 2. Performs browser-free PKCE S256 handoff (`call(METHOD_REQUEST_HANDOFF)`) via
 *    `POST /api/v1/auth/family-handoff`, returning ONLY a 60-second single-use authorization code
 *    bound to the caller's PKCE challenge and OAuth client_id.
 *
 * Security invariants:
 * - Strictly allowlists sibling package AND verifies APK signing certificate SHA-256 via [IgngFamilySignatureVerifier].
 * - Never exposes `access_token`, `refresh_token`, or `session_id`.
 * - Caller can only request handoff codes for its own registered `client_id` and `redirect_uri`.
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
        if (!IgngFamilySignatureVerifier.isTrustedSibling(ctx, caller)) {
            return null
        }
        val specs = IgngFamilyApps.loadSpecs(ctx)
        val entries = IgngFamilyDirectoryStore.read(ctx)
        val cursor = MatrixCursor(COLUMNS, entries.size)
        val selfPkg = ctx.packageName
        val selfName = specs[selfPkg]?.displayName ?: "IGNG 应用"
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

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != METHOD_REQUEST_HANDOFF) return null
        val ctx = context ?: return errorBundle("provider_unavailable")
        val caller = callingPackage ?: return errorBundle("caller_unknown")
        if (!IgngFamilySignatureVerifier.isTrustedSibling(ctx, caller)) {
            return errorBundle("caller_untrusted")
        }
        val callerSpec = IgngFamilyApps.loadSpecs(ctx)[caller]
            ?: return errorBundle("caller_unregistered")

        val targetClientId = extras?.getString(EXTRA_TARGET_CLIENT_ID)?.trim().orEmpty()
        val redirectUri = extras?.getString(EXTRA_REDIRECT_URI)?.trim().orEmpty()
        val codeChallenge = extras?.getString(EXTRA_CODE_CHALLENGE)?.trim().orEmpty()
        val codeChallengeMethod = extras?.getString(EXTRA_CODE_CHALLENGE_METHOD)?.trim().orEmpty()
        val expectedUserId = extras?.getLong(EXTRA_EXPECTED_USER_ID, 0L)?.takeIf { it > 0L }

        // Caller may only request handoff for its own registered OAuth client & callback URI
        if (callerSpec.clientId.isBlank() || targetClientId != callerSpec.clientId) {
            return errorBundle("client_id_mismatch")
        }
        if (callerSpec.redirectUri.isBlank() || redirectUri != callerSpec.redirectUri) {
            return errorBundle("redirect_uri_mismatch")
        }
        if (codeChallengeMethod != "S256" || !PKCE_CHALLENGE_RE.matches(codeChallenge)) {
            return errorBundle("invalid_pkce")
        }

        val delegate = IgngFamilyHandoffHost.current()
            ?: return errorBundle("host_not_ready")

        val outcome = runCatching {
            runBlocking(Dispatchers.IO) {
                withTimeout(12_000L) {
                    delegate.issueHandoffCode(
                        targetClientId = targetClientId,
                        redirectUri = redirectUri,
                        codeChallenge = codeChallenge,
                        expectedUserId = expectedUserId,
                    )
                }
            }
        }.getOrElse {
            FamilyHandoffIssueResult.Failed(it.message ?: "handoff_failed")
        }

        return when (outcome) {
            is FamilyHandoffIssueResult.Issued -> Bundle().apply {
                putBoolean(EXTRA_OK, true)
                putString(EXTRA_HANDOFF_CODE, outcome.code)
                putString(EXTRA_EXPIRES_AT, outcome.expiresAt)
                putLong(EXTRA_USER_ID, outcome.userId)
                putString(EXTRA_USERNAME, outcome.username)
            }
            is FamilyHandoffIssueResult.Failed -> errorBundle(outcome.reason)
        }
    }

    private fun errorBundle(reason: String): Bundle = Bundle().apply {
        putBoolean(EXTRA_OK, false)
        putString(EXTRA_ERROR, reason)
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

        const val METHOD_REQUEST_HANDOFF = "request_family_handoff"
        const val EXTRA_TARGET_CLIENT_ID = "target_client_id"
        const val EXTRA_REDIRECT_URI = "redirect_uri"
        const val EXTRA_CODE_CHALLENGE = "code_challenge"
        const val EXTRA_CODE_CHALLENGE_METHOD = "code_challenge_method"
        const val EXTRA_EXPECTED_USER_ID = "expected_user_id"
        const val EXTRA_OK = "ok"
        const val EXTRA_ERROR = "error"
        const val EXTRA_HANDOFF_CODE = "handoff_code"
        const val EXTRA_EXPIRES_AT = "expires_at"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_USERNAME = "username"

        private val PKCE_CHALLENGE_RE = Regex("^[A-Za-z0-9_-]{43,128}$")

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
 * Discovers IGNG accounts currently signed into certificate-verified sibling IGNG apps installed
 * on the same device, and requests browser-free PKCE S256 handoff codes from them.
 */
object IgngFamilyAccountDiscovery {
    suspend fun discoverSiblingAccounts(context: Context): List<FamilySharedAccountHint> =
        withContext(Dispatchers.IO) {
            val appCtx = context.applicationContext
            val selfPackage = appCtx.packageName
            val knownSpecs = IgngFamilyApps.loadSpecs(appCtx)
            val discovered = mutableListOf<FamilySharedAccountHint>()

            for ((pkg, spec) in knownSpecs) {
                if (pkg == selfPackage) continue
                if (!IgngFamilySignatureVerifier.isTrustedSibling(appCtx, pkg)) continue
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
                                    c.getString(idxSrcName).orEmpty().ifBlank { spec.displayName }
                                } else {
                                    spec.displayName
                                },
                                isActiveInSource = idxActive >= 0 && c.getInt(idxActive) != 0,
                                sourceCertVerified = true,
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

    /**
     * Requests a 60-second single-use PKCE S256 handoff code from a verified sibling IGNG app.
     * Returns null if the sibling app does not support handoff yet, is not trusted, or fails.
     */
    suspend fun requestSiblingHandoff(
        context: Context,
        sourcePackage: String,
        targetClientId: String,
        redirectUri: String,
        codeChallenge: String,
        expectedUserId: Long? = null,
    ): FamilyHandoffGrant? = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        if (!IgngFamilySignatureVerifier.isTrustedSibling(appCtx, sourcePackage)) {
            return@withContext null
        }
        val extras = Bundle().apply {
            putString(IgngFamilySsoProvider.EXTRA_TARGET_CLIENT_ID, targetClientId)
            putString(IgngFamilySsoProvider.EXTRA_REDIRECT_URI, redirectUri)
            putString(IgngFamilySsoProvider.EXTRA_CODE_CHALLENGE, codeChallenge)
            putString(IgngFamilySsoProvider.EXTRA_CODE_CHALLENGE_METHOD, "S256")
            if (expectedUserId != null && expectedUserId > 0L) {
                putLong(IgngFamilySsoProvider.EXTRA_EXPECTED_USER_ID, expectedUserId)
            }
        }
        val authority = IgngFamilyApps.authorityFor(sourcePackage)
        val result = runCatching {
            appCtx.contentResolver.call(
                Uri.parse("content://$authority"),
                IgngFamilySsoProvider.METHOD_REQUEST_HANDOFF,
                null,
                extras,
            )
        }.getOrNull() ?: return@withContext null

        if (!result.getBoolean(IgngFamilySsoProvider.EXTRA_OK, false)) {
            return@withContext null
        }
        val code = result.getString(IgngFamilySsoProvider.EXTRA_HANDOFF_CODE)?.trim().orEmpty()
        if (code.isBlank()) return@withContext null
        FamilyHandoffGrant(
            code = code,
            expiresAt = result.getString(IgngFamilySsoProvider.EXTRA_EXPIRES_AT).orEmpty(),
            userId = result.getLong(IgngFamilySsoProvider.EXTRA_USER_ID, 0L),
            username = result.getString(IgngFamilySsoProvider.EXTRA_USERNAME).orEmpty(),
        )
    }
}
