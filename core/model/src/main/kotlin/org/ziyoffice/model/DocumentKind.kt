package org.ziyoffice.model

/**
 * The document families ZiyOffice recognises.
 *
 * This model is deliberately platform-independent (plain Kotlin, no Android classes)
 * so it can be unit-tested fast and reused by future non-Android front-ends.
 */
enum class DocumentKind(val label: String, val extensions: Set<String>) {
    DOCUMENT("Document", setOf("docx", "doc", "odt", "rtf", "txt")),
    SPREADSHEET("Spreadsheet", setOf("xlsx", "xls", "ods", "csv")),
    PRESENTATION("Presentation", setOf("pptx", "ppt", "odp")),
    PDF("PDF", setOf("pdf")),
    UNKNOWN("File", emptySet()),
    ;

    companion object {
        fun fromFileName(fileName: String): DocumentKind {
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension.isEmpty()) return UNKNOWN
            return entries.firstOrNull { extension in it.extensions } ?: UNKNOWN
        }

        /**
         * The family an intent's MIME type names. A share or view intent always
         * carries one, and it is more reliable than the file name: some content
         * providers hand back a display name with no extension at all
         * ("document:1000142160"), which would otherwise read as [UNKNOWN].
         */
        fun fromMimeType(mimeType: String?): DocumentKind? = when (mimeType?.lowercase()) {
            "application/pdf" -> PDF
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> DOCUMENT
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> SPREADSHEET
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> PRESENTATION
            "text/csv" -> SPREADSHEET
            "text/plain", "application/rtf",
            "application/vnd.oasis.opendocument.text",
            -> DOCUMENT
            "application/vnd.oasis.opendocument.spreadsheet" -> SPREADSHEET
            "application/vnd.oasis.opendocument.presentation" -> PRESENTATION
            else -> null
        }

        /** The extension to give a file whose name lost it, e.g. "docx". */
        fun extensionFor(mimeType: String?): String? = when (mimeType?.lowercase()) {
            "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "text/csv" -> "csv"
            "text/plain" -> "txt"
            "application/rtf" -> "rtf"
            "application/vnd.oasis.opendocument.text" -> "odt"
            "application/vnd.oasis.opendocument.spreadsheet" -> "ods"
            "application/vnd.oasis.opendocument.presentation" -> "odp"
            else -> null
        }
    }
}
