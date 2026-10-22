# ZiyOffice feature matrix vs WPS Office

A checklist that gets updated as things land. Status values: `done`, `in progress`, `planned`, `not planned`. Only mark `done` what a user can actually do on a phone today.

Content preservation: Saving writes the edited parts back into the package the file came from instead of building a new one from ZiyOffice's own model. A paragraph the user did not touch comes out of a save byte for byte. A paragraph they did touch keeps its properties, hyperlinks, comment anchors, and drawings, and only the text and formatting they changed get rewritten. Everything the editor cannot edit (comments, footnotes, tracked changes, equations, text boxes, section breaks, charts, pivot tables, conditional formatting, filters, hidden rows and columns) stays in the file exactly as it was. The editors list that content when you open the file. Nothing gets removed by a save. See `DocxPreservationTest` and `XlsxPreservationTest` for the enforced contract.

## Foundations (M0)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Opens documents from user folders (SAF) | yes | yes | done |
| Opens from another app ("Open with" / "Share to") | yes | yes | done |
| Recent files list, local only | yes | yes | done |
| No system permissions | no (dozens) | yes | done |
| No ads / no subscription | no | yes | done |
| No account required | no | yes | done |
| Can't use the network at all | no | yes | done |
| Dark mode | yes | yes | done |
| Home screen, Settings, About (privacy receipt) | yes | yes | done |
| Save-safety: unsaved-edit prompt, crash recovery draft | yes | yes | done |
| Accessible controls (TalkBack labels/state, 48 dp targets) | partial | yes | done |

## Documents (M1)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.docx` | yes | yes | done |
| Bold/italic/underline/strike, colors, sizes | yes | yes | done |
| Headings, lists, alignment, line spacing | yes | yes | done |
| Paragraph indent and spacing | yes | Increase or decrease indent, set tight, normal, or relaxed spacing; survives a save | done |
| Images (insert/resize) | yes | yes | done |
| Tables (insert, add/remove rows/cols, merged cells) | yes | Horizontal and vertical merges are read, drawn, and kept by a save | done |
| Find and replace | yes | Find next match, replace one, or replace all; search skips raw formula references | done |
| Copy and paste cells | yes | Copy range as tab-separated text; copied formula stays a formula | done |
| Undo/redo, word count | yes | yes | done |
| Autosave and crash recovery | yes | yes | done |
| Export to PDF (matches screen exactly) | yes | yes | done |
| Print via the system print service | yes | yes | done |
| Page numbers | yes | Real page number footer field, filled per page | done |
| Headers and footers | yes | Shown above and below body, edited in place, kept on save | done |
| Mobile reflow reading mode | yes | Text wraps to phone width without horizontal scrolling | planned |
| Page setup (margins, orientation, paper size) | yes | Margin presets, portrait/landscape toggle, standard paper sizes | planned |
| Superscript and subscript | yes | Character baseline shift controls | planned |
| Footnotes and endnotes | yes | Kept as they are by save; editing and inserting planned | planned |
| Comments and track changes review | yes | Kept on save; panel to read, reply, and resolve planned | planned |
| Equations, SmartArt | partial | Kept byte for byte on save, not editable | not planned |
| Mail merge | yes | Kept byte for byte on save | not planned |

## Sheets (M2)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.xlsx`, CSV import/export | yes | yes | done |
| Grid with touch and keyboard editing | yes | yes | done |
| Formula engine (SUM, IF, VLOOKUP, and ~70 functions) | yes | Aggregates, maths, text, logic, conditional counting, lookup, statistics, dates | done |
| Number/date formatting | yes | Standard numeric and date formatting | done |
| Freeze panes, sorting | yes | Sort by one column or two columns with tie-breaking | done |
| Named ranges | yes | Defined names read, written, and usable in formulas | done |
| Merged cells | yes | Range select, merge/unmerge, read and write `.xlsx` | done |
| Column widths and row heights | yes | Resizes selected columns or rows; reads and writes `<col>` widths and row `ht` | done |
| Cell borders | yes | Top, bottom, left, right, all edges, box borders | done |
| Dropdown lists in cells | yes | Range choices with tap selection; plain lists survive a save | done |
| Insert and delete rows and columns | yes | Adjusts cells, merged ranges, sizes, freeze lines, and formula references | done |
| Charts (bar chart from a column) | yes | yes | done |
| Cell notes | yes | Pin notes to cells; compatible with standard spreadsheet tools | done |
| Export to PDF (paginated, header row repeated) | yes | Custom widths, heights, fills, borders, and alignments honoured | done |
| Print via the system print service | yes | yes | done |
| Content kept intact on save (charts, images, pivot tables, filters) | no | Kept as they are by save; listed on open | done |
| Multi-sheet workbook tabs | yes | Add, delete, rename, reorder, duplicate sheets | planned |
| AutoFilter dropdowns on column headers | yes | Filter by value or text criteria | planned |
| Text wrap in cells (`wrap_text`) | yes | Multi-line cell text wrapping toggle | done |
| Extended number format picker | yes | Explicit currency, accounting, percentage decimal places, scientific | planned |
| Line and pie charts | yes | Plot line trends and circular proportions from selected ranges | planned |
| Pivot tables | yes | Kept intact on save | not planned |
| Macros / VBA | yes | Kept intact on save | not planned |

## PDF Viewer and Editor (M3)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Viewer (page render, page list) | yes | yes | done |
| Annotate (highlight, ink, notes) | yes | yes | done |
| Merge / split / rotate / reorder | yes (paid) | yes | done |
| Page labels and text stamps | yes | Stamp text on a page, number pages on export or print | done |
| Fill forms (AcroForms) | yes (paid) | Interactive text boxes, checkboxes, list choices written to file fields | done |
| Digital signature | yes (paid) | Handwritten ink signature placement | done |
| Redaction | partial (paid) | Permanent removal of underlying vector and text content | done |
| Export progress with cancel | no | Output verified non-empty and valid `%PDF` | done |
| Marks rotate with page; mark list with remove | no | yes | done |
| Print via the system print service | yes | Current page order, marks baked in | done |
| Visual page sorter | yes (paid) | Drag-and-drop page thumbnail reorder and single-page delete | planned |
| PDF compression | yes (paid) | Compress embedded image streams and unused objects | planned |
| Password protection and decrypt | yes (paid) | Set open password on export; unlock encrypted PDFs | planned |
| Watermarks | yes (paid) | Add text or image watermark; remove existing watermarks | planned |
| Direct text edit in digital PDFs | yes (paid) | Modify existing text runs in born-digital PDFs | planned |

## PDF Conversions (M3.5)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Export DOCX, XLSX, PPTX to PDF | yes | yes | done |
| PDF to Word (`.docx`) | yes (paid) | Offline structural text, table, and image extraction to DOCX | planned |
| PDF to Excel (`.xlsx`) | yes (paid) | Extract detected tabular grids to structured worksheet cells | planned |
| PDF to PowerPoint (`.pptx`) | yes (paid) | Convert pages to presentation slides | planned |
| PDF to Images (JPG/PNG) | yes (paid) | Render pages as standalone image files | planned |
| Image to PDF | yes (paid) | Compile one or more pictures into a standard PDF | done |

## Slides (M4)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Open/edit/save `.pptx` text | yes | Preserves other package parts byte for byte | done |
| Slide strip and text editing | yes | Layout, images, animations kept intact | done |
| Print via the system print service | yes | yes | done |
| Shape and image editing on slides | yes | Insert pictures or shapes; retains original file bytes | done |
| Present mode and speaker notes | yes | yes | done |
| Transitions / animations | yes | Per-slide transition preserved on save | done |
| Slide layout picker for new slides | yes | Templates for Title, Title and Content, Two Content, Blank | planned |
| Duplicate and delete slides in UI | yes | Manage deck slides directly from the thumbnail strip | planned |
| Slide sorter view | yes | Full-screen grid view to reorder and organize slides | planned |
| Presenter ink and laser pointer | yes | Freehand drawing and pointer during full-screen presentation | planned |

## Scanner and Camera Tools (M5)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Offline OCR (picture to text) | cloud (limited) | Bundled Tesseract engine with English model, runs offline | done |
| Camera document scanner | yes | Camera capture with automatic edge detection and crop | planned |
| Perspective deskew filter | yes | Rectify skewed document photographs to flat rectangles | planned |
| B&W document enhancement filter | yes | Remove shadows, clean background, sharpen text contrast | planned |
| Multi-page batch scan to PDF | yes (paid) | Capture multiple photos and compile into a single PDF | planned |
| ID Card 2-in-1 scan mode | yes (paid) | Composite front and back card photos onto a single A4 page | planned |
| Paper to spreadsheet | yes (paid) | OCR table grid directly into an `.xlsx` workbook | planned |

## Shell and Utilities (M6)

| Feature | WPS | ZiyOffice | Status |
|---|---|---|---|
| Encrypted vault with biometric lock | no | Local AES-GCM encrypted vault, keys never leave device | done |
| Metadata scrub on share and export | no | Strip `docProps/` on new files; preserve author's own on edit | done |
| Built-in templates | yes (gated) | Template documents without accounts or subscriptions | done |
| Starred / favorites document list | yes | Local favorites tab on Home screen | planned |
| ZIP package inspector | yes | View and extract files inside archive packages | planned |
| License-safe font pack | yes (gated) | Bundled open-source fonts for consistent rendering | planned |
| Reproducible source builds (F-Droid) | no | Verified reproducible build setup | planned |

## Non-goals (for now)

Collaboration, cloud sync, macros/VBA, pivot tables, mail merge, equation editor. Each one may come back later with an explicit architectural decision recorded.
