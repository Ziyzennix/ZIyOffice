# ZIyOffice

An Android office suite for documents, spreadsheets, slides and PDFs. It works
completely offline, needs no account, and has no network permission — so it cannot send
your files anywhere, even if it wanted to.

Free and open source (GPL-3.0). No subscription, no ads, no tracking.

**Version 0.5 — early development. Expect bugs.** If something breaks, please
[open an issue](https://github.com/Ziyzennix/ZIyOffice/issues).

## What it does

| Format | Open | Edit | Save | Export | Print |
|---|---|---|---|---|---|
| `.docx` | yes | text, formatting, tables, images | yes | PDF | yes |
| `.xlsx` / `.csv` | yes | cells, formulas, formatting, sorting | yes | PDF, CSV | yes |
| `.pptx` | yes | slide text, add/remove/reorder, notes | yes | PDF (text only) | yes |
| `.pdf` | yes | highlight, ink, notes, rotate, reorder, delete, merge, split | yes | PDF | yes |

Other things it does:

- **Redaction that actually removes content** — redacted areas are gone from the exported
  bytes, not covered with a black box.
- **Recovery** — unsaved edits are kept in a local draft, so a crash does not lose work.
- **Templates** — start a document from a built-in template.
- **Find and replace** in documents.
- **Charts** from a spreadsheet column.
- **Present mode** with speaker notes.

## Privacy

Four things are enforced by the build, not just promised:

1. **No network permission.** The manifest declares no `android.permission.INTERNET`, so
   Android itself blocks network access. A CI check fails the build if any permission is
   added without review.
2. **No ads, analytics or crash reporting.** A CI check fails the build if such a
   dependency is declared.
3. **Local-only storage.** Files stay in the folders you pick through Android's file
   picker. ZIyOffice stores only a list of recent files and local recovery copies of
   unsaved edits.
4. **Free forever.** GPL-3.0 — nobody can take this, close it, and sell it back to you.

The About screen shows the permissions the installed app actually declares.

## Known limitations

**Saving removes content ZIyOffice cannot read.** Each file is rebuilt from ZIyOffice's
own document model on save, so parts it does not model are dropped: headers, comments,
tracked changes, charts, merged cells, pivot tables and similar. The app checks the file
when it opens and tells you exactly what a save will remove before the first write.
**"Save as copy" leaves the original untouched** — use it if the file has content you
need to keep.

Not implemented yet: encrypted vault, OCR, handwriting notes, slide shapes and images,
PDF form filling, headers and footers, macros, pivot tables.

## Install

Download the APK from [Releases](https://github.com/Ziyzennix/ZIyOffice/releases) and
open it on your phone. Android will ask you to allow installing from this source.

Requires Android 8.0 (API 26) or newer.

## Build

Requirements: JDK 17+ and Android SDK platform 36.

```bash
scripts/setup-dev.sh          # check your toolchain
./gradlew :app:assembleDebug  # build
./gradlew test                # run unit tests
scripts/install-device.sh     # install on a connected phone
```

A signed release build needs `keystore.properties` in the repo root — copy
`keystore.properties.example` and fill in your own keystore. Without it, release builds
are unsigned and `assembleRelease` fails with a message saying so.

## Project layout

| Path | Purpose |
|---|---|
| `app/` | Screens, storage access, PDF engine, exporters |
| `core/design/` | Compose theme and colors |
| `core/model/` | Document model — plain Kotlin, unit-tested |
| `core/ooxml/` | DOCX / XLSX / PPTX readers and writers |
| `scripts/ci/` | Privacy guardrails |
| `docs/` | Threat model, architecture decisions, feature matrix |

`FEATURES.md` tracks what works today against WPS Office, row by row.

## Reporting bugs

This is early software, so bugs are expected. When you report one, include:

- what you did, and what happened instead
- the file type you were working on (not the file itself, unless you are happy to share it)
- your Android version and phone model

Open an issue: https://github.com/Ziyzennix/ZIyOffice/issues

## Contributing

See `CONTRIBUTING.md`. Two rules: never add network capability, and never copy code,
assets, text or layouts from proprietary office apps.

## License

GNU GPL-3.0-or-later — see `LICENSE`.
