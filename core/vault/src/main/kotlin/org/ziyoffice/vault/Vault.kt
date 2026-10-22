package org.ziyoffice.vault

import java.io.File
import java.security.SecureRandom
import javax.crypto.SecretKey

/**
 * A vault on the disk.
 *
 * The folder holds three things: the salt and the number of stretches, which
 * are not secret; the index, which is encrypted and holds the names; and the
 * files, each encrypted on its own.
 *
 * The index is encrypted too. A name is a piece of information about the
 * person who owns the file, and a vault that leaks its names leaks that.
 */
class Vault(
    /** The folder the vault lives in. */
    val directory: File,
    /** The key the passphrase was stretched into. */
    val key: SecretKey,
) {

    /** The files in the vault. */
    fun list(): List<VaultEntry> {
        val index = readIndex() ?: return emptyList()
        return index.entries.sortedBy { it.name.lowercase() }
    }

    /**
     * True when the key is the right one.
     *
     * The index is what proves it: it only reads with the right key. A vault
     * with nothing in it yet cannot tell, and does not pretend to.
     */
    fun readsCorrectly(): Boolean {
        val file = File(directory, INDEX_NAME)
        if (!file.isFile) return true
        return VaultCipher.decrypt(key, file.readBytes()) != null
    }

    /**
     * Puts a file in the vault.
     *
     * The name is the one the user gave it. The bytes are encrypted before
     * they touch the disk.
     */
    fun put(name: String, bytes: ByteArray): VaultEntry {
        val entry = VaultEntry(
            id = newId(),
            name = name,
            size = bytes.size,
            createdAt = System.currentTimeMillis(),
        )
        val encrypted = VaultCipher.encrypt(key, bytes)
        dataFile(entry.id).writeBytes(encrypted)
        val index = readIndex() ?: VaultIndex()
        writeIndex(index.copy(entries = index.entries + entry))
        return entry
    }

    /** The bytes of a file, or null when the vault does not have it. */
    fun get(id: String): ByteArray? {
        val file = dataFile(id)
        if (!file.isFile) return null
        return VaultCipher.decrypt(key, file.readBytes())
    }

    /** The name of a file. */
    fun nameOf(id: String): String? = readIndex()?.entries?.firstOrNull { it.id == id }?.name

    /** Takes a file out of the vault. */
    fun delete(id: String) {
        dataFile(id).delete()
        val index = readIndex() ?: return
        writeIndex(index.copy(entries = index.entries.filterNot { it.id == id }))
    }

    /** Gives a file another name. */
    fun rename(id: String, name: String) {
        val index = readIndex() ?: return
        writeIndex(
            index.copy(
                entries = index.entries.map { entry ->
                    if (entry.id == id) entry.copy(name = name) else entry
                },
            ),
        )
    }

    /** A file's bytes, written somewhere the platform can read. */
    fun extractTo(id: String, target: File): Boolean {
        val bytes = get(id) ?: return false
        target.writeBytes(bytes)
        return true
    }

    private fun dataFile(id: String) = File(dataFolder, "$id.enc")

    private fun readIndex(): VaultIndex? {
        val file = File(directory, INDEX_NAME)
        if (!file.isFile) return null
        val plain = VaultCipher.decrypt(key, file.readBytes()) ?: return null
        return VaultIndex.parse(plain.decodeToString())
    }

    private fun writeIndex(index: VaultIndex) {
        File(directory, INDEX_NAME).writeBytes(
            VaultCipher.encrypt(key, index.toText().encodeToByteArray()),
        )
    }

    private fun newId(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private val dataFolder: File
        get() = File(directory, DATA_FOLDER).also { it.mkdirs() }

    companion object {
        /** The name of the part that holds the salt and the number of stretches. */
        const val KEYS_NAME = "vault.keys"

        /** The name of the encrypted index. */
        const val INDEX_NAME = "vault.index"

        /** The folder the encrypted files live in. */
        const val DATA_FOLDER = "data"
    }
}

/** One file in a vault. */
data class VaultEntry(
    /** What the file is called inside the vault. */
    val id: String,
    /** The name the user gave it. */
    val name: String,
    /** How many bytes the file holds. */
    val size: Int,
    /** When it was put in, in milliseconds since the epoch. */
    val createdAt: Long,
)

/**
 * The list of files in a vault.
 *
 * It is written as plain text, one file per line, and the whole thing is
 * encrypted. The format is deliberately dull: a name may hold any character
 * except a newline, and the length is written out so a name with a space in
 * it survives.
 */
internal data class VaultIndex(val entries: List<VaultEntry> = emptyList()) {

    /** The index as text. */
    fun toText(): String = buildString {
        entries.forEach { entry ->
            append(entry.id).append('\t')
            append(entry.size).append('\t')
            append(entry.createdAt).append('\t')
            append(entry.name.replace("\t", " ").replace("\n", " ")).append('\n')
        }
    }

    companion object {
        /** The index as text. */
        fun parse(text: String): VaultIndex {
            val entries = mutableListOf<VaultEntry>()
            text.split('\n').forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('\t')
                if (parts.size < 4) return@forEach
                val size = parts[1].toIntOrNull() ?: return@forEach
                val created = parts[2].toLongOrNull() ?: return@forEach
                entries += VaultEntry(
                    id = parts[0],
                    name = parts.drop(3).joinToString("\t"),
                    size = size,
                    createdAt = created,
                )
            }
            return VaultIndex(entries)
        }
    }
}
