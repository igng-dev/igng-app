package net.igng.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceGeneratorTest {
    @Test
    fun `RFC 7636 known test vector matches S256 challenge`() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expected = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        assertEquals(expected, PkceGenerator.generateCodeChallenge(verifier))
        assertEquals(expected, PkceGenerator.computeS256Challenge(verifier))
    }

    @Test
    fun `generated pairs have high entropy and valid length`() {
        val a = PkceGenerator.generate()
        val b = PkceGenerator.generate()
        assertTrue(a.verifier.length in 43..128)
        assertTrue(a.state.length >= 20)
        assertNotEquals(a.verifier, b.verifier)
        assertNotEquals(a.state, b.state)
        assertEquals(PkceGenerator.generateCodeChallenge(a.verifier), a.challenge)
    }
}
