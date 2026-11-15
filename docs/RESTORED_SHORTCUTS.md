# App shortcuts after reinstall and restore

Implemented locally, 2026-10-18. Device verification remains pending.

Chikku backs up shortcut publisher package, shortcut ID, profile serial, placement and chosen
appearance. Android holds the publisher's executable pinned shortcut record separately. Restoring
Chikku's JSON after uninstalling/reinstalling the launcher does not recreate that record. A saved
shortcut ID can therefore fail even while its owning app is installed. Previously home/folder taps
reported only “That action is not available on this device.”

## Recovery behavior

- Try `LauncherApps.startShortcut` first, preserving genuine shortcut/PWA behavior when available.
- If Android reports `ActivityNotFoundException`, stable Firefox (`org.mozilla.firefox`) shortcuts
  whose ID is a valid HTTP(S) URL can open that URL as an ordinary page in Firefox. This uses the
  existing saved ID; it does not reconstruct a PWA or Android pin record.
- The fallback stays in the calling profile and the same Firefox package, after the normal app-lock
  admission and a fresh profile check. A work-profile shortcut never opens in personal Firefox.
  Security/profile failures do not trigger the fallback. Missing Firefox does not open another browser.
- Opaque IDs, other publishers and non-web URIs receive a specific unavailable-shortcut result.
  Home/folder/gesture, Cards and Search explain that the shortcut must be added again from its app.
- Settings → Backup and previews containing workspace/folder shortcuts explain the reinstall limit.
  Saved placements and IDs remain intact. No settings field, Room schema, backup-format change,
  permission, dependency or new persisted reference was introduced. Format 1 remains compatible.

For an opaque Firefox shortcut, open its website in Firefox and use the browser's add-to-home-screen
action again. Confirm the new shortcut works, remove the stale placement, and move the new one into
place. Its new ID cannot safely be matched to the old one using a label. Chikku does not infer URLs
from labels or replace unrelated placements.

## Platform and publisher evidence

Inspected on 2026-10-18; used as API/behavior references only, with no copied source or assets:

- [Android LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
  exposes shortcut launch by publisher/ID/profile, rather than a portable executable intent.
  [ShortcutQuery](https://developer.android.com/reference/android/content/pm/LauncherApps.ShortcutQuery#FLAG_MATCH_PINNED)
  also documents that an ordinary launcher cannot query another launcher's pinned shortcuts.
- Archived Android Components revision
  [`f5ac21c0250fdf7bb837130ca7cb3c43733d917f`](https://github.com/mozilla-mobile/android-components/blob/f5ac21c0250fdf7bb837130ca7cb3c43733d917f/components/feature/pwa/src/main/java/mozilla/components/feature/pwa/WebAppShortcutManager.kt)
  uses the page URL for basic shortcut IDs and the manifest start URL for PWA IDs.
- Firefox revision
  [`1967f78d98efce2eab72c0cfc79e9319360d7b1e`](https://github.com/mozilla-firefox/firefox/blob/1967f78d98efce2eab72c0cfc79e9319360d7b1e/mobile/android/android-components/components/feature/pwa/src/main/java/mozilla/components/feature/pwa/WebAppShortcutManager.kt)
  uses random UUIDs for basic shortcuts, while PWA IDs retain the start URL. A random UUID in an
  existing backup contains no recoverable destination. No universal Firefox recovery is claimed.

## Verification

`RestoredWebsiteShortcutTest` passed 18 synthetic Robolectric cases across API 29/34, including
exact URL query/fragment retention, native-first launch, one app-lock admission, locked/missing/
changing profiles, cross-profile rejection, missing Firefox, security failures, opaque/hostile IDs
and the shared Search launch path. These tests capture intents; they do not launch Firefox.

`BackupRestoreRoomTest` adds an export/decode/clean-restore regression for URL and UUID shortcut IDs,
custom appearance, folder membership and exact retained placement identities. Final build/test
commands and results are recorded in [implementation status](IMPLEMENTATION_STATUS.md#restored-website-shortcuts--2026-10-18).

`adb devices -l` found no attached device. Real Firefox launch, native reinstall/SAF restore,
TalkBack and PWA-versus-browser presentation remain unverified on a phone in this change.
