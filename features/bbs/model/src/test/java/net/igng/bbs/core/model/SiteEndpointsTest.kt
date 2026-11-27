package net.igng.bbs.core.model

import net.igng.core.model.V1Id
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteEndpointsTest {

    private val endpoints = SiteEndpoints()

    @Test
    fun trustedHostsOnlyIncludeConfiguredIgngOrigins() {
        assertTrue(endpoints.isTrustedAuthHost("https://www.igng.net/api/v1/bbs/home"))
        assertTrue(endpoints.isTrustedAuthHost("https://sso.igng.net/api/oauth/token"))
        assertFalse(endpoints.isTrustedAuthHost("https://evil.example.com/api/v1/bbs/home"))
    }

    @Test
    fun resolvesLegacyMediaPathIntoV1AuthenticatedEndpoint() {
        val raw = "/api/media/files/0123456789abcdef0123456789abcdef/content"
        val resolved = endpoints.resolveMediaUrl(raw, postId = "post_42")
        assertEquals(
            "https://www.igng.net/api/v1/media/med_0123456789abcdef0123456789abcdef/content?post_id=post_42",
            resolved,
        )
    }

    @Test
    fun v1IdHelpersParseAndFormatIds() {
        assertEquals(108, V1Id.numericOf("post_108"))
        assertEquals(108, V1Id.numericOf("108"))
        assertEquals("post_108", BbsV1Id.post(108))
    }

    @Test
    fun extractsManagedMediaHexIdAndResolvesMediaMetaAcrossUrlFormats() {
        val hex = "0123456789abcdef0123456789abcdef"
        val mediaUrls = mapOf(hex to "https://www.igng.net/api/v1/media/med_$hex/content?post_id=post_2048")
        val mediaMeta = mapOf(hex to MediaMetaDto(width = 1600, height = 900, mimeType = "image/webp"))

        val variants = listOf(
            hex,
            "/i/$hex",
            "/i/$hex.webp",
            "https://www.igngbbs.net/i/${hex.uppercase()}.webp?v=1",
            "https://www.igngbbs.net/i/${hex.uppercase()}?v=1",
            "/p/$hex",
            "/api/media/posts/2048/$hex",
            "/api/media/files/$hex/content",
            "https://www.igng.net/api/v1/media/med_$hex/content?post_id=post_2048",
        )
        variants.forEach { raw ->
            assertEquals("Failed to extract hex from $raw", hex, SiteEndpoints.extractMediaHexId(raw))
            assertEquals(mediaUrls[hex], endpoints.resolvePostMediaUrl(raw, postId = "post_2048", mediaUrls = mediaUrls))
            assertEquals(1600f / 900f, endpoints.resolvePostMediaAspectRatio(raw, mediaMeta)!!, 0.0001f)
        }
        assertEquals(null, SiteEndpoints.extractMediaHexId("https://example.com/photo.png"))
        assertEquals(null, endpoints.resolvePostMediaAspectRatio("https://example.com/photo.png", mediaMeta))
    }
}
