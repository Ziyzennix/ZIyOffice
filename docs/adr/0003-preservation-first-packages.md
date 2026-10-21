# 0003: Preservation-first office packages

- **Status:** accepted
- **Date:** 2026-10-21
- **Supersedes:** part of [0001](0001-ooxml-layer.md)

## Context

The first OOXML layer read a document into a ZiyOffice model and wrote the model back out as
a new package. The model holds a documented subset of each format: paragraphs, runs, lists,
tables, images, headers and footers for Word; cells, formulas, basic formatting, merges and
sizes for Sheets.

A round trip through that pair worked. Read, edit, write, reopen kept working. That's why
the design survived as long as it did. But the writer rebuilt the whole package from the
model, so everything the model couldn't express disappeared on the first save: comments,
footnotes and endnotes, tracked changes, hyperlinks, equations, text boxes, section breaks,
multi-column layouts, styles, themes and document properties in Word; charts, drawings,
images, pivot tables, comments, conditional formatting, filters, sheet protection, hidden rows
and columns and named ranges in Sheets.

The app knew. `ContentAudit` scanned the package on open and warned before the first write.
That turned silent data loss into announced data loss, which is better and still not
acceptable. An office suite that empties a chart when you fix a typo in a cell isn't usable
on a document anybody cares about. The prompt for this work was right that a warning dialog
isn't a fix for a preventable loss.

## Decision

**A save writes the edited parts back into the package the file came from.**

`DocxSource` and `XlsxSource` hold the original package together with a snapshot of the
elements the model was built from. `DocxEditor` and `XlsxEditor` then:

- copy through every part they don't own, byte for byte;
- rewrite only the elements the user changed, keeping the markup around them;
- add new content (a formatting style, a picture) to the parts that already exist rather
  than replacing them.

For Word:

- a paragraph whose model still matches what the source produced is copied verbatim;
- a paragraph that changed keeps its `w:pPr`, its run properties and the elements around its
  runs (a `w:hyperlink` wrapper, a `w:commentRangeStart`, a `w:drawing`). Only the text of the
  runs the user edited and the properties they changed get rewritten, and a property is only
  written when the model disagrees with what the source resolved to, so a run that inherits
  its bold from a style doesn't gain a redundant `<w:b/>`;
- the section properties, the styles, the numbering, the theme, the settings and the document
  properties are never touched.

For Sheets:

- the worksheet part gets rewritten, but a cell keeps the style it had unless its formatting
  changed, in which case the new formatting is *added* to the workbook's own style sheet and
  the cell points at that;
- a row keeps the attributes the workbook gave it (hidden, outline level) that the model
  doesn't hold;
- the charts, drawings, pivot tables, comments, filters, conditional formatting and named
  ranges live in other parts, which are copied through untouched.

The cost is that the writers no longer produce a package from nothing. `DocxWriter` and
`XlsxWriter` stay, for the files ZiyOffice creates; `DocxEditor` and `XlsxEditor` are the path
for the files it edits.

## Consequences

- The warning before a save is gone: there's nothing to warn about. The editors list the
  content they can't *edit*, which is a different and more useful thing to say.
- The reader and the writer have to agree about what a document contains, or the writer would
  patch the wrong elements. The old pull parser couldn't say where an element sat in the
  source, so `XmlScanner` replaced it: a small scanner that keeps the source offsets of
  everything it reads. One parser serves both directions, which removes the class of bug where
  the model and the file disagree.
- `ContentAudit` changed meaning: it reports what's kept but not editable, not what's lost.
  `ContentLossCopy` became `PreservedCopy` for the same reason.
- Editing a document no longer strips its metadata. A document ZiyOffice creates still
  carries none; a document it edits keeps the metadata its author put there, which is what an
  editor should do.
- A save is now a *patch*, so the writer has to be careful about a paragraph that changed shape
  (a paragraph that became a table, a row that was inserted). Those cases fall back to
  generating the block from the model and splicing it into the original XML, so the rest of
  the document keeps its own markup.
- The model also has to keep run boundaries intact. A model that flattened a paragraph onto a
  single run would put the paragraph's whole text into the first run and empty out the rest,
  which quietly kills the hyperlink, the comment anchor and the tracked change the run was
  carrying. `Paragraph.applyText` and `RunAlignment` exist for that.

## Alternatives considered

- **Adopt Apache POI or a similar library.** Rejected for now: the licence is fine, but POI's
  object model is a poor fit for Android (it's heavy, and its `XSSF`/`HWPF` APIs pull in a lot
  of surface), and the preservation problem isn't solved by owning the package. It's solved by
  editing it in place, which is what POI's `OPCPackage` gives but which costs a large
  dependency. The custom layer keeps the APK small and the code auditable, and the
  offset-based scanner is a small amount of code to own.
- **Keep the rebuild and rely on the warning.** Rejected: the warning is honest but the loss
  is still real, and it's the kind of loss that loses a chart.
- **Save to a copy and never overwrite.** Rejected: it makes the app unusable for normal
  editing, and it isn't what the user asked for.
