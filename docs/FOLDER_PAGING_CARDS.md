# Horizontal folders and local cards

Dated implementation: 2026-10-11. This continuation of the user-authorized
[Nova phone settings inventory](NOVA_PHONE_SETTINGS.md) uses independent Chikku Kotlin code and
Chikku's rounded panels, resource strings, existing theme colours and motion preferences.
It does not change the provisional foundation decision, M0/M1 release gates or publish the app.

## Horizontal folder paging

Settings → Folders → Scroll direction chooses Vertical (the existing default) or Horizontal pages.
Rows per page (1–6) and the existing column count define capacity; the actual grid fits the available
screen and touch-target space. The popup binds one page at a time, with swipes, Previous/Next buttons
and an accessible page indicator. RTL reverses swipe direction. Styling/content updates retain and
clamp the current page; reopening starts at the first page. Navigation does not rewrite manual order.

Drag/drop reorders within the displayed page using global indexes. Move earlier/Move later
accessibility actions can move entries across page boundaries. Header/footer drops are inert.
Alphabetical presentation does not rewrite stored order. Paging changes are immediate, including
under reduced motion. Arbitrary cross-page dragging and endless folder scrolling are not implemented.

## Local automation cards

Settings → Cards manages up to 24 named cards: add, edit, cancel, reorder, enable/disable,
delete and undo deletion. Each card has an optional featured personal-profile app, public app shortcut
or existing Home widget reference, plus six configurable action slots. Empty slots do not render.
The widget feature opens its existing placement on Home; it does not host a duplicate widget inside
the panel. Missing targets remain in stored configuration for recovery.

Open the card panel from the long-press Home menu or Home's accessible Cards action. The panel
also opens Cards settings. Cards are shown only when enabled and all configured local conditions
match: selected ISO weekdays (none selected means every day), a local 24-hour time interval,
charging, and default-network Wi-Fi transport. Equal start/end times mean all day. Overnight windows
use their starting weekday, including Sunday-to-Monday; the end minute is exclusive.

Actions require a deliberate tap and fresh target/configuration/condition validation. Conditions
control visibility; they do not launch apps in the background. Unavailable, hidden or nonpersonal
targets withhold the whole card, including its title. The editor uses generic recovery rows for
inaccessible cards. Cosmetic hiding remains cosmetic and is not app security.

At this initial pass, Bluetooth/weather/media cards remained open; the subsequent
[card content extension](CARD_CONTENT_EXTENSIONS.md) implements that documented subset.
Authenticated services, named-Wi-Fi/SSID conditions, unplugged/
non-Wi-Fi predicates and automatic background actions remain open. This is a bounded local cards
implementation, not complete Nova card parity or a privileged Android routine service.

## Storage and platform boundaries

Folder direction/rows and card configuration extend the existing version-1 settings JSON and
Chikku backup. Missing fields keep Vertical/three-row defaults and an empty card list. Cards use
component/profile identities and stable workspace item IDs; no device-local widget ID is stored
inside a card. Restored widgets still require fresh binding before their cards can appear.
No Room migration, dependency, advertising SDK, account or exported component is added.
Malformed structured card data rejects import/backup atomically and preserves damaged stored
settings through the existing recovery mechanism; it is never silently truncated or discarded.

`ACCESS_NETWORK_STATE` is the one added normal install-time permission, for optional card Wi-Fi
conditions. [Android's network-state guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
and [ConnectivityManager reference](https://developer.android.com/reference/android/net/ConnectivityManager)
define default-network callbacks and Wi-Fi transport. No SSID, location, online probe or network
request is used. Charging uses the system's sticky battery broadcast, following
[Android battery monitoring](https://developer.android.com/training/monitoring-device-state/battery-monitoring).
Callbacks and minute ticks exist only while the card panel is open and STARTED, and are removed
on close/stop/destruction. Native target lookups and registrations run off the main thread.
No card titles, app inventories, shortcut identifiers, queries or profile contents are logged.

## Verification

Android Studio JBR verification from an isolated source snapshot passed
`assembleDebug :core:model:test testDebugUnitTest lintDebug assembleDebugAndroidTest`:
**130 core + 536 app tests**, zero failures/errors/skips. Lint passed with warnings. The privacy
gate passed: four forbidden permissions absent, 49 SDK rules checked against 113 runtime modules.
The isolated build avoids overlapping outputs from the other active search/folder change in the
shared checkout; that change is preserved. No production source differed from the verified snapshot
at final comparison. Documentation links and `git diff --check` passed.

Android 16/API 36 `sdk_gphone64_arm64` emulator checks passed **8/8** in 66.233 seconds:

- `FolderPagingDeviceTest` (2): saved direction/row controls and activity recreation; synthetic
  four-page folder with buttons, swipes, RTL, page clamping/style updates and reduced motion.
- `CardEditorDeviceTest` (1): synthetic card creation, action selection, weekday/time/charging
  settings, persistence/recreation, cancelled draft, reorder, delete and undo.
- `CardsDeviceTest` (1): actual Home entry, live weekday visibility changes, Back and tapped
  drawer action; temporarily completes first-run setup and restores the original card/setup state.
- `HomeActivityDeviceTest` (3) and `StrictModeStartupTest` (1): existing Home/drawer/search/launch,
  recreation and narrow startup checks. These are emulator evidence for this pass.

Early device runs exposed cold workspace streams whose first emission is Loading: Save and
action validation now wait for a completed snapshot, remain bounded, reject failed/recovery states
and recheck visibility after native validation. Regression tests cover these paths. Other failed
attempts came from offscreen or clipped settings controls and fresh-emulator onboarding; the
fixtures now scroll within the actual viewport and preserve setup state. Folder/card bounds now
respect asymmetric system-bar/cutout insets, with measured-layout regressions. Final screenshots
of the synthetic editor, card panel and RTL folder were manually inspected after these fixes.

The tested emulator APK matches the saved `verified-app-debug.apk` by SHA-256. Ignored logs,
source hashes, test counts, synthetic screenshots and the APK are in
`build/verification/folder-paging-cards/`. The Samsung phone disconnected before this pass could
install or test the new build, so there is **no new physical-device acceptance or after-state
comparison**. Earlier Nova source screenshots and phone tests remain separate evidence.

Remaining acceptance: real Wi-Fi/battery transitions, public third-party shortcuts, widget-provider
loss/rebinding and widget navigation on hardware, physical profile-lock transitions, full archive
restore on a device, process death/reboot, TalkBack, physical RTL/large fonts and additional OEM/OS
coverage. Unit privacy/restore cases, synthetic folders, emulator runs and screenshots do not prove
those checks. No performance benchmark, release publication, merge or ADR acceptance is implied.
