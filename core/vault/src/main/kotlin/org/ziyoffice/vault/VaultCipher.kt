package org.ziyoffice.vault

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The cryptography of the vault.
 *
 * A passphrase is never stored. It is stretched into a key with PBKDF2, and
 * the key protects everything in the vault. Each file gets a fresh random
 * number, so the same passphrase never encrypts two files the same way.
 *
 * Nothing here talks to the disk. That is [VaultStore]'s job, and keeping
 * them apart means the cryptography can be tested on its own.
 */
object VaultCipher {

    /** The salt size, in bytes. */
    const val SALT_BYTES = 16

    /** The number of bytes of a random number. */
    const val NONCE_BYTES = 12

    /** The tag size of the authentication, in bytes. */
    const val TAG_BITS = 128

    /** How many times the passphrase is stretched. */
    const val ITERATIONS = 210_000

    /** The algorithm the key is used with. */
    private const val ALGORITHM = "AES"

    /** The mode the cipher runs in. */
    private const val MODE = "AES/GCM/NoPadding"

    /** A stretch of the passphrase into a key. */
    fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKey {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return SecretKeySpec(factory.generateSecret(spec).encoded, ALGORITHM)
        } finally {
            spec.clearPassword()
        }
    }

    /** A run of random bytes. */
    fun randomBytes(count: Int): ByteArray = ByteArray(count).also {
        SecureRandom().nextBytes(it)
    }

    /**
     * The encrypted form of [plaintext].
     *
     * The random number that was used is at the front, so the same key can
     * read it back.
     */
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        val encrypted = cipher.doFinal(plaintext)
        return nonce + encrypted
    }

    /**
     * The plaintext of [data], or null when the key is the wrong one.
     *
     * A wrong passphrase fails here, because the authentication tag does not
     * match. There is no way to tell a wrong key from damaged bytes, and
     * there is no need to.
     */
    fun decrypt(key: SecretKey, data: ByteArray): ByteArray? {
        if (data.size <= NONCE_BYTES) return null
        val nonce = data.copyOfRange(0, NONCE_BYTES)
        val encrypted = data.copyOfRange(NONCE_BYTES, data.size)
        return try {
            val cipher = Cipher.getInstance(MODE)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    /** A fingerprint of a key, so a vault can tell one passphrase from another. */
    fun fingerprint(key: SecretKey): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.encoded)
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
