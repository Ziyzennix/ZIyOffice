package org.ziyoffice.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The cryptography of the vault.
 */
class VaultCipherTest {

    @Test
    fun theSamePassphraseGivesTheSameKey() {
        val salt = VaultCipher.randomBytes(VaultCipher.SALT_BYTES)
        val first = VaultCipher.deriveKey("a passphrase", salt)
        val second = VaultCipher.deriveKey("a passphrase", salt)
        assertTrue(first.encoded.contentEquals(second.encoded))
    }

    @Test
    fun anotherPassphraseGivesAnotherKey() {
        val salt = VaultCipher.randomBytes(VaultCipher.SALT_BYTES)
        val first = VaultCipher.deriveKey("a passphrase", salt)
        val second = VaultCipher.deriveKey("another passphrase", salt)
        assertTrue(!first.encoded.contentEquals(second.encoded))
    }

    @Test
    fun anotherSaltGivesAnotherKey() {
        val first = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        val second = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        assertTrue(!first.encoded.contentEquals(second.encoded))
    }

    @Test
    fun whatGoesInComesOut() {
        val key = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        val plain = "a document, in the clear".encodeToByteArray()
        val encrypted = VaultCipher.encrypt(key, plain)
        assertTrue(!encrypted.toString(Charsets.ISO_8859_1).contains("in the clear"))
        assertTrue(VaultCipher.decrypt(key, encrypted)?.contentEquals(plain) == true)
    }

    @Test
    fun theWrongKeyGetsNothing() {
        val salt = VaultCipher.randomBytes(VaultCipher.SALT_BYTES)
        val key = VaultCipher.deriveKey("a passphrase", salt)
        val other = VaultCipher.deriveKey("another passphrase", salt)
        val encrypted = VaultCipher.encrypt(key, "a document".encodeToByteArray())
        assertNull(VaultCipher.decrypt(other, encrypted))
    }

    @Test
    fun damagedBytesGetNothing() {
        val key = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        val encrypted = VaultCipher.encrypt(key, "a document".encodeToByteArray())
        val damaged = encrypted.copyOf()
        damaged[damaged.size - 1] = (damaged[damaged.size - 1] + 1).toByte()
        assertNull(VaultCipher.decrypt(key, damaged))
    }

    @Test
    fun theSameBytesAreNeverEncryptedTheSameWayTwice() {
        val key = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        val plain = "the same bytes".encodeToByteArray()
        val first = VaultCipher.encrypt(key, plain)
        val second = VaultCipher.encrypt(key, plain)
        assertTrue(!first.contentEquals(second))
        assertTrue(VaultCipher.decrypt(key, first)?.contentEquals(plain) == true)
        assertTrue(VaultCipher.decrypt(key, second)?.contentEquals(plain) == true)
    }

    @Test
    fun anEmptyFileIsStillEncrypted() {
        val key = VaultCipher.deriveKey("a passphrase", VaultCipher.randomBytes(VaultCipher.SALT_BYTES))
        val encrypted = VaultCipher.encrypt(key, ByteArray(0))
        assertNotNull(VaultCipher.decrypt(key, encrypted))
        assertEquals(0, VaultCipher.decrypt(key, encrypted)?.size)
    }

    @Test
    fun aFingerprintTellsOneKeyFromAnother() {
        val salt = VaultCipher.randomBytes(VaultCipher.SALT_BYTES)
        val first = VaultCipher.deriveKey("a passphrase", salt)
        val second = VaultCipher.deriveKey("another passphrase", salt)
        assertTrue(VaultCipher.fingerprint(first) != VaultCipher.fingerprint(second))
        assertEquals(VaultCipher.fingerprint(first), VaultCipher.fingerprint(first))
    }
}
