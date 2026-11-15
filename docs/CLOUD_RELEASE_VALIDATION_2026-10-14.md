# Cloud release validation — 2026-10-14

**Release decision: NO-GO.** All three areas remain partially verified. This report separates source fixes, executed assertions, corrected failures, assumption skips and unavailable acceptance.

**Evidence loss:** the final cloud execution service disconnected and recovered into an environment without the checkout, SDK, APKs or raw QA files. Resource counters reset to zero. The cause of the reset is unverified. The source commits remain on GitHub. This report and the [machine-readable summary](qa/cloud-release-validation-2026-10-14.json) preserve results confirmed before that loss; missing raw logs, intermediate APK hashes and unobserved completion are not fabricated. No duplicate toolchain was installed after the reset.

## Source, versions and isolation

- Current main: `1c081bb9fdd7df4d854d2de7e31ba8e8128587d4`, newer than the supplied `39b3930`; final reviewed code: `8160cd7a715297b8332a31c790a9015685239f24`.
- Dedicated branch: `test/cloud-release-validation-20261014`. Main remained unchanged and the repository remained private at the last GitHub read.
- Historical development source: `f69121e08e04c64d0c3541304484893877c9788f`; verified Room schema **3**, without widgetPadding. Main is schema **4**, with nullable widgetPadding. Portable backup format stays **1**.
- This is a **historical development-APK upgrade**, not a production-release upgrade. No published production release baseline was identified.
- Normal IDs, versionCode **1**, versionName **0.1.0-dev** and signing remain unchanged. Test overrides require `io.github.aj4abinjacob.chikku.acceptance.<scenario>`; unisolated signing/version overrides, invalid IDs/values and a keystore inside the repository are rejected.
- Only session-owned disposable installations/profiles were manipulated. No Mac, personal phone, real user installation, publication, visibility change, public repository creation or main merge was used.

Important source revisions:

| Revision | Scope |
|---|---|
| `6e739ee14a227121063e01072f678edeb613a31c` | Cards parent naming/disabled state, in-memory fixtures, profile snapshot hardening, guarded acceptance build/runners; fresh full JVM checks |
| `9bd050e971a1d333c97bdcc49f2a6dd0251b736d` | Acceptance storage ABI and CI resource caps; represented corrected actual upgrade/service APK pair |
| `7af8974944fb43138246354a1b08cea83fe827fc` | HOME restoration validation before mutation; corrected Cards required checks |
| `da8d94425fdd5b304318714e1d19c4cbf9c11f81` | Revised editor/SAF/widget active-window fixtures and later-API quiet setup |
| `2f6a5fe5f7d3ec2a5dc61505a358b1baa6b45a5e` | Remove broad Cards diagnostics; durable failure evidence and guaranteed cleanup |
| `e53f2b1542cebf1d830caba545e08457aaf86eea` | Exact creation nonce reconciliation, complete baseline cleanup; 71 fresh Python checks |
| `8160cd7a715297b8332a31c790a9015685239f24` | Acceptance ViewModelProvider API and bounded unlocked-screen wait; final fixture compilation |

Historical APK preparation applied only the guarded acceptance build/signing rules, acceptance API keeps and historical-compatible seed fixture to f69121e. Current product code was not backported. Some APKs were built just before the commit that represents their exact source; artifact hashes identify the executed inputs.

## Environment and resource limits

Ubuntu24.04 x86_64, Linux6.18.44; CPU quota **8 cores** (9 reported), cgroup memory **8GiB**, 32GiB filesystem with approximately 27.6GiB initially free. Initially no SDK/adb/emulator or /dev/kvm existed; VMX/SVM/KVM acceleration was unavailable.

One shared Temurin **21.0.8+9**, Gradle **9.7.1** and SDK were provisioned: compile platform **37.0 revision2**, selected build tools **36.0.0**, platform tools **37.0.1**, emulator **37.3.4.0/build16555280**. Only the orchestrator ran heavyweight work. An exclusive Gradle lock, two workers, 2GiB heap, in-process Kotlin and no daemon/configuration cache were used; emulators stopped before builds. At most one Gradle build and one emulator ran across agents. Agents did disjoint source work only.

API36 Google APIs x86_64 revision7, forced TCG, requested2GiB/effective2.5GiB guest RAM: bounded boot **failed after481.46s**, with zero APK installations/tests. API29 Google APIs x86 revision13 booted and executed native workflows: fingerprint `google/sdk_gphone_x86/generic_x86:10/QSR1.211112.011/13135432:userdebug/dev-keys`, 720×1280/320dpi, requested1536/effective2048MiB, two cores, Swiftshader/TCG. The final AVD enforced a6GiB data partition despite smaller requests.

Downloaded archives, unused build tools37 and byte-identical duplicate APK pulls were reclaimed while retaining source APK inputs and guest stores. Stale runtime lock files were cleared only after the owned emulator stopped. The last captured cgroup snapshot had **0 OOM/0 OOM kills**; it is not a diagnosis of the later reset.

Final unlocked-workflow preparation disabled only the insecure keyguard in the synthetic guest; no credentials were supplied or bypassed. Install-time ART filter was temporarily set to verify via this disposable userdebug guest, and adbd identity was confirmed restored to2000 before tests. This changes no target APK permissions/R8/signing and establishes no battery/startup/performance acceptance.

## Compact evidence table

PASS means an executed check succeeded. Cached/up-to-date compilation is reuse, not fresh tests. Repeated native methods are not summed into a misleading unique-test total. Earlier 2026-10-12 results remain historical.

| Check | Result | Evidence and limits |
|---|---|---|
| assembleDebug / debug test APK | PASS | Normal ID/signing build and fixture compilation |
| :core:model:test --rerun-tasks | PASS | 224 fresh tests, 0 failures/errors/skips |
| testDebugUnitTest | PASS | 949 fresh tests, 0 failures/errors/skips; includes Cards8 and profile privacy7 |
| lintDebug | PASS with warnings | 0 errors; app167 warnings/1 hint, bridge3 warnings |
| scripts/check-privacy.sh | PASS | Four forbidden permissions absent, 49 SDK rules, 113 modules |
| Python runners/parsers | PASS | 71 fresh tests, 0 skips; strict counts/boot UUID and failed/async cleanup |
| Acceptance invalid configurations | PASS | Seven actual rejected configurations |
| Final reviewed fixture builds | PASS | Cards31s, widgets3m9s, upgrade3m1s; compiled, not native passes |
| Cards empty/populated naming, real activation/dismiss/reopen, enabled/clickable,48dp, preferences | PASS on API29 | Exact resource/text/description queries, in-memory dependencies, map/XML-byte preservation |
| Cards max24 and pending-save disabled action | PASS on API29 | Accessible name retained, disabled state and refused activation |
| Broader schedule/recreate/delete/Undo/reorder | PASS on API29 | Passed twice; later503.502s host duration is not a performance result |
| Broader content-kind editor | FAIL / SKIPPED | Original scroll fixture failure; two later unlocked-screen assumption skips. Final wait/privacy APK update unavailable |
| Actual historical910001→910002 replacement/schema3→4/HOME/retained stores | PASS on API29 | Nondebuggable R8 app pair, same disposable certificate, actual package replacement |
| Bounded adb reboot/changed UUID/boot/first unlock | FAIL |600s acceptance timeout; returned reboot command is insufficient |
| Later retained-image cold start/HOME/retention | PASS |126.32s; changed UUID and boot/unlock checked separately |
| Full service export/fresh install/import/missing dependencies | PASS | Production stores, portable widget ID0, attention without discarding saved values |
| Durable Undo after external restart | PASS | Separate apply/restart/assertion |
| Malformed/truncated/future archives | PASS | Rejected without writes |
| Four interrupted restore cuts | PASS | Four external terminations/new-process recoveries; preparations EXPECTED_INTERRUPTION |
| Actual SAF picker round trip | FAIL / fixed native UNAVAILABLE | Earlier named-control failures; final owned/drawn Settings attempt failed on ViewModelProvider class resolution. Acceptance keep compiled; no fixed native pass observed |
| Genuine managed active identity | PASS on API29 | Same component/personal-work identities, fresh unprivileged enumeration |
| Managed pause/resume/remove | OPEN | Initial MANAGE_USERS setup failed; later timeouts produced no completed transition assertions |
| Managed host cleanup | Corrected | Tracked-set PASS did not prove complete cleanup; committed synthetic profile reconciled and removed; later exact-baseline cleanup passed |
| Genuine external Fossify install/seed | PASS | External APK/real configure Activity; not a stock/test-only provider |
| Genuine widget consent/config/restart/removal/rebind/fresh restore | FAIL / UNAVAILABLE | Initial consent traversal hit Android10 framework null Provider.id; revised fixture compiled. Later startup wait failed before assertions. No configured pass claimed |
| API36 native / API35+ actual private exclusion | UNAVAILABLE | Boot/resource environment prevented execution; Private Space UI deliberately unsupported |
| Physical OEM/TalkBack/foldable/battery/performance | UNAVAILABLE | No physical cloud device; emulator evidence does not satisfy these gates |
| Final runtime continuation after reset | UNAVAILABLE | Last observed updated Cards test pair unavailable; widget app update pending. No subsequent assertion was observed |

## Cards finding and preservation

Resolved Compose BOM **2026.09.00** uses Material3 **1.4.0**. Its extended FAB text Row clears semantics; Chikku supplied a null-described icon and no parent label. The parent now uses existing localized cards_add and explicit disabled semantics, with a guarded callback. This addresses the refined unnamed-clickable-node diagnosis.

F-02 historical API36 failed queries and S24 Ultra/Android16 investigation remain evidence. No fixed Samsung pass before the crash is inferred. Cards native fixtures use synthetic in-memory settings/apps/workspace/groups/customizations, no-op weather/media and an empty injected widget catalog. They compare saved preference values and XML bytes without rewriting them. Real semantic queries, dialog actions and48dp assertions remain. Failure window dumps/screenshots/label-path logging are removed. Cloud foreign-ANR dismissal is guarded by exact acceptance.cards/QEMU/unlocked-user identity and refuses a Chikku ANR.

## Upgrade, restore, profile and widget boundaries

The actual upgrade used **acceptance.upgrade**, historical **910001/historical-schema3-acceptance** and current **910002/current-schema4-acceptance**. The schema4 target includes nullable widgetPadding; seeded synthetic production repositories survived actual replacement. Normal versions did not change. App/test APKs share disposable certificate `8cf6697f3b4bdcce181552501911536893e07082eec519012e34fe3de25f2dbd`; test APK version fields were absent and remain null. Full hashes for nine verified pairs appear in the machine summary. Binary/raw local artifacts are unavailable after reset.

Acceptance-only Trace/Kotlin/coroutine, Chikku public/protected, SQLite/Room and ViewModelProvider API keeps retain separately compiled native consumers while allowing body optimization. Initial zero-test Trace/Kotlin crashes and one-test factory/SQLite failures remain failed evidence. These acceptance APKs are nondebuggable/minified but are not byte-equivalent closed-world production shrink/size/performance evidence.

Service restore, SAF document UI and actual widget rebind remain separate. Backup service passes include full four-store contents, missing icon-pack/document URI attention, portable widget IDs, durable Undo and externally interrupted journal recovery. Real Create/OpenDocument UI fixtures require named picker controls, preview/cancel/confirm, actual producer completion and visible feedback. The final65.161s SAF failure had an owned, focused/drawn Settings screen and create action, but no producer because the provider class could not resolve. Its descriptor was absent in the old APK and present in the corrected APK; no passing corrected native result was observed.

The real profile runner creates genuine framework managed users; this is not DPC/OEM provisioning evidence. API29–34 setup opts into a temporary foreground acceptance HOME, restores previous HOME/settings, validates both components before mutation, and enumerates without adopted permissions. Later APIs adopt only MODIFY_QUIET_MODE for setup and drop it before reads; those branches remain native unavailable. Known private/inaccessible profiles and stale serial/type/quiet/unlocked snapshots fail closed before and after labels are read. API29 work type remains OTHER by the public metadata boundary. Private Space UI is outside this milestone; native API35+ exclusion is still open.

Creation may commit after the client times out. A tracked-owned-set cleanup PASS was insufficient; the next sole-user guard stopped. The session's fresh baseline, sole creation attempt and unique synthetic managed profile identified that committed state, which was removed and user0-only freshly confirmed. The reviewed runner registers an exact in-memory nonce before mutation and refuses ambiguous/pending ownership, waits for asynchronous removal, then compares all IDs with the preflight baseline. Names/details are not logged.

Fossify Clock **1.6.0/code10**, source `4ecb9e33c51c0b2096af2379e7ac9dc17c3e07e6`, APK SHA `43cf9f0ec45f1f1ff2df47286622e5b8f3acedaeb07b7f7641b5243dbedca079`, min26/target36/no INTERNET, was separately installed. Its digital provider and real WidgetDigitalConfigureActivity/Save are pinned. GPL3 source/assets were not copied into Chikku and no dependency/permission was added. Eight phased native methods cover actual consent, configuration, hosted TextClock, options, provider/launcher PID restart, provider loss, fresh-ID rebind and portable full backup/fresh restore. The Android10 null Provider.id failure came from all-window accessibility traversal with an unbound allocation; the fixture uses the real active window/resource and ACTION_CLICK. It is not an OS patch or physical TalkBack result. A later failed startup wait was retained rather than treated as a configured widget.

## Reproducibility and unfinished checks

The [upgrade protocol](CLOUD_UPGRADE_RESTORE_ACCEPTANCE.md) and [profile/widget protocol](CLOUD_PROFILE_WIDGET_ACCEPTANCE.md) describe guarded fixtures and boundaries. The machine summary records exact build commands with workspace prefixes normalized, known artifact hashes/source revisions, actual retention instrumentation flags, method names and resource environment. Main upgrade CLI native bounds are300s/assertion and600s/reboot; manual multi-step software UI calls used600s, profile assertions300s and the later creation retry300s. Bounded installation/preflight failures remain failures even if later bytes verified. Same-version fixture refresh is not another version upgrade.

GitHub Actions previously failed to start because of account budget. It proves neither a code failure nor a passing build. Current PR checks must be inspected separately.

Remaining: passing corrected full Cards editor on API36 and physical TalkBack, reliable actual reboot/first unlock, passing fixed SAF picker, genuine configured external-widget consent/restart/removal/rebind/fresh restore, complete managed transitions, actual API35+ private exclusion, representative OEMs, folding/tablet/external-display layouts, scaling/RTL/navigation/refresh variants, startup/frame/memory/battery/soak acceptance and licensing/distribution gates. No production release gate is marked green.


## Review follow-up — 2026-10-15

Reviewed parent: `b2cd8d6e492a4e4a2213379a5459dd0028d80dab` (PR #4). The two review findings
were acceptance-harness defects, not evidence of a new production-device failure.

- Profile timeout/crash could previously report cleanup PASS after checking only profile IDs,
  while native finally restoration of HOME/onboarding had not completed. An on-device quarantine
  marker is now armed before mutations. Unknown/failed native outcomes retain it, report cleanup
  FAIL and unsafe device reuse, and avoid cleanup races. The three host runners refuse later use;
  discard and recreate the disposable AVD. Only fully passing native restoration assertions and
  confirmed profile cleanup release it. This is fail-unsafe quarantine, not automatic restoration.
- External-widget phases now wait for genuine HOME startup stability before closing successful
  fresh/recreated activities, without resetting the production crash-loop guard.
- Fresh host checks: **79 Python parser/profile/upgrade tests passed**, including actual subprocess timeout and
  crash through runner.main, host interruption, restoration failure, retained quarantine/new-client
  refusal, cross-runner refusal, guard failures and successful release. Python compilation also
  passed. These synthetic checks do not establish Android runtime behavior.
- No emulator, physical device, Android SDK/build or paid Actions rerun was used for these fixes.
  JDK 21 is present, but this environment has only a connector-fetched source subset and no Android
  SDK/toolchain. Native fixture compilation/execution remains unavailable in this follow-up.

**Release remains NO-GO.** Reboot/first unlock, SAF, complete configured external widgets,
quiet/resume/removal, API35+ exclusion and physical/OEM/accessibility checks remain open. The
2026-10-14 evidence and counts above are historical results, not reruns of this follow-up.
