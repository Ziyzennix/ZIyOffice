package org.ziyoffice.saf

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ziyoffice.ooxml.PackageIntegrity
import org.ziyoffice.ooxml.PackageKind

/**
 * Writes a document to a URI and checks that it landed.
 *
 * The Storage Access Framework gives no way to replace a file in one step, so
 * the write is done in the order that keeps the risk smallest:
 *
 *  1. the bytes are checked before anything is written, so a broken package
 *     never reaches the user's file;
 *  2. the bytes go into a temporary file in the app's own cache, and that
 *     file is read back to confirm it is complete;
 *  3. only then is the destination opened, and the copy is the last thing
 *     that happens.
 *
 * The destination is still overwritten in place — no provider offers a
 * transaction — but a failure before step 3 leaves the original untouched,
 * and a failure during it leaves a file that was valid right up to the
 * moment it was replaced.
 */
object SafeWrite {

    /**
     * Writes [bytes] to [uri].
     *
     * Returns the number of bytes written, or throws when the package is not
     * sound or the destination could not be written. The caller reports the
     * failure; it never claims a save that did not happen.
     */
    suspend fun write(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        kind: PackageKind,
    ): Int = withContext(Dispatchers.IO) {
        if (!PackageIntegrity.verify(bytes, kind)) {
            throw IOException("the file could not be written: the package is not readable")
        }
        val temporary = File.createTempFile("ziyoffice-write", ".tmp", context.cacheDir)
        try {
            temporary.outputStream().use { out -> out.write(bytes) }
            val staged = temporary.readBytes()
            if (staged.size != bytes.size || !PackageIntegrity.verify(staged, kind)) {
                throw IOException("the file could not be written: the copy did not verify")
            }
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("the file could not be opened for writing")
            stream.use { out -> out.write(staged) }
            staged.size
        } finally {
            temporary.delete()
        }
    }

    /**
     * Writes plain bytes, for a format that is not a package — a CSV export,
     * for instance. The bytes are still staged in a temporary file first, so a
     * failure while producing them never truncates the destination.
     */
    suspend fun writePlain(context: Context, uri: Uri, bytes: ByteArray): Int =
        withContext(Dispatchers.IO) {
            val temporary = File.createTempFile("ziyoffice-write", ".tmp", context.cacheDir)
            try {
                temporary.outputStream().use { out -> out.write(bytes) }
                val staged = temporary.readBytes()
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("the file could not be opened for writing")
                stream.use { out -> out.write(staged) }
                staged.size
            } finally {
                temporary.delete()
            }
        }

    /**
     * Reads [uri] back and checks that what is there is the file that was
     * written.
     *
     * A provider can fail quietly — a full disk, a revoked grant — and the
     * only way to know is to look. The check is what turns "the write call
     * returned" into "the file is really there".
     */
    suspend fun verifyWritten(
        context: Context,
        uri: Uri,
        expectedSize: Int,
        kind: PackageKind,
    ): Boolean = withContext(Dispatchers.IO) {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext false
        bytes.size == expectedSize && PackageIntegrity.verify(bytes, kind)
    }
}
