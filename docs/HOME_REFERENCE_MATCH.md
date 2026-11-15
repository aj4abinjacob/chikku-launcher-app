# Personal home and drawer reference — 2026-10-11

The user initially requested their installed Nova home screen, then expanded the check to the
app drawer. Folder windows and search-result screens remain outside this appearance pass. No
research source/feature identifiers are introduced here.

The reference is Nova 8.9.4 on Galaxy S24 Ultra SM-S928B, Android 16/API 36, 1080 × 2340,
density 450. Home uses a five-column/five-row workspace, date/seconds clock, two app rows,
five unlabeled dock icons, no page indicator, and a navy rounded search bar below the dock.
The drawer uses a solid charcoal background, five widely spaced rows, condensed labels,
top Apps/Work pills and a slim scroll thumb. Screenshots and private backups remain in ignored
`build/verification/nova-visual-match/`, never application assets.

## Implemented behavior

- Optional Minimal home search style has original neutral circles, a 48 dp touch target with
  44 dp painted height, 12 dp side margins and 21 dp bottom clearance. Provider badge remains
  the default. Appearance does not change search engines, contacts or network opt-ins.
- Page indicator None removes its reserved height. Home icon/label gap and downward cell offset
  are saved independently; offsets clamp to spare cell space so large text remains within bounds.
- An original offline RemoteViews/TextClock widget shows date/time/seconds and respects system
  12/24-hour format. Its receiver is non-exported; no new permission, dependency, periodic job or
  network service. Existing widget binding and fresh IDs on restore remain in use.
- Optional Wide pills drawer tabs share available width for up to three tabs. With a separate Work
  tab enabled, that style retains an empty Work tab when no work apps are currently available;
  it does not manufacture profile contents. Empty-tab status stays below the top navigation row.
  Compact defaults retain prior behavior.
- Optional Scroll thumb retains draggable section jumps and accessible letter targets. API 29+
  paints a native thumb; older platforms retain the alphabet rail. Five manual rows and wide pills
  use additional grid clearance; automatic spacing and compact styling retain their behavior.
- Icon packs' calendar prefixes resolve the local day, fall back to static artwork when unavailable,
  and refresh on HOME resume and date/time-zone broadcasts while HOME exists. Personal-profile
  activities declaring a daily icon array can also supply native calendar artwork when the pack
  lacks it. Other profiles retain LauncherApps native icons. Artwork remains in the installed apps.
  The metadata convention was checked against AOSP IconProvider (Apache-2.0, blob
  `594db358185d6fa9bff0da562319ced668e951fb`); the Kotlin implementation is original, with no source
  or artwork copied. [AOSP contract reference](https://android.googlesource.com/platform/frameworks/libs/systemui/+/refs/heads/main/iconloaderlib/src/com/android/launcher3/icons/IconProvider.java).
- The exported `ShortcutPinActivity` is required for Android/browser Add to home screen requests.
  It accepts a valid framework shortcut pin token after explicit Add, only for the current personal
  user, default HOME and unlocked editable layout. Arbitrary intents/tokens are rejected. Rendering
  and launching use owning package, provider shortcut ID and profile serial, with fresh profile checks.
  [Android's pin-request contract](https://developer.android.com/reference/android/content/pm/LauncherApps.PinItemRequest)
  defines validation and acceptance. No new permission/dependency is needed.
- Shortcut pack/resource appearance is optional metadata, independent of launch identity. Room v3
  adds nullable fields to workspace and folder entries without deleting rows. Portable backups,
  folder moves and recovery snapshots preserve them; old backups/snapshots default to native icons.
  The schema upgrade is additive; previous exported schemas remain available.

## Applied personal configuration

Original Chikku settings/layout/database were backed up privately. All eight original placements,
including folder contents and existing widget binding, remain on additional pages. The new first
page and dock contain fourteen app placements, the clock and a fresh Firefox x.com pin. Solid
Explorer is labeled Files; YouTube Music is labeled Music. Nova's actual YouTube target is retained.
Files, Lyfta and X artwork resolve from the user's installed pack at runtime. Nova's broken X
shortcut was recreated with the user-confirmed x.com destination; Chikku shows an enabled icon.
Bixby and Samsung Calendar are cosmetically hidden in the personal drawer to match the observed
first page; Google Calendar remains visible. Cosmetic hiding does not provide app security.

No Nova code, branding or proprietary artwork is bundled. Existing wallpaper is retained. The
provisional build-versus-fork ADR is unchanged.

## Verification

Debug APK and test APK assembly, core/app local unit tests, Android lint and the privacy gate pass
in an isolated source snapshot. Tests cover tolerant settings/backup decoding, framework-token
rejection, additive schema migration, shortcut identity/appearance and snapshot retention, stale
icon cancellation, tab/rail geometry restoration and calendar resource parsing. Exact final counts
and physical evidence are recorded in [implementation status](IMPLEMENTATION_STATUS.md).

Physical checks include installed appearance, real Firefox pin consent and x.com launch, native
app-target identification, and saved configuration retention. Visual differences remain in
framework clock font metrics, Nova's disabled X opacity, and scroll-thumb length derived from the
visible app list. This is a close appearance match, not a pixel-identical rendering guarantee.
TalkBack, large-font/RTL acceptance, midnight transition and other devices remain unverified.
