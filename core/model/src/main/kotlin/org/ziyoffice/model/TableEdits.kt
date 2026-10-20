package org.ziyoffice.model

fun emptyTable(rows: Int, columns: Int, borders: Boolean = true): Table {
    val r = rows.coerceAtLeast(1)
    val c = columns.coerceAtLeast(1)
    return Table(
        rows = List(r) {
            TableRow(
                cells = List(c) {
                    TableCell(listOf(Paragraph()))
                },
            )
        },
        columnCount = c,
        borders = borders,
    )
}

fun Table.withRowInserted(at: Int): Table {
    val clamped = at.coerceIn(0, rows.size)
    val newRow = TableRow(cells = List(columnCount) { TableCell(listOf(Paragraph())) })
    val newRows = rows.toMutableList().apply { add(clamped, newRow) }
    return copy(rows = newRows)
}

fun Table.withRowRemoved(at: Int): Table {
    if (at !in rows.indices) return this
    val newRows = rows.toMutableList().apply { removeAt(at) }
    if (newRows.isEmpty()) {
        newRows.add(TableRow(cells = List(columnCount) { TableCell(listOf(Paragraph())) }))
    }
    return copy(rows = newRows)
}

fun Table.withColumnInserted(at: Int): Table {
    val clampedAt = at.coerceIn(0, columnCount)
    val newRows = rows.map { row ->
        var gridOffset = 0
        var insertIndex = row.cells.size
        for ((idx, cell) in row.cells.withIndex()) {
            if (gridOffset >= clampedAt) {
                insertIndex = idx
                break
            }
            gridOffset += cell.columnSpan
        }
        val newCells = row.cells.toMutableList().apply {
            add(insertIndex, TableCell(listOf(Paragraph())))
        }
        row.copy(cells = newCells)
    }
    return copy(rows = newRows, columnCount = columnCount + 1)
}

fun Table.withColumnRemoved(at: Int): Table {
    if (columnCount <= 0) return this
    val clampedAt = at.coerceIn(0, columnCount - 1)
    val newRows = rows.map { row ->
        val newCells = row.cells.toMutableList()
        var gridOffset = 0
        for ((idx, cell) in row.cells.withIndex()) {
            val cellStart = gridOffset
            val cellEnd = gridOffset + cell.columnSpan
            if (clampedAt in cellStart until cellEnd) {
                if (cell.columnSpan > 1) {
                    newCells[idx] = cell.copy(columnSpan = cell.columnSpan - 1)
                } else {
                    newCells.removeAt(idx)
                }
                break
            }
            gridOffset = cellEnd
        }
        if (newCells.isEmpty()) {
            newCells.add(TableCell(listOf(Paragraph())))
        }
        row.copy(cells = newCells)
    }
    return copy(rows = newRows, columnCount = (columnCount - 1).coerceAtLeast(1))
}
