package org.ziyoffice.model

/** Which part of the page a paragraph lives in. */
enum class DocumentRegion { BODY, HEADER, FOOTER }

/**
 * Addresses one editable paragraph inside a document.
 *
 * [region] matters because a header paragraph is not reachable from the body
 * list: Word keeps headers and footers in their own package parts, and so does
 * the model. Editing a header is therefore the same operation as editing a body
 * paragraph, applied to a different block list.
 *
 * [blockIndex] and the cell fields address the paragraph inside that region's
 * blocks, so the same path shape works for body, header and footer.
 */
data class FocusPath(
    val blockIndex: Int,
    val cellRow: Int? = null,
    val cellColumn: Int? = null,
    val cellParagraph: Int = 0,
    val region: DocumentRegion = DocumentRegion.BODY,
)

/** The blocks of [region], or null when the document has no such part. */
fun Document.blocksIn(region: DocumentRegion): List<Block>? = when (region) {
    DocumentRegion.BODY -> blocks
    DocumentRegion.HEADER -> header
    DocumentRegion.FOOTER -> footer
}

internal fun Document.withBlocksIn(region: DocumentRegion, newBlocks: List<Block>): Document =
    when (region) {
        DocumentRegion.BODY -> copy(blocks = newBlocks)
        DocumentRegion.HEADER -> copy(header = newBlocks)
        DocumentRegion.FOOTER -> copy(footer = newBlocks)
    }

fun Document.allFocusPaths(): List<FocusPath> {
    val paths = mutableListOf<FocusPath>()
    DocumentRegion.entries.forEach { region ->
        val regionBlocks = blocksIn(region) ?: return@forEach
        for ((bIdx, block) in regionBlocks.withIndex()) {
            when (block) {
                is Paragraph -> paths.add(FocusPath(bIdx, region = region))
                is Table -> {
                    for ((rIdx, row) in block.rows.withIndex()) {
                        for ((cIdx, cell) in row.cells.withIndex()) {
                            for ((pIdx, innerBlock) in cell.blocks.withIndex()) {
                                if (innerBlock is Paragraph) {
                                    paths.add(FocusPath(bIdx, rIdx, cIdx, pIdx, region))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    return paths
}

fun Document.paragraphAt(path: FocusPath): Paragraph? {
    val regionBlocks = blocksIn(path.region) ?: return null
    if (path.cellRow == null) {
        return regionBlocks.getOrNull(path.blockIndex) as? Paragraph
    }
    val table = regionBlocks.getOrNull(path.blockIndex) as? Table ?: return null
    val row = table.rows.getOrNull(path.cellRow) ?: return null
    val col = path.cellColumn ?: return null
    val cell = row.cells.getOrNull(col) ?: return null
    return cell.blocks.getOrNull(path.cellParagraph) as? Paragraph
}

fun Document.mapParagraphAt(path: FocusPath, transform: (Paragraph) -> Paragraph): Document {
    val regionBlocks = blocksIn(path.region) ?: return this
    if (path.blockIndex !in regionBlocks.indices) return this
    if (path.cellRow == null) {
        val block = regionBlocks[path.blockIndex]
        if (block !is Paragraph) return this
        val transformed = transform(block)
        return withBlocksIn(
            path.region,
            regionBlocks.mapIndexed { i, b -> if (i == path.blockIndex) transformed else b },
        )
    }
    val cellRow = path.cellRow
    val cellCol = path.cellColumn ?: return this
    val table = regionBlocks[path.blockIndex] as? Table ?: return this
    if (cellRow !in table.rows.indices) return this
    val row = table.rows[cellRow]
    if (cellCol !in row.cells.indices) return this
    val cell = row.cells[cellCol]
    if (path.cellParagraph !in cell.blocks.indices) return this
    val targetBlock = cell.blocks[path.cellParagraph]
    if (targetBlock !is Paragraph) return this

    val transformedParagraph = transform(targetBlock)

    return withBlocksIn(
        path.region,
        regionBlocks.mapIndexed { bIdx, b ->
            if (bIdx == path.blockIndex && b is Table) {
                b.copy(
                    rows = b.rows.mapIndexed { rIdx, r ->
                        if (rIdx == cellRow) {
                            r.copy(
                                cells = r.cells.mapIndexed { cIdx, c ->
                                    if (cIdx == cellCol) {
                                        c.copy(
                                            blocks = c.blocks.mapIndexed { pIdx, p ->
                                                if (pIdx == path.cellParagraph) transformedParagraph else p
                                            },
                                        )
                                    } else {
                                        c
                                    }
                                },
                            )
                        } else {
                            r
                        }
                    },
                )
            } else {
                b
            }
        },
    )
}

/**
 * Inserts [paragraph] directly after the paragraph at [path], in whichever
 * region and container that paragraph lives in.
 */
fun Document.withParagraphAddedAfter(path: FocusPath, paragraph: Paragraph): Document =
    mapAtPath(path) { blocks, index ->
        blocks.toMutableList().apply { add(index + 1, paragraph) }
    }

/**
 * Removes the paragraph at [path]. A container never ends up with zero
 * paragraphs, because Word requires at least one `w:p` per body, cell, header
 * and footer.
 */
fun Document.withParagraphRemovedAt(path: FocusPath): Document =
    mapAtPath(path) { blocks, index ->
        blocks.toMutableList().apply {
            removeAt(index)
            if (isEmpty()) add(Paragraph())
        }
    }

/**
 * Applies [transform] to the block list that contains the paragraph at [path],
 * handing the transform the list and the index of that paragraph.
 *
 * Header, footer, body and table-cell paragraphs all go through here, so a
 * structural edit only has to be written once.
 */
private fun Document.mapAtPath(
    path: FocusPath,
    transform: (List<Block>, Int) -> List<Block>,
): Document {
    val regionBlocks = blocksIn(path.region) ?: return this
    if (path.cellRow == null) {
        if (path.blockIndex !in regionBlocks.indices) return this
        return withBlocksIn(path.region, transform(regionBlocks, path.blockIndex))
    }
    val cellRow = path.cellRow
    val cellCol = path.cellColumn ?: return this
    val table = regionBlocks.getOrNull(path.blockIndex) as? Table ?: return this
    if (cellRow !in table.rows.indices) return this
    val row = table.rows[cellRow]
    if (cellCol !in row.cells.indices) return this
    val cell = row.cells[cellCol]
    if (path.cellParagraph !in cell.blocks.indices) return this

    val newCellBlocks = transform(cell.blocks, path.cellParagraph)
    return withBlocksIn(
        path.region,
        regionBlocks.mapIndexed { bIdx, b ->
            if (bIdx != path.blockIndex || b !is Table) return@mapIndexed b
            b.copy(
                rows = b.rows.mapIndexed { rIdx, r ->
                    if (rIdx != cellRow) return@mapIndexed r
                    r.copy(
                        cells = r.cells.mapIndexed { cIdx, c ->
                            if (cIdx != cellCol) return@mapIndexed c
                            c.copy(blocks = newCellBlocks)
                        },
                    )
                },
            )
        },
    )
}
