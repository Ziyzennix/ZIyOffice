# ZiyOffice

An office suite for Android. It opens, edits and prints documents, spreadsheets,
slides and PDFs — with no account, no ads, no subscription, and not a single
permission.

## why this exists

The big office apps for Android are free only in the sense that you pay them
later. They want an account before you can use the feature you opened the app
for, they ask for storage, camera and notification permissions they do not
need, they show ads or premium nudges, and the moment a feature wants the cloud,
your documents sit on somebody else's server.

ZiyOffice is the other trade:

- **it works offline.** the manifest has no `android.permission.INTERNET`, so
  Android blocks network access for the app. not "we don't phone home" —
  "the app cannot".
- **no account, no ads, no analytics.** no ad SDK, no crash reporter, no
  telemetry. the build fails if one shows up in the dependencies.
- **no permissions at all.** not "minimal". the About screen shows what the
  installed APK actually declares, and the list is empty.
- **your files stay in your folders.** you pick them with Android's file picker.
  the app never goes looking through your storage.
- **free forever.** GPL-3.0 means nobody can take this, close it, and sell it
  back to you.

None of that is a promise in a policy file. The build checks it: a CI script
fails if a permission appears without review, another fails if networking code
is written, and a third fails if a tracking dependency is declared.

## how it works

### the files

ZiyOffice shows up in Android's "Open with" and share sheets, so a document
attached to a mail or chat message opens straight in the editor. You always pick
the file through the system picker, and the app keeps a grant for it — nothing
else on the device is readable.

Unsaved work is kept as a local draft, so a crash or a killed process does not
take your typing with it. Saving rebuilds the file from ZiyOffice's own model,
which means parts the model does not know about (comments, tracked changes,
embedded charts, hidden rows) are dropped. The app tells you that *before* the
first write, and "Save as copy" always leaves the original alone.

### documents

Bold, italic, underline, strikethrough, font size and colour, headings, lists,
alignment, line spacing, paragraph indent and spacing, page breaks, tables with
addable rows and columns, pictures, find and replace, word count, undo/redo,
headers and footers. Exports to PDF and prints through Android's own print
service.

### spreadsheets

Cells, about 70 formula functions (`SUM`, `AVERAGE`, `COUNTIF`, `SUMIF`, `IF`,
`IFERROR`, `VLOOKUP`, `INDEX`/`MATCH`, `ROUND`, `TEXT`, `LEFT`, `MID`, `DATE`
and the rest), number and date formats, frozen rows, sorting, merged cells,
column widths and row heights, cell borders, dropdown lists, inserting and
deleting rows and columns, find and replace, copy and paste, bar charts, and
CSV import and export.

### slides

Slide text, adding, deleting and reordering slides, speaker notes, a present
mode, PDF export. Images, shapes and animation in a slide are preserved
untouched by an edit, but cannot be edited yet.

### pdf

Highlight, freehand ink, text notes, a drawn signature, text stamped on a
page, page numbers on a saved copy, rotate, reorder, delete, merge in pages
from another file, split by page range, and redaction that removes the content
underneath instead of covering it. Printing goes through the system print
service, so no permission is involved.

## what it cannot do yet

Encrypted vault, OCR of a scanned page, handwriting notes, images and shapes on
slides, PDF form filling, macros, pivot tables, and `.odt`/`.ods`/`.odp` files.
`FEATURES.md` has the full list against WPS Office, and the roadmap below says
what is next.

## install

Grab the APK from [Releases](https://github.com/Ziyzennix/ZiyOffice/releases) and
open it on your phone; Android asks you to allow installing from this source.
Needs Android 8.0 (API 26) or newer. The APK is about 1.8 MB.

## build and test

JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # run the unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root — copy
`keystore.properties.example` and fill in your own keystore. Without it
`assembleRelease` fails and says why.

310 unit tests run on the JVM in CI. Format tests build a package in memory,
read it back and assert the round trip; the content-audit test audits a file,
saves it through the real reader and writer, and audits again to prove the
warning was honest. There is no instrumented suite yet, so device behaviour is
checked by hand.

Everything sits on Android platform APIs: no Apache POI, no PDFium, no
third-party format library. The dependencies are Compose, Activity Compose and
Kotlin's test library.

| Layer | Purpose |
|---|---|
| `core/model` | Document, workbook and deck models in plain Kotlin, unit-tested on the JVM |
| `core/ooxml` | DOCX, XLSX and PPTX readers and writers, on `java.util.zip` and the XML pull parser |
| `core/design` | Compose theme and colours |
| `app` | Screens, file access through the Storage Access Framework, the PDF engine, exporters |
| `scripts/ci` | The privacy guardrails |

`docs/threat-model.md` says what the privacy claims do and do not cover,
`docs/adr/` records the decisions behind the format layer, and
`FEATURES.md` lists what works today.

## roadmap

0.5 is the current release. Next, by area:

**Sheets** - conditional formatting, cell notes, named ranges, sorting by more
than one column, filters, charts that are not bar charts.

**Documents** - page numbers, footnotes, a table of contents, hyperlinks, spell
check.

**Slides** - images and shapes on a slide, title/content layouts, transitions
and animations.

**PDF** - filling forms, editing text on the page, and offline OCR of a picture
you pick, so a scanned document becomes text you can search and copy.

**Everywhere** - copy and paste between apps, reading and writing
`.odt`/`.ods`/`.odp`, an encrypted vault with a biometric lock, handwriting
notes, F-Droid reproducible builds.

## reporting bugs

Include what you did and what happened instead, the file type you were working
on (not the file itself, unless you want to share it), and your Android version
and phone model.

Open an issue at https://github.com/Ziyzennix/ZiyOffice/issues.

## contributing

See `CONTRIBUTING.md`. Never add network capability, and never copy code,
assets, text or layouts from proprietary office apps.

## license

GNU GPL-3.0-or-later. See `LICENSE`.

[![](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/ziyzenix)
