# Latest-main closed-test update — 18 October 2026

The owner requested releasing the update from pushed development main. This update continues the
existing selected Alpha closed test; it does not expand the tester list or publish a production app.
Play Console showed beta2/build3 available to selected testers, two opted-in testers, and no
unpublished changes before preparation.

## Integrated source and compatibility

Development main is pinned at `4ba391fc377af4936bef4b0764cad9cccde99982`. Its complete22-path
change since `53bb644ab4b56112eefaf28ef75b0abc2ec8a0a1` is integrated onto release preparation
`5bd1d1e14e226d5fbf3c6159061ec5723af7f2fa`, preserving MET/offline GeoNames, privacy/legal/source
notices, Google-app-only release handoff, upload signing and release recovery/widget fixes.
Only the two documentation insertions needed manual integration. 15 app/test/resource paths match pushed development main byte for byte; the remaining path
retains only the existing reviewed accessibility-service summary in strings_gestures.xml. Existing development checkouts and main are preserved.

The open-folder title and its synthetic preview now share a smaller dimension. Restored stable
Firefox HTTP(S) shortcut IDs can open their page in the same personal Firefox after Android loses
the pinned intent; native shortcut launch stays first. Opaque IDs receive re-add instructions.
[Recovery behavior and verification limits](../RESTORED_SHORTCUTS.md) remain explicit. URL-based
recovery does not reconstruct a PWA. No new permission, exported component, dependency, persisted
setting, Room migration or backup-format change is introduced. Restored IDs, labels/appearance,
folder membership and placements remain intact; backup format remains1.

Package: `io.github.aj4abinjacob.chikku`, versionCode4, versionName0.1.0-beta.3, min29/target36/
compile37. R8 stays enabled and the app remains nondebuggable. Use the existing external upload
key; Google-managed installed-app signing is a separate identity. The resource/source inventories
are refreshed without changing existing licence declarations or the twelve approved historical-art
exclusions. Earlier signed artifacts and source pages remain available for recovery/reference.

## Verification and distribution record

Local Gradle verification: 231 core and 1113 app test results passed without
failures/errors/skips (Gradle cache/up-to-date results reused where applicable); 88 Python and four browser-client tests passed. Debug/test/optimized release
assembly and debug/release lint passed (zero errors; 171 warnings). Two native API36 ARM64
16KB tests passed: packaged graphics-path initialization/conic conversion and real drawer/folder
rendering with synthetic content. No physical workflow is implied.

Exact test results, signed artifact/source/mapping hashes and Console submission status are retained
outside Git in `chikku-release-evidence/2026-10-18/main-refresh/`. The final candidate manifest
pins the committed source revision and AAB; its public source page is published and verified before
Play submission. A dated Console record distinguishes preparation, upload, review and serving.

Remote Android CI on development main did not start: GitHub's check annotation says an Actions
budget is preventing further use. This is not a passing CI result. Local automated verification
and scoped emulator evidence are recorded separately. No attached physical device was available;
actual Firefox pin/reinstall/SAF restore, real browser/PWA presentation, physical TalkBack/OEM and
broader performance/profile acceptance are not newly established. Production remains NO-GO.

Play's release editor showed Google-managed app signing active, automatic protection off and
automatic app-text translation off. Keep the existing selected Alpha audience and source access;
Google review and tester updates are external steps. This document alone does not claim serving.

## Submitted outcome

Candidate commit `1065ed15d48f08b86748cc1ac97d61a8e5959253` produced the signed code4 AAB
SHA256 `2eb2b364951c4cfd0f2e95a8d1ca688b2830cf428de90eb38350c5ce76e27ad3`.
Its [matching source](https://www.abinjacob.in/chikku/source/1065ed15d48f/) is public; all parts,
joined checksum, remaining files and three older source versions were verified. A real Firefox
source download matched, and an extracted clean optimized release rebuild passed (27 executed,
26 cached tasks; no byte-identical binary claim). Website commit `1221707`, deployment
`5bb920d6-3cec-4ce1-bc3b-d8d64fcda180`, adds the new source and preserves earlier versions.
The first download verification hit HTTP403/500; subsequent full public/browser downloads passed.

At 20:08 Asia/Kolkata on18October, Console confirmed **0.1.0-beta.3 (4), In review, Full rollout**
for the existing Closed Alpha track. No audience change. Managed publishing is off, so approved
changes can publish automatically; Google review/quick checks remain external steps. Beta2 remained
available at inspection. Validation had zero errors and one warning: native debug symbols absent
from the upstream dependency; the ReTrace mapping is attached. Local checks do not turn the
account-budget-blocked remote CI into a pass. Production is not released.

This later documentation receipt does not replace the frozen candidate source revision above.

The generated Play-signed universal APK passed package/version, legal/source, dependency and native
alignment checks. Its certificate matches the previously verified Google-managed identity
`43dd51858d5fef2d47e85ba0d9fc8b046b5ce8ce7fb5ce7853b6cb9d208eed61`. On a disposable read-only
API36 ARM64/16KB emulator, `adb install -r` updated the preserved Play-signed build3 to4, and cold
startup displayed Chikku onboarding. This is not a Play Store install or full saved-layout/Firefox
restore/physical acceptance proof. The prior local upload-key APK correctly failed to replace the
Play-signed installation; no uninstall or signing-identity change was made. The first host certificate
command lacked JAVA_HOME; the corrected verifier passed with Android Studio's JBR.
