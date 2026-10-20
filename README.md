# ZiyOffice

An office suite for Android. It opens, edits and prints documents, spreadsheets,
slides and PDFs — no account, no ads, no subscription, no permissions.

## why

The big office apps for Android are free only in the sense that you pay them
later. They want an account before you can use the feature you opened the app
for, they ask for storage and camera permissions they do not need, they show
ads or premium nudges, and the moment a feature wants the cloud, your documents
sit on somebody else's server.

ZiyOffice is the other trade:

- **it works offline.** no `android.permission.INTERNET` in the manifest, so
  Android blocks network access for the app. not "we don't phone home" —
  "the app cannot".
- **no account, no ads, no analytics.** the build fails if an ad SDK, a crash
  reporter or a tracking dependency shows up.
- **no permissions at all.** the About screen shows what the installed APK
  declares. the list is empty.
- **your files stay in your folders.** you pick them with Android's file picker.
  the app never scans your storage.
- **free forever.** GPL-3.0 means nobody can take this, close it, and sell it
  back to you.

CI enforces all of it: a script fails the build if a permission appears without
review, another if networking code is written, a third if a tracking dependency
is declared.

## how it works

You pick files through the system picker, and ZiyOffice shows up in Android's
"Open with" and share sheets. Unsaved work is kept as a local draft, so a crash
does not take your typing with it. Saving rebuilds the file from ZiyOffice's own
model, so parts the model does not know (comments, tracked changes, embedded
charts, hidden rows) are dropped — the app says so before the first write, and
"Save as copy" always leaves the original alone.

- **Documents** — text formatting, headings, lists, alignment, line spacing,
  indent and spacing, page breaks, tables, pictures, headers and footers, find
  and replace, PDF export, print.
- **Spreadsheets** — cells, about 70 formula functions, number and date
  formats, frozen rows, sorting, merged cells, column widths and row heights,
  cell borders, dropdown lists, inserting and deleting rows and columns, find
  and replace, copy and paste, charts, CSV.
- **Slides** — slide text, adding, deleting and reordering slides, speaker
  notes, a present mode, PDF export. Images and shapes survive an edit but
  cannot be edited yet.
- **PDF** — highlight, ink, notes, a drawn signature, text stamped on a page,
  page numbers, rotate, reorder, delete, merge, split, and redaction that
  removes the content underneath instead of covering it.

Printing goes through Android's own print service, so no permission is involved.

## not yet

Encrypted vault, OCR, handwriting notes, images and shapes on slides, PDF form
filling, macros, pivot tables, `.odt`/`.ods`/`.odp`. `FEATURES.md` has the full
list against WPS Office.

## install

APK from [Releases](https://github.com/Ziyzennix/ZiyOffice/releases), opened on
your phone. Android 8.0 (API 26) or newer. The APK is about 1.8 MB.

## build

JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root — copy
`keystore.properties.example` and fill in your own keystore.

Everything sits on Android platform APIs: no Apache POI, no PDFium, no
third-party format library. `core/model` holds the document model in plain
Kotlin, `core/ooxml` the DOCX/XLSX/PPTX readers and writers, `app` the screens,
the PDF engine and the exporters. 310 unit tests run on the JVM in CI; device
behaviour is still checked by hand.

## roadmap

Next: conditional formatting, cell notes and named ranges (sheets); page
numbers, footnotes and a table of contents (documents); images and shapes on
slides; PDF form filling and OCR; copy/paste between apps; `.odt`/`.ods`/`.odp`
support; an encrypted vault with a biometric lock.

## bugs

Include what you did and what happened instead, the file type, and your Android
version and phone model. Issues: https://github.com/Ziyzennix/ZiyOffice/issues

## contributing

See `CONTRIBUTING.md`. Never add network capability, and never copy code,
assets, text or layouts from proprietary office apps.

## license

GNU GPL-3.0-or-later. See `LICENSE`.

[![](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/ziyzenix)
