# WPS UX recon: what we adopt, what we reject

**Method.** On-device observation of WPS Office 26.9.1 (Android 14, dark mode) via runtime
screenshots and the accessibility view hierarchy. Nothing was decompiled. No WPS code, assets,
icons, fonts or strings enter this repository. Screenshots stay outside the repo, in local temp
files. This document is a written spec; the implementation uses original code and assets.

## Screen inventory (observed)

### 1. Home / Files dashboard
- **Top bar:** circular profile avatar (left), search pill with an embedded scan icon
  (centre), notification bell, and a diamond (premium) icon.
- **"Files" card** (rounded container on a near-black background, subtle elevation):
  grid of tiles, 4 per row, each a coloured rounded square with a glyph and a label below:
  - Row 1 (create): **Docs** (blue), **Slides** (orange), **Sheets** (green), **PDF** (red)
  - Row 2 (locations): Images (violet), My Devices, My Documents, Download
  - Row 3: SD Card
- **"Cloud Storage" section:** a promotional WPS Cloud card with a Sign In button, then
  rows for Google Drive, Gmail, OneDrive, Dropbox, TeraBox each with a "+" connector.
- **Bottom navigation** (5 tabs, icon + label, active tab in brand orange):
  Home · Files · Scan · Discover · Me

### 2. Document list ("All Documents")
- Top bar: back chevron, left-aligned bold title, search icon, overflow menu.
- **Permission banner:** "Access to local files requires permission" with a blue
  **Grant Access** because WPS asks for broad storage access to browse everything.
- **Category tab strip** (horizontally scrollable): All · Docs · Slides · Sheets · PDF ·
  TXT · …, with the selected tab bold + colour-underlined.
- **Empty state:** centred grey page glyph + "No documents" in muted text.

### 3. Editor chrome (documents/PDF)
- Top bar: back, an "AI" diamond, page-view toggle, reading-mode toggle, comments, share,
  overflow.
- **Upsell banner** injected above the page: "You are currently using WPS Pro+ features
  for editing; please upgrade to save your changes." with a **Get it now** button.
- Bottom **contextual tool row** (Edit / Text+ / image / OCR …), where premium features
  carry a small diamond badge.
- Below that, a **mode tab strip**: Edit · Annotate · Fill & Sign · Convert · All.

### 4. Sign-in wall
Full-screen "Sign in to WPS Office": Google / Facebook / Email buttons, "More Sign-In
Options", "No account? Sign Up", and terms/privacy text. Appears unprompted in normal use.

### 5. Second pass: more screens (from the owner's own screenshots)

**Recents / file list (Files tab)**
- Segmented mode tabs: This Device · Server?/Recent · Shared · Starred (active one bold,
  with an underline).
- Time-group headers in muted text: "Today", "Within 7 days".
- Right-side controls: filter icon and a list/grid view toggle.
- File-row anatomy: coloured type glyph (PDF = red round "P" badge), bold file name,
  and a meta line of small chips (location chip like "This Device" + a "Local" tag) plus a
  star (favourite) toggle; on the right an edit icon and a vertical-ellipsis overflow.
- A large circular **orange "+" FAB** floating bottom-right above the nav bar.

**Home tab**
- Two big promo cards at the top: "Scanner, one tap, capture everything" and
  "AI Slides, AI builds your slides".
- A horizontal row of quick-action chips: "Edit PDF", "PDF to Word", "PDF Signature", …

**Me tab**
- Signed-out header: avatar + "Not logged in"; headset and bell icons top-right.
- Sign-in card ("Sign in to enjoy more features" → Sign In).
- Promo banner: "Start 3-day Free Trial".
- Chevron rows: My wallet · My template · My coupons; then a Settings row with a gear.
- Bottom nav: 5 tabs, active tab in the brand orange.

**Spreadsheet editor (Sheets)**
- Top bar: back chevron; ghosted premium icons; a "3" badge; cloud; share; hamburger.
- A **sheet-tab strip** under the toolbar ("Sheet1" + overflow "···").
- Formula bar and grid with column letters and row numbers; green selection outline.
- Bottom tool row (dark): Edit · Mobile View · Find and Replace · Tools · Edit on PC
  (icon + two-line label each; "Edit on PC" is a desktop cross-sell).
- A small floating expand icon at the grid corner.

**PDF reader/editor**
- Top bar: back, AI diamond, cloud, "3"-pages badge, reading-mode toggle, share, overflow.
- Pro+ upsell banner: "You are currently using WPS Pro+ features for editing; please
  upgrade to save your changes." + "Get it now".
- First-run product onboarding rendered as document content ("Get Started with WPS PDF…").
- Bottom tool row where premium tools carry **diamond badges** (OCR, Text+, image, …),
  and below it the mode tab strip: Edit · Annotate · Fill & Sign · Convert · All.

## Permission posture (measured, `dumpsys package cn.wps.moffice_eng`)

WPS requests **35 permissions**, including: `INTERNET`, `ACCESS_NETWORK_STATE`,
`ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_NETWORK_STATE`,
**`ACCESS_ADSERVICES_AD_ID` / `_ATTRIBUTION` / `_TOPICS`** (Android Advertising ID /
Privacy Sandbox), `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`,
`RECORD_AUDIO`, `GET_ACCOUNTS`, `MANAGE_EXTERNAL_STORAGE`, `READ_/WRITE_EXTERNAL_STORAGE`,
`SYSTEM_ALERT_WINDOW`, `POST_NOTIFICATIONS`, `NFC`, `BLUETOOTH_*`, `USE_BIOMETRIC`,
`USE_FINGERPRINT`, `FOREGROUND_SERVICE*`, `WAKE_LOCK`, `VIBRATE`, `WRITE_SYNC_SETTINGS`,
`REORDER_TASKS`, `BIND_QUICK_SETTINGS_TILE`, and more.

ZiyOffice: **0.** Everything WPS needs those permissions for, we either do with scoped user
consent (SAF file access) or deliberately don't (ads, cloud, location, contacts, background
services).

## What we adopt (structure and interaction patterns)

| Pattern | ZiyOffice implementation |
|---|---|
| Coloured type tiles with labels (Docs/Slides/Sheets/PDF) | Same idea, our own vector glyphs from the Material Symbols set (Apache-2.0), our own type colours. |
| Card-based sections with generous corner radius on a dark background | Our Material 3 `surfaceContainer*` roles; 18 dp corners; same visual rhythm. |
| Horizontally scrollable category tabs with a colour underline | M3 `ScrollableTabRow` on the Files screen (All/Docs/Sheets/Slides/PDF/Other). |
| File rows with a type badge, name, and meta line | Already implemented in M0; kept and reused on both Home and Files. |
| Bottom navigation, icon + label, single accent for the active tab | 3 tabs: **Home · Files · Settings**. Scan was dropped (the recon kept it as a placeholder for M5 OCR); OCR arrives as a feature, not a tab, in a later milestone. |
| Contextual bottom tool row inside editors | Planned in the M1 editor chrome skeleton (module-specific tool groups). |
| Calm empty states: glyph + one muted line | Adopted throughout. |
| Time-group headers on the file list ("Today", "Within 7 days") | Candidate for the Files screen once the list gets long. |
| Row anatomy: badge + name + small meta chips + overflow | Adopted (badge, name, kind/size/time line). Favourite stars and per-row overflow come with the editor milestones. |
| Segmented text tabs with underline (mode switching) | Same mechanism as the category tabs; reused for editor mode strips in M1. |
| Chevron settings rows on a plain list | Adopted on the Settings tab (About row) and for future subpages. |
| Local "Starred" favourites | Candidate later \u2014 local-only, no cloud. |

## What we reject (with reason)

| WPS pattern | Why ZiyOffice will not have it |
|---|---|
| Sign-in wall (Google/Facebook/Email) | No accounts exist. There is nothing to sign in to. |
| Cloud storage promos and Drive/OneDrive connectors | No cloud. Files live in the user's own folders. |
| Premium diamond badges + "Pro+ upgrade to save" banners | Every feature is free. Saving documents is the point of the app, not a paywall. |
| "Grant Access" to all local files (`MANAGE_EXTERNAL_STORAGE`) | Scoped SAF grants only. We never ask for the whole storage. |
| Discover feed, notification bell, push notifications | There is no content feed and no promotions; the app never notifies you about itself. |
| Avatar / account profile | Nothing to personalise server-side. |
| Advertising ID permissions | No ads, so no ad identifiers. |
| Promo cards on Home ("Scanner", "AI Slides") and quick-action chips ("PDF to Word") | No upsells or cross-sells. Every tool is already in the app, free. |
| "Start 3-day Free Trial" banners, wallet / coupons rows | There is no trial because there is no paid tier; nothing to sell. |
| Diamond (premium) badges on tools | Everything is unlocked from the first launch; no markers needed. |
| "Edit on PC" desktop cross-sell | We are not selling anything else. |
| First-run product onboarding as document content | The empty states explain the app instead; no marketing inside files. |

## Design tokens observed (approximate, for our own styling)

- Dark canvas near-black; cards one step lighter; text white + muted grey secondary.
- Corner radius on cards ≈ 16–20 dp; tiles ≈ 12–16 dp; icon squares ≈ 48–56 dp.
- Section spacing ≈ 20–28 dp; 4-column tile grid.
- Bottom navigation standard Android height with a brand accent on the active item.
- Type colours follow the industry convention (blue docs, green sheets, orange slides,
  red PDF). Colour conventions are fine to follow; the artwork is ours.

## Decisions (2026-10-18, owner)

1. **Accent colour:** teal stays. The structure is WPS-like; the identity is ours.
2. **Tab labels:** **Home · Files · Settings.** The Scan tab was removed; OCR (M5) arrives
   as a feature entry point (a create tile, say) rather than a tab, so nothing in the app
   is a dead end.
