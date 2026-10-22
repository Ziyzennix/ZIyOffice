# ZiyOffice

An office app for Android. Opens, edits and prints documents, spreadsheets, slides and PDFs.
No account, no ads, no subscription.

## why

The big office apps on Android are free in the sense that you pay them later. They want an account before you can use the feature you just opened, they ask for storage and camera permissions they don't need, they show ads and premium nudges, and the second a feature wants the cloud your documents sit on somebody else's server.

ZiyOffice does the other trade:

- **it works offline.** There's no `android.permission.INTERNET` in the manifest, so Android blocks network access for the app. Not "we do not phone home": the app cannot.
- **no account, no ads, no analytics.** The build fails if an ad SDK, a crash reporter or a tracking dependency shows up.
- **no system permissions.** The About screen shows what the installed APK actually declares. There's nothing in the list.
- **your files stay in your folders.** You pick them with Android's file picker, the app never goes looking through your storage.
- **content stays intact.** Saving writes your edits back into the file you opened without touching the rest. A paragraph you didn't edit comes back byte for byte.
- **free forever.** GPL-3.0, so nobody can take this, close it and sell it back to you.

## what it can do

- **Documents**: text formatting, headings, lists, alignment, line spacing, indent, tables, pictures, headers, footers with page numbers, word count stats, find and replace, copy and paste, PDF export, print.
- **Spreadsheets**: cells, about 70 formula functions, number and date formats, frozen rows, sort by one or two columns, named ranges, merged cells, column widths, row heights, borders, dropdown lists, text wrap, cell notes, multi-sheet tabs (add, switch, rename, duplicate, delete), find and replace, copy and paste, bar charts, CSV import/export, PDF export.
- **Slides**: slide text, reordering slides, pictures and shapes, speaker notes, slide transitions, full-screen present mode, print.
- **PDF**: fill interactive forms, highlight, ink, notes, drawn signature, text stamps, page numbers, rotate, reorder, delete, merge, split, true redaction, print.
- **OCR & Photos to PDF**: pull text off photos into a document offline, or bundle pictures straight into a PDF.
- **Vault & Starred**: keep sensitive files in an encrypted vault on device, and star files to find them quickly.

## heads up for 0.5.2

All unit tests pass in Gradle, but this 0.5.2 build has not been tested on a physical phone yet. My phone is broken and my PC is too weak to run the Android emulator.

If you hit any bugs, crashes or weird layouts, please open an issue:
https://github.com/Ziyzennix/ZiyOffice/issues

## install

APK from [Releases](https://github.com/Ziyzennix/ZiyOffice/releases), opened on your phone.
Android 8.0 (API 26) or newer.

## support

[![Support on Ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/ziyzenix)

## license

GNU GPL-3.0-or-later.
