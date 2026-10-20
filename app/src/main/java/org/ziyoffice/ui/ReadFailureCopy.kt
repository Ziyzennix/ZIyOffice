package org.ziyoffice.ui

/**
 * Turns a read failure into something a person can act on.
 *
 * The raw message is whatever the platform or the format layer threw —
 * `SecurityException` text names an internal content-provider class and tells
 * the user to "obtain access using ACTION_OPEN_DOCUMENT or related APIs", which
 * is an instruction for a developer, not a reader. This maps the failures that
 * actually happen to one sentence each, and falls back to the raw message only
 * when it is genuinely informative.
 *
 * Unit-tested so the wording cannot silently drift back into stack-trace prose.
 */
internal object ReadFailureCopy {

    /** A sentence describing what went wrong and what to do about it. */
    fun describe(reason: String?): String {
        if (reason.isNullOrBlank()) return GENERIC
        val lower = reason.lowercase()
        return when {
            "permission denial" in lower || "securityexception" in lower ->
                "ZiyOffice was not granted access to this file. Open it with " +
                    "\u201COpen a file\u201D so the app can ask for permission, or " +
                    "share it to ZiyOffice again from the other app."
            // Before the plain "not found" branch: a missing package part is a
            // broken file, not a file that moved.
            "not found in the package" in lower ->
                "This file is missing the part that holds its content, so there " +
                    "is nothing to show."
            "not found" in lower || "no such file" in lower ->
                "The file is no longer where it was. It may have been moved, " +
                    "renamed or deleted."
            "safety limit" in lower || "audit limit" in lower ->
                "This file is larger than ZiyOffice will open. The limit exists " +
                    "so a malformed file cannot exhaust the phone's memory."
            "not a valid" in lower || "zip" in lower || "malformed" in lower ->
                "This does not look like a complete Office file. It may be " +
                    "damaged, or saved in a format ZiyOffice cannot read."
            else -> GENERIC
        }
    }

    private const val GENERIC =
        "The file could not be read. It may be damaged, or saved in a format " +
            "ZiyOffice cannot open."
}
