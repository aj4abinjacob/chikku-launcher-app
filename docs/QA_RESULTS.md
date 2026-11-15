# Release-validation QA results

## Source download and recovery follow-through — 2026-10-16

Source download/browser/checksum/extracted-build validation,1,065 app tests,88 host regressions,
four client checks and debug/release lint pass. The core test task was up to date in the latest
invocation; its earlier fresh230-test evidence remains separate.15 focused native recovery,
migration, persistence, widget-host and startup/HOME methods passed on the named API36 AVD.
Actual historical same-key APK upgrade, real reboot, fresh-install service restore and corrected
real SAF round trip passed. Source-reader copy/readback and native graphics conic execution also
passed on4096-byte pages. These do not establish physical, secure-first-unlock or16KB acceptance.
External-widget optimized test-APK ABI failures and an environment assumption skip are preserved;
ordered continuations subsequently pass real configuration, restart, provider loss and fresh-ID
rebind/restore after the fixes below; this is not one uninterrupted clean run. A confirmed recovery defect
reserved an invisible five-row dock and clipped recovery actions with the non-default HOME banner.
Both regression assertions failed before the fix; all eight focused Home/dock tests pass after
releasing dock space during recovery. The preserved nondebuggable API36 installation now exposes
the full exit action and returns to stable Home through it.
The real provider-reinstall sequence also exposed F-04: a newly hosted ID in an already measured
cell received no current size report. Its regression fails before the fix and both size cases pass
in the final full app run. Six native continuation stages pass, including retained failed-state
size recovery, another provider removal/reinstall/configuration/fresh-ID rebind, export, fresh
installation/restore and fresh-ID/configuration again. Geometry/ownership/positive stable options
are asserted; no direct size-option test write or damaged-layout reset is used.
One subsequent fresh corrected widget sequence passes all nine native invocations with zero
failures/skips. Six API36 fresh/unprivileged profile-enumeration stages also pass: work identity,
pause/resume/removal and private running/stopped exclusion; owned cleanup restores the baseline.
These are distinct from actual credential-lock/Private Space UI, derived-surface, DPC/OEM evidence.
Source publication/rights, final committed candidate pairing and wider acceptance gates remain open. See the
[dated report](release/SOURCE_AND_ACCEPTANCE_2026-10-16.md).

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
or complete declaration video was recorded in that initial smoke run. One wrong automation label (Close rather than
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


Subsequent owner-authorized follow-through on the same named disposable API36 AVD recorded an
unedited84.914-second1080x1920 H264 demo: Home gesture disclosure, refusal, return/retry, affirmative
Android handoff, system enable consent and actual Recent apps action. Revocation was then checked:
enabled service list empty, settled-Home retry displays disclosure again. Public hosting approval
and the Play declaration remain pending. Four genuine phone captures and an original feature
graphic were prepared and visually reviewed; the Backup settings capture is not restore evidence.
On HOME-role grant, onboarding returned toStep1; Skip reached Home. The cause remains unisolated,
so full onboarding retention is not claimed. These are emulator results, not physical TalkBack/OEM.

The upload-key-signed AAB at exact source9d19ff3 passes signature, bundletool and manifest/legal/native
checks; its matching archive rebuilt offline in10s (26 tasks executed/27 cached), followed by a
fresh runtime-inventory/static APK check. Signed packaging did not change production inputs.
Exact hashes and gates are in [artifact evidence](release/ARTIFACT_VERIFICATION.md).

## Cloud release validation — 2026-10-14

**NO-GO; all three areas are partially verified.** Started from current main `1c081bb9fdd7df4d854d2de7e31ba8e8128587d4`, preserving changes newer than `39b3930`; reviewed code is `8160cd7a715297b8332a31c790a9015685239f24` on `test/cloud-release-validation-20261014`.

| Area | Newly established | Still open |
|---|---|---|
| Cards accessibility | Resource-backed parent name/disabled semantics; native API29 empty/populated activation, dismissal/reopen, clickability, 48dp and preference value/XML-byte preservation; max/pending disable checks | Broader content-kind editor failed then assumption-skipped; final corrected fixture unavailable; fixed API36/Samsung/TalkBack acceptance |
| Actual upgrade / reboot / restore | Nondebuggable same-key historical development APK910001→910002, Room3→4, retained synthetic production stores and HOME; separate cold-boot retention; service export/fresh-install restore/missing dependencies/Undo/four external interruption recoveries | Bounded adb reboot failed; actual SAF round trip failed; corrected SAF native unavailable; secure first unlock and real widget rebind |
| Widgets / profiles / compatibility | Genuine external Fossify APK installed/seeded; same-component personal/work identity via fresh unprivileged enumeration; private/stale-profile source guards and cleanup safety | No complete configured external-widget pass; pause/resume/remove assertions incomplete; API35+ private exclusion and API36 native unavailable; physical OEM/foldable/battery gates |

Fresh automated checks: **224 core + 949 app JVM tests**, **71 Python tests**, debug build/test APK, lint (0 errors; warnings retained), privacy (4 forbidden permissions, 49 SDK rules, 113 modules), seven invalid acceptance configuration rejections. Final fixture builds compiled; their reused tasks are not new native passes. Earlier dated counts remain historical.

The final command service recovered after a disconnect without the checkout, SDK, APKs or raw QA files. The reset cause is unverified; only confirmed results and known hashes are preserved in the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) and [curated JSON](qa/cloud-release-validation-2026-10-14.json). Unobserved final native completion is **UNAVAILABLE**, never PASS. One orchestrator serialized builds and software emulators within the actual 8GiB/8-core cloud limit; no personal-device workload, publication, visibility change or main merge occurred.

## Historical production reliability evidence — 2026-10-12

**Scoped reliability pass.** Base `23bd9dff7de46f83505ca52c006d158967d5e6ca`, branch `test/production-reliability`, plus the recorded working-tree changes. This is executed reliability evidence, not release acceptance. [Inventory](QA_MASTER_MATRIX.md), [environments](QA_DEVICE_MATRIX.md), [risks](QA_RISK_REGISTER.md), [repeatable commands](QA_REGRESSION_STRATEGY.md), [release decision](QA_RELEASE_READINESS.md).

Three `gpt-6-luna` subagents handled layout/search stress, recovery fixtures and privacy/inventory; the parent serialized builds and each device's instrumentation. Native classes run sequentially in groups of at most three. No publication, personal-device wipe, app uninstall, permission grant or personal HOME-role change was performed.

## Executed foundation checks

| Check | Actual result | Evidence under `build/qa/2026-10-12/` | Scope |
|---|---|---|---|
| Forced core JVM tests | PASS: 214 tests; 0 failures/errors/skips | `full-unit.log`, `final-core-unit-xml/`, `unit-summary.json` | Pure domain logic; synthetic layouts/apps |
| Forced Android unit tests | PASS: 860 tests; 0 failures/errors/skips | `full-unit.log`, `final-app-unit-xml/`, `unit-summary.json` | Robolectric/Room fixtures; not physical integration |
| Debug/release builds and debug/release lint | PASS | `build-lint-release.log`, native lint reports under `app/build/reports/` | Optimized R8 release built; installation/runtime acceptance separate. Each app lint report has 133 warnings and one hint, zero errors. |
| Privacy manifest/dependency gate | PASS | `privacy-gate.log` | 4 forbidden permissions absent; 49 SDK patterns against 113 resolved modules. Not a CVE or complete licence audit. |
| Runner result parser | PASS: 7 standard-library unit tests | `runner-parser-tests.log` | Captured native result, failures, unfinished tests, count mismatch and skips |
| Native recovery on API 36 baseline APK | PASS: 8/8 in 1.472 s | `emulator-recovery-baseline.log` | Separate SQLite/preferences/journal fixtures; APK before arithmetic correction |
| Real process termination at each restore store | PASS: 4/4 recovery assertions after external force-stop; fresh PID asserted | `process-death.log`, `process-death/20261012-223841-9387330f/` | Synced marker after settings/customizations/groups/workspace write. Terminated preparation is expected interruption, not a passing test. Synthetic stores and widget reference only; not power loss or HOME drag. |
| Native recovery on Redmi/API 30 existing APK | PASS: 8/8 in 7.029 s, 8.578 s host wall time | `physical-isolated-fixtures.log`, `physical-preservation.json`, `physical-apk-sha256.txt` | Installed APK/test APK retained; revision is not embedded, so this does not verify the new arithmetic fix physically. |

The first attempted build encountered a new fixture compilation error (`BooleanSubject.named`); the fixture was corrected before executed results were accepted. `baseline.log` retains this attempt. Up-to-date Gradle tasks were not counted as fresh test execution; the full unit run used `--rerun-tasks`.

## Confirmed defect F-01

- **Priority/severity:** P1, defensive layout integrity under malformed extreme geometry; no ordinary UI data loss or launcher crash has been demonstrated.
- **Environment:** macOS/JVM, project base above plus new regression tests; independent of device/OEM.
- **Scenario mapping:** 058, 068, 073, 153 and adversarial state checks. These tests cover arithmetic boundaries, not the complete corresponding UI journeys.
- **Setup/reproduction:** use a 12×12 grid and a valid domain placement with `x=1, spanX=Int.MAX_VALUE`; validate it. Separately compare collision against the cell at `x=2`. For nearest placement, compare a 4×4 grid containing occupied holes against an independent `BigInteger` squared-distance oracle with `Int.MIN_VALUE`/`Int.MAX_VALUE` preferred coordinates.
- **Expected:** reject the oversized placement, detect the collision and retain exact nearest-cell/tie ordering.
- **Actual before fix:** overflow admitted the oversized placement, hid the collision and returned the wrong nearest candidate. Three independent methods failed in one executed original-engine run; the crowded-grid stress case passed.
- **Root cause:** Int endpoint arithmetic and subtraction before widening; the sum of squared extreme distances also exceeds signed Long.
- **Fix:** Long endpoint comparisons; exact nearest-candidate score with the constant preferred-coordinate squares removed. `GridSpec.MAX_DIMENSION=12` bounds the remaining products well within Long; scan order preserves ties.
- **Regression:** `ReliabilityLayoutStressTest` — independent bounds, overlap and BigInteger-oracle methods; seed 20261012, 3,000 mixed-span attempts across 20 full pages.
- **Evidence:** `adversarial-before-fix.log`, `overflow-original-expanded.log` (3 failures), `overflow-fixed-expanded.log` (4/4 passing), and the complete forced unit run.
- **Status:** fixed and JVM-verified; no new setting, storage schema, backup version, dependency, permission or exported component.

## Additional new adversarial coverage

`ReliabilitySearchStressTest` feeds 10,000 synthetic multilingual labels with component/profile copies into universal search and checks stable identity, repeatability and hidden-key exclusion. This is not a real installed-app collection or a phone latency benchmark.

`ReliabilityRestoreRetryTest` uses separate file-backed Room/preferences and an AtomicFile journal. A failed target write and failed first rollback leave durable recovery state; reopen/retry restores all four stores and synthetic widget ID 7301, removes the pending journal and remains stable on another prepare. No real widget is bound by this test.

## First broad native emulator pass

Fresh disposable `chikku_qa_20261012`, `emulator-5580`, API 36 Google APIs ARM64, fingerprint `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`, 1080×2400, 420 dpi, 2 GB configured RAM, 60 Hz configuration, software graphics. Debug APK hash `7308cfedd8b759461e2a3bdaa23ab4c0b269d45628bda8bd6af57acd451d3e2d`. Exact commands and metadata are in `emulator/20261012-213102-42f810b3/`.

Result: **68 PASS, 16 FAIL, 2 assumption skips**, across 32 classes (19 PASS classes, 12 FAIL, 1 BLOCKED). The preparation placed Chikku in the emulator HOME role; this does not exercise the official interactive role consent flow. The external process-kill fixture is intentionally excluded from ordinary discovery. Failed attempts are retained, not erased or relabelled by later passes.

| Failed class | Initial observed failure | Current disposition |
|---|---|---|
| DockSettingsDeviceTest | Initial remembered dock-page/show predicate timed out | PASS after completing real onboarding and reverting emulator to stock HOME; production unchanged |
| HomeActivityDeviceTest | Dock not shown; accessible drawer arrow missing; click did not open visible drawer | 3/3 PASS after the same environment preparation; production unchanged |
| WidgetResizeFrameDeviceTest | Real drag produced no commit in LTR/RTL | 2/2 PASS after environment preparation and explicit measured/drawn-frame fixture waits |
| SearchDragDeviceTest | Search expected hidden after stationary long press, then a retained context menu covered the next tap | PASS in `triage/20261012-225943-028ad966/`; same-pointer continuation and normal menu dismissal preserve drag/cancel/lock assertions |
| CardEditorDeviceTest | Empty list did not expose Add card selector | Empty-state selector corrected; both later add-card workflows still FAIL because the rendered Add card FAB is absent from the accessibility hierarchy (F-02) |
| DrawerRowsDeviceTest | Old untrimmed row-height expectation disagreed with rendered 746 px slot | PASS in `triage/20261012-223511-5678e8b9/` after checking visible row centers, density change and recreation |
| ExpandedSettingsDeviceTest | Drawer arrow missing during HOME workflow; old scroll helper missed controls | 3/3 PASS after scoping onboarding and scanning the actual vertical settings viewport |
| FolderEditorDeviceTest | Expected an unused preview title | 2/2 PASS in `triage/20261012-224146-6bcd7331/`, asserting the live folder preview/action instead |
| FolderPagingDeviceTest | Could not find Scroll direction | 2/2 PASS in that same run after checking the actual viewport instead of treating a scroll return flag as end-of-list |
| SettingsValueClarityDeviceTest | Settings selector failure | 3/3 PASS in `final-native/20261012-232733-212b218f/`; scroll through the gutter to avoid changing sliders, handle subpages whose content fits without scrolling, and retain exact displayed-value assertions |
| SettingsValuesDeviceTest | Slider title lived under its range owner; a retained offset hid controls | 3/3 PASS in `triage/20261012-230440-98492a39/` after native-tree lookup and viewport corrections |
| WeatherCardEditorDeviceTest | Synthetic first consent row was under the status bar | 2/2 PASS in `triage/20261012-225943-028ad966/` after applying system-bar insets to the synthetic fixture |

The first environment was genuinely fresh: the observed foreground UI was onboarding Step 1 of 6. `DefaultHomeDeviceTest` temporarily completes onboarding and restores the original incomplete state; later HOME workflows therefore encountered onboarding. The orchestrator selected **Skip** through the actual UI and restored Pixel Launcher as emulator HOME before retrying the three HOME classes. These hypotheses must not be reported as product defects without isolation.

WebSuggestionDeviceTest was BLOCKED because its live-provider opt-in argument was not supplied; actual network requests were not silently enabled. MediaObservationDeviceTest passed one test and skipped the synthetic-session test because notification access had not been granted. The orchestrator did not grant it.

## Native retests and open finding F-02

The latest result for each of the original 32 classes totals **82 PASS, 2 FAIL and 2 assumption skips** (`latest-native-summary.json`). This combines dated focused reruns with the original run, not one clean acceptance run. Classes are replaced as a whole when a method is renamed; repeated passes are not added to the total. Failed and superseded attempts remain available. The rebuilt-source batch in `final-native/20261012-232537-35c8f636/` reconfirmed all seven restore/migration checks. A later batch in `final-native/20261012-232733-212b218f/` passed all three settings-clarity methods and all three HOME methods, and reconfirmed both CardEditor failures after the production experiments were reverted. No production formatting change was needed.

**F-02 (P1, unresolved):** both native Cards workflows repeatedly encounter a visually rendered Add card FAB absent from the Android accessibility hierarchy. Final rebuilt-APK screenshots and hierarchy files are `card-editor-add-card-1791827919242.png/.xml` and `card-editor-add-card-1791827969061.png/.xml`; earlier evidence is retained too. The regression retains semantic querying; no coordinate-tap bypass was introduced. Column-level and direct-button stacking experiments did not resolve the failure and were reverted. Root cause remains unknown, and actual TalkBack has not been tested.

The final debug/test APKs were rebuilt and installed only on the disposable emulator after those production experiments were reverted. `final-source-build.log` records successful debug/release APKs, instrumentation compilation, both unit tasks and both lint tasks; cached or up-to-date tests are not new execution. `final-clarity-build.log` and `final-instrumentation-lint.log` verify the last fixture adjustment compiles and passes lint. `runner-parser-final-source.log` records seven fresh parser passes. `privacy-final-source-retry.log` passes with the documented JDK; the preceding attempt records a missing-JDK setup failure. The first final-native selection used an incorrect class package and was rejected before executing any test; the corrected selection is retained separately.

## Physical state preservation

Redmi 9 M2004J19PI, Android 11/API 30, fingerprint `POCO/shiva/shiva:11/RP1A.200720.011/V12.5.7.0.RJRINXM:user/release-keys`, MemTotal 5,894,088 kB, 1080×2340, density 440, font scale 1.0. The three native recovery classes used separate UUID-named storage; no HOME UI or real provider was opened. Original preferences/no-backup file hashes and logical production database rows matched after execution, no extra preference/no-backup files remained, and HOME resolution stayed unchanged. Raw personal data was processed locally for comparison, not saved in the report or committed.

## Evidence and interpretation

Local logs/XML/metadata are retained in gitignored `build/qa/2026-10-12/`; the committed reports summarize them. Scripts can recreate new evidence without overwriting prior run directories. Unit fixtures, isolated native stores, real system HOME, real widget hosting, third-party providers and physical OEM acceptance remain different evidence.

The scoped batch ended on 2026-10-12. The disposable emulator was stopped with stock HOME retained; its dedicated AVD and synthetic diagnostics remain available for follow-up. No overnight or recurring job was scheduled. The physical phone was not modified beyond the isolated fixture execution described above.

No startup/frame/battery budget, one-hour leak acceptance, 1,000-switch soak, reboot, production-version APK upgrade, real managed/private-profile transition, TalkBack or broad manufacturer acceptance is claimed by the checks above. Source review did not identify a confirmed privacy leak; it is not a runtime privacy proof.
