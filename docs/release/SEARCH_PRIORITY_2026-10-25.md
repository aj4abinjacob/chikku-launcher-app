# Search priority closed-test update — 25 October 2026

The owner requested committing, pushing and making the search update available on Google Play.
The Console currently allows the existing closed Alpha track; production access is disabled,
with two opted-in testers against the displayed requirement of twelve for fourteen continuous days.
This candidate preserves that selected tester audience.

## Source and compatibility

Development change `edbd329c400b5775d13eced44deaaa18478d16e9` adds the current Go-target
highlight and compact app priority direction. Settings/portable backups preserve the direction,
and older backups default to following the language. No permission, dependency, Room migration
or portable backup format change is introduced.

The release baseline is the verified build 8 source archive, SHA-256
`d94548dac77369bd37ce2c280956d00482bd98cec40e84839443264fe5cfcb45`, based on release
`5a83402a5e739128bc0622812e54586855dca165`. It retains the previously uploaded Home return,
memory, search-bar actions, shortcut and choice-sheet improvements, plus the release-specific
MET/offline city data, notices, app handoffs and signing configuration. Existing checkouts and
unrelated development/branding edits remain preserved.

Candidate package is `io.github.aj4abinjacob.chikku`, version code 9, version `0.1.0-beta.8`.
R8 stays enabled and the release remains nondebuggable. The existing external upload key signs
the AAB; Google's installed-app signing identity remains separate. Exact matching source,
including the already audited dependency/native sources and build instructions, is available
on the public [version source page](https://www.abinjacob.in/chikku/source/4f398d5a93ad/),
which the candidate's About/source-copy action already links. A dedicated static Worker is scoped
to `/chikku/source/*`, preserving older downloads without deploying unrelated portfolio work.
Both repositories remain private. The only excluded archive files are the twelve previously
approved unused historical design artifacts listed by the source-export manifest.

## Verification and submission

Development verification passed: 233 core and 1,116 app JVM/Robolectric tests, debug build, lint
and privacy. Integrated checks passed: 235 core and 1,216 app tests, 88 Python and four source-client
tests, debug/instrumentation APK assembly, optimized release assembly, debug/release lint and
privacy. Remote Android CI did not start because the account's Actions budget is exhausted; this
is not a passing CI result. The production/test/data comparison with the frozen build 8 archive
found only the approved search paths and version changed. Signed artifact/source/mapping hashes and the actual
Console upload/review outcome are retained in the owner's local
`chikku-update-evidence/2026-10-25/search-priority-code9/` and recorded after execution.

No connected device is currently available. Physical one-handed use, TalkBack, font scaling,
OEM keyboard behavior and real saved-layout upgrade acceptance are not newly established.
Preparation alone does not imply Google review approval, tester availability or production launch.

## Execution receipt

On 25 October 2026 at 18:47 IST, the owner-authorized closed Alpha update was submitted.
The Console's Latest releases table shows `0.1.0-beta.8 (9)` as **In review — Full rollout**,
178 of 178 selected countries/regions. Publishing overview shows **Changes in review**;
its quick checks were still running when submission was verified. Managed publishing remains
off, so an approved change is eligible to publish automatically to this existing closed audience.
Build 4 remains the release available to testers until Google approves/publishes this update.
No production rollout or new tester audience was created.

The review preview reported no blocking error and one warning: the prebuilt native library has
no uploaded debug symbols. R8 mapping is attached. Supported-device counts did not change;
the bundle explorer reports 14,454 supported Android devices and static 16 KB page-size support.
These are Console/static findings, not device-runtime acceptance.

| Artifact | SHA-256 |
| --- | --- |
| Uploaded AAB, code 9 | `9ca12e74bdbbb9528deadf988b8dd721d303153d0da86375d4892febd655623a` |
| Google-signed universal APK | `64b82309a1da6458fd4ba78a521076f1ff4637f7e7a170f309fbf361c19b8288` |
| R8 mapping | `0a80d95c8a02d91d406f108827b09dc18110c3453c98790159f3972889b7cc28` |
| Private preparation source archive | `1336ad42dae49e3f3f459008c973f832f123d7a351ac10fae4731f702f9696fe` |
| Public corresponding-source archive | `5e0c5f645f28ac31fc6075452f4c46f05aa823491f9190c7084b3058310d3436` |

The immutable uploaded candidate is source revision
`4f398d5a93ad62d14b095709c4e737825692fb55`. This receipt is a later documentation-only change;
it does not change the source or bytes of that candidate. `bundletool validate`, AAB signature
verification and packaged APK inspection passed. The external upload certificate matches the
existing enrolled upload key. Signing credentials remain outside the repositories and archives.
The Console-generated universal APK also passed packaged inspection and v3/SourceStamp signature
verification. Its certificate SHA-256 is
`43dd51858d5fef2d47e85ba0d9fc8b046b5ce8ce7fb5ce7853b6cb9d208eed61`, matching the existing
Play app-signing identity. It was not installed on a device in this session.

Public source delivery uses Worker version `925f3ac9-72a9-46e4-af1a-5b4d2bd66265` on the
source-only route. Large-part requests initially failed; ten parts of at most 4 MiB were deployed
without changing the assembled archive. All ten downloaded public parts passed their manifest
hashes, and the assembled 38,902,095-byte archive passed its hash. A Firefox download completed
and its saved file has the same hash. Transient requests required retries during verification.
The filtered source archive was extracted separately and `assembleDebug` passed offline with
the installed toolchain/dependency cache. Earlier source version directories remain served.

Development PR: [development #8](https://github.com/aj4abinjacob/chikku-launcher-development/pull/8).
Release PR: [release #9](https://github.com/aj4abinjacob/chikku-launcher/pull/9).
Both branches were pushed; no main-branch merge or CI-budget bypass was performed.
