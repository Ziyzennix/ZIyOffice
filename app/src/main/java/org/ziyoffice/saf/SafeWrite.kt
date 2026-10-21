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
 * SAF cannot replace a file in one step, so the bytes are checked first (a
 * broken package never reaches the user's file), staged in a temp file and read
 * back, and only then copied over the destination. The copy is in place, since
 * no provider offers a transaction, but a failure before it leaves the original
 * untouched.
 */
object SafeWrite {

    /**
     * Writes [bytes] to [uri], returning the number of bytes written. Throws
     * when the package is not sound or the write failed, so the caller never
     * claims a save that did not happen.
     */
    suspend fun write(
        context: Context,
        uri: Uri,
        bytes: ByteArray,
        kind: PackageKind,
    ): Int = withContext(Dispatchers.IO) {
        if (!PackageIntegrity.verify(bytes, kind)) {
            throw IOException("the package isn't readable")
        }
        val temporary = File.createTempFile("ziyoffice-write", ".tmp", context.cacheDir)
        try {
            temporary.outputStream().use { out -> out.write(bytes) }
            val staged = temporary.readBytes()
            if (staged.size != bytes.size || !PackageIntegrity.verify(staged, kind)) {
                throw IOException("the copy didn't verify")
            }
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("the file couldn't be opened for writing")
            stream.use { out -> out.write(staged) }
            staged.size
        } finally {
            temporary.delete()
        }
    }

    /**
     * Writes plain bytes, for a format that is not a package (a CSV export).
     * Staged in a temp file first, so a failure while producing them never
     * truncates the destination.
     */
    suspend fun writePlain(context: Context, uri: Uri, bytes: ByteArray): Int =
        withContext(Dispatchers.IO) {
            val temporary = File.createTempFile("ziyoffice-write", ".tmp", context.cacheDir)
            try {
                temporary.outputStream().use { out -> out.write(bytes) }
                val staged = temporary.readBytes()
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("the file couldn't be opened for writing")
                stream.use { out -> out.write(staged) }
                staged.size
            } finally {
                temporary.delete()
            }
        }

    /**
     * Reads [uri] back and checks that what is there is the file that was
     * written. A provider can fail quietly (a full disk, a revoked grant) and
     * the only way to know is to look.
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
