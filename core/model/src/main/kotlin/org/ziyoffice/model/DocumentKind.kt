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
    }
}
