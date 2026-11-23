package net.igng.core.model

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreModelTest {
    @Test
    fun `V1Id parses prefixed and bare numeric ids`() {
        assertEquals(42, V1Id.parseNumeric("usr_42"))
        assertEquals(108, V1Id.numericOf("post_108"))
        assertEquals(7, V1Id.parseNumeric("7"))
        assertNull(V1Id.parseNumeric(""))
        assertNull(V1Id.parseNumeric("usr_abc"))
        assertEquals("tkt_15", V1Id.format("tkt", 15))
    }

    @Test
    fun `IgngCoreEndpoints trusts only api and sso hosts`() {
        val ep = object : IgngCoreEndpoints {
            override val apiRoot = "https://www.igng.net"
            override val ssoRoot = "https://sso.igng.net"
        }
        assertTrue(ep.isTrustedAuthHost("https://www.igng.net/api/v1/users/me"))
        assertTrue(ep.isTrustedAuthHost("https://sso.igng.net/api/oauth/token"))
        assertFalse(ep.isTrustedAuthHost("https://evil.example.com/api/v1/users/me"))
        assertFalse(ep.isTrustedAuthHost("https://www.igng.net.evil.com/test"))
    }

    @Test
    fun `asBooleanFlag handles bool int and string primitives`() {
        assertTrue(JsonPrimitive(true).asBooleanFlag())
        assertTrue(JsonPrimitive(1).asBooleanFlag())
        assertTrue(JsonPrimitive("yes").asBooleanFlag())
        assertFalse(JsonPrimitive(0).asBooleanFlag())
        assertFalse(JsonPrimitive("false").asBooleanFlag())
        assertFalse((null as kotlinx.serialization.json.JsonElement?).asBooleanFlag())
    }
}
