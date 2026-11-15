# Candidate verification — 15 October 2026

## Current continuation — 17 October 2026

ARM64/API36 16KB native initialization/conic conversion and optimized production installation/onboarding passed with compatibility fallback disabled. The existing RELRO header caveat and other-ABI/workflow limits remain. The preserved1840777 bundle remains historical; final corrected artifact hashes belong to a separate external manifest. See [dated evidence](SOURCE_PUBLICATION_2026-10-17.md). Historical sections below retain their dates and scope.

The16October source-download/recovery follow-through adds uncommitted source-reader and
recovery-layout corrections. They are absent from the preserved1840777 signed candidate; that
candidate is inspection evidence, not the corrected distribution build. A new allowed commit,
clean source archive, signed candidate and corresponding public source entry are required before
external distribution. See [the latest acceptance report](SOURCE_AND_ACCEPTANCE_2026-10-16.md).

## MET branch package verification — 16 October 2026

The migration branch's optimized nondebuggable unsigned APK passes identity/permission allowlist,
unchanged exact dependency-byte audit, all **eight** offline legal documents, GeoNames input/index
hashes and gzip magic, four native ELF LOAD alignments and APK ZIP 16 KiB alignment. The index is
13,835,366 compressed bytes with 236,192 entries. Native packaged-index/real-MET tests passed;
see [migration evidence](MET_NORWAY_SWITCH.md). Genuine 16 KiB runtime/RELRO remains unverified.
New signed/source checksums belong in an external manifest after a clean reviewed commit, avoiding
a self-referential archive hash. The 15 October signed artifacts below retain their exact older source;
they are not evidence of MET behavior. No artifact has been uploaded to Play.


**Signed preparation AAB verified; production remains NO-GO. No Play upload occurred.**
Build source is the fresh snapshot plus the review-branch changes, with a recorded production-input
hash inventory. Final committed-source archive/provenance is generated separately after review.
Initial inspection used an unsigned APK. The owner subsequently authorized a new external upload
key; the signed candidate and its exact source are recorded below.

## Static package evidence

Inspected `:app:assembleRelease` output with SDK36.0.0 aapt2/zipalign, source assets and merged
manifest. Exact static output is [candidate-apk.json](candidate-apk.json). This report belongs to
the inspected working-tree artifact; final committed-source rebuild hashes are in the separate
local distribution manifest, avoiding a self-referential source/archive hash.

- Package `io.github.aj4abinjacob.chikku`; code2/name0.1.0-beta.1; min29/target36/compile37.
- Production R8 enabled, nondebuggable. No companion APK or androidTest fixtures are in the app.
- All seven offline legal/policy/source documents are present byte-for-byte, including full GPL
  and Apache terms, 74-vector notices, exact library notices and native source headers.
- Expected permission set: ACCESS_NETWORK_STATE, INTERNET, BLUETOOTH_CONNECT,
  BLUETOOTH(maxSdk30), READ_CONTACTS, EXPAND_STATUS_BAR, REQUEST_DELETE_PACKAGES, USE_BIOMETRIC,
  plus AndroidX's application-specific signature-level dynamic-receiver permission. The forbidden
  package-inventory, usage, device-admin and advertising-ID permissions remain absent.
- Existing exported HOME/settings/pin-consent activities remain; pin requests are framework-token
  guarded. Notification/accessibility services require their system bind permissions. AndroidX
  ProfileInstallReceiver is DUMP-protected. FileProvider and initialization provider are not exported.
  No new permission, exported component or dependency was added by this preparation.
- Automatic platform backup remains disabled, with explicit extraction exclusions. Chikku's
  user-initiated backup/restore remains the supported path.

## Native dependency

The APK has `libandroidx.graphics.path.so` in arm64-v8a, armeabi-v7a, x86 and x86_64 from the exact
resolved graphics-path1.0.1 AAR. Its native source headers are AOSP Apache-2.0; no Skia/BSD licence
is inferred merely because an implementation comment mentions Skia structures. Fourteen native,
build and licence files were retrieved at the official release-range endpoint, with original
copyright notices and Git blob/hash verification retained in the [source manifest](graphics-path-native-source.json).
The published source JAR alone omitted this native code.

All inspected ELF LOAD alignments are16,384 and APK `zipalign -c -P16 4` passes. **This is not a
16KB runtime acceptance pass.** GNU_RELRO end offsets are retained in the JSON; they are not all
16KB-aligned under the stricter current Android guidance. The source CMake uses max-page-size16384.
Investigate/test on a genuine16KB image before declaring full compliance; do not silently label
LOAD alignment as a complete native compatibility result. The available emulator image's actual
page size is recorded separately. [Official page-size checks](https://developer.android.com/guide/practices/page-sizes).

## Automated evidence

One Gradle process at a time, max-workers2, heap2GiB, active processor limit2, in-process Kotlin;
unit forks limited to one with1400MiB heap. Hardware: 16GiB Mac,10 logical CPUs, roughly25GiB free
at preflight; Android Studio JBR25.0.2, Gradle9.7.1, SDK37 installed, build tools36.0.0.

Fresh forced run: **230 core +1054 app JVM/Robolectric tests**, zero failures/errors/skips.
Debug and release lint each retain170 warnings and one hint, zero errors. The final disclosure
regression initially needed the normal ComponentActivity view-tree owner setup in its fixture;
that fixture was corrected without weakening the no-intent-on-refusal checks. An earlier syntax
error in the shared disclosure refactor was corrected before accepted results.
Final changed-source build/unit/debug+release lint and APK/test-APK assembly also passed; task
reuse is retained in logs. **79 host Python regressions** passed. Privacy gate passes four forbidden
permissions and49 SDK rules across113 debug/release module families. Release licence audit separately
covers109 selected external components and70 unique runtime artifacts, including BOM/variant metadata.

Three signing guard checks are expected rejections: unsigned Play bundle; partial upload
credentials; production upload credentials mixed with an acceptance application ID. No unsigned
AAB was emitted as a deliverable. Gradle wrapper9.7.1 JAR matches the official checksum
`7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`.

GitHub Android CI did **not start** at initial root1ccde1e: the check annotation says an Actions
budget prevents further use. Dependabot generated its own wrapper-update PR; it is preserved and
not merged. Its failure is not a code/test verdict. Local results are separate from remote CI.

Native/emulator results and open physical gates are recorded in [QA results](../QA_RESULTS.md)
and [current review](PLAY_PREPARATION.md). No primary-phone install/data/HOME change is performed.
Local raw logs/APKs/screenshots live outside Git in the owner's release-evidence directory.

## Exact-source archive build

The preparation archive for commit442cbd7 was verified against every tracked Git blob/mode,
70 dependency source archives and14 native source/build/licence files, then extracted into a
clean directory with only a local SDK pointer added. Offline optimized APK build passed in42s
(33 executed tasks/21 from cache), and the extracted APK passed the same static verifier. This
is clean-source build evidence, not binary reproducibility or recipient-access clearance. Final
documentation-only commit archive/rebuild hashes are recorded in external `FINAL_HANDOFF.json`;
these historical unsigned results remain distinct from the subsequent signed candidate.

## Owner-authorized signed candidate

Candidate source: `9d19ff3f3caceafb068887e6706d5da23f9df8e2` (tree
`b5c8cb937ee965e4625aaa9c69b43b5ab9e5d906`). Its567 production input hashes remain unchanged
from the tested code; subsequent changes through this revision are documentation/store assets.
The archive includes every tracked blob/mode,70 dependency source archives and14 native
source/build/licence files. A fresh extraction built offline in10s (26 executed/27 cached tasks)
and passed the static verifier after independently resolving its runtime artifact inventory.
This is buildability evidence, not a reproducible-binary claim or recipient-source clearance.

The owner-approved RSA4096 upload key, password and recovery instructions are outside Git with
private local permissions. Public certificate SHA256:
`6A:C3:C3:C0:DF:72:F9:B2:C0:2A:21:CD:E9:B5:B3:38:CC:5D:8C:92:98:40:88:C4:01:56:38:53:2E:2B:C3:D6`.
Encrypted recovery backup and Play enrollment remain owner gates; this is not Google's installed-app key.

Bounded `bundleRelease` passed in32s (19 executed/39 up-to-date tasks). The raw AGP bundle placed
JAR manifest/signature entries last, causing JDK25's stream-order inconsistency warning. Standard
`jarsigner` rewrote/signature-ordered the canonical copy with the same key; all270 non-signature
payload entries remained byte-identical. The canonical bundle passes JAR signature verification
without that inconsistency and official bundletool1.18.3 validation. Expected self-signed/PKIX,
no-TSA and POSIX-attribute warnings are retained.

Canonical AAB SHA256: `adda86fde82eda55e22415bd50d1e3689d3ef68d64726f94165e9deeaa717794`.
Matching source archive SHA256: `42d3e5f0e5a8a25fc3bbd6bf74bf9776f75fa9d78f630a05278fc5d063cdec71`.
R8 mapping SHA256: `b8458518f64914e01a5e55356e5e081f7d633cf54bc03a6bc38331f72f03a4fc`.

The AAB embeds the exact source SHA and matching R8 mapping. Its manifest confirms package,
version/min/target, nondebuggable release and disabled platform backup. All seven notices match
source, native bytes match the reviewed dependency and expected permissions remain unchanged.
Bundletool generated a local upload-key-signed universal APK; `apksigner` verifies the same
certificate and ZIP16KB alignment passes. This locally generated APK is **not a Play-signed APK**
and cannot establish Play-installed update compatibility.

Exact paths, sizes, hashes, signature logs and scope are in external `SIGNED_CANDIDATE.json` and
`FINAL_HANDOFF.json`. No upload, Play enrollment, release publication or public app-repository
change is claimed. Source delivery/provenance, weather rights, declarations and native/physical
acceptance gates still apply.
