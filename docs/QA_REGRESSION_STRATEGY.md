# QA regression strategy — 2026-10-12

The run order is P0 availability, recovery/data integrity and privacy; then P1 integration and reliability; then P2 accessibility/performance refinement. Each case records setup, exact actions, expected behavior, actual result, evidence and cleanup in the parent-owned `QA_RESULTS` artifact. The master matrix is the source of IDs and current status. Keep failed, blocked and unsupported outcomes distinct.

## Existing synthetic coverage (not this-run PASS)

Repository status and tests identify useful automated coverage:

- HOME and launch: `DefaultHomeDeviceTest`, `HomeActivityDeviceTest`, `HomeIntentResolutionTest`, `AppLaunchDeviceTest`; JVM `LauncherAppsRepositoryTest`, `PlatformMappingTest`.
- Recovery and persistence: `BackupServiceTest`, `BackupRestoreRoomTest`, `RestoreAccessTest`, `FileRestoreJournalTest` (as described by `docs/TEST_STRATEGY.md`), `RestoreRecoveryDeviceTest`, `MigrationRecoveryDeviceTest`, `StartupRecoveryDeviceTest`, `ProcessDeathRestoreDeviceTest`.
- Layout, folders, widgets: workspace/layout engine suites, `LayoutChangeDeviceTest`, `WidgetBindFlowTest`, `WidgetLifecycleDeviceTest`, `WidgetResizeFrameDeviceTest`; these do not cover arbitrary third-party provider behavior.
- Search and profile identity: `UniversalSearchTest`, `SearchViewModelTest`, `SearchPlatformTest`, `ShortcutProfileTransitionsTest`, `LauncherAppsRepositoryDeviceTest`, `TypoContactsSearchTest`.
- Permissions and optional integrations: `BluetoothEnvironmentOptInTest`, `MediaObservationDeviceTest`, `OpenMeteoWeatherSourceTest`, `HttpsWebSuggestionSourceTest`, `SearchHandoffLockTest`.
- Settings and accessibility semantics: `LivePreviewsDeviceTest`, `PreviewGapsDeviceTest`, `PersonCardLayoutTest`, `ContactCardPreviewTest`; physical TalkBack and genuine provider/contact behavior remain distinct.

These names describe available fixtures; exact prior results and limitations are in [implementation status](IMPLEMENTATION_STATUS.md). Synthetic data, fake services, Robolectric, native isolated fixtures, emulator runs and physical workflows must be labeled separately. General settings fixtures require completed onboarding and stock HOME; DefaultHomeDeviceTest is separate. A partial test-class result does not pass an entire numbered scenario unless it covers the full acceptance criteria.

## Execution recipes by inventory group

| IDs | Setup and actions | Expected behavior | Recovery / evidence |
|---|---|---|---|
| 001–034 | Disposable install; retain stock HOME; exercise role choice, app-to-HOME, lock/reopen, process kill, force-stop as a separate control, rotations and startup guard. | Chikku renders or Android returns to a usable HOME; committed state survives; transient state resolves; no restart loop. | Restore stock HOME, capture build/role/logcat and exact steps; process kill and reboot evidence are separate. |
| 035–052 | Use generated app inventory for scale, then install real packages for lifecycle integration. Include multilingual labels, similar labels, multiple activities, updates and removal during drawer use. | One correct launch target per component/profile; stale rows disappear; fallback icons do not crash. | Preserve package inventory fixture; capture count, API and timing separately. |
| 053–074 | Seed synthetic placements on isolated DB; fill cells/pages, collide, drag/cancel/rotate, change grid and use Undo. | Deterministic placement, no lost or unreachable item, recoverable edits. | Reopen DB and compare identities/placements plus Undo; retain failing fixture. |
| 075–100 | Register controlled widget providers on disposable emulator; add/configure/cancel/resize/remove, kill launcher, uninstall provider and restore. | Host lifecycle is isolated; provider loss retains resolvable placement; no invalid ID reuse; widget failure does not take down HOME. | Record provider component and allocated IDs; clean only fixture-owned IDs. Real third-party providers need a separate run. |
| 101–120 | Same-build stock-like emulator in gesture and 3-button modes, then named OEM comparisons; exercise Home/Recents/Back/keyboard/system UI. | Chikku behavior matches supported public APIs and guidance; distinguish OEM/platform transitions from app failure. | Capture screen recording/build/nav mode; never promise universal Recents animation. |
| 121–134 | Controlled shortcut publishers and icon packs; pin, cancel, update/remove, profile identity and missing icons. | Only valid user-confirmed actions persist; stale targets fail safely; fallback artwork resolves. | Record publisher fixture and shortcut token outcome; clear fixture placements. |
| 135–156 | Synthetic backup corpus; same-device and cross-version restore on isolated storage; corrupt, future, large and interrupted inputs. | Validate before apply; recover atomically or retain explicit recovery state; preserve privacy exclusions and device-local identity checks. | Keep original store snapshot; compare full rows/settings/files; record archive hashes without personal content. |
| 157–172 | Managed profile fixture and API 35+ Private Space-capable disposable setup. Test add/pause/resume/remove, duplicate package identity, lock and unlock. | Profile identities remain separated; locked/private contents are absent. Private Space container support is NOT IMPLEMENTED in M0. | Capture API/role/profile configuration; no personal profile data. Unsupported boundary is not PASS. |
| 173–197 | Define workloads before run; measure cold/warm/Home latency, scrolling, memory, idle CPU/battery and frame timing at controlled app counts. | Publish measured baselines only; identify ANR/jank/resource leaks with repeatable workload. | Record tool/version, sample count, device thermal/power state and raw trace; no invented budget. |
| 198–218 | TalkBack, keyboard/switch access, 200% font, display scale, RTL, translations, contrast, theme, landscape, insets and foldable transitions. | All essential actions reachable; descriptions and targets understandable; content not clipped. | Screenshots/video and exact accessibility settings; synthetic account/app data only. |
| 219–233 | Review merged manifest, exported components, URI grants, intent validation, backup contents, permissions, dependencies and captured logs; offline and denied-permission runs. | No unintended private data exposure, unsafe path/import behavior, or unconsented external request. | Attach sanitized manifest/report/log snippets; do not include real labels, contacts, or profile identifiers. |
| 234–252 | Offline/airplane, network toggles, timezone/locale/theme, screen events, notification/call, wallpaper, accessibility service and reboot scenarios. | Core HOME remains available offline; optional features fail contained; configuration changes preserve committed state. | Restore only fixture-owned settings; capture state and exact environmental change. |
| 253–266 | Seeded random UI/fault sequences on disposable environment; save seed and event trace; repeat failures. | No crash loops, deadlocks, duplicated state or silent data loss; failure reproducible from seed. | Restore from snapshot; keep seed, trace and post-failure DB; never fuzz personal device. |
| 267–278 | Install debug and optimized release; use signed previous-version fixture then upgrade on disposable API 30/36. | Minified behavior works; signing/upgrade keeps layouts and settings; migrations are idempotent. | Keep APK hashes/signing identity and before/after state; no personal app version replacement. |

## Executed evidence — 2026-10-12

| Scope | Result | Evidence and limit |
|---|---|---|
| Forced core + app unit suites | PASS — 214 core tests + 860 app tests; zero failures, errors or skips | `build/qa/2026-10-12/unit-summary.json`; XML under `final-core-unit-xml/` and `final-app-unit-xml/`; full output `full-unit.log`. JVM/Robolectric coverage only. |
| New layout/search/restore regressions | PASS — six tests included in the suite above | `ReliabilityLayoutStressTest` (4: seeded 12×12 occupancy over 20 pages, extreme-span bounds and overlap, independent BigInteger nearest-cell oracle); `ReliabilitySearchStressTest` (10,000 synthetic apps, deterministic profile identity/hidden filtering); `ReliabilityRestoreRetryTest` (failed rollback, file journal, Room close/reopen/retry, original widget ID). XML names are in the final report directories. |
| Layout overflow regression before fix | FAIL reproduced, then fixed and PASS in final suite | `adversarial-before-fix.log` recorded `Int.MAX_VALUE` span accepted by `WorkspaceLayoutEngine.validate`; final `boundsCheckDoesNotOverflowForMalformedLargeSpan` and `overlapCheckDoesNotOverflowForMalformedLargeSpan` pass. This is a pure-model adversarial finding, not evidence of ordinary UI data loss. |
| Native restore/migration/startup recovery | PASS — 8/8 tests on disposable API 36 emulator, baseline APK before layout fix | `emulator-recovery-baseline.log`: 2 `RestoreRecoveryDeviceTest`, 5 `MigrationRecoveryDeviceTest`, 1 `StartupRecoveryDeviceTest`. Synthetic isolated stores; this does not prove full HOME launch, process death outside the named fixtures, release APK behavior, or final-APK acceptance. |
| Privacy gate | PASS — 4 forbidden permissions absent; 49 SDK patterns checked against 113 resolved modules | `privacy-gate.log`. This is the repository's permission/SDK-pattern gate, not a CVE audit or network traffic capture. |
| API 36 native reliability suite | PARTIAL — 32 classes; 82 pass, 2 fail, 2 assumption skips | The latest aggregate leaves only the two CardEditor failures. SettingsValueClarity passed 3/3 in `build/qa/2026-10-12/final-native/20261012-232733-212b218f/batch-01-01-SettingsValueClarityDeviceTest.log`; HomeActivity passed 3/3 in the prior focused report `build/qa/2026-10-12/triage/20261012-231644-d8ddc6c9/report.json`. Aggregate is not a release pass. |
| Cards accessibility discovery | FAIL — repeated 3+ times; rendered “Add card” FAB absent from Android accessibility hierarchy | `card-editor-add-card-1791827244856.xml` / `.png` and `card-editor-add-card-1791827296688.xml` / `.png`, copied from emulator output. Cause unknown; experimental `zIndex` changes were reverted. |
| Native fixture prerequisites and physical preservation | General settings fixtures used completed onboarding and stock HOME; default-HOME workflow is separate. Physical isolated fixtures PASS 8/8 and preserved installed APK, original preferences and database. | `build/qa/2026-10-12/physical-isolated-fixtures.log`, `physical-preservation.json`. Redmi 9 was limited to this isolated run; no destructive/personal-setting tests. |
| Process termination restore | PASS — 4/4 recovery cut points | `build/qa/2026-10-12/process-death/20261012-223841-9387330f/report.json`; disposable API 36 emulator and synthetic state. |
| Optional native integrations | WebSuggestion BLOCKED; media-session observation assumption-skipped | Missing prerequisites are not passes; refer to the parent-owned results artifact for exact blocker details. |

Scenario status changes in the master matrix are limited to: 054 (synthetic grid occupancy); 142–145 (corrupt/truncated/future/oversized backup decode fixtures); 148 (injected restore interruption/failed rollback and recovery); 149 (native synthetic schema 1/2/3 migration and newer-schema preservation); 219 (manifest/source exported-component audit); and 233 (the explicit forbidden-permission/SDK-pattern gate plus source review). IDs 165–168 are NOT IMPLEMENTED for M0 Private Space UI. The evidence does not establish end-to-end UI acceptance for those or neighboring scenarios. The latest Clarity fixture pass retained exact dp assertions after correcting scroll-gutter/no-scroll-page traversal; evidence does not indicate a production value-formatting defect. The two CardEditor Add card accessibility-query failures remain the only known native failures reconfirmed by the final rebuilt-APK CardEditor rerun; see F-02 in the risk register.

## Repeating programmatic batches

Use the repository's documented JDK and SDK, then build the debug application and instrumentation APKs. On a **disposable emulator only**, install both successfully built APKs with replacement; complete onboarding through the UI and retain stock HOME for general settings fixtures. Run DefaultHomeDeviceTest separately with its required HOME-role setup. Never substitute a personal phone for these runners: both verify an `emulator-N` serial and `ro.kernel.qemu=1`.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug assembleDebugAndroidTest
python3 -m unittest discover -s scripts/tests -v
python3 scripts/run-reliability.py --serial emulator-5580 \
  --adb /opt/homebrew/share/android-commandlinetools/platform-tools/adb \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --classes io.github.aj4abinjacob.chikku.backup.RestoreRecoveryDeviceTest \
    io.github.aj4abinjacob.chikku.data.MigrationRecoveryDeviceTest \
    io.github.aj4abinjacob.chikku.home.StartupRecoveryDeviceTest \
  --timeout-seconds 300 --output-root build/qa/reliability
python3 scripts/run-process-death-restore.py --serial emulator-5580 \
  --adb /opt/homebrew/share/android-commandlinetools/platform-tools/adb \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --output-root build/qa/process-death
```

Adapt the SDK path and serial to the verified disposable emulator. `--all` discovers native classes and groups them in batches of at most three, excluding the externally orchestrated process-death fixture. Native calls remain sequential. The runner does not install, wipe, grant access or configure roles. It records unique timestamped logs, build hashes and parsed results, stops after timeout or lost transport, and counts assumption skips separately. If a call times out, resolve the potentially still-running remote instrumentation before another invocation. The process runner force-stops only the debug package on the verified emulator at each synced synthetic checkpoint; killed preparation is expected interruption, never a PASS.

No recurring automation or overnight job was scheduled in this pass. These scripts support further bounded batches when a disposable emulator and the required prerequisites are supplied.

## Run acceptance

A PASS requires the scenario to execute in its named environment and retain evidence. BLOCKED names the unavailable dependency and attempted setup. NOT IMPLEMENTED marks a product boundary, including M0 Private Space UI. NOT RUN means no execution evidence for this run. Source review and previous dated results may guide selection, but do not change the current-run status. Each confirmed fix needs a focused regression case before release evaluation.
