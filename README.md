# ZIyOffice

**A free, open-source office suite that cannot spy on you — it has no network permission.**

ZIyOffice is an Android office suite (Documents, Sheets, Slides, PDF) built from scratch in
Kotlin. It is **permanently free**: no subscription, no ads, no accounts, no tracking, no
upsells. All editing works **fully offline**, and your documents stay in the folders you
choose on your own device.

## The privacy contract

These are guarantees, not marketing claims — each one is enforced by the build:

1. **No network permission.** The app manifest declares no `android.permission.INTERNET`,
   so Android itself makes network access impossible. A CI check fails the build if any
   permission is added without review, and `INTERNET` can never be added at all.
2. **No ads, analytics, crash reporting or tracking.** A CI check fails the build if any
   such dependency is declared.
3. **Local-only storage.** Documents live in real files in real folders (via Android's
   Storage Access Framework). Nothing is uploaded, synced or backed up.
4. **Every feature free, forever.** GPL-3.0 — nobody can take ZIyOffice, close it and sell
   it back to you.

The About screen shows you a live receipt: it reads the permissions the installed APK
actually declares and displays them.

## Project status

Milestones **M0–M4 complete**: an installable app shell with the privacy guardrails, plus
Documents (`.docx`), Sheets (`.xlsx`/CSV), PDF (view, annotate, merge/split/rotate/reorder,
sign, redact) and Slides (`.pptx` text + present mode). See `FEATURES.md` for the living
feature matrix and `docs/design/wps-ux-recon.md` for the UX reference we build against.
A save-safety and accessibility pass has landed on top (see
`docs/superpowers/plans/2026-10-19-ux-review-fixes.md` for what is verified and what still
needs a physical-device check). Remaining work (M5–M6): encrypted vault, offline OCR,
handwriting, F-Droid release, signed GitHub APKs.

## Building

Requirements: JDK 17+, Android SDK (platform 36) — see `scripts/setup-dev.sh` to verify.

```bash
# Check the toolchain
scripts/setup-dev.sh

# Build the debug APK
./gradlew :app:assembleDebug        # or: gradle :app:assembleDebug

# Run unit tests
./gradlew test

# Build, install and launch on a connected phone
scripts/install-device.sh
```
Unit tests cover `core/model`, `core/ooxml`, and the app's pure UI helpers
(`EditorExit`, accessibility copy, mark rotation) — not Compose composition itself.

### What a save does to a file ZIyOffice cannot fully read

Each writer rebuilds the package from ZIyOffice's model. Anything the reader does not
model — headers, comments, tracked changes, charts, merged cells, pivot tables and the
like — is therefore **removed by a save**, not preserved. The editor audits the file on
open and names exactly what will go before the first write; "Save as copy" keeps the
original intact. The enforced list lives in `ContentAudit` and is pinned by
`ContentAuditTest`.

## Project layout

| Path              | Purpose                                                        |
|-------------------|----------------------------------------------------------------|
| `app/`            | Application shell: home, settings, About, storage integration  |
| `core/design/`    | Compose theme, colors, typography (the ZIyOffice look)         |
| `core/model/`     | Platform-independent document model (plain Kotlin, unit-tested)|
| `scripts/ci/`     | Privacy guardrail scripts run on every change                  |
| `docs/`           | Threat model, architecture decision records                    |

## Roadmap (summary)

- **M0** Foundations — repo, guardrails, app shell ✅
- **M1** Documents — open/edit/save `.docx` offline, autosave, PDF export ✅
- **M2** Sheets — grid, formulas, `.xlsx` + CSV ✅
- **M3** PDF — view, annotate, merge/split/rotate/reorder, sign, redact ✅
- **M4** Slides — edit + present, `.pptx` ✅
- **M5** Privacy weapons — encrypted vault, metadata scrub, offline OCR, handwriting (in progress)
- **M6** Release — F-Droid, signed GitHub APKs, performance and accessibility passes

## Contributing

See `CONTRIBUTING.md`. The two absolute rules: never add network capability, and never
copy code, assets or text from proprietary office apps (WPS included) — feature parity
comes from public documentation, reimplemented.

## License

GNU GPL-3.0-or-later — see `LICENSE`.
