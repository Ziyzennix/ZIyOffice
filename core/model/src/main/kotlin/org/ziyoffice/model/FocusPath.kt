package org.ziyoffice.model

data class FocusPath(
    val blockIndex: Int,
    val cellRow: Int? = null,
    val cellColumn: Int? = null,
    val cellParagraph: Int = 0,
)

fun Document.allFocusPaths(): List<FocusPath> {
    val paths = mutableListOf<FocusPath>()
    for ((bIdx, block) in blocks.withIndex()) {
        when (block) {
            is Paragraph -> paths.add(FocusPath(bIdx))
            is Table -> {
                for ((rIdx, row) in block.rows.withIndex()) {
                    for ((cIdx, cell) in row.cells.withIndex()) {
                        for ((pIdx, innerBlock) in cell.blocks.withIndex()) {
                            if (innerBlock is Paragraph) {
                                paths.add(FocusPath(bIdx, rIdx, cIdx, pIdx))
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
    if (path.cellRow == null) {
        return blocks.getOrNull(path.blockIndex) as? Paragraph
    }
    val table = blocks.getOrNull(path.blockIndex) as? Table ?: return null
    val row = table.rows.getOrNull(path.cellRow) ?: return null
    val col = path.cellColumn ?: return null
    val cell = row.cells.getOrNull(col) ?: return null
    return cell.blocks.getOrNull(path.cellParagraph) as? Paragraph
}

fun Document.mapParagraphAt(path: FocusPath, transform: (Paragraph) -> Paragraph): Document {
    if (path.blockIndex !in blocks.indices) return this
    if (path.cellRow == null) {
        val block = blocks[path.blockIndex]
        if (block !is Paragraph) return this
        val transformed = transform(block)
        return copy(
            blocks = blocks.mapIndexed { i, b ->
                if (i == path.blockIndex) transformed else b
            },
        )
    }
    val cellRow = path.cellRow
    val cellCol = path.cellColumn ?: return this
    val table = blocks[path.blockIndex] as? Table ?: return this
    if (cellRow !in table.rows.indices) return this
    val row = table.rows[cellRow]
    if (cellCol !in row.cells.indices) return this
    val cell = row.cells[cellCol]
    if (path.cellParagraph !in cell.blocks.indices) return this
    val targetBlock = cell.blocks[path.cellParagraph]
    if (targetBlock !is Paragraph) return this

    val transformedParagraph = transform(targetBlock)

    return copy(
        blocks = blocks.mapIndexed { bIdx, b ->
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
