# ZiyOffice

An Android office suite for documents, spreadsheets, slides and PDFs. It works offline, needs no account, and has no network permission, so it cannot send your files anywhere.

Free and open source under GPL-3.0. No subscription, no ads, no tracking.

Version 0.5 is early development — expect bugs. If something breaks,
[open an issue](https://github.com/Ziyzennix/ZiyOffice/issues).

[![](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/ziyzenix)   

## What it does

| Format | Open | Edit | Save | Export | Print |
|---|---|---|---|---|---|
| `.docx` | yes | text, formatting, tables, images | yes | PDF | yes |
| `.xlsx` / `.csv` | yes | cells, formulas, formatting, sorting | yes | PDF, CSV | yes |
| `.pptx` | yes | slide text, add/remove/reorder, notes | yes | PDF (text only) | yes |
| `.pdf` | yes | highlight, ink, notes, rotate, reorder, delete, merge, split | yes | PDF | yes |

ZiyOffice also appears in Android's "Open with" and share sheets for these formats, so a document attached to a mail or chat message opens straight in the editor. A file shared that way usually arrives with a one-shot grant that Android will not let an app keep, so it opens for the session without joining the recent list — "Save as copy" is how you keep the result.

Documents support bold, italic, underline and strikethrough, font size and colour, headings, lists, alignment, line spacing, paragraph indent and spacing, page breaks, tables with addable rows and columns, inserted pictures, find and replace, word count, undo and redo, and PDF export. Spreadsheets support arithmetic, cell references, ranges and about 70 functions — `SUM`, `AVERAGE`, `COUNT`, `COUNTIF`, `SUMIF`, `MIN`, `MAX`, `MEDIAN`, `IF`, `IFERROR`, `AND`, `OR`, `VLOOKUP`, `INDEX`/`MATCH`, `CHOOSE`, `ROUND`, `MOD`, `ABS`, `SQRT`, `POWER`, `LEN`, `LEFT`, `RIGHT`, `MID`, `TRIM`, `UPPER`, `PROPER`, `CONCATENATE`, `SUBSTITUTE`, `REPLACE`, `FIND`, `TEXT`, `VALUE`, `DATE`, `TODAY` and more — plus number and date formats, frozen rows, sorting, merged cells with range selection, adjustable column widths and row heights, cell borders, dropdown lists, inserting and deleting rows and columns, find and replace, copy and paste, charts from a column, and CSV import and export. Slides keep slide text, adding, deleting and reordering slides, speaker notes, a present mode, and PDF export; images and shapes on a slide are preserved untouched but cannot be edited yet. PDF work includes highlighting, freehand ink, text notes, a drawn signature, rotation, reordering and deletion of pages, merging pages from another file, page-range split, and redaction that removes the content underneath rather than covering it. Printing goes through Android's print service with no permission needed.

## Privacy

Four guarantees, each checked by the build:

1. **No network permission.** The manifest declares no `android.permission.INTERNET`, so Android blocks network access. A CI check fails the build if a permission is added without review.
2. **No ads, analytics or crash reporting.** A CI check fails the build if such a dependency is declared.
3. **Local-only storage.** Files stay in the folders you pick with Android's file picker. ZiyOffice stores a list of recent files and local copies of unsaved edits.
4. **Free forever.** GPL-3.0 means nobody can take this, close it, and sell it back to you.

The About screen lists the permissions the installed app declares.

## Known limitations

Saving rebuilds each file from ZiyOffice's own document model, so parts it does not model are dropped: comments, tracked changes, charts, hidden rows and columns, pivot tables and similar. When you open such a file, the app lists what a save will remove before the first write. Use "Save as copy" to keep the original untouched.

Not implemented yet: encrypted vault, OCR, handwriting notes, slide shapes and images, PDF form filling, macros, pivot tables.

## Install

Download the APK from [Releases](https://github.com/Ziyzennix/ZiyOffice/releases) and open it on your phone. Android asks you to allow installing from this source. Requires Android 8.0 (API 26) or newer. The release APK is about 1.8 MB.

## How it is built

Everything sits on Android platform APIs. There is no Apache POI, no PDFium, no third-party format library, and no analytics SDK. The dependencies are Compose, Activity Compose, and Kotlin's test library.

| Layer | Purpose |
|---|---|
| `core/ooxml` | DOCX, XLSX and PPTX readers and writers, built on `java.util.zip` and the XML pull parser |
| `core/model` | Document, workbook and deck models in plain Kotlin, with no Android imports |
| `core/design` | Compose theme and colors |
| `app` | Screens, file access through the Storage Access Framework, the PDF engine, and exporters |

The format layer is a documented subset rather than a claim of full OOXML support. It reads what the editors can work with, and `ContentAudit` reports what it skipped so the app can warn you before a save discards anything.

The PDF writer renders pages with `PdfRenderer` and rebuilds them with `PdfDocument`. That trade is what makes redaction real: a redacted area is not in the output bytes.

## Testing

308 unit tests run on the JVM in CI, covering the document model, every format reader and writer, formula evaluation, number formatting, CSV, sheet sorting, row and column edits, borders, dropdown lists, find and replace, clipboard, page-range parsing, the save and exit decisions, and the accessibility copy. Around 24,000 lines of Kotlin, 19,000 of them app and library code.

Format tests build packages in memory, read them back, and assert the round trip. The content-audit test audits a package, saves it through the real reader and writer, and audits again to confirm the warning was accurate.

There is no instrumented test suite yet, so device behaviour is checked by hand. Version 0.5 was checked on a 360 dp Android 14 phone: content-loss warnings, sheet PDF export across two pages, print preview, the editors at their narrowest width, and inserting and deleting sheet rows and columns with their formulas, merges and sizes following along.

## Build

Requirements: JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # run unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root — copy `keystore.properties.example` and fill in your own keystore. Without it, `assembleRelease` fails and says why.

CI runs the tests, the debug build, Android lint, and two guardrail scripts: one rejects any declared permission that has not been reviewed, the other rejects ads, analytics and tracking dependencies.

## Project layout

| Path | Purpose |
|---|---|
| `app/` | Screens, storage access, PDF engine, exporters |
| `core/design/` | Compose theme and colors |
| `core/model/` | Document model, plain Kotlin, unit-tested |
| `core/ooxml/` | DOCX / XLSX / PPTX readers and writers |
| `scripts/ci/` | Privacy guardrails |
| `docs/` | Threat model, architecture decisions, feature matrix |

`FEATURES.md` lists what works today against WPS Office. `docs/threat-model.md` says what the privacy claims do and do not cover. `docs/adr/` records the decisions behind the format layer and the toolchain.

## Roadmap

0.5 is the current release. Next:

- encrypted vault with biometric lock
- offline OCR from images you pick
- handwriting notes
- slide shapes and images
- PDF form filling
- F-Droid reproducible builds

## Reporting bugs

Include:

- what you did and what happened instead
- the file type you were working on (not the file itself, unless you want to share it)
- your Android version and phone model

Open an issue at https://github.com/Ziyzennix/ZiyOffice/issues.

## Contributing

See `CONTRIBUTING.md`. Never add network capability, and never copy code, assets, text or layouts from proprietary office apps.

## License

GNU GPL-3.0-or-later. See `LICENSE`.
