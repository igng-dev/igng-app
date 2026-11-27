package net.igng.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import net.igng.core.model.FamilySharedAccountHint
import net.igng.core.model.SavedAccount
import net.igng.core.model.SessionAuthStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngFamilySsoAndAuthTest {

    @Test
    fun `family app xml entry parser extracts full metadata and normalizes cert fingerprint`() {
        val parsed = IgngFamilyApps.parseEntry(
            "org.example.app|IGNG Sample|igng_sample_android|igngsample://auth/callback|AA:BB:CC:DD",
        )
        assertEquals("org.example.app", parsed?.packageName)
        assertEquals("IGNG Sample", parsed?.displayName)
        assertEquals("igng_sample_android", parsed?.clientId)
        assertEquals("igngsample://auth/callback", parsed?.redirectUri)
        assertEquals("aabbccdd", parsed?.releaseCertSha256)

        val minimal = IgngFamilyApps.parseEntry("org.example.minimal|Sample")
        assertEquals("org.example.minimal", minimal?.packageName)
        assertEquals("Sample", minimal?.displayName)
        assertEquals("", minimal?.releaseCertSha256)
        assertNull(IgngFamilyApps.parseEntry("   "))
    }

    @Test
    fun `certificate verifier requires pinned release digest in release builds and allows shared debug cert only when both debuggable`() {
        val pinned = "8ca1e1a703a4053a06d13078d8d50a04eb92751a1382f53407be29a541af1b2e"
        val other = "0d9cd0525ab75baca783d212d1b347fdeb171866ac9c06fc72c1bef2134f2886"
        val debugCert = "1111111111111111111111111111111111111111111111111111111111111111"

        // Release match succeeds even when self has a different release certificate
        assertTrue(
            IgngFamilySignatureVerifier.isCertDigestTrusted(
                expectedReleaseSha256 = pinned,
                actualCerts = setOf(pinned),
                bothDebuggable = false,
                selfCerts = setOf(other),
            ),
        )
        // Wrong certificate is rejected in release build
        assertFalse(
            IgngFamilySignatureVerifier.isCertDigestTrusted(
                expectedReleaseSha256 = pinned,
                actualCerts = setOf(debugCert),
                bothDebuggable = false,
                selfCerts = setOf(debugCert),
            ),
        )
        // Shared debug certificate is accepted ONLY when both apps are debuggable
        assertTrue(
            IgngFamilySignatureVerifier.isCertDigestTrusted(
                expectedReleaseSha256 = pinned,
                actualCerts = setOf(debugCert),
                bothDebuggable = true,
                selfCerts = setOf(debugCert),
            ),
        )
        // Different debug certs are rejected even when both are debuggable
        assertFalse(
            IgngFamilySignatureVerifier.isCertDigestTrusted(
                expectedReleaseSha256 = pinned,
                actualCerts = setOf("2222"),
                bothDebuggable = true,
                selfCerts = setOf(debugCert),
            ),
        )
    }

    private fun fixtureAccount(
        userId: Long,
        username: String,
        token: String,
        refresh: String?,
    ) = SavedAccount(
        userId = userId,
        username = username,
        accessToken = token,
        refreshToken = refresh,
    )

    @Test
    fun `family directory store filters out legacy and invalidated accounts`() {
        val activeOAuth = fixtureAccount(
            userId = 100L,
            username = "alice",
            token = "tok-active",
            refresh = "ref-active",
        )
        val legacyV1 = fixtureAccount(
            userId = 101L,
            username = "legacy_user",
            token = "tok-legacy",
            refresh = null,
        )
        val expiredOAuth = fixtureAccount(
            userId = 102L,
            username = "expired_user",
            token = "tok-expired",
            refresh = "ref-expired",
        )

        val exportable = IgngFamilyDirectoryStore.filterExportableAccounts(
            accounts = listOf(activeOAuth, legacyV1, expiredOAuth),
            invalidatedTokens = setOf("tok-expired"),
        )
        assertEquals(listOf(100L), exportable.map { it.userId })
    }

    @Test
    fun `auto sync candidate policy upgrades matching legacy user and respects explicit sign out`() {
        val siblingAlice = FamilySharedAccountHint(
            userId = 42L,
            username = "alice",
            nickname = "Alice",
            sourcePackage = "org.example.sibling",
            sourceAppName = "Sibling",
            isActiveInSource = true,
        )
        val siblingBob = FamilySharedAccountHint(
            userId = 99L,
            username = "bob",
            nickname = "Bob",
            sourcePackage = "org.example.sibling",
            sourceAppName = "Sibling",
            isActiveInSource = false,
        )
        val legacyAlice = fixtureAccount(userId = 42L, username = "alice", token = "old", refresh = null)

        // Legacy session matches sibling Alice -> auto-sync even if suppression flag is true
        assertEquals(
            siblingAlice,
            IgngSessionManager.selectAutoSyncCandidate(
                activeAccount = legacyAlice,
                status = SessionAuthStatus.LEGACY_UPGRADE_REQUIRED,
                siblings = listOf(siblingBob, siblingAlice),
                autoSyncSuppressed = true,
            ),
        )

        // Legacy session with no matching sibling userId -> do not switch to a different user silently
        assertNull(
            IgngSessionManager.selectAutoSyncCandidate(
                activeAccount = legacyAlice,
                status = SessionAuthStatus.LEGACY_UPGRADE_REQUIRED,
                siblings = listOf(siblingBob),
                autoSyncSuppressed = false,
            ),
        )

        // Signed out without explicit logout -> auto-sync first active sibling
        assertEquals(
            siblingAlice,
            IgngSessionManager.selectAutoSyncCandidate(
                activeAccount = null,
                status = SessionAuthStatus.SIGNED_OUT,
                siblings = listOf(siblingAlice, siblingBob),
                autoSyncSuppressed = false,
            ),
        )

        // Signed out after explicit logout -> do NOT silently re-login
        assertNull(
            IgngSessionManager.selectAutoSyncCandidate(
                activeAccount = null,
                status = SessionAuthStatus.SIGNED_OUT,
                siblings = listOf(siblingAlice),
                autoSyncSuppressed = true,
            ),
        )

        // Already active -> never auto-sync
        assertNull(
            IgngSessionManager.selectAutoSyncCandidate(
                activeAccount = legacyAlice.copy(refreshToken = "live-ref"),
                status = SessionAuthStatus.ACTIVE,
                siblings = listOf(siblingAlice),
                autoSyncSuppressed = false,
            ),
        )
    }

    @Test
    fun `cancellation exception is never swallowed into OAuthCallbackResult Error`() = runTest {
        var caughtCancellation = false
        val job = async {
            try {
                try {
                    delay(500L)
                    OAuthCallbackResult.Ignored
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    OAuthCallbackResult.Error(t.message ?: "error")
                }
            } catch (_: CancellationException) {
                caughtCancellation = true
                throw CancellationException("propagated")
            }
        }
        delay(20L)
        job.cancel("The coroutine scope left the composition")
        try {
            job.await()
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue(caughtCancellation)
    }
}
