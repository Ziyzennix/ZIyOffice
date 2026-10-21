package org.ziyoffice.ooxml

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Checks that a package is a readable ZIP holding the part its format needs.
 *
 * A save is only worth reporting as successful when the file that was
 * actually written can be opened again. A truncated package, a stream that
 * failed halfway or a provider that wrote nothing all look like success from
 * inside the app, and the user only finds out when the file will not open.
 *
 * The check is deliberately small: it reads the entry names and confirms the
 * one part that makes the format what it is.
 */
object PackageIntegrity {

    /** The part that must be present for each format. */
    fun requiredPart(kind: PackageKind): String = when (kind) {
        PackageKind.DOCUMENT -> "word/document.xml"
        PackageKind.SPREADSHEET -> "xl/workbook.xml"
        PackageKind.PRESENTATION -> "ppt/presentation.xml"
    }

    /**
     * True when [bytes] is a readable package that holds the part [kind]
     * needs. A package that cannot be read at all is false, not an error: the
     * caller reports the failure, it does not crash on it.
     */
    fun verify(bytes: ByteArray, kind: PackageKind): Boolean {
        if (bytes.isEmpty()) return false
        if (!hasEndOfCentralDirectory(bytes)) return false
        var found = false
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == requiredPart(kind)) found = true
                    // The entries are read so a corrupt archive is discovered
                    // here rather than by the app that opens it later.
                    zip.readBytes()
                    zip.closeEntry()
                }
            }
            found
        } catch (error: java.io.IOException) {
            false
        }
    }

    /**
     * True when the archive ends with the end-of-central-directory record.
     *
     * A stream reads the entries one after another and never notices that the
     * directory at the end of the file is missing, so a package that was cut
     * short halfway would pass. The record is what says "the archive is
     * complete", so its absence means the file is not.
     */
    private fun hasEndOfCentralDirectory(bytes: ByteArray): Boolean {
        val signature = byteArrayOf(0x50, 0x4b, 0x05, 0x06)
        val window = minOf(bytes.size, 66_000)
        val from = bytes.size - window
        var index = bytes.size - 4
        while (index >= from) {
            if (bytes[index] == signature[0] && bytes[index + 1] == signature[1] &&
                bytes[index + 2] == signature[2] && bytes[index + 3] == signature[3]
            ) {
                return true
            }
            index--
        }
        return false
    }
}

/** The package formats ZiyOffice writes. */
enum class PackageKind { DOCUMENT, SPREADSHEET, PRESENTATION }
