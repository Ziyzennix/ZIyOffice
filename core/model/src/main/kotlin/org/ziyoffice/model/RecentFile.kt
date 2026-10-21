package org.ziyoffice.model

/**
 * A file the user has opened, remembered locally so it can be reopened with one tap. The
 * URI is a Storage Access Framework document URI and the app holds a read grant for it.
 * Nothing here ever leaves the device.
 */
data class RecentFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val lastOpenedAtEpochMs: Long,
) {
    val kind: DocumentKind get() = DocumentKind.fromFileName(displayName)
}
