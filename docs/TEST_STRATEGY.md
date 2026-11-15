# Testing, compatibility and release quality

## Search top result highlight (2026-10-25)

`SearchTopResultTest` composes the real search screen with synthetic apps/contacts and drives its
editor actions. It checks one marker matching Go in list/compact layouts and both result anchors,
late contact promotion, stale-query withdrawal, explicit-action/web fallback, unmarked history
and copy-only answers, recycled native drag-source labels/state and the synthetic Results preview.
Existing drag, anchoring and person-card suites remain separate regression evidence. These are
JVM/Robolectric checks; physical screenshots, TalkBack and OEM keyboard behavior require a device.
The direction follow-up checks physical placement in both language directions and native/Compose
app tiles, unchanged Go targets/text direction, immediate preference changes, rank-based traversal,
empty-query suggestions, unchanged vertical lists and a stable pinned preview whose description
follows the choice. `SearchAppPriorityDirectionTest` (core) covers every choice through settings
and portable backup, plus missing/future-value defaults; the full-field backup guard includes it.
Executed checks are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#search-top-result-highlight--2026-10-25).

## HOME return flicker regression (2026-10-21)

`HomeResumeStateTest` uses the production LauncherApps repository over synthetic platform data,
virtual time and the real folder-icon binder. After 20 seconds without UI collectors it checks
unchanged resolved state, the same cover drawable, one app enumeration and one workspace
subscription. A separate case checks immediate background profile invalidation and callback/
workspace cleanup after the ViewModel is cleared. Both tests failed before the sharing-policy fix.

`HomeActivityDeviceTest.returningFromLongStopDoesNotReplayWorkspaceOrAppLoading` exercises an
actual activity stop for 16 seconds and resume, collecting states only while STARTED. Assertions
report booleans, never device app labels. It uses the existing debug-only drawer/startup fixture;
it temporarily completes/restores onboarding and opens/finishes Chikku Settings to stop/return.
It does not change the HOME role or edit existing placements. This is separate from system Home gesture
animation, process-death, folder screenshots and frame-pacing acceptance.
[Executed checks and limits](IMPLEMENTATION_STATUS.md#home-return-flicker--2026-10-21).
## Memory footprint regressions (2026-10-21)

- `IconBitmapCacheTest`: one shared immutable bitmap per icon with separate drawables per caller,
  byte bound and LRU eviction, `trimMemory` levels, re-rasterising on scale/base-size change,
  shortcut caching and invalidation, fallback never cached, three `AppIconView`s sharing a bitmap.
- `WorkspaceBindWindowTest`: which pages are bound for a current page and scroll position, wrap
  seam with infinite scroll, pinned drag source page, release after settle, placeholders keep ids
  and cells. `DrawerCustomizationTest` adds release after hide, rebind on open, position restore.
- `HomeMemoryTrimTest`, `BadgeMemoryTest`, `FolderSearchDerivationTest`: dock photo trim/reload and
  one-time masking, shadow gradients built once per size, badge content equality and avatar cache
  bounds, ranking-update gating, folder search from the shared workspace model.
- Device check (manual, pending): `dumpsys meminfo` on the debug build at home idle, drawer open,
  drawer scrolled, drawer closed and after `am send-trim-memory RUNNING_CRITICAL`, compared with the
  same script on the previous build; plus a visual check for icon pop-in on swipe and drawer open.

## Restored website shortcuts (2026-10-18)

`RestoredWebsiteShortcutTest` uses synthetic LauncherApps failures and captured intents on
Robolectric API 29/34. It covers native-first Firefox launch, exact HTTP(S) destination recovery,
opaque/hostile IDs, missing browser, security/app-lock/profile boundaries and the shared Search path.
`BackupRestoreRoomTest` covers URL and UUID identities, appearance and placements through a clean
JSON/Room restore. [Evidence and remaining phone checks](RESTORED_SHORTCUTS.md) distinguish these
fixtures from native Firefox pinning, reinstall and SAF acceptance.

## Cloud acceptance rules — 2026-10-14

Read the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md), [historical upgrade/restore protocol](CLOUD_UPGRADE_RESTORE_ACCEPTANCE.md)
and [profile/widget protocol](CLOUD_PROFILE_WIDGET_ACCEPTANCE.md). The release remains NO-GO.

Inspect actual CPU quota, memory, disk, SDK and acceleration first. Use one orchestrator, one
Gradle process and one emulator; stop the emulator before builds. This run used max-workers2,
Xmx2g and in-process Kotlin on an 8GiB container without KVM. Host runners consume prebuilt APKs
and share a serial lock; they must never provision a second SDK/build/emulator.

- Cards UI fixtures inject in-memory settings/workspace/groups/customizations/apps and no-op
  media/weather/empty widget catalog. Assert original preference values **and XML bytes** without
  rewriting them. Keep resource/text/content-description queries, real activation/dismissal,
  enabled/clickable/disabled state and 48dp bounds in empty/populated/max/pending cases.
  Unlocked-screen assumptions are skips. Do not use coordinate fallbacks or attach inventories,
  labels, queries, broad window dumps or private-profile details.
- Actual upgrade requires distinct increasing versionCodes, exact package/certificate/byte
  verification, nondebuggable isolated IDs and genuine `adb install -r`, with historical source
  and seed compiled together. Room fixtures alone are insufficient. Require a changed canonical
  boot UUID plus boot completion/unlock after reboot; returning from adb reboot is insufficient.
- Service restore, real SAF producer/preview/cancel/confirm and actual configured external-widget
  rebind each need independent evidence. Portable widget ID0 is not a rebound provider.
  Externally interrupted preparations are EXPECTED_INTERRUPTION, not passing tests.
- Managed/private setup may be privileged only for setup; enumerate using fresh unprivileged
  adapters after dropping permissions. Validate HOME components before mutation and restore
  previous HOME/raw settings. Register an exact creation nonce before issuing create; reconcile
  timeout only through that unique in-memory identity, refuse ambiguous/unowned/pending state,
  wait for actual removal, and compare the complete final user-ID set with the preflight baseline.
- Persist the first failure before cleanup. Always terminate local instrumentation clients;
  unconfirmed remote cleanup blocks subsequent native recovery. A timeout, unknown APK update,
  zero completed assertions or assumption skip cannot pass.
- Acceptance R8 API keeps support separately compiled native consumers. Nondebuggable/minified
  acceptance builds do not establish normal closed-world production shrinking, performance,
  physical TalkBack/OEM/foldable/battery or Private Space support.

Newly executed:224 core and949 app JVM tests,71 Python tests, debug/test APK builds, lint/privacy
and seven rejected acceptance configurations. Native passes/failures/skips and final unavailable
checks are recorded separately; 2026-10-12 counts below are historical. Final native inputs were
compiled at8160cd7; their installation/completion was not confirmed before the cloud reset.
Missing raw artifacts are explicitly disclosed; no rerun result is inferred.

**Goal:** Chikku must never make a phone feel unusable after an update. HOME startup, workspace persistence, widgets, search and profiles are release-critical.

## Drawer thumb tracking regression (2026-10-12)

`DrawerCustomizationTest` checks continuous thumb mapping, preserved finger grab position,
uneven letter populations, actual visible-row popup labels, ordinary scrolling, cancellation,
and visibility when disabled or searching. The grid keeps mixed-letter rows with no headers or
filler cells. `DrawerFastScrollerDeviceTest` independently exercises native Android layout and
dispatched touch events on both rail sides, including an incomplete final row and both scroll
limits. Its drawer, app data and icons are synthetic and in memory; it does not launch HOME,
change saved settings or query the installed app inventory. Native fixture results are separate
from on-screen finger feel, screenshots, TalkBack and frame-pacing evidence. Executed checks are
recorded in [implementation status](IMPLEMENTATION_STATUS.md#drawer-thumb-tracking--2026-10-12).

## Test pyramid

| Layer | Focus | Tools / examples |
|---|---|---|
| Pure Kotlin unit | Grid occupancy, collision, search ranking, gesture mappings, backup parser | JUnit, property-based/fuzz tests |
| Repository/integration | Room transactions, migrations, DataStore, app/profile event coalescing | AndroidX Test, Room migration tests |
| Instrumentation | HOME role, LauncherApps, widget binding/config, shortcuts, Private Space | Emulator + real-device tests |
| UI/accessibility | Drag/drop, folders, dock, settings, TalkBack, keyboard, large font | Espresso/Compose tests, Accessibility Scanner |
| Performance | Cold/warm HOME, drawer, drag, search, icon cache, memory | Macrobenchmark, JankStats, Baseline Profiles |
| Release smoke | Upgrade, restore, switch launcher, package changes | Physical OEM matrix |

## Minimum compatibility matrix

| Device/OS | Focus |
|---|---|
| Android 10/API 29 emulator | minSdk and older navigation |
| Android 12/API 31 emulator | widget/shortcut behavior |
| Android 14/API 34 emulator | permissions and role flow |
| Android 15/API 35 Pixel + OEM | Private Space, work profile |
| Android 16/API 36 Pixel + Samsung/OnePlus | target policy, gestures, widgets |
| Android 17/API 37 Pixel | new memory/widget rules, adaptive layouts |
| Samsung One UI physical | gesture navigation, Recents, widgets |
| OnePlus/OxygenOS physical | home resume, icon rendering, animation |
| Motorola physical (if available) | widgets, process death |
| Foldable/tablet | cover/inner profiles, resize, rotation |
| Managed work-profile test | profile isolation and quiet mode |

A green emulator suite is not proof of OEM compatibility. Keep a public known-issues matrix after launch.

## P0 regression suites

1. HOME role selection, first launch, resume after process death, corrupt optional settings.
2. Search rapid typing, invalid icons/labels, package changes, locked profiles.
3. Icon install/update/remove, custom icon pack missing, no invisible icons.
4. Widget add/bind/config/resize/remove and missing provider; no leaked widget IDs.
5. Backup roundtrip across different grid sizes and schema versions; import rollback.
6. Locked Private Space content never appears in drawer, search, logs or backup.
7. Android 17 target-37 RemoteViews memory limit (before raising target).
8. Accessibility: TalkBack folder navigation, keyboard, large fonts, reduced motion.

## Property and fault injection

Hidden-app picker checks (`HiddenAppsGridViewTest`, Robolectric SDK 36): select/deselect multiple
apps without removing them from the picker, distinguish component/profile copies, tap and drag
the alphabet rail including the final letter, empty/single-bucket lists, narrow RTL grid touch
targets, custom names/icon sizing and an accessible Open action. These use fake app/icon data;
physical selection/persistence, TalkBack, large-font settings layout and icon-pack appearance
require separate device workflow evidence.

Notification/quick-settings drag regression checks (`NotificationDragTest`, Robolectric SDK 36):
handoff before finger lift, exactly one request, cancellation during/after handoff, a subsequent
gesture, short/reversed/horizontal movement, icon overrides and the release-fling fallback.
SystemUI is faked in these checks; physical-device touch injection verifies shade opening separately,
and neither check measures animation frame pacing.

Widget resize regression checks (`WidgetResizeFrameTest`, Robolectric SDK 36): all four edges
and four corners, fixed opposite edges, repeated resizing, provider/grid limits, RTL, full-grid
handles, cancellation, rejected and in-flight commits, and no-op release. Position and span use
one repository transaction; Room tests verify unchanged state on rejection and single-step undo.
`WidgetResizeFrameDeviceTest` injects real drags into a test overlay in HOME (LTR and RTL); it does
not bind a third-party widget or modify the persisted layout. Real-provider add/configure/resize,
process death and TalkBack still require separate workflow evidence.

Widget picker layout checks (`WidgetPickerTest`, Robolectric SDK 36): portrait previews sharing
a row, wider previews spanning columns, RemoteViews scaling without clipping, window narrowing
and expanding, larger fonts, RTL ordering and selecting the correct card by tap/long press.
These checks use synthetic providers; screenshot inspection of real Calendar previews on a
Galaxy S24 Ultra verifies the compact picker visually, without verifying widget binding or drag/drop.

Optional Discover regression checks: preference absent/off → no provider queries or binding;
enable → first-page swipe (and mirrored RTL direction)/accessible open; infinite-scroll precedence;
cancelled or inward swipe; Google absent or disabled; direct service rejection/unresponsive provider; app-opening fallback and launch failure; provider Binder failure/reconnect;
disable/stop/safe mode → teardown; queued callbacks after detach cannot move HOME. JVM/Robolectric
fake-service checks are implemented; live Google feed/OEM/TalkBack coverage is still required as
described in [ADR-0004](decisions/0004-optional-google-discover.md).

- Generate random grids/items/spans; assert no collisions and stable undo/redo.
- Interrupt every stage of backup import and DB migration; assert atomic rollback.
- Randomize package/profile change event ordering; ensure stable AppKey identity.
- Supply malformed icons, null labels, oversized backup entries and invalid shortcut IDs.
- Kill process during drag, widget configuration and page reorder.

Optional web autocomplete regression checks: settings default-off and roundtrip; fixed origins and
query encoding; unsupported engines; bounded/malformed responses; rapid typing and stale-request
cancellation; provider change, disable and closing search; independent offline app results; no
contact/app/profile data passed to the source. Live provider calls and physical UI checks are
separate from fake-source unit/Robolectric evidence. See [ADR-0005](decisions/0005-optional-web-search-suggestions.md).

## Performance gates

Record measured P50/P95 on named reference hardware; define thresholds **after** prototype measurements. Track first-frame time, drawer open latency, drag frame jank, search latency, memory growth after days of uptime, widget bitmap use and ANRs. Do not claim a universal "60 fps" without device measurements.

## CI gates (to add when code exists)

- Every PR: formatting, static analysis, unit tests, lint, debug build, dependency/license scan.
- Nightly: instrumentation on API 29, 35, 36, 37; migration fixtures and screenshot tests.
- Before beta: manual OEM smoke, widget and profile tests, privacy manifest audit.
- Before stable: staged rollout, changelog, recovery/rollback APK, no P0 open issues, known limitations documented.
- Release builds signed using secure secrets; no signing keys in repo.

## Bug triage and verification

Each issue records device, OS, launcher version, reproduction steps, evidence, severity, suspected root cause, fix PR and **device/build that verified the fix**. Historical Nova bug reports are regression inspiration, not proof Chikku shares the same bug. See [USER_PAIN_POINTS.md](USER_PAIN_POINTS.md).

Dock customization checks: backward-compatible settings/backup decode and bounded numeric values;
independent dock sizing, labels and padding; wrap/reset/remember page behavior and delayed writes;
custom round/cut corner rendering; overlay reparenting, hidden-state recreation, recovery and
successful/failed launches. The physical `DockSettingsDeviceTest` uses real dock swipes,
activity recreation, drawer swipe, own-app launch and mapped double tap while restoring its
preferences. Process death, reboot, physical drag/drop, TalkBack, RTL, large fonts and other OEMs
need separate evidence. See [dock settings](DOCK_SETTINGS.md).

## Article regression coverage (2026-10-11)

`MicroResultsTest` exercises the article conversion examples, affine temperature offsets,
incompatible/invalid units, ambiguous input, bounded URL/provider construction, search switches
and settings compatibility. `MicroHandoffTest` verifies chooser/encoded map/dialer/composer intents
and IME selection without sending or calling. `SearchHistoryTest` covers recording, decay,
hour-of-day ranking, expiry, caps, the codec and live resolution. `LocalSearchHistoryTest` covers
default-off, hidden/locked/nonpersonal filtering, pause-keeps-history, restart, forget/clear,
frequent-apps migration and corrupt storage. `SearchHistoryViewModelTest` checks the empty-query
pipeline and immediate withdrawal of hidden, locked or turned-off items. `WidgetCornersTest` covers padded clipping
and radius bounds. `DockImageTest` covers masking/opacity and refusal of ungranted/noncontent URIs.
These are JVM/Robolectric checks. `MicroResultsDeviceTest` exercises actual search rows, recreation,
settings navigation and the visibility of widget controls, while restoring touched search settings.
It does not perform calls, send messages, open providers, change default HOME or edit placements.
Further acceptance is listed in [article audit](NOVA_ARTICLE_PARITY.md).

## Phone settings regression scope (2026-10-11)

The [phone inventory](NOVA_PHONE_SETTINGS.md) distinguishes Nova observation from Chikku behavior;
final run outcomes and named hardware are recorded there and in
[implementation status](IMPLEMENTATION_STATUS.md). The new suites cover the following scope;
their presence alone is not a passing-run or device claim.

- `ExpandedSettingsCodecTest`: old payload defaults, independent settings roundtrip, invalid
  enums and bounded geometry. Existing backup suites cover portable settings inclusion.
- `LabelPresentationTest`, `WorkspaceLabelSettingsTest`, `DrawerCustomizationTest` and
  `FolderCustomizationTest`: label visibility/measurement/color reset, separate surface styles,
  list rows, navigation/launch policies and preservation of stored folder order.
- `ExtraGestureClassifiersTest`, `ExtraGestureTouchTest` and `WorkspaceTransitionTest`: cancelled,
  reversed and unassigned input, child touch preservation, rotation ambiguity, Home-button scope,
  and clearing/restoring transforms under effect changes or reduced motion.
- `AppMenuCustomizationTest` and `BadgeCustomizationTest`: independent menu actions, provider
  limits, accessible wrapping/RTL touch targets, explicit public listing intent, appearance reset
  and synthetic badge geometry. Shortcut success reports its originating surface; failure does
  not invoke close-after-launch policy. No uninstall, store install or notification is performed.
- `SettingsSearchIndexTest`, `SearchCustomizationTest`, `FolderSearchTest` and
  `NewAppsControllerTest`: static settings navigation, input/result presentation, hidden/profile
  folder exclusion, enable-generation/lock/recovery gates and injected install failures/races.

`ExpandedSettingsDeviceTest` supplies a separate instrumentation workflow. Device screenshots,
synthetic Robolectric input, mocked platform calls and physical runs remain distinct evidence.
Physical multi-pointer gesture feel, widget/child conflicts, keyboard compliance, TalkBack, large
fonts, real install/removal, process death and broader OEM coverage require their own named checks.

## Folder paging and cards acceptance (2026-10-11)

See [named checks](FOLDER_PAGING_CARDS.md). Cover folder page retention/clamping, RTL, touch targets, manual global reorder indexes and alphabetical nonmutation; card overnight weekday/end boundaries, unknown condition state, strict damaged configuration, durable mutations, stale target/profile/hide races, callback cleanup and immediate open/close/reopen. Physical fixtures must restore all touched preferences and leave workspace rows intact. Mocked widget/network/profile validation does not establish physical provider-loss or profile-lock acceptance.

## Search and folder usability regressions (2026-10-11)

[Search/folder behavior](SEARCH_FOLDER_CUSTOMIZATION.md) requires sizing and backup roundtrip/bounds, two bar positions/window resizing/RTL, independent dock gap and unchanged capsule height, continuous result drag/cancellation, live hidden/profile filtering at drop, layout lock/recovery refusal, real folder long-press routing, editor Save/Cancel, member-cover fallback, and explicit Open folder gesture checks. Named executed suites and device evidence are recorded separately in implementation status.


## Card content extension (2026-10-11)

[Card extensions](CARD_CONTENT_EXTENSIONS.md) require default-off/backward decode, invalid nested data, Bluetooth unknown/denied state and callback opt-in; weather malformed/network/cancel/cache/attribution; media access, multiple collectors, hidden/profile/session/control races; widget pending consent/configuration/cancel, provider loss, host ownership, corruption and fresh restore identity. Distinguish synthetic UI/source fakes from genuine hardware sessions and provider workflows. Preserve touched device settings.

## Drawer row spacing (2026-10-11)

`DrawerCustomizationTest` covers grid/list density changes, viewport resizing and bottom insets,
automatic reset, paged-search behavior and large icons/multiline-label minimum height.
`LauncherSettingsCodecTest` and `BackupCodecTest` cover legacy defaults and portable row settings.
`DrawerRowsDeviceTest` exercises the actual settings toggle/slider, drawer geometry and recreation,
restoring touched preferences and leaving placements intact. Executed results and hardware are
recorded in [implementation status](IMPLEMENTATION_STATUS.md#drawer-row-spacing--2026-10-11).

[Appearance control clarity](SETTINGS_CLARITY_AUDIT.md) requires legacy shared-folder-color migration, separate icon/window colors through settings and backup, and renderer checks that changing the window does not repaint its cached home icon. UI review checks concrete spacing amounts and inside/outside descriptions.

## Automatic search keyboard regression (2026-10-11)

`SearchKeyboardDeviceTest` checks focused input and actual IME visibility without tapping the
opened field, immediate key input and standalone activity recreation with the query retained.
It restores touched search preferences and never edits placements or changes default HOME.
Additional HOME overlay/presentation and result-drag checks were run with the surrounding
uncommitted feature work; see [implementation status](IMPLEMENTATION_STATUS.md#automatic-search-keyboard--2026-10-11).
An emulator run does not establish physical OEM or hardware-keyboard behavior.

## Priority reliability regression coverage (2026-10-11)

`BackupServiceTest`, `RestoreAccessTest`, `FileRestoreJournalTest`, `SettingsCommitFailureTest`
and `BackupRestoreRoomTest` cover interruption after each store, cancellation, journal prepare/
commit failure, uncertain write results, failed rollback and retry, reopened durable undo, exact
workspace widget IDs, damaged-record preservation, concurrent edits/paused observation, unsuccessful
settings commits and keeping rejected layouts out of the edit-undo stack.

`MediaCardSourceTest` and `MediaCardViewTest` cover independent collectors, hidden/work-owner
withholding, hide/access races, callback cleanup, cancellation and containment of source/command
exceptions. `UniversalSearchTest`, `SearchViewModelTest` and `SearchPlatformTest` cover immediate
local matches, provider failures, cooperative deadlines, an uncooperative old provider, query/profile
changes and cancellation reaching a blocked contacts provider. `ShortcutProfileTransitionsTest`,
`PlatformMappingTest` and `LauncherAppsRepositoryTest` cover cached results through lock/unlock,
profile removal, role loss, mid-read changes, unknown state and immediate snapshot clearing.

`RestoreRecoveryDeviceTest` uses isolated native SQLite/preferences/AtomicFile fixtures, injected
interruption and reopened stores, preserving the user's real layout. It does not kill the Android
process or bind a real widget. `MediaObservationDeviceTest` tests native default-off/access-denial
emission and a synthetic native media session only when notification access was already granted;
it never grants access or changes saved preferences. Actual work/private-profile transitions,
real process termination/power loss, archive selection on a phone and third-party provider acceptance
remain distinct checks. The user requested physical-phone checks for this pass; no emulator is used.

On the Galaxy S24 Ultra SM-S928B (Android 16/API 36), these two device classes plus
`SearchKeyboardDeviceTest` and `AppLaunchDeviceTest` recorded 10 passes, zero failures and one
notification-access-dependent skip in the final 42.027-second run. Default HOME stayed unchanged.
The keyboard fixture additionally restores raw settings/damage strings after restoring the model;
this avoids rewriting an older serialized payload during test cleanup. Exact settings-file equality
was verified on the final rerun. The first run retained only a pre-run checksum and changed the
serialized file, so that run does not establish exact raw-settings preservation.

## Remaining reliability regression coverage (2026-10-11)

`WidgetBindFlowTest` covers lost/mismatched provider consent, stale rebind targets, cancellation after persistence, deferred retirement for Undo and late configuration cancellation. `BadgePublicationRaceTest` rejects an old generation after clear/disconnect and checks access-denial cleanup. `HiddenItemsTest`, `LayoutChangeImpactTest` and `HiddenLayoutRecoveryRoomTest` cover shrink impact, retained identities/spans/folder members, atomic multi-item relocation, late transactional failure and avoiding Undo of an unrelated edit.

`WidgetLifecycleDeviceTest` uses a separate native host, test-only provider and in-memory Room database, scoped shell identity and cleanup. It checks native binding/RemoteViews/options, host restart, provider disappearance with a retained placement and fresh-ID rebind. `LayoutChangeDeviceTest` uses in-memory settings/workspace for native confirmation/cancel/apply. It never rewrites saved placements.

`scripts/phone-home-smoke.py` records the original HOME holder, checks real Home-button navigation and drawer close, replaces the same-version debug APK without uninstall, then records a small system-HOME timing sample, classifying launch state and excluding already-running instances from cold-start evidence. Finally it restores the role and startup-guard state, comparing exact settings bytes, logical database rows and no-backup files. Private snapshots are ignored local evidence. Same-version replacement does not verify migration or reboot. Named executed results are in [implementation status](IMPLEMENTATION_STATUS.md#remaining-reliability-work--2026-10-11).

## Personal home appearance coverage (2026-10-11)

`HomeSearchBarStyleTest` checks old/unknown defaults, codec and backup round trips, and unchanged
network opt-ins. `SearchBarSizingTest` checks the minimal 48 dp touch target, local-only search and
restoration of provider styling/margins. Clock ticking and final geometry require a physical
screenshot comparison; local checks alone do not establish visual equivalence. See
[home reference verification](HOME_REFERENCE_MATCH.md).

Widget rebind identity is compared again inside the Room update transaction, through the restore access gate. `WidgetBindFlowTest.editBetweenRebindPreflightAndCommitCannotReplaceTheNewerBinding` injects an intervening edit and verifies rejection, retained newer binding, no extra Undo snapshot and release of the unreferenced fresh ID. Repositories without an atomic rebind implementation reject this operation.

Widget-reference cleanup also reads all bounded retained edit snapshots, in addition to current content and durable restore Undo. Cancellation and production orphan sweeping share the restore gate. Unreadable/malformed snapshots defer deletion; a late-cancel regression verifies that an ID used only by ordinary Undo stays live. No Room schema change.

Executed on Galaxy S24 Ultra SM-S928B, Android 16/API 36: the final seven-class run recorded 13 passes, zero failures and one notification-access-dependent skip in 51.528 seconds; exact saved settings equality passed. Native HOME/same-version replacement smoke separately preserved the original role, settings bytes, logical database rows and no-backup files. One final confirmed cold sample was 294 ms; repeated already-running HOME deliveries are excluded and no P95/frame/battery acceptance is claimed. No emulator was used.

Home/drawer reference checks additionally cover exported shortcut token rejection, v2→v3 fixture
migration, shortcut identity/appearance backup and snapshot roundtrips, stale icon cancellation,
wide-tab/scroll-thumb restoration and dynamic calendar prefix parsing. A real browser pin consent,
x.com launch and screenshot comparison are physical evidence, separate from local unit tests.
See [reference scope and remaining checks](HOME_REFERENCE_MATCH.md).

Calendar metadata fallbacks additionally reject missing/malformed arrays and invalid days; physical
Google Calendar artwork on the reference phone confirms the valid daily-resource path. Empty Work
status preserves top-tab navigation order. Date transitions and non-personal native dynamic artwork
are not accepted by that single-day check.

## Offline typo matching (2026-10-12)

`TypoSearchTest` covers the reported app and multiword-name misspellings, insertions/deletions/
transpositions, partial typing, short/unrelated queries, distinct-word assignment, literal-first
and custom/original-name ranking, Unicode, oversized input and hidden identities.
`TypoContactsSearchTest` exercises an empty Android filter with a target beyond 10,000 candidates,
rank-before-limit, final-only action lookup, fresh renamed/deleted names, permission revocation,
scan failure and cancellation. Existing search pipeline/drag tests cover enable/disable,
stale requests and suggestion labeling on recycled result views.
These are JVM/Robolectric fixtures, not a real address-book or phone latency benchmark.
See [executed validation](IMPLEMENTATION_STATUS.md#offline-typo-matching--2026-10-12).


## Settings live preview coverage (2026-10-12)

[Live preview boundaries](SETTINGS_LIVE_PREVIEWS.md) distinguish synthetic sample scenes, runtime
renderers and physical acceptance. `PreviewDrawerFolderTest` checks fitting, first-row trim,
minimum content height, chrome reservation, sorting and pager boundaries.
`PreviewHomeDockSearchTest` checks native capsule/dock height and gap proportions, fixed envelopes,
bar position, navigation extension, overlay closing and synthetic result composition.
`PreviewAppearanceGesturesTest` checks independent menu actions, color/opacity, missing pack fallback,
effective inherited/folder gesture policies and cancelled/ambiguous touch input.

`LivePreviewsDeviceTest` uses actual settings controls with in-memory values and write/launch-rejecting
repositories. Held input distinguishes preview drafts from saved values. The scoped 2× font fixture
preserves the device's font configuration and supplies its real ActivityResultRegistryOwner.
Native AndroidView descriptions are exercised through UiAutomator, not inferred from Compose
semantics alone. Exact settings preservation and named physical outcomes are in
[implementation status](IMPLEMENTATION_STATUS.md#settings-live-previews--2026-10-12).

## Dock pages preview regression (2026-10-12)

Three focused `LivePreviewsDeviceTest` cases cover adding three pages during a held drag with
unchanged preview/slider bounds, numbered navigation/swipe/end boundary/reopen, shrinking at
2× font scale, and explaining when five apps per page limits three requested pages to two.
They use in-memory settings and reject real repository writes/launches. Sample navigation must
not save a new dock position. Native screenshot and settings-preservation evidence is recorded
in [implementation status](IMPLEMENTATION_STATUS.md#dock-pages-live-preview--2026-10-12).

## Contact communication services (2026-10-12)

`ContactServicesTest` (core) covers the default Call/Message/WhatsApp card, swapped and reordered
buttons, buttons left out without empty slots, a fewer-action provider, provider availability by
profile/hidden state, forged rows, and strict id parsing. `LauncherSettingsCodecTest` and `RestoreChecksTest`
cover persistence and restore. `ContactActionPlatformTest` (Robolectric) models the shipping
WhatsApp manifest (chat open, calls `CALL_PHONE`-guarded) and covers missing/unexported/foreign
handlers, stale/reassigned/joined rows, permission revocation, app lock and rejected launches.
`PersonCardLayoutTest` renders the real one-row card with native text measurement, four buttons
at 320/360/412dp × 1.0/1.3/1.5/2.0 font scale, RTL and long names, and checks Compose's merged
semantics tree: TalkBack labels and order, ≥48dp targets, no overlap, no clipped or ellipsized text.
`ContactCardPreviewTest` checks the settings preview follows the chosen, reordered buttons.
These are JVM tests, not
device runs; TalkBack on a device and real app launches remain manual checks.

## Search app-lock bypass regression (2026-10-12)

`SearchHandoffLockTest` uses Robolectric API 29/36 fixtures for the contact-to-WhatsApp bypass,
exact-chat retry, cancelled authentication, one-time grants, implicit contact/dial/SMS handlers,
chooser selection, profile identity, unreadable/loading lock state and removed handlers.
`SearchPlatformTest` retains store-to-web fallback and no-handler checks through the guarded path.
These use synthetic handlers/contacts and do not open WhatsApp or exercise a real biometric/PIN
prompt. Device authentication, OEM resolution and accessibility require separate physical evidence.
See [app lock](APP_LOCK.md) and the named results in implementation status.

## Recovery and native migration continuation (2026-10-12)

`UndoSnapshotRecoveryTest` injects structurally valid but semantically damaged snapshots (11
variants), rejects malformed present folder arrays without treating them as empty legacy data,
verifies unchanged live content/retained recovery records, and checks that widget cleanup defers. A separate case preserves out-of-viewport overlapping content.
`DamagedSettingsRecoveryTest` refuses recovery-copy commits and checks update/reset/import,
including operations before observation, then permits retry and verifies reopening.
`StartupGuardRecoveryTest` covers wrong preference types, negative/overflowing counters, failed
startup commits, retained evidence and explicit safe-mode exit. These are Robolectric checks.

`MigrationRecoveryDeviceTest` bundles the existing exported Room schemas as test-only assets,
opens synthetic schema 1/2/3 databases through the production factory with an explicit test-only
database name and isolated recovery directory, and
checks current-schema validation, identities/placements, folders/widgets/customizations/groups,
shortcut appearance, reopening and old Undo. It also tests a future schema without destructive
reset and semantically damaged Undo without replacing current rows.
`StartupRecoveryDeviceTest` exercises native preferences in separate files. Neither class
launches HOME, edits real layouts, queries the real app inventory or binds user widgets.

These fixtures exercise actual native SQLite/preferences on the named device, but do not install
an older production APK, reboot the phone, kill the process, simulate power loss or establish
third-party configured-widget compatibility. Keep those acceptance gates separate.
[Executed results](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

`ProcessDeathRestoreDeviceTest` is explicitly orchestrated: pause after each of settings,
customization, groups and workspace commits; sync a synthetic marker; externally force-stop the
target process; recover in a new PID before exposing workspace content. Without a supplied
canonical fixture UUID it skips, so normal connected suites cannot hang or kill HOME. Each pair
uses separate UUID-named native stores/journal and removes them after recovery. A deliberate
termination is not a passing preparation test; only the subsequent executed recovery assertion
is counted. This proves process termination at controlled boundaries, not power loss, reboot or
real production-version upgrade. [Actual execution](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

Executed on Redmi 9 M2004J19PI, Android 11/API 30: the final combined native suite passed 9/9
in 9.676 seconds, then all four externally killed restore fixtures recovered in new PIDs. Exact
settings/all original preference files, logical database rows, no-backup files and HOME selection
were unchanged. One initial non-void Kotlin test-method registration error was repaired before
the accepted run; it was a fixture failure. See the dated evidence above for scope and limitations.

## Missing live preview checks (2026-10-12)

`PreviewGapsDeviceTest` exercises visible source/ranking changes, the shared synthetic contact card,
offline autocomplete, drawer launch/return/scroll, folder outcomes, group tab/member order,
automatic menu context, held HSV draft input and Cancel, actual/add/demo home pages, RTL Discover,
large-font debug overlay and card condition sample controls. The fixture freezes real repositories,
uses only synthetic app/group/card data and asserts that real serialized preferences are unchanged.
The fixture waits for drawn frames/enabled replay controls and fixes its intentional font configuration.
Search pinning checks the scrolling-settings viewport, because reported accessibility bounds varied
while the viewport remained fixed. Cropped screenshots are synthetic scenes, not HOME/app inventories.
Partial/interrupted runs and
physical results are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#missing-live-previews--2026-10-12).

## Focused appearance navigation (2026-10-12)

`SettingsSearchIndexTest` checks that appearance controls resolve to Accent, Icons, Motion,
Screen or App menus, while the Style overview remains a separate destination.
`PreviewGapsDeviceTest` uses frozen repositories to navigate the five pages, compare the fixed
preview/settings viewport, exercise custom accent drafts and menu-corner held input before save,
and switch independent Home/Drawer actions. Large-text/RTL is a scoped configuration fixture;
its Activity wake flag ends with the fixture and does not change device settings. Existing radius
workflow tests now open App menus before testing its slider. Executed and unavailable checks are
recorded in [implementation status](IMPLEMENTATION_STATUS.md#focused-look--feel-pages--2026-10-12).

## Production reliability verification batch (2026-10-12)

The current reliability batch adds overflow-safe workspace bounds and overlap checks, exact nearest-placement ranking, adversarial layout/search stress coverage, and restore-retry cases. The final forced JVM suite and native evidence are summarized in the [QA regression report](QA_REGRESSION_STRATEGY.md#executed-evidence--2026-10-12) and [QA results](QA_RESULTS.md); scenario-level status remains in the [master matrix](QA_MASTER_MATRIX.md).

Native recovery used synthetic stores on a disposable API 36 emulator. Process termination at each restore checkpoint recovered successfully in a new process. Separate isolated physical recovery fixtures preserved the Redmi's installed APK, settings, database and HOME selection. General settings fixtures require completed onboarding and stock HOME; DefaultHomeDeviceTest exercises HOME-role behavior separately. Neither synthetic recovery nor the physical isolation check establishes release-upgrade, reboot, widget-provider or broad OEM acceptance.

The API 36 settings run also exposed an unresolved accessibility finding: the Cards page draws its Add card FAB, while the Android accessibility hierarchy does not expose the control. See [F-02](QA_RISK_REGISTER.md#qa-risk-register--2026-10-12). Retain semantic-query coverage and use the disposable native fixture for retesting. Current native totals and any final rerun are recorded in the linked QA report, not inferred from source or earlier runs.

## Horizontal scrolling regression coverage (2026-10-13)

`PageMotionTest` covers direction/sensitivity, cancelled/velocity/reversed settling, virtual wrap
slots, duration, reduced-motion transforms and old/damaged settings. The settings codec's full
roundtrip and backup-coverage guard include all added preferences. `HorizontalPagingTest` covers
rendered wrap pixels in LTR/RTL, original view identity, cancellation, disabled edge feedback,
diagonal input, interrupted settling and indicator tap/scrub/direct accessibility actions.
`DrawerCustomizationTest` exercises real paged transforms, fractional indicators, independent
controls, reduced motion and page jumping. `HorizontalPagingPreviewTest` checks native cells
follow each surface's preferences and a held strength draft without any repository writes.
`HorizontalPagingDeviceTest` compiles three synthetic native fixtures, but compilation is not
a passing device run. The Redmi blocked test-runner installation.
[Executed evidence and unverified acceptance](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).

Review regression coverage adds `HorizontalPagingTest` checks for clearing pages during release/
cancellation in LTR/RTL with looping on/off, a fresh swipe after recovery, and clearing an animated
settle without stale motion reaching new pages. `WorkspacePagerRemovalTest` checks that layout
preserves the incoming slide, completion returns control to page effects, and navigation or reduced
motion cleans up translation/alpha. `DrawerCustomizationTest` checks both indicator visibility
and reclaimed body height through search, return to paging, vertical style and a single page.
These are synthetic Robolectric fixtures; native animation/accessibility acceptance remains pending.

## Swipe-action app search (2026-10-13)

`AppPickerSearchTest` checks real Compose input/recomposition, no matches, clearing and component/
profile target selection with an existing action. `SwipeActionSearchDeviceTest` checks the actual
HOME dialog host and swipe sheet with a native keyboard in both swipe directions, synthetic app
data and in-memory edits. Control taps wait for animation bounds to settle; real serialized
preferences must remain unchanged. Native execution and unavailable checks are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#swipe-action-app-search--2026-10-13).


## Compose test frame isolation (2026-10-14)

`AppPickerSearchTest` and `DrawerGridOverviewTest` exercise in-place search and preview changes.
Their Robolectric fixtures use fresh lifecycle-bound main dispatchers and Android frame clocks,
explicit snapshot notifications and virtual frame advancement before semantic/layout reads.
This prevents test order from reusing an earlier test's Choreographer or leaving state changes
undelivered. Keep the actual query, selection, draft, mode and geometry assertions; fresh
first compositions alone do not cover these interactions. Full-suite verification and its
initial failures are recorded in [implementation status](IMPLEMENTATION_STATUS.md).

## Play candidate packaging and consent (2026-10-15)

`GoogleDiscoverResolverTest`/`DiscoverHealthCheckerTest` exercise the app-only distribution with
an installed same-signer bridge, preserving the restored setting and requiring no bridge.
`AccessibilityDisclosureTest` distinguishes constructing/showing/dismissing the shared disclosure
from the affirmative Android-settings handoff; actual Settings-row activation and system consent
need native workflow evidence. Source review found and removed its prior off-service direct path.

The release APK gate checks actual package/version/min/target/debuggable state, final permission
set, byte-identical bundled notices/policy/source documents, reviewed runtime coordinates/artifact
hashes and native LOAD/ZIP alignment. It does not establish 16KB runtime/RELRO, TalkBack, native
provider/profile, upgrade/reboot or performance acceptance. CI now includes optimized release build,
release lint and this gate; the current GitHub Actions budget prevents jobs from starting, so local
results and unavailable CI remain distinct. See [current artifact review](release/ARTIFACT_VERIFICATION.md).


MET weather regression coverage includes offline aliases/country ambiguity/Unicode/results bounds, exact unchanged restored coordinates, Celsius/Fahrenheit conversion without refetch, forecast units/time/range validation, Expires/Last-Modified/304,203 warning, gzip/redirect limits, persisted throttling/Retry-After and coroutine cancellation. A separate API36 native test uses only public Greenwich fixture coordinates; it is distinct from packet capture, physical-device or scaling evidence.
