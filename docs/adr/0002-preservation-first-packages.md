# 0002 — Preservation-first office packages

- **Status:** accepted
- **Date:** 2026-10-21
- **Supersedes:** part of [0001](0001-ooxml-layer.md)

## Context

The first OOXML layer read a document into a ZiyOffice model and wrote the model back
out as a new package. The model holds a documented subset of each format: paragraphs,
runs, lists, tables, images, headers and footers for Word; cells, formulas, basic
formatting, merges and sizes for Sheets.

A round trip through that pair worked — read, edit, write, reopen kept working — which
is why the design survived its first year. But the writer rebuilt the *whole* package
from the model, so everything the model could not express disappeared on the first save:
comments, footnotes and endnotes, tracked changes, hyperlinks, equations, text boxes,
section breaks, multi-column layouts, styles, themes and document properties in
Word; charts, drawings, images, pivot tables, comments, conditional formatting, filters,
sheet protection, hidden rows and columns and named ranges in Sheets.

The app knew. `ContentAudit` scanned the package on open and warned before the first
write. That turned silent data loss into announced data loss, which is better and still
not acceptable: an office suite that empties a chart when you fix a typo in a cell is
not usable on a document anybody cares about. The prompt for this work was right that a
warning dialog is not a fix for a preventable loss.

## Decision

**A save writes the edited parts back into the package the file came from.**

`DocxSource` and `XlsxSource` hold the original package together with a snapshot of
the elements the model was built from. `DocxEditor` and `XlsxEditor` then:

- copy through every part they do not own, byte for byte;
- rewrite only the elements the user changed, keeping the markup around them;
- add new content — a formatting style, a picture — to the parts that already exist
  rather than replacing them.

Concretely, for Word:

- a paragraph whose model still equals what the source produced is copied verbatim;
- a paragraph that changed keeps its `w:pPr`, its run properties and the elements
  around its runs (a `w:hyperlink` wrapper, a `w:commentRangeStart`, a `w:drawing`);
  only the text of the runs the user edited and the properties they changed are
  rewritten, and a property is written only when the model disagrees with what the
  source resolved to — so a run that inherits its bold from a style does not gain a
  redundant `<w:b/>`;
- the section properties, the styles, the numbering, the theme, the settings and the
  document properties are never touched.

For Sheets:

- the worksheet part is rewritten, but a cell keeps the style it had unless its
  formatting changed, in which case the new formatting is *added* to the workbook's
  own style sheet and the cell points at that;
- a row keeps the attributes the workbook gave it — hidden, outline level — that the
  model does not hold;
- the charts, drawings, pivot tables, comments, filters, conditional formatting and
  named ranges live in other parts, which are copied through untouched.

The cost is that the writers no longer produce a package from nothing. `DocxWriter`
and `XlsxWriter` stay, for the files ZiyOffice creates; `DocxEditor` and `XlsxEditor`
are the path for the files it edits.

## Consequences

- The warning before a save is gone: there is nothing to warn about. The editors list
  the content they cannot *edit*, which is a different and more useful thing to say.
- The reader and the writer have to agree about what a document contains, or the
  writer would patch the wrong elements. The old pull parser could not say where an
  element sat in the source, so `XmlScanner` replaced it: a small scanner that keeps
  the source offsets of everything it reads. One parser serves both directions, which
  removes the class of bug where the model and the file disagree.
- `ContentAudit` changed meaning: it reports what is kept but not editable, not what
  is lost. `ContentLossCopy` became `PreservedCopy` for the same reason.
- Editing a document no longer strips its metadata. A document ZiyOffice creates still
  carries none; a document it edits keeps the metadata its author put there, which is
  what an editor should do.
- A save is now a *patch*, so the writer has to be careful about a paragraph that
  changed shape — a paragraph that became a table, a row that was inserted. Those
  cases fall back to generating the block from the model and splicing it into the
  original XML, so the surrounding document keeps its own markup.

## Alternatives considered

- **Adopt Apache POI or a similar library.** Rejected for now: the licence is fine,
  but POI's object model is a poor fit for Android (it is heavy, and its `XSSF`/`HWPF`
  APIs pull in a lot of surface), and the preservation problem is not solved by owning
  the package — it is solved by editing it in place, which is what POI's
  `OPCPackage` gives but which costs a large dependency. The custom layer keeps the
  APK small and the code auditable, and the offset-based scanner is a small amount of
  code to own.
- **Keep the rebuild and rely on the warning.** Rejected: the warning is honest but
  the loss is still real, and it is the kind of loss that loses a chart.
- **Save to a copy and never overwrite.** Rejected: it makes the app unusable for
  normal editing, and it is not what the user asked for.
