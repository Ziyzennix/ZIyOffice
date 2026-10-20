# ZIyOffice

An Android office suite for documents, spreadsheets, slides and PDFs. It works offline,
needs no account, and has no network permission, so it cannot send your files anywhere.

Free and open source under GPL-3.0. No subscription, no ads, no tracking.

Version 0.5, early development. Expect bugs. If something breaks,
[open an issue](https://github.com/Ziyzennix/ZIyOffice/issues).

## What it does

| Format | Open | Edit | Save | Export | Print |
|---|---|---|---|---|---|
| `.docx` | yes | text, formatting, tables, images | yes | PDF | yes |
| `.xlsx` / `.csv` | yes | cells, formulas, formatting, sorting | yes | PDF, CSV | yes |
| `.pptx` | yes | slide text, add/remove/reorder, notes | yes | PDF (text only) | yes |
| `.pdf` | yes | highlight, ink, notes, rotate, reorder, delete, merge, split | yes | PDF | yes |

**Documents.** Bold, italic, underline, strikethrough, font size and colour, headings,
lists, alignment, line spacing, page breaks, tables you can add rows and columns to,
inserted pictures, find and replace, word count, undo and redo, and export to PDF.

**Spreadsheets.** A grid you can drive by touch or keyboard, a formula engine with
arithmetic, cell references, ranges, `SUM`, `AVERAGE`, `COUNT`, `MIN`, `MAX`, `IF` and
`VLOOKUP`, number and date formats, frozen rows, sorting, charts from a column, and CSV
import and export.

**Slides.** A thumbnail strip, per-slide text editing, add, delete and reorder, speaker
notes, a present mode, and PDF export. Text only; images and shapes on a slide are
preserved untouched but cannot be edited yet.

**PDF.** Page rendering, highlight, freehand ink, text notes, a drawn signature, rotate,
reorder, delete, merge pages in from another file, split out a page range, and redaction
that removes the content underneath rather than covering it.

Everything above also prints through Android's print service, which needs no permission.

## Privacy

Four guarantees, each checked by the build:

1. **No network permission.** The manifest declares no `android.permission.INTERNET`, so
   Android blocks network access. A CI check fails the build if a permission is added
   without review.
2. **No ads, analytics or crash reporting.** A CI check fails the build if such a
   dependency is declared.
3. **Local-only storage.** Files stay in the folders you pick with Android's file picker.
   ZIyOffice stores a list of recent files and local copies of unsaved edits.
4. **Free forever.** GPL-3.0 means nobody can take this, close it, and sell it back to you.

The About screen lists the permissions the installed app declares.

## Known limitations

Saving rebuilds each file from ZIyOffice's own document model, so parts it does not model
are dropped: headers, comments, tracked changes, charts, merged cells, pivot tables and
similar. When you open such a file, the app lists what a save will remove before the first
write. Use "Save as copy" to keep the original untouched.

Not implemented yet: encrypted vault, OCR, handwriting notes, slide shapes and images, PDF
form filling, headers and footers, macros, pivot tables.

## Install

Download the APK from [Releases](https://github.com/Ziyzennix/ZIyOffice/releases) and open
it on your phone. Android asks you to allow installing from this source. Requires Android
8.0 (API 26) or newer. The release APK is about 1.6 MB.

## How it is built

Everything sits on Android platform APIs. There is no Apache POI, no PDFium, no third-party
format library, and no analytics SDK. The dependencies are Compose, Activity Compose, and
Kotlin's test library.

| Layer | What it does |
|---|---|
| `core/ooxml` | DOCX, XLSX and PPTX readers and writers, built on `java.util.zip` and the XML pull parser |
| `core/model` | Document, workbook and deck models in plain Kotlin, with no Android imports |
| `core/design` | Compose theme and colours |
| `app` | Screens, file access through the Storage Access Framework, the PDF engine, and exporters |

The format layer is a documented subset rather than a claim of full OOXML support. It reads
what the editors can work with, and `ContentAudit` reports what it skipped so the app can
warn you before a save discards anything.

The PDF writer renders pages with `PdfRenderer` and rebuilds them with `PdfDocument`. That
trade is what makes redaction real: a redacted area is not in the output bytes.

## Testing

243 unit tests run on the JVM in CI, covering the document model, every format reader and
writer, formula evaluation, number formatting, CSV, sheet sorting, page-range parsing, the
save and exit decisions, and the accessibility copy. Around 17,600 lines of Kotlin.

Format tests build packages in memory, read them back, and assert the round trip. The
content-audit test goes further: it audits a package, saves it through the real reader and
writer, and audits again to confirm the warning was accurate.

There is no instrumented test suite yet, so device behaviour is checked by hand. Version 0.5
was checked on a 360 dp Android 14 phone: content-loss warnings, sheet PDF export across two
pages, print preview, and the editors at their narrowest width.

## Build

Requirements: JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # run unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root. Copy
`keystore.properties.example` and fill in your own keystore. Without it, `assembleRelease`
fails and says why.

CI runs the tests, the debug build, Android lint, and two guardrail scripts: one rejects any
declared permission that has not been reviewed, the other rejects ads, analytics and
tracking dependencies.

## Project layout

| Path | Purpose |
|---|---|
| `app/` | Screens, storage access, PDF engine, exporters |
| `core/design/` | Compose theme and colors |
| `core/model/` | Document model, plain Kotlin, unit-tested |
| `core/ooxml/` | DOCX / XLSX / PPTX readers and writers |
| `scripts/ci/` | Privacy guardrails |
| `docs/` | Threat model, architecture decisions, feature matrix |

`FEATURES.md` lists what works today against WPS Office. `docs/threat-model.md` says what
the privacy claims do and do not cover. `docs/adr/` records the decisions behind the format
layer and the toolchain.

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

Open an issue at https://github.com/Ziyzennix/ZIyOffice/issues.

## Contributing

See `CONTRIBUTING.md`. Never add network capability, and never copy code, assets, text or
layouts from proprietary office apps.

## Support

If this is useful to you, you can buy me a coffee at
[ko-fi.com/ziyzenix](https://ko-fi.com/ziyzenix). Donations do not unlock anything; every
feature is already free.

## License

GNU GPL-3.0-or-later. See `LICENSE`.
