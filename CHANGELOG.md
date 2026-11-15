# Changelog

## 0.1.0-beta.20 (21) — submitted for closed-test review, 2026-11-13

- Long-press menus: action buttons in a row of three (the folder menu's Edit folder, Open, Remove)
  sit closer together, and rows of one or two buttons are narrower still; a full row of four is
  unchanged. The folder menu has more room under its button labels. Ported from development
  `bd3f7b7` (menu files only; the rest of that commit is not in this build).
- No permission, database-schema, settings or backup-format change. Checked with unit tests; not
  yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA20_DEV_FIX_2026-11-13.md).

## 0.1.0-beta.19 (20) — submitted for closed-test review, 2026-11-13

- Bring in development `main` at `1d33360`, reconciled with the release line rather than picked
  cleanly, because the release line already had its own icon bitmap cache, badge avatar cache, dock
  photo trimming and MET weather client. Those are kept; the audit changes sit on top of them.
- Fixes: a dragged shortcut no longer stays faded after a re-bind during the drag; a widget moved or
  re-rendered during its pop-in no longer freezes part-way or turns invisible; remembered contacts
  show up in search Recent again; a shortcut named exactly what you type comes first; an app shortcut
  started from the long-press menu respects a locked or paused work profile; a translucent drawer tab
  accent from a restored backup no longer crashes the drawer; the fast-scroller thumb follows lists
  with a pinned header.
- App lock settings are redesigned: a summary card, a four-way "ask again" choice, grouped apps and a
  keyboard-safe search.
- Less work while you use the launcher (not measured on a device): icon loads that finish after their
  cell scrolled away are kept, changing one app's icon reloads only that app, badge rebuilds are
  coalesced, and swiping dock pages no longer re-lays out the home screen.
- Housekeeping: shared launch guards and appearance rules, `HomeActivity` and the settings codec
  split into smaller parts (the codec's output is pinned by golden tests generated from the previous
  codec), dead code and about 90 unused strings removed, three unused Gradle dependencies removed.
- No permission, database-schema or backup-format change. Checked with unit tests, lint and the
  instrumented suite on an API 36 emulator (same failures as beta 18 there); not yet checked on a
  physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA19_DEV_MAIN_2026-11-13.md).

## 0.1.0-beta.18 (19) — submitted for closed-test review, 2026-11-11

- Bring in development commit `dd4d788`. A widget (clock, calendar) that was dragged to a new spot no
  longer stays faded afterwards, and while it is held its old cell is dimmed to 75% instead of 30% so
  it stays readable on a light wallpaper. App icons are unchanged.
- No settings, backup, permission or database change. Not yet checked on a physical device; covered
  by a unit test only.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA18_DEV_FIX_2026-11-11.md).

## 0.1.0-beta.17 (18) — submitted for closed-test review, 2026-11-10

- Bring in development commits `7004ebc` and `bbca49b`. In search, the top-result highlight is inset
  from the screen edges so rounded corners and screen guards no longer clip it. Rows that leave the
  result list vanish at once instead of fading out, which stops old results being drawn over the new
  ones while typing.
- No settings, backup, permission or database change. Not yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA17_DEV_MAIN_2026-11-10.md).

## 0.1.0-beta.16 (17) — submitted for closed-test review, 2026-11-09

- Bring in development main `5025744`. Search in window mode is a floating card with the field as its
  header; tapping outside closes it, and with results kept near the keyboard it docks above the keyboard.
  Shortcuts that an app lists twice under the same name now appear once.
- Home menu buttons follow the card's corner radius, labels that are slightly too wide shrink instead of
  being cut, and the menu is a little wider. The pointer on an app's long-press menu stays on the flat
  part of the card edge. A folder's long-press menu uses the labelled buttons Edit folder, Open, Remove.
- No settings, backup, permission or database change. Not yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA16_DEV_MAIN_2026-11-09.md).

## 0.1.0-beta.15 (16) — submitted for closed-test review, 2026-11-08

- Bring in development commit `165b124`. On phones the home screen stays in portrait: a landscape
  phone is only about 390 dp tall, too little for the grid, dock and page indicator, so the desktop
  icons shrank to the minimum and lost their labels. Tablets and an unfolded foldable (smallest width
  600 dp or more) still rotate. The Settings, search and app drawer screens are unchanged.
- No settings, backup, permission or database change. Not yet checked on a tablet or a foldable.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA15_DEV_MAIN_2026-11-08.md).

## 0.1.0-beta.14 (15) — submitted for closed-test review, 2026-11-08

- Bring in development main `5dd5274`. An app icon's long-press menu shows at most 4 actions, and you
  choose which under Settings → Appearance → Menus (a switch per action for Home and for the drawer; a
  fifth waits until one is turned off). Home defaults are Edit, App info, Remove and Uninstall. Actions
  are always tinted chips: the wide bars from beta 13 are gone, and a short last row spreads across the card.
- The empty-space menu always shows **Remove page**. An empty page goes at once; a page with items asks
  first, removes them with it, and one Undo brings everything back. The Settings button is named
  "Settings" instead of being cut to "Chikku s…". The popup corner radius defaults to 14 dp (a radius you
  already chose is kept).
- Six new menu settings are saved in backups; older backups use the defaults. No permission or database
  change. Not yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA14_DEV_MAIN_2026-11-08.md).

## 0.1.0-beta.13 (14) — submitted for closed-test review, 2026-11-08

- Bring in development main `d587617`: long-press menus on app icons now show app actions
  (including a Folder option on app-covered folders), a short last row stretches into wide buttons,
  and the empty-space menu pairs its small buttons two to a row and stays clear of rounded card corners.
- Gestures settings are regrouped into everyday gestures and an expandable "More gestures" list,
  with a clearer accessibility-service prompt. The accessibility disclosure sheet is kept: turning
  the service on from Gestures still shows it first.
- No backup field or permission changed. Not yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA13_DEV_MAIN_2026-11-08.md).

## 0.1.0-beta.12 (13) — submitted for closed-test review, 2026-11-07

- Bring in development main `c44cab4`: a redesigned Settings front page, Backup & restore and About,
  and more compact long-press menus.
- Settings opens with the full-colour White Coat icon, a search field and a default-home status line.
  Home screen, App drawer, Dock and Folders are tiles. Backup leads with Save and Restore buttons.
  About shows the app card with "No ads", "No account" and "Works offline".
- Long-press on an app icon: shorter shortcut rows and small tinted action buttons in a fixed order
  (Edit, Swipe actions, App info, Play Store, Remove, Hide, Uninstall), with only Uninstall in red.
  Long-press on empty space: Widgets, Wallpapers and Cards as large buttons, Add page and Remove
  page side by side, and Settings as a full-width button.
- No setting, backup field or permission changed. Not yet checked on a physical device.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA12_DEV_MAIN_2026-11-07.md).

## 0.1.0-beta.11 (12) — submitted for closed-test review, 2026-11-07

- Bring in development main `36eac25`: search learns which result you pick for the first letters
  you type, and a clear habit becomes the top result. Off unless **Learn from my searches** is on;
  only the first one to four typed letters are kept, on the phone, never backed up. Details under
  [Unreleased] below.
- Development main's shortcut-icon blink fix (`0719bf4`) is not needed here: the release line
  already keeps a shortcut's icon while the same shortcut re-renders, so its version is kept.
- Signed and submitted to the existing Closed Alpha audience through the Play Developer API; Google
  review is pending. [Source, checks and submission receipt](docs/release/BETA11_DEV_MAIN_2026-11-07.md).

## 0.1.0-beta.10 (11) — closed-test update preparation, 2026-11-04

- Bring in development main as of 2026-11-04 (`ffe0f05`): speed-aware motion across home, dock,
  folders, drawer, cards and Settings; drop landing previews with a folder ring; steadier icon
  refresh after app, icon-pack and lock changes; HOME return flicker fix; White Coat app icon.
- Search top result returns to the animated tinted outline with a Top result label (development
  main's design), replacing build 10's fill-and-marker treatment.
- Keep the release line's memory work (page bind window, shared icon bitmaps), MET weather,
  search bar actions and shortcut recovery. The drop highlight still glides between cells with a
  light haptic tick, now combined with development main's folder ring and fade.

## 0.1.0-beta.9 (10) — submitted for closed-test review, 2026-10-26

- Refine search priority into a soft filled selection with an inset marker and a short reveal.
  Icon/text positions stay fixed and animation-off preferences are respected.
- Signed and submitted to the existing Closed Alpha audience; Google review is pending.
  [Source provenance, verification and submission receipt](docs/release/SEARCH_SELECTION_2026-10-26.md).

## 0.1.0-beta.8 (9) — submitted for closed-test review, 2026-10-25

- Mark the current search Go target with a tinted background, outline and Top result label.
- Choose left-to-right or right-to-left priority for compact matching/suggested app rows, with
  a pinned live preview, backup support and Follow language as the compatible default.
- Retain all previously uploaded build 8 improvements. [Provenance, checks and submission](docs/release/SEARCH_PRIORITY_2026-10-25.md).
- Submitted to the existing Closed Alpha audience; Google review is pending. Matching source is
  published and verified. Production access remains unavailable.

## 0.1.0-beta.7 (8) — upload preparation, 2026-10-21

- Combine shared settings choice/color, gesture and card/widget bottom sheets with build 7.
  Retain existing values and live previews, with consistent insets, neutral colors, selected-row
  visibility, stable filtered lists and nested back navigation.
- [Build provenance, verification and upload status](docs/release/PLAY_UPLOAD_2026-10-21_BUILD8.md).

## 0.1.0-beta.6 (7) — upload preparation, 2026-10-21

- Combine the pending shortcut-icon editor and drag settling/highlight/haptic improvements with
  the build 6 changes. Keep shortcut labels out of the picker while their profile is unavailable,
  and cover edited Home/folder icons through portable restore.
- [Build provenance, verification and upload status](docs/release/PLAY_UPLOAD_2026-10-21_BUILD7.md).

## 0.1.0-beta.3 (4) — 2026-10-18

- Reduce the open-folder title size; the settings preview uses the same dimension.
- Recover restored stable-Firefox URL shortcuts as ordinary pages when Android no longer holds
  their pinned intent. Explain how to re-add opaque shortcuts; preserve all saved identities.
- Integrate development main `4ba391fc377af4936bef4b0764cad9cccde99982` into the existing
  Play closed-test candidate. [Release verification and limits](docs/release/MAIN_REFRESH_2026-10-18.md).

## Source download preparation — 2026-10-16

- Prepare verified source downloads, manual joining instructions and retained per-revision pages;
  add an explicit source-link copy action to the offline About reader. Hosting approval is pending.
- Verify historical APK upgrade, real emulator reboot, fresh-install restore and Android file-picker
  backup round trip. Preserve failed fixture attempts and wider acceptance limits in the
  [dated report](docs/release/SOURCE_AND_ACCEPTANCE_2026-10-16.md).
- Release the invisible dock's layout space while safe-mode or failed-layout recovery is shown,
  so its actions fit with the non-default HOME banner. Preserve normal loading and existing
  dock settings/content. Two regression assertions fail before the fix and pass afterward.
- Report a fresh widget binding's current size even when its cell was already measured, so a
  provider restored or rebound into unchanged geometry receives size options without a resize.

## MET weather preparation — 2026-10-16

- Replace forecasts with MET Norway and city search with offline GeoNames data. Preserve saved
  locations/backups, label the next-24-hour range, respect provider caching/throttling and bundle
  full CC BY 4.0 terms. [Verification and public-policy status](docs/release/MET_NORWAY_SWITCH.md).

## Private Play preparation — 2026-10-15

- Preserve development history under its renamed private repository; create a separate private
  release snapshot from newer local main with fresh ancestry and commit-window checks.
- Bundle full privacy/GPL/Apache/asset/runtime/native notices with an offline About reader;
  audit exact transitive dependencies and prepare verified matching-source packaging.
- Prepare code2/0.1.0-beta.1, external upload signing guards and release Google-app fallback.
- Require disclosure consent from the Settings accessibility entry before Android handoff.
- Record230core/1054app/79host and five API36 Cards tests; production and external-distribution
  gates remain open. No signed upload bundle, production submission or public repository.


All notable changes to Chikku Launcher are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions will follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) once stable releases exist. No production
release has been published yet; dated closed-test releases are recorded above.

Entries under "In progress" describe work that has started but is not complete or not yet verified on
a device. Per-requirement status and verification evidence live in
[docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md).

## [Unreleased]

- **Shortcuts in the long-press menu respect a locked or paused work profile.** Starting an app
  shortcut from the menu now refuses, like every other launch path, when its profile is locked or in
  quiet mode. Unit tests only; not yet checked on a device.

- **Fixes found during a code audit.** A drawer tab accent colour with transparency (possible only
  from a restored backup) no longer crashes the drawer and its settings page. An icon picked up for
  a drag while it is still fading in stays dimmed, and an item that starts moving or is emphasised
  mid-fade no longer stays invisible. Rotating the standalone search screen keeps shortcut results
  refreshing. Shortcut icons and the icon picker now notice an icon-pack update. Unit tests only;
  not yet checked on a device.

- **Less work while you use the launcher** (not measured on a device): an icon that finishes loading
  after its cell scrolled away is kept instead of loaded again; changing one app's icon no longer
  reloads every icon from scratch; notification badges update at most once per 150 ms burst and
  unchanged badges are not redrawn; typing in drawer search refreshes the list once per keystroke
  instead of twice; search matches apps once per input; swiping dock pages no longer re-lays out the
  home screen. Unit tests only.

- Housekeeping, no behaviour change intended: launch guards, shortcut queries, the two HTTPS
  clients, drawer/folder/search-bar appearance rules (now shared with their settings previews),
  item motion and several Compose building blocks each live in one place; `HomeActivity` and the
  settings codec are split into smaller parts; three unused Gradle dependencies are removed (Compose
  UI tooling and tooling-preview, Room testing).

- **Search: remembered contacts show up in Recent again.** A contact opened from search is looked
  up by its saved contact key, as intended. Before, the key was searched as if it were a name, so
  the person was usually not found and each lookup also scanned the whole address book.
  "Did you mean" contact suggestions now rank names by typo distance only, without mixing in the
  provider's unrelated matches. Unit tests only; not yet checked on a device.

- Housekeeping, no behaviour change intended: removed the old View-based folder editor that HOME
  never showed (the Compose editor is the only one), an unused restore flag for card widgets
  (a restore already gives each card widget a new identity, which forces a fresh widget ID), and
  about 90 unused strings, 15 unused drawables and a few unreferenced helpers. Unit tests and lint
  only; not yet checked on a device.

- **Search: a shortcut named exactly what you type comes first.** Typing "x" now opens a browser
  site shortcut named "X" instead of an app that only contains an X ("Galaxy Enhance-X"). An app
  named exactly the query still wins, and a learned habit needs to be overwhelming to take the top
  from an exact-name app or shortcut. Unit tests only; not yet checked on a device.

- Long-press menus on an app icon show at most 4 actions, and you choose which. Settings → Appearance →
  Menus has a switch per action for Home and for the app drawer (Edit, Swipe actions, App info, Play
  Store, Remove, Hide, Uninstall; the drawer has no Swipe actions or Remove). A fifth switch waits until
  you turn one off, and the preview shows exactly what the menu will. New defaults on Home: Edit, App
  info, Remove, Uninstall (Swipe actions is off); the drawer keeps Edit, App info, Hide, Uninstall. An
  icon that covers a folder keeps its Folder action and drops the lowest-priority action if needed.
  Six new settings are saved in backups; older backups use the defaults. Not yet checked on a device.
- The empty-space long-press menu always shows **Remove page**. An empty page goes at once; a page with items
  asks first ("The 3 items on this page will be removed…") and one Undo brings back the page and everything
  on it. Your only page can’t be removed and says so.
- The empty-space long-press menu names its last button "Settings" so it is no longer cut to "Chikku s…",
  and the popup corner radius defaults to 14 dp instead of 20 dp (a radius you already chose is kept;
  change it under Settings → Appearance → Menus).
- Settings → Gestures is reorganised: six everyday gestures stay open and the ten rarer ones sit under
  "More gestures · n of 10 set" (it opens by itself when the practice pad detects one of them). Unset
  gestures read "Not set", rows bound to Lock screen or Recent apps say whether the accessibility
  service is on, and the service card only asks for it once such an action is bound. The practice pad
  lost its dropdown, follows the row you tap, and is taller. Icon swipe rows show how many icons and
  apps have actions. Not yet checked on a device.
- App-icon actions are always the same tinted chips (glyph over name). A short last row (for example
  4 + 3 or 3 + 2) spreads its chips across the card instead of becoming wide bars. Not yet checked on a device.
- The empty-space long-press menu puts its small buttons two to a row, so with one page "Add page" and
  Settings share a row instead of taking one each. A button left alone still spans the full width.
- The empty-space long-press menu keeps its buttons clear of the card's rounded corners at any corner
  radius, instead of clipping the bottom row. Unit tests pass; not yet checked on a device.
- The long-press menus are more compact. On an app icon, shortcut rows are shorter and the actions
  are small tinted buttons in a fixed order (Edit, Swipe actions, App info, Play Store, Remove, Hide,
  Uninstall), with only Uninstall in red. On empty space, Widgets, Wallpapers and Cards are three
  large buttons, Add page and Remove page sit side by side, and Settings is a full-width button.
  A short last row of app-icon actions (for example 4 + 3) now stretches to the full width instead
  of sitting centred with gaps. Unit tests, lint and the debug build pass; not yet checked on a device.
- Settings front page, Backup & restore and About are redesigned. The front page opens with the
  full-colour White Coat icon, a search field and a default-home status line, and shows Home screen,
  App drawer, Dock and Folders as tiles. Backup leads with Save and Restore buttons; About shows the
  app card with "No ads", "No account" and "Works offline". No setting, string or behaviour was
  removed. Checked with unit tests, lint and the privacy gate; not yet checked on a device.
- Search learns what you pick for the letters you type. With **Learn from my searches** on, opening
  a result after typing remembers the first one to four letters you typed (only those, never the
  rest of the query). Among equally good matches, the one you pick more often comes first. A
  result you pick again and again becomes the top result, the one **Go** opens, even if another
  name matches the letters better. A contact you pick becomes the person card, and a shortcut or
  folder moves to the top. One typed letter needs 3 picks and 70% of picks among the results
  shown. Two letters need 2 picks and 60%. Three or more letters need 2 picks and 50%. The habit
  must also be 1.5 times the runner-up, so the top result doesn't flip back and forth. An app
  whose name is exactly what you typed stays on top unless 80% of your picks went elsewhere. A sum
  keeps the calculator result first. Learned letters fade like the rest of search history (half
  every 14 days), are dropped with it, and are never backed up or logged. Typed text with no letter
  (numbers, PINs) is not learned. Unit tests only; not yet checked on a device.
- New app icon, "White Coat". It shows a rounded-square phone whose ivory frame is Chikku's white
  coat, wearing his tan ears with the right ear flicked out and folded. A slate home screen shows a
  2×2 app grid in amber, sea-green, burnt orange and periwinkle above a cream dock. The background
  is now `#1F1D1C`. In blind tests, simulated users who saw only the icon read it as a launcher
  (5/5 in two fresh rounds), and all described a dog. It replaces the Nose notch mark in the
  launcher icon, the themed (monochrome) icon and the splash screen. Not yet checked on a device or
  with real people. [Design notes, test log and limits](design/branding/white-coat/README.md).
- Fix reshaped icons (Settings > Style > Icons, any non-system shape, notably with "Reshape legacy
  icons") drawing as a small, cropped picture in the top-left of the shape, and folder previews
  losing their mini icons: the shaped icon drew its adaptive layers relative to the canvas origin
  instead of its own bounds. Icons are now centred in the shape on Home, dock, drawer, folder popup
  and folder previews. Checked on a Redmi M2004J19PI (MIUI, Android 11) for Teardrop, Circle,
  Squircle and Square with reshape on, and a shape with reshape off: Home, dock, dock folder
  preview, open folder and drawer. Not checked: drawer plates on a non-white background, folders
  with three or more apps, RTL and font scale.
- Typing the first letter in the app drawer search no longer blanks the grid and flashes "Loading
  apps…" for a moment; the previous list (tabs, scroll position and all) stays until the filtered
  results replace it in place, and a "No matches" message is never shown for an older query while
  you edit the text. Loading is shown only while the app list itself is loading.
- Fix a daily calendar icon (Google Calendar's "4") drawing as a white square plate with a stray
  outline offset up and left on MIUI, whose icon mask is a plain square: raw adaptive icons are now
  drawn by Chikku as a circle inside their own bounds on Home, dock, folders, drawer and Hidden apps.
  App-menu shortcut icons, the icon picker's pack-app icon, widget picker icons, the intent chooser
  and card-widget app rows, and the Settings "System default" shape preview use the same wrapper on
  square-mask ROMs; other devices are unchanged. Verified on a Redmi M2004J19PI (Android 11): drawer,
  folder preview, open folder and Hidden apps; the dock, a plain Home cell and the work-profile badge
  were not checked.
- Dropping an icon no longer leaves a blank cell for a moment (measured on a phone: about 130 ms
  before, about one frame after). A reordered folder entry shows at its new cell the instant the drag
  shadow ends. An app dropped on a free Home cell (from the drawer, search, a folder or elsewhere on
  Home) shows there as an icon that lands with the same settle bounce until it is stored, and then
  hands over to the real icon. If a drop is rejected or never stored, the icon shows briefly at the
  target and fades back. The hand-over uses a real icon view, not a screenshot, so nothing of the
  dragged app is kept as an image, and it is released as soon as the drop is stored, rejected or
  cancelled. Not changed: widgets and widget placeholders, drops onto another icon (folders), the
  Remove target, and targets outside the visible dock page. A folder icon, or any icon whose
  drawable cannot be copied, dragged to a free cell gets no landing icon and behaves as before. A new
  drag started during the 0.3 s landing ends it at once.
- Installing, updating or removing an app no longer flashes the Home, folders, drawer, search and
  cards surfaces to a loading state (labels, work badges, drawer scroll position, an open folder
  or a fast-scroller drag stay put). Profile lock, pause and removal events still clear that
  profile's apps immediately.
- Fix app icons and shortcut icons that stayed stale on Home. An app update that changes its
  launcher icon now refreshes only that app's icons (workspace, dock, drawer, folder popup and folder
  previews) without flashing; icons that fell back or were first drawn before the app was known are
  retried; pinned shortcut icons hide and restore as soon as an app is locked or unlocked, including
  right after startup; the first icon theme after a slow start now reloads icons; pinned shortcut
  icons no longer blink when the icon theme changes; and a view reused between a folder and an app no
  longer keeps the wrong icon. Device checks were not run.
- Fix stale or inconsistent UI state: editing an app from drawer or universal search no longer
  clears its custom name on Done; the app long-press menu closes when the app is locked or leaves
  the app list; Compose app icons reload when the icon theme changes; an open folder restyles when
  the theme panel colour, wallpaper tint or accent changes; the icon-pack lists refresh when a
  package is installed or removed; search shortcut results are re-read when apps change their
  shortcuts; the Folders closed-tile preview follows the home label settings; sample preview icons
  follow the icon shape; and the Hidden apps grid uses the drawer's effective icon size.
  Checked by unit tests only; no phone check yet.
- Folder motion polish: dragging an entry out of an open folder (or opening another folder) now
  fades the panel out instead of cutting it away; changing page slides the old page out and the new
  one in; adding or removing an entry pops it in or out while the others glide to their new cells,
  and a title or icon update no longer rebuilds the grid or interrupts running motion. Dropping an
  entry into a new place inside a folder gives drop feedback, and each cell shift while reordering
  gives a light tick. All of it follows the animation speed setting and is skipped with animations
  off. The cover "Tap opens" row in the folder edit sheet now collapses with its content.
  Device checks (frame timing, haptic strength, TalkBack) are not done; covered by Robolectric
  unit tests only.
- Home drag and drop feels finished: a dropped icon no longer flashes back at its old cell but
  stays hidden until the move is stored, then pops into the new cell; the drag image is slightly
  enlarged; the landing spot is previewed live (a ring and scale-up on an app or folder that will
  take the drop, the real free cell when pulling an entry out of a folder); icons glide to new
  cells, fade in when added and out when removed; dock page changes slide and fade; the remove
  target, undo bar and dock toggle fade instead of flipping. Dimmed (unavailable) icons keep their
  dimming after a drag. All of it follows Animation speed and the system "Remove animations".
- Haptics now confirm only what happened: page/dock edge hover ticks only when the page moves,
  a drop buzzes only once the layout shows it (rejected drops stay silent), the remove target and
  widget resize steps give a tick, and a stored resize gives a confirming buzz. The widget resize
  frame fades and glides to the snapped size following Animation speed.
- Settings lists now move instead of snapping. Cards: the dragged card keeps its lift while it
  glides into its slot, with haptics on pickup, each slot crossed and drop, and the page scrolls
  when a card is held near its edge; add, delete, undo and the move up/down menu glide too.
  Hidden apps: cells show pressed feedback, tick at once with a short animation and haptic, and
  filtering or unhiding in "hidden only" slides cells instead of jumping; the hint no longer
  collapses when that would leave the list unscrollable. Drawer groups, contact card buttons and
  app pickers animate reorder, add and remove; onboarding steps slide. Compose screens (settings,
  icon picker, onboarding) now follow Appearance → Motion → Animation speed, including None.
  Device checks (haptics, drag feel, TalkBack) were not run.
- Smoother search, cards and drawer motion, all following Settings → Look and feel → Animation
  speed (and Android's "Remove animations"): search app tiles keep their icons while you type and
  result rows are keyed by identity and slide into place; the top-result highlight and its label fade
  instead of popping and the tile height no longer changes; the HOME cards panel slides out on Back,
  scrim tap and drag-dismiss (continuing from the drag), keeps scroll position, hosted widgets and
  unchanged cards when its list updates, and springs back with Motion; the paged drawer cross-fades
  re-chunked pages; list and page item animators use the animation speed; drawer tabs fade the list
  and pill, update renamed tabs in place and fade the empty/status block; the fast scroller bubble,
  wave and nudge use Motion, no longer end a rail drag on unrelated drawer updates, and restore
  clipping afterwards. Not verified on a device.

- Choose which side starts app priority for one-handed search: Settings → Search → Apps and
  shortcuts → App priority direction, with **Left to right**, **Right to left** and the existing
  **Follow language** default. Applies to compact matching/suggested app rows, keeping the best
  match highlighted and selected by Go. The pinned preview updates immediately; backups include
  the choice. [Verification](docs/IMPLEMENTATION_STATUS.md#search-top-result-highlight--2026-10-25).

- Highlight the current typed-search keyboard Go target with a tinted background, accent outline
  and **Top result** label, including compact apps and contact cards. The marker follows live
  ranking and clears while results are stale. The synthetic Results preview reflects the styling.
  Local verification and phone-check limits are recorded in
  [implementation status](docs/IMPLEMENTATION_STATUS.md#search-top-result-highlight--2026-10-25).

- Fix intermittent redraws when returning to Home after using another app. HOME's state
  observation now stays active for its ViewModel's lifetime, avoiding a fresh app/workspace
  loading cycle that briefly changed folder covers into content previews. Background profile
  invalidation and lifecycle cleanup remain covered. [Verification and limits](docs/IMPLEMENTATION_STATUS.md#home-return-flicker--2026-10-21).
- Home search bar actions. Settings → Search → Home screen search bar has a new **When you tap**
  section. Choose whether tapping the bar opens Chikku search or your web search engine (for
  Google, the Google app's search, or google.com when the app is missing). Place the Chikku button
  (always Chikku search) and the engine's button (always the web) on the left or right, or turn
  either off. The pinned preview redraws the real bar on every change. Each button is its own
  48dp TalkBack target, App lock still applies to the receiving app, and older settings and backups
  keep their look. Verified locally on 2026-10-21 with JVM/Robolectric tests only. Not yet checked
  on an emulator or a physical device, including the Google app handoff.

- Removing or moving a folder member that was its click action now selects the first remaining
  app or shortcut in stored order. An empty folder returns to opening normally. The action and
  removal share one Undo; unrelated tap actions and swipe actions are preserved. Regression
  coverage includes restart, full target identity, rejected moves and portable backup/restore.
  Integrated onto current main on 2026-10-18: debug/release-bundle builds, 231 core tests,
  all 1,105 app tests, 79 script tests, lint and privacy passed. Physical-device verification
  is pending; the release bundle is unsigned and has not been uploaded to Google Play.
- Cut HOME's resident memory. Icons are rasterised once into shared immutable bitmaps held in a
  byte-bounded cache (1/8 of the app's memory class, 16–64 MiB) instead of one mutable adaptive
  icon per view; only the current workspace page and its neighbours hold real icon, folder and
  widget views (other pages keep same-size placeholders and bind as the pager approaches; parked
  widget host views are reused, since the widget host API cannot release them); the closed drawer
  detaches its rows after one second and rebinds as the open gesture starts; the process now
  answers `onTrimMemory` (icon cache, dock photo, drawer); the dock photo is masked once instead of
  through a per-frame layer; notification badge avatars are cached and compared by content; folder
  search reuses the ViewModel's workspace model instead of building a second one. Verified locally
  on 2026-10-21: full unit suite (`:core:model:test`, `testDebugUnitTest`), debug build, lint,
  androidTest compile and the privacy gate. Phone measurement (Galaxy S24 Ultra, Android 16,
  debug build, default layout, forced GC before each sample, `dumpsys meminfo` PSS): home idle
  183 → 161 MB, drawer open 216 → 192 MB, drawer scrolled 278 → 212 MB, drawer closed again
  262 → 197 MB, after `RUNNING_CRITICAL` trim 263 → 187 MB; native heap with the drawer closed
  93 → 38 MB and bitmap memory 63 → 9 MB. Multi-page layouts and widgets were not measured on the
  phone; the drawer rendered without placeholders or visible quality loss.
- Fix a crash when a browser or app asks to add a shortcut to the home screen (Firefox "Add app to
  Home screen"): the confirm card's activity has no content view, and Chikku's prompt crashed
  without a lifecycle owner. Prompts now bring their own view-tree owners, so they show in any
  activity. Verified on 2026-10-21: new `ChikkuPromptsHostTest` fails without the fix and passes
  with it; full app/core unit suites and lint pass. On a Galaxy S24 Ultra (One UI, Android 16), the
  fixed debug build as HOME showed the card for x.com from Firefox and placed the shortcut; the
  installed release beta.4 crashed on the same request.
- Pinned shortcuts (for example Chrome or Firefox website shortcuts) get a long-press menu with
  **Change icon** and **Remove**, on HOME and inside folders. Change icon opens the icon picker for
  that one shortcut: any icon from an installed icon pack, or back to the icon the shortcut came
  with. The choice is stored on the shortcut's workspace entry, is undoable, already travels in
  backups, and is kept when the shortcut is dragged out of a folder. Before, a long press on a
  shortcut only started a drag. Re-rendering HOME no longer flashes shortcut icons to the
  placeholder.
- Smoother drag and drop on HOME: the dragged icon or widget lifts exactly where the finger holds
  it (it used to jump so its centre was under the finger) and is drawn opaque; the copy left
  behind fades; the drop highlight glides between cells; and on drop the item moves from under the
  finger into its cell instead of flashing back to its old cell until the move was stored. A move
  that is not stored (refused, or dropped on the same cell) returns it the same way. Haptics: a tick
  as the drop target moves to another cell, a tick on reaching Remove, and a pick-up tick when a
  drag starts from an open long-press menu (icons, folder entries, drawer and widgets), in addition
  to the existing long-press and drop feedback. All of it follows the animation-speed setting and
  Android's touch-feedback setting. Verified with JVM/Robolectric tests only; frame pacing and the
  feel of the haptics on a phone are not yet checked.

- Folder icons on HOME redraw when an icon inside them changes (a new per-app icon, icon pack or
  shape, or an app updating a pinned shortcut's icon). Previously they kept the old preview until
  the folder's contents changed.
- Reordering inside an open folder now moves: the dragged app lifts out of its cell, the others
  slide aside to show where it will land (after a short hover, so passing over does not shuffle),
  and it pops into place with a small bounce. Alphabetical folders lift the app but do not shuffle.
  Animation speed "None" and the system "Remove animations" setting skip the motion. Verified with
  JVM tests only; not yet tried on a phone.

- Reduce open-folder titles from 18sp to 14sp so they sit closer to app-label typography. The
  Settings folder preview uses the same size; rename touch targets and system font scaling remain.
  Verified locally on 2026-10-18: debug APK build, 55 folder/preview JVM tests, lint (retry after a
  concurrent-build intermediate-file failure) and privacy gate. Physical screenshot comparison is pending.

- Handle website shortcuts after a Chikku reinstall/restore: Firefox shortcuts whose saved ID is a
  web URL can open that page in Firefox when Android's pin record is missing. Other missing shortcuts
  now explain how to add them again; Backup explains the limitation. Layouts and shortcut appearance
  are preserved. Newer Firefox UUID shortcuts require re-adding from Firefox. [Scope and checks](docs/RESTORED_SHORTCUTS.md);
  full local unit suite, debug build, lint and privacy gate passed on integration with main. Actual
  Firefox launch/reinstall testing on a phone remains pending.

- Threema joins the contact card providers (`ch.threema.app`, the Google Play and Threema Shop
  build). **Threema** opens the chat, and **Threema call** can be added in Search › Contacts ›
  Contact card buttons; it is the first app call Chikku can offer, because Threema does not require
  `CALL_PHONE` for it. Tapping it starts the call in Threema. Both entry points were verified in
  Threema's source (commit `d44a2b5`); no Threema build was installed on the test phone, so they
  are covered by unit tests only. Threema Libre and the HMS build reuse the same contact mimetypes
  under other package names and are not supported.

- Contact card app buttons (WhatsApp, Signal, Telegram) now match Call and Message: each shows the
  app's own single-colour glyph from its themed icon (Android 13+), tinted in the same accent colour,
  instead of the full launcher icon, which disappeared into the dark button with themed or
  icon-pack icons. No brand artwork is bundled; an app without a monochrome icon, or Android 12 and
  older, gets a generic chat glyph and the label still names the app. Telegram is a new card
  provider: **Telegram** opens the chat (entry point verified on Telegram 13.0.2's installed
  manifest and in its source); its calls need `CALL_PHONE` and are not offered. Checked on a Samsung
  phone (Android 16, 1080×2340) for WhatsApp; Signal and Telegram buttons are covered by unit tests
  only. The pre-existing `SearchResultsAnchorTest.slowDragScrollsResultsInBothModes` fails in the
  full unit run on main as well (passes alone).

- Choose the home search bar's icon (Settings → Search → Home search bar → Search icon): Dog nose
  (default), Paw, Floppy ears, Dog face, Nose notch, Chikku mark or Plain. All are original
  single-colour magnifiers that follow the bar's contrast colour. They replace a dot-grid glyph that
  looked like another launcher's and suggested app-only search; the whole bar still opens universal
  search. The pinned preview redraws the chosen icon. Written to backups; older backups restore as
  Dog nose. The card editor's "App shortcut" menu item now uses the launch-app icon. Covered by
  model and Robolectric preview tests; not yet checked on a device.

- Search results can start near the keyboard for one-handed reach (Settings → Search → Results
  start). Near the keyboard, the best-match section (suggested apps, top matches, person card,
  shortcuts) sits just above the keyboard and later sections stack upward, each heading still above
  its items; TalkBack reads the best match first. "Under the search field" keeps today's layout and
  stays the default, so existing setups don't change. Written to backups; older backups restore as
  "Under the search field". The pinned search preview draws a sample keyboard and moves the sample
  results with the setting. Covered by Robolectric tests, full unit suite, lint and privacy gate;
  checked on a Samsung phone (1080×2340) with SwiftKey for empty and typed queries. TalkBack order
  is covered by a semantics test only, not yet checked with TalkBack on a device.

- Make cards easier to reach and to start. A new "Open cards" gesture action opens the Cards panel
  from any home, icon or folder gesture (no default binding changes; card action slots don't offer
  it). "Add card" starts from a template: Blank, Morning (weekdays 06:00–10:00), Headphones or car
  (media while Bluetooth audio is connected), Charging, or Weather (online weather stays off until
  turned on). An unconfigured weather card says what's missing and offers "Set up weather" instead
  of a Refresh button that couldn't work, and makes no network request. The empty panel now
  distinguishes no cards, all cards off and none matching right now, with one action each.
  Covered by JVM/Robolectric tests, full unit suite, lint and privacy gate; not yet checked on a
  device.

- Settings choice dialogs now use the same rows as the gesture action picker: a tinted selection
  with a check mark, and they open with the current choice in view. The practice "Choose gesture"
  list is grouped (One finger, Two fingers, Double tap, Home), with a motion icon for each gesture
  and the action it currently runs shown underneath. The search engine chooser shows the web address
  each engine searches, taken from the URL Chikku actually opens; the Dock and Search pages now share
  one chooser. The label font chooser draws each option in its own font, using the same mapping as
  home labels, through one chooser shared by every surface. New gesture icons are original artwork,
  apart from two Material arrows (Apache 2.0). Covered by JVM/Robolectric tests only so far.

- Redesign the gesture action picker (icon swipes, home gestures, folder actions). Actions are
  grouped under Chikku, Apps, System and Voice headings, each with an icon. The chosen action gets
  a tint and a check mark. "Use default" says what the default currently does. App… and App
  shortcut… end in a chevron and name the chosen app. Lock screen and Recent apps say they need the
  accessibility service. The list opens with the selected action in view. Only the display order
  changed; saved values and backups are unaffected. Covered by JVM/Robolectric tests; the user
  checked it by hand on a Redmi Note 9 Pro (M2004J19PI, MIUI) on 2026-10-15.

- Icons with a swipe-up or swipe-down action now follow the finger by up to 10dp (eased, so the
  pull gets stiffer), give one haptic tick once the swipe has gone far enough to run, and spring
  back on release. Icons without a swipe action stay put. When system animations are off, only
  the haptic remains. The icon swipe practice pad in the action editor moves the same way. Covered
  by JVM/Robolectric tests; the user tried it by hand on a Redmi Note 9 Pro (M2004J19PI,
  MIUI) on 2026-10-15 and reported it works well. No instrumented device test was run.

- Android audit fixes (2026-10-15): guard Discover/Store/voice handoffs and shortcut metadata;
  retain widget allocations across HOME recreation; align supported page growth/export and
  preserve private recovery; cancel stale drag/search actions; protect Quick start layout edits;
  isolate blocking restore dependency checks; recover default-page selection with page-delete Undo.
  Targeted regression coverage and verification limits: [audit fix validation](docs/ANDROID_AUDIT_FIXES_2026-10-15.md).

- Review follow-up (2026-10-15): quarantine disposable profile-acceptance AVDs after interrupted
  or unconfirmed native runs instead of reporting user-list cleanup as HOME/settings restoration.
  All three host runners reject quarantined devices. Wait for genuine startup stability in
  successful external-widget HOME sessions instead of accidentally accumulating crash-loop flags.
  Host regression checks are separate from unavailable Android compilation/device reruns;
  production release remains NO-GO with the existing acceptance gates open.

- Give Cards “Add card” an explicit localized accessibility name and disabled semantics. API29
  native fixtures now verify empty/populated real activation/dismissal, clickable state, 48dp
  bounds and preference preservation using synthetic in-memory dependencies.
- Fail closed on private, inaccessible and changed profile snapshots before and after reading
  app labels. Private Space UI remains outside this milestone; complete real profile transitions
  remain unverified.
- Add guarded isolated acceptance build overrides, historical development-APK upgrade/restore
  and genuine external-widget/profile fixtures, with serialized host runners and complete
  asynchronous ownership/cleanup checks. Normal IDs, versions/signing, Room4 and backup format1
  remain unchanged; no production permission or dependency was added.
- Record the 2026-10-14 cloud evidence and remaining failures: real version replacement and
  service restore passed on API29; reboot, SAF, configured external widgets and broader profile/
  hardware acceptance remain open. Final runtime artifacts were lost in an unexplained cloud
  environment reset. **Production release remains NO-GO.** See
  [cloud validation](docs/CLOUD_RELEASE_VALIDATION_2026-10-14.md).

- Stabilize app-picker search and drawer-grid preview unit checks with fresh activity-bound
  Compose frame clocks and explicit state notifications. The fixtures now read updates after
  their virtual frames; the existing behavior assertions and application code are unchanged.

- Prepare project wording for a future public repository: describe Chikku directly in the
  introduction, requirements and roadmap, generalize contribution guidance, and rename the
  extended-settings resource file. Keep dated research, attribution, licensing notes and
  icon-pack discovery identifiers. No repository has been created or published by this change.

- App drawer → Grid & icons: changing the row count or Fit rows switches the preview to the full
  grid scaled to fit, so fitting five rows shows all five instead of one row and a clipped second
  row. The preview still opens at actual size (the whole grid only fits at about a quarter scale)
  and returns to it when the icon or label size changes; a Full grid / Actual size button switches
  by hand. Both modes follow slider drafts and keep the preview height fixed. Regression coverage
  includes lower-row pixels, draft updates, mode switching, constrained height, large text and RTL.

- With page-dot auto-hide on, the dots now fade in as soon as a page swipe starts. The swipe asked
  them to appear on every frame, and each request restarted the fade-in from the beginning, so on a
  first swipe they stayed hidden until the page stopped moving. The app drawer's dots had the same
  fault. Robolectric skips the fade, so no unit test covers this; checked by hand on a Galaxy S24 Ultra
  (SM-S928B) on 2026-10-14.

- With page-dot auto-hide on, the home screen no longer leaves an empty band above the dock. The
  hidden dots now keep only the room they draw in (about 22dp instead of a 48dp touch target);
  visible dots keep the full touch target. The drawer and folder dots follow the same rule. A unit
  test covers the height. Installed on a physical phone; appearance there not yet confirmed.

- A swipe up or down on the home screen no longer switches page by accident. The home pager now
  starts paging only on a clearly sideways movement (within about 27 degrees of horizontal, was
  about 37), so a curved thumb swipe is left to the swipe gestures. A release that moves mostly
  vertically no longer counts as a page fling. The Discover feed swipe on page 0 uses the same
  rule. The app drawer keeps its looser rule, and the Settings paging preview follows whichever
  surface it shows. Unit tests cover both rules. Installed on a physical phone; gesture feel there
  not yet confirmed.

- Settings previews that were too small to show their setting now draw at true size. Dock →
  Spacing, Icons and Background show the real dock edge to edge (spacing shaded); Dock → Search bar
  and the home search bar pages show the real bar at true size instead of a phone shrunk to under
  half size. App drawer → Grid, Tabs and Search use true-size drawer cells, tab bar and search
  field, and lose page buttons that had nothing to do with them. The App menus preview is near real
  size instead of about a fifth. The Gestures preview follows "Only on default page", the badge
  preview uses the launcher's badge colour in dark mode, and the home grid preview's dock follows
  the grid side space. Robolectric tests cover each preview. Checked on an emulator: Dock Spacing,
  Icons and Background, the home search bar page, App drawer Grid, App menus and Home grid. Not
  checked: the drawer Tabs and Search pages, Badges, Gestures, dark mode, RTL, large text, TalkBack.
- The List drawer style is easier to read. Rows put the name first: a 17sp label and a 40dp icon,
  where the grid uses 12sp and 52dp. A narrow column shows each letter once, beside the first app
  of its group, so a long list reads in chapters. The current group's letter stays pinned at the
  top while its apps scroll past, and the next letter pushes it up. Search results and manually
  ordered groups show no letters. "Fit rows to screen" no longer applies to List style. With 5 rows
  it had stretched each app to a fifth of the screen. The List sample in Settings → App drawer shows
  the new rows and letters.
- The scroll bar's letter bubble now names the apps beside it, in the List style and the vertical
  grid. It used to show the top row's letter while sitting next to a different group. In a grid,
  a row is named by its first app. List rows no longer shrink aside for the bubble.
- Dragging the alphabet rail bulges the letters near your finger toward the apps, and you feel a
  tick on each new letter. The bulge is skipped when system animations are off, and the tick
  follows the system's touch-feedback setting.
- Unit tests cover the letters, pinning, row size, rail and bubble letters. On a Galaxy S24 Ultra
  (SM-S928B), with an earlier build of these changes from another checkout, the List drawer showed
  13 apps per screen instead of 5. The pinned letter and a bubble naming the rows beside it showed
  in List style. The vertical grid bubble and the alphabet rail were not checked on a device.

- CI now builds and tests on JDK 21 instead of 17. Robolectric refuses to run the SDK 36 unit tests
  on Java 17 ("Android SDK 36 requires Java 21"), so 119 test classes failed on every CI run that got
  far enough to test. Reproduced locally with JDK 17. The app still compiles to Java 17 bytecode.

- Animation speed now also changes the long-press app menu and the folder menu. These used to open
  and close at the Normal speed whatever the slider said. They now use their own Fast, Slow and
  Relaxed timings. A unit test covers the style chosen for each speed. On a Galaxy S24 Ultra
  (SM-S928B), screen recordings of the Motion preview showed about 160 ms at Fast, 310 ms at Normal
  and 590 ms at Relaxed. Nobody has yet watched the menu timings on a device.

- Settings previews now play a motion setting as soon as it changes, instead of waiting for a tap
  on Replay or Next. Appearance → Motion plays the sample at each animation speed step, including
  while the slider is held. The horizontal paging sample turns a page when the page snap duration
  settles or the transition effect changes. Turning on the fast scroller popup shows it once.
  Opening a page still leaves its preview idle. Compiles, and unit tests pass apart from the known
  flaky BadgeSizePreviewTest. Not checked on a device or emulator.
- Hidden apps picker cleaned up. A hidden app now shows a filled check on its icon's corner instead
  of a checkbox on every tile. The hidden count moved into the filter button ("9 hidden", or "Hidden
  only" when none are hidden), which matches the search field's height and shape. "Learn more" sits
  at the end of the hint instead of on its own line. Rows have more space between them. On a narrow
  screen or with large text the filter moves below the search field instead of squeezing it, and the
  hint folds away once the grid is scrolled and returns at the top. Unit tests pass. Checked on a
  Galaxy S24 Ultra (SM-S928B) and an emulator, including 360×640 dp at 2× font scale; the final row
  spacing was checked on the emulator only. Not checked: TalkBack, RTL.
- Page dots now follow the swipe. While you swipe between pages, the active dot stretches toward the
  next page's dot and then settles on it. Before, the dots only faded. When infinite scroll wraps
  from the last page to the first, the dot shrinks on the last page and grows on the first. This
  applies to the home screen, the paged app drawer and the Settings paging preview. Covered by unit
  tests and installed on a Galaxy S24 Ultra (SM-S928B); the swipe has not been checked on that phone
  yet.
- Settings → Folders preview redesigned. The old one mixed a floating folder icon, a tiny phone
  mockup, an enlarged single icon and two rows of demo buttons, and it was hard to tell which part
  was which. It is now two labelled tiles, "On home screen" (the closed folder icon) and "Opened"
  (the folder window), both drawn with the launcher's own folder icon and app cells. It follows
  every appearance setting, including while a slider is dragged, and is about a third shorter. The
  close-after-launch and swipe demos were removed; those switches explain themselves in their
  summaries. Robolectric tests cover it, and an API 36 emulator showed the floating and immersive styles.
  Not yet checked on a physical device, in dark mode, in RTL or with TalkBack.

- App drawer → Vertical scrolling (Vertical list and List styles): scroll speed (Slow/Normal/Fast
  flick distance), end-of-list effect (Android's stretch or off), and fast scroller,
  remember-position and pull-to-search gathered in one place, with a live sample on the drawer's
  real list view. Backups include the two new settings; older backups use the defaults.
  [Verification and limits](docs/IMPLEMENTATION_STATUS.md#vertical-scrolling-controls--2026-10-13).

- Badge size is a smooth slider from 50% to 150% in 5% steps, instead of three sizes. The sample
  icon at the top of Settings → Badges resizes its badge while you drag, and the size is saved when
  you let go. Saved sizes and backups are unchanged. A Robolectric test checks that the preview
  follows the slider. Not yet checked on a device.
- Apps and shortcuts now open with an animation from where they were tapped: icons on Home, the
  dock, drawer, folders, the app menu, search results and cards (Android had no origin to animate
  from before, so some devices opened apps instantly). The assistant, voice search and Play Store
  pages open in their own task instead of HOME's. "Add to home screen" requests that can't be
  placed now say so, and a shortcut is only pinned once it has a place on Home. Pinned shortcut
  icons refresh when their app changes or removes them. Status and navigation bar icons turn dark
  over a light wallpaper. Unit-tested; not yet checked on a device.

- Motion and feel: folders and search now fade out on Back, an outside tap or their close button
  instead of vanishing, and search rises into place when it opens. A predictive back swipe moves
  the drawer, folders and search with the finger (HOME opts in on Android 13–15; Android 16
  already enables it), and abandoning the swipe settles them back. Home gestures, drops and
  page changes during a drag give haptic feedback (off when system touch feedback is off). Live
  wallpapers receive taps on empty HOME space and drops. Unit-tested; not yet checked on a device.

- Stabilized app search when choosing an icon's swipe action: the search field stays outside
  the changing app rows, and the picker keeps its viewport while the keyboard is open.
  [Regression checks and limits](docs/IMPLEMENTATION_STATUS.md#swipe-action-app-search--2026-10-13).

- Horizontal scrolling settings now match the rest of Settings: the sample is framed like Home →
  Pages with Previous/Next arrows, choices are inline picture tiles instead of radio dialogs, the
  sliders name their ends (Quick–Gentle, Subtle–Full), controls are grouped under section headers,
  and controls that don't apply are dimmed in place. Checked on a physical MIUI phone (Home page,
  dark theme) and an API 36 emulator (drawer page, light/dark, Arabic RTL, 2× font).

- Fixed horizontal paging review regressions: clearing home pages during a swipe or settling no
  longer crashes or moves recovered pages; incoming page-removal slides survive layout and clean
  up when interrupted; search, vertical and single-page drawers reclaim unused indicator space.
  [Regression checks and limits](docs/IMPLEMENTATION_STATUS.md#horizontal-paging-review-fixes--2026-10-13).

- Home and horizontal drawer paging now have independent sensitivity, settling time, effect
  strength, edge-feedback and indicator controls with a pinned synthetic live sample. Home wraps
  through adjacent virtual slots without copying widgets; indicators support tap/scrub and direct
  accessible page selection. Settings remain backup-compatible. [Scope and actual checks](docs/IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).

- Settings → Folders preview redesigned. The old one mixed a floating folder icon, a tiny phone
  mockup, an enlarged single icon and two rows of demo buttons, and it was hard to tell which part
  was which. It is now two labelled tiles, "On home screen" (the closed folder icon) and "Opened"
  (the folder window), both drawn with the launcher's own folder icon and app cells. It follows
  every appearance setting, including while a slider is dragged, and is about a third shorter. The
  close-after-launch and swipe demos were removed; those switches explain themselves in their
  summaries. Robolectric tests cover it, and an API 36 emulator showed the floating and immersive styles.
  Not yet checked on a physical device, in dark mode, in RTL or with TalkBack.

- Apps and shortcuts now open with an animation from where they were tapped: icons on Home, the
  dock, drawer, folders, the app menu, search results and cards (Android had no origin to animate
  from before, so some devices opened apps instantly). The assistant, voice search and Play Store
  pages open in their own task instead of HOME's. "Add to home screen" requests that can't be
  placed now say so, and a shortcut is only pinned once it has a place on Home. Pinned shortcut
  icons refresh when their app changes or removes them. Status and navigation bar icons turn dark
  over a light wallpaper. Unit-tested; not yet checked on a device.

- Motion and feel: folders and search now fade out on Back, an outside tap or their close button
  instead of vanishing, and search rises into place when it opens. A predictive back swipe moves
  the drawer, folders and search with the finger (HOME opts in on Android 13–15; Android 16
  already enables it), and abandoning the swipe settles them back. Home gestures, drops and
  page changes during a drag give haptic feedback (off when system touch feedback is off). Live
  wallpapers receive taps on empty HOME space and drops. Unit-tested; not yet checked on a device.

- The icon pack setting now opens a full-screen chooser instead of a dialog of radio buttons, so
  dozens of installed packs stay manageable. It has search, an A–Z fast scroller, the current pack
  pinned at the top, and a few of each pack's own icons on its row. A pack that is no longer
  installed stays listed as "Not installed". *(Not yet checked on a device.)*
- Long lists you pick from now stay usable with hundreds of entries. *(Not yet checked on a device.)*
  - Icon pack lists (the launcher-wide chooser and the per-app icon picker) always show a search bar.
  - App lists for gesture actions, icon swipe actions, per-app swipe defaults, drawer group members
    and folder actions share one list with search, app icons, an A–Z fast scroller, and the current
    or checked apps pinned at the top. The shortcut picker lists only apps that have shortcuts.
  - Hidden apps has search and a "Hidden only" filter.
  - The card widget picker groups widgets under their app with its icon, searches widget and app
    names, and pins the current widget.
  - The folder action picker keeps the folder's own apps and the full app list in one scroll, and the
    cover picker opens scrolled to the current cover.
  - Smaller lists: home-screen icons for swipe actions have search, icons and a "With swipe actions"
    section; the featured-widget chooser marks the current widget; "Open with" shows app icons.

- Settings → Look & feel is now **Style**, and Gestures & inputs is now **Gestures**. Only the
  names changed: pages, settings search and backups are the same.
- Labs is now shown or hidden by tapping the version number in Settings → About 7 times in a row,
  instead of long-pressing volume down in Settings. The volume keys now always change the volume.
- The Google feed help text no longer names another launcher's companion app, and code comments
  describe Chikku's behaviour without naming Nova. Icon-pack discovery still lists Nova's
  icon-pack intents so those packs keep working.
  Checked on a Galaxy S24 Ultra (SM-S928B, Android 16), dark theme, debug build: the Settings list
  shows Style and Gestures, and 7 taps on the version in About hid Labs and 7 more showed it again.
  Not checked: light theme, TalkBack, large font scale, other phones.

- Split Look & feel into Accent, Icons, Motion, Screen and App menus pages. Each has a compact,
  relevant pinned preview; the overview is a short task list. Settings search opens the right
  page directly. Existing appearance settings and backup values are preserved.

- Settings search now appears only on the main Settings list. Inside a page, the top-bar
  magnifier read as searching that page: on App lock, people tapped it expecting to find an app.
  App lock's own "Search apps" field now stays pinned while the list scrolls.
- Long lists in Settings now have the app drawer's scroll thumb, with its letter bubble and
  TalkBack "Jump to" letters, following the drawer's fast-scroller side, colour and popup
  settings. This covers App lock, drawer group members, gesture "Choose app", gesture app
  defaults, the card action and shortcut pickers, the widget provider chooser, and the icon
  picker's app list and icon grid. The card picker's search placeholder now names only what it
  filters, and "App shortcuts" stays visible while typing. No new saved settings, permissions,
  dependencies or backup changes. Unit tests and lint pass. Not yet checked on a device.

- Completed missing Settings previews for search contacts/sources/suggestion ranking, dock
  autocomplete, drawer opening/groups, folder behavior and card conditions. Discover, custom
  accent, actual home page counts and the Labs overlay now have illustrative previews. Samples
  remain synthetic and offline; card/provider titles and artwork are no longer exposed in previews.
  No new persisted fields, permissions, dependencies or backup format changes.
  [Coverage and validation](docs/SETTINGS_LIVE_PREVIEWS.md).

- Fixed Settings → Search → Contacts not updating after you allow contacts access. If contact search
  was already switched on but access was missing, allowing it left the page without the card preview
  and button settings until you left and came back. Settings now re-check contacts access, media-card
  notification access and Bluetooth card access whenever you return to the page. No new saved
  settings, permissions or backup changes. Robolectric test added; not yet checked on a device.

- Search suggests things as soon as it opens, based on what you've opened before, completely offline.
  - **What you see:** a **Suggested** row of app icons and a **Recent** list of contacts, shortcuts, folders and web searches. Suggestions are ranked by how recently and how often you used them, and by what you usually open at this hour. Tapping a recent search puts it back in the box to edit or send; it never runs by itself.
  - **Controls:** **Learn from my searches** (off by default) and **Show suggestions** (on) are separate switches in Settings → Search → Suggestions. Turning learning off asks whether to keep or clear what was learned. **Clear search history** asks before clearing. In search, long-press a Recent item to remove it, and the ⋮ menu offers clear, stop or resume learning, and settings.
  - **Privacy:** history stays on this phone and is never logged or backed up; the two switches are backed up. Only identities and searches you actually sent are stored, never names or text you typed and didn't send. Digit-only searches are not kept. Hidden, app-locked, work and private-profile apps are never learned or suggested. Entries unused for 90 days are dropped.
  - **Replaces "Learn frequently opened apps and shortcuts":** existing app counts carry over and the old setting turns learning on; learned shortcut counts start fresh. Shortcuts opened from home, folders, cards, gestures and the long-press menu are still learned, and more-used shortcuts still rank first among equally good typed matches.
  - Unit tests (core, Robolectric store and view model), lint and the privacy gate pass. Not yet checked on a device.
- Search suggests what you meant when a typo finds nothing. Typing "seigg" now shows **Did you mean: Swiggy**. Suggestions only appear when no app, folder, shortcut, contact, calculation or quick result matched, so normal results never change. They allow 1 typo in 3–4 letter queries and 2 in longer ones (a wrong, missing, extra or swapped letter), matched against the start of a name or of any word in it. Hidden apps are never suggested. Contact names are suggested only when contact search is on, use the existing contacts permission, and are compared in memory without being stored or logged. Suggestions wait for contact results so they don't flash first, and the keyboard's Go key never opens a guess. Unit tests and lint pass; not yet checked on a device.

- Dock → Apps and pages now demonstrates added pages in the live preview, with numbered page
  buttons and an explanation when the requested pages exceed the current storage capacity.
  Page-count edits keep the slider in place. No new persisted state or backup changes.

- Long-pressing an app in search results or inside an open folder now opens the same app menu as
  on home and in the drawer: app shortcuts, App info, Edit, Hide, Store and Uninstall. In a folder,
  **Remove** takes the app out of the folder (with undo). Moving the finger after the menu opens
  closes it and starts the drag, as before. Pinned shortcuts in folders still drag immediately. The
  menu is also available as a TalkBack action. No new saved settings, permissions or backup
  changes. Unit tests and lint pass. Not yet checked on a device.

- Settings previews now cover drawer/folder density, dock/search placement, scoped night colors,
  icon packs and menu contents. Motion and gesture settings have safe finite replay/practice;
  badge/card samples remain visible while editing. No new saved settings, permissions,
  dependencies or backup changes. [Coverage and validation](docs/SETTINGS_LIVE_PREVIEWS.md).

- Search now finds shortcuts you added to the home screen (for example a browser site shortcut), not only the shortcuts apps publish. With **Learn frequently opened apps and shortcuts** on (off by default), Chikku also counts shortcut launches from search, home, folders, cards, gestures and the long-press menu. Your most-used shortcuts, such as a payment app's **Scan QR**, then appear under empty search. When you type, a shortcut you use more is listed before others that match the text equally well. Counts are personal-profile only, stay on the phone, are left out of backups, and are erased when the setting is turned off or cleared. No settings or backup format changes. Unit tests pass. Not yet checked on a device.

- The contact search card can be customised: it keeps its one row of buttons, by default **Call** (opens the dialer), **Message** (opens the SMS composer) and **WhatsApp** (opens the chat). In Search › Contacts › **Contact card buttons** you can swap a button for another supported one, for example Signal, add a fourth, reorder or remove them, with a live preview of the card (using a sample contact) at the top of the page. A button the contact can't use is simply left out. WhatsApp and Signal voice and video calls are limited by those apps to callers with phone-call permission, which Chikku doesn't request, so they're offered for messages only. Choices are backed up and kept when an app isn't installed. Unit, layout and accessibility-tree tests pass. Not yet checked on a device. [Details](docs/CONTACT_SERVICES.md).

- Fixed contact search → WhatsApp bypassing App lock. Search contact actions, web/store links and
  micro-actions now check the receiving app before launch and keep the same destination after an
  unlock. App selection stays inside Chikku so choosing a locked handler cannot bypass the check.
  No new permission or persisted state. Local validation is recorded in [app-lock notes](docs/APP_LOCK.md);
  real WhatsApp/system-authentication checks on a phone remain pending.

- Size, spacing, colour and corner settings show a live preview that follows the slider while it is dragged; the value is still saved on release. Home › Icons, Grid and Widgets, Drawer › Grid and Background, Folders, Dock, the search bar pages and the app-menu controls in Look & feel each show sample Chikku icons (never installed apps) drawn with the launcher's own views where practical. Previews have a fixed height so controls don't move under the finger. No saved settings or backup format changes. Compile, unit tests, lint and the privacy gate pass. Not yet checked on a device.

- Drawer scroll-thumb dragging now follows the grid's scroll distance, and its popup names the
  first app in the actual top visible row. Uneven letter counts and incomplete final rows no
  longer use equally spaced alphabet targets. Mixed-letter rows stay compact, without headers
  or empty cells; the separate alphabet-rail style keeps its letter jumps. Long single-letter
  lists work, and lists that fit hide the thumb. No saved settings or backup format changes.
  [Validation](docs/IMPLEMENTATION_STATUS.md#drawer-thumb-tracking--2026-10-12) includes JVM tests
  and native synthetic-drawer checks on a Redmi 9; on-screen finger feel and TalkBack remain unchecked.

- Size and spacing settings are stepped sliders instead of pop-up lists, so changing one is a single drag or tap. This covers grid side space, grid vertical space, widget padding, dock vertical space, folder columns, badge size, animation speed and app-lock relock time. Dock side space is now a **Follow home grid** switch, with a size slider when it is off. Saved values and backups are unchanged. A saved custom badge size keeps its own step. Unit tests and lint pass. Not yet checked on a device.

- Offline app and contact search tolerates bounded typos: `blonkit` can find Blinkit, and
  `Akshiy Albort` can find Askhay Albert. Literal names rank first, including an app's original
  name when it has been renamed. Universal search labels fuzzy results **Suggested match**;
  contact lookup uses permission-gated, per-query name scans without storing contacts or adding
  network access. No settings or backup format changes. [Scope and validation](docs/SEARCH_FOLDER_CUSTOMIZATION.md#offline-typo-matching--2026-10-12).

- The app drawer keeps its row spacing while searching. With **Fit rows to screen** on, opening the keyboard used to shrink every row to its minimum height, so the grid looked crowded. Rows now keep the height they have with the keyboard closed; the keyboard covers the lower rows and the list still scrolls the last app above it. Unit-tested under Robolectric. Not yet checked on a device.

- Settings rows have icons. On the App drawer, Dock, Search and Home screen pages, each row starts with an icon for what it controls (a lock for Lock home screen layout, A–Z for Fast scroller, a globe for Web search), in the same style as the sections on the settings home. Icons are Material Design icons (Apache License 2.0). Checked on a Redmi 9 (M2004J19PI, Android 11), light theme: all four main pages. Not checked: dark theme, large font scale.

- The fast scroller's letter bubble no longer covers apps. With **Letter bubble** on, the drawer grid leaves an empty gutter beside the scroller, and the bubble follows your finger inside it, so it never covers an icon or label. Each cell is a little narrower. Unit tests and lint pass. Checked on a Samsung phone, dark theme, scroll thumb on the right, while dragging. Not checked: letter style, left side, RTL.
- The fast scroller's letter bubble no longer covers apps. While you drag the scroller, the row or two of apps next to the bubble shrink slightly toward the far side, just enough to clear it, and ease back when you let go. The rest of the drawer keeps its full width and layout. The amount adjusts to the column count, so cells keep their spacing and labels don't collide with 4 to 8 columns. This works with the scroller on either side and follows the system animation settings. Only how the apps are drawn changes: tap targets and TalkBack positions stay the same. Unit-tested under Robolectric with 4 to 8 columns. Checked on a Samsung phone, dark theme, scroll thumb on the right, 5 columns. Not checked on a device: other column counts, letter style, left side, RTL.
- Shorter App drawer settings. Settings → App drawer had about 30 rows in one list. It now shows **Scrolling style** and one row per feature, each with its current state: **Grid & icons** (for example "Auto columns · Icons 100%"), **Fast scroller** ("Off", "Scroll thumb · Right", or "Not available with horizontal pages"), **Tabs**, **Search bar**, **Opening & closing** and **Background**, then **Hidden apps** and **Drawer groups**. Each row opens its own page, the top bar shows that page's name, and Back or the back arrow returns to the main page. The fast scroller's switch, style, side, spacing, letter bubble and accent now sit together on one page under a large **Use fast scroller** switch. It was called **Alphabet scroller**, and it had to be on for the scroll thumb style to show. When it is off, or the drawer uses horizontal pages, its options are hidden and a short note says why, instead of staying active or greyed out. The same applies to tab appearance (shown once groups or a work tab are on), search bar options (hidden with no search bar), the column controls in List style and the icon size slider while **Match desktop icon size** is on. **Automatic row spacing** is now **Fit rows to screen**, switched on to choose how many rows fill the drawer. **Infinite scroll** sits under Scrolling style with Horizontal pages. Settings search opens the page that holds a result and shows it as "App drawer › Fast scroller". No setting values or backups change. Unit tests and lint pass. Checked on a Redmi 9 (M2004J19PI, Android 11, MIUI 12.5), light theme: the main page, Grid & icons, Fast scroller (on and off), Tabs, Opening & closing, Hidden apps, Back and the back arrow, and a search result opening the Fast scroller page. Not checked: dark theme, TalkBack, large font scale, Drawer groups.

- Shorter Dock settings. Settings → Dock now shows **Show dock** and one row per task (Apps and pages, Icons and app names, Background, Spacing, Showing and hiding, Search bar), each with its current state; the controls moved to those sub-pages, rows that don't apply are hidden with a one-line explanation, and settings search opens the right sub-page. Unit tests only; not yet checked on a device.

- Shorter Search settings. Settings → Search now keeps **Search display** and **Stop the keyboard learning searches** on the main page, with one row per task showing its current state: Colors and corners, Home screen search bar (with its own Size and spacing page), Apps and shortcuts, Contacts, Web search and Quick answers. Home bar, contacts and web search each have an on/off switch at the top; while off, their other rows are hidden with a one-line explanation. Contacts permission is still asked only when you turn contacts on. Settings search opens the right sub-page. Unit tests only; not yet checked on a device.

- Shorter Home screen settings. Settings → Home screen now keeps **Lock home screen layout**, **Add new apps to home** and **Shade behind status and navigation bars** on the main page, with one row per task showing its current state: Grid and spacing, Icons and labels, Widgets, Pages and scrolling, and Google Discover feed (with an on/off switch at the top). While the layout is locked, the grid size, moving hidden items, widget overlap, adding pages and new-app placement are hidden with a one-line explanation instead of greyed out; label options are hidden while labels are off. Some labels are clearer: "Lock desktop" is now **Lock home screen layout**, "Infinite scroll" **Loop pages**, "Widget corner radius" **Round widget corners**, "Widget inner space" **Space inside widgets**, "Allow widget overlap" **Let widgets overlap other items**, "Space below icons" **Space between icon and label**, "Wallpaper scrolling" **Move wallpaper with pages**. Settings search opens the right sub-page. Unit tests only; not yet checked on a device.

- Folder swipe down no longer opens the folder. When a folder's tap runs another action, only **swipe up** opens the folder by default. A swipe down with no action of its own goes back to the home screen, so it opens notifications (or your home swipe-down action) again. Choosing **Open folder** for swipe down in the folder editor still works. Unit-tested; not yet checked on a device.

- Drawer search bar is easier to tell apart from the app grid. It now has a search icon, and **App drawer → Search bar style** offers **Subtle** (default: tonal fill and icon, no outline, like Material 3's search bar), **Outlined** (a light outline) and **High contrast** (an outline with at least 3:1 contrast). The style is saved in backups; older backups restore as Subtle. Unit tests and lint pass. Checked on a Redmi 9 (Android 11, MIUI 12.5): the icon and the high-contrast outline in the light drawer. Not yet checked on a device: Subtle and Outlined, dark mode, a custom drawer color, TalkBack.
- Chikku's own UI everywhere, never the phone's skin. People use Chikku to style their phone, so its prompts, messages and screens no longer borrow One UI or MIUI styling.
  - **Prompts:** every system dialog is now a Chikku prompt sheet with your accent: a removed or disabled app on home or in a folder (with **Find in Play Store** for personal apps), a widget whose app is missing, the accessibility explanation for gestures (copy checked against what the service can do), resetting card widget setup, app lock needing a screen lock, the Google Discover problem, and another app's request to pin a shortcut (now a transparent overlay instead of a blank window). The Hidden apps long-press menu is a Chikku menu.
  - **Messages:** short messages that were system toasts now show in a Chikku message bar on home (it stacks above Undo and is read by TalkBack) and as snackbars in Settings, onboarding, search and the icon picker. Only the shortcut-pin request keeps a toast, because it closes at once.
  - **Home theme:** the home screen no longer inherits the device theme. Buttons (Undo, recovery, drawer search), the search and folder-name cursor and selection, the widget picker's spinner, popup animations, the long-press highlight and notification dots all use Chikku's styles and accent. With no accent chosen they use Chikku orange instead of the phone's accent. The old indigo accent color left in some home layouts now matches too.
  - **Screens:** the icon picker is redesigned in the settings style (app header, default and icon pack rows with previews, a suggested icon, search, loading placeholders, an explanation when no icon pack is installed). Onboarding matches Settings and follows your accent and night mode. Search uses your accent. Settings, onboarding and the icon picker no longer flash white in dark mode, and their Android 12+ splash matches. Before Android 12, settings-style screens use neutral greys instead of Material's purple-tinted ones.
  - **Guard:** a unit test fails if app code adds a platform `AlertDialog`, `PopupMenu` or `Toast`.
  - Unit tests and lint pass. Checked on a Redmi 9 (M2004J19PI, Android 11, MIUI 12.5): onboarding, Settings and the icon picker. Not yet checked on a device: the home prompts and message bar, Hidden apps menu, search, the shortcut-pin overlay, dark mode, TalkBack.

- Edit app sheet. The app menu's **Edit** now opens a bottom sheet in Chikku's own style instead of the system dialog (which on One UI looked like a stock Samsung prompt). It matches the folder editor: the app's icon and live name at the top, a rounded **Name** field with a button to go back to the app's own name, a **Change icon** row with the current icon and a Default/Custom tag, and a Done/Discard footer. As in the folder editor, the name saves when the sheet closes (back, swipe down or tapping outside), and only Discard drops it. Change icon saves the name first. Checked on a Galaxy S24 Ultra (SM-S928B), dark theme, from a home-screen icon: opening the sheet, resetting the name and Discard. Not checked: from the drawer, light theme, TalkBack, saving a new name.

- Fast scroller letter bubble. While you drag the app drawer's fast scroller, a bubble in the scroller accent color pops out beside it and shows the letter you are on. It follows your finger, bounces slightly when the letter changes, and shrinks away when you let go. It works with the letter and thumb styles on either side and follows the system animation settings. Turn it off with **Letter bubble** in Settings → App drawer → Fast scroller (on by default). The setting is written to backups, and older backups keep it on. TalkBack keeps the per-letter "Jump to" buttons. The scroller's letters now follow the phone's languages and the drawer's sort order. In French, accented names sit under their plain letter (É under E). Swedish keeps Å, Ä and Ö as letters after Z. Korean uses initial consonants (ㄱ, ㄴ, ㄷ), Japanese uses kana rows and Chinese uses pinyin letters, instead of one entry per character. Names that start with a digit, symbol or emoji go under #, and names in a script none of the phone's languages use share one … entry at the end. When the rail has more entries than fit, it labels only some of them, but every entry still works for dragging and TalkBack (which reads # as "numbers and symbols"). Unit-tested under Robolectric with ICU collation for English, French, Swedish, Korean, Japanese and Turkish; not yet checked on a device.

- Fast scroller side and spacing. Settings → App drawer now has **Fast scroller side** (Right, the default, or Left for left-handed use) and **Space from apps** (0–32 dp, default 8 dp). A close-up preview drawn at real size follows the slider while you drag it. The scroll thumb is drawn at the screen edge on the chosen side, and the touch strip covers only the bar and the gap, so it never sits over an app. Both settings are written to backups; older backups keep Right and 8 dp. Checked on a Galaxy S24 Ultra (SM-S928B), dark theme: switching sides, dragging the slider with the preview, and the drawer with the thumb on the left at 19 dp. Not checked: letter style on the left, light theme, TalkBack, RTL.

- Fixed: in the app drawer, a tap slightly off an app in the right-hand column could land on the fast-scroll rail and jump the list. The rail now has its own strip in both the letter and thumb styles (column count accounts for it), and the thumb style moves the list only when you drag it, not on a stray tap. Unit-tested; installed on a Galaxy S24 Ultra (SM-S928B) but not yet checked by hand.

- Accent color picker. Settings → Look & feel now opens with an **Accent** section: twelve named presets (Chikku's own orange plus Cherry, Rose, Grape, Indigo, Ocean, Teal, Forest, Olive, Amber, Cocoa, Slate) and a **Custom** swatch that opens a hue, saturation and brightness picker. This replaces the plain swatch row at the bottom of the page. The settings page recolors as soon as you choose. The settings top bar's search icon and the status bar icons now turn dark on light accents. Uses the existing `accentColor` setting, so stored settings and backups are unchanged. Checked on a Galaxy S24 Ultra (SM-S928B), dark theme: presets, the custom picker, a light custom accent and switching back to Chikku. Not checked: light theme, TalkBack, home dialogs and highlights with a new accent.

- Cards redesigned. On Home, cards open in a floating sheet at the bottom of the screen (drag handle, edit and close icons) instead of a full-height box of text buttons. Each card shows why it is showing (for example "Weekdays · 09:00–18:00"), its featured app as a large icon button, and its actions as a row of icons. With no cards yet, Settings → Cards is a first-run page: an animated drawing of the Cards sheet sliding up over the Home screen, the steps that open it (long-press Home → Cards) and a pinned **Create your first card** button; once cards exist it folds into a **What are cards?** row. Each card in the list shows its icon, when it shows, its action count and an on/off switch; drag the handle to reorder, swipe a card away to delete it (with Undo), or use the ⋮ menu. The editor is a full-screen page with a preview that matches the Home panel, content chips (None, Weather, Media controls, Embedded widget), and an icon grid for actions instead of six "Button N" rows. Actions and the featured app are picked from a searchable sheet with app icons, launcher actions and app shortcuts. Days are chips and times use a clock picker instead of typed HH:mm. Closing with unsaved changes asks first. Stored card data and backups are unchanged. Checked on a Galaxy S24 Ultra (SM-S928B), dark theme: first-run page and animation, creating a card (name, action and featured app from the picker, search), the list row, the Home panel opened from the long-press menu, and delete with Undo. Not checked: reordering by drag, swipe-to-delete, the time picker, conditions, light theme, TalkBack. The on-device editor test was updated but not run.

- Home screen long-press menu redesigned in the same style as the app and widget menus (menu theme colors and corner radius, icons, grouped rows) instead of plain text rows, and it gains **Add page**: the new page slides in, grows from an outline and offers Undo. Checked on a Galaxy S24 Ultra (SM-S928B): menu look, Add page and Remove page. Not checked: light theme, custom menu colors, TalkBack, RTL.

- Page indicator can now **Show only while swiping** (Settings → Home screen, under Page indicator, for the Dots and Line styles). The indicator stays out of sight, appears while you swipe between pages, and fades out shortly after you stop. TalkBack still reads the current page. The setting is written to backups. Unit-tested only; not yet checked on a device.

- Removing a home screen page is now animated: the page shows a light outline, shrinks and fades, then the next page slides into its place (or, for the last page, the home screen glides back to the previous one). Before, removing an empty page next to another empty page looked like nothing had happened. Follows the animation-speed setting and system animation settings; a refused removal brings the page back. Checked on a Galaxy S24 Ultra (SM-S928B) for an empty middle page and an empty last page.

- Fixed: removing a home screen page was always refused in the app. The restore-safety wrapper around the layout store did not pass the request on, so it fell through to the default "rejected" answer; tests of the store itself still passed. A new test fails if the wrapper misses any layout operation. Unit-tested; not yet checked on a device.

- Restore a backup during first-run setup: the welcome step has **Restore from a backup**, with the same preview, rollback and undo as Settings → Backup, then skips the remaining setup choices. Restored settings that depend on the new phone are kept, not reset, and explained in the preview and once after the restore with a link to the setting: Google Discover without the Google app or without the Chikku Discover bridge (the swipe then opens the Google app), a missing icon pack, or a dock image that cannot be opened here. A test now fails if a new setting is not written to backups. Unit-tested only; not yet run on a device.

- Google Discover cards now appear on the home screen instead of opening the Google app. The overlay window was attached with the wrong token, so Google's window was always rejected. Release builds, which Google refuses as overlay clients, use the new optional **Chikku Discover bridge** app (`:discover-bridge`): a separate debuggable app with no permissions or data that relays the connection, only for apps signed with the same key. Without it, the swipe still opens the Google app. If the bridge stops working (for example because battery settings block it, or another launcher's Google companion is connected), the swipe explains the cause and offers the bridge's app settings instead of silently opening the Google app; Settings → Home screen shows the same status, and Chikku reconnects automatically. Checked on a Galaxy S24 Ultra (SM-S928B) with a debug build and with a release build plus the bridge. Not yet checked: rotation, TalkBack, offline, work/private profiles, other devices, and the failure explanations and reconnects on a device. [Decision](docs/decisions/0004-optional-google-discover.md).

- Folder editor rebuilt as a bottom sheet in the Chikku settings style (settings theme and accent, tonal action rows with value pills, picker pages instead of stacked system dialogs), replacing the system-styled dialog that looked foreign on One UI. The cover is picked visually (Folder preview, First app, Last app, or any app inside, shown by its icon). When the cover is an app, a "Tap opens <app>" switch links the tap to it and follows later cover changes, so a folder can launch the app it shows while a swipe still opens it. The tap and swipe pickers now list the folder's own apps first. The View dialog remains as a fallback. Checked on a Galaxy S24 Ultra (SM-S928B): sheet styling, cover selection relinking the tap, the Tap picker page, and Save with no changes. Not yet checked on a device: saving a changed cover or tap, the light theme, TalkBack.

- Redesigned open folder: a floating folder opens over the icon it came from and grows out of it, with the scrim fading in. Folders opened without an icon (e.g. from search) stay centred. The centred folder name is now a footer, beside a quiet ⋮ edit button that replaces the "Edit folder" text button. When no folder colour is chosen, the panel takes a hint of the wallpaper's hue (the Android 12+ system palette; WallpaperColors on Android 10–11), so icon tiles stand out from it, and it has a hairline edge. Immersive folders keep their top bar. No new permission. Checked on a Galaxy S24 Ultra (SM-S928B) for a 2-app page-2 folder: anchoring, tint and the ⋮ edit button. Not yet checked: dock folders, screen-edge clamping, landscape, light theme and Android 10–11.

- In progress: App lock (Settings → App lock). Locked apps need the system screen lock (strong biometrics or the device PIN, pattern or password) before Chikku opens them from any surface, including shortcuts and App info. Unlocks end per the chosen timing and when the screen turns off. While apps are locked, the settings page needs the same check, and locks are kept out of backups. Adds the normal `USE_BIOMETRIC` permission. Unit-tested only; not yet run on a device. [Scope and limits](docs/APP_LOCK.md).

- In progress: with contact search on, a query that names a person better than any app shows a leading person card with Call (opens the dialer), Message (SMS) and WhatsApp (superseded by the contact card redesign above; only for contacts WhatsApp has synced) actions. Online search suggestions now follow all local results. No new permission. Card layout and ranking checked on a Galaxy S24 Ultra (SM-S928B); the Call, Message and WhatsApp buttons are not yet exercised on a device.

- Match the requested personal home and drawer appearance with optional Minimal search styling, an offline clock, home spacing, wide drawer pills and a slim scroll thumb. Support native shortcut pin requests, saved shortcut icon appearance and dynamic calendar pack icons. Additive Room v3 migration preserves existing content; search opt-ins remain unchanged. [Scope and validation](docs/HOME_REFERENCE_MATCH.md).

- Further reliability: warn before grid changes hide placements; relocate fitting hidden items atomically with one Undo. Preserve widget IDs needed by Undo and reject stale provider/cancellation results. Invalidate stale badge reads, show listener reconnect state, and disclose opt-in search/weather requests accurately. [Validation and limits](docs/IMPLEMENTATION_STATUS.md#remaining-reliability-work--2026-10-11).

- Reliability fixes: journal restore/reset/undo across process restart; block edits on failed recovery;
  retain workspace widget IDs required for undo and reject failed settings saves. Contain media-card
  exceptions, keep app search independent of optional providers, cancel contact queries promptly,
  and revalidate shortcuts/profile state through cache hits and launch actions. See
  [verification and limits](docs/IMPLEMENTATION_STATUS.md#priority-reliability-fixes--2026-10-11).

- Bluetooth audio conditions, opt-in city weather and media playback cards, plus embedded widgets
  with separate bindings and fresh restore identities. Keeps Chikku styling; see
  [card content](docs/CARD_CONTENT_EXTENSIONS.md) for validation and platform limits.


### Added

- Configurable visible row count for the vertical/list app drawer. Turn off Automatic row spacing
  to adjust Number of rows; existing density remains the default. Spacing follows available height
  and insets, with enough room for icons, labels and touch targets. Settings travel in backups.

- Offline search micro-results: unit conversions, URL/browser chooser, map addresses, dialer/SMS
  composer and package/flight/blockchain explorer links, with an independent settings switch.
- Optional local frequently opened personal-app suggestions, off by default with explicit
  clear/disable erasure, hidden/profile filtering and no system usage permission.
- Widget overlap switch and widget corner radius under Home screen settings, plus a document-based
  dock background image picker, live preview and recoverable color fallback for lost files/grants.
  Image files and local app counts are excluded from portable backups. See
  [article audit](docs/NOVA_ARTICLE_PARITY.md) for integration boundaries and validation.

- Home search uses a solid navy capsule matching the user reference, with an app-search glyph
  on the left and a mark for the selected web engine on the right. Top and below-dock positions
  share the design; tapping the bar opens universal search. No dependency or permission is added.

- Below-dock search controls in Dock settings, shared web engine selection and a provider-labelled
  search bar. Optional Google/DuckDuckGo autocomplete is off by default and discloses typed-query
  transmission; local results stay offline, and failed suggestions retain browser search. This adds
  the normal INTERNET permission with the rationale in [ADR-0005](docs/decisions/0005-optional-web-search-suggestions.md).
  Both providers' bar → suggestions → browser flow was verified on Samsung Galaxy S24 Ultra, Android 16.

- Optional Google Discover (Google Now cards) switch under Home screen settings, off by default.
  A swipe towards the start edge of the first page opens the external Google overlay when the
  Google app accepts a direct connection; otherwise the swipe opens the installed Google app.
  No companion app is required. Includes an accessible action, provider failure messages,
  lifecycle cleanup, settings/backup persistence and safe-mode exclusion. This unofficial integration
  is experimental; the app-opening fallback displayed live Discover cards on a Samsung Galaxy
  S24 Ultra (Android 16). Embedded overlay compatibility remains unverified.

- Gradle project bootstrap: Gradle wrapper 9.7.1, Android Gradle Plugin 9.4.1, Kotlin 2.4.21, version
  catalog, modules `:app` and `:core:model`; minSdk 29, compileSdk 37, targetSdk 36; application ID
  `io.github.aj4abinjacob.chikku` with a `.debug` suffix for debug builds. The initial bootstrap omitted
  `INTERNET`; optional web suggestions now declare it under ADR-0005.
- Continuous integration (GitHub Actions): debug build, `:core:model` and app unit tests, Android lint,
  privacy/manifest gate and APK/report artifacts on every pull request and push to `main`; optional
  manual instrumentation run on an API 36 emulator; advisory ktlint formatting check.
- `scripts/check-privacy.sh`: fails on forbidden permissions in the merged manifest and on known
  ads/analytics/crash-reporting SDKs in the runtime classpaths.
- Dependabot for Gradle and GitHub Actions, `.editorconfig`, release checklist, implementation status
  matrix and updated pull request template.

- M0 launcher foundation (see [docs/IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md) for the
  evidence behind each item):
  - HOME activity, non-default banner and Compose settings screen with RoleManager request and the
    system Home-app screen for switching back (FR-001).
  - LauncherApps-backed app list, keyed by component and profile serial, with coalesced package and
    profile events (FR-002), and typed, never-throwing app launching (FR-003).
  - View-based workspace pages, dock seeded once from the default dialer/SMS/browser/camera, and Room
    persistence in half-cell units with bounded undo snapshots (FR-004).
  - Long-press drag and drop with persisted-state-first commits, remove target and undo (FR-005).
  - Offline, accent-insensitive app search (FR-008).
  - Recovery: damaged databases are preserved before any reset and announced; crash-loop safe mode;
    recovery panel when the layout cannot load (FR-009).
- Unit tests (105) and device instrumentation tests (19).

- Nova-guide feature set G01–G22 on branch `feat/nova-guide` (in progress: covered by unit and
  Robolectric tests only, not yet run on an emulator or device; see the
  [Nova-guide table](docs/IMPLEMENTATION_STATUS.md#nova-guide-features-g01g22)):
  - Settings model with sections for home screen, dock, drawer, folders, gestures, badges, search,
    look & feel, night mode, backup and Labs; Room schema v2 with a migration from v1.
  - Home and dock: grid size, icon and label size, padding, page indicator, infinite scroll, wallpaper
    scrolling, default page, dock pages and background, home search bar, layout lock (G02, G03, G12,
    G17).
  - App drawer: open method, separate grid, vertical or paged style, search bar position, background
    color and opacity, fast scroller, hidden apps and drawer groups as tabs (G13, G14).
  - Folders: create by dropping one item on another, rename, reorder, open panel, preview styles, in
    the dock, and folder tap/swipe actions (G22, G07).
  - Gestures: swipe, double-tap, pinch, two-finger and home-button actions, per-icon swipe actions,
    app shortcuts as actions; optional accessibility service for lock screen and Recents (G07, G10).
  - Universal search: apps (also by custom name), app shortcuts, opt-in contacts, calculator, web
    search handoff with a selectable engine and Play Store row (G04–G06).
  - Notification badges (dot, count, dynamic) through an optional notification listener (G08).
  - Look & feel: icon packs, icon shapes, per-app icon and label, animation speed, night mode per
    surface, orientation, status bar mode, long-press app menu with app shortcuts, uninstall and
    "Swipe actions" (G01, G09, G15).
  - Widgets: picker, bind with consent, configuration, resize, overlap, options, and a long-press home
    menu with Widgets, Wallpapers and Settings (G16, G21).
  - Backup and restore of layout and settings to one versioned JSON file, shared through the share
    sheet; quick start, reset to default, onboarding and Labs (G11, G18–G20).

### Changed

- Settings now use a compact warm-accent toolbar, flat preference rows, section bands and
  selected-value dialogs inspired by the linked Nova guide. Original Chikku branding/icons,
  night appearance and custom accent colors are retained. This supersedes the earlier large-title,
  rounded-card settings design recorded below.

- Dock settings now follow the captured Nova dock workflow with a pinned Chikku preview, desktop
  size matching, expandable label/background controls, independent padding, round/cut corners,
  navigation-area background, infinite scrolling and overlay/automatic-close/remember-page options.
  Toggle dock is available through gestures and accessibility. Preferences and placements remain
  recoverable and compatible with old backups; see [dock settings](docs/DOCK_SETTINGS.md).


- Hidden apps settings now use a drawer-style icon grid with selection checkboxes, a hidden-app
  count and an always-available alphabet rail for tapping or dragging to a letter. Apps stay in the
  picker after selection so several can be hidden or shown without reopening it. Drawer icon size,
  icon packs, custom names and profile identity are retained; touch and hold offers Open.

- Icon pack selection always opens a scrollable list of System icons and every detected installed
  pack. The selected pack appears in the settings row, so a short inline button group no longer
  makes the setting look limited to two choices.

- Widget picker previews now use an adaptive grid: narrow previews share a row and wider
  previews span columns. Cards are centred with tighter spacing; narrow windows and larger
  fonts reduce the column count. Visually checked with Calendar previews on a Galaxy S24 Ultra.

- Home now has a subtle up arrow in place of the labelled All apps pill. App drawer settings
  include a "Show up arrow" switch and a separate Search section with Top, Bottom and None
  positions. Hiding the search bar clears its query and keyboard so all apps remain visible.
  The existing opening and search preferences continue to persist and travel in backups.

- Settings redesign: large collapsing titles; the root list is grouped (Layout, Appearance,
  Behaviour, System) with a tonal icon per section and shorter summaries; rows inside each section
  sit in rounded card groups; choice and slider values show as pills instead of extra text lines;
  explanatory text is a small footnote; a compact "set as default" banner replaces the full status
  card on the root list. Inside sections, short choices (night mode, padding, page indicator, badge
  style, orientation…) are inline segmented buttons, gesture actions show as value pills, summaries
  are shorter, and long explanations fold behind "Learn more" (privacy, permission and
  not-a-security-feature disclosures keep their full wording there). The hidden-apps and drawer-group
  lists use the same cards, and About has an app header, a status card and a privacy row. Fixed
  pages shorter than the screen being vertically centred. Checked on a Galaxy S24 Ultra (root list
  only, dark theme) and in Robolectric renders of every section (light theme); section pages are
  not yet checked on a device.

### Fixed

- Opening search now waits for the field and window to be ready before focusing it and showing
  the keyboard. Applies to both home search bars, Window/Fullscreen and standalone search;
  no extra field tap is required, and deliberate keyboard dismissal is respected.

- Compact app shortcut popups now keep a 280dp width (capped by the visible window) instead of
  stretching across the screen when shortcut labels load. Launcher action buttons sit closer
  together and retain their 48dp touch targets.

- Widget resizing now offers handles on all four edges and all four corners for supported axes.
  Left/top drags update both position and size while keeping the opposite edge fixed; provider
  size limits, grid boundaries, RTL, overlap settings and undo are respected. Cancelled drags
  and rejected edits restore the frame to the saved placement.

- Downward swipes mapped to Notifications or Quick settings now open the system shade during
  a deliberate drag (24dp after swipe recognition), removing the wait for finger lift. Each
  drag hands off once; short flings retain the release fallback. Android owns the shade animation.

- Default dock apps could be seeded onto the second dock page when several dock pages were enabled;
  they now fill the first page only.

- Swipe up only opened the drawer from empty desktop space (not from the dock or the "All apps" row),
  and the drawer could not be closed by swiping down. Both gestures now work anywhere, and swipe down
  only closes the drawer when the app list is scrolled to the top.
- Drops from the app drawer onto the workspace were lost on a real device (found on Android 11/MIUI).
- Security review: corruption notice could be missed; early crashes were not counted by the crash-loop
  guard; items of a paused or failed work profile could be shown as uninstalled.

### Not yet verified

- Selecting Chikku as default HOME and reverting on a device; app install/uninstall updates on a
  device; work profile and Private Space behaviour; reboot persistence.

FR-006 folders and FR-007 widgets, deferred within M0, are implemented on `feat/nova-guide` but not
yet verified on a device.

### Added — phone settings pass (2026-10-11)

- A [device-observed Nova settings inventory](docs/NOVA_PHONE_SETTINGS.md), separating local
  implementations, partial equivalents and open work. No Nova source or assets were copied.
- Independent home/drawer/folder label styling; true drawer list rows, desktop-size matching,
  tab placement, fast-scroller color, paged wrapping, pull-to-search, transitions and launch controls.
- Folder panel modes, content sizing, columns, preview counts, optional name/shadow/corners,
  presentation-only alphabetical sorting and configurable closing after a successful launch.
- Home page effects, optional edge shadow and opt-in new personal-app placement while the
  launcher process is alive, respecting hidden apps, layout lock, recovery and existing placements.
- Settings search, optional local folder search, search presentation/background/result-limit
  controls, advisory private-keyboard input and home search-bar styling.
- Empty-space horizontal gesture actions, double-tap-plus-swipe, two-finger rotations and
  Home-button scope; badge position/size and synthetic samples; popup colors/corners and
  independent home/drawer optional actions with wrapping, accessible touch targets.

Settings remain portable and backward-compatible; no permission, dependency or account
integration was added. Verification outcomes and remaining device/accessibility coverage are
recorded in the inventory and [implementation status](docs/IMPLEMENTATION_STATUS.md).

### 2026-10-11 — Horizontal folders and local cards

- Added horizontal folder pages, adjustable row count, RTL swipes and accessible page controls.
- Added named cards with personal app/shortcut or existing-widget features, six action slots, day/time/charging/Wi-Fi visibility conditions and recoverable editing. Actions run on tap.
- Added normal network-state permission for local Wi-Fi conditions; no new dependency or exported component. Full scope and named checks: [continuation notes](docs/FOLDER_PAGING_CARDS.md).

## Search and folder usability fixes — 2026-10-11

- Added home search-bar width, height, horizontal/vertical padding and size reset controls, persisted in backups.
- Moved HOME search into its window so app results can be dragged to the workspace, dock and folders with fresh visibility/lock checks.
- Added folder long-press Edit/Remove actions, refreshed the folder editor and grouped folder settings.
- Added first/last/chosen-member app icon covers and explicit Open folder tap/swipe actions; missing chosen members fall back to the preview.
- Validation and remaining device acceptance: [search/folder notes](docs/SEARCH_FOLDER_CUSTOMIZATION.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md).

- Fix below-dock search touching dock labels: add a persisted Space above search bar control (0–64 dp, default 12 dp), separate from inner padding.
- Fix both search corner-radius labels displaying dp values as percentages; explain which surface
  each control rounds and when the home bar is already fully rounded. Saved values are preserved.
- Audit remaining settings units and active values: preserve large saved grid counts in their sliders,
  hide inactive drawer counts, correct Labs descriptions and disable the unfinished cache option,
  show the saved compact-search limit and refresh widget-card height labels on language changes.
  Unit, spacing and badge-label corrections in the working tree are retained. See the
  [settings clarity audit](docs/SETTINGS_CLARITY_AUDIT.md) and named validation in implementation status.

- Clarify grid edges, widget inner space, dock app spacing and painted background insets; show actual spacing amounts; expose search gap in Dock; separate folder icon and window colors while preserving old colors.

### Settings value audit — 2026-10-11

- Require explicit slider units; use consistent fractional dp for widget, folder, app-menu and search corners.
- Allow fine widget/menu radius adjustments; retain saved values without truncating their labels.
- Show custom badge sizes accurately and keep them selected in the picker; label preset percentages.
- Explain how custom dock corner percentages map to the background size.

- Apply manual drawer row spacing immediately without compact outgoing row animations; restore normal list animation and sizing when automatic row spacing is enabled.

### Production reliability verification — 2026-10-12

- Harden workspace bounds and overlap arithmetic against malformed large spans, while retaining exact nearest-placement ordering. Add adversarial layout/search stress and restore-retry regression cases.
- Record forced unit, privacy-gate, disposable-emulator recovery, process-termination and isolated physical-preservation evidence with explicit limits. Native acceptance and the unresolved Cards accessibility finding are tracked in [QA results](docs/QA_RESULTS.md), the [regression strategy](docs/QA_REGRESSION_STRATEGY.md#executed-evidence--2026-10-12) and [risk register](docs/QA_RISK_REGISTER.md).

### Reliability recovery continuation — 2026-10-12

- Reject semantically damaged Undo snapshots before replacing the current layout; retain the
  snapshot and defer widget-ID cleanup when its references cannot be trusted. Off-grid placements
  and historical overlaps remain recoverable.
- Refuse settings replacement until a damaged-input copy reaches storage, including first-operation
  restore/reset; allow retry when storage recovers.
- Enter safe mode for corrupted startup flags or an uncertain commit, and saturate the crash counter
  to prevent integer overflow from disabling recovery. Keep damaged flags until explicit exit.
- Add native older-schema migration/reopen/Undo and newer-schema refusal fixtures in isolated
  storage. [Actual verification and remaining acceptance](docs/IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

  Verification: 176 core + 805 app tests, build/lint/privacy; Redmi 9 Android 11 passed nine native
  recovery cases and four recoveries after real process kills, preserving all original saved data
  and HOME selection. Real release upgrade/reboot and broader acceptance remain open.

  Local main integration: 209 core + 823 app tests passed, app/test APK assembly, lint and privacy
  gate passed. Both sets of overlapping documentation additions were retained; native checks were
  not repeated for this merge. No remote push or publication.


## 0.1.0-beta.2 closed-test preparation — 2026-10-17

Integrate local development main53bb644 into the Play-prepared source while retaining MET/offline
cities, consent/legal/source and recovery/widget fixes. Prepare versionCode3, refresh124 Material
vector notices and resource hashes, and retain backup coverage for the new search presentation
choices. Exact distribution status and package/source hashes are recorded separately after signing.
