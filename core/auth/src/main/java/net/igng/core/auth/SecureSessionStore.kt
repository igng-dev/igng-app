package net.igng.core.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import net.igng.core.model.OAuthPendingRequest
import net.igng.core.model.SavedAccount
import net.igng.core.network.IgngJson

@Serializable
private data class VaultPayload(
    val activeUserId: Long? = null,
    val accounts: List<SavedAccount> = emptyList(),
)

/**
 * Storage configuration allowing each IGNG app to keep its own isolated SharedPreferences file,
 * AndroidKeyStore alias, and backward-compatible on-disk migration format so existing users
 * upgrade seamlessly without re-logging in.
 */
data class VaultStorageConfig(
    val prefsName: String,
    val pendingPrefsName: String,
    val keyAlias: String,
    val deviceIdKey: String = "device_id",
    /**
     * When true, imports legacy per-account SharedPreferences entries (`account_<id>_token` with
     * `base64(iv).base64(ct)` format, used by IGNGmc v1.0.10–v2.3.2) into the unified `vault_v2`
     * encrypted payload on first load.
     */
    val legacyPerFieldCompat: Boolean = false,
)

/**
 * Hardware-backed Android Keystore AES-256-GCM encrypted store for multi-account OAuth 2.1
 * credentials, persistent `dev_*` device identifier, and short-lived PKCE pending state.
 */
class SecureSessionStore(
    context: Context,
    private val config: VaultStorageConfig,
) {
    private val appContext = context.applicationContext
    private val vaultPrefs = appContext.getSharedPreferences(config.prefsName, Context.MODE_PRIVATE)
    private val pendingPrefs = appContext.getSharedPreferences(config.pendingPrefsName, Context.MODE_PRIVATE)
    private val lock = Any()

    private val _accounts = MutableStateFlow<List<SavedAccount>>(emptyList())
    val accounts: StateFlow<List<SavedAccount>> = _accounts.asStateFlow()

    private val _activeAccount = MutableStateFlow<SavedAccount?>(null)
    val activeAccount: StateFlow<SavedAccount?> = _activeAccount.asStateFlow()

    init {
        reloadFromDisk()
    }

    fun getOrCreateDeviceId(): String = synchronized(lock) {
        val existing = vaultPrefs.getString(config.deviceIdKey, null)?.trim()
        if (!existing.isNullOrBlank() && DEVICE_ID_RE.matches(existing)) return existing
        val randomBytes = ByteArray(12)
        SecureRandom().nextBytes(randomBytes)
        val hex = randomBytes.joinToString("") { "%02x".format(it) }
        val generated = "dev_$hex"
        vaultPrefs.edit().putString(config.deviceIdKey, generated).apply()
        generated
    }

    fun currentAccessToken(): String? = activeAccount.value?.accessToken
    fun activeAccountSnapshot(): SavedAccount? = activeAccount.value
    fun accountsSnapshot(): List<SavedAccount> = accounts.value

    fun upsertAccount(account: SavedAccount, makeActive: Boolean = true): Unit = synchronized(lock) {
        val list = _accounts.value.filterNot { it.userId == account.userId } + account
        val activeId = if (makeActive) account.userId else (_activeAccount.value?.userId ?: account.userId)
        persistVault(VaultPayload(activeUserId = activeId, accounts = list))
    }

    fun switchAccount(userId: Long): SavedAccount? = synchronized(lock) {
        val target = _accounts.value.firstOrNull { it.userId == userId } ?: return null
        persistVault(VaultPayload(activeUserId = target.userId, accounts = _accounts.value))
        target
    }

    fun updateActiveProfile(
        username: String? = null,
        nickname: String? = null,
        avatarUrl: String? = null,
    ) = synchronized(lock) {
        val current = _activeAccount.value ?: return
        val updated = current.copy(
            username = username ?: current.username,
            nickname = nickname ?: current.nickname,
            avatarUrl = avatarUrl ?: current.avatarUrl,
        )
        upsertAccount(updated, makeActive = true)
    }

    fun removeAccount(userId: Long): Unit = synchronized(lock) {
        val remaining = _accounts.value.filterNot { it.userId == userId }
        val nextActive = if (_activeAccount.value?.userId == userId) {
            remaining.firstOrNull()?.userId
        } else {
            _activeAccount.value?.userId
        }
        persistVault(VaultPayload(activeUserId = nextActive, accounts = remaining))
    }

    fun clearActiveAccount(): Unit = synchronized(lock) {
        val active = _activeAccount.value ?: return
        removeAccount(active.userId)
    }

    fun savePendingOAuth(request: OAuthPendingRequest): Unit = synchronized(lock) {
        val raw = IgngJson.instance.encodeToString(OAuthPendingRequest.serializer(), request)
        val encrypted = encryptString(raw)
        pendingPrefs.edit().putString(KEY_PENDING_OAUTH, encrypted).apply()
    }

    fun consumePendingOAuth(expectedState: String): OAuthPendingRequest? = synchronized(lock) {
        val encrypted = pendingPrefs.getString(KEY_PENDING_OAUTH, null)
        if (encrypted != null) {
            pendingPrefs.edit().remove(KEY_PENDING_OAUTH).apply()
            val decoded = runCatching {
                val raw = decryptString(encrypted) ?: return null
                IgngJson.instance.decodeFromString(OAuthPendingRequest.serializer(), raw)
            }.getOrNull() ?: return null
            if (decoded.state != expectedState || decoded.isExpired()) return null
            return decoded
        }
        // Fallback: legacy MC plain pending prefs (`verifier` / `state` / `created_at`)
        if (config.legacyPerFieldCompat) {
            val savedState = pendingPrefs.getString("state", null)
            val verifier = pendingPrefs.getString("verifier", null)
            val createdAtMs = pendingPrefs.getLong("created_at", 0L)
            pendingPrefs.edit().clear().apply()
            if (!savedState.isNullOrBlank() && !verifier.isNullOrBlank() && savedState == expectedState) {
                val req = OAuthPendingRequest(
                    state = savedState,
                    codeVerifier = verifier,
                    createdAtEpochSec = createdAtMs / 1000L,
                )
                if (!req.isExpired()) return req
            }
        }
        return null
    }

    fun clearPendingOAuth() = synchronized(lock) {
        pendingPrefs.edit().clear().apply()
    }

    private fun reloadFromDisk() = synchronized(lock) {
        val encrypted = vaultPrefs.getString(KEY_VAULT_BLOB, null)
        if (!encrypted.isNullOrBlank()) {
            val payload = runCatching {
                val plain = decryptString(encrypted) ?: return@runCatching null
                IgngJson.instance.decodeFromString(VaultPayload.serializer(), plain)
            }.getOrNull()
            if (payload != null) {
                _accounts.value = payload.accounts
                _activeAccount.value = payload.accounts.firstOrNull { it.userId == payload.activeUserId }
                    ?: payload.accounts.firstOrNull()
                return
            }
            vaultPrefs.edit().remove(KEY_VAULT_BLOB).apply()
        }

        // Seamless one-time migration from legacy MC per-account keys (igng_mc_auth)
        if (config.legacyPerFieldCompat) {
            val migrated = loadLegacyPerFieldVault()
            if (migrated != null && migrated.accounts.isNotEmpty()) {
                persistVault(migrated)
                return
            }
        }

        _accounts.value = emptyList()
        _activeAccount.value = null
    }

    private fun loadLegacyPerFieldVault(): VaultPayload? {
        val ids = vaultPrefs.getStringSet("account_ids", emptySet()).orEmpty()
        if (ids.isEmpty()) return null
        val activeId = vaultPrefs.getString("active_user_id", null)?.toLongOrNull()
        val accounts = ids.mapNotNull { idStr ->
            val userId = idStr.toLongOrNull() ?: return@mapNotNull null
            val key = "account_$userId"
            val encToken = vaultPrefs.getString("${key}_token", null) ?: return@mapNotNull null
            val token = runCatching { decryptLegacyDotFormat(encToken) }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val encRefresh = vaultPrefs.getString("${key}_refresh", null)
            val refresh = encRefresh?.let { enc ->
                runCatching { decryptLegacyDotFormat(enc) }.getOrNull()?.takeIf { it.isNotBlank() }
            }
            SavedAccount(
                userId = userId,
                username = vaultPrefs.getString("${key}_username", "").orEmpty(),
                nickname = vaultPrefs.getString("${key}_nickname", "").orEmpty(),
                email = vaultPrefs.getString("${key}_email", "")?.ifBlank { null },
                avatarUrl = vaultPrefs.getString("${key}_avatar", "")?.ifBlank { null },
                accessToken = token,
                refreshToken = refresh,
                expiresAt = vaultPrefs.getString("${key}_expires", "")?.ifBlank { null },
                refreshExpiresAt = vaultPrefs.getString("${key}_refresh_expires", "")?.ifBlank { null },
                grantedScope = vaultPrefs.getString("${key}_scope", "").orEmpty(),
                sessionId = vaultPrefs.getString("${key}_session_id", "").orEmpty(),
                deviceName = vaultPrefs.getString("${key}_device", "")?.ifBlank { null },
                adminRoleWire = vaultPrefs.getString("${key}_role", "")?.ifBlank { null },
            )
        }.sortedBy { it.userId }
        return VaultPayload(activeUserId = activeId ?: accounts.firstOrNull()?.userId, accounts = accounts)
    }

    private fun persistVault(payload: VaultPayload) {
        val encoded = IgngJson.instance.encodeToString(VaultPayload.serializer(), payload)
        val encrypted = encryptString(encoded)
        vaultPrefs.edit().putString(KEY_VAULT_BLOB, encrypted).apply()
        _accounts.value = payload.accounts
        _activeAccount.value = payload.accounts.firstOrNull { it.userId == payload.activeUserId }
            ?: payload.accounts.firstOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(config.keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                config.keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encryptString(plain: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        val combined = ByteArray(1 + iv.size + ciphertext.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(ciphertext, 0, combined, 1 + iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decryptString(encoded: String): String? {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size < 14) return null
        val ivLen = bytes[0].toInt() and 0xFF
        if (bytes.size <= 1 + ivLen) return null
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    private fun decryptLegacyDotFormat(encoded: String): String {
        val parts = encoded.split(".")
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ct = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), StandardCharsets.UTF_8)
    }

    companion object {
        private const val KEY_VAULT_BLOB = "vault_v2"
        private const val KEY_PENDING_OAUTH = "pending_oauth"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private val DEVICE_ID_RE = Regex("^[a-z0-9_-]{8,64}$")
    }
}
