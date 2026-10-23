package org.ziyoffice.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Local copies of unsaved edits, one per document uri, in app-private storage,
 * so a crash or a killed process does not lose work that was never written back
 * to the user's file.
 *
 * The discard marker closes a real race: a debounced draft write may already be
 * running when the user taps Discard, and without the guard that write lands
 * after [discard] and the thrown-away document reappears on the next open. A
 * discarded uri refuses further draft writes for the life of this instance; a
 * new session builds a new instance, so the next real edit recovers normally.
 */
class DraftStore(context: Context) {
    private val dir = File(context.filesDir, "drafts").also { it.mkdirs() }

    /** Per-instance, per-uri: "the user threw this draft away; do not write it again". */
    private val discarded = mutableSetOf<String>()
    private val lock = Any()
    private fun isVaultUri(uri: String): Boolean = uri.contains("/vault-open/")


    fun save(uri: String, bytes: ByteArray) {
        if (isVaultUri(uri)) return
        val k = key(uri)
        synchronized(lock) {
            if (k in discarded) return
            File(dir, k).writeBytes(bytes)
        }
    }

    fun load(uri: String): ByteArray? {
        if (isVaultUri(uri)) return null
        val f = File(dir, key(uri))
        return if (f.exists()) f.readBytes() else null
    }

    /** Drops the recovery copy because the content reached its real file. */
    fun clear(uri: String) {
        val k = key(uri)
        synchronized(lock) { File(dir, k).delete() }
    }

    /**
     * Drops the recovery copy *and* refuses any draft write already in flight,
     * for when the user explicitly chose to throw the work away.
     */
    fun discard(uri: String) {
        val k = key(uri)
        synchronized(lock) {
            discarded += k
            File(dir, k).delete()
        }
    }

    private fun key(uri: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uri.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(40) + ".draft"
}
