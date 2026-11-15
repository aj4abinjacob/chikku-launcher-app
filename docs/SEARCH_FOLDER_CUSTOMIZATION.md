# Search sizing, drag and folder editing fixes

Implemented locally on 2026-10-11 after reported missing search sizing, result dragging and folder editing controls. Named check outcomes belong in [implementation status](IMPLEMENTATION_STATUS.md); broader compatibility and release gates remain open.

## Search bar

**Chikku settings → Search → Home search bar** exposes width (40–100% of available space), height (48–96 dp), horizontal padding (0–48 dp) and vertical padding (0–24 dp), alongside the existing color, corners and shadow controls. A reset restores the original 100% / 56 dp / 20 dp / 10 dp dimensions. Both top and below-dock positions use the same settings, center within their available width and respond to window resizing. Padding fits around decorative icons when a narrow or short capsule cannot accommodate the requested amount. The minimum height preserves a 48 dp touch target.

**Space above search bar** (0–64 dp, default 12 dp) adds an external gap between the dock and the below-dock bar. Internal padding adjusts its contents independently. Reset restores the gap too; the top bar keeps its existing outer margin.

**Search icon** (added 2026-10-17) picks the decorative magnifier at the start of the capsule: Dog nose (default), Paw, Floppy ears, Dog face, Nose notch, Chikku mark or Plain. It replaced a dot-grid-and-magnifier glyph that resembled another launcher's and implied app-only search. The whole capsule still opens universal search; the icon has no separate action. The pinned home bar preview redraws the chosen glyph and names it in its TalkBack description.

The fields have backward-compatible defaults, bounded decode and travel in settings exports/backups. Changing dimensions does not change placements, search providers or the autocomplete opt-in.

Both search corner-radius controls display 0–48 dp. **Search corner radius** rounds the opened
search input and, in Window mode, the containing panel. **Search bar corner radius** independently
rounds the home bar in either position. Once the radius reaches half the home bar height, the ends
are fully round and further increases look the same (28 dp for the default 56 dp bar).
The earlier percentage labels, such as 4576% for a stored 45.76 dp radius, were a formatting defect;
the saved radius values and their separate scopes are preserved.

The settings audit also standardizes widget, folder and app-menu radii to one decimal place in dp.
Widget and app-menu radius sliders allow fine adjustment throughout 0–48 dp instead of snapping to
4 dp steps. Scale/opacity sliders explicitly use percentages, and integer dimensions/counts retain
their own units. Generic float sliders require an explicit formatter so new controls cannot silently
inherit percentages. Custom badge sizes remain selectable and show their percentage alongside the
named presets; dock corner-size percentages explain their geometry. Saved settings and backups are
unchanged.

## Search result dragging

Typed search marks its current keyboard **Go** target with an accent outline, tinted background
and **Top result** label. The same target is identified in accessibility state. This applies to
list rows, compact app tiles, the leading contact card and explicit action/web fallbacks. The
existing ranking and Go behavior determine the target. Empty-query history and results waiting
to catch up with new typing carry no marker. The synthetic Results settings preview shows the
same styling; the marker is transient UI state and adds no persisted setting or backup field.

**Settings → Search → Apps and shortcuts → App priority direction** (2026-10-25) places the
highest-priority app on the left or right for one-handed reach when **Show matching apps in one
row** is on. **Follow language** preserves the existing default; explicit **Left to right** and
**Right to left** choices keep their physical direction in either language layout. The pinned
Results preview updates immediately. Matching and suggested app rows share the direction;
vertical lists retain their order. Ranking, the highlighted identity, Go selection, app label
direction and launch/drag behavior are preserved. The choice travels in settings and backups;
older backups retain Follow language.

Opening search focuses its input and shows the keyboard automatically. The shared screen waits
for field placement and window focus, then allows Compose to establish the input connection
before requesting the keyboard. The request runs once per opening, so dismissing the keyboard
does not cause it to reopen on result updates. This covers both home bar positions, Window and
Fullscreen presentations, gesture/drawer handoffs and standalone search. Android's
[keyboard visibility guidance](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)
describes the focus/input-connection requirements. Named checks are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#automatic-search-keyboard--2026-10-11).

Search opened from HOME uses a Compose overlay inside HOME's existing window. Long-press an app result and continue dragging onto a desktop cell, dock cell or folder. Tap still launches. The source remains attached but invisible throughout the local Android drag; drag end/cancellation releases it without saving a placement on cancellation. Standalone search remains available as a non-exported activity.

App identity includes component and profile. Immediate result filtering and fresh drop validation exclude hidden apps and profiles no longer listed. Locked layouts and unavailable/recovery workspaces reject edits. The drag uses no global flags, serialized app inventory, new permissions or exported component. Back/Home and activity stop close search; optional source flows and transient view models are released.

## Folders

Long-press a desktop or dock folder to find **Edit folder** and **Remove from home**. Moving the held finger continues into the existing drag behavior. The editor has a named text field, grouped icon/gesture controls and scrollable content; Save commits staged changes and Cancel leaves them untouched. Layout lock also guards the editor and destructive menu actions.

A folder cover can use its normal preview, the first app, last app or a chosen member's app icon. First/last follow stored membership order, so alphabetical display sorting does not silently change the cover. A chosen app retains component/profile identity. Missing or removed selected members fall back to the preview; stored folder membership stays intact. Cover choices are keyed by stable folder ID in existing versioned settings, without a database migration.

Tap, swipe up and swipe down can explicitly **Open folder**. This is a contextual action that opens the actual folder and remains honored when other folder gesture overrides are disabled. It is excluded from global gesture pickers. Appearance is independent of launching: choosing a cover does not assign an app-launch action. Global folder settings group icon appearance, inside-folder styling, layout/scrolling, icon sizing, labels and behavior, with a decorative preview and relevant controls.

## Removed click-action target (2026-10-18)

Removing a folder member, including dragging it to the desktop or dock, replaces a tap action
that launches that exact member with the first remaining member in stored manual order. If the
folder becomes empty, a tap opens it. Matching uses component/profile for apps and package,
shortcut ID and profile for shortcuts. Other tap actions and all swipe actions stay as configured.
The membership and tap update are one Room transaction and one Undo snapshot, including after
reopening the database. Existing action fields carry the fallback through portable backups;
no schema or backup-format change is needed. Decorative cover fallback keeps its existing rules.
An action left stale by an earlier build needs to be selected again once: stored actions do not
record whether a target was formerly a member or was deliberately chosen from outside the folder.

`RoomWorkspaceRepositoryFolderTest` covers removal, moving out, stored ordering, empty folders,
exact identities, unrelated actions, restart, Undo and rejected moves. `BackupRestoreRoomTest`
covers export/reset/restore of the new fallback through the real Room stores and backup service.
These are Robolectric checks; physical-device verification is pending.

## Verification boundaries

Regression coverage includes stored sizing/cover identity and backup compatibility, numeric bounds and malformed-cover rejection, actual bar measurement/window changes/RTL, folder editor Save/Cancel, explicit folder gestures, cover fallback, and HOME's folder long-press routing. JVM/Robolectric coverage is distinct from Android instrumentation and injected touch workflows. Real OEMs, TalkBack, RTL with large fonts, reboot/process death and work/private-profile lock acceptance remain separate device checks.

Final host checks passed: 130 core and 534 app unit tests, debug/app-test assembly, Android lint and the privacy gate. The named Android 16/API 36 emulator run passed continuous desktop placement, compact/lifecycle cancellation, locked-layout/tap behavior, editor cover/action callbacks and existing folder paging checks. See [the evidence record](IMPLEMENTATION_STATUS.md#search-sizing-drag-and-folder-editing--2026-10-11) for precise scope and remaining acceptance.

## Offline typo matching — 2026-10-12

App search in the drawer and universal search now ranks exact names, prefixes, word prefixes and
substrings before token-prefix and typo matches. Original and custom app names compete in the
same pass; the custom name remains the displayed name. Each query word must match a distinct
name word, with stable input order on equal scores. Folder and shortcut matching stay literal.

The shared Kotlin matcher uses bounded optimal-string-alignment distance (substitution, insertion,
deletion and adjacent transposition), over normalized Unicode code points. One-to-three-letter
words and numeric tokens have no typo allowance, four-to-five-letter words allow one edit, and
longer words allow two. The whole query allows at most three edits. Literal matches remain
unrestricted; approximate matching is limited to six query words, 128 query UTF-16 units and
256 name UTF-16 units, with bounded word lengths. These limits contain work on malformed or very
long input; they are not a measured phone latency guarantee.

Examples covered by regression tests: `blonkit` finds **Blinkit**, and `Akshiy Albort` finds
**Askhay Albert**. Universal search marks fuzzy app/contact results **Suggested match**, without
rewriting the query. Fuzzy contacts do not become the leading person card. Exact original app
names retain their ranking even when a different custom name is displayed.

Contacts still require the existing opt-in and `READ_CONTACTS` permission. Android's filtered
matches are supplemented by a fresh, cancellable personal-contact name scan for each query,
retaining only the best candidates and reading action details for at most five final contacts.
There is no retained address-book cache, background observation, learned query history, new
permission or network call. Permission loss withholds results; a failed supplementary scan keeps
available filter results. Edits/deletions are reflected on the next query. Very large or slow
providers remain subject to the existing provider timeout. Host validation and remaining physical
checks are recorded in [implementation status](IMPLEMENTATION_STATUS.md#offline-typo-matching--2026-10-12).

Algorithm reference: [Apache Lucene's fuzzy-query documentation](https://lucene.apache.org/core/9_6_0/core/org/apache/lucene/search/FuzzyQuery.html).
Platform boundary: [Android ContactsContract.Contacts](https://developer.android.com/reference/android/provider/ContactsContract.Contacts).
Consulted 2026-10-12; implementation is original Kotlin, with no source or dependency copied.

## Below-dock search spacing — 2026-10-11

The connected Samsung SM-S928B screenshot confirmed that inner padding did not separate the search capsule from dock labels. Added an independent persisted `widgetDockGapDp` (0–64 dp, default 12 dp), exposed as **Space above search bar** in Settings → Search. It changes the below-dock bar’s outer top margin, preserves its size/inner padding and the top bar’s existing margin, and participates in reset and settings backups.

Built a frozen source snapshot to avoid concurrent card edits during verification. Debug assembly, 130 core tests, 537 app tests (including six search sizing workflows) and the privacy gate passed. Lint reported four MissingPermission errors in the concurrent `CardBluetoothSource.kt` work; none concerns search spacing. Installed the verified snapshot on physical Samsung SM-S928B with `adb install -r`, preserving data, and visually confirmed the gap in before/after screenshots. This is physical screenshot evidence, not a phone instrumentation or full OEM acceptance result.
