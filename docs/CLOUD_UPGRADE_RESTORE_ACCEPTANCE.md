# Historical APK upgrade and restore acceptance in the cloud

## Executed cloud status — 2026-10-14

The [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) records the actual API29 historical development upgrade910001→910002,
retained production stores/HOME and service restore/Undo/interruption passes. Bounded reboot
failed; separate cold start passed. SAF failed on an acceptance ViewModelProvider resolution
error; the corrected API keep compiled but fixed native execution was unavailable. API36 boot
failed. The final environment reset lost raw artifacts; do not infer final execution.

Acceptance-only keeps retain Trace/Kotlin/coroutine and Chikku/SQLite/Room/ViewModelProvider
public/protected APIs for separately compiled instrumentation while allowing body optimization.
This is nondebuggable/minified acceptance, not byte-equivalent production shrink/size/performance
evidence. Normal release rules/signing/versions and Room4/backup format1 are unchanged.

The reviewed host persists primary failure before cleanup, always terminates its local client
and refuses subsequent recovery instrumentation when remote cleanup is unconfirmed. Exactly one
completed method with zero assumptions is required. An incomplete fixture APK update must fail
byte verification; a same-version refresh is not a second upgrade.

This is a guarded acceptance protocol, not a passing run or production-readiness claim. Actual
results, exact commands and unavailable checks belong in [QA results](QA_RESULTS.md).
The runner consumes already-built APKs and never starts Gradle or an emulator. One orchestrator
must serialize all builds and native work. The shared `/tmp/chikku-acceptance-<serial>.lock`
prevents the upgrade and managed-profile runners from using the same emulator concurrently.

## Verified source baseline

Historical development revision `f69121e08e04c64d0c3541304484893877c9788f` declares Room schema
3 and has no per-item `widgetPadding`. Current main at
`1c081bb9fdd7df4d854d2de7e31ba8e8128587d4` declares schema 4 with the additive nullable column.
The historical source was retrieved at that exact revision; the shared seed fixture deliberately
uses its own existing repository/settings APIs. In particular, historical search used
`frequentAppsEnabled`; the current-only verifier checks the replacement's default-off history
field. This is a **historical development-APK upgrade**. No published production APK has been
identified as its starting point.

Copy only `app/src/androidTest/java/io/github/aj4abinjacob/chikku/acceptance/HistoricalUpgradeSeedDeviceTest.kt`
and the guarded acceptance build configuration into a separate historical checkout. Compile the
seed against the historical checkout's classes; using a current-compiled instrumentation APK
against changed historical Kotlin constructors is not a valid upgrade test. The current test APK
contains both the historical seed helper and `UpgradeRestoreAcceptanceDeviceTest`.

## APK preparation

Use a disposable keystore **outside** both repositories. Set `CHIKKU_ACCEPTANCE_KEYSTORE` and
`CHIKKU_ACCEPTANCE_STORE_PASSWORD`; optional `CHIKKU_ACCEPTANCE_KEY_ALIAS` defaults to
`chikku-acceptance`, and `CHIKKU_ACCEPTANCE_KEY_PASSWORD` defaults to the store password. Never
commit the key or expose passwords in recorded command lines. Use the same key for all four APKs.

The following is an example preparation command, not executed evidence. Run it first in the
historical checkout, then sequentially in the current checkout with code `910002` and a new name:

```bash
./gradlew --no-daemon --max-workers=2 \
  -Dorg.gradle.jvmargs='-Xmx2g -XX:MaxMetaspaceSize=768m' \
  -Pkotlin.compiler.execution.strategy=in-process \
  -PchikkuTestApplicationId=io.github.aj4abinjacob.chikku.acceptance.upgrade \
  -PchikkuTestVersionCode=910001 \
  -PchikkuTestVersionName=historical-schema3-acceptance \
  -PchikkuAcceptanceRelease=true \
  :app:assembleRelease :app:assembleReleaseAndroidTest
```

Heap sizing remains the orchestrator's decision based on actual available memory. Acceptance
release keeps R8/minification and a nondebuggable application, changes `testBuildType` only with
explicit opt-in, and signs the application/test APKs using the disposable configuration.
The acceptance instrumentation APK alone suppresses a missing JDK-only compiler-model enum used
by compile-time Truth/Error Prone annotations; production R8 rules are unchanged. Normal
IDs, versions, debug suffix and release signing remain unchanged. An acceptance version/signing
override without an isolated `io.github.aj4abinjacob.chikku.acceptance.<scenario>` ID is rejected.

## Disposable emulator workflow

Start one fresh API 36 AVD named `chikku_acceptance_upgrade`, with only system user 0 and no existing
scenario app/test registration, including retained package state. No physical transport is
permitted. API 29 is an additional independent scenario, using its matching AVD/ID and `--api 29`.
The runner verifies actual QEMU state, AVD name, API, user and APK identity before installation.
Native fixtures independently verify QEMU, system user, unlock, isolated package, installed
version, expected schema and nondebuggable status before opening production store names.

Example invocation (replace all artifact paths and the current revision with recorded values):

```bash
python3 scripts/run-upgrade-restore.py \
  --serial emulator-5554 --avd-name chikku_acceptance_upgrade --api 36 \
  --application-id io.github.aj4abinjacob.chikku.acceptance.upgrade \
  --old-apk /tmp/historical/app-release.apk \
  --old-test-apk /tmp/historical/app-release-androidTest.apk \
  --new-apk /tmp/current/app-release.apk \
  --new-test-apk /tmp/current/app-release-androidTest.apk \
  --old-revision f69121e08e04c64d0c3541304484893877c9788f \
  --new-revision 1c081bb9fdd7df4d854d2de7e31ba8e8128587d4 \
  --aapt2 /opt/android-sdk/build-tools/36.0.0/aapt2 \
  --apksigner /opt/android-sdk/build-tools/36.0.0/apksigner
```

The runner checks a strictly increasing application versionCode, expected application/test IDs, test target,
verified matching certificate and nondebuggable application manifests. Generated instrumentation
APKs may omit version fields; these are recorded as unknown rather than fabricated or compared as
application versions. It seeds synthetic
settings, apps/folder/shortcut/widget placement, customizations and drawer groups through the
historical production repositories using **normal production filenames within the isolated ID**.
The widget ID is synthetic and unbound; it proves retained references and portable placeholders,
not widget-provider integration. The seed marks default-dock initialization complete without
reading a real inventory.

It replaces the historical APK using `adb install -r` without uninstall/downgrade, checks installed
version and migration/sentinel content, launches the explicit HOME component/category, reboots
the disposable emulator, waits for `RUNNING_UNLOCKED`, and checks persisted state. `am start -W`
`Status: ok` establishes a completed activity launch; it does not establish rendered interaction,
default-HOME role persistence, OEM gesture behavior, startup/frame timing or physical acceptance.

It then exports a full synthetic portable backup, records its host-file SHA-256, removes only its
own newly created acceptance application, installs the current APK fresh, and imports the staged
archive through `BackupCodec`/production `BackupService`. That transport is a service-level test.
Missing icon-pack/document dependencies must preserve the restored values and return attention;
workspace widgets must return with ID 0. It applies a replacement, externally force-stops, and
verifies durable Undo after reopening. Malformed/truncated/future archives must reject without
store or journal changes.

Finally, it pauses after each actual settings/customization/group/workspace write, following a
durable production journal and synced marker; external force-stop interrupts the target. Only a
fresh-process recovery assertion counts as PASS. Preparation is EXPECTED_INTERRUPTION, not a
passing test. Progress output carries only synthetic PID/stage, so release testing needs no
`run-as`, test-only exported endpoint or production permission.

## Separate SAF and widget evidence

`safDocumentPickerRoundTrip` separately opens the actual Settings Backup page and system
CreateDocument/OpenDocument pickers, saves a uniquely named synthetic document in Downloads,
reads it through the production content resolver, cancels the real preview without changing the
replacement, then confirms and verifies full restored state. It uses named text/description
selectors. English DocumentsUI labels are an explicit fixture prerequisite; an unavailable picker
or unmet prerequisite is SKIPPED/BLOCKED, never PASS. Picker controls or unexpected UI failures
remain recorded failures requiring investigation, not silent coordinate fallbacks.

External widget consent/configuration/restart/removal/rebind is **NOT_RUN by this runner**.
Follow the separate [provider/profile acceptance protocol](CLOUD_PROFILE_WIDGET_ACCEPTANCE.md)
and label test-only, unconfigurable stock and genuinely configured external providers distinctly.
Neither service restore nor successful SAF selection proves that a real provider was rebound.

## Evidence and cleanup

Each run records exact APK IDs, codes/names, certificate/byte hashes, source revisions plus the
current source-diff fingerprint, emulator fingerprint/display/memory, commands and native parser
results. One expected class/method must report exactly one completed pass with zero skips; a
successful adb exit or an assumption skip does not qualify. Fresh scenarios refuse previously
installed or retained application/test registrations. There is no `adb install -d`, `pm clear`,
production-package uninstall or blanket device cleanup. Successful and failed isolated scenario
state is retained for diagnosis. The uniquely named SAF document is synthetic and also remains
inside the disposable AVD.

Run the host safety/parser tests without Android hardware:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_run_upgrade_restore.py' -v
```

Do not merge these fixtures' availability with executed evidence. Physical OEM/foldable/TalkBack/
battery checks, cross-device user-serial mapping, secure-lock reboot behavior, real configured
widget rebind and distribution/release gates remain separate until named runs establish them.
