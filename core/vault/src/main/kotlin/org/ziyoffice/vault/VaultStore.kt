package org.ziyoffice.vault

import java.io.File
import javax.crypto.SecretKey

/**
 * The vault on the disk.
 *
 * Creating a vault writes the salt the passphrase is stretched with. Opening
 * one reads that salt back and stretches the passphrase into the same key.
 */
class VaultStore(
    /** The folder the vault lives in. */
    val directory: File,
) {

    /** True when there is a vault here. */
    val exists: Boolean get() = File(directory, Vault.KEYS_NAME).isFile

    /**
     * A new vault, with [passphrase] as its key.
     *
     * The salt is written in the clear, because it has to be: the passphrase
     * is stretched with it, and the result is the only thing that can open
     * the vault.
     */
    fun create(passphrase: String): Vault {
        directory.mkdirs()
        val salt = VaultCipher.randomBytes(VaultCipher.SALT_BYTES)
        val key = VaultCipher.deriveKey(passphrase, salt)
        File(directory, Vault.KEYS_NAME).writeBytes(salt)
        File(directory, Vault.DATA_FOLDER).mkdirs()
        return Vault(directory, key)
    }

    /**
     * The vault, or null when the passphrase is the wrong one.
     *
     * A vault that does not exist is not a wrong passphrase, and says so.
     */
    fun open(passphrase: String): Vault? {
        val keys = File(directory, Vault.KEYS_NAME)
        if (!keys.isFile) return null
        val salt = keys.readBytes()
        if (salt.size != VaultCipher.SALT_BYTES) return null
        val key = VaultCipher.deriveKey(passphrase, salt)
        val vault = Vault(directory, key)
        if (!vault.readsCorrectly()) return null
        return vault
    }

    /** The key a passphrase gives, without opening anything. */
    fun keyFor(passphrase: String): SecretKey? {
        val keys = File(directory, Vault.KEYS_NAME)
        if (!keys.isFile) return null
        val salt = keys.readBytes()
        if (salt.size != VaultCipher.SALT_BYTES) return null
        return VaultCipher.deriveKey(passphrase, salt)
    }
}
