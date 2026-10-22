# ZiyOffice

An office app for Android. Opens, edits and prints documents, spreadsheets, slides and PDFs.
No account, no ads, no subscription.

## why

The big office apps on Android are free in the sense that you pay them later. They want an
account before you can use the feature you just opened, they ask for storage and camera
permissions they don't need, they show ads and premium nudges, and the second a feature
wants the cloud your documents sit on somebody else's server.

ZiyOffice does the other trade:

- **it works offline.** there's no `android.permission.INTERNET` in the manifest, so Android
  blocks network access for the app. Not "we do not phone home": the app cannot.
- **no account, no ads, no analytics.** the build fails if an ad SDK, a crash reporter or a
  tracking dependency shows up.
- **no system permissions.** the About screen shows what the installed APK actually declares.
  There's nothing in the list (AndroidX adds one signature permission of its own, which is
  worthless outside this app).
- **your files stay in your folders.** you pick them with Android's file picker, the app
  never goes looking through your storage.
- **free forever.** GPL-3.0, so nobody can take this, close it and sell it back to you.

CI enforces all of it: one script fails the build if a permission appears, another if
networking code gets written, a third if a tracking dependency gets declared.

## how it works

You pick files through the system picker. ZiyOffice shows up in Android's "Open with" and
share sheets. Unsaved work is kept as a local draft, so a crash doesn't take your typing
with it.

Saving writes your edits back into the file you opened. A paragraph you didn't touch comes
out of a save byte for byte. A paragraph you did touch keeps everything around it (the
hyperlink, the comment anchor, the image), and only the text and formatting you actually
changed get rewritten. Comments, footnotes, tracked changes, charts, pivot tables,
conditional formatting and hidden rows survive a save even though the app can't edit them.
"Save as copy" always leaves the original alone, and a save only reports success once the
file has been written *and* read back.

- **Documents**: text formatting, headings, lists, alignment, line spacing, indent and
  spacing, page breaks, tables, pictures, headers and footers, find and replace, copy and
  paste, PDF export, print.
- **Spreadsheets**: cells, about 70 formula functions, number and date formats, frozen rows,
  sorting by one column or two, named ranges, merged cells, column widths and row heights,
  cell borders, dropdown lists, inserting and deleting rows and columns, find and replace,
  copy and paste, charts, CSV.
- **Slides**: slide text, adding/deleting/reordering slides, pictures and shapes, speaker
  notes, a transition per slide, a present mode, PDF export.
- **PDF**: fill in the forms a file already has, highlight, ink, notes, a drawn signature,
  text stamped on a page, page numbers, rotate, reorder, delete, merge, split, and redaction
  that removes the content underneath instead of covering it.

Printing goes through Android's own print service, so no permission is involved.

## not yet

OCR, handwriting notes, macros, pivot tables, `.odt`/`.ods`/`.odp`. `FEATURES.md` has the full list against WPS Office.

## install

APK from [Releases](https://github.com/Ziyzennix/ZiyOffice/releases), opened on your phone.
Android 8.0 (API 26) or newer. The APK is about 1.8 MB.

## build

JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root. Copy
`keystore.properties.example` and fill in your own keystore.

Everything sits on Android platform APIs: no Apache POI, no PDFium, no third-party format
library. `core/model` holds the document model in plain Kotlin, `core/ooxml` the
DOCX/XLSX/PPTX readers, writers and preservation layer, `app` the screens, the PDF engine and
the exporters. The unit tests run on the JVM in CI. Device behaviour is still checked by
hand.

## roadmap

Next up: editing the comments, footnotes and tracked changes the app now keeps; cell notes;
page numbers and a table of contents in documents; handwriting; `.odt`/`.ods`/`.odp` support;
OCR of a picture you pick.

## bugs

Say what you did and what happened instead, the file type, and your Android version and
phone model. Issues: https://github.com/Ziyzennix/ZiyOffice/issues

## contributing

See `CONTRIBUTING.md`. Never add network capability, and never copy code, assets, text or
layouts from proprietary office apps.

## license

GNU GPL-3.0-or-later. See `LICENSE`.

[![](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/ziyzenix)
