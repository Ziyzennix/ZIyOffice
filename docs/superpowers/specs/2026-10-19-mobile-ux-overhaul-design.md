# ZIyOffice Mobile UX Overhaul — Design

## Intent

Make ZIyOffice approachable to people who already know WPS Office and Canva, while
keeping ZIyOffice's own identity and privacy contract. Users should find familiar
office tasks quickly, understand what each tool can do, and complete common work
without hunting through menus or guessing at controls.

This is an experience overhaul, not a claim of feature parity. Familiar interaction
patterns can make existing features easier to discover; they cannot substitute for
missing document, spreadsheet, presentation, or graphic-design capabilities. Product
capability gaps remain explicit in `FEATURES.md` and need their own implementation work.

## Current state

- `HomeScreen.kt`: branding, recent-file search, Open, a collapsible template list,
  four create tiles, and recent files. PDF tile currently shows a notice instead of
  starting a useful workflow.
- `FilesScreen.kt`: category tabs over recent files and an Open action. It is not a
  general file browser; users must use Android's Storage Access Framework (SAF) to
  choose files.
- `ZiyOfficeApp.kt`: three root destinations — Home, Files, Settings — with editing
  screens launched separately.
- `EditorScreen.kt`: document canvas plus formatting rows and a More sheet; editing
  actions and document status compete for limited phone space.
- `SheetScreen.kt`: grid-oriented editor with formula and formatting actions, optimized
  for a large feature set but dense for first-time mobile users.
- `PresentationScreen.kt`: slide strip, selected-slide text fields, speaker notes,
  present mode, and PDF export. It edits text, not slide artwork/shapes/images.
- `PdfScreen.kt`: rendered page list, annotation modes, page operations, merge, and
  save-a-copy. SAF handles file and export locations.
- `docs/design/wps-ux-recon.md`: records observed WPS interaction patterns and the
  patterns/privacy practices ZIyOffice rejects.

## Users and success

Primary users are Android phone users migrating from familiar office apps. They may
know where to look for Open, New, Save, formatting, page navigation, and presentation
controls, but should not need to learn ZIyOffice's internal architecture.

Success means a user can, without instruction:

1. Open a recent file or choose a file through the system picker.
2. Start a new document, spreadsheet, or presentation; understand PDF entry and export.
3. Find common edit actions in the editor currently in use.
4. Tell whether edits are saved, unsaved, or being exported.
5. Return to Home/Files without losing their place or confusing editor navigation with
   Android Back.
6. Understand, before investing effort, which advanced features are supported and
   which are not.

## Design direction

- **Familiar, not copied.** Use common office conventions: clear file-type creation,
  recents, category filters, a persistent editor title/status area, contextual tool
  groups, a visible save state, and predictable back navigation. Canva-inspired
  influence is limited to approachable template discovery and a visual-first slide
  creation path using capabilities the app actually has. Do not imitate proprietary
  screens pixel-for-pixel.
- **Original ZIyOffice identity.** Keep the teal accent, Material 3 foundation, current
  type-color distinctions, original layouts, and Apache-2.0 Material Symbols. Avoid
  WPS/Canva logos, icons, branded copy, fonts, illustrations, and distinctive trade
  dress.
- **Task-first.** Prioritize Open, Continue editing, Create, and Export. Do not use
  decorative promotions, empty cloud sections, accounts, or paid-feature affordances.
- **Progressive disclosure.** Put high-frequency actions on the first toolbar layer;
  group lower-frequency actions under clearly named menus or sheets. Keep labels on
  unfamiliar or high-impact actions.
- **Honest status and recovery.** Show saving/saved/failed states, confirm destructive
  operations where recovery is not obvious, and explain unsupported formats/actions
  before users lose work.
- **Mobile-first and accessible.** One-hand reachable primary actions, touch targets
  at least 48 dp, readable contrast and type, keyboard-safe layouts, screen-reader
  labels, visible focus, and no horizontally clipped critical controls at supported
  phone widths.
- **Privacy is structural.** No network permission, account, cloud dependency, ads,
  analytics, or broad storage permission. File access and exports continue through
  user-selected SAF flows.

## Proposed experience

### 1. Home — “what do you want to do?”

- Compact branded header with one prominent search entry point.
- Primary actions: **Open file** and **Create**.
- Create opens a labeled chooser for Docs, Sheets, Slides, and a useful PDF workflow;
  do not make PDF creation appear possible if it only supports opening/exporting.
- Recent files show type, name, last-opened status, and a clear Continue action. Empty
  state explains how to open a file and where it remains stored.
- Templates are previewable and categorized before creating a copy. Only templates
  that exist and can be created are shown.
- Remove redundant separate “Open a file” and create affordances where they compete;
  preserve fast direct access to each common task.

### 2. Files — find and filter, without implying full-device access

- Keep familiar All / Docs / Sheets / Slides / PDF filters.
- Make Search, Open via SAF, and recent-file list purpose clear.
- Explain that this list is recent files, not a scan of device storage. Avoid wording that
  suggests files were copied or that broad storage access is required.
- Preserve Android's system picker as the only way to browse outside the user's recent
  list.

### 3. Editors — consistent shell, task-specific controls

All editors share a predictable shell: Back, concise file name, save state/action, and
overflow for secondary file actions. Each editor keeps its own visual work surface and
uses labeled, horizontally scrollable or grouped actions that do not clip on phone.

- **Docs:** retain the page-like surface. Separate writing actions from paragraph and
  character formatting; keep common formatting visible when editing and put advanced
  options in named groups. Show selection/focus clearly and keep document count/status
  legible. Never imply unsupported Word layout fidelity.
- **Sheets:** keep the grid central. Make selected-cell address/value, formula entry,
  sheet navigation, and keyboard editing understandable. Group sort, freeze, number
  formats, charts, and other tools under a labeled Tools surface rather than a crowded
  icon row. Keep formula-bar access usable with the keyboard open.
- **Slides:** shift from placeholder-style text fields toward a clear slide canvas
  preview with a selectable thumbnail strip and visible title/body editing controls.
  Add slide, duplicate/delete/reorder, notes, and present actions have explicit labels.
  Do not imply shape/image editing until implemented.
- **PDF:** keep the rendered page as the focus. Group Annotate and Organize actions;
  page count, selected page, rotation, removal state, and export count remain visible.
  Merge and save-a-copy use clearly named actions and report failures with actionable
  feedback. Export must finish with a non-empty valid PDF before reporting success.

### Save and PDF entry decisions

- Editors retain both an explicit Save action and local recovery drafts. The UI clearly
  distinguishes unsaved edits, draft recovery state, saving, saved-to-source, and saved
  copy/export. Leaving with edits not successfully written to the source must offer a
  clear save/discard/stay choice; draft presence alone must not be represented as a
  successful save.
- Home's PDF tile starts **Open PDF** using SAF. PDF export remains available from the
  relevant editor. Do not pretend ZIyOffice can create a blank PDF document.

### 4. Navigation and transitions

- Keep Home, Files, and Settings as root destinations. Do not add placeholder tabs.
- Editors are task destinations. Android Back returns to the prior root/list context;
  editor Back handles unsaved changes explicitly.
- Reopening a recent file should return to the same file and make draft/recovery status
  visible where applicable.

## Work phases

1. **Foundation and shell:** unify editor chrome, save/error feedback, navigation and
   touch/accessibility behavior. Fix misleading PDF create entry and status copy.
2. **Home and Files:** simplify task hierarchy, search, recents, SAF explanation, and
   template preview/discovery.
3. **Docs and Sheets:** reduce toolbar density and validate keyboard/selection flows.
4. **Slides and PDF:** improve visual hierarchy while keeping current feature boundaries
   explicit; harden large-document/export states.
5. **Capability roadmap:** separately prioritize actual parity features based on user
   tasks; update `FEATURES.md` only after they work. Canva-like general-purpose design
   canvas is not implied by this UI overhaul and requires a separate capability design.

Each phase is a reviewable, buildable slice; do not replace every screen in one
unverified rewrite.

## Out of scope

- Copying proprietary code, assets, branding, text, or pixel-identical layouts.
- Network, cloud sync, sign-in, ads, analytics, subscription or broad storage access.
- Claiming WPS/LibreOffice/Canva parity from visual changes alone.
- Presenting unimplemented functionality as available (including Canva-like freeform
  design, slide shape editing, OCR, or unsupported file-format fidelity).
- Rewriting office file engines as part of the first UX slice.

## Validation

- Walk through first launch, open recent, open via SAF, create each supported file type,
  edit, save, export, recover, and navigate Back on an attached phone.
- Test narrow and larger Android phone widths, keyboard-open states, larger font scale,
  TalkBack labels, touch targets, and contrast.
- Verify success messages only follow successful writes; check output documents for
  non-zero size and parseability.
- Run unit tests, app compilation/assembly, `scripts/ci/check-manifest.sh`, and
  `scripts/ci/check-dependencies.sh` for each implementation phase.
- Measure common-task taps and record remaining capability gaps; no “complete” claim
  without working behavior.

## Decisions left to implementation

- Exact Compose layouts and component extraction, following current project structure.
- Whether the home template chooser is a full screen or bottom sheet, chosen by phone
  usability and accessibility testing.
- Incremental ordering inside each phase, without changing stated goals or constraints.
