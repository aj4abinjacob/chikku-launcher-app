# QA device matrix — updated 2026-10-14

## Disposable local Play preparation — 2026-10-15

Mac16GiB/10logical CPUs; one Gradle or emulator process at a time. New AVD
`chikku_play_preparation_20261015`, ARM64 Android16/API36 Google APIs, serial emulator-5590,
two cores/2048MiB, actual4096-byte pages. Five corrected Cards editor tests pass; optimized
APK privacy/native-reader and consent/refusal/handoff smoke pass. No service grant, physical
TalkBack, OEM,16KB-runtime or full reliability acceptance. AVD shut down after testing.
See [current results](QA_RESULTS.md#play-preparation-verification--2026-10-15).


## Newly executed cloud environments — 2026-10-14

| Environment | Configuration | Evidence | Limits / status |
|---|---|---|---|
| Cloud container | Ubuntu24.04/x86_64/Linux6.18.44; 8-core quota,8GiB memory,32GiB disk; no KVM/VMX/SVM | Serial Gradle2workers/Xmx2g;224+949 fresh JVM,71 Python, debug/lint/privacy checks | Final unexplained environment reset lost raw local artifacts; curated evidence survives |
| Disposable API36 | Google APIs x86_64 revision7; TCG/Swiftshader; requested2048/effective2560MiB | Attempted first; boot failed after481.46s; zero APKs/tests | Native UNAVAILABLE; no new API36 or Private Space result |
| Disposable API29 | Google APIs x86 revision13; fingerprint `google/sdk_gphone_x86/generic_x86:10/QSR1.211112.011/13135432:userdebug/dev-keys`;720×1280/320dpi;1536 requested/2048 effective MiB;TCG2cores | Cards required checks, actual development APK upgrade, service restore/Undo/interruption recovery, separate cold-boot retention and active work identity passed | Reboot/SAF/profile transitions/external configured widget checks failed/incomplete; assumption skips separate; no secure-lock/performance acceptance |
| Physical Samsung/OEM/TalkBack | No cloud physical device available | No newly executed physical result | UNAVAILABLE; earlier dated results below are history |
| Tablet/foldable/external display/battery | No representative cloud hardware available | No newly executed hardware result | UNAVAILABLE; emulator coverage is insufficient |

Reviewed code `8160cd7a715297b8332a31c790a9015685239f24`; exact per-run revisions/APK versions/
hashes and native results are in the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) and its curated JSON. API29 synthetic scenarios
used isolated acceptance IDs and positively owned profiles. No real user installation or
personal-device state was changed. Final insecure guest preparation and install-time ART verify
with adbd unrooted were fixture setup, not secure-lock/performance evidence. Refresh/theme/locale/
navigation combinations were not fully recorded or exercised, so those dimensions remain open.

## Historical matrix — 2026-10-12

This matrix separates available evidence from planned coverage. Device or emulator availability does not itself indicate a scenario was executed. The branch is `test/production-reliability`, base `23bd9dff7de46f83505ca52c006d158967d5e6ca`; the final unit run includes the reliability patch. The initial 8/8 native recovery baseline predates the layout fix; later process-death and recovery/migration fixtures ran on the rebuilt APK and are recorded separately. Physical isolated fixtures exercised synthetic recovery while preserving the existing primary-device state.

| Environment | Known configuration | Evidence recorded | Scope / limits | Status |
|---|---|---|---|---|
| Xiaomi Redmi 9 M2004J19PI | Android 11 / API 30 / MIUI; `POCO/shiva/shiva:11/RP1A.200720.011/V12.5.7.0.RJRINXM:user/release-keys`; RAM 5,894,088 kB; 1080×2340, density 440, font 1.0 | Eight isolated recovery/migration/startup fixtures passed against the already-installed APK; preservation record confirms HOME, original preference/no-backup files, logical DB rows and installed APK unchanged. `build/qa/2026-10-12/physical-isolated-fixtures.log`; `physical-preservation.json`. | Synthetic isolated stores only; no destructive or personal-settings test, HOME change, reboot, real process kill or widget binding. | 8/8 isolated fixture PASS; broad device acceptance OPEN |
| Samsung Galaxy S24 Ultra SM-S928B | Android 16 / API 36 / One UI | Earlier named workflow results in implementation status, each with stated scope | No device operations assigned to this batch | NOT RUN |
| Disposable API 30 emulator | Stock-like baseline; exact image/build/configuration to be recorded by parent | No evidence supplied for this batch | Preferred for destructive install, process-death, backup, migration and data-corruption scenarios | NOT RUN |
| Disposable API 36 emulator `emulator-5580` | Google `sdk_gphone64_arm64` / `emu64a`, API 36 / Android 16, fingerprint `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`; ARM64 Google APIs, 1080×2400, density 420, MemTotal 2,531,868 kB; AVD `chikku_qa_20261012` | Recovery baseline 8/8 before layout fix; rebuilt-APK recovery/migration 7/7; process-death recovery 4/4; native reliability aggregate 32 classes, 82 pass / 2 fail / 2 assumption skips. Final Clarity rerun passed 3/3 with exact dp assertions preserved (`build/qa/2026-10-12/final-native/20261012-232733-212b218f/batch-01-01-SettingsValueClarityDeviceTest.log`). Focused HomeActivity run passed 3/3. The two remaining failures are CardEditor; its final rebuilt-APK rerun reconfirmed both failures. | Disposable storage only. General settings/native fixtures require completed onboarding and stock HOME; the default-HOME role workflow is tested separately. No primary-device destructive or personal-settings tests. | Partial; native acceptance OPEN |
| Samsung / OnePlus / Nothing / Oppo / Vivo | Physical OEM variants | No evidence supplied for this batch | Required for OEM claims; unverified until named workflow evidence is attached | NOT RUN |
| Tablet / foldable / external display | Form-factor-specific | No evidence supplied for this batch | Conditional where scenario requires hardware; use emulator only for layout simulations | NOT RUN |

For every executed environment, record OS build fingerprint, Chikku commit and build type, RAM, display size/density/refresh, navigation mode, font/display scale, theme, locale, date, test IDs, result, evidence path and cleanup/rollback. The latest API 36 aggregate totals 82 pass / 2 fail / 2 assumption skips across 32 classes. SettingsValueClarity passed 3/3 on the final rerun after a scroll-fixture correction that retained exact dp assertions. The remaining failures are the two CardEditor tests; WebSuggestion is blocked and media-session observation was assumption-skipped. General fixtures require complete onboarding and stock HOME; DefaultHomeDeviceTest is separate. The physical isolated run reported 8/8 and preserved the installed APK and original user state (`physical-preservation.json`). Physical run does not prove other OEMs or API levels. Emulator runs do not prove real package/profile/provider integration.

Coverage dimensions from the brief remain open unless a named run records them: API 29/30–36 supported range, 3-button and gesture navigation, 60/90/120 Hz, low/high memory, small/large displays, dark/light, scaling, battery saver, reduced motion, portrait/landscape, locales, and locked/unlocked/rebooted states. API 29 is the configured minSdk. The Redmi was limited to the explicitly isolated synthetic fixtures; no destructive or personal-settings test was run.
