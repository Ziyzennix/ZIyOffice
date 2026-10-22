# ZiyOffice feature matrix vs WPS Office

A checklist that gets updated as things land. `Status` values: `done` · `in progress` ·
`planned` · `—` (not planned yet). Only mark `done` what a user can actually do on a phone
today.

**Content preservation.** Saving writes the edited parts back into the package the file came
from instead of building a new one from ZiyOffice's own model. A paragraph the user didn't
touch comes out of a save byte for byte. A paragraph they did touch keeps its properties, its
hyperlinks, its comment anchors and its drawings, and only the text and the formatting they
changed get rewritten. Everything the editor can't edit (comments, footnotes, tracked
changes, equations, text boxes, section breaks, charts, pivot tables, conditional formatting,
filters, hidden rows and columns) stays in the file exactly as it was. The editors list that
content when you open the file; nothing gets removed by a save. See the per-format rows
below, and `DocxPreservationTest` / `XlsxPreservationTest` for the enforced contract.

## Foundations (M0)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Opens documents from user folders (SAF) | yes | yes | done |
| Opens from another app ("Open with" / "Share to") | yes | yes | done |
| Recent files list, local only | yes | yes | done |
| **No system permissions** | no (dozens) | **yes** | done |
| **No ads / no subscription** | no | **yes** | done |
| **No account required** | no | **yes** | done |
| **Can't use the network at all** | no | **yes** | done |
| Dark mode | yes | yes | done |
| Home screen, Settings, About (privacy receipt) | yes | yes | done |
| Save-safety: unsaved-edit prompt, crash recovery draft | yes | yes | done |
| Accessible controls (TalkBack labels/state, 48 dp targets) | partial | yes | done |

## Documents — M1

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.docx` | yes | | done |
| Bold/italic/underline/strike, colors, sizes | yes | | done |
| Headings, lists, alignment, line spacing | yes | | done |
| Paragraph indent and spacing | yes | Increase or decrease the indent and set tight, normal or relaxed spacing; both survive a save | done |
| Images (insert/resize) | yes | | done |
| Tables (insert, add/remove rows/cols, merged cells) | yes | Horizontal and vertical merges are read, drawn and kept by a save | done |
| Find & replace | yes | Find the next match, replace one match or replace all; a formula is searched through the text it quotes, never through its references | done |
| Copy and paste cells | yes | Copy a range as tab-separated text and paste it anywhere, including into another app; a copied formula stays a formula | done |
| Undo/redo, word count | yes | | done |
| Autosave + crash recovery | yes | | done |
| Export to PDF (matches screen exactly) | yes | | done |
| Print via the system print service | yes | | done |
| Headers/footers | yes | Shown above and below the body and edited in place; kept by a save. First-page and even-page variants are kept as they are but can't be edited | done |
| Comments, track change, hyperlinks, equations, text boxes, section/column layout | yes | Kept as they are by a save, so the file stays complete, but not editable here (listed on open) | — |
| Equations, SmartArt | partial | | — |
| Mail merge | yes | | — |

## Sheets — M2

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.xlsx`, CSV import/export | yes | | done |
| Grid with touch + keyboard editing | yes | | done |
| Formula engine (SUM, IF, VLOOKUP, … growing set) | yes | About 70 functions: aggregates, maths, text, logic, conditional counting, lookup, statistics, dates and information tests | done |
| Number/date formatting | yes | | done |
| Freeze panes, sorting | yes | Sort by one column or by two, the second breaking the ties | done |
| Named ranges | yes | Give a range a name and use it in a formula; read and write `.xlsx` defined names | done |
| Merged cells | yes | Range select, merge/unmerge, read and write `.xlsx`; sorting is refused while merges exist | done |
| Column widths and row heights | yes | Resize the selected columns or rows; read and write `.xlsx` `<col>` widths and row `ht` | done |
| Cell borders | yes | Draw the top, bottom, left or right edge, every edge or a box around the selection; read and write `.xlsx` border styles | done |
| Dropdown lists in cells | yes | Give a range a list of choices and pick one with a tap; a plain list survives a save, a rule that points at another range is warned about | done |
| Insert and delete rows and columns | yes | Insert above/below or left/right and delete from the selected range; cells, merged ranges, sizes, the freeze line and formula references follow the edit, and a deleted reference becomes `#REF!` | done |
| Charts (bar, from a column) | yes | | done |
| Cell notes | yes | Pin a note to a cell and read it back; written the way a spreadsheet program expects, and a spreadsheet program reads it | done |
| Export to PDF (paginated, header row repeated) | yes | Merged titles span their columns; custom widths, heights, fills, borders and alignment are honoured | done |
| Print via the system print service | yes | | done |
| Content a save keeps but can't edit (charts, images, pivot tables, conditional formatting, filters, protection, hidden rows/columns) | no | Kept as they are by a save; listed on open | done |
| Pivot tables | yes | | — |
| Macros | yes | | — |

## PDF — M3

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Viewer (page render, page list) | yes | | done |
| Annotate (highlight, ink, notes) | yes | | done |
| Merge / split / rotate / reorder | yes (paid) | | done |
| Page labels and text stamps | yes | Stamp text on a page, and number the pages of a saved or printed copy | done |
| Fill forms | yes (paid) | Fill the text boxes, tick the boxes and pick from the lists, and save the filled document. The answers go into the file's own fields, so the copy keeps the fonts, the images and the layout | done |
| Digital signature (handwritten ink, drawn not cryptographic) | yes (paid) | | done |
| True redaction (content removed, not black boxes) | partial (paid) | | done |
| Export progress + cancel, output verified non-empty and `%PDF` | no | | done |
| Marks rotate with their page; mark list with remove | no | | done |
| Print via the system print service (current page order, marks baked in) | yes | | done |

## Slides — M4

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.pptx` text (preserve other package parts) | yes | | done |
| Slide strip + text editing | yes | Editing a slide's text keeps the layout, the images, the animations and the rest of the package | done |
| Print via the system print service | yes | | done |
| Shape/image editing on slides | yes | Put a picture or a shape on a slide, and read the ones a file already had; the picture keeps its own bytes, so a save puts the same picture back | done |
| Present mode + speaker notes | yes | | done |
| Transitions / animations | yes | A transition per slide, kept by a save | done |

## Privacy & freedom (the "beat WPS" list). M5

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Encrypted vault + biometric lock | no | | planned |
| Metadata scrub on share/export | no | A document ZiyOffice *creates* carries no `docProps/` at all (verified by `MetadataScrubTest`). A document it *edits* keeps the metadata it came with, because the file stays the author's own, and adds none of its own | done |
| Offline OCR (scan → text) | cloud, limited | | planned |
| Handwriting / ink notes | premium | | planned |
| Templates (create from built-in templates) | yes, account-gated | | done |
| License-safe font pack | yes, account-gated | | planned |
| Works with radios off | no | | — (by construction) |
| Reproducible source builds (F-Droid) | no | | planned |

## Non-goals (for now)

Collaboration, cloud sync, macros/VBA, pivot tables, mail merge, equation editor. Each one
may come back later, one at a time, with a decision recorded.
