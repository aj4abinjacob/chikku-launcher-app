# Repository instructions

## Project state and reading order

An M0 Android launcher codebase (Kotlin, Gradle) is being bootstrapped on top of the research documentation. Read README.md, docs/PRODUCT_REQUIREMENTS.md, docs/ARCHITECTURE.md, docs/DATA_MODEL.md, docs/ROADMAP.md, docs/TEST_STRATEGY.md and docs/decisions before coding.

The user's task determines whether to begin implementation. A roadmap's presence is not authorization to automatically publish an application.

Dated evidence supplements live under docs/research. The 2026-10-10 Nova supplement uses NS/NF/NP/NU/CV identifiers; they must not resolve to the main source/feature identifiers.

The main architecture and build-vs-fork ADR are provisional. Use evidence from the feasibility spike to accept or revise them. A supplementary recommendation does not silently override the main decision.

## Product invariants

- Independent Chikku branding and properly licensed source/assets.
- Offline core without ads, advertising SDKs, compulsory accounts or subscriptions.
- Component and profile identity; labels or package name alone are not sufficient persistent identity.
- Locked private-profile contents absent from all derived launcher surfaces and diagnostics.
- Optional permissions requested only when the relevant feature is enabled.
- Cosmetic hiding is not app security.
- Recoverable edits, upgrades and restore; never silently reset damaged layouts.
- Widget provider loss leaves a resolvable placement; restore uses fresh widget IDs.
- No universal stock Recents/gesture-animation promise for a normal non-root app.

## Backup and restore coverage (core, applies to every feature)

Backup/restore (Settings → Backup, and "Restore from a backup" on the first onboarding step) is a
core part of Chikku, not a feature that is finished once. Every new feature that persists user state
must keep it working:

- **Covered or excluded on purpose.** New persisted user state is either written to the backup or
  listed, with the reason, under "Excluded from backups" in docs/DATA_MODEL.md. Silent omission is a
  defect.
- **Settings fields** (`LauncherSettings`): give the field a default, add it to
  `LauncherSettingsCodec` `toMap` *and* `fromMap`, and to `roundTripPreservesEveryField`.
  `everySettingsFieldIsWrittenToBackups` fails CI if a field is not written.
- **Room tables/columns or other stores**: extend `ChikkuBackup`, `BackupCodec`, the `BackupService`
  checkpoint/apply steps and recovery, `BackupSummary` if the user should see it in the preview, and
  `BackupRestoreRoomTest`.
- **Device-local references** (package names, content URIs, widget IDs, companion apps such as the
  Chikku Discover bridge, icon packs): never assume they exist after a restore, which can happen on a
  fresh phone before anything else is installed. Keep the restored value, and add a `RestoreCheck`
  plus a `RestoreAttention` value (core/model `backup/RestoreChecks.kt`), answered by
  `AndroidRestoreEnvironment`, so the preview and the post-restore card explain it once and link to
  the setting. Never silently reset or turn a feature off because a dependency is missing.
- **Compatibility**: older backups must keep decoding (missing keys fall back to defaults). Bump
  `BackupCodec.FORMAT_VERSION` only for a breaking change, and migrate the old meaning on decode.
- **Privacy**: backups and restore checks never contain or log app labels, locked/private-profile
  contents or app lock state.

## Settings live previews (applies to every setting)

A setting whose effect is not obvious from its name alone (sizes, spacing, colours, shapes, order
or choice of buttons, layout or presentation modes, anything a user would have to leave Settings to
understand) needs a live preview on its settings page:

- **Shows the real thing.** Draw the change with the launcher's own views/composables where
  practical, not a description of it, and update immediately: while a slider is dragged, and on
  every toggle, swap or reorder. The saved value can still be written on release.
- **Synthetic data only.** Sample apps, contacts and icons; never real contacts, app labels, app
  icons, notifications or other personal data, so screenshots of Settings are safe to share.
- **Stable layout.** Pin the preview above the page's scrolling settings and keep its size
  steady, so controls don't move under the finger. Respect dark mode, RTL and font scale.
- **Accessible.** One TalkBack description stating what the preview currently shows; interactive
  sample content must not be focusable or launch anything.
- **Tested.** Add a test that the preview follows the setting (see `ContactCardPreviewTest`), and
  record unavailable device checks as such.

A setting with no preview must be self-explanatory from its title and summary; if a reviewer has
to ask what it does, add one.

## Working process

Inspect the working tree and preserve concurrent work. Establish actual toolchain/build commands from the selected foundation. Record exact upstream revisions and inspect per-file/dependency/asset licences and notices before copying code. Current Lawnchair's root notice alone does not constitute a full dependency audit.

Keep platform boundaries explicit. Test real workflows and relevant failure modes. Unit mocks, emulator tests, physical-device runs and screenshots are different evidence. Report unavailable checks; never invent a passing device test or benchmark.

Use the owning-layer evidence to separate app defects from platform limitations. Verify mutable Android/store facts when implementing.

Keep requirements, architecture, backlog, compatibility claims, ADRs and changelog consistent. Follow protection and required checks when configured. Never force-push over another contributor's work.

## Documentation verification

Check relative links, source namespaces, table structure, evidence dates and proposed-versus-implemented wording.


## Codebase layout (M0)

    :core:model   Pure Kotlin/JVM. Domain types (AppKey, Workspace*), repository contracts,
                  WorkspaceLayoutEngine, app search. No Android imports.
    :app          Android application. Packages under io.github.aj4abinjacob.chikku:
                    platform/  LauncherApps, icons, launching, HOME role (Android boundary)
                    data/      Room database, DAOs, WorkspaceRepository implementation
                    home/      HomeActivity, View-based workspace, dock, drawer, recovery UI
                    settings/  Compose onboarding/settings
                    AppContainer.kt  manual constructor-injection root

Extract further modules (see docs/ARCHITECTURE.md) only when a boundary is earning its keep.

## Build and test commands

Use Android Studio's bundled JDK if no system JDK 21+ is installed (Robolectric needs Java 21 for
the SDK 36 unit tests; the code still compiles to Java 17 bytecode):

    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    ./gradlew assembleDebug            # debug APK: app/build/outputs/apk/debug/
    ./gradlew :core:model:test         # pure Kotlin unit tests
    ./gradlew testDebugUnitTest        # app unit tests (Robolectric, Room)
    ./gradlew lintDebug                # Android lint
    ./gradlew connectedDebugAndroidTest  # needs an emulator/device
    scripts/check-privacy.sh           # manifest permission + tracking-SDK gate (also in CI)

On MIUI devices, Gradle's connected task uninstalls the app and loses the per-app
"background pop-up" permission that instrumentation needs. Install with `adb install -r` (app and
androidTest APKs), then run `adb shell am instrument -w <appId>.test/androidx.test.runner.AndroidJUnitRunner`.

`local.properties` (gitignored) must point `sdk.dir` at an SDK with platform 37 installed.

## Coding conventions

- Kotlin only for new code; official Kotlin style; immutable data classes for state.
- Constructor injection wired in AppContainer; no global mutable singletons.
- Coroutines with structured scopes (lifecycleScope/viewModelScope/injected scopes). No GlobalScope.
- No disk, package-manager or database work on the main thread.
- Platform calls return typed results (LaunchResult, EditResult) instead of throwing into UI.
- Never log app labels, package inventories, search queries or profile details.
- Strings in resources; content descriptions on every interactive view; respect font scale.
- No new permission, exported component or dependency without a stated reason in the PR.

## Agent roles and file ownership

When work is split across agents, each owns disjoint files; the orchestrator owns integration
(AppContainer.kt, build files, version catalog, AGENTS.md, ADRs) and merges.

| Role | Owns |
|---|---|
| Architecture lead / orchestrator | build files, gradle/, core/model contracts, AppContainer, ADRs, AGENTS.md |
| Platform & launcher APIs | app/.../platform/, core/model search |
| Launcher UI & interaction | app/.../home/, app/.../settings/, layouts/resources, manifest activities |
| Data, state & persistence | app/.../data/, app/schemas/, core/model layout engine |
| QA & performance | app/src/test, app/src/androidTest, core/model/src/test (shared, coordinate) |
| Security, privacy & compatibility | review only; findings go to the owning role |
| DevOps & documentation | .github/, CHANGELOG.md, README.md, docs/ (except ADRs) |

## Workflow

Branch per change (feat/..., fix/..., test/...), small commits, run the build/tests above before
committing, and record what was verified on which device/emulator. Merge to main only after the
orchestrator has reviewed the integration.
