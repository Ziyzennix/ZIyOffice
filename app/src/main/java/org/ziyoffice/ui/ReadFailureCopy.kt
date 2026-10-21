package org.ziyoffice.ui

/**
 * Turns a read failure into something a person can act on.
 *
 * The raw message is whatever the platform threw, and it tends to be written
 * for a developer ("obtain access using ACTION_OPEN_DOCUMENT or related
 * APIs"). This maps the failures that actually happen to one sentence each and
 * falls back to a plain generic sentence for everything else.
 *
 * Unit-tested so the wording cannot drift back into stack-trace prose.
 */
internal object ReadFailureCopy {

    /** A sentence describing what went wrong and what to do about it. */
    fun describe(reason: String?): String {
        if (reason.isNullOrBlank()) return GENERIC
        val lower = reason.lowercase()
        return when {
            "permission denial" in lower || "securityexception" in lower ->
                "ZiyOffice doesn't have permission for this file. Open it with " +
                    "\u201COpen a file\u201D so the app can ask, or share it to " +
                    "ZiyOffice again from the other app."
            // Before the plain "not found" branch: a missing package part is a
            // broken file, not a file that moved.
            "not found in the package" in lower ->
                "This file is missing the part that holds its content, so there " +
                    "is nothing to show."
            "not found" in lower || "no such file" in lower ->
                "The file is no longer where it was. It may have been moved, " +
                    "renamed or deleted."
            "safety limit" in lower || "audit limit" in lower ->
                "This file is larger than ZiyOffice will open. The limit is there " +
                    "so a malformed file can't eat the phone's memory."
            "not a valid" in lower || "zip" in lower || "malformed" in lower ->
                "This doesn't look like a complete Office file. It might be " +
                    "damaged, or saved in a format ZiyOffice can't read."
            else -> GENERIC
        }
    }

    private const val GENERIC =
        "Couldn't read this file. It might be damaged, or saved in a format " +
            "ZiyOffice doesn't open."
}
