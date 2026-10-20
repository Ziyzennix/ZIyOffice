package org.ziyoffice.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The failure screen used to show the raw exception message, which on a real
 * device read: "Permission Denial: reading
 * com.android.providers.media.MediaDocumentsProvider uri content://… requires
 * that you obtain access using ACTION_OPEN_DOCUMENT or related APIs". That is a
 * developer instruction, not a sentence for a reader. These tests pin the
 * mapping and, more importantly, that no internal class name survives it.
 */
class ReadFailureCopyTest {

    @Test
    fun permissionDenialBecomesAnActionableSentence() {
        val raw = "Permission Denial: reading com.android.providers.media." +
            "MediaDocumentsProvider uri content://com.android.providers.media.documents/" +
            "document/document%3A1000142160 from pid=30708, uid=10287 requires that you " +
            "obtain access using ACTION_OPEN_DOCUMENT or related APIs"
        val copy = ReadFailureCopy.describe(raw)
        assertTrue(copy.contains("permission", ignoreCase = true), copy)
        assertTrue(copy.contains("Open a file"), copy)
        assertFalse(copy.contains("com.android.providers"), copy)
        assertFalse(copy.contains("ACTION_OPEN_DOCUMENT"), copy)
        assertFalse(copy.contains("uid="), copy)
    }

    @Test
    fun securityExceptionWithoutPermissionWordingIsStillHandled() {
        val copy = ReadFailureCopy.describe("java.lang.SecurityException: no access")
        assertTrue(copy.contains("permission", ignoreCase = true), copy)
        assertFalse(copy.contains("SecurityException"), copy)
    }

    @Test
    fun missingFileSaysItWasMovedOrDeleted() {
        val copy = ReadFailureCopy.describe("ENOENT (No such file or directory)")
        assertTrue(copy.contains("no longer where it was"), copy)
    }

    @Test
    fun sizeLimitExplainsTheLimit() {
        val copy = ReadFailureCopy.describe("Package exceeds the 128 MB safety limit")
        assertTrue(copy.contains("larger than"), copy)
    }

    @Test
    fun missingPackagePartIsNotReportedAsCorruption() {
        val copy = ReadFailureCopy.describe("word/document.xml not found in the package")
        assertTrue(copy.contains("missing the part"), copy)
    }

    @Test
    fun emptyOrUnknownReasonsFallBackToPlainWords() {
        val generic = ReadFailureCopy.describe(null)
        assertEquals(generic, ReadFailureCopy.describe(""))
        assertEquals(generic, ReadFailureCopy.describe("   "))
        // An unrecognised internal message is replaced, not shown raw.
        assertEquals(generic, ReadFailureCopy.describe("IllegalStateException: parser depth 0"))
        assertFalse(generic.contains("Exception"))
    }
}
