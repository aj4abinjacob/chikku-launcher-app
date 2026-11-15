# Implementation status

## Search top result highlight — 2026-10-25

Implemented on `feat/search-top-result-highlight`. Typed queries mark the existing keyboard Go
target with an accent outline, tinted background and **Top result** label. List/compact apps,
the leading person card, folders, shortcuts, contact rows and action/web fallbacks share the
marker. It follows late ranking changes and clears immediately when the field no longer matches
the displayed results. Empty-query history stays unmarked. Native drag sources expose and clear
the same accessibility state; the Results settings preview uses synthetic content and shared styling.

The highlight preserves ranking, launch/drag behavior and saved settings. No permission, dependency,
schema or backup-format change is introduced; the marker is derived UI state only.

Initial highlight verification passed: 32 JVM/Robolectric tests across `SearchTopResultTest`,
`SearchDragSourceTest`, `SearchResultsAnchorTest`, `SearchPreviewGapsTest` and
`PersonCardLayoutTest`. Debug APK assembly, 231 core tests, Android lint and the privacy gate passed
with Android Studio's bundled JBR. The initial full app suite ran twice: 1,112/1,113 passed both times;
`DrawerGridOverviewTest.actualSizeIsDefaultAndModeFollowsRowAndSizeDraftsAndTheButton` failed at
line 83 because its full-grid description had not updated. That test passed alone and alongside
all six new search tests; neither its source nor drawer runtime code was changed. That run remained
failing; the cause was not established as a product regression or a baseline issue.
No adb device is connected; physical screenshots, TalkBack and OEM
keyboard checks have not been performed for this change.

Direction follow-up: Settings → Search → Apps and shortcuts now offers **App priority direction**
for compact matching/suggested app rows. Follow language keeps the default; Left to right and
Right to left select physical priority order independently of language. One shared row renderer
serves search and synthetic previews, retaining ranked data, app-label direction, the Go target
and the highlighted app. Tile traversal indexes keep priority order. Settings/backup codecs carry
the new defaulted enum without a format change or device dependency. The pinned Results preview
updates immediately; vertical lists retain their order. Search fixtures now drain their destroyed
activities' callbacks, reset the shared Compose dispatcher after each test and give the pinned
preview its own lifecycle-bound frame clock.

Final direction verification passed on the development Mac with Studio's bundled JBR: debug APK
assembly, all 233 core tests and 1,116 app JVM/Robolectric tests, Android lint and the privacy gate.
The previously failing drawer-preview test passed in this full run. Added documentation links
and whitespace checks passed. Physical screenshots, TalkBack, font-scale acceptance and OEM
keyboard/one-handed gesture checks remain unavailable because no adb device is connected.

## HOME return flicker — 2026-10-21

Implemented on `fix/home-return-flicker` from `ba304ea`. HOME's STARTED-only UI collection stopped
the combined model after five seconds; the nested app observation then stopped as well. Returning
restarted Room/LauncherApps with loading states. Apps temporarily resolved as UNKNOWN, so selected
folder covers fell back to member previews before returning to their normal icons. This also
caused unnecessary workspace/label redraws.

`HomeViewModel.uiState` now starts lazily on first observation and stays subscribed until its
ViewModel is cleared. Views still render only while STARTED. Existing package/profile callbacks
keep the model current while backgrounded, including immediate invalidation; no polling, service,
permission, dependency, persisted state or backup-format change is introduced.

Local checks: both new regression tests failed on the original implementation, then passed after
the fix. Full verification passed: 231 core tests, 1,107 app JVM tests, debug and instrumentation
APK builds, Android lint and the privacy gate.

Physical check: Galaxy S24 Ultra SM-S928B, Android 16/API 36, separate debug installation.
`HomeActivityDeviceTest.returningFromLongStopDoesNotReplayWorkspaceOrAppLoading` passed 1/1 in
18.817 seconds: open Chikku Settings, confirm HOME is stopped, wait 16 seconds, finish Settings,
then verify every returned state for another second without workspace/app-loading changes. The
first fixture attempt failed to resume through `ActivityScenario.moveToState`; the corrected test
uses an actual activity handoff and temporarily completes/restores the debug onboarding flag.
It does not select a default HOME or edit existing placements. The original release installation
was not replaced. Temporary debug/test installations were removed after the check.

The native fixture does not exercise a covered folder or the system Home gesture; covered-folder
artwork is synthetic JVM evidence. System-gesture frame pacing, process-death startup and broader
OEM animation acceptance remain unverified.

Owner-authorized phone update follow-up: built `0.1.0-beta.4`/code 5 from release-repository
`5a83402a5e739128bc0622812e54586855dca165` plus this exact HOME fix, its tests and the version bump.
Persistence code, settings, schemas, dependencies and other release inputs match the installed
code 4 source. The frozen matching source archive and build evidence are retained locally under
`chikku-release-evidence/2026-10-21/home-return-update/`. This separate release baseline passed
231 core and 1,115 app tests, release build/lint, privacy and APK/dependency/notice/alignment checks.

With the owner's explicit approval, uploaded the bundle through Play's upload-only route and
downloaded its signed universal APK. Its signing certificate matches the installed release;
permissions are unchanged. `adb install -r` upgraded the S24 Ultra from code 4 to 5, retaining
the application UID and default-HOME selection. Play Protect requested a one-time scan; its
global preference was not changed. Release HOME resumed with the existing clock, icon layout,
dock and search bar, without onboarding, recovery or a current-process fatal crash. No uninstall
or app-data clearing occurred. Raw data equality was not inspected on the nondebuggable release.
Play bundle 5 remains a draft with zero releases; no track rollout or review submission was made.

## Restored website shortcuts — 2026-10-18

Implemented on `fix/restored-website-shortcuts`. A Chikku reinstall can leave restored shortcut
placements without Android's pinned intent. Native launch remains first; stable-Firefox HTTP(S)
URL IDs can fall back to an ordinary page in the same personal-profile Firefox. Newer UUID IDs
cannot reveal their destination and need to be added again from Firefox. Missing shortcuts receive
specific instructions across Home/folders/gestures, Cards and Search; Backup explains the limit.
No saved identities, appearance or placement are changed, and backup format 1 is unchanged.
[Publisher evidence, behavior and limits](RESTORED_SHORTCUTS.md).

Verification on the development Mac with Android Studio's bundled JBR:

- `assembleDebug`, `:core:model:test` (230 tests), `lintDebug` and `scripts/check-privacy.sh` passed.
- `RestoredWebsiteShortcutTest`: 18 synthetic cases passed across Robolectric API 29/34.
  `BackupRestoreRoomTest`: five cases passed, including clean restore of URL/UUID shortcut IDs,
  appearance and folder/placement identities. Focused shortcut, gesture and backup tests passed.
- Initial full `testDebugUnitTest` on the older development baseline: 1,094 cases, one failure in
  `SearchResultsAnchorTest.slowDragScrollsResultsInBothModes` (line 84); reproduced on a second full
  run. The SearchResultsAnchorTest class passed alone. An isolated archive of unchanged baseline
  `c8237be5dd1b016a19d9ad46f893dac4a711a57d` reproduced that suite/isolation failure.
  No unrelated search-scroll implementation was changed in this patch.
- Final integration over main `53bb644ab4b56112eefaf28ef75b0abc2ec8a0a1` (which already includes
  the Compose test-dispatcher fix): `assembleDebug :core:model:test testDebugUnitTest lintDebug`
  passed. All 1,098 app and 231 model tests passed, including the earlier search-scroll case.
  The privacy gate and all 79 `scripts/tests` parser regressions also passed. Integration review
  retained native-first launch, package/profile boundaries and all restored identities; no extra
  permission, dependency or backup-format change was introduced.
- `git diff --check` and relative links in changed documentation passed. No new source-ID namespace
  or table was introduced.
- `adb devices -l` returned no devices. Native Firefox pin/reinstall/SAF restore, actual URL launch,
  TalkBack and PWA/browser presentation were unavailable, not passed.


## Source download and recovery follow-through — 2026-10-16

The matching-source page and verified multipart archive are prepared locally, with permanent
per-revision pages and an offline About copy-link action. Publication/ownership answers are
pending; the new app changes are not in the1840777 candidate. Actual historical APK upgrade,
real credential-free reboot, fresh-install service restore and corrected system SAF round trip
pass on the named API36 emulator. Real external widget configuration/restart/provider loss and
fresh-ID rebind/restore pass through preserved-state continuations, including correction of
the missing size handoff for an already measured cell; a subsequent fresh complete sequence passes
all nine native invocations. Six scoped API36 work/private enumeration/transition stages and
owned cleanup pass too.16KB runtime and
profile/physical/performance gates remain open. A recovery-layout fix releases the invisible
dock's space so recovery controls fit with the non-default banner; two regressions fail before
and pass after it; actual API36 recovery exit and stable Home also pass. No Play upload or release was made. See the
[dated evidence and limits](release/SOURCE_AND_ACCEPTANCE_2026-10-16.md).

## MET weather verification — 2026-10-16

Owner-approved migration is implemented on `feat/met-weather-migration`: MET forecasts, offline
GeoNames city search, bounded provider cache/throttling, attribution and revised local policy.
Full run: **230 core + 1,062 app tests**, zero failures/errors/skips; **79 Python regressions**;
debug/test/optimized release assembly, debug/release lint (170 warnings + one hint, zero errors),
privacy gate and static package/dependency checks pass. **Three API 36 ARM64 native tests passed
in 58.760 seconds**, including real packaged-city search, a public Greenwich MET forecast, cache
reuse on Refresh/unit conversion, offline editor selection and lifecycle cancellation.
A missing gzip asset path was found on the first native attempt, fixed and successfully retested;
that failure remains recorded. This is not physical, packet-capture, Play-delivery, aggregate-load
or genuine 16 KiB runtime acceptance. Existing backup settings/format remain unchanged.
See [migration evidence](release/MET_NORWAY_SWITCH.md). Revised public-policy publication is
owner-approved and verified; Data safety is saved, awaiting review submission. **Production remains NO-GO.**


## Play preparation verification — 2026-10-15

**Production remains NO-GO.** The separate private release repository preserves all942 files of
newer local main9ba58a9 with fresh ancestry. The [current review](release/PLAY_PREPARATION.md)
and [artifact report](release/ARTIFACT_VERIFICATION.md) record migration, licences, signing,
privacy, exact-source preparation and remaining distribution gates.

Fresh checks: **230 core +1054 app JVM/Robolectric tests**, zero failures/errors/skips;
**79 Python tests**; debug and optimized release/test-APK builds; debug/release lint each
170 warnings and one hint, zero errors; manifest/tracking gate; exact109-component/70-artifact
runtime licence/source audit; three expected signing-configuration rejections. Android CI could
not start because of the GitHub Actions account budget; local results are separate.

A newly created disposable ARM64 Android16/API36 AVD ran **all five CardEditorDeviceTest methods
successfully in95.466 seconds**. Synthetic fixtures preserve real preference maps/XML bytes.
This closes the previously unavailable corrected API36 Cards fixture run, not physical
TalkBack/Samsung acceptance. The optimized nondebuggable production-ID APK was signed with the
standard Android debug key **only for local emulator installation**, never as a Play artifact.
Its offline privacy reader opened/scrolled/closed; native notices displayed original headers.
The Settings accessibility entry displayed the disclosure, refusal returned with service OFF,
and retry/affirmative consent reached Android Accessibility settings. No service grant/action
or complete declaration video was recorded. One wrong automation label (Close rather than
Close document) was corrected using the actual UI; it was not an app defect.

The AVD used two cores/2048MiB, actual4096-byte pages, fingerprint
`google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`.
No Gradle ran while the emulator was active; the emulator was stopped after checks. All daily
launcher/device data remained untouched. APK ELF LOAD/ZIP16KB alignment passes, but GNU_RELRO
and genuine16KB runtime acceptance remain open. Real upgrade/reboot/first unlock, SAF restore,
configured external widgets, managed/private transitions, physical scaling/RTL/OEM and measured
performance/battery/soak gates remain open. Historical evidence below retains its original scope.
Raw logs/screenshots and exact archive/rebuild manifest are in the owner's local
`chikku-release-evidence/2026-10-15` directory; do not distribute preparation artifacts until
source-access, provenance, weather rights and signing gates close.


## Cloud release validation — 2026-10-14

**NO-GO; all three areas are partially verified.** Started from current main `1c081bb9fdd7df4d854d2de7e31ba8e8128587d4`, preserving changes newer than `39b3930`; reviewed code is `8160cd7a715297b8332a31c790a9015685239f24` on `test/cloud-release-validation-20261014`.

| Area | Newly established | Still open |
|---|---|---|
| Cards accessibility | Resource-backed parent name/disabled semantics; native API29 empty/populated activation, dismissal/reopen, clickability, 48dp and preference value/XML-byte preservation; max/pending disable checks | Broader content-kind editor failed then assumption-skipped; final corrected fixture unavailable; fixed API36/Samsung/TalkBack acceptance |
| Actual upgrade / reboot / restore | Nondebuggable same-key historical development APK910001→910002, Room3→4, retained synthetic production stores and HOME; separate cold-boot retention; service export/fresh-install restore/missing dependencies/Undo/four external interruption recoveries | Bounded adb reboot failed; actual SAF round trip failed; corrected SAF native unavailable; secure first unlock and real widget rebind |
| Widgets / profiles / compatibility | Genuine external Fossify APK installed/seeded; same-component personal/work identity via fresh unprivileged enumeration; private/stale-profile source guards and cleanup safety | No complete configured external-widget pass; pause/resume/remove assertions incomplete; API35+ private exclusion and API36 native unavailable; physical OEM/foldable/battery gates |

Fresh automated checks: **224 core + 949 app JVM tests**, **71 Python tests**, debug build/test APK, lint (0 errors; warnings retained), privacy (4 forbidden permissions, 49 SDK rules, 113 modules), seven invalid acceptance configuration rejections. Final fixture builds compiled; their reused tasks are not new native passes. Earlier dated counts remain historical.

The final command service recovered after a disconnect without the checkout, SDK, APKs or raw QA files. The reset cause is unverified; only confirmed results and known hashes are preserved in the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) and [curated JSON](qa/cloud-release-validation-2026-10-14.json). Unobserved final native completion is **UNAVAILABLE**, never PASS. One orchestrator serialized builds and software emulators within the actual 8GiB/8-core cloud limit; no personal-device workload, publication, visibility change or main merge occurred.

**Last updated:** 2026-10-14. **Milestone:** M0 Foundation (in progress), plus the Nova-guide feature set on branch `feat/nova-guide` (see [below](#nova-guide-features-g01g22)).

This page tracks what has actually been built and how it was verified, separately from the requirements in [PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md) and the plan in [ROADMAP.md](ROADMAP.md). A requirement moves to a "Verified" status only with a recorded entry in the [verification log](#deviceemulator-verification-log). Unit tests, emulator runs, physical-device runs and screenshots are different kinds of evidence; name the one you have.

## Drawer grid overview correction — 2026-10-13

Implemented on `fix/drawer-grid-live-preview` from main `b7c4b63`. The reported 5×5 fitted grid
was previously cropped to one row and part of the next by the actual-size preview window.
A full-grid overview now draws the complete visible grid, with the real drawer cells and spacing
uniformly scaled to fit. The preview opens at actual size, because the whole grid only fits the
frame at about 25% scale (measured on a 411×891dp Robolectric screen), and follows the edit: row
count and Fit rows changes switch to the full grid, icon and label size changes return to actual
size. A temporary UI toggle switches by hand. Both modes follow drafts, preserve the preview
envelope and respect parent height constraints.

Added lower-row pixel/geometry and Compose mode/draft/accessibility-description regression tests.
[Repository CI run 38444377956](https://github.com/aj4abinjacob/chikku-launcher-development/actions/runs/38444377956)
did not start: GitHub reported that an Actions budget prevented the jobs from running, so CI
build/unit/lint/privacy checks did not execute. Local run (2026-10-14, JDK 21 from Android Studio):
`DrawerGridOverviewTest` and `DrawerTrueScalePreviewsTest` pass, `lintDebug` and the privacy gate
pass. Draft [PR #3](https://github.com/aj4abinjacob/chikku-launcher-development/pull/3)
remains unmerged pending automated and device verification.
No physical device, emulator or TalkBack check was executed for this correction.
See [preview boundaries](SETTINGS_LIVE_PREVIEWS.md#drawer-grid-overview-correction-2026-10-13).

## Status values

**Optional Google Discover experiment (2026-10-10): app-opening fallback verified on one device; embedded overlay unverified.**
Home screen settings now offer an off-by-default Google Discover (Google Now cards) switch.
The first-page outward swipe and an accessibility action request the Google-owned overlay through a
direct connection to the installed Google app. If embedding is unavailable, the user-authorized
fallback opens the existing Google app; no companion installation is needed. Missing providers and Binder failures are contained; disabling, stopping or
safe mode removes the connection. Settings/backup persistence, swipe routing and fake modern/legacy
service lifecycle are covered by `LauncherSettingsCodecTest`, `SharedPreferencesSettingsRepositoryTest`,
`WorkspacePagerDiscoverTest` and `GoogleDiscoverClientTest` (JVM/Robolectric API 36). Live Google
embedded cards, direct service compatibility, broader OEM behavior, TalkBack and benchmarks remain
unverified. The app-opening fallback displayed live Discover cards on a Samsung Galaxy S24 Ultra.
See [ADR-0004](decisions/0004-optional-google-discover.md) for sources and the remaining acceptance checks.

| Status | Meaning |
|---|---|
| Not started | No code merged for this requirement. |
| In progress | Code is being written or partially merged; acceptance criterion not met. |
| Implemented – unverified | Code merged and CI green; acceptance criterion not yet exercised on an emulator or device. |
| Verified on emulator | Acceptance criterion exercised on an emulator; log entry recorded. |
| Verified on device | Acceptance criterion exercised on a physical device; log entry recorded. |

## M0 feature matrix

| ID | Requirement | Status | Evidence | Notes |
|---|---|---|---|---|
| FR-001 | Register as HOME; guide role selection | Implemented – partly verified on device | Redmi 9: Android lists Chikku as a HOME candidate next to the stock and installed launchers (`HomeIntentResolutionTest`, `cmd package query-activities`); banner and settings screen offer the RoleManager request and the system Home-app screen | S24 Ultra default-HOME navigation and same-version replacement were checked in the 2026-10-11 reliability log below. **Open:** interactive system-role selection and reversion through the system picker. |
| FR-002 | Enumerate launchable apps/profiles | Implemented – partly verified on device | Redmi 9: drawer lists all launcher activities alphabetically with icons; `LauncherAppsRepositoryDeviceTest` (personal-profile identity, unique keys, deterministic order, refresh) | **Open:** install/uninstall updates on a device; work profile, quiet mode and Private Space exclusion untested (no work profile on the test phone). |
| FR-003 | Launch app and show app details | Verified on device (personal profile) | Redmi 9: drawer tap launched `com.miui.calculator`; `AppLaunchDeviceTest` (Started for a real component; typed `Unavailable` for unknown serial/component/package and non-launcher activity) | Work-profile launch and app-details screen not exercised on a device. |
| FR-004 | Persistent workspace, pages and dock | Verified on device (process death) | Redmi 9: items placed by drag survived `am force-stop` + cold start (new PID, same placement); `WorkspacePersistenceDeviceTest` (close/reopen, undo snapshot) | Reboot not yet tested. Multiple pages exist in the model, but drag cannot yet create a new page. |
| FR-005 | Drag/drop and collision resolution | Implemented – verified on device for basic moves | Redmi 9: dock → desktop move and drawer → desktop add both commit; a device-only bug (drops from the drawer were lost) was found and fixed in f498309; layout-engine property tests | No visual feedback for occupied cells; no keyboard/TalkBack move action; edge-hover page switching not exercised on a device. |
| FR-006 | Folder create/rename/reorder | Implemented – unverified | `RoomWorkspaceRepositoryFolderTest`, `FolderLogicTest`, `FolderHomeTest` (Robolectric, real `HomeActivity` against fakes) | Built as Nova-guide G22. Not exercised on an emulator or device. |
| FR-007 | Widget picker/bind/config/resize/delete | Implemented – partial synthetic device verification | `WidgetBindFlowTest`, `WidgetSizingTest`, `WidgetResizeFrameTest`, `WidgetResizeFrameDeviceTest`, `RoomWorkspaceRepositoryV2Test`, `HomeMenuAndCellLayoutTest`, `BackupRestoreRoomTest` | Built as Nova-guide G16. Resize handles cover all edges/corners on supported axes, including RTL; position and span commit atomically with undo, and cancelled/rejected edits restore the frame. Provider loss and restored widgets (id 0) keep a "needs rebind" placeholder; rebinding allocates a fresh id. Native host/provider-loss/fresh-rebind checks using a test-only provider passed on the S24 Ultra in the 2026-10-11 reliability log below. Third-party consent/configuration workflows remain open. |
| FR-008 | Offline app search | Verified on device | Redmi 9: typing "calc" filtered to Calculator; `HomeActivityDeviceTest` search/no-match/Back flows; 10,000-app unicode fuzz test (unit) | Local app search remains offline. Optional autocomplete now uses INTERNET only after opt-in ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)). Queries are never logged. |
| FR-009 | Recovery snapshots and schema migration | Implemented – partly verified | Corrupt database preserved before reset (Robolectric + device test on an isolated file); bounded undo snapshots; crash-loop safe mode (unit tests) | The current schema is v4 with additive v1→v2→v3→v4 migrations and host fixture coverage. New native older-schema fixtures are tracked in the 2026-10-12 continuation below; real production APK upgrade/reboot remain pending. The 2026-10-11 reliability pass adds journaled restore recovery; see the dated evidence below. |

## Nova-guide features (G01–G22)

Features described in the Android Authority Nova Launcher guide (modified 2025-11-04, read 2026-10-10), implemented on branch `feat/nova-guide`. The G-IDs are this branch's own identifiers; they are not the NS/NF/NP/NU/CV identifiers of the Nova research supplement and not the main FR/H/I/... matrix IDs. Unless a row explicitly records device evidence, features remain "Implemented – unverified" with JVM unit and Robolectric evidence only. Narrow physical-device checks are recorded in the verification log; they do not verify the whole feature.

| G | Feature | Status | Evidence (unit / Robolectric) | Known gaps |
|---|---|---|---|---|
| G01 | Animation speed incl. "none" | Implemented – unverified | `LookFeelThemeTest` | Applies to the drawer, folder open, page snap and app-menu popups (`theme.Motion`); other transitions (e.g. undo bar) use fixed platform defaults. |
| G02 | Desktop grid rows/columns, icon size, labels | Implemented – unverified | `HomeLayoutMathTest`, `WorkspaceControllerSettingsTest`, `LayoutPresetTest`, `RoomWorkspaceRepositoryV2Test` | Shrinking the grid keeps out-of-bounds items in storage rather than reflowing them. |
| G03 | Dock icon count, size, background, labels, dock pages and overlay | Implemented – partial device verification | `DockSettingsDeviceTest` on Galaxy S24 Ultra; `WorkspaceControllerSettingsTest`, `DockSurfaceControllerTest`, `DockBackgroundTest`, `HomeLayoutMathTest`, codec tests | See the 2026-10-11 dock verification log and [dock settings](DOCK_SETTINGS.md); storage remains one row, with no schema change. |
| G04 | Search bar on home and in the drawer; gesture opens search | Implemented – unverified | `HomeSearchEntryPointsTest`, `SearchViewModelTest`, `GestureActionExecutorTest` | — |
| G05 | Web search handoff (selectable engine), Play Store row, calculator | Implemented – unverified | `WebSearchUrlsTest`, `CalculatorTest`, `UniversalSearchTest` | Web search opens the user's browser/Play Store through an intent; online autocomplete is a separate off-by-default Google/DuckDuckGo integration ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)). |
| G06 | Deep search: contacts (opt-in), app shortcuts, custom app names | Implemented – unverified | `UniversalSearchTest`, `SearchPlatformTest`, `CustomLabelSearchTest` | Authenticated cloud integrations remain unavailable; opt-in local personal-app usage suggestions are implemented in the article additions below. Shortcut results need Chikku to be the default HOME. |
| G07 | Swipe actions on app icons and folders | Implemented – unverified | `GestureClassifiersTest`, `HomeGesturesActivityTest`, `FolderHomeTest`, `LongPressMenuDragTest` | Folder and icon actions share `GestureActionExecutor`; per-icon actions are edited in Gestures settings and from the app menu ("Swipe actions"). |
| G08 | Notification badges: dot, count, dynamic | Implemented – unverified | `BadgeAggregatorTest`, `BadgeAndroidTest` | Needs the optional notification listener; folders show an aggregate dot. Not checked against real notifications. |
| G09 | Night mode for drawer, folders, search, menus | Implemented – unverified | `LookFeelThemeTest` | A drawer background color chosen in drawer settings overrides the night-mode drawer color. |
| G10 | Home gestures bound to actions | Implemented – notification drag smoke verified; remaining actions unverified | `GestureClassifiersTest`, `GestureActionExecutorTest`, `HomeGesturesActivityTest`, `VerticalSwipeTest`, `NotificationDragTest` | Downward Notifications/Quick settings actions hand off during a deliberate drag, once per gesture. "Lock screen" and "Recents" need the optional accessibility service; shade actions use a non-public status-bar call that some OEM builds may block. Android owns the shade animation; no finger-tracked shade or stock Recents/gesture-animation replacement is claimed. |
| G11 | Labs (tap the version in About 7 times) | Implemented – unverified | `OnboardingAndLabsTest` | The "faster icon cache" Labs switch is stored but has no effect yet. The version-tap toggle was checked on a Galaxy S24 Ultra (SM-S928B, Android 16) on 2026-10-12: 7 taps hid Labs, 7 more showed it. |
| G12 | Padding, wallpaper scrolling, page indicator, infinite scroll, default page | Implemented – unverified | `HomeLayoutMathTest`, `WorkspaceControllerSettingsTest` | Wallpaper parallax not checked with a live wallpaper. |
| G13 | Drawer open method, subtle up arrow with visibility switch, grid, paged/vertical style, Top/Bottom/None search bar placement, background, fast scroller | Implemented – unverified | `DrawerModelTest`, `DrawerFeatureActivityTest` | Hiding search clears its query and keyboard. Visibility uses the existing opening preference; the arrow stays reachable if swipe up is assigned elsewhere. |
| G14 | Hide apps; drawer groups (tabs) | Implemented – unverified on device | `DrawerHiddenAppsViewModelTest`, `DrawerGroupsSettingsTest`, `HiddenAppsGridViewTest`, `CustomizationRepositoriesTest`, `UniversalSearchTest` | Hidden-app selection uses drawer icons with checkboxes and an alphabet rail, even when the drawer's rail is disabled or its style is paged. Hidden apps are left out of the drawer and search only. Hiding is cosmetic, not security; apps already on the workspace stay there. |
| G15 | Icon packs, icon shape, per-app icon/label, orientation, status bar, app long-press menu | Implemented – popup sizing smoke verified; remaining behavior unverified | `AppFilterParserTest`, `IconShaperTest`, `ShapeGeometryTest`, `ThemedIconProviderTest`, `LongPressMenuDragTest`; physical popup screenshots below | Compact popup width is 280dp, capped by the visible window; action buttons retain 48dp touch targets. Icon packs are found through the usual pack intent actions (`<queries>`); not tested with real third-party packs. |
| G16 | Widgets: add, resize, overlap, options; long-press home menu | Implemented – picker layout and resize input smoke verified; remaining workflows unverified | `WidgetBindFlowTest`, `WidgetSizingTest`, `WidgetPickerTest`, `WidgetResizeFrameTest`, `WidgetResizeFrameDeviceTest`, `RoomWorkspaceRepositoryV2Test`, `HomeMenuAndCellLayoutTest` | Adaptive picker grid keeps narrow previews side by side and wider previews across columns; fewer columns at larger fonts/narrower widths. See FR-007 for binding/hosting gaps. |
| G17 | Lock home-screen layout | Implemented – unverified | `LayoutLockViewModelTest`, `WorkspaceControllerSettingsTest`, `FolderHomeTest` | The lock covers moves, adds, removals, folder edits and widget changes; app menu entries such as App info still work. |
| G18 | Backup/restore to a local file; share the backup | Implemented – unverified | `BackupCodecTest`, `BackupServiceTest`, `BackupViewModelTest`, `BackupRestoreRoomTest` | One versioned JSON file through the Storage Access Framework; sharing uses the system share sheet (no Google Drive). Widgets restore as "needs rebind" placeholders. Restore also starts from the first onboarding step; restored settings that depend on this phone (Discover without Google app or bridge, missing icon pack, unreadable dock image) are kept and explained (`RestoreChecksTest`, `AndroidRestoreEnvironmentTest`). |
| G19 | Quick start and reset to default | Implemented – unverified | `BackupViewModelTest` (reset to default with undo), `OnboardingAndLabsTest` | Re-running onboarding from "Quick start" has no dedicated test. |
| G20 | Onboarding and default-launcher request | Implemented – unverified | `OnboardingAndLabsTest` | The role request was not exercised on a device. |
| G21 | Separate settings entry; settings from long-press home | Implemented – unverified | `HomeMenuAndCellLayoutTest`, `SettingsTest` | — |
| G22 | Folders: create by drag, rename, reorder, open/close, in dock | Implemented – unverified | `RoomWorkspaceRepositoryFolderTest`, `FolderLogicTest`, `FolderHomeTest` | See FR-006. |

Cross-feature integration on `feat/nova-guide`: folder tap/swipe actions use the gestures executor and its action picker (hosted over HOME through `ComposeDialogHost`); the long-press menu turns into a drag on move without breaking icon or folder swipes; the home menu, app menu, widget options and folder popup close each other and on HOME. These interactions are covered by Robolectric tests only, which do not reproduce real touch timing.

## Build and CI evidence

| Date | Check | Result | Where |
|---|---|---|---|
| 2026-10-10 | `assembleDebug :core:model:test testDebugUnitTest lintDebug` on the bootstrap commit | Build and lint pass; both test tasks reported NO-SOURCE (no tests existed yet) | Developer machine (macOS, Android Studio JBR) |
| 2026-10-10 | `scripts/check-privacy.sh` on the bootstrap commit | Pass: no forbidden permissions, no listed SDKs in 113 resolved runtime modules | Developer machine |
| 2026-10-10 | `:core:model:test testDebugUnitTest lintDebug assembleDebug` at 5a0bece | 105 unit tests pass (JVM + Robolectric SDK 36); lint 0 errors | Developer machine |
| 2026-10-10 | `scripts/check-privacy.sh` at 5a0bece | Pass: only AndroidX's signature-level receiver permission; no ad/analytics SDKs | Developer machine |
| 2026-10-10 | `:core:model:test testDebugUnitTest assembleDebug lintDebug compileDebugAndroidTestKotlin` on `feat/nova-guide` after merging all Nova-guide branches | 90 `:core:model` and 314 app unit tests pass (JVM + Robolectric SDK 36); lint passes; instrumentation sources compile (not run) | Developer machine |
| 2026-10-10 | `scripts/check-privacy.sh` on `feat/nova-guide` | Pass: 5 forbidden permissions absent; 49 SDK rules against 113 runtime modules | Developer machine |
| — | GitHub Actions `Android CI` | Not yet run on GitHub (branch not pushed) | [workflow](../.github/workflows/android.yml) |

2026-10-10 notification drag change on `fix/notification-pull-down` (local working tree):
`assembleDebug :core:model:test testDebugUnitTest lintDebug` passed (90 core JVM and 337 app
unit/Robolectric tests, zero failures); `scripts/check-privacy.sh` passed (113 runtime modules).
The nine `NotificationDragTest` cases use a fake SystemUI boundary; physical-device evidence is
recorded below. No new permission, component or dependency was added for this change.

Passing build checks are not feature verification.

2026-10-10 Discover feature worktree on the developer's macOS machine (Android Studio JBR):
`:core:model:test testDebugUnitTest lintDebug assembleDebug` passed (90 JVM and 328 app
unit/Robolectric tests, zero failures); `scripts/check-privacy.sh` passed (113 runtime modules,
no forbidden permissions/tracking SDKs). Changed Markdown relative links and `git diff --check`
passed. Connected-device inspection found no AIDLBridge, so live feed instrumentation was not run;
no bridge was installed and no device layout or default HOME was changed.



## Known platform limitations

- **No Recents/Quickstep integration.** A normal, non-root launcher cannot replace the system Recents screen or the stock gesture-navigation animations; Chikku does not promise them. See [ANDROID_APIS.md](ANDROID_APIS.md).
- **Private Space is not surfaced in M0.** The manifest does not declare `ACCESS_HIDDEN_PROFILES`, so Android 15+ Private Space apps are not shown. Adding it later requires a separate container with locked-state semantics ([PRIVACY_SECURITY.md](PRIVACY_SECURITY.md)).
- **MIUI test-harness restrictions.** On MIUI, adb input injection needs Developer options → "USB debugging (Security settings)". Instrumentation that starts activities needs the per-app "Display pop-up windows while running in the background" permission, which is lost if Gradle's connected task uninstalls the app. Run with `adb install -r` + `adb shell am instrument` instead. These restrictions apply to the tooling, not to Chikku.
- **OEM gesture coverage is limited.** The notification drag handoff is checked separately below;
  general home-gesture, back and app-open animation coverage across OEM builds is still required.
- **targetSdk stays at 36** until Android 17/API 37 widget memory rules are validated.
- **Cosmetic hiding is not security.** Hidden apps (G14) are a visual convenience only: they leave the drawer and search, but remain installed and launchable.

## Device/emulator verification log

Record one row per verification session. Do not record a pass for a check that was not run.

| Date | Build (commit / versionName) | Device or emulator | Android version / API | OEM build | Requirements checked | Result | Notes |
|---|---|---|---|---|---|---|---|
| 2026-10-10 | f498309 / 0.1.0-dev-debug | Xiaomi Redmi 9 (M2004J19PI, "shiva"), physical, 3-button navigation | Android 11 / API 30 | MIUI 12.5 (RP1A.200720.011) | FR-001 (candidate only), FR-002, FR-003, FR-004, FR-005, FR-008 | 19/19 instrumentation tests pass; manual checks pass after one fix | Drawer→home drop bug found and fixed (f498309). Follow-up the same day: swipe-up from dock/"All apps" and swipe-down-to-close added and checked with `adb shell input swipe` (8 gesture cases). Cold start 1575 ms on first install, 604 ms after force-stop (`am start -W`, single sample, not a benchmark). Dark user wallpaper, so wallpaper rendering not clearly confirmed. Chikku was not set as the default HOME during this session. |
| 2026-10-10 | `fix/notification-pull-down` local working tree / 0.1.0-dev-debug | Samsung Galaxy S24 Ultra (SM-S928B), physical | Android 16 / API 36 | BP4A.251205.006.S928BXXS6DZI1 | G10 / FR-014: notification swipe timing only | ADB touch smoke: notification shade focused before UP in two deliberate drags; a short slow drag and a horizontal swipe leave the shade closed | Debug APK updated with `adb install -r`, retaining settings/layout. Existing swipe-down mapping was Notifications. System window focus checked without collecting notification content. Quick settings, accessibility fallback, other OEMs, full instrumentation suite and frame-pacing benchmarks were not run in this session. Default HOME selection was not changed. |
| 2026-10-10 | `fix/widget-all-sides-resize` local working tree / 0.1.0-dev-debug | Samsung Galaxy S24 Ultra (SM-S928B), physical | Android 16 / API 36 | BP4A.251205.006.S928BXXS6DZI1 | FR-007 / G16: resize overlay input only | 2/2 targeted instrumentation tests pass: all 8 edge/corner handles in LTR and RTL | Real input injected into a test resize frame hosted over HOME; no third-party widget bound or persisted layout changed. APK updated with `adb install -r`. Provider min/max, grid edges, cancellation/rejection and pending edits covered by 12 Robolectric resize-frame tests; atomic position/span and undo covered by Room tests. Full local checks pass: 351 app unit tests, 90 core tests, debug APK build, Android lint and privacy gate. Third-party widget add/bind/configure/resize/restart, TalkBack and other devices remain unverified. Default HOME selection unchanged. |
| 2026-10-10 | `fix/compact-app-shortcuts` local working tree / 0.1.0-dev-debug | Samsung Galaxy S24 Ultra (SM-S928B), physical | Android 16 / API 36 | BP4A.251205.006 | G15: compact app-menu sizing only | ADB long-press and screenshot smoke: four Instagram shortcut rows and all five launcher action buttons fit in the narrower popup; a dock popup is placed above its icon | APK updated with `adb install -r`. Screenshot evidence in ignored `build/verification/compact-app-shortcuts/after.png`. Debug build, 90 core and 353 app unit tests (cached results), lint and privacy gate pass; build/test/lint verified in an isolated source snapshot to avoid concurrent Gradle output collisions. No app action, layout edit or HOME-role change performed. Large fonts, TalkBack, narrow windows, detailed menu style, other devices and full instrumentation remain unverified. |

### Compact widget picker — 2026-10-10

On `fix/compact-widget-picker`, local working tree / `0.1.0-dev-debug`: the debug APK was
updated with `adb install -r` on a physical Samsung Galaxy S24 Ultra (SM-S928B), Android 16 /
API 36, build BP4A.251205.006.S928BXXS6DZI1. ADB long press opened the home menu; tapping
Widgets and scrolling showed the real Calendar previews. Screenshot inspection confirms
Countdown (2 × 2) and Month (4 × 5) share a row, with the wider Today preview below. Evidence
is in ignored `build/verification/compact-widget-picker/after.png`. No widget was placed,
bound or configured; the existing layout and default HOME selection were preserved.
Physical drag/drop, TalkBack, large fonts, RTL, rotation and other devices remain unverified.

Local validation in an isolated source snapshot (macOS, Android Studio JBR, two Gradle workers):
`assembleDebug :core:model:test testDebugUnitTest lintDebug` passed; 90 core and 362 app
unit/Robolectric tests passed, including all four `WidgetPickerTest` cases. The privacy gate
passed against 113 runtime modules. The picker source and tests match the working tree.
Initial shared-checkout attempts encountered incremental-cache/class-output interference and a
lint detector crash; an early snapshot captured an unrelated, partially edited search feature.
The final snapshot includes that feature's current correction and passes lint without disabling
checks. These build results are separate from the physical picker screenshot check above.

### Embedded Discover and the bridge app — 2026-10-12

On `feat/discover-bridge`, local macOS / Android Studio JBR: `assembleDebug`, Discover unit tests
(17, including bridge resolution and binding), `:discover-bridge:lintDebug` and
`scripts/check-privacy.sh` passed. The full `testDebugUnitTest` run had one failure,
`HomeCardsIntegrationTest.homePressDismissesCardsAndRestoresHomeAccessibility`, which also fails on
the base commit without these changes. `:app:lintVitalAnalyzeRelease` crashed once in lint itself on
the concurrently edited `AppMenuController.kt` and was skipped for the device builds.

Physical workflow (ADB input, logcat and screenshot inspection): Samsung Galaxy S24 Ultra
(SM-S928B), Android 16, Google app `17.64.15.ve.arm64`. Debug build: Google's window attached and
live cards showed over Chikku HOME; Back returned to HOME. Release Chikku (R8, not debuggable) signed
with a throwaway key: Google refused the bind ("Only system and debuggable apps are allowed to
connect") and the swipe opened the Google app. Release Chikku plus release bridge, same throwaway key:
cards showed over Chikku, Back closed them, the feed reattached after leaving and returning to HOME,
and with the bridge disabled the swipe opened the Google app. The test-key builds were uninstalled and
the debug build restored as HOME afterwards. Screenshots were not committed because they show a
personalized feed. Not verified: rotation, Google process death, TalkBack, offline, profiles, other
devices or Google app versions.

Follow-up the same day: bridge failures are explained instead of silently opening the Google app
(battery-restricted, not starting, wrong key, disabled, refused, or Google serving another client),
with automatic reconnects and one re-registration with Google after 5 s of silence (see ADR-0004).
Local checks: `assembleDebug`, `:core:model:test`, `:app:testDebugUnitTest` (668 tests; only the
pre-existing `HomeCardsIntegrationTest` failure), `lintDebug` for both apps (0 errors) and
`scripts/check-privacy.sh` passed; 25 Discover tests cover these paths with fakes. Device evidence so
far: Samsung froze the unbound bridge but not while Chikku was bound to it, and Nova's running
companion caused `ensureValidCaller` rejections that stopped once Nova was stopped. The new dialog,
status row, reconnects and re-registration have not been run on a device. Device testing paused
after a real incoming call arrived during scripted input at 09:49; scripted input must not run while
the phone may receive calls.

### Discover without a companion — 2026-10-10

On `feat/direct-google-discover`, local macOS / Android Studio JBR: debug build, 90 pure Kotlin tests,
358 app unit tests, `lintDebug` and `scripts/check-privacy.sh` passed. Focused checks cover direct resolution,
Binder lifecycle, app-only fallback, failed bind/launch and first-page swipe/RTL. `lintDebug` was
initially interrupted by an `ExperimentalDetector` crash while analyzing the concurrently edited
`WidgetPicker.kt` (`Unexpected owner function: null`); the final rerun passed. An earlier
full-test attempt ended with a worker EOF; a rerun with two Gradle workers passed.

Physical workflow (ADB touch/key injection plus screenshot inspection): Samsung Galaxy S24 Ultra
(SM-S928B), Android 16 / API 36, Google app `17.64.15.ve.arm64`, Chikku debug build. No AIDLBridge
was installed (`pm path amirz.aidlbridge` returned no package). The existing Google app was used:
first-page outward swipe → Google's `SearchActivity` with visible Discover cards → Back returned to
Chikku HOME. Switch off → outward swipe stayed in Chikku. This verifies the user-approved
**app-opening fallback**, not the embedded overlay; it does not establish release-build overlay
acceptance, TalkBack, locked-profile isolation, offline feed behavior or performance. Only Chikku
was updated; no companion or instrumentation APK was installed and default HOME/layout were not
changed. Screenshots were inspected locally and are not committed because they contain a
personalized feed. The feed preference was on before this check and is restored to on afterwards.

### Dock web search and suggestions — 2026-10-10

Implemented: Dock → Search bar below dock, shared engine selection, provider-labelled home bars and
optional live Google/DuckDuckGo suggestions. Search settings edit the same fields. Suggestions are
off by default and disclose typed-query transmission. Other engines retain explicit browser search;
unsupported autocomplete never falls back to a different provider. No history or local app/contact/
profile results are sent. See [ADR-0005](decisions/0005-optional-web-search-suggestions.md).

Local macOS / Android Studio JBR checks passed: debug APK, 91 pure Kotlin tests, 370 app unit/Robolectric
tests, Android lint and privacy gate (113 runtime modules, no forbidden SDKs). Focused checks cover
URL encoding, settings default/roundtrip, bar visibility/provider labels, bounded JSON/HTTP responses,
redirect rejection, IO failure, query/provider/disable cancellation and independent local search.

On Samsung Galaxy S24 Ultra (SM-S928B), Android 16 / API 36, the debug and instrumentation APKs were
updated with `adb install -r`, preserving app data. `WebSuggestionDeviceTest` with runner argument
`liveWebSuggestions=true` passed (one test, both providers): HTTPS requests for the non-personal
query `weather` returned non-empty suggestions through the production Android adapter. This test
is skipped by default in offline CI and changes no settings or layout. The same two endpoints
returned HTTP 200 from the development host. These are network/adapter checks, not UI screenshots.
After the user unlocked the phone, ADB touch/key injection plus local screenshot inspection verified
Dock → enable below-dock bar and suggestions → HOME provider label → type `weather` → live suggestion
list → tap `weather forecast` → matching browser results, separately for DuckDuckGo and Google.
Changing the provider in Dock updated the bar and search handoff. The browser's location prompt was
dismissed without granting access. Screenshots are in ignored `build/verification/dock-search/`;
browser screenshots contain personalized provider content and are not committed. Original bar
visibility/position, engine, web row and suggestion opt-in were restored while retaining other
current preferences. Default HOME and workspace placements were preserved. TalkBack/large-font/RTL,
airplane-mode UI and a broader OEM matrix remain unverified.

### Icon pack settings list — 2026-10-11

On `fix/icon-pack-picker-list`, Icon pack under Look & feel always opens a scrollable single-choice
dialog. System icons and all detected installed packs remain available; the settings row shows the
selected pack. This removes the short inline button group that looked limited to two choices.
Discovery, persisted selections, missing-pack handling and per-app overrides are unchanged.

Local macOS / Android Studio JBR validation in an isolated copy of the committed source plus the
two changed settings files: `assembleDebug :core:model:test testDebugUnitTest lintDebug` passed
(91 core and 370 app unit/Robolectric tests); the privacy gate passed against 113 runtime modules.
The initial shared-workspace run hit a lint detector crash while another file was being edited;
the isolated run passed with all checks enabled. The APK is in ignored
`build/verification/icon-pack-picker/app-debug.apk`. It was not installed on a device in this session;
physical picker interaction, multiple real packs, TalkBack and large fonts remain unverified.

Installation follow-up (2026-10-11, user requested): the saved APK was installed on the connected
Samsung Galaxy S24 Ultra (SM-S928B) with `adb install -r` (Success), updating the debug app without
uninstalling or clearing its data. Package inspection confirms `0.1.0-dev-debug`. No settings,
workspace or default HOME changes were requested; physical picker interaction remains unverified.

### Hidden-app selection grid — 2026-10-11

The hidden-app picker now reuses drawer icon rendering and the icon provider, with checkboxes,
a hidden-app count and tap/drag alphabet navigation. It preserves component/profile keys and
keeps selected apps in place for multi-selection and deselection. Drawer icon scale, icon-pack
rendering and custom names are retained; labels remain visible for identification. The picker
uses a vertical grid and shows its alphabet rail independently of drawer style/rail preferences.
Touch and hold offers Open, also exposed as an accessibility action. Hiding remains cosmetic.

Local verification on macOS / Android Studio JBR, in an isolated source snapshot with two Gradle
workers: `assembleDebug :core:model:test testDebugUnitTest lintDebug` passed (94 core and 383 app
unit/Robolectric tests, including four `HiddenAppsGridViewTest` cases). The privacy gate passed
against 113 runtime modules. The changed implementation and test files match the verified snapshot;
the APK and logs are saved in ignored `build/verification/hidden-apps-grid/`.
The first shared-checkout test run encountered output EOF errors; a rerun passed, and the final
isolated verification avoids concurrent build-output interference.

A preliminary APK was updated with `adb install --no-streaming -r` on the connected Samsung Galaxy
S24 Ultra (SM-S928B), Android 16; only opening App drawer settings was observed. Other work was
using the phone, so hidden-picker selection/persistence, final APK device validation, TalkBack,
large-font settings layout, icon-pack appearance and physical RTL remain unverified. No passing
physical hidden-app workflow is claimed.


### Dock settings reference and workflow — 2026-10-11

Direct reference: Nova Launcher 8.9.4 (`versionName=89400 (8.9.4)`) on Samsung Galaxy S24 Ultra
(SM-S928B), Android 16/API 36. Captured Dock, expanded Label/background and Advanced screens;
local reference and Chikku screenshots are in ignored `build/verification/dock-settings/`.
No Nova code, icons or wallpaper are bundled. See [dock settings](DOCK_SETTINGS.md) for the
implemented controls and compatibility choices.

Local Android Studio JBR validation of the isolated dock source: `assembleDebug`,
`assembleDebugAndroidTest`, `:core:model:test`, `testDebugUnitTest` and `lintDebug` passed;
94 core and 379 app unit/Robolectric tests passed. The privacy gate passed against 113 runtime
modules. Checks cover old settings/backup defaults, all new preference roundtrips, bounds,
desktop-matched sizing, independent padding/labels, page wrapping/reset/restore, delayed preference
writes, round/cut corner pixels, background disable/re-enable, overlay reparenting, recovery,
hidden-overlay recreation and successful-versus-failed launch notifications.

Physical `DockSettingsDeviceTest`: **1/1 passed** on the Galaxy S24 Ultra after installing the
app/test APKs with `adb install -r`. Real swipes page 2 → page 1 → page 3 wrap, remembered page
survives activity recreation, overlay uses the workspace host, swipe opens the drawer, launching
Chikku's own Settings closes the overlay, Back returns and a mapped double tap reopens it.
The test restores the original dock and edited gesture preferences. Default HOME was Chikku
before and after; the test did not request a role change or edit placements. Manual screenshot
inspection checked the dock page and expanded label controls at the device's current font scale.
The initial device test assumed a visible All apps button; the existing swipe-only preference
hid it. The corrected test uses the swipe workflow. Initial shared-checkout build attempts also
hit overlapping Gradle output and an unrelated in-progress hidden-app test; final checks use an
isolated dock branch without disabling tests or lint.

Remaining: physical process-death/reboot restoration, dock drag/drop with wrapping/overlay,
TalkBack and RTL workflows, large fonts/narrow windows, visual navigation-bar background checks
and additional OEM/OS coverage. Activity recreation, unit tests and screenshots are distinct
evidence and do not prove those checks. G03/FR-013 is partially device-verified; M1 stays incomplete.

### Home search widget appearance — 2026-10-11

On `feat/search-widget-style`, both home search positions use a solid navy capsule based on the
user-provided phone screenshot. An original app-search glyph sits on the left; the right displays
an original typographic mark for the selected Google, DuckDuckGo, Bing, Brave, Startpage or Ecosia
engine. The middle remains blank. Disabling web search hides the provider mark. The bar remains
one accessible action with the engine in its content description. No provider/Nova assets,
dependencies, permissions or exported components were added.

Local macOS / Android Studio JBR verification in an isolated source snapshot:
`assembleDebug :core:model:test testDebugUnitTest lintDebug` passed (94 core and 383 app tests,
zero failures/errors/skips). The privacy gate passed against 113 runtime modules. The snapshot
avoids concurrent build-output replacement in the shared checkout.

The saved APK was installed with `adb install --no-streaming -r` on Samsung Galaxy S24 Ultra
(SM-S928B), Android 16 / API 36. Below-dock appearance was visually inspected with DuckDuckGo
and Google selected through Search settings; tapping the bar opened universal search and its
keyboard. DuckDuckGo was restored afterward. Reference, both provider screenshots, the opened
search view and the verified APK are saved in ignored `build/verification/search-widget-style/`.
The existing autocomplete/browser workflow evidence remains in the prior web-search section;
it was not repeated for this cosmetic change. Physical top placement, TalkBack, large fonts,
RTL and the other four provider marks remain unverified.

## Nova article additions — 2026-10-11

Local implementation on `feat/nova-article-features`; see the exhaustive
[article-to-control audit](NOVA_ARTICLE_PARITY.md). Added offline micro-results, opt-in local app
suggestions, widget overlap/corners, dock background images and compact settings lists. These
changes preserve the pre-existing staged foundation and do not settle the provisional ADRs.

Android Studio JBR verification: `:core:model:test assembleDebug testDebugUnitTest lintDebug
assembleDebugAndroidTest` passed; **100 core + 393 app tests**, zero failures/errors/skips.
The privacy gate passed (four forbidden permissions absent; 49 SDK rules checked against 113
runtime dependencies). No new dependencies, permissions or exported components were introduced.

Physical `MicroResultsDeviceTest`: **2/2 passed** on Xiaomi Redmi 9 (M2004J19PI), Android 11/API 30,
MIUI 12.5.7, with debug version `0.1.0-dev-debug`, installed using `adb install -r`. Final run took
12.317 seconds. Checks cover conversion results, phone/flight result updates, activity recreation,
settings navigation and visible widget overlap/corner controls. Touched search preferences are
restored; default HOME and placements are not changed. The initial locked-screen attempt failed;
after the user unlocked the phone both tests passed, then passed again on the final build.

Screenshots from the first successful unlocked run were manually inspected for the flat settings
list, search conversion and widget controls. They are in ignored
`app/build/outputs/verification/nova-articles/`, alongside final build/privacy/device logs. The
phone disconnected after the final run, before screenshots could be pulled again; these local
screenshots precede the final neutral section-band color and pinned-toolbar refinement. The final
run verified behavior, not a new visual capture. No emulator or performance benchmark was run.

Third-party service account integrations are unavailable beyond public app shortcuts. Real
photo-provider loss/selection, actual third-party widget hosting, external handoffs, extended-use
learning, default-HOME shortcut integration, TalkBack, large fonts, RTL and broader OEM/OS
acceptance remain unverified. Etherscan's live page returned HTTP 403 to the browsing service;
its constructed handoff is tested locally, without live availability evidence.


## Nova phone settings — 2026-10-11

Direct inspection of Nova `89400 (8.9.4)` with Prime on Samsung Galaxy S24 Ultra SM-S928B,
Android 16/API 36, informed the [158-row phone settings inventory](NOVA_PHONE_SETTINGS.md).
This pass adds 63 saved preference fields, independent labels, list drawer/navigation controls,
folder presentation/sorting, extra gestures, search appearance/local folder results, badge
placement and menu actions, plus offline settings search. It preserves earlier article work on
`feat/nova-phone-settings`; no new permission, dependency, exported component or Room schema.

Final JBR Gradle build, model/app unit tests, lint and test-APK assembly passed: **107 core +
466 app tests**, zero failures/errors/skips. Lint has warnings. Privacy gate passed: four forbidden
permissions absent, 49 SDK rules checked against 113 runtime modules.

Installed with `adb install --no-streaming -r`; installed APK hash matches the verified local APK.
Physical settings suite **3/3** passed (29.913 s), HOME workflows **3/3** and narrow startup
StrictMode check **1/1** passed (14.84 s). Test fixtures and first-attempt selector/configuration
failures are detailed in the inventory. Search-mode/folder samples were synthetic; the drawer and
settings interactions were real. Original preferences/default HOME were restored/preserved, and
before/after comparison confirms identical workspace database rows and original setting fields.
Ignored logs, screenshots, state check and APK live under `build/verification/nova-phone-settings/`.

Real install callbacks/placement, physical extra gestures, profile-lock transitions, widget-provider
loss, full device archive restore, TalkBack, large fonts, physical RTL, process death and benchmarks
remain unverified. See Open/Partial inventory rows for remaining feature gaps. No architecture ADR
acceptance, release publication or merge is implied.

## Horizontal folder paging and local cards — 2026-10-11

The user-requested [continuation](FOLDER_PAGING_CARDS.md) adds horizontal folder pages with
row/grid controls, swipes, RTL and accessible navigation; plus named local cards with six actions,
personal app/shortcut or existing-widget references, weekday/time/charging/Wi-Fi conditions,
durable edits, reorder and delete/undo. Styling uses Chikku's existing theme and rounded panels.
Storage extends settings/backup version 1 without a Room migration. The one new normal permission
is `ACCESS_NETWORK_STATE` for local optional Wi-Fi predicates; no new dependency or exported
component is added. Card actions run only when tapped. Embedded card widgets and Bluetooth,
weather/media/service integrations remain open.

Final Android Studio JBR build, model/app unit tests, lint and test-APK assembly passed from an
isolated source snapshot: **130 core + 536 app tests**, zero failures/errors/skips. Lint has warnings.
Privacy gate passed against 113 modules, four forbidden permissions and 49 SDK rules. The snapshot
includes and preserves the concurrent search/folder work below; no production source differed at
final comparison. Source hashes and the exact verified APK are saved separately from shared build
outputs in ignored `build/verification/folder-paging-cards/`.

Android 16/API 36 `sdk_gphone64_arm64`, serial `emulator-5582`: **8/8 instrumentation tests passed**
in 66.233 seconds: two folder workflows, one card-editor workflow, one Home/cards workflow, three
existing Home workflows and one narrow startup StrictMode check. Tests restore their edited
settings and never select default HOME. Synthetic folder/editor/panel screenshots were visually
inspected; installed emulator APK SHA-256 matches the saved artifact. Initial cold-stream failures,
viewport/setup fixture corrections and inset regressions are documented in the continuation.

The Samsung phone disconnected before the new build could be installed or tested. No new physical
acceptance, physical after-state comparison or benchmark is claimed. Real condition transitions,
third-party shortcut/widget behavior, profile locking, full device restore, reboot/process death,
TalkBack and broader OEM coverage remain unverified. Existing release gates and provisional ADRs
remain unchanged.

## Search sizing, drag and folder editing — 2026-10-11

Implemented [search/folder usability fixes](SEARCH_FOLDER_CUSTOMIZATION.md): persistent bar width/height/independent padding and reset; HOME-window search with continuous app-result drag; folder long-press Edit/Remove, grouped scrollable editor/settings, app icon covers and explicit Open folder gestures. Earlier concurrent folder paging/cards changes were preserved. No new permissions, dependencies, exported components or Room migration were introduced by these fixes.

Host validation on macOS with Android Studio's bundled JBR:

- `assembleDebug`, `assembleDebugAndroidTest`: pass; debug APK at `app/build/outputs/apk/debug/app-debug.apk`.
- `:core:model:test`: **130 tests, 0 failures**. `testDebugUnitTest`: **534 tests, 0 failures**.
- `lintDebug`: pass (**0 errors, 44 warnings, 1 hint**); warnings remain separate from errors.
- `scripts/check-privacy.sh`: pass; four forbidden permissions absent, 49 SDK rules checked against 113 resolved modules.
- `git diff --check` and relative Markdown link-target verification: pass.

Named new JVM/Robolectric evidence includes `SearchSizingFolderCoverCodecTest` (storage/backup defaults, ranges, exact component/profile cover and contextual action encoding), `SearchBarSizingTest` (both positions, responsive width/height, padding, RTL, bounds/reset), `SearchDragSourceTest` (continuous touch source and visibility), `SearchDropVisibilityTest` (fresh hidden/profile checks before the real HOME drop handler), `FolderLongPressIntegrationTest` (actual workspace long press, editor Cancel, locked layout), and `FolderEditRefreshTest` (Save/Cancel, changed-lock rejection, membership/availability fallback and full cover drawable). Existing workspace, Room, search, settings, folder and paging regression suites were also included in the full unit run.

Android instrumentation on **sdk_gphone64_arm64 emulator, Android 16 / API 36, serial emulator-5580**, debug build, installed with `adb install -r`:

- `SearchDragDeviceTest`: pass. Injected DOWN → hold → MOVE → UP from a search app row persisted the correct component/profile and new desktop page. Compact results → activity stop while held → cancellation → resume left layout unchanged. Locked layout rejected a held drag without launching; a subsequent tap opened the correct app.
- `FolderEditorDeviceTest.coverChoiceAndExplicitFolderGestureSaveFromScrollableEditor`: pass. A synthetic folder editor's real cover/action selectors saved First app icon and an explicit Open folder swipe. This checks UI callbacks, not a real user's stored folder or physical-profile locking.
- `FolderPagingDeviceTest`: **2 tests pass**; settings direction/row preferences/recreation and synthetic page buttons/swipes/RTL remained functional after regrouping controls.

The four-method combined instrumentation run passed. A subsequent `FolderEditorDeviceTest` run passed both methods, adding `groupedFolderSettingsShowStylePreviewAndEditGuidance` and screenshot inspection of the settings preview/group headers; five distinct device-test methods passed overall. The test fixture restored original settings/layout/customization in `finally`; no default-HOME selection was changed. Screenshots under `app/build/reports/usability/` are local, ignored build artifacts; the folder editor and grouped settings were visually inspected for spacing, readable grouping, the style preview and unclipped Save/Cancel controls. No physical-device, TalkBack, performance, reboot, broader OEM or actual work/private-profile acceptance is claimed. Search-to-dock/folder use the existing drop handlers; direct touch evidence here is desktop placement.

## Below-dock search spacing — 2026-10-11

The connected Samsung SM-S928B screenshot confirmed that inner padding did not separate the search capsule from dock labels. Added an independent persisted `widgetDockGapDp` (0–64 dp, default 12 dp), exposed as **Space above search bar** in Settings → Search. It changes the below-dock bar’s outer top margin, preserves its size/inner padding and the top bar’s existing margin, and participates in reset and settings backups.

Built a frozen source snapshot to avoid concurrent card edits during verification. Debug assembly, 130 core tests, 537 app tests (including six search sizing workflows) and the privacy gate passed. Lint reported four MissingPermission errors in the concurrent `CardBluetoothSource.kt` work; none concerns search spacing. Installed the verified snapshot on physical Samsung SM-S928B with `adb install -r`, preserving data, and visually confirmed the gap in before/after screenshots. This is physical screenshot evidence, not a phone instrumentation or full OEM acceptance result.
## Search corner-radius labels — 2026-10-11

Direct screenshot inspection of the Galaxy S24 Ultra (SM-S928B, Android 16) showed
**4576%** for a saved `search.cornerRadiusDp` of 45.7596206665 dp. Both search radius sliders
inherited a percentage formatter intended for normalized values. They now display one decimal
place in dp, with explanations distinguishing opened search from the home bar and explaining
the fully rounded limit at half the bar height. Stored values and appearance behavior are unchanged.

Verification: debug app and test APK assembly passed; the updated app was installed with replacement
on that phone. All 552 app unit tests and the privacy gate passed. Three physical instrumentation
workflows passed: `NovaPhoneSettingsDeviceTest#settingsSearchFindsSearchAppearanceAndWritesWindowAndOneRowControls`,
`NovaPhoneSettingsDeviceTest#fullScreenAndWindowSearchRenderThreeSyntheticAppResults`, and
`MicroResultsDeviceTest#conversionPhoneAndFlightActionsAppearInSearchAndSurviveRecreation`.
These cover settings writes/recreation, both search presentations and typed local results; they
restore the touched preferences and do not measure radius-slider dragging or animation performance.
Post-install screenshot inspection confirmed the same saved radius now displays **45.8 dp**
and the scope explanation is visible on the Search settings screen.
Full core tests were blocked by existing unresolved `kotlin.test` imports in `CardContentTest`;
Android lint was blocked by two existing Bluetooth `MissingPermission` errors in `CardBluetoothSource`.

## App lock — 2026-10-11

Implemented on `feat/app-lock`. Every Chikku launch path checks the lock at the platform boundary,
and unlocking uses the system `BiometricPrompt`. JVM/Robolectric tests, lint and the privacy gate
pass. No emulator or device run yet: the system prompt, Android 10 credential fallback, lockout and
screen-off relock are unverified. [Threat model, limits and validation](APP_LOCK.md).

## Remaining settings value audit — 2026-10-11

The [settings clarity audit](SETTINGS_CLARITY_AUDIT.md) records corrected units, saved custom sizes
and spacing scope. This follow-up preserves saved grid sizes above the usual picker limit instead
of displaying 8, hides inactive drawer counts, corrects the active extended-grid description,
disables the unfinished faster-cache control, displays the actual compact-search limit and makes
widget-card height labels configuration-aware. Existing unit/spacing/badge corrections in the
working tree were preserved. No settings schema, layout, permission, dependency or ADR changed.

Validation with Android Studio's bundled JBR:

- Debug app/test assembly, Android lint and the privacy gate passed (lint retains warnings).
- **24 focused JVM/Robolectric tests passed** across `GridSettingsRangeTest`, `SettingsTest`,
  `SettingsSearchIndexTest`, `SearchCustomizationTest` and `SearchViewModelTest`.
- **3 instrumentation tests passed** in `SettingsValueClarityDeviceTest` on Android 16/API 36,
  `sdk_gphone64_arm64`, serial `emulator-5580`, in 22.918 seconds. Real Compose controls rendered
  synthetic settings: radius dp versus opacity percentages, saved grid values 10/9/11, hidden
  automatic-layout counts, the saved three-app search limit and actual disabled-cache/enabled-grid
  taps. These tests write only in-memory fixture state, never stored settings or layouts.
- Folder and popup screenshots were visually inspected: 31.5 dp versus 70% opacity, and 48.0 dp.
  The first Labs test attempt inspected an enabled text descendant of a disabled toggle; the
  corrected test verifies the real touch behavior and passed with the other two workflows.
- The Samsung phone disconnected before replacement installation; no new physical verification
  or successful phone installation is claimed. The verified APK and logs/screenshots are under
  ignored `build/verification/settings-control-audit/`.

The broader app run executed **594 tests with 8 failures**: two drawer row-spacing checks, a folder
bitmap color check, three media-source checks, an embedded-widget startup fixture and a search-index
section-order assertion. These remain separate failures; the focused pass does not make the full
suite green. The earlier core-test import blocker was not reassessed by this UI-only change.
TalkBack, physical large-font/RTL, animation measurements and wider device acceptance remain open.

## Appearance settings clarity audit — 2026-10-11

The [audit](SETTINGS_CLARITY_AUDIT.md) distinguishes desktop grid edges, widget inner space, dock app padding, painted background insets and outside search spacing. Choices show their real 0/4/8/16 dp amounts. Dock exposes the same persisted search-gap preference as Search. Folder preview/window colors are independent, with legacy shared colors preserved by decode. Manual drawer-row changes disable outgoing row animations so recycled compact holders do not temporarily replace the requested slots; automatic spacing restores the original animator.

Verified against the current local source: debug assembly and Android lint pass (0 errors, 53 warnings, 1 hint); 140 core tests pass; 55 focused appearance tests pass, including settings search, native folder rendering/cache independence, manual row changes/insets and the return to automatic row spacing. Privacy gate passes (4 forbidden permissions absent, 49 SDK rules, 113 resolved modules). Whitespace and relative documentation links pass.

The final full app unit run has 594 tests, 590 passing and four failures in separate in-progress card work: three MediaCardSourceTest failures (cross-coroutine Flow emission / missing session callbacks) and one HomeEmbeddedWidgetIntegrationTest failure (an onboarding intent left in the fixture). These were left with their owning work rather than changing card behavior in this appearance pass. This is not a clean full-suite result.

No phone or emulator was connected after the interruption. Visual review of this pass did not complete (the earlier emulator capture showed only launch splash), and this version was not reinstalled on the phone. The earlier phone-verified search-gap fix is separate evidence. The built debug artifact is `app/build/outputs/apk/debug/app-debug-settings-clarity.apk`. No commit, merge or release was performed.

## Drawer row spacing — 2026-10-11

Vertical grid and list styles now offer **Automatic row spacing** and **Number of rows** in
App drawer settings. Automatic spacing defaults on for existing settings/backups; disabling it
uses the saved row count against the available list height after headers/insets. Larger icons,
multiline labels and touch targets set a minimum slot height. Horizontal pages retain their
existing rows-per-page behavior. Deferred layout updates and disabled outgoing row animations
keep recycled holders at the requested height. No dependency, permission or Room migration.

Final checks on the current local source: `assembleDebug`, `assembleDebugAndroidTest` and
`lintDebug` pass; 140 core tests pass; 49 selected app tests pass, including all 14
`DrawerCustomizationTest` cases and eight `DrawerFeatureActivityTest` cases. Settings/backup
roundtrips preserve the manual count and automatic-spacing flag. Privacy gate, whitespace and
relative documentation links pass. Two import-only corrections in the ongoing cards work enabled
compilation: JUnit imports in `CardContentTest` and an unshadowed `BadgeStyle` reference in the media
source. No card behavior was changed by those corrections.

`DrawerRowsDeviceTest` passes (one test, 5.695 seconds) on `chikku_api36`, an Android 16/API 36
ARM64 emulator, using replacement APK installation and explicit instrumentation. It operates the
actual automatic-spacing toggle and row-count slider, verifies saved preferences after settings
recreation, checks actual drawer row heights for three and four rows, and recreates HOME to verify
retention. It restores drawer/onboarding preferences and never edits placements or default HOME.
Earlier device attempts were blocked by the first-run welcome screen; the final fixture temporarily
marks onboarding complete and restores its prior value afterward. This is emulator workflow evidence,
not a physical-phone, TalkBack, large-system-font, RTL or performance claim.

The latest full app run recorded above remains **594 tests with four failures** in separate media/
embedded-widget work; the focused pass does not make that full suite green. The tested debug
artifact is `app/build/outputs/apk/debug/app-debug-drawer-rows.apk`. It was installed on the emulator,
not on a physical phone. Changes remain uncommitted with the existing concurrent work preserved;
no merge or release was performed.

## Automatic search keyboard — 2026-10-11

Search now waits for the input to be placed and the window to receive focus before requesting
field focus, then waits a Compose frame for the editor connection before showing the IME.
This is one request per opening; result/settings updates and deliberate keyboard dismissal do
not repeatedly trigger it. HOME overlays and the standalone search activity share the behavior.

Verified on the working tree, including surrounding uncommitted features: debug/app-test assembly, Android lint, 140 core tests,
41 search app unit tests and the privacy gate pass. The full app suite ran 594 tests with four
failures in the previously documented card work: three `MediaCardSourceTest` cases and
`HomeEmbeddedWidgetIntegrationTest.accessibilityCardsEntryRendersChooseWidgetAndBackClosesItsHostSurface`.
The focused pass does not make that full suite green. Whitespace and relative documentation
links pass. No permission, dependency, persisted setting or layout migration was added.

Final explicit instrumentation on `chikku_api36` (Android 16/API 36 ARM64 emulator) passed three
tests in 42.523 seconds: both working-tree `SearchKeyboardDeviceTest` workflows and the
`SearchDragDeviceTest`. The keyboard checks use actual IME insets/focused editor and key input
without tapping the field, covering top/below-dock bars, Window/Fullscreen, close/reopen,
deliberate keyboard dismissal and standalone recreation/query retention. The drag workflow
checks continuous placement, cancellation, locked-layout refusal and normal app tapping.
Touched preferences/layout are restored. No physical phone was connected; physical OEM,
hardware-keyboard and accessibility acceptance remain separate checks.

The tested APK is `app/build/outputs/apk/debug/app-debug-search-keyboard.apk`. The isolated
keyboard fix and standalone keyboard regression are committed on `fix/search-keyboard-on-open`;
the HOME overlay/presentation additions and their expanded tests remain separate working-tree
changes. No merge or release was performed.


Before committing, an isolated checkout of the exact staged tree passed `assembleDebug`,
`assembleDebugAndroidTest`, `:core:model:test` (94 tests), `testDebugUnitTest` (383 tests),
`lintDebug` and the privacy gate. All staged-tree unit tests passed; the four card failures
above belong to the broader working tree. The committed standalone `SearchKeyboardDeviceTest`
also passed on the same Android 16/API 36 ARM64 emulator (one test, 5.668 seconds).

## Priority reliability fixes — 2026-10-11

The user requested restore, media, search and profile-transition fixes first, while preserving the
surrounding uncommitted work and using a phone for device checks. Changes are on
`codex/fix-launcher-reliability`; no merge/release was performed.

- Restore/reset/undo: one shared production service/access gate and a synced atomic no-backup
  journal record the previous state before mutations and mark completion last. Pending recovery
  runs before normal repository access; failed/damaged recovery preserves evidence and blocks
  edits. Undo survives process restart. Unsuccessful settings commits no longer report Applied.
  Widget cleanup retains live IDs needed for durable workspace undo, including nonvisible items;
  recovery cannot add a rejected layout to the ordinary undo stack.
- Media: rendezvous channel-flow emissions remove the cross-coroutine failures; collectors own
  callbacks independently and clean up on cancellation. Optional source/command failures are
  contained by the card view. The embedded-widget unit fixture now marks onboarding complete,
  fixing its unrelated started-intent assertion without bypassing onboarding in production.
- Search: local matches and optional provider results have independent cancellation paths. Failed
  providers have typed fallbacks/cooperative 3-second deadlines. Even a blocked, uncooperative old
  provider cannot delay new local results. Contacts receive native cancellation immediately;
  stale query/hidden/profile results are filtered before display.
- Profiles: cache hits recheck HOME permission and profile availability before/after reads;
  unknown/quiet/locked/private targets are rejected. App/profile changes clear snapshots before
  debounced reload, and stopped streams expire replay. Launch and shortcut-menu actions recheck
  availability. Private Space support remains excluded.

Verification on the full current working tree: **150 core tests and 607 app tests, zero failures,
errors or skips**; debug APK and androidTest APK assembly pass; Android lint passes with **0 errors,
53 warnings, 1 hint**; privacy gate passes (4 forbidden permissions, 49 SDK rules, 113 runtime
modules). This supersedes the earlier four-failure full-suite result for this tested tree. New named
checks are described in [test strategy](TEST_STRATEGY.md#priority-reliability-regression-coverage-2026-10-11).
No dependency, permission, exported component, Room migration or portable backup-version change.

Physical checks: after the user reconnected the Galaxy S24 Ultra SM-S928B (Android 16/API 36),
the verified debug build was installed with replacement, without uninstalling. The final run of
`RestoreRecoveryDeviceTest`, `MediaObservationDeviceTest`, `SearchKeyboardDeviceTest` and
`AppLaunchDeviceTest` completed in 42.027 seconds: **10 passed, zero failures, 1 skipped**.
The skipped synthetic native media-session test requires existing notification-listener access;
access was not granted by the test. Restore interruption/reopen and durable undo, native media
default-off/access denial, both home search bars in both presentations/reopening, standalone
search recreation, and own-app/invalid-target launch cases passed. Default HOME is unchanged.
No emulator was used.

The first phone run also passed, but its settings XML checksum changed: the keyboard fixture
restored the decoded settings model through the current serializer. Only its pre-run checksum
was retained, so exact preservation of that first payload cannot be verified. The fixture now
also restores the original raw settings/damage strings; the complete rerun confirms the settings
file is byte-for-byte unchanged. Production sources were unchanged between the runs.
Restore fixtures use isolated storage and synthetic widget IDs. Real managed/private-profile
transitions, real process kill/power loss, third-party widget/provider acceptance and cross-device
profile remapping remain unverified.

Verified APKs are retained under ignored `build/verification/reliability/`. The recovery record is
private device-local data, separate from portable archives. Lower-priority grid/permission-copy,
widget/badge/OEM lifecycle, battery/performance and accessibility work remains open as recorded
in the [roadmap](ROADMAP.md#priority-reliability-work-2026-10-11).

## Remaining reliability work — 2026-10-11

Continues the user-requested priority pass, preserving all surrounding uncommitted work. Another chat is preparing home appearance changes in the same checkout; its branch and source edits are retained. No merge or release.

- Widget binding checks provider/profile identity again after consent and before persistence, rejects stale rebind targets, and keeps IDs referenced by saved content even if cancellation arrives after the write. Unknown storage defers cleanup; production retains old IDs for ordinary/durable Undo until an orphan sweep. Both cancellation cleanup and orphan sweeping include every retained edit snapshot and the durable restore checkpoint under the shared restore gate; malformed snapshots defer cleanup. The binding identity comparison runs inside the update transaction, closing the intervening-edit race. Metadata reads run off main; cancelled/recycled cells cannot display late results, provider loss leaves a recoverable placement, and failed size updates remain retryable.
- Badge publication requires the current connection/generation token. Clear, disconnect, style and profile changes invalidate older reads immediately. Settings distinguish access permission from a connected listener and offer Reconnect without granting access.
- Desktop/dock shrinking warns before hiding more placements. Original rows remain intact. Recovery moves fitting items in one transaction with one Undo; oversized widgets stay available for a larger grid. Failure rolls back the complete plan; recovery Undo refuses to undo an intervening unrelated edit. The confirmation body scrolls at large text sizes.
- Privacy copy describes optional autocomplete query/weather city or coordinate requests, third-party app/widget policies and shared badge/media notification access accurately. Offline core, no ads, no compulsory account and no launcher-content upload remain unchanged.

Local full-tree checks: **151 core + 619 app tests, zero failures/errors/skips**, debug and test APK assembly, lint (**0 errors, 57 warnings, 1 hint**) and privacy gate (113 runtime modules, 49 SDK rules, 4 forbidden permissions absent from both merged manifests) pass. This includes the concurrent home source work present at that build. No production dependency, permission, exported component, schema or backup-version change. A synthetic provider exists in the test APK only.

Physical checks used the Galaxy S24 Ultra SM-S928B (Android 16/API 36), without an emulator or uninstall. The final seven-class run (`RestoreRecoveryDeviceTest`, `MediaObservationDeviceTest`, `SearchKeyboardDeviceTest`, `AppLaunchDeviceTest`, `WidgetLifecycleDeviceTest`, `LayoutChangeDeviceTest`, `DefaultHomeDeviceTest`) completed in **51.528 seconds: 13 passed, zero failures, 1 skipped**. The skip requires existing notification-listener access, which was not granted by tests. Exact settings XML equality was verified across this run.

The final HOME smoke on the same verified APK also passed real Home-button navigation/drawer close and same-version replacement, restoring the original HOME role and startup-guard file. Exact saved settings, logical database rows and no-backup files were unchanged. One confirmed COLD system-HOME sample was **294 ms**. An earlier five-attempt run had one COLD launch and four already-running/unknown-state deliveries; those zero-duration deliveries are excluded from cold-start evidence. No P95, frame or battery acceptance is claimed. Private snapshots and verified APKs remain under ignored `build/verification/remaining-reliability/`.

Early concurrent phone installs and fixture failures are retained as diagnostics, not acceptance passes. The grid fixture was moved clear of system bars. The test-only widget receiver avoids Kotlin runtime helpers because Android starts it outside instrumentation; its explicit synthetic refresh runs through shell, with no user provider updates or stored permission grants. The final provider-loss/rebind fixture restores its component state and releases its separate host/IDs/database. The user authorized coordination with the concurrent appearance chat; it held phone access during accepted checks and was notified when the phone became free.

Remaining acceptance: actual version migration/reboot, real notification revocation/rebind and third-party configured widgets, managed/private-profile transitions and cross-device identity mapping, full frame/battery measurements, TalkBack, large-font/RTL and wider Android/OEM coverage. A cold-start smoke sample does not establish a performance or battery budget. Private Space remains deliberately excluded.

## Personal home reference — 2026-10-11

Optional minimal search-bar styling, reclaimed page-indicator space and an original TextClock
widget are implemented. The isolated combined snapshot passed assembly, local unit tests and lint;
the scope was expanded to the drawer and applied after the reliability run. See
[home reference evidence](HOME_REFERENCE_MATCH.md).


### Final personal home and drawer checks — 2026-10-11

The final isolated snapshot passes **154 core + 628 app tests (782 total), zero failures/errors/skips**,
debug/test APK assembly, Android lint (**0 errors, 59 warnings, 1 hint**) and the privacy gate
(113 runtime modules, 49 SDK rules, 4 forbidden permissions absent from both merged manifests).
No new dependency or permission. The framework shortcut-pin confirmation activity is exported for
Android's validated pin requests; the clock receiver is non-exported. Room v3 adds nullable shortcut
appearance fields with an additive migration; portable archive version remains compatible.

The final debug APK was installed by replacement on the Galaxy S24 Ultra SM-S928B, Android 16/API 36.
The user-authorized personal configuration is applied to home and drawer. A direct stationary touch
at the X icon center launches Firefox's external browser; the fresh provider pin points to x.com.
Drawer swipe-open, scroll-rail drag to later sections, empty Work selection and return to Apps pass.
The top tabs stay fixed when Work is empty. Google Calendar shows the actual day (11) from its own
metadata; the selected icon pack declares a calendar prefix but does not contain its daily resources.
Cold activity recreation retains the home, clock and drawer appearance. No device reboot, midnight
transition or performance benchmark was run.

The private final portable export verifies all eight original placement IDs/targets, nested folder
contents and live widget bindings remain; total placements are 24. Applied settings and per-component
customizations match after float normalization. Only four search appearance fields differ from the
pre-change settings; all other search settings, including opt-ins, remain equal. Chikku retains the
HOME role. Original placements are recoverable on additional pages. The temporary configuration
helper was removed from the isolated source and the ordinary test APK reinstalled. Production sources
and final APK are retained; private snapshots remain ignored. No merge, commit or release.

Visual differences remain in clock font metrics, Nova's disabled X opacity, and thumb position/length.
The result is a close match, not a pixel-identical claim. Accessibility/large-font/RTL, real work/private
profiles, midnight transitions, other Android versions and OEMs require separate acceptance.

## Offline typo matching — 2026-10-12

Implemented locally using the requested GPT-6.1 Sol agent, followed by an independent parent
review and validation. [Matching scope](SEARCH_FOLDER_CUSTOMIZATION.md#offline-typo-matching--2026-10-12)
extends FR-008/019 with bounded app/contact typo suggestions, joint original/custom-name ranking,
and fresh personal-contact name scans after opt-in and permission checks. No new settings,
persisted index, backup/schema change, permission, dependency or network call.

Final host verification used Homebrew JDK 26 and a frozen source copy with separate build outputs
to avoid concurrent work in the shared checkout. All 47 search source/test/resource files were
compared against that copy after verification and were identical. Results:

- `./gradlew assembleDebug :core:model:test testDebugUnitTest lintDebug --console=plain`:
  passed; **172 core tests and 741 app tests**, zero failures/errors/skips. App tests use
  Robolectric, not an Android device. The API 36 tests reject JDK 17; the separate focused core
  run also passed under JDK 17.
- `scripts/check-privacy.sh`: passed; four forbidden permissions absent from both merged
  manifests, 49 SDK rules checked against 113 runtime modules.
- Independent one-off reference calculation: **6,000 deterministic cases** (seed 9234), comparing
  the bounded matcher against a full OSA matrix and exhaustive distinct-word assignment; all
  scores matched. Both `blonkit` → Blinkit and `Akshiy Albort` → Askhay Albert passed.
- Eight `TypoContactsSearchTest` cases cover an empty Android filter with the target after
  10,000 unrelated contacts, ranking before limiting/action lookup, blank/enterprise exclusions,
  preserved provider-only matches, refreshed renamed/deleted names, scan failure, cancellation
  and permission revocation. The native result-view regression checks visible/accessibility
  suggestion labels and clearing them on rebind in both compact and full presentations.
- Diff whitespace and relative documentation link checks passed.

An earlier run in the shared checkout aborted with a test-result `EOFException`; the isolated
full run and final rerun passed. No search assertion was suppressed. The sources preserve
concurrent settings/drawer work, which is outside this change's review scope.

No APK was installed and no physical-device/emulator workflow, real contact-provider behavior,
TalkBack/large-font/RTL layout, phone latency, or battery benchmark was verified for this change.
Very large/slow contact providers remain subject to the existing three-second deadline; there is
no retained index to accelerate subsequent queries. These limits do not complete a milestone or
authorize publishing a release.


## Drawer thumb tracking — 2026-10-12

The thumb style now draws and handles input in `DrawerFastScroller` using the same scroll
range, extent and offset. `DrawerController` enables span-aware grid metrics so an incomplete
last row does not distort the range. Dragging preserves the finger's grab offset, stops an
existing fling and scrolls continuously; the popup uses `LocaleSectionIndex` on the first app
in the actual top visible row. Different letters can still share a row, and no header or filler
items are inserted. Ordinary scrolling updates the thumb. Refresh/cancellation/detachment
clear the drag, long single-section lists remain scrollable, and short/disabled/search/paged
drawers do not expose a thumb. The alphabet-rail style retains accessible letter jumps.
All new geometry and gesture state is transient; no settings, database, backup format,
permission, dependency or exported component changed.

The user requested implementation by a Sol agent with independent validation. The parent
reviewed the changes and ran:

- `:app:testDebugUnitTest`: 740 tests, zero failures/errors/skips, including all 25
  `DrawerCustomizationTest` checks (Robolectric SDK 36).
- `:core:model:test`: 172 tests, zero failures/errors/skips.
- `lintDebug`, `assembleDebug`, `:app:assembleDebugAndroidTest`, `scripts/check-privacy.sh`
  and `git diff --check`: passed. An initial combined run's core task failed with a missing
  temporary binary results file during concurrent checkout work; the separate core/lint rerun
  succeeded. That initial failure was not a test assertion failure.
- Physical Redmi 9 M2004J19PI, Android 11/API 30: `DrawerFastScrollerDeviceTest`, two tests
  passed in 1.609 seconds. The native, manually laid-out in-memory drawer uses 163 synthetic
  apps with very uneven letter populations, a mixed-letter row and an incomplete final row.
  Checks cover both rail sides, popup/visible-app agreement, both scroll limits, compact cells,
  stray taps and cancellation. APKs were installed with `adb install -r`, followed by direct
  instrumentation, without uninstalling. The shared-preferences archive checksum was identical
  before installation and after the tests. No activity, HOME role, real app inventory or saved
  placement was used by the test fixture.

These native synthetic input checks do not establish on-screen finger feel, screenshot
appearance, TalkBack, frame pacing, or compatibility with other physical OEMs. Existing
concurrent search/settings work was preserved during validation. No merge or release was performed.


## Settings live previews — 2026-10-12

[Coverage and boundaries](SETTINGS_LIVE_PREVIEWS.md): home/drawer/folder geometry, dock/search
placement, effective appearance/night colors, finite motion replay, safe gesture practice and
pinned badge/card editing samples are implemented locally. Existing saved values, layout rows,
backup codecs, permissions and dependencies are unchanged. Synthetic samples illustrate the result;
static widget-provider artwork is optional and never allocates a widget ID.

Verification on the final preview source snapshot, branch `feat/settings-live-previews`:

- `assembleDebug`, `:core:model:test`, `testDebugUnitTest`, `lintDebug` and
  `:app:assembleDebugAndroidTest` passed. Core: 176 tests; app: 768 tests; zero failures/errors/skips.
  The three new preview suites contribute 25 app tests (8 drawer/folder, 8 home/dock/search,
  9 appearance/gesture). Lint remains enabled; no detector was suppressed.
- `scripts/check-privacy.sh` passed: four forbidden permissions absent from two merged manifests,
  49 SDK rules checked against 113 resolved modules.
- Physical Redmi 9 M2004J19PI, Android 11/API 30: `LivePreviewsDeviceTest`, five executed passes,
  zero failures/skips, 24.032 seconds. APKs were replaced with `adb install -r` and instrumentation
  was invoked directly; no uninstall or HOME-role change. Fixtures use in-memory settings and
  repositories that reject writes/launches. Checks cover drawer updates during a held drag and
  pinned bounds while scrolling, dock/search gap updates before release, night-surface recoloring,
  actual safe gesture input at scoped 2× font scale, and widget-height drafts discarded before Save.
- The exact saved `chikku_settings.xml` checksum was unchanged before/after installation and
  testing. The default HOME component was unchanged. Cropped synthetic drawer/dock/night previews
  were inspected visually; local PNG evidence is gitignored under `captures/live-previews/`.

The first five-case attempt skipped while the nonsecure screen was locked and is not counted as
physical acceptance. Subsequent fixture/bridge failures exposed missing native preview descriptions
and a test-only missing ActivityResultRegistryOwner; these were fixed before the final executed run.
The prior drawer row tests were aligned with committed first-row trimming rather than reverting it.
Concurrent shortcut-learning and scroll-thumb work was preserved.

Remaining acceptance: full TalkBack/keyboard navigation, physical RTL/landscape and other OS/OEMs,
real wallpaper/provider fidelity and frame-pacing/performance measurements. Synthetic/native test
results do not establish these. No merge, publication or milestone completion is claimed.

## Dock pages live preview — 2026-10-12

Dock → Apps and pages now shows numbered sample-page buttons and demonstrates a newly available
page while the page-count slider is held. The caption explains a requested count that exceeds
the existing 12-cell dock storage row (five apps per page fits two pages; four fits three).
Caption/navigation space stays reserved at every count so the controls do not shift under a
held finger. Reopen has a labelled icon to keep the caption readable at large font sizes.
Preview navigation is temporary; no settings field, backup format, permission or dependency changed.

Verification on `fix/dock-pages-live-preview`:

- `assembleDebug`, `:core:model:test`, `testDebugUnitTest`, `lintDebug` and
  `:app:assembleDebugAndroidTest` passed. Core: 176 tests; app: 772 tests; no failures/errors/skips.
  The app count includes concurrent drawer tests, whose source changes remain separate.
- `scripts/check-privacy.sh` passed (four forbidden permissions absent; 49 SDK rules;
  113 resolved modules). Diff whitespace and the changed documentation links were checked.
- Galaxy S24 Ultra SM-S928B, Android 16/API 36: final focused `LivePreviewsDeviceTest` run
  executed three passes with status code 0, no skips/failures, in 13.471 seconds. Checks cover
  held-drag updates before save, fixed preview/slider bounds, numbered navigation, an interior
  swipe, the finite end boundary, reopen, shrinking at scoped 2× font scale and the capacity
  explanation. Repositories reject real writes/launches. Synthetic cropped previews were inspected.
- APKs replaced using `adb install -r`; the default HOME component and exact
  `chikku_settings.xml` checksum were unchanged. Local evidence is ignored under `captures/dock-pages/`.

Earlier attempts exposed an edge swipe invoking Android Back, stale accessibility-node timing,
an incorrect fixture assumption that five apps fit three pages, and off-screen controls at large
fonts; the focused fixtures were corrected. The final pre-unlock reruns skipped on the lock screen
and are not counted. A broader eight-case attempt also failed the existing card-widget slider
lookup and dock/search route navigation on this device; this is not a passing full preview suite.
TalkBack, RTL, other devices and physical frame pacing remain unverified for this change.

## Contact card and communication services (2026-10-12)

Branch `feat/contact-connect`. The search person card keeps its original one-row look with
user-configurable buttons (default Call, Message, WhatsApp; swap, add up to 4, reorder, remove) on
an app-neutral provider model ([design](CONTACT_SERVICES.md),
[ADR-0007](decisions/0007-contact-communication-services.md)). WhatsApp and Signal can be added for
**Message** only: their call entry points require `CALL_PHONE` (verified on WhatsApp 2.26.39.79's
installed manifest and Signal source at `92f5ccd`). A first version with a separate services panel
was installed on a device on 2026-10-12, judged worse than the original card, and replaced.

Executed (host, JDK from Android Studio): `:core:model:test`, `testDebugUnitTest`, `lintDebug`,
`assembleDebug`, `scripts/check-privacy.sh`. Results: 185 core tests pass; 757 app tests run
with 754 passing. The 3 failures are `DrawerCustomizationTest` row-height cases; they also fail in
this worktree with this change stashed (and passed in a fresh HEAD checkout), so they are unrelated
to this change but unexplained. Lint: 0 errors, no findings in the new files. Privacy gate passed.

Not executed: installing the build on a device or emulator, TalkBack, device font/display-size
changes, keyboard-open layout on a device, or opening WhatsApp/Signal. No call was placed, no message
sent and no contact modified.

## Search app-lock bypass — 2026-10-12

Fixed contact search → WhatsApp opening a chat without Chikku authentication. All search intent
handoffs now resolve on IO, pin the receiving component and check its package/current-profile
identity immediately before launch. Authentication retries the same deep link; cancellation and
expired/consumed grants do not launch. Chikku's selection sheet checks the chosen handler instead
of handing control to an unchecked system chooser. Stopped activities and dismissed HOME search
sessions do not launch pending actions. Current-profile handlers only; resolver/profile-forwarding
entries are excluded. No permission, dependency, exported component or persisted state was added.
Narrow manifest visibility queries support handler resolution. See [app-lock scope](APP_LOCK.md).

Validation on `fix/search-app-lock`, based on `cc2bf2a`:

| Check | Result |
|---|---|
| `assembleDebug` | Pass; APK built locally, not installed |
| `:core:model:test` | 172 tests, zero failures |
| `testDebugUnitTest` | 760 tests: 757 pass, three existing drawer-spacing failures |
| `SearchHandoffLockTest` within that run | 22 passes across Robolectric SDK 29 and 36 (11 cases per SDK) |
| `SearchPlatformTest` within that run | Seven passes, including missing-handler and store/web fallback |
| `lintDebug` | Pass (existing warnings remain) |
| `scripts/check-privacy.sh` with Android Studio JBR | Pass; no new permissions or tracking SDKs |
| `git diff --check`, changed-doc relative-link checks | Pass |
| Physical WhatsApp, biometric/PIN, TalkBack/large-font/RTL chooser checks | Not run |

The three failures are `DrawerCustomizationTest.keyboardDoesNotSqueezeRowSpacing`,
`rowCountChangesVerticalSpacingInGridAndListWithoutLosingApps` and
`rowSpacingRecalculatesForResizingAndBottomInsets`. Running that class from a separate unchanged
`cc2bf2a` source archive reproduced the same three failures (22 tests, 19 pass). Concurrent drawer
and settings-preview work in the original checkout was preserved. Tests use synthetic contacts and
handlers; passing them does not establish actual WhatsApp or OEM authentication behavior.

## Reliability recovery continuation — 2026-10-12

Implemented on `fix/reliability-acceptance` in an isolated managed worktree, preserving the ongoing
settings-preview work in the shared checkout. Three concrete recovery weaknesses were reproduced:
semantically damaged Undo could replace a valid layout; failed settings damage-copy commits could
authorize later replacement; malformed or overflowing startup flags could crash or bypass recovery.
The initial six-case regression run had five failures. A further parser review found that a present
malformed folder-members field was silently decoded as an empty list.

- Ordinary Undo validates identities, raw geometry, page/container relationships, action values
  and folder ownership/member types before replacing rows. Rejected records stay intact.
  Malformed present folder arrays reject; missing legacy arrays remain compatible. Off-grid
  placements and historical overlaps remain recoverable. Invalid retained snapshots defer
  widget-ID cleanup. No snapshot or portable backup version changes.
- Settings render defaults when damaged input cannot decode, but update/reset/import cannot
  replace it until its recovery copy commits. First-operation reset/import follows the same gate,
  and storage recovery allows a retry. The pending-copy flag is transient.
- Startup recovery saturates the crash counter, uses safe mode for malformed flags or an uncertain
  commit, and preserves damaged flag values until explicit exit. The existing small synchronous
  pre-UI startup write remains; no startup/frame/battery budget is established by these fixes.
- Native fixtures use explicit test-only database names and isolated recovery directories. Earlier
  exported schema files are bundled in the test APK only; production permissions, dependencies,
  exported components, user settings, Room columns and backup version remain unchanged.

Host verification (macOS, Homebrew JDK 26, frozen combined source):

- `assembleDebug :core:model:test testDebugUnitTest lintDebug :app:assembleDebugAndroidTest` passed:
  **176 core + 805 app tests**, zero failures/errors/skips. App tests use Robolectric API 36.
- `scripts/check-privacy.sh` passed: four forbidden permissions absent from two merged manifests,
  49 SDK rules against 113 runtime modules. No new dependency or production permission.
- `UndoSnapshotRecoveryTest` covers 11 semantic corruption variants, three malformed folder-array
  shapes, valid legacy decoding and retained off-grid/overlapping content.
  `DamagedSettingsRecoveryTest` covers failed preservation before observation, edits/reset/import
  and retry/reopen. `StartupGuardRecoveryTest` covers bad types, negative/overflowing counters,
  failed commits, preserved evidence and explicit exit.

**Native verification completed for the controlled fixtures below.** Before the phone slot,
`MigrationRecoveryDeviceTest`, `StartupRecoveryDeviceTest` and the explicitly orchestrated
`ProcessDeathRestoreDeviceTest` were recorded as compiled but unexecuted. The latter skips
without a supplied fixture UUID; passing preparation/compilation alone is not device evidence.

Phone access is coordinated with the live-preview chat. An early replacement of the app/test APKs
on the Redmi interrupted that chat's instrumentation; reliability instrumentation was not started.
Later competing installs reported by the preview chat were not performed by this pass. Device
checks were held until the phone was explicitly released; no reboot or persistent notification
access grant was performed.
Private snapshots, logs and APKs are retained in ignored `build/verification/reliability-2026-10-12/`.

Remaining acceptance: actual production-version upgrade/reboot, third-party widget consent/
configuration, notification-access transitions, real managed/private-profile behavior, cross-device
identity mapping, full accessibility/RTL/font-scale and wider Android/OEM checks, and repeatable
startup/frame/memory/battery measurements. The architecture decisions remain provisional; no
milestone completion or publication is claimed. The subsequent local main integration is recorded below.

### Accepted native run

Physical **Xiaomi Redmi 9 M2004J19PI, Android 11/API 30 (MIUI)**, pinned to the released USB
serial, with replacement installs (`adb install -r`), no uninstall or emulator. The app/test APK
SHA-256 values and private before/after snapshots are saved in the ignored verification directory.

- `MigrationRecoveryDeviceTest` (5), `StartupRecoveryDeviceTest` (1),
  `RestoreRecoveryDeviceTest` (2) and `WidgetLifecycleDeviceTest` (1): **9 executed passes,
  zero failures/skips, 9.676 seconds**. The production factory migrated native schema 1/2/3
  fixtures to v4, preserving placements/identity, folders, synthetic widget references,
  customizations/group membership, shortcut artwork, seeded metadata and old Undo through reopen.
  A future schema was refused without resetting its rows; damaged Undo retained both current rows
  and its record. Native corrupt/overflowing startup flags entered recovery.
- Four `ProcessDeathRestoreDeviceTest` pairs paused after settings, customizations, drawer groups
  and workspace writes respectively. Each preparation process was externally force-stopped after
  its durable marker appeared. **All four subsequent recovery tests passed in different PIDs**,
  before workspace observation, restoring the full checkpoint including synthetic widget IDs.
  Individual recovery durations: 0.640, 0.659, 0.618 and 0.647 seconds. The intentional terminations
  are not counted as passing preparation tests or product crashes.
- Exact saved settings bytes and **every original preference file**, logical database rows,
  no-backup files and resolved HOME selection were equal before installation and after all checks.
  Fixture stores/hosts/IDs were removed. The phone was explicitly released for the preview chat
  immediately after the preservation comparison; no later phone command is part of this pass.

The first native attempt passed the four existing/native startup cases but JUnit rejected the new
migration class because one Kotlin expression inferred a non-void return. Explicit Unit return
types fixed the fixture; production sources were unchanged before the final 9-case run. First-run
logs are retained as diagnostics and are not reported as a complete pass. All preservation checks
also passed on that attempt.

This verifies **controlled process termination across four restore-write boundaries and native
older-schema migration**, not a real older-production-APK upgrade, reboot/power loss, cross-device
profile mapping, user widget configuration or notification-access transitions. The native widget
workflow uses a test-only provider and separate host with scoped shell bind permission, not the
user's third-party widget consent path. No frame/memory/battery or full accessibility acceptance is
claimed. Remaining gates above stay open.


### Local main integration — 2026-10-12

Reviewed and merged reliability tip `30b7430` with main tip `5e56622` in the clean
`chikku-merge-main` checkout. Four append-only documentation conflicts retained both main's
contact/search acceptance sections and the reliability evidence. The unfinished preview checkout
was preserved; its subsequent preview commit is outside this merge.

Executed on this combined main source with macOS/Homebrew JDK 26:
`assembleDebug :core:model:test testDebugUnitTest lintDebug :app:assembleDebugAndroidTest` passed.
**209 core + 823 app tests (1,032 total)** passed with zero failures/errors/skips. Lint had zero
errors, 104 warnings and one hint. `scripts/check-privacy.sh` passed (four forbidden permissions,
two merged manifests, 49 SDK rules, 113 resolved modules); changed-document relative file links
and `git diff --check` passed. Ignored host logs are under
`build/verification/reliability-main-2026-10-12/` in that checkout.

No device run was repeated for this integration; the accepted native evidence above belongs to
its recorded frozen source and APKs. Existing device/release limitations remain open. This is a
local main merge, with no remote push or publication.

## Missing live previews — 2026-10-12

Branch `feat/settings-preview-gaps` completes the audited Search Contacts/Apps/Frequent, Dock
Search autocomplete, Drawer Opening/Groups, Folders behavior, Cards conditions and drawer-menu
context gaps, plus the four Discover/custom-accent/actual-page-count/Labs improvements. Scenes use
synthetic titles, icons, contacts, weather, widgets and measurements. Card sample conditions and HSV
drafts are temporary; no settings field, Room schema, backup format, permission or dependency was
added. Home’s condition evaluator and debug TextView styling are shared; other offline behavior
examples are illustrative. Editing appearance restores drawer/folder sample scenes, group labels
reserve font-scaled cell space, and source changes keep the search preview envelope fixed.

Host validation on the final source snapshot: `assembleDebug`, `:core:model:test`,
`testDebugUnitTest`, `lintDebug`, `:app:assembleDebugAndroidTest` and `scripts/check-privacy.sh`
passed. There are 176 core and 798 app unit tests (zero failures/skips), including 26 new focused
preview tests. No release, M0 acceptance or broader platform compatibility claim follows from these
checks.

Physical validation: Redmi 9 (M2004J19PI), Android 11/API 30, portrait. Across the corrected focused
runs, all 12 distinct `PreviewGapsDeviceTest` methods and all eight `LivePreviewsDeviceTest`
regressions passed. The final combined Search/regression run passed nine tests with zero skips.
Coverage includes actual control toggles/reordering, held HSV changes before Done, Cancel, safe
launch/return examples, actual/add/demo pages, 2× contact/debug samples, scoped RTL contact/Discover,
and all five card sample inputs (charging, Wi-Fi, Bluetooth, weekday and time). Saving after card
simulation returned the original card unchanged. Cropped synthetic scenes were visually inspected;
local ignored evidence is under `captures/preview-gaps/`.

The real settings-file SHA-256 and resolved HOME component remained identical to the pre-test
baseline. Fixtures reject real settings/layout/group writes, app launching and web requests; no
contacts or Bluetooth permissions were granted. Initial locked runs were skips, not passes. Early
S24 Ultra checks and interrupted Redmi runs are partial evidence only; APK replacements interrupted
runs without an app exception. The later installers could not be attributed to the coordinated chat.
The corrected fixture waits for drawn frames and enabled sample buttons and holds its scoped
configuration steady. Search stability compares the real scrolling-settings viewport and preview
top, because the reported accessibility bounds changed during scrolling while the actual settings
viewport and preview top stayed fixed. Full TalkBack use, rotation/multiwindow, broader OEM
acceptance and frame-pacing/battery benchmarks remain unverified.

After removing temporary diagnostics, the rebuilt final APK was reinstalled with `adb install -r`.
The Search viewport/ranking and large-font RTL contact checks passed again (two tests, zero skips).

### Preview integration with local main — 2026-10-12

Merged preview commit `19a0335` with reliability/main tip `e511f8f` in `chikku-merge-main`.
Append-only docs retained both evidence sets. Search conflicts preserve main’s history rows,
long-press actions and configurable contact-card renderer. Its replacement history controls now
have a dedicated Search → Suggestions page and synthetic Suggested/Recent preview; turning off
learning keeps sample rows, while turning off showing hides them. Search-index routes follow the
new page. Contact samples preserve reordered provider buttons, wrapping text and RTL digits.

On the combined source, `assembleDebug :core:model:test testDebugUnitTest lintDebug
:app:assembleDebugAndroidTest` passed: **209 core + 849 app tests (1,058 total)**, zero failures/skips.
The main privacy gate passed with the same four forbidden permissions, 49 SDK rules and 113 modules.
The other preview/device evidence above remains tied to its recorded source snapshot. The combined
main APK was installed with `adb install -r`; affected Search/contact integration checks are recorded
below. No remote push or publication is part of this local merge.

On the merged build, the large-font RTL contact test passed with the actual configurable renderer,
including button reorder/removal and an inert sample tap. The Search source/show/learning test passed
with unchanged first-control bounds, synthetic Suggested/Recent rows, and paused learning retaining
suggestions. Both executed with zero skips. The initial post-merge attempt skipped while locked;
a fixture assumption about a scrolling Apps page was corrected after Suggestions moved to its own
page. Real settings-file bytes and HOME selection remained unchanged after the accepted checks.

## Settings search scope and long-list scrolling — 2026-10-12

Reported issue: on Settings → App lock, the pinned top-bar magnifier searched all settings, while
the page's own "Search apps" field had scrolled off. An audit of the settings pages and pickers
found the same ambiguity on Hidden apps, Drawer group members and the Search page, plus long app,
widget and icon lists without a scroll indicator.

Changes: settings search is offered only on the section list; App lock's app filter is a sticky
header; `SettingsFastScroller` embeds `DrawerFastScroller` (thumb mode, drawer side/colour/popup
settings) beside Compose lazy lists and grids, with the bubble drawn in the window's root view.
It is applied to App lock, group members, gesture app pickers, card action/shortcut pickers, the
card widget provider chooser and the icon picker's app list and icon grid. Scroll metrics are
estimated from visible items, so the thumb position is approximate when row heights vary.

Host validation: `assembleDebug`, `:app:compileDebugAndroidTestKotlin`, `:core:model:test`,
`testDebugUnitTest` (978 unit tests across both modules, zero failures/skips, including four new
`SettingsFastScrollerTest` checks), `lintDebug` and `scripts/check-privacy.sh` passed.

Not verified: no device or emulator was connected, so the new `AppLockScrollDeviceTest` and the
updated `NovaPhoneSettingsDeviceTest` were compiled but not run. Finger feel, bubble placement in
dialogs and bottom sheets, TalkBack, RTL and large-font behaviour remain unverified on hardware.
Not yet addressed: the View-based home widget picker and the folder action picker fallback still
lack a fast scroller, and the icon picker's search hints are placeholders that disappear while typing.

## Focused Look & feel pages — 2026-10-12

The user reported the oversized, unrelated preview on the live Look & feel screen and requested
separate tasks. The overview now lists Accent, Icons, Motion, Screen and App menus; each task
opens a subpage with only its relevant controls and a compact pinned sample. The sample envelope
is capped at 208 dp / 32% of the available body and stays fixed through edits. Settings search
opens the owning page directly. Existing settings, backup meanings and placements are unchanged.
See [preview boundaries](SETTINGS_LIVE_PREVIEWS.md#focused-appearance-pages--2026-10-12).

Host validation after integrating current main: `assembleDebug`, `assembleDebugAndroidTest`,
`:core:model:test`, `testDebugUnitTest` (854 app + 209 core tests; 1,063 passes, zero failures/skips),
`lintDebug` and `scripts/check-privacy.sh` passed. The revised appearance radius workflows compile.
`SettingsSearchIndexTest` covers every appearance route and the separate overview.

Physical checks of the main-integrated build:

- Redmi 9 M2004J19PI, Android 11/API 30: five targeted passes, zero failures/skips, 45.076 s.
- Galaxy S24 Ultra SM-S928B, Android 16/API 36: five targeted passes, zero failures/skips, 45.388 s.

These cover five-page navigation/pinning, independent Drawer-menu action changes, custom HSV
held draft/Cancel, menu-corner held draft before release with fixed controls, and a scoped 2×
font/RTL fixture. Frozen repositories reject real writes/launches; each fixture checks that the
real serialized settings export is unchanged. S24 raw settings were additionally compared byte
for byte. Sample screenshots were inspected; private captures remain local.

Earlier S24 runs were partial (four of five passes): the fixture tapped immediately after a
Compose fling and stopped it rather than opening/toggling the target. Waiting for three stable
row-bound samples fixed both misses. The fixture keeps only its own Activity awake, without
changing the phone's timeout. An earlier locked-phone run skipped all five tests; its `OK` summary
is not passing UI evidence. The Redmi run preceded this test synchronization refinement.

Not verified: actual TalkBack use, rotation/multiwindow, physical maximum-font configuration,
subpage process recreation, broader device acceptance or gesture benchmarks. No emulator or
release acceptance is claimed. Existing radius recreation workflows were adapted and compiled,
not run as part of this focused pass.

## Production reliability verification batch — 2026-10-12

The reliability batch hardens workspace placement validation against integer overflow, preserves exact nearest-placement ranking, and adds adversarial layout/search and restore-retry regression coverage. The forced core/app unit suites passed with the new tests included. The privacy permission/SDK gate also passed; its scope is four forbidden permissions and SDK-pattern checks, not a vulnerability audit or network capture.

Native verification used the disposable API 36 emulator for recovery and settings workflows. Four controlled restore process-termination checkpoints recovered in a new process. Separate isolated physical recovery fixtures passed while preserving the installed APK, original preferences, logical database rows and HOME selection. General settings fixtures require complete onboarding and stock HOME; default-HOME role selection is a separate workflow. These checks do not establish production-version upgrade, reboot or broad OEM acceptance.

Open native findings and the current run totals are maintained in [QA results](QA_RESULTS.md), with the complete inventory in the [master matrix](QA_MASTER_MATRIX.md). F-02 records the repeated case where the Cards page visibly renders Add card but Android's accessibility hierarchy omits the control; its root cause remains unknown. Experimental production ordering changes were reverted, and the semantic-query regression remains in place. No release acceptance or milestone completion is claimed by these bounded checks.

## Horizontal scrolling controls — 2026-10-13

Implemented on `feat/horizontal-scrolling`:

- **Settings → Home screen → Pages → Horizontal scrolling**, and **Settings → App drawer →
  Horizontal scrolling** when Horizontal pages is selected. Each surface has independent swipe
  sensitivity, base settling time (100–500 ms), transition effect/strength, soft edge resistance
  or no edge feedback, looping, and indicator controls. Home retains its existing page/default-page
  and wallpaper choices; drawer opening animations remain separate from page transitions.
- Home wraps in the swipe direction by translating the original edge page into a virtual adjacent
  slot, then normalizing at rest. It never copies widgets or changes persisted placements.
  Discover still owns the start-edge swipe when enabled. Wallpaper offsets retain the existing
  system-wallpaper policy; this is not a guarantee of circular wallpaper-provider animation.
- Drawer keeps its existing sentinel-page wrapping, alphabetical ordering, group/profile filtering
  and session-only position memory. AndroidX one-page flings use the selected settling duration.
  Page transforms reset when motion is disabled. Direction gating protects taps and natural
  vertical gestures; touching an in-flight page interrupts at the rendered position.
- Home/drawer indicators support tap, slide-to-scrub, fractional progress (including wrapping),
  dots/line/none, and auto-hide. A single accessible range exposes previous/next/direct page
  actions. The indicator touch strip requests 48 dp height; native TalkBack use remains pending.
- A pinned synthetic native pager/icon/indicator sample follows held slider drafts, with an
  accessible Next page demonstration. It uses no installed apps, personal data, launches or
  wallpaper updates. The drawer sample shares page effects/settings through the workspace pager;
  it does not reproduce RecyclerView's exact fling timing, drawer tabs, or the external Discover
  overlay. Reset scrolling controls affects only this surface's motion/indicator/loop choices.
- New preferences travel in settings exports and portable backups. Missing keys retain defaults;
  numeric values are bounded. No Room migration, backup-version bump, dependency, permission,
  exported production component, copied upstream source/asset, or architecture decision changes.

Research informing this batch: [Samsung Members sorting requests](https://r2.community.samsung.com/t5/Galaxy-S/IMP-One-UI-7-App-Drawer/td-p/19920066),
[Samsung Community looping request](https://eu.community.samsung.com/t5/mobile-apps-services/one-ui-home-problem/m-p/11112559),
[community indicator wishlist](https://r2.community.samsung.com/t5/Tech-Talk/1-One-UI-5-Based-On-Android-13-Features-Wishlist/m-p/11508874),
and [Reddit gesture-conflict reports](https://www.reddit.com/r/OctopiLauncher/comments/1thytqo/swipe_left_right_too_sensitive/).
These are qualitative, version/device-dependent requests, not prevalence measurements or claims
that all current Samsung launchers expose these controls.

Verification on the development Mac with Android Studio's bundled JBR, offline Gradle caches:
`:core:model:test` passed **218** tests; `testDebugUnitTest` passed **867** app tests (zero failures,
errors or skips). `assembleDebug`, `lintDebug` and `:app:assembleDebugAndroidTest` passed.
`scripts/check-privacy.sh --offline` passed (four forbidden permissions, 49 SDK-pattern rules,
113 resolved modules). The final debug APK has the ordinary `.debug` identity. New local doc
links and `git diff --check` were checked. These JVM fixtures are not device or benchmark evidence.
The native test
APK compiled with a temporary `.pagingcheck20261013` identity. On the connected Xiaomi Redmi 9
(M2004J19PI, Android 11), the temporary app installed, but test-runner installation returned
`INSTALL_FAILED_USER_RESTRICTED` / `Install canceled by user`. Instrumentation could not find
that runner; **zero native checks ran**. The temporary app was then removed. The installed Chikku
app was not replaced, and no HOME-role selection, production settings or workspace edit was
performed. No emulator, on-screen animation capture, finger-feel assessment or benchmark is claimed.

Remaining acceptance: real home/drawer/folder/widget gesture conflicts, TalkBack/keyboard, physical
large-font/RTL and rotation/recreation, private/work transitions during motion, and frame/memory/
battery measurements. Alphabet jump for horizontal pages, page overview, global manual drawer
ordering, additional effects/haptics and multi-page flings remain follow-up work. This batch does
not complete M0/M1, accept the provisional architecture, merge to main or publish a release.

## Horizontal paging review fixes — 2026-10-13

The review of `7f79a3c` found three regressions, now fixed on
`fix/horizontal-paging-regressions`:

- Clearing the workspace during a swipe no longer settles into an empty page range. Removing the
  last view aborts pending scrolls and resets gesture state, so recovered pages cannot inherit motion.
- Incoming page-removal slides retain control of translation/alpha across layout. Completion
  returns control to page effects; navigation, reduced motion, removal and detachment clean up
  interrupted slides.
- Drawer indicator visibility is applied after updating its page state. Search, vertical style
  and a single page collapse the unused strip instead of reserving an invisible 48 dp row.

Synthetic Robolectric regressions cover release/cancellation in LTR/RTL with looping on/off,
fresh swipes after recovery, stale animated settling, layout during removal, RTL completion,
reduced-motion/navigation interruption, and the drawer's actual reclaimed body height.
The initial regression run reproduced the reviewed failures. One additional animation fixture
needed to wait for the outgoing fade and then apply the layout removal, matching the existing
asynchronous removal fixture; the final focused and full app suites passed.

On the development Mac with Android Studio's bundled JBR, `assembleDebug`, `:core:model:test`,
`testDebugUnitTest`, `lintDebug` and `:app:assembleDebugAndroidTest` passed. The unchanged core suite
reused its previous Gradle result (218 tests); the modified app suite executed afresh (874 tests,
zero failures, errors or skips). The offline privacy gate
passed against 113 resolved modules, with four forbidden permissions absent and 49 SDK rules checked.
No persisted state, schema, settings, backup format, dependency or permission changed.
No physical-device or emulator test ran for these fixes; native gesture feel, animation,
accessibility and performance acceptance remain pending.

Installation follow-up (2026-10-13): the fixed debug APK was installed successfully with
`adb install -r` on the connected Galaxy S24 Ultra SM-S928B (Android 16), updating the existing
`io.github.aj4abinjacob.chikku.debug` package without uninstalling it. The installed APK's SHA-256
matched the verified local build, and the HOME holder remained Chikku. This verifies installation
and build identity only; no gesture, animation, accessibility or instrumentation workflow ran.

## Swipe-action app search — 2026-10-13

The shared gesture/swipe app picker keeps its focused search field outside the lazy app rows
when it has no caller header. The list fills its bounded viewport, and the swipe-action sheet
keeps a steady picker height as filtering changes the row count. Caller headers and their
fast-scroller offsets remain supported. No saved settings, identities, backups, permissions or
dependencies change.

`AppPickerSearchTest` exercises real Compose input, rapid query replacement, no matches, clearing
and selecting the target with an existing app action (Robolectric API 36). The native
`SwipeActionSearchDeviceTest` exercises the HOME Compose dialog host, swipe sheet, real keyboard,
both directions, no matches, clearing and selection with synthetic apps/icons and in-memory
actions; it checks that serialized real preferences are unchanged. It omits simple actions to
isolate app-page navigation. Before the focus-layout change, the native fixture lost the visible
picker during input or could not select its result; no process-crash stack trace was captured.
The corrected fixture passed on the Android 16/API 36 arm64 `chikku_qa_20261012` emulator
(1 test, 22.509 seconds). `assembleDebug`, app unit tests (876 tests) and `lintDebug` passed;
`:core:model:test` was up-to-date (218 recorded tests), and the privacy gate passed against
113 resolved modules. Earlier native attempts also needed fixture repairs for animation-time
taps and stale accessibility nodes; those attempts are not passing workflow evidence.
Physical-phone reproduction, OEM keyboards, TalkBack, RTL and large font remain unverified.

## Vertical scrolling controls — 2026-10-13

- **Settings → App drawer → Vertical scrolling**, shown when the Vertical list or List style is
  selected (also reachable from settings search). Scroll speed scales the drawer's fling velocity
  by 0.6×/1×/1.6×, capped at the platform maximum; dragging is unchanged. End-of-list effect keeps
  Android's own (stretch on 12+, labelled Glow before 12) or turns it off. Remember scroll position
  and pull-to-search are the existing settings, also shown on Opening & closing and Search bar.
- The drawer's `drawer_list` is now `VerticalDrawerRecyclerView`; `DrawerController` applies both
  settings on every render. Data and backup coverage: [data model](DATA_MODEL.md#vertical-scrolling-preferences-2026-10-13).
- Verified: `ListScrollingTest` (core), `VerticalDrawerScrollingTest` (fling scaling, platform cap,
  edge mode restore, drawer applies saved values), `VerticalScrollingPreviewTest`, settings codec
  round-trip, full unit suite and lint (remaining failures/errors are in concurrent badge work).
- API 36 emulator: page, tiles, icon row, and preview in light/dark, Arabic RTL and 1.5× font. From
  the top, the preview's standard flick reached sample 13 (Slow), about 29 (Normal) and the end, 48 (Fast).
- Not verified: the real drawer by hand on a device, the end-of-list effect visually (covered by
  the over-scroll-mode test), Android 10/11 glow, TalkBack reading of the page.



## Public repository wording preparation — 2026-10-14

Prepared on `fix/publication-wording` from commit `15b4523`. The project introduction,
requirements, roadmap, contribution guidance and feature-request template describe Chikku
directly instead of using a competitor name as a feature or milestone label.
`strings_nova_phone.xml` is renamed to `strings_extended_settings.xml` with identical contents;
resource identifiers and application behavior are unchanged. Icon-pack source comments now
describe interoperability without a competitor list.

Dated research, source links, historical verification records, licensing notes, backup-import
limitations and the Nova/TeslaCoil icon-pack discovery actions are retained. This is preparation
for a future public repository; no GitHub repository was created, published or made public.
Licence selection and the existing release gates remain pending.

Verified on the developer machine with Android Studio's bundled JDK: `assembleDebug`,
`:core:model:test`, `testDebugUnitTest`, `lintDebug` and `scripts/check-privacy.sh` pass.
Gradle reused cached core/app unit-test results for unchanged executable code. Documentation
checks confirm unchanged link targets, no missing relative-link files in edited documents,
unchanged Markdown table structure, identical resource bytes, unchanged manifest/icon-pack
actions, and untouched research/ADRs. No device/emulator tests were run for this wording
and resource-filename change.


Integration verification on current main (2026-10-14): applied only cleanup commit `1c94d66`
to main `f42cf61`, preserving its newer drawer/gesture changes and leaving the separate icon
work out. Initial full-suite runs exposed stale Compose frame/snapshot delivery in
`AppPickerSearchTest` and `DrawerGridOverviewTest`; the app-picker check passed in isolation.
The two fixtures now use fresh lifecycle-bound dispatchers/frame clocks, deliver snapshot
notifications and advance the virtual frame before reading semantics. Assertions and app
behavior are unchanged. The corrected full build/core-and-app-unit-test/lint run passes;
privacy gate, seven reliability-parser tests and documentation checks also pass.
No device/emulator tests were run for this integration.

## Folder click-action removal fallback — 2026-10-18

On `fix/folder-click-fallback`, removing or moving out a folder member whose exact app/shortcut
identity matches its tap action selects the first remaining member in stored order; an empty
folder returns to its normal open action. Membership and action changes share one transaction
and Undo snapshot. Unrelated tap/swipe actions remain configured. Existing workspace action
storage and portable backup format 1 cover the fallback without new fields or migrations.

Developer macOS host, Android Studio bundled JDK, Robolectric SDK 36: debug assembly, all 230
core tests, 38 targeted app tests (`RoomWorkspaceRepositoryFolderTest`, `FolderHomeTest`,
`FolderLogicTest`, `BackupRestoreRoomTest`), `lintDebug` and the privacy gate passed. New checks
cover exact component/profile/shortcut identity, stored order, empty folders, removal/move-out,
unchanged unrelated actions, rejected moves, reopening/Undo and portable service export/restore.
The full app run completed 1,082 tests with one failure in
`SearchResultsAnchorTest.slowDragScrollsResultsInBothModes` (the row stayed at 147px); that test
passed when rerun alone. The full suite is therefore not recorded as passing. No device was
connected; physical removal, launch, drag and accessibility checks remain unverified.

Main integration verification (2026-10-18): applied the folder fix onto `4ba391f`, retaining
newer shortcut recovery, contact and search changes, including the existing search-test dispatcher
fix. Integration source `d753d9f` passed `assembleDebug :app:bundleRelease :core:model:test
testDebugUnitTest lintDebug`: 231 core and 1,105 app tests, zero failures/skips. All 79 script
tests and the privacy gate passed separately. No device/emulator was connected.
The nondebuggable production-package bundle remains unsigned, versionCode 1 / `0.1.0-dev`;
it is a build-validation artifact, not a published release. SHA-256:
`33e13e85565c2670f884a1d2dc7c2b47fb196c8aa221572de38bcbb4bc7f698d`.
Play publication still needs production upload signing, Console access and confirmed release
version/track. The latest pre-integration main GitHub Actions run could not start because the
account's Actions budget prevents further use; local results do not imply passing hosted CI.
## Shortcut icons and drag feel — 2026-10-21

Branch `fix/shortcut-icons-drag-feel` (from main `4ba391f`), uncommitted at the time of writing.

- **Pinned shortcut icons (G15, FR-006).** Pinned shortcuts on HOME and in folders now have a
  long-press menu (Change icon, Remove). Change icon opens `IconPickerActivity.shortcutIntent`,
  which follows the shortcut live from the workspace and writes
  `WorkspaceRepository.setShortcutIcon` (undoable; the `RecoveryWorkspace` wrapper delegates it).
  The `shortcutIconPack`/`shortcutIconDrawable` columns and their backup keys already existed, so no
  schema, migration or backup-format change. Dragging a shortcut out of a folder keeps its icon.
  Icon packs map app components, so the picker suggests no pack icon for a shortcut.
- **Drag and drop (FR-005).** Finger-anchored opaque drag shadow, faded source, animated drop
  highlight, and a drop that glides from the finger into the stored cell (or back after 400 ms
  when nothing is stored). Haptics on cell change, on reaching Remove and on menu-to-drag pick-up.
  Shortcut icons are no longer reset to the placeholder on every re-render. `Haptics.tick` now gates
  `SEGMENT_TICK` on API 34, where it exists (it was a no-op on Android 13).

Verified on the developer machine: `assembleDebug`, `:core:model:test` (231), `testDebugUnitTest`
(1106, including the new `WorkspaceDropSettleTest`, `ShortcutIconTargetTest` and two
`RoomWorkspaceRepositoryV2Test` cases), `lintDebug` and `scripts/check-privacy.sh` pass. A first
lint run crashed inside lint while sources were being edited; the clean rerun passed. No
device/emulator run: the native drag shadow, frame pacing, haptic feel and a real Chrome/Firefox
pinned shortcut with an installed icon pack are unverified.
