# WPS Office feature inventory and gap analysis

This document lists the features found in WPS Office for Android (package `cn.wps.moffice_eng`), based on official product specifications, documentation, and the application's mobile interface. It compares them against ZiyOffice and outlines what is needed to reach parity without accounts, ads, or network requirements.

## Sources

- WPS Office Android product overview: https://www.wps.com/office/android/
- WPS PDF Android specification: https://www.wps.com/feature/pdf-editor-for-android/
- WPS PDF tools reference: https://pdf.wps.com/en/
- WPS Premium feature and pricing disclosure: https://www.wps.com/academy/how-to-download-wps-office-premium/about-wps/1886749/
- WPS Android package permissions and manifest analysis (`dumpsys package cn.wps.moffice_eng`)

## 1. Document processing (Writer)

### Core editing and formatting
- Character styling: bold, italic, underline, strikethrough, superscript, subscript, font color, highlight color, font size picker, font family selection.
- Paragraph formatting: left, center, right, and justified alignment; line spacing options (1.0, 1.15, 1.5, 2.0, custom); first-line and hanging indents; bulleted and numbered lists.
- View modes: Page layout view, mobile reflow view (adjusts body text to screen width for phones), dark mode reading.
- Insert items: photos (from camera or gallery), tables, basic shapes, text boxes, page breaks, hyperlinks, bookmarks, footnotes, endnotes, date and time fields.
- Table editing: row and column insertion/deletion, cell merging and splitting, table borders and shading, column width adjustment.
- Page setup: margin presets (normal, narrow, wide) and custom margins; paper orientation (portrait, landscape); standard page sizes (A4, Letter, Legal, A3, Executive); page background color; watermarks.
- Document review: word and character counters, comments list and editor, revision marks (track changes).

### Parity status
- ZiyOffice currently supports: basic character formatting, headings, lists, paragraph indent and line spacing, image insertion and resizing, table structure and cell merging, headers and footers with page number fields, find and replace, undo/redo, PDF export, system printing.
- Gaps in ZiyOffice: mobile reflow reading mode, page margin and orientation setup dialog, footnote/endnote preservation and creation, comment authoring and review panel, superscript/subscript controls, track changes viewer.

## 2. Spreadsheets

### Calculation and data structures
- Grid layout: multi-cell selection, freeze panes (top row, first column, arbitrary split), column width and row height resizing, row and column insertion and deletion, row and column hiding.
- Multi-sheet workbooks: tab bar showing sheets; add, rename, duplicate, reorder, delete, hide, and unhide sheets; tab color styling.
- Calculation engine: support for standard arithmetic, ranges, named ranges, cross-sheet references, and categorized functions (mathematical, statistical, logical, text, date and time, financial, lookup).
- Number formats: general, number with decimal places, currency symbols, accounting format, short/long dates, times, percentages, fractions, scientific notation, plain text.
- Cell design: cell background fills, border styles (thin, thick, double, dashed, colored borders), horizontal alignment, vertical alignment, text wrap (`wrap_text`), merge and center.
- Data tools: auto-filter dropdowns on header rows, single and multi-column sorting, text find and replace, data validation dropdown choices.
- Insert objects: charts (bar, column, line, pie, area, scatter), cell notes and threaded comments, shapes, floating pictures.

### Parity status
- ZiyOffice currently supports: XLSX round-trip with preservation, 70 formula functions, single and two-column sorting, freeze panes, named ranges, merged cells, custom column widths and row heights, cell borders, in-cell dropdown choices, row and column insertion/deletion, cell notes, bar charts, paginated PDF export.
- Gaps in ZiyOffice: multi-sheet tab navigation and management, auto-filter dropdown menus on column headers, text wrapping toggle, full number format selection picker (currency, accounting, percentage decimals), line and pie chart rendering.

## 3. Presentations (Slides)

### Deck authoring and delivery
- Slide organization: slide thumbnail strip; slide creation with layout templates (Title, Title and Content, Section Header, Two Content, Comparison, Blank); slide reordering, duplication, deletion, hiding.
- Slide sorter view: full-screen grid view to organize and batch-manage slides.
- Slide objects: text frames with multi-column text, shapes, pictures, tables, lines, connectors. Object z-order arrangement (bring to front, send to back), alignment guides, rotation.
- Slide show mode: full-screen playback, presenter view with speaker notes, hardware/software laser pointer pointer, live ink drawing and highlighter during presentation, slide transition effects with duration settings.

### Parity status
- ZiyOffice currently supports: PPTX package reading and preservation-first saving, slide thumbnail strip, slide text editing, speaker notes, presentation playback mode, basic slide transitions, shape and picture preservation.
- Gaps in ZiyOffice: slide layout selection for new slides, slide duplication and deletion in UI, slide sorter grid view, on-screen ink drawing and pointer tools during presentation playback, interactive shape insertion and manipulation.

## 4. PDF tools (WPS commercial focus)

PDF tooling represents the primary subscription driver in WPS Office on mobile. The tools divide into page operations, file format conversions, and content modification.

### Page and document operations
- Merge: combine multiple PDF files into one file with custom file ordering.
- Split: extract specific page ranges or break every page into a separate file.
- Page organizer: rotate individual or selected pages, reorder pages by dragging thumbnails, delete unwanted pages, insert pages from another PDF or blank pages.
- Compression: re-encode images and compress object streams to reduce file size.
- Document security: set password encryption to open the file; apply permission restrictions; decrypt authorized files.
- Watermarks: add text or image watermarks with angle and opacity controls; remove existing watermarks.

### Content editing and annotation
- Text editing: direct editing of existing text runs in digital PDFs.
- Annotations: highlight, underline, strikethrough, squiggly line, freehand drawing with pen and highlighter, sticky notes, geometric shapes, stamps (standard stamps like Approved/Confidential, plus custom text stamps).
- Forms and signatures: fill AcroForm fields (text boxes, checkboxes, radio options, dropdowns); place saved handwritten signatures; flatten form fields and annotations.
- Redaction: permanent removal of underlying content and vector paths across selected areas.

### Conversion tools
- PDF to editable formats: PDF to DOCX, PDF to XLSX, PDF to PPTX.
- Image conversions: PDF to individual JPG/PNG images, Long Picture export (stitching pages vertically).
- Office formats to PDF: DOCX to PDF, XLSX to PDF, PPTX to PDF, Image to PDF (single or multi-page compilation).

### Parity status
- ZiyOffice currently supports: PDF page rendering, annotation drawing (highlight, ink, notes), page rotation/merge/split, text stamps, AcroForm filling, true redaction, export progress handling, printing.
- Gaps in ZiyOffice: PDF to DOCX text and layout converter, Image-to-PDF multi-picture compiler, PDF page deletion and visual drag-and-drop reorder screen, PDF compression utility, password protection setup and removal.

## 5. Scanner and camera tools

WPS incorporates a document scanner directly into the mobile application to digitize physical paperwork.

### Capture and processing
- Camera capture: document boundary detection, perspective correction (deskewing angled shots), shadow removal, and contrast enhancement filters (magic color, black and white document mode, grayscale).
- Modes: single-page document, multi-page batch scanning, two-sided ID card composite mode (front and back placed together on an A4 sheet).
- Text extraction: Optical Character Recognition (OCR) converting captured images to searchable text, plain TXT files, or formatted DOCX documents.
- Paper to spreadsheet: table recognition converting photographed tabular data into structured XLSX rows and columns.

### Parity status
- ZiyOffice currently supports: working OCR engine via bundled offline Tesseract (`tess-two` with English trained data), direct conversion of plain OCR text into paragraph blocks.
- Gaps in ZiyOffice: camera capture screen with edge detection crop, perspective rectification filter, multi-page scan batching, ID card 2-in-1 layout generator.

## 6. Utilities and shell

### Navigation and file handling
- Home view: categorized tiles for creating Docs, Sheets, Slides, and PDFs, plus quick action tools.
- Recent files and favorites: recent document history stored on-device; favorite/starred documents list.
- Storage access: standard Android Storage Access Framework (SAF) document provider integration without requiring broad file system permissions (`MANAGE_EXTERNAL_STORAGE`).
- Vault: encrypted local container for sensitive documents protected by biometrics or a passphrase.
- Package utilities: viewing and extracting documents inside ZIP archives.

### Parity status
- ZiyOffice currently supports: clean SAF file access, zero network permissions, dark mode, accessible controls, local recent files list, AES-GCM encrypted vault with biometric lock, metadata scrubbing on export.
- Gaps in ZiyOffice: starred/favorites list, quick-action converter tools on Home screen, ZIP archive browser.
