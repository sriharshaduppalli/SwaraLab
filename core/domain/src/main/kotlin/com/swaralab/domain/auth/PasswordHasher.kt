package com.swaralab.domain.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256. The file store calls this before a password is written.
 * A server adapter must not use this class; the server hashes the password itself.
 */
class PasswordHasher(
    private val iterations: Int = DEFAULT_ITERATIONS,
) {
    fun salt(): String {
        val bytes = ByteArray(SALT_BYTES)
        random.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(
            password.toCharArray(),
            Base64.getDecoder().decode(salt),
            iterations,
            HASH_BITS,
        )
        try {
            val encoded = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .encoded
            return Base64.getEncoder().encodeToString(encoded)
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(password: String, salt: String, expectedHash: String): Boolean {
        val actual = Base64.getDecoder().decode(hash(password, salt))
        val expected = Base64.getDecoder().decode(expectedHash)
        return MessageDigest.isEqual(actual, expected)
    }

    private companion object {
        const val DEFAULT_ITERATIONS = 210_000
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
        val random = SecureRandom()
    }
}
