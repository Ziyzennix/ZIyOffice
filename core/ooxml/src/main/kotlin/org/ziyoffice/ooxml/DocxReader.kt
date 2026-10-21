package org.ziyoffice.ooxml

import java.io.IOException
import java.io.InputStream
import org.ziyoffice.model.Document

/**
 * Reads a .docx package into the ZiyOffice document model using only platform
 * APIs: `java.util.zip` for the container and a small XML scanner for the
 * parts. No Apache POI, no xmlbeans.
 *
 * Supported subset:
 *  - document.xml: paragraphs, runs, tabs and breaks, alignment, indentation,
 *    list membership (`w:numPr`), tables (`w:tbl` with grid, grid spans,
 *    repeating header rows, nested tables)
 *  - styles.xml: `w:docDefaults` plus paragraph styles with their `w:basedOn`
 *    chains, resolved onto each run (run-level formatting always wins);
 *    table styles are inspected for borders
 *  - numbering.xml: `num` → `abstractNum` → level number formats and starts
 *  - header/footer parts: the default variant of each section, including their
 *    own images. First-page and even-page variants are preserved but not
 *    modelled.
 *
 * Unsupported content is skipped, never faked. It is also *preserved*: the
 * reader keeps the whole package, so [DocxEditor] can write the document back
 * with those parts untouched.
 *
 * The reader and the writer share one parser, so the model and the package
 * can never disagree about what the document contains.
 */
class DocxReader {

    /**
     * Reads a package into the model.
     *
     * Use [readSource] when the document may be edited and saved again: it
     * keeps the original package so a save can preserve everything the model
     * does not cover.
     */
    fun read(input: InputStream): Document = readSource(input).document

    /**
     * Reads a package into the model together with the snapshot of the
     * original package that a save needs.
     */
    fun readSource(input: InputStream): DocxSource = DocxSourceReader().read(input)
}

/**
 * The WordprocessingML namespace, as defined by ECMA-376 / ISO 29500.
 */
const val WORDPROCESSINGML_NAMESPACE =
    "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

/**
 * Thrown when a package cannot be read as a WordprocessingML document.
 */
internal fun missingPart(part: String): Nothing =
    throw IOException("$part not found in the package")
