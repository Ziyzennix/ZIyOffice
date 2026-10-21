package org.ziyoffice.model

/**
 * Built-in document templates. Everything here is authored for ZiyOffice and
 * ships as plain model data: no downloaded files, no fonts, no licences.
 */
data class DocumentTemplate(
    val id: String,
    val name: String,
    val description: String,
    val document: Document,
)

private fun heading(text: String) = Paragraph(
    runs = listOf(Run(text, bold = true, sizePt = 18f)),
    spacingBeforeTwips = 240,
    spacingAfterTwips = 120,
)

private fun title(text: String) = Paragraph(
    runs = listOf(Run(text, bold = true, sizePt = 24f)),
    alignment = ParagraphAlignment.CENTER,
    spacingAfterTwips = 240,
)

private fun body(text: String) = Paragraph(
    runs = listOf(Run(text, sizePt = 11f)),
    spacingAfterTwips = 160,
)

private fun bullet(text: String, numId: Int = 1) = Paragraph(
    runs = listOf(Run(text, sizePt = 11f)),
    list = ListInfo(numId = numId, level = 0, kind = ListKind.BULLET),
    spacingAfterTwips = 80,
)

val DOCUMENT_TEMPLATES: List<DocumentTemplate> = listOf(
    DocumentTemplate(
        id = "blank",
        name = "Blank document",
        description = "An empty page.",
        document = Document(blocks = listOf(Paragraph())),
    ),
    DocumentTemplate(
        id = "letter",
        name = "Formal letter",
        description = "Sender, date, recipient and body.",
        document = Document(
            blocks = listOf(
                body("Your Name"),
                body("Street address · City · Postcode"),
                body(""),
                body("Date"),
                body(""),
                body("Recipient name"),
                body("Recipient address"),
                Paragraph(runs = listOf(Run("Dear Recipient,", sizePt = 11f)), spacingBeforeTwips = 240, spacingAfterTwips = 160),
                body("Write your letter here."),
                Paragraph(runs = listOf(Run("Sincerely,", sizePt = 11f)), spacingBeforeTwips = 240),
                body("Your Name"),
            ),
        ),
    ),
    DocumentTemplate(
        id = "meeting",
        name = "Meeting notes",
        description = "Attendees, agenda and action items.",
        document = Document(
            blocks = listOf(
                title("Meeting notes"),
                body("Date: "),
                body("Attendees: "),
                heading("Agenda"),
                bullet("Item one"),
                bullet("Item two"),
                heading("Decisions"),
                body(""),
                heading("Action items"),
                bullet("Owner — task — due date"),
            ),
        ),
    ),
    DocumentTemplate(
        id = "resume",
        name = "Résumé",
        description = "A clean one-page CV outline.",
        document = Document(
            blocks = listOf(
                title("Your Name"),
                Paragraph(
                    runs = listOf(Run("Role · email · phone · city", sizePt = 10f)),
                    alignment = ParagraphAlignment.CENTER,
                    spacingAfterTwips = 240,
                ),
                heading("Experience"),
                body("Job title — Employer — Dates"),
                bullet("Achievement with a number in it."),
                bullet("Responsibility you owned."),
                heading("Education"),
                body("Qualification — Institution — Year"),
                heading("Skills"),
                bullet("Skill one"),
                bullet("Skill two"),
            ),
        ),
    ),
    DocumentTemplate(
        id = "report",
        name = "Short report",
        description = "Summary, findings and recommendation.",
        document = Document(
            blocks = listOf(
                title("Report title"),
                body("Prepared by · Date"),
                heading("Summary"),
                body("One paragraph that states the conclusion first."),
                heading("Findings"),
                bullet("Finding one"),
                bullet("Finding two"),
                heading("Recommendation"),
                body("What should happen next, and who owns it."),
            ),
        ),
    ),
)

/** Looks a template up by [id]; blank when the id is unknown. */
fun documentTemplate(id: String): DocumentTemplate =
    DOCUMENT_TEMPLATES.firstOrNull { it.id == id } ?: DOCUMENT_TEMPLATES.first()
