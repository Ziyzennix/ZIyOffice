# ZiyOffice Accessibility Audit — Punch List

**Status (2026-10-21):** items 1–15 below are addressed in code, item 11 included:
the create tile's icon surface is 48 dp. The device checks in "Still needs a device"
are outstanding. This document is kept as the audit record, so the findings are
preserved verbatim below the status note.

**Addressed:** recent rows / template rows / create tiles / About row are labelled `Role.Button` nodes with ≥48 dp targets (1, 2, 3, 4); KindBadge minimum touch target raised to 48 dp (10); sheet tabs announce name + selection with `Role.Tab` (5, 8); slide thumbnails announce position/title/selection (6); fill and font-colour swatches have distinct spoken names plus a selection state (7, 9); Docs and Sheets format toggles expose On/Off state instead of relying on tint (15); list markers use a minimum width so large font scales do not clip them (12); `contentDescription` was removed from the search and formula fields because on an editable node it suppresses read-back (see "Corrections to this audit"); document text style no longer hardcodes 11.sp so system font scale is respected (12); recent-row file names no longer ellipsis-clipped (14); slide titles and body previews no longer ellipsis-clipped in slide cards (14).

**Still needs a device:** TalkBack announcement order and wording; measured contrast for
`onSurfaceVariant` on `surfaceVariant` and `outline` on `surfaceContainer`; large-font and
keyboard-open layouts; focus order through the sheet grid.

## Corrections to this audit

- Item 12/13 proposed replacing hardcoded `.sp` sizes with typography tokens. That premise
  was wrong for the *scaling* concern: `.sp` already follows the system font scale, and
  changing document text to a Material token would misrepresent the document's own point
  size. The real large-font defect was the fixed `22.dp` list-marker gutter, now a minimum
  width.
- Adding `contentDescription` to `OutlinedTextField`/`TextField` (as the first pass did for
  the Home search field and the Sheets formula bar) *replaces* the node's text semantics,
  so TalkBack stops reading back what the user types. Those two were reverted; the field
  label and the adjacent address text carry the name instead.
- `presentation of "Selected"` via `contentDescription` on a check icon duplicates state.
  The container item now carries `stateDescription` and the icon is decorative.

---

**Scope of the original audit:** Read-only audit of `app/src/main/java/org/ziyoffice/ui/`
plus `core/design/src/main/java/org/ziyoffice/design/`.
**Skipped (owned by fix worker):** `PdfScreen.kt`, `PdfMarks.kt`, `PdfRewriter.kt`.
**Unread items:** FormatAction internals, SheetAction internals beyond label prop,
SheetCell per-cell focus (SheetScreen.kt:1043), DocMoreMenu FilterChip visual
selection indicators, `ZiyTypeColors` badge colors (imported but not defined in
the three theme files read).

---

## CONTROL TABLE

Columns: file:line | type | action | contentDescription | semantics | touch target ≥48dp | state-by-color?

### RecentRow.kt
- RecentRow.kt:34 | clickable (whole row) | open recent file | NONE (row has no contentDescription) | NONE | UNKNOWN (only vertical 6.dp padding; no min touch target) | YES — kind color via KindBadge is the type indicator
- RecentRow.kt:61 | Surface (KindBadge) | show document type badge | NONE | NONE | NO — Modifier.size(44.dp) < 48dp | YES — color is the only type indicator (badgeText duplicates as text)

### HomeScreen.kt
- HomeScreen.kt:92 | Icon (search leadingIcon) | decorative inside search field | NONE | NONE | N/A (inside OutlinedTextField) | NO
- HomeScreen.kt:99 | FilledTonalButton | open a file | label from Text "Open a file" | implicit from button text | YES (fillMaxWidth) | NO
- HomeScreen.kt:105 | TextButton | show/hide templates | label from Text | implicit | YES | NO
- HomeScreen.kt:118 | clickable (template row) | create from template | NONE | NONE | UNKNOWN | NO
- HomeScreen.kt:236 | clickable (CreateTile Column) | create doc/slides/sheets/PDF | NONE (Icon child has contentDescription=label but Column is the click target) | NONE | UNKNOWN | YES — tile color conveys type (Docs blue, Slides orange, Sheets green, PDF red)
- HomeScreen.kt:195 | TextButton | show all files | label from Text "All N files" | implicit | YES | NO

### FilesScreen.kt
- FilesScreen.kt:62 | TextButton | open a file | label from Text "Open" | implicit | YES | NO
- FilesScreen.kt:69-75 | Tab (x6, SecondaryScrollableTabRow) | filter by category | label from Text name | implicit Material3 Tab semantics | YES | YES — selected tab indicated by color/position
- FilesScreen.kt:90 | Icon (InsertDriveFile) | decorative empty-state | NONE | NONE | N/A (48.dp size but decorative) | NO
- FilesScreen.kt:118 | RecentRow items | see RecentRow.kt | — | — | — | —

### SettingsScreen.kt
- SettingsScreen.kt:72 | clickable (row) | open About | NONE | NONE | UNKNOWN (padding vertical 12.dp) | NO
- SettingsScreen.kt:82 | Icon (ArrowForward) | navigate to About | NONE | NONE | N/A (inside clickable row) | YES — meaning conveyed only by icon shape/position; no contentDescription

### AboutScreen.kt
- AboutScreen.kt:57-58 | IconButton | back | "Back" | NONE | YES (IconButton default) | NO

### UnsupportedScreen.kt
- UnsupportedScreen.kt:43-45 | IconButton | back | "Back" | NONE | YES | NO
- UnsupportedScreen.kt:78-83 | FilledTonalButton | open a different file | label from Text | implicit | YES (fillMaxWidth) | NO

### FindReplaceBar.kt
- FindReplaceBar.kt:54 | OutlinedTextField | find query input | placeholder "Find…" | implicit from placeholder | YES | NO
- FindReplaceBar.kt:71 | IconButton | previous match | "Previous match" | NONE | YES | NO
- FindReplaceBar.kt:77 | IconButton | next match | "Next match" | NONE | YES | NO
- FindReplaceBar.kt:83 | IconButton | close search | "Close search" | NONE | YES | NO
- FindReplaceBar.kt:93 | OutlinedTextField | replace text input | placeholder "Replace…" | implicit | YES | NO
- FindReplaceBar.kt:100 | TextButton | replace current | "Replace" | implicit | YES | NO
- FindReplaceBar.kt:106 | TextButton | replace all | "Replace all" | implicit | YES | NO

### TableActions.kt
- TableActions.kt:42-68 | TextButton (x6) | insert/delete row/col/table | label from Text ("Row above", etc.) | implicit | YES | NO
- TableActions.kt:43,47,51,55,59,63 | Icon (contentDescription=null) | decorative (paired with text label) | NONE | NONE | N/A | NO — null is correct here because text label carries meaning

### CharacterStyleMenus.kt
- CharacterStyleMenus.kt:42 | IconButton | open size menu | "Font size" | NONE | YES | NO
- CharacterStyleMenus.kt:60 | Icon (Check) | indicate selected size | "Selected" | NONE | Modifier.size(16.dp) < 48dp | YES — selected state also shown by primary tint (color only)
- CharacterStyleMenus.kt:87 | IconButton | open colour menu | "Font colour" | NONE | YES | NO
- CharacterStyleMenus.kt:132 | Icon (Check) | indicate selected colour | "Selected" | NONE | Modifier.size(16.dp) < 48dp | YES — selected state also shown by primary tint (color only)
- CharacterStyleMenus.kt:109-116 | Box (color swatch, x7) | pick font colour | NONE | NONE | Modifier.size(20.dp) < 48dp | YES — colour swatches convey choice by color only (text label duplicates)

### ChartView.kt
- ChartView.kt:26-87 | BarChart (Canvas) | visualise column data | NONE | NONE | N/A | YES — bar color (primary vs error) conveys positive/negative; text summary at line 48-55 provides accessible alternative

### EditorScreen.kt
- EditorScreen.kt:748 | IconButton | back | "Back" | NONE | YES | NO
- EditorScreen.kt:770 | IconButton | undo | "Undo" | NONE | YES | NO
- EditorScreen.kt:773 | IconButton | redo | "Redo" | NONE | YES | NO
- EditorScreen.kt:781 | IconButton | edit / finish editing | dynamic "Edit" / "Finish editing" | NONE | YES | NO
- EditorScreen.kt:786 | IconButton | save | "Save" | NONE | YES | NO
- EditorScreen.kt:795 | IconButton | find and replace | "Find and replace" | NONE | YES | NO
- EditorScreen.kt:799 | IconButton | more actions | "More actions" | NONE | YES | NO
- EditorScreen.kt:808-828 | DropdownMenuItem (x4+) | share/export/save-as/insert | text label | implicit | YES | NO
- EditorScreen.kt:1096-1123 | FormatAction (x4: Bold/Italic/Underline/Strikethrough) | toggle format | label prop ("Bold", etc.) | FormatAction internals UNREAD | YES | YES — selected shown by runs.all{it.bold} etc.; visual selection indicator UNREAD
- EditorScreen.kt:1124 | SizeMenu (IconButton) | font size | "Font size" | NONE | YES | NO
- EditorScreen.kt:1129 | ColourMenu (IconButton) | font colour | "Font colour" | NONE | YES | NO
- EditorScreen.kt:1134 | DocMoreMenu IconButton | more formatting | "More formatting" | NONE | YES | NO
- EditorScreen.kt:1187-1194 | FilterChip (paragraph presets) | set paragraph style | label prop | implicit | YES | YES — selected shown by FilterChip selected state (color/tint)
- EditorScreen.kt:1207-1214 | FilterChip (line spacing) | set line spacing | label prop | implicit | YES | YES — selected shown by color
- EditorScreen.kt:1225-1232 | FilterChip (list style) | set list kind | label prop | implicit | YES | YES — selected shown by color
- EditorScreen.kt:1245-1252 | FilterChip (alignment) | set alignment | label prop | implicit | YES | YES — selected shown by color
- EditorScreen.kt:1259-1270 | OutlinedButton (x2) | page break / delete paragraph | text label | implicit | YES | NO
- EditorScreen.kt:975-995 | TextButton (x2) | restore / discard draft | text label | implicit | YES | NO

### SheetScreen.kt
- SheetScreen.kt:658 | IconButton | undo | "Undo" | NONE | YES | NO
- SheetScreen.kt:661 | IconButton | redo | "Redo" | NONE | YES | NO
- SheetScreen.kt:664 | IconButton | save | "Save" | NONE | YES | NO
- SheetScreen.kt:668 | IconButton | more actions | "More actions" | NONE | YES | NO
- SheetScreen.kt:674-741 | DropdownMenuItem (x8) | share/save-as/CSV/rename/freeze/chart/sort | text label | implicit | YES | NO
- SheetScreen.kt:1129-1156 | SheetAction (x4: Bold/Italic/Underline/Strikethrough) | toggle cell format | label prop | implicit (Icon contentDescription=label) | YES | YES — selected shown by tint=primary (color)
- SheetScreen.kt:1168 | IconButton | more formatting | "More formatting" | NONE | YES | NO
- SheetScreen.kt:1209-1236 | clickable Box (fill colour swatches, x7) | pick cell fill colour | NONE (only "No fill" icon has contentDescription) | NONE | Modifier.weight(1f)+aspectRatio(1f) — dimensions UNVERIFIED vs 48dp | YES — colour conveys fill choice by color only
- SheetScreen.kt:1249-1253 | FilterChip (alignment) | set cell alignment | label prop | implicit | YES | YES — selected shown by color
- SheetScreen.kt:1264-1269 | FilterChip (number format) | set number format | label prop | implicit | YES | YES — selected shown by color
- SheetScreen.kt:1335-1354 | clickable Surface (sheet tabs, xN) | switch sheet | NONE (Text child shows name) | NONE | UNKNOWN | YES — selected tab shown by primary background color
- SheetScreen.kt:1355 | IconButton | add sheet | "Add sheet" | NONE | YES | NO
- SheetScreen.kt:795-816 | SheetGrid | grid navigation + cell edit | NONE | focusable() + focusRequester + onPreviewKeyEvent | grid is focusable; cell touch target UNREAD | NO
- SheetScreen.kt:1043 | SheetCell (per cell) | edit cell value | NONE | UNREAD | UNREAD | NO

### PresentationScreen.kt
- PresentationScreen.kt:188 | IconButton | back | "Back" | NONE | YES | NO
- PresentationScreen.kt:199 | IconButton | present slides | "Present slides" | NONE | YES | NO
- PresentationScreen.kt:208 | IconButton | add slide | "Add slide" | NONE | YES | NO
- PresentationScreen.kt:217 | IconButton | export as PDF | "Export as PDF" | NONE | YES | NO
- PresentationScreen.kt:220 | IconButton | save | "Save" | NONE | YES | NO
- PresentationScreen.kt:269 | clickable Column (slide card, xN) | select slide | NONE | NONE | Modifier.width(132.dp) + aspectRatio — height UNVERIFIED vs 48dp | YES — selected shown by primary border + primary number color
- PresentationScreen.kt:376 | IconButton | move slide left | "Move slide left" | NONE | YES | NO
- PresentationScreen.kt:382 | IconButton | move slide right | "Move slide right" | NONE | YES | NO
- PresentationScreen.kt:388 | IconButton | remove slide | "Remove slide" | NONE | YES | NO
- PresentationScreen.kt:408 | IconButton | remove line | "Remove line N" | NONE | YES | NO
- PresentationScreen.kt:416 | OutlinedButton | add a line | "Add a line" | implicit | YES | NO
- PresentationScreen.kt:458 | Surface (clickable, present mode) | tap to advance | "Slide N of M. Tap to advance." | semantics contentDescription set | YES (fillMaxSize) | NO
- PresentationScreen.kt:505 | IconButton | previous slide | "Previous slide" | NONE | YES | NO
- PresentationScreen.kt:509 | IconButton | next slide | "Next slide" | NONE | YES | NO
- PresentationScreen.kt:512 | IconButton | toggle speaker notes | "Toggle speaker notes" | NONE | YES | NO
- PresentationScreen.kt:515 | IconButton | end presentation | "End presentation" | NONE | YES | NO

---

## SECTION 1 — Text elements conveying state ONLY by color

- PresentationScreen.kt:314-318: Slide number text color = primary when selected, onSurfaceVariant otherwise. Selected state is ALSO shown by a 2.dp primary border (line 280-284), so not color-only.
- SheetScreen.kt:1345-1349: Sheet tab text color = onPrimary when selected, onSurface otherwise. Selected state IS color-only (primary background surface behind text). Users with color-vision deficiency may not perceive which tab is active.
- SheetScreen.kt:1219-1223: Fill colour swatches — choice conveyed by color only. Text label absent for swatches (only "No fill" has an icon+label).
- CharacterStyleMenus.kt:117-128: Font colour swatches — choice conveyed by color swatch + adjacent text label ("Black", "Blue", etc.). Text duplicates color, so acceptable.
- EditorScreen FormattingBar (lines 1100, 1107, 1113, 1120): Bold/Italic/Underline/Strikethrough selected state — FormatAction internals UNREAD; cannot verify if selected state has non-color indicator.
- SheetScreen SheetFormatBar (lines 1133, 1140, 1147, 1154): Bold/Italic/etc selected shown by tint=primary (color only); SheetAction internals show label prop but visual selection indicator UNREAD.

---

## SECTION 2 — Contrast risks from core/design/Color.kt

Cannot compute exact ratios without on-device measurement; flagging pairs that need verification.

Light theme:
- ZiyOnSurfaceVariantLight (#3F4946) on ZiySurfaceVariantLight (#DBE5E0) — medium gray on light gray; used for body-secondary text (RecentRow.kt:54, EditorScreen.kt:762, SheetScreen.kt:651, PresentationScreen.kt:304, HomeScreen.kt:81, etc.). Likely FAILS WCAG AA 4.5:1 for normal text. Needs measurement.
- ZiyOutlineLight (#6F7975) on ZiySurfaceContainerLight (#EDECE8) — used for dividers/outlines; may FAIL 3:1 for graphical objects. Needs measurement.
- ZiyPrimaryLight (#0E5A4E) on ZiySurfaceLight (#FAF9F6) — dark teal on near-white; likely passes 4.5:1 but unverified.
- ZiyOnPrimaryContainerLight (#00201A) on ZiyPrimaryContainerLight (#9CF2DE) — very dark on light teal; likely passes.

Dark theme:
- ZiyOnSurfaceVariantDark (#BEC9C5) on ZiySurfaceVariantDark (#3F4946) — light gray on medium gray; borderline for normal text. Needs measurement.
- ZiyOutlineDark (#89938F) on ZiySurfaceContainerDark (#222625) — may FAIL 3:1. Needs measurement.
- ZiyPrimaryDark (#81D5C2) on ZiySurfaceDark (#191C1B) — light teal on near-black; likely passes but unverified.

Material tokens involved: onSurfaceVariant, surfaceVariant, outline, primary, primaryContainer, surfaceContainer. Flag all onSurfaceVariant-on-surfaceVariant and outline-on-surfaceContainer pairs for on-device WCAG measurement.

---

## SECTION 3 — Focus/keyboard

Clickable WITHOUT `.focusable()` or `semantics` Role:
- RecentRow.kt:34 (row clickable)
- HomeScreen.kt:118 (template row clickable)
- HomeScreen.kt:236 (CreateTile Column clickable)
- SettingsScreen.kt:72 (About row clickable)
- SheetScreen.kt:1335-1354 (sheet tab clickable Surfaces)
- PresentationScreen.kt:269 (slide card clickable Column)

These are reachable only by touch, not by keyboard/Tab or TalkBack linear navigation unless the parent provides semantics.

SheetScreen.kt:803-804: SheetGrid HAS `.focusable()` + `focusRequester` + `onPreviewKeyEvent` — good keyboard traversal for the grid.
SheetGrid cell level (SheetCell.kt:1043): UNREAD — cannot verify per-cell focus.
No list/grid besides SheetGrid has explicit keyboard traversal. HomeScreen LazyColumn, FilesScreen LazyColumn, PresentationScreen SlideStrip LazyRow rely on default Compose focus behavior (clickable items are not focusable by default).

---

## SECTION 4 — Large font scale risks

Hardcoded `.sp` / fixed sizes / `maxLines` that will clip at largest system font scale:

- EditorScreen.kt:1499: `fontSize = ... ?: 11.sp` — hardcoded 11.sp document text base size.
- EditorScreen.kt:1500: `lineHeight = 16.sp` — hardcoded line height.
- EditorScreen.kt:1906: `fontSize = 11.sp, lineHeight = 16.sp` — DocumentTextStyle hardcoded.
- EditorScreen.kt:1553: `Modifier.width(22.dp)` — fixed-width line-number gutter; may clip at large font scales.
- EditorScreen.kt:1813: `Modifier.width(22.dp)` — same.
- SheetScreen.kt:1102: `fontSize = (format.sizePt ?: 13f).sp` — hardcoded 13f.sp fallback cell font.
- RecentRow.kt:44: `maxLines = 1` with Ellipsis — displayName may clip at large font scale.
- RecentRow.kt:72: `maxLines = 1` on badge text — may clip.
- HomeScreen.kt:77: headlineMedium (scales, OK). HomeScreen.kt:124: bodyLarge on template name (scales, OK).
- PresentationScreen.kt:297: `maxLines = 2` Ellipsis on slide title — may clip.
- PresentationScreen.kt:303: `maxLines = 2` Ellipsis on slide body preview — may clip.
- FindReplaceBar.kt:58: `singleLine = true` on query field — OK for input.
- FindReplaceBar.kt:97: `singleLine = true` on replace field — OK.
- SheetScreen.kt:651: `maxLines = 1` on meta line — may clip.
- PresentationScreen.kt:192: `maxLines = 1` on title — may clip.

---

## SECTION 5 — TalkBack on the home screen

- "ZiyOffice" headline (line 77): announces as text, meaningful.
- Subtitle (line 79): announces as text, meaningful.
- Search field (line 87-95): OutlinedTextField with placeholder "Search recent files" — TalkBack announces field role + placeholder. Leading search icon has contentDescription=null (decorative) — correct.
- "Open a file" button (line 99): announces button + label — meaningful.
- "Start from a template" / "Hide templates" (line 105): announces button + label — meaningful.
- Template rows (line 114-131): clickable Column with NO contentDescription — TalkBack announces the two Text children (name + description) but NOT that the row is clickable/actionable. Users learn it's a row but not that tapping creates a document.
- CreateTiles (line 143-167): Column.clickable with NO contentDescription on the Column itself. Icon has contentDescription=label ("Docs"/"Slides"/"Sheets"/"PDF") and Text(label) — TalkBack likely announces icon label + text label (duplicate) but NOT that the tile is a button/action. Role missing.
- "Recent files" heading (line 172): text only.
- RecentRow items (line 190): clickable row, NO contentDescription — TalkBack announces badge text + displayName + meta line but NOT that it's a clickable row.
- "All N files" button (line 195): meaningful.
- Footer offline text (line 204): text only.

---

## SECTION 6 — contentDescription = null on Icons that carry meaning

- SettingsScreen.kt:82-84: ArrowForward icon on the About row — contentDescription=null. This icon is the only visual indicator that the row navigates to About (no text says "About" on the icon; text says "About ZiyOffice" but the icon carries the forward/chevron meaning). TalkBack user hears the row text but the icon adds no info — the row itself has no contentDescription either (line 72), so the forward gesture is not announced. CONFIRMED PROBLEM.
- TableActions.kt:43,47,51,55,59,63: All six Add/Delete icons have contentDescription=null, BUT each is paired with a Text label in the same TextButton. The text label is the accessible name; null is acceptable for redundant decorative icons.
- HomeScreen.kt:92: Search leadingIcon contentDescription=null — decorative inside a search field with placeholder — acceptable.
- FilesScreen.kt:92: InsertDriveFile empty-state icon contentDescription=null — decorative — acceptable.

---

## SEMANTICS IMPORT GREP RESULT

Only TWO files in the app module import anything from `androidx.compose.ui.semantics`:

- PresentationScreen.kt:55 imports `androidx.compose.ui.semantics.contentDescription`
- PresentationScreen.kt:56 imports `androidx.compose.ui.semantics.semantics`
- SheetScreen.kt:90 imports `androidx.compose.ui.semantics.contentDescription`
- SheetScreen.kt:91 imports `androidx.compose.ui.semantics.selected`
- SheetScreen.kt:92 imports `androidx.compose.ui.semantics.semantics`

No file imports `Role` from `androidx.compose.ui.semantics` anywhere in the app module. The fix pass should follow the PresentationScreen.kt / SheetScreen.kt import pattern and add `import androidx.compose.ui.semantics.Role` where needed.

---

## PUNCH LIST — 15 highest-impact accessibility changes

Ordered by how many users they block. Each entry: file:line — what to add — why (which user is blocked).

1. **RecentRow.kt:34** — Add `contentDescription` + `semantics { role = Role.Button }` to the row clickable — Blind/TalkBack users cannot know the row is activatable or what file it opens without hearing all children.
2. **HomeScreen.kt:236** (CreateTile Column) — Add `contentDescription = label` + `semantics { role = Role.Button }` — Blind users cannot tell tiles are actionable create buttons.
3. **HomeScreen.kt:118** (template rows) — Add `contentDescription` + `semantics { role = Role.Button }` — Blind users cannot activate templates from TalkBack.
4. **SettingsScreen.kt:72** (About row) + **SettingsScreen.kt:82** (ArrowForward icon) — Add `contentDescription` to row and icon — Blind users cannot discover About navigation.
5. **SheetScreen.kt:1335-1354** (sheet tab Surfaces) — Add `contentDescription = sheet.name` + `semantics { role = Role.Tab }` — Blind users cannot switch sheets via keyboard/TalkBack.
6. **PresentationScreen.kt:269** (slide cards) — Add `contentDescription` + `semantics { role = Role.Button }` — Blind users cannot select slides.
7. **SheetScreen.kt:1209-1236** (fill colour swatches) — Add `contentDescription` with colour name to each swatch Box — Color-vision-deficient users cannot identify fill colours.
8. **SheetScreen.kt:1345-1349** (selected tab color) — Add non-color indicator (e.g. "Active" content description or visual underline) — Color-vision-deficient users cannot tell active sheet.
9. **CharacterStyleMenus.kt:109-116** (colour swatches) — Add `contentDescription` with colour name — same as #7.
10. **RecentRow.kt:61** (KindBadge) — Change `Modifier.size(44.dp)` to `48.dp` minimum touch target — Motor-impaired users struggle with small tap targets.
11. **HomeScreen.kt:240-244** (CreateTile icon Surface) — Change `Modifier.size(44.dp)` to `48.dp` — same.
12. **EditorScreen.kt:1499** / **EditorScreen.kt:1906** (11.sp hardcoded font size) — Replace with `MaterialTheme.typography`-scaled size — Low-vision users on large font scales get clipped or undersized text.
13. **SheetScreen.kt:1102** (13f.sp hardcoded) — Use scalable typography `sp` — same.
14. **RecentRow.kt:44** / **PresentationScreen.kt:297,303** (`maxLines` with Ellipsis) — Remove `maxLines` or increase line limit — Low-vision users get truncated file names / slide titles.
15. **EditorScreen.kt** and **SheetScreen.kt** IconButtons — Add `semantics { role = Role.Button }` to IconButtons that have `contentDescription` but no explicit Role — TalkBack may not announce button role consistently.
