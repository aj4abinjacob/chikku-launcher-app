# Android audit fixes and validation — 15 October 2026

Baseline: `eb8d59e0b2f4e723865ecc5bc901b16268d787de` (main after PR #4).
Scope: the ten findings from the repository-wide review; no merge or release authorization.

## Fix and regression map

| Finding | Boundary repaired | Targeted regression |
|---|---|---|
| PS-1 | Guarded Store/Discover/assistant/voice dispatch, exact authentication retry, STARTED deferral | AuditExternalLaunchGuardTest, AuditExternalActionsRetryTest |
| PS-2 | Non-consuming fail-closed shortcut metadata reads and live lock invalidation | AuditShortcutMetadataPrivacyTest, AuditCardsLockInvalidationTest, AuditPickerLockInvalidationTest |
| UI-4 | Shared widget allocation/commit/sweep ownership and pending reservations | WidgetRecreationAuditTest |
| D1 | 256-page shared supported cap, truthful export, independent private recovery decode | BackupBoundsAuditTest, JournalBoundsAuditTest, PageEditRecoveryAuditTest |
| UI-1 | Idempotent desktop/dock drag termination and lifecycle cleanup | HiddenDockDragAuditTest, HiddenDockNativeAuditDeviceTest |
| UI-3 | Session-scoped contact lookup/actions, cancellation and credential-resume ownership | ContactSessionOwnershipAuditTest |
| UI-2 | Query-owned drawer results and IME freshness gate | DrawerQueryFreshnessAuditTest |
| UI-5 | Quick start uses normal layout-lock/hidden-placement warning flow and synthetic preview | QuickStartHiddenPlacementAuditTest, QuickStartLayoutAuditDeviceTest |
| PS-3 | Bounded isolated blocking work, prompt UNKNOWN deadlines and late-result rejection | BlockingRestoreDeadlineAuditTest, AndroidRestoreEnvironmentTest |
| D2 | Conditional default-page Undo plus durable cross-store rollback preserving snapshots | PageEditRecoveryAuditTest |

## Validation status

Final combined source, including the approved Nose notch Ink adaptive/themed icon, was checked
with JDK 21, Gradle 9.7.1, Android platform 37 and build tools 36.0.0:

- Full fresh `:core:model:test testDebugUnitTest`: **228 core + 1,014 app tests passed**,
  zero failures, errors or skips. Tests ran serially with bounded workers.
- `assembleDebug assembleDebugAndroidTest lintDebug`: **passed**. Debug app/bridge and
  native-fixture APKs compiled. Android lint: app zero errors, 169 warnings and one hint;
  bridge zero errors and three warnings. The base icon's monochrome warning coexists with
  an explicit API-33 resource override, verified by the SDK-33 resource regression.
- `scripts/check-privacy.sh`: **passed**; four forbidden permissions absent from two merged
  manifests, 49 prohibited-SDK rules checked against 113 resolved modules.
- Python host regressions: **79 passed**. `git diff --check`: clean.
- All 24 added Kotlin files pass pinned ktlint 1.8.0. Existing edited files retain advisory
  body-formatting debt; their baseline versions already reported 3,316 diagnostics. Import
  blocks were normalized without body changes. This is not a repository-wide ktlint pass.

Earlier diagnostic runs found fixture and real lifecycle/Compose issues; those were corrected
before the fresh complete runs above. One earlier combined lint/test invocation lost its daemon;
validation was rerun with bounded serial workers. One prematurely started final test command was
immediately interrupted while lint was active; its partial output is not counted as a test result.
No emulator or device was executed. New native fixtures remain **compiled but unexecuted**.

## Interpretation and remaining native checks

Unit fakes and synthetic native fixtures do not establish biometric/device-credential behavior on
real hardware, third-party shortcut-provider confidentiality, actual widget consent/configuration
and HOME recreation, OEM drag routing, a stalled real document provider, TalkBack/RTL/large-font
acceptance, production R8, upgrade/reboot, or performance. Run those separately before release.
App lock remains an in-launcher control, not an OS sandbox. Embedded Google content is withheld
for configured locked owners even while a launch grant exists. Existing oversized private layouts
are retained; bounded portable export can refuse them rather than silently discarding data.

Implementation references: [ContentResolver cancellation](https://developer.android.com/reference/android/content/ContentResolver),
[bounded ThreadPoolExecutor queues](https://docs.oracle.com/en/java/javase/22/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html).
