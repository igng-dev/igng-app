package net.igng.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class PkcePair(
    val verifier: String,
    val challenge: String,
    val state: String,
)

object PkceGenerator {
    private val secureRandom = SecureRandom()
    private val urlEncoder = Base64.getUrlEncoder().withoutPadding()

    fun generate(): PkcePair {
        val verifier = generateCodeVerifier()
        return PkcePair(
            verifier = verifier,
            challenge = generateCodeChallenge(verifier),
            state = generateState(),
        )
    }

    fun generateCodeVerifier(byteLength: Int = 48): String {
        val bytes = ByteArray(byteLength.coerceIn(32, 96))
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return urlEncoder.encodeToString(digest)
    }

    fun computeS256Challenge(verifier: String): String = generateCodeChallenge(verifier)

    fun generateState(byteLength: Int = 24): String {
        val bytes = ByteArray(byteLength.coerceIn(16, 48))
        secureRandom.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }
}
