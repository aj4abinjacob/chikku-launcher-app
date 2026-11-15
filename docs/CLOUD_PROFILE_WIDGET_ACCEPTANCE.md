# Cloud profile and widget acceptance

## Executed cloud status — 2026-10-14

See the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md). Genuine API29 managed creation and fresh unprivileged personal/work
identity passed; complete quiet/resume/removal assertions remain open after setup failure and
bounded timeouts. Profile-user cleanup was corrected from tracked-set-only proof to exact creation nonce/
asynchronous removal/user-list baseline verification. That did not prove HOME/settings restoration
after interrupted native execution; the 2026-10-15 review correction below closes that false-green path. Known private/stale snapshots fail closed in
source; actual API35+ private exclusion is unavailable. Private Space UI remains unsupported.

The pinned genuine Fossify FOSS APK below was downloaded, SHA/certificate/metadata verified and
installed; production session seed passed. Initial real consent failed on an Android10
all-window accessibility traversal (null Provider.id), so revised fixtures use the actual active
window/resource and ACTION_CLICK. A later provider startup wait failed before assertions; final
reviewed installation/execution was unobserved after the cloud reset. There is **no passing
configured external-widget result**. Service restore and SAF remain separate.

Eight phased methods in `ExternalWidgetAcceptanceDeviceTest` cover seed, decline/accept/config,
actual hosted TextClock/options, provider/launcher process restart, package loss, fresh-ID rebind,
portable production export and fresh-install restore/rebind. They never grant BIND_APPWIDGET or
substitute coordinate taps. Compile availability is not acceptance. Tests must record actual
provider instance/options/PID changes and rebind consent/config completion separately from the
unbound synthetic reference used by the upgrade runner.

This is an execution guide and an evidence boundary, not a passing device result. Newly executed
checks and environmental blockers belong in [QA results](QA_RESULTS.md) and the
[device matrix](QA_DEVICE_MATRIX.md). Use cloud emulators only for this batch; no personal device,
remote Mac connection, existing launcher installation or existing profile may be reset.

## Review corrections — 2026-10-15

The profile runner now arms an on-device quarantine marker before the first mutation. A host
crash, native timeout/crash, incomplete result, assertion failure or failed restoration cannot
report cleanup PASS. Uncertain native execution is not force-stopped and treated as restored:
no racing profile removals are attempted, and the marker remains. The profile, upgrade/restore
and reliability runners reject this device on later entry, even after serial reassignment or
host restart and even if only user 0 remains. Destroy and recreate the disposable AVD before
reuse; do not manually delete the marker or infer restoration from a force-stop. This is a
conservative discard policy, not host recovery of HOME/settings. No personal device is eligible.
A fully successful native result includes the existing finally checks for previous HOME and
exact raw preferences; only then can confirmed owned-profile removal and the user-list baseline
allow the guard to clear. Failed guard creation/release also fails closed.

The marker is `/data/local/tmp/chikku-acceptance-profile-unsafe`. It is device-local, not an
inventory or preference backup. Continue using a sole orchestrator: this does not introduce a
distributed lock or make simultaneous independent instrumentation workflows safe.

The external-widget fixture now waits for the real HOME startup-stability callback before closing
successful fresh/recreated sessions. It checks the persisted guard rather than clearing it, so
repeated short acceptance phases cannot manufacture a sticky recovery mode.

Fresh host regression results and exact scope are in the
[review follow-up](CLOUD_RELEASE_VALIDATION_2026-10-14.md#review-follow-up--2026-10-15).
These fixes do not establish a configured-widget pass or completed quiet/resume/removal acceptance.
All previously open production release and physical-device gates remain open: **NO-GO**.

## Guarded profile fixture

`scripts/cloud-profile-acceptance.py` requires all of the following before creating a profile:

- An explicit `emulator-<port>` transport, `ro.kernel.qemu=1`, and an AVD name beginning
  `chikku_acceptance_`.
- Current user 0, no existing secondary users/profiles, and API 29 or newer.
- An already installed app/test pair with an exact application ID matching
  `io.github.aj4abinjacob.chikku.acceptance.[a-z][a-z0-9_]{0,47}`. Normal release/debug IDs and
  a `.debug` suffix are rejected. The runner does not install, uninstall or clear the target app.
- Exclusive access to `/tmp/chikku-acceptance-<serial>.lock`, shared with other acceptance runners.
  The profile and upgrade runners take this lock; the reliability runner checks quarantine but
  does not acquire it. The parent orchestrator remains the only owner of Gradle, SDK provisioning and emulator work.

After the parent builds/installs the isolated pair, run sequentially:

```sh
python3 scripts/cloud-profile-acceptance.py \
  --serial emulator-5554 \
  --package io.github.aj4abinjacob.chikku.acceptance.profiles
```

The runner creates a real managed profile using Android's shell provisioning command, installs
only the synthetic acceptance app into it, and starts it without credentials. The native fixture
checks the same Settings activity component under two different user serials. API29–34 quiet
setup uses the public `UserManager.requestQuietModeEnabled` path from a temporary foreground
acceptance HOME with completed synthetic onboarding; previous HOME and raw settings are restored
in `finally`. API35+ temporarily adopts only MODIFY_QUIET_MODE for setup, then drops it before
fresh own-UID enumeration; all setup permissions must be denied at reads. Those later APIs remain
native unavailable. No persistent permission is granted.

Validate both temporary/prior HOME components before mutation; an unresolved chooser is refused.
Register an exact unique in-memory creation nonce before issuing create. If response/start times
out, reconcile only that positively owned identity; pending, ambiguous or unowned extra users
cannot produce cleanup PASS. Wait for asynchronous removal and freshly compare all user IDs with
the preflight baseline. Creation can commit after a client timeout, so an empty tracked-owned
set alone cannot pass cleanup. Names/IDs/details are not added to reports.

Each stage starts a new adapter and enumerates under the application's own UID. A retained
repository snapshot or an enumeration performed while setup permissions are adopted cannot pass
the fixture. Assertions and reports contain booleans/counts, not inventories, labels, queries,
profile names, private user serials or raw native stacks. Regular instrumentation discovery
assumption-skips this explicitly orchestrated class; that skip is not acceptance evidence.

API 35+ additionally creates a synthetic private-type profile, installs the same synthetic app,
and checks exclusion while it is running and after stopping it. This tests the deliberately
unsupported milestone boundary. It does **not** exercise the actual Private Space lock/unlock,
credential setup, UI container or hidden-profile permission/HOME role combination. API 29 has no
public secondary-profile type query: an accessible noncalling profile remains `OTHER`, and the
fixture checks that documented behavior without inventing Private Space support.

Public platform contracts: [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps)
returns only accessible profiles and requires hidden-profile permission plus HOME role for
Private Space; [work profiles](https://developer.android.com/work/managed-profiles) use separate
user identities. Shell-created managed profiles are not a DPC/enterprise enrollment test.

`ProfilePrivacyBoundaryTest` covers source policy for stale/reused serials, changed classification,
paused/locked/inaccessible state and private/calling-user exclusion. Production
`AndroidAppsPlatformSource.activities` now rechecks serial, classification and availability before
reading labels and before returning them. This is defensive source hardening; a real private-data
leak has not been reproduced by this batch.

## Widget evidence must remain separate

| Provider/workflow | Existing evidence | What it establishes | What remains open |
|---|---|---|---|
| Test-only `ReliabilityWidgetProvider` | `WidgetLifecycleDeviceTest`; earlier dated device results | Native host/RemoteViews/options, listening restart, component disable, retained synthetic placement and fresh-ID rebind in an in-memory Room repository | User consent, provider configuration, provider-package uninstall, external process restart and production persisted placement |
| Unconfigurable stock widget | Earlier picker visual/clock checks with the named limits in implementation status | The recorded stock provider/picker workflow only | A configuration Activity workflow cannot be inferred from a provider with no configuration Activity |
| Genuine configured external provider | No passing result in this guide | Nothing inferred from the two rows above | Actual consent, completed configuration, process restart, package removal/reinstallation, retained placement and fresh-ID rebind |

Source review found these existing guards: widget catalog filters allowed serials and fresh profile
availability before provider labels and again before returning groups; binding requires an exact
component/profile match after consent/configuration; rebind compares the previous target inside
the repository transaction; uncertain/unreadable storage defers ID deletion; ordinary/durable Undo
references retain IDs; portable backups restore fresh IDs. Source review is not external-provider
runtime evidence.

For genuine external acceptance, the parent should obtain a licensed provider APK separately,
record its exact package/version/hash and declared configuration Activity, and install it only on
the disposable emulator. A provider from Chikku's target or test APK cannot satisfy this case.
Record each result separately:

1. Begin through Chikku's real widget picker with bind permission initially absent. Accept or
   decline the system consent using text/content-description/resource selectors; confirm that
   decline leaves no placed widget or owned orphan. A bind-permission shell grant bypasses this
   acceptance case.
2. Complete the genuine provider's configuration Activity and observe its result. Verify the
   configured content is hosted, can be resized on supported axes, and survives leaving HOME,
   launcher force-stop/relaunch, provider restart and emulator reboot/unlock.
3. Remove the disposable external provider package. Confirm the existing production placement,
   component/profile identity, span and options remain recoverable. Reinstall the same provider,
   rebind with a fresh widget ID, repeat required configuration, and verify the retained placement.
4. Export/import and restore on a fresh acceptance installation. Validate fresh widget IDs and
   user consent/configuration as **widget rebind acceptance**. Keep this distinct from service-level
   backup assertions and from SAF document-picker selection/result/grant handling.

Private Space remains unsupported. Remaining physical checks include TalkBack focus/action order,
48dp targets and font/RTL presentation around real external widgets; Samsung/other-OEM bind/config
and launcher-restart behavior; foldable/tablet posture and display changes; navigation/Recents
limitations; startup/frame/memory/battery workloads. A software emulator cannot establish those
hardware or OEM results.

## External provider selected for the disposable acceptance run

Primary-source lookup on 2026-10-14 selected **Fossify Clock, FOSS release 1.6.0 (versionCode 10)**.
The candidate lookup below preceded execution. The cloud report subsequently records a verified
download and installation; configuration/consent/restart/rebind did not obtain a passing result.

| Property | Pinned value / source |
|---|---|
| Maintainer release | [FossifyOrg/Clock 1.6.0](https://github.com/FossifyOrg/Clock/releases/tag/1.6.0), published 2026-01-30 |
| Source commit | `4ecb9e33c51c0b2096af2379e7ac9dc17c3e07e6` |
| APK | [clock-10-foss-release.apk](https://github.com/FossifyOrg/Clock/releases/download/1.6.0/clock-10-foss-release.apk), advertised size 9,110,265 bytes |
| Advertised SHA-256 | `43cf9f0ec45f1f1ff2df47286622e5b8f3acedaeb07b7f7641b5243dbedca079` from the [maintainer release API](https://api.github.com/repos/FossifyOrg/Clock/releases/tags/1.6.0); compare actual downloaded bytes before installation |
| Application identity | `org.fossify.clock`; version fields in [tagged gradle.properties](https://github.com/FossifyOrg/Clock/blob/1.6.0/gradle.properties) |
| Digital provider | `org.fossify.clock.helpers.MyDigitalTimeWidgetProvider`, declared in the [tagged manifest](https://github.com/FossifyOrg/Clock/blob/1.6.0/app/src/main/AndroidManifest.xml) |
| Configuration Activity | `org.fossify.clock.activities.WidgetDigitalConfigureActivity`, exported for `APPWIDGET_CONFIGURE` |
| Configuration/resize metadata | [widget_digital_clock_info.xml](https://github.com/FossifyOrg/Clock/blob/1.6.0/app/src/main/res/xml/widget_digital_clock_info.xml) declares `android:configure`, both resize axes and no optional-configuration flag |
| Actual configuration completion | [Activity source](https://github.com/FossifyOrg/Clock/blob/1.6.0/app/src/main/kotlin/org/fossify/clock/activities/WidgetDigitalConfigureActivity.kt) wires `org.fossify.clock:id/config_digital_save` (`@string/ok`) to save colors, publish a widget update and return `RESULT_OK` plus the supplied widget ID |
| Platform levels | minSdk 26, target/compileSdk 36 in [tagged version catalog](https://github.com/FossifyOrg/Clock/blob/1.6.0/gradle/libs.versions.toml) |
| License | [GPL version 3 license text](https://github.com/FossifyOrg/Clock/blob/1.6.0/LICENSE); [F-Droid metadata](https://f-droid.org/packages/org.fossify.clock/) identifies GPL-3.0-only |

The maintainer's [Clock privacy policy](https://www.fossify.org/policy/clock/) states offline
operation without Internet permission or an account/cloud service. Use the **FOSS asset** above:
its [flavor resource](https://github.com/FossifyOrg/Clock/blob/1.6.0/app/src/foss/res/values/bools.xml)
sets `pretend_thank_you_installed=true`; the referenced Commons 6.1.0 helper accepts that value,
avoiding the configuration screen's optional Thank You app gate. No provider code, assets,
library or permission is incorporated into Chikku. Inspect the downloaded APK's package, version,
certificate, merged permissions and provider metadata before the parent installs it on the
disposable emulator. If the emulator/provider workflow is unavailable, keep consent/configuration,
restart/removal and rebind rows open.

