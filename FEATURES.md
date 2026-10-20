# ZiyOffice — Feature matrix vs WPS Office

Living checklist. `Status` values: `done` · `in progress` · `planned` · `—` (not planned yet).
The rule for this file: only mark `done` what a user can actually do on a phone today.

**Content preservation.** Saving rebuilds each package from ZiyOffice's own model, so
content the readers do not model is *removed* by a save, not preserved. The editors
audit the file on open and warn before the first write that would do this; "Save as
copy" leaves the original untouched. See the per-format rows below for exactly what is
at risk, and `ContentAudit` / `ContentAuditTest` for the enforced list.

## Foundations (M0)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Opens documents from user folders (SAF) | yes | yes | done |
| Opens from another app ("Open with" / "Share to") | yes | yes | done |
| Recent files list, local only | yes | yes | done |
| **Zero permissions requested** | no (dozens) | **yes** | done |
| **No ads / no subscription** | no | **yes** | done |
| **No account required** | no | **yes** | done |
| **Cannot use the network at all** | no | **yes** | done |
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
| Headers/footers | yes | Shown above and below the body and edited in place; kept by a save. A document using first-page or even-page variants loses those variants (warned) | done |
| Comments, track change, hyperlinks, equations, text boxes, section/column layout | yes | Not editable — and **removed by a save** (warned) | — |
| Equations, SmartArt | partial | | — |
| Mail merge | yes | | — |

## Sheets — M2

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.xlsx`, CSV import/export | yes | | done |
| Grid with touch + keyboard editing | yes | | done |
| Formula engine (SUM, IF, VLOOKUP, … growing set) | yes | About 70 functions: aggregates, maths, text, logic, conditional counting, lookup, statistics, dates and information tests | done |
| Number/date formatting | yes | | done |
| Freeze panes, sorting | yes | | done |
| Merged cells | yes | Range select, merge/unmerge, read and write `.xlsx`; sorting is refused while merges exist | done |
| Column widths and row heights | yes | Resize the selected columns or rows; read and write `.xlsx` `<col>` widths and row `ht` | done |
| Cell borders | yes | Draw the top, bottom, left or right edge, every edge or a box around the selection; read and write `.xlsx` border styles | done |
| Dropdown lists in cells | yes | Give a range a list of choices and pick one with a tap; a plain list survives a save, a rule that points at another range is warned about | done |
| Insert and delete rows and columns | yes | Insert above/below or left/right and delete from the selected range; cells, merged ranges, sizes, the freeze line and formula references follow the edit, and a deleted reference becomes `#REF!` | done |
| Charts (bar, from a column) | yes | | done |
| Export to PDF (paginated, header row repeated) | yes | Merged titles span their columns; custom widths, heights, fills, borders and alignment are honoured | done |
| Print via the system print service | yes | | done |
| Content a save removes (charts, images, hidden rows/columns, validation, filters, protection) | no | Warned before the first save | done |
| Pivot tables | yes | | — |
| Macros | yes | | — |

## PDF — M3

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Viewer (page render, page list) | yes | | done |
| Annotate (highlight, ink, notes) | yes | | done |
| Merge / split / rotate / reorder | yes (paid) | | done |
| Fill forms | yes (paid) | | — |
| Digital signature (handwritten ink — drawn, not cryptographic) | yes (paid) | | done |
| True redaction (content removed, not black boxes) | partial (paid) | | done |
| Export progress + cancel, output verified non-empty and `%PDF` | no | | done |
| Marks rotate with their page; mark list with remove | no | | done |
| Print via the system print service (current page order, marks baked in) | yes | | done |

## Slides — M4

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.pptx` text (preserve other package parts) | yes | | done |
| Slide strip + text editing | yes | | done |
| Print via the system print service | yes | | done |
| Shape/image editing on slides | yes | | planned — text edits preserve other parts |
| Present mode + speaker notes | yes | | done |
| Transitions / animations | yes | | — |

## Privacy & freedom (the "beat WPS" list) — M5

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Encrypted vault + biometric lock | no | | planned |
| Metadata scrub on share/export | no | Implemented by construction — writers rebuild every package from the model, so no `docProps/`, `core.xml`, or `app.xml` parts are ever written (verified by `MetadataScrubTest`) | done |
| Offline OCR (scan → text) | cloud, limited | | planned |
| Handwriting / ink notes | premium | | planned |
| Templates (create from built-in templates) | yes, account-gated | | done |
| License-safe font pack | yes, account-gated | | planned |
| Works with radios off | no | | — (by construction) |
| Reproducible source builds (F-Droid) | no | | planned |

## Non-goals (for now)

Collaboration, cloud sync, macros/VBA, pivot tables, mail merge, equation editor.
Each may come back later, one at a time, with a decision recorded.
