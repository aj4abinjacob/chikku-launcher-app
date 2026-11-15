# Source download and remaining acceptance — 2026-10-16

## Current continuation — 17 October 2026

The owner has since approved ownership/publication/exclusions and the second disposable16KB emulator. The approved old source snapshot is public; the actual16KB check passed within the scope recorded in the continuation. Statements below describe the16October state. See [dated evidence](SOURCE_PUBLICATION_2026-10-17.md). Historical sections below retain their dates and scope.

**Preparation only; production and external distribution remain NO-GO.** Both application
repositories remain private. No Play bundle upload, review submission, tester invitation, release
rollout or automatic job was performed. The daily launcher and physical devices were untouched.

## Matching source

The static source route is prepared at `/chikku/source/` in an isolated website worktree; it is
not published. The owner has been asked to approve the concrete page/archive and its12 explicit
historical-art exclusions, and to confirm rights to project-owned code/current branding, including
contributor/employer interests. Ownership of the dog's nose reference alone does not answer those
other questions. The complete private historical archive remains preserved.

The38.7MB source export matches the recorded1840777 preparation candidate, retains1,100 project
files plus84 dependency-source files, and includes current artwork and GeoNames inputs. Firefox
download, independent whole-file checksum, file-by-file Git-blob comparison, executable mode and
clean extracted offline build/static APK inspection passed. The download is split into verified
parts, with manual joining instructions and permanent pages for earlier revisions. See
[source delivery](SOURCE_DELIVERY.md) for exact hashes and the procedure.

About's offline source reader now has the proposed URL, selectable text and an explicit
**Copy source download link** button with accessible success/error feedback. No clipboard read,
browser launch, network request, permission, dependency, setting or portable backup field is added.
The native source-only button/copy/readback/stay-in-app check passed; the privacy reader does not
show the source-only action.

These new changes, the recovery sizing fix below and acceptance-fixture changes are uncommitted
on `feat/source-download-and-acceptance`. The app's weekday
09:00–18:59 Asia/Kolkata commit restriction was respected. The existing signed AAB and source
manifests were not overwritten or relabelled. After an allowed commit, generate and verify a new
clean source/candidate pair and publish its own source entry before distributing that changed app.

## Build and host checks

- Final verification after both product fixes succeeded in3m12s: debug/test/optimized release assembly,
  app unit tests and debug/release lint. App unit execution:1,065 tests, zero failures/errors/skips. The
 230-core-test task was up to date in this invocation; the earlier fresh run remains its evidence.
- Both lint reports: zero errors,170 warnings and one hint. Static release APK inspection and the
  privacy manifest/tracking-dependency gate passed.
- Python regressions:88 passed; browser-client unit checks:4 passed. The latter exercise joined
  bytes, corrupt-part/final-hash rejection and HTTP failure; they are not browser automation.
- Isolated website build passed in4.23s. Local preview and genuine Firefox downloads passed before
  the Mac locked; no public-route/browser verification is claimed.
- Remote Android CI remains unavailable because the GitHub Actions account budget prevents it
  from starting. Local checks are distinct from remote CI.

## Native recovery and actual upgrade

All runs used the explicitly authorized `chikku_play_preparation_20261015`, API36 ARM64,
two cores/2GB RAM. Only one emulator or bounded Gradle invocation ran at a time.

-15 native methods passed: restore recovery2, workspace persistence/corrupt-byte preservation3,
  schema migration/recovery5, test-provider widget lifecycle1, narrow StrictMode startup1 and
  HOME intent resolution3. These synthetic stores/test providers are not third-party acceptance.
- A clean historical development source `f69121e08e04c64d0c3541304484893877c9788f`
  (Room3/code910001) was built with only guarded acceptance build/fixture additions. Installing
  current code910002/Room4 over it retained synthetic production stores. Both APKs were
  nondebuggable and used the same disposable local debug certificate, separate from upload/Play.
- A real `adb reboot` changed the boot identifier; the selected HOME and retained state survived.
  This was a credential-free emulator, not secure PIN/biometric first-unlock acceptance.
- Production backup export, uninstall/reinstall of only the owned acceptance package, fresh
  service restore and restored-state verification passed. Portable synthetic widget references
  returned with ID0, not a reused local binding.
- Real Android SAF export/preview/cancel/restore round trip passed after fixing a stale native
  Downloads control lookup. The original failed attempt remains recorded; the correction
  re-queries the actual root row without coordinate taps or weakened producer/state assertions.
- The packaged graphics-path JNI library initialized and converted conics successfully with
  actual page size4096. This is **not a16KB runtime pass**. Permission for another disposable16KB
  emulator is pending; native/RELRO acceptance remains open.

## Genuine external widget follow-through

The official Fossify Clock1.6.0 APK was checked against the vendor release SHA-256, version10,
certificate and manifest. A separate nondebuggable acceptance.widgets installation was used;
neither existing app data nor a real user's widget was modified. The first run seeded the fresh
synthetic stores, then failed before consent because app R8 had removed an AndroidX method still
called by the separate test APK. A platform pre-draw observer fixes that fixture dependency; a
second revealed the same acceptance-only ABI issue for Lifecycle.getCurrentState/State. Their
contract is now retained only in the opt-in acceptance build. No production shrink rule changed.

One intermediate attempt was assumption-skipped because the emulator package manager could not
parse its newly installed provider after a cold boot. Reinstalling the same verified, owned fixture
without clearing launcher stores repaired that environment. Failures/skips remain preserved.
Real system-consent refusal then passed with unchanged placement/owned-ID assertions. Repeated
short fixture launches also left the target in sticky safe mode; the first visible recovery action
was used and recorded without editing its private preferences. With the non-default banner,
however, recovery controls were clipped and actual scrolling did not expose the exit. Subsequent
configuration acceptance stopped at its stable-HOME precondition; it did not pass configuration.
The original widget refusal pass and every failure/skip remain preserved. Stock HOME is retained
between separate instrumentation stages to avoid incidental target HOME relaunches on cleanup.

## Confirmed recovery layout defect

**P1, code fixed and native recovery verified.** Before a workspace has ever reached Ready,
the dock's CellLayout still has its default five-row grid. Recovery made this dock INVISIBLE,
which kept its large measured height. The non-default HOME banner reduced the remaining area
further, clipping the real **Leave safe mode** or **Retry** controls. The actual API36 screen and
hierarchy are preserved in `widget-safe-mode-clipped.png` and `widget-stock-recovery-*.xml`.
No workspace was reset or deleted.

Two real-HomeActivity regression assertions reproduce this on a360×640dp viewport with the
non-default banner: failed-workspace Retry and safe-mode exit must have their complete measured
touch target visible. Both failed before the product change; after it, all eight focused Home/dock
tests pass. An initial default-HOME fixture did not reproduce the failure; it was corrected to
model the observed non-default banner instead of treating that first pass as evidence.

`DockSurfaceController` now removes the dock's layout reservation while recovery is visible;
HomeActivity supplies the actual Disabled/Failed state. Ordinary loading keeps its existing
invisible reservation, and Ready restores the same dock with its existing preferences/contents.
The new recovery flag is transient view state, not a setting or backup field. No schema, backup
format, permission, exported component or dependency changes. The old1840777 signed candidate
does not include this correction and must not be treated as the final distribution candidate.
Native stable-HOME/configured-provider acceptance is being rerun on a rebuilt opt-in pair.

The preserved nondebuggable acceptance installation was updated without clearing its stores.
With the non-default banner, the actual safe-mode button now has its full touch target inside
the recovery panel. Its real visible action exits to Home, and the subsequent native stability
precondition passes. Screenshot/hierarchy and `recovery-layout-native.json` preserve this
separately from JVM geometry assertions. The widget test then exposed an asynchronous catalog
fixture race: it sought a scroll container during the progress-only frame. The fixture now waits
for the pinned provider or actual scrollable catalog before scrolling; provider matching and
consent/configuration/ownership assertions remain intact.

## Confirmed fresh-binding size defect

**F-04, P1, code fixed and scoped native verification passes.** After the catalog wait correction,
genuine external configuration/hosting, repeated activity recreation/process restart and provider
removal all pass. Provider reinstall performs actual configuration, allocates a different ID and
preserves placement/padding/provider identity and owned-reference checks. Its final size-options
assertion then fails; this attempt remains a failure, not a completed rebind acceptance.

`WidgetCellView` reported size only on a geometry change. If asynchronous binding finishes after
the placeholder's final layout, a fresh ID in the same cell never receives that current size.
The delayed/replacement-binding regression fails before the change, while the initial unmeasured
binding control passes. Both pass in the final1,065-test app run. Attaching a new host view now reports its existing positive measured
dimensions; unchanged repeated attachment remains a no-op, and unmeasured bindings still wait
for first layout. Provider option writes continue through the controller's existing IO boundary.
No placement, ID, portable state, permission or dependency is reset or added.

The failed native installation retains its real newly bound widget and the original marker.
A narrowly guarded continuation may advance that marker only after verifying the current fresh ID,
all retained identity/geometry fields, real hosting/ownership and positive stable options equal
to the original placement. It does not simulate configuration or allocate a replacement. A second
real provider-removal/reinstall cycle and fresh-install backup restoration then exercise the
corrected ordinary rebind path again. All six continuation stages pass: retained-size recovery,
provider removal, real reinstall/configuration/fresh-ID rebind, backup export, fresh-install restore
and actual fresh-ID/configuration after restore. Before reopening Home, the retained failed widget's
four size options were all0; expected min/max dimensions were228×73dp. The ordinary production
handoff restores those exact values, without direct test writes to AppWidgetManager or preferences.
The old failure remains recorded.

The earlier external-provider evidence combines explicit ordered continuations on preserved state.
After both product fixes, a **fresh complete sequence passes all nine native invocations** with
zero failures/skips: seed, genuine refusal, real consent/configuration/hosting, restart/recreation,
provider removal, reinstall/configuration/fresh-ID rebind, export, fresh installation/restore and
fresh-ID/configuration again. Only positively owned synthetic fixture packages were removed for
this new sequence; the earlier backup, failures and recovery reports remain preserved. The corrected rebind
paths exercise real provider configuration, native clock rendering, stable positive options,
same placement/padding/provider identity, different widget IDs and owned-reference integrity.
These opt-in nondebuggable APKs retain acceptance API contracts; normal production R8 size and
Play-delivery identity are separate checks. After completion, the emulator was synced and stopped
with stock Pixel HOME. The restored synthetic widget remains available for inspection.

## Profile boundary follow-through

The existing runner now optionally pins the exact owner-authorized disposable AVD name with
`--expected-avd`. Without this explicit argument, the dedicated acceptance-name prefix is still
required. Exact-name mismatch, malformed names, non-QEMU transport, existing profiles, non-primary
user and quarantine remain rejected before mutation. Four added regressions cover this boundary;
all88 host regressions pass. This permits the already authorized preparation AVD without operating
another emulator or relaxing fresh-profile-group/owned-cleanup safeguards.

All **six native API36 profile stages pass**, each1 passed/0 failed/0 skipped: work-active,
work-paused, work-resumed, work-removed, private-running and private-stopped. Production
enumeration uses a fresh source under the target UID; scoped quiet-mode setup identity is dropped
before reads, and MANAGE_USERS/MODIFY_QUIET_MODE/ACCESS_HIDDEN_PROFILES are asserted denied.
The same component has distinct personal/work identities; paused/removed work and running/stopped
private profiles are absent as expected. Only profiles created by this invocation were removed;
cleanup confirmed the original user set and released the quarantine guard.

This is shell-created synthetic profile/API evidence, not enterprise DPC provisioning, actual
Private Space credential/lock UI or complete derived-surface/OEM acceptance. Private Space support
remains deliberately unsupported in M0. Native summary is under `native-profiles/`; no raw profile
names, package inventory or untrusted native output is retained in its summary.

## Remaining gates and local evidence

Public source approval/ownership, final post-commit source/candidate pairing, owner-secured key
backup/upload-certificate registration, Play-delivered identity and Accessibility-specific Console
declaration remain open. The tester list remains owner-supplied. Do not start a test release or
send invitations implicitly. Existing all178 selectable country/region choices and feedback
contact remain saved.

End-to-end profile UI/credential-lock/DPC/OEM acceptance remains open beyond the six staged native
enumeration checks above. Physical TalkBack/OEM/font-scale/RTL, secure
first unlock, representative dependency-loss layouts and measured startup/frame/battery/soak
checks remain open. No source/unit/emulator result is relabelled as those checks.

Raw evidence is retained outside Git in `chikku-release-evidence/2026-10-16/`: source-download
manifests/integrity/build checks; `source-access-full-verification.log`; recovery/platform reports;
`local-upgrade-restore-report.json`; `remaining-acceptance-report.json`; failed and rerun widget
reports/logs; original/fixed acceptance APKs and their exact historical-source copies. No signing
secret or personal package inventory is included in this report.
