# ZIyOffice Full Product — Design

## Intent

Build ZIyOffice into a standalone Android productivity app for office work, visual
design, and desktop-class workflows on phones and tablets. The full release target is
broader than the current Alpha preparation: users should finish supported work inside
ZIyOffice without needing another office/design app or a PC.

Development order is deliberate: first close the WPS Office workflow gaps, then build
Canva-like visual design capability, then make the app work as a desktop-class tool on
mobile hardware. Release only after the complete target is implemented and validated.

## Product principles

- **Capability before parity claims.** Build original implementations from public
  format specifications and observed common workflows. Never copy proprietary code,
  assets, text, branding, or distinctive layouts. Do not claim WPS, Canva, or desktop
  parity until corresponding user workflows pass documented tests.
- **Compact means organized, not reduced.** Keep the full feature set. Use clear task
  groups, progressive disclosure, contextual controls, searchable commands where useful,
  and persistent space for the document/canvas. Avoid removing useful capabilities to
  make screens look sparse or putting every action in a crowded toolbar.
- **Phone-first, adaptive upward.** Primary touch workflows must work on a phone. Use
  available width for denser tool layouts on tablets and external displays; support
  keyboard and mouse without making them prerequisites for core work.
- **Work stays recoverable.** Saving, exporting, printing, unsupported content, and
  recovery states must be explicit. Never silently discard user content or report a
  successful operation before it completes.
- **Privacy remains structural.** Preserve offline operation, no account/cloud
  dependency, no ads/analytics/tracking, scoped user-selected file access through SAF,
  and zero declared system permissions unless owner approves a specific change.

## Target users and success

Android phone and tablet users who need to create, edit, exchange, and present office
documents; create visual designs; and complete work with touch or desktop peripherals.

Full release succeeds when users can complete representative end-to-end workflows in
each stage below on supported Android devices, with file integrity, honest capability
feedback, accessible controls, and no dependence on an external office/design app or
PC for workflows ZIyOffice claims to support.

## Product stages

### Stage 1 — WPS Office workflows

Close practical office-work gaps across Docs, Sheets, Slides, and PDF. Scope is full
coverage of common office workflows, not implementation of every proprietary feature.

- **Docs:** create, edit, format, paginate, save, reopen, export, and print documents;
  support common tables, images, headers/footers, page setup, and reliable DOCX
  round-tripping. Detect unsupported content and explain the risk before changes can
  remove it.
- **Sheets:** edit XLSX and CSV; support a useful documented set of common formulas,
  cell formats, ranges, sorting, frozen panes, charts, and print/export workflows.
  Preserve supported workbook parts and disclose unsupported formulas/features.
- **Slides:** create and edit presentations with a visual slide canvas, text, images,
  basic shapes, slide add/duplicate/delete/reorder, notes, present mode, and visual
  export. Preserve or clearly warn about unsupported package content.
- **PDF:** view, organize, annotate, sign, fill supported forms, redact content
  permanently, merge/split/rotate/reorder, save copies, export, and print. Redaction
  must remove underlying content, not merely cover it visually.
- **Common workflow:** SAF open/save destinations, useful recent files and file
  organization, search, print service integration, local recovery, and clear error and
  unsupported-feature states.

Do not represent unimplemented advanced office features (for example macros, pivot
tables, tracked changes, or complex equation authoring) as supported. Add each only
through a separate capability decision and format-integrity tests.

### Stage 2 — Canva-like visual design

Add an original general-purpose visual design workspace, not only a slide editor:

- page/canvas sizes and multipage projects;
- text, shapes, imported images, basic drawing, and reusable templates;
- selectable, movable, resizable, reorderable, alignable, and groupable layers;
- undo/redo, duplicate, delete, zoom, and page navigation;
- export to practical image and PDF formats with predictable dimensions and quality.

All design assets and templates must be original or have redistribution rights. Cloud
template libraries and online AI are excluded while offline/no-network constraints hold.

### Stage 3 — Desktop-class mobile workflows

Make supported workflows efficient on tablets, foldables, and external displays:

- adaptive layouts that use wider screens without stretching phone layouts;
- keyboard shortcuts and visible focus; mouse, trackpad, and stylus interaction;
- resizable panes or windows where Android supports them;
- robust file selection, multiple-document workflows where supported, print, and export;
- reliable behavior through rotation, resizing, multi-window, and keyboard changes.

This stage means desktop-like productivity for the workflows ZIyOffice supports; it does
not imply Windows/macOS/Linux builds or unbounded desktop-suite parity.

### Stage 4 — Full release gate

Publish only after all three stages' agreed capabilities are implemented and tested.
Maintain an explicit feature matrix. Validate representative real-world files,
round-trip integrity, large documents, failure/cancel paths, accessibility, performance,
privacy guards, and signed installation/update behavior on a device matrix. Alpha or beta
builds may be shared earlier only if clearly marked as pre-release and not confused with
full release.

## UX direction

- Keep all capabilities; organize them by task and context.
- Give the work surface most of the screen. Keep frequent actions visible and group
  occasional actions into labeled menus, panels, or command search.
- Use consistent file title, save state, Back behavior, and overflow conventions across
  editors while retaining controls specific to each document type.
- Prefer compact, scannable tool groups and adaptive rails/panels over long icon rows.
- Preserve touch-target, text-scale, TalkBack, keyboard-focus, and contrast requirements;
  compact layout is not permission to make controls tiny or hidden.
- Explain unsupported content before a destructive save/export; provide recovery when
  an operation fails or the user leaves with edits.

## Constraints and non-goals

- Current platform remains Android and current implementation remains Kotlin/Compose
  unless a separately reviewed architecture decision changes it.
- No network permission, accounts, cloud sync, ads, analytics, or tracking under the
  current privacy contract.
- No camera permission for OCR in this design. If OCR is later added, input comes from
  user-selected images/documents unless the owner explicitly approves a permission
  change.
- No copied proprietary material, pixel-perfect WPS/Canva recreation, or parity claims
  based on UI alone.
- Do not promise every desktop-suite, macro, collaboration, or proprietary format feature.

## Delivery and validation approach

Deliver each product stage in reviewable vertical slices. Prioritize data integrity and
core workflows before breadth. Every supported format capability needs representative
fixtures and tests. Every save/export/print flow needs success, failure, cancellation,
and recovery validation. Device checks must include small phone, large phone, tablet or
foldable, keyboard/mouse where available, large text, and TalkBack.

The existing UX overhaul plan remains a bounded pre-release UX slice; it does not by
itself satisfy the full product target. Roadmap and `FEATURES.md` status must reflect
working, validated behavior rather than aspirations.

## Decisions left to implementation planning

- Exact feature cut for “common office workflows” and supported formula/format matrix.
- File compatibility corpus and measurable round-trip acceptance thresholds.
- Visual design document model and rendering/export architecture.
- Supported Android/device matrix and minimum performance budgets.
- Whether zero declared permissions remains absolute through full release; any proposed
  exception needs an explicit product/privacy decision first.
