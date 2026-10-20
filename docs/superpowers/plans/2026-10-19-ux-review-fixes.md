# ZIyOffice UX Review Fixes — Completion Record

**Goal:** Address highest-impact review findings across save safety, recoverability, file clarity, accessibility, and privacy honesty without pretending to deliver office-suite parity.

**Status:** Tasks 1–5 implemented and verified by build + unit tests + privacy guards.
Task 6 device checks remain **outstanding** — see "Not verified" below. Nothing in this
file claims a device result that was not observed.

**Architecture:** Kept the existing Compose screens and SAF flows. Editor exit and
in-flight-write decisions were extracted into one pure, tested helper (`EditorExit.kt`)
rather than duplicated three times.

**Spec:** `docs/superpowers/specs/2026-10-19-mobile-ux-overhaul-design.md`.

## Global Constraints (all held)

- App still declares **zero** permissions; `scripts/ci/check-manifest.sh` passes.
- No ads/analytics/tracking artifacts; `scripts/ci/check-dependencies.sh` passes.
- Local drafts are kept and are never described as a successful source save.
- No save/export success is reported before the output write is confirmed.

## Task 1: Prevent edit loss in Docs and Sheets — done

- [x] One shared exit decision, `exitAction(dirty, saving, needsConfirmation)`, used by
      toolbar Back and system Back in all three editors. `EditorExitTest` covers
      clean-leave, ask, save-and-leave, and write-in-flight-blocks-exit.
- [x] Docs: `tryExit` no longer starts a background save and navigates away. Unsaved
      work opens a Stay / Discard / Save dialog; Save navigates only on success.
- [x] Docs/Sheets/Slides: a `writeVersion` counter is bumped by every edit, so a write
      that finishes after newer edits landed keeps the document dirty
      (`writeSettlesDirty`, unit-tested).
- [x] Sheets: `commitEdit()` runs before save, undo, redo, clear, freeze, sort, format,
      chart, rename, add-sheet and exit, so a typed-but-uncommitted cell is never lost.
      It also sets `dirty`, which it previously did not.
- [x] Sheets: undo/redo restore the *snapshot's* active sheet instead of clamping the
      current index, so undo of a sheet switch returns to the right sheet.

## Task 2: Prevent edit loss and stuck states in Slides — done

- [x] Shared exit decision; unsaved deck opens Stay / Discard / Save.
- [x] Explicit load-error state with the reason, "Try again", and "Back" — a failed
      parse no longer leaves the screen on "Loading presentation…" forever.
- [x] Draft autosave failure is surfaced once instead of vanishing into `runCatching`.
- [x] Export reports the name the user chose, states that the PDF is text-only, and
      re-reads the destination to confirm non-zero bytes before claiming success.

## Task 3: Correct PDF operation truth and recoverability — done

- [x] Export verifies the written file is non-empty and starts with `%PDF` before any
      success message; the message names the destination the user picked.
- [x] Progress dialog with real per-page progress (`PdfRewriter.export(onProgress=…)`)
      and a Cancel that stops the blocking page loop between pages.
- [x] Cancellation reports "Export cancelled · no file was written" and never a false
      success or a spurious failure.
- [x] Rotating a page now rotates its marks with it (`rotateMarks`, `RotateMarksTest`),
      so highlights, ink, notes and redactions stay on their content.
- [x] Notes are disclosed as not included in the exported PDF.
- [x] Accessible mark list: every mark is listed with its type, page and a 48 dp remove
      action — the only non-drag way to find or undo a highlight/redaction.

## Task 4: Accessible office controls and mobile density — done (static)

- [x] Spreadsheet cells announce coordinate + value + selected state and carry
      `Role.Button`; sheet tabs announce name, selected state and `Role.Tab`, and have a
      48 dp minimum height.
- [x] Bold/Italic/Underline/Strikethrough in Docs and Sheets expose an On/Off
      `stateDescription` instead of relying on tint.
- [x] Fill swatches and font-colour items have distinct spoken names plus a selection
      state (asserted by `AccessibilityCopyTest`).
- [x] Slide thumbnails announce position, title and selection.
- [x] Recent rows, template rows, create tiles and the About row are single labelled
      `Role.Button` nodes; the create tiles and kind badge are now ≥48 dp.
- [x] Document list markers use a minimum width instead of a fixed 22 dp, so numbers
      are not clipped at large font scales.
- [x] `contentDescription` was deliberately **removed** from the search and formula
      fields: on an editable node it replaces the text TalkBack reads back. The address
      and label are spoken by adjacent nodes instead.

## Task 5: Correct file scope and privacy copy — done

- [x] Files tab is titled "Recent files" with the subtitle "Files you have opened in
      ZIyOffice"; the SAF action is "Browse device".
- [x] Home's PDF tile now opens the PDF picker (`Open PDF`) instead of showing a notice,
      matching the spec's "PDF tile starts Open PDF" decision.
- [x] Privacy copy states what is declared (no network permission) and what is stored
      locally (recents + recovery copies), and notes that a folder synced by another app
      may still be synced by that app.
- [x] Unverified absolutes removed: "cannot spy on you" and "Nothing is uploaded
      anywhere".

## Task 6: Verification

- [x] `./gradlew test` — passes (`core/model`, `core/ooxml`, `app`).
- [x] `./gradlew :app:assembleDebug` — passes.
- [x] `./gradlew :app:lintDebug` — no errors (26 warnings, all pre-existing categories:
      `UseKtx`, version-update hints, one `InlinedApi` in `PdfExporter`).
- [x] `scripts/ci/check-manifest.sh` — "the app declares zero system permissions".
- [x] `scripts/ci/check-dependencies.sh` — no ads/tracking/telemetry artifacts.

### Not verified (no device attached)

- TalkBack announcement order and wording on a real device.
- 48 dp hit areas and large-font layout by eye at 200 % font scale.
- Keyboard-open layout on a short phone.
- Save failure with a revoked grant, and PDF export of a large document.
- Round-trip integrity against a real-world corpus beyond the existing tests.

These must be run before any release claim. The code paths are in place; the
observations are not.

## Explicitly deferred

- PDF lazy bitmap rendering / large-document memory architecture.
- Vector (text-preserving) PDF export; rasterization is disclosed instead.
- General-purpose Canva-style canvas, full slide artwork editing, OCR, WPS parity.
- Clearing individual recent entries (needs a safe way to distinguish the app's index
  entry from the user's file).
