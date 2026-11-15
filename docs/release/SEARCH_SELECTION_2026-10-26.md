# Search selection closed-test update — 26 October 2026

Version `0.1.0-beta.9`, code 10, was signed, uploaded and submitted to the existing Closed Alpha
track at approximately 18:54 IST on 26 October. Console shows **In review — Full rollout**, with
178 of 178 existing countries/regions. Publishing overview shows **Changes in review**, with quick
checks still running before Google review. Managed publishing remains off. Build 9 is still the
release available to testers until Google approves/publishes this update. No production launch or
new tester audience was created.

## Changes and source identity

The candidate retains the published beta 8 baseline, app-priority direction preference, layouts
and backups. Search selection uses a soft fill, inset marker and brief reveal at the existing
motion speed. Icons, text, neighboring results and hit regions remain fixed. Stale selection clears
immediately; animation-off preferences and system motion scale are respected. Ranking, Go,
launch/drag behavior and reading order are preserved. No permission, dependency, persisted field,
database migration or backup-format change was added.

Development commit `2d5086cab3f93b853794433a48cf299174011ecf` is pushed on
`fix/search-selection-polish`: [development PR #9](https://github.com/aj4abinjacob/chikku-launcher-development/pull/9).
The exact candidate is commit `11b817cf1e55bfb0393b25e4877adf3854b740d1`, tree
`29c38b61158879cdf41967edbe3dfba97e549927`, pushed on `fix/play-search-selection-20261026`:
[release PR #11](https://github.com/aj4abinjacob/chikku-launcher/pull/11).
Commits and pushes ran after the permitted evening window, without modifying or bypassing the
local Git activity hook. No main merge, force push, visibility change or check bypass was performed.

Source was frozen before the commit window using `prepare-source-archive.py --staged-snapshot`.
The exporter rejected unstaged/untracked files and verified every archived Git blob against the
index tree, plus 84 audited dependency/native source files. That exact tree was subsequently
committed unchanged. Public metadata retains its staged-tree export identity and records the
associated candidate commit. The version page explains this association.
The receipt commit is documentation only and does not change the uploaded candidate or its source.

## Verification

Development checks passed: 233 core and 1,117 app JVM/Robolectric tests, debug APK assembly,
Android lint and privacy. Integrated release checks passed: 235 core and 1,219 app tests, 89 Python
tests, four source-client tests, debug and optimized release assembly, debug/release lint and privacy.
The initial release commands were interrupted before completion; final lint/signing logs establish
the completed checks. No passing result is inferred from those interrupted logs.

`bundletool validate`, AAB signature verification and packaged APK inspection passed. The upload
certificate matches the enrolled key. The downloaded Google-signed universal APK also passed
v3/SourceStamp signature verification, package/version and permission checks, legal/offline city
asset checks, dependency-byte audit and static 16 KB ELF/ZIP alignment checks. Its certificate is
`43dd51858d5fef2d47e85ba0d9fc8b046b5ce8ce7fb5ce7853b6cb9d208eed61`, the existing Play identity.
The Console reports static 16 KB support and no supported-device removals. R8 mapping is attached.
One nonblocking Play warning remains: native debug symbols were not uploaded for the prebuilt library.

Development Actions did not start because the account's Actions budget prevents further use.
This is not a passing remote CI result. No physical-device run was performed; motion feel,
one-handed use, TalkBack, OEM keyboard behavior and real saved-layout upgrade acceptance remain
unverified by this change. Local synthetic renders and static package checks do not establish them.

## Artifact and public source receipt

| Artifact | SHA-256 |
| --- | --- |
| Uploaded code 10 AAB | `8f7aae13ee592aa59fdba4f3fafdd864da6b724c8e8063f8b15630e2dbebb875` |
| Google-signed universal APK | `cc5b4ef7b760919e7a2f07cc30b2059a4dd71c23556dae92a3ebb677da0ffb81` |
| R8 mapping | `449004715ae4b1d084226b3fa7085778a48f8668eae329f72fdfcb2140d2b5e2` |
| Private full preparation source | `9cf2555ca701d417cc61277a0570d1edf9e3ff72996c97b8f25887bc546b3f01` |
| Public corresponding source | `13f5956f50b3e8248d2811bac24928fa7fc4abcdbe2ea3f3a2ec05d272ec4909` |

[Matching public source](https://www.abinjacob.in/chikku/source/29c38b611588/) is served by
source-only Worker version `db21bf1b-5673-4de8-ae3d-6b20018991fe`. All ten public parts passed
individual hashes; their assembled 38,884,198-byte archive passed its hash. Initial transient
requests needed retries. The same filtered archive rebuilt successfully offline with the installed
toolchain/dependency cache. Exactly the twelve previously owner-approved unused historical art
files are excluded; current artwork, build inputs, licence notices and dependency sources remain.
Earlier source versions remain served. No unrelated portfolio checkout was deployed.

Exact build, signing, source-download and Console receipts are retained in the owner's local
`chikku-update-evidence/2026-10-26/search-selection-code10/`. Private signing credentials remain
outside the repositories and archives. Submission is complete; Google review approval and tester
availability are not claimed by this receipt.
