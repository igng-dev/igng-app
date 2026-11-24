package net.igng.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IgngSemVerTest {

    @Test
    fun parsesStableAndPrereleaseWithOrWithoutPrefix() {
        val stable = IgngSemVer.parse("v3.3.1")
        assertNotNull(stable)
        assertEquals(3, stable!!.major)
        assertEquals(3, stable.minor)
        assertEquals(1, stable.patch)
        assertFalse(stable.isPrerelease)
        assertEquals(30301, stable.inferredStableVersionCode)
        assertEquals("3.3.1", stable.canonicalName)

        val pre = IgngSemVer.parse("3.4.0-rc.2")
        assertNotNull(pre)
        assertTrue(pre!!.isPrerelease)
        assertEquals(listOf("rc", "2"), pre.prerelease)
        assertEquals(30400, pre.inferredStableVersionCode)

        assertNull(IgngSemVer.parse("invalid-tag"))
    }

    @Test
    fun comparesNumericMinorPatchAndPrereleaseOrderCorrectly() {
        assertTrue(IgngSemVer.compareNames("3.10.0", "3.2.0") > 0)
        assertTrue(IgngSemVer.compareNames("3.4.0", "3.4.0-rc.2") > 0)
        assertTrue(IgngSemVer.compareNames("3.4.0-rc.10", "3.4.0-rc.2") > 0)
        assertTrue(IgngSemVer.compareNames("3.4.0-beta.1", "3.4.0-alpha.9") > 0)
        assertEquals(0, IgngSemVer.compareNames("v3.3.1", "3.3.1"))
    }

    @Test
    fun enforcesMonotonicVersionCodeAndAllowsRcToStableSameCode() {
        assertTrue(
            IgngSemVer.isNewerRelease(
                candidateVersionCode = 30400,
                candidateVersionName = "3.4.0",
                currentVersionCode = 30301,
                currentVersionName = "3.3.1",
            ),
        )
        assertFalse(
            IgngSemVer.isNewerRelease(
                candidateVersionCode = 30301,
                candidateVersionName = "3.3.1",
                currentVersionCode = 30301,
                currentVersionName = "3.3.1",
            ),
        )
        // Never downgrade versionCode even if versionName looks higher
        assertFalse(
            IgngSemVer.isNewerRelease(
                candidateVersionCode = 30300,
                candidateVersionName = "3.4.0",
                currentVersionCode = 30301,
                currentVersionName = "3.3.1",
            ),
        )
        // Same versionCode upgrading from rc -> stable is allowed
        assertTrue(
            IgngSemVer.isNewerRelease(
                candidateVersionCode = 30400,
                candidateVersionName = "3.4.0",
                currentVersionCode = 30400,
                currentVersionName = "3.4.0-rc.1",
            ),
        )
    }

    @Test
    fun badgeAlertRespectsChangeScopePushPolicy() {
        val syncOnly = AppUpdateRelease(
            tagName = "v3.4.0",
            versionName = "3.4.0",
            versionCode = 30400,
            title = "v3.4.0",
            changelog = "",
            assetName = "igng-sample-v3.4.0.apk",
            downloadUrl = "https://github.com/igng-dev/igng-app/releases/download/v3.4.0/igng-sample-v3.4.0.apk",
            releasePageUrl = "https://github.com/igng-dev/igng-app/releases/tag/v3.4.0",
            changeScope = AppUpdateChangeScope.UNCHANGED,
        )
        val snapSync = AppUpdateUiSnapshot(
            currentVersionName = "3.3.1",
            currentVersionCode = 30301,
            state = AppUpdateState.Available(release = syncOnly),
        )
        assertFalse(snapSync.hasBadgeAlert)

        val appChanged = syncOnly.copy(changeScope = AppUpdateChangeScope.APP_ONLY)
        val snapChanged = snapSync.copy(state = AppUpdateState.Available(release = appChanged))
        assertTrue(snapChanged.hasBadgeAlert)
    }
}
