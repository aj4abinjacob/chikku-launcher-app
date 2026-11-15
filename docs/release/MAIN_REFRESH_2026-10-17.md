# Latest-main closed-test candidate — 17 October 2026

The owner explicitly requested making the new main changes available to the three accounts
already selected on closed Alpha. This authorizes this targeted test distribution and its matching
source update; it does not authorize a production/public GitHub launch or sending tester emails.

## Integrated source

Development main is pinned at `53bb644ab4b56112eefaf28ef75b0abc2ec8a0a1` (local main was newer
than origin/main). Its delta from the preserved original snapshot `9ba58a91e8686554b52dd4c9df736ee4912ee50a`
was applied onto release preparation `fe10690dc66be452813c4d4cc399a9f25b6b9bf9`, preserving fresh
release-repository ancestry, privacy/source notices, MET/offline city search, Google-app-only
handoff, signing setup, recovery layout and fresh-widget size fixes. The original checkout and
main branch were not modified. The130-path development delta is byte-exact/deleted on126 paths;
changelog, About/gesture legal-consent rows and weather-editor controls retain reviewed release
integration differences. The sole merge conflict combines the new rounded weather field with
MET's offline city-search availability before online opt-in.

Included changes: compact/icon-labelled settings and rounded fields; settings first-frame hold;
search-bar dog glyph choices; optional near-keyboard results and continuous scrolling; updated
long-press menus; local communication-app glyphs and Telegram/Threema contact options where the
installed provider exposes an eligible public entry. No new permission, exported component,
dependency, Room migration or backup-format version. The two new search appearance settings are
covered by both codec directions and non-default backup/settings roundtrips; see [data model](../DATA_MODEL.md).

## Package and notices

`io.github.aj4abinjacob.chikku`, versionCode3, versionName0.1.0-beta.2, min29/target36/compile37.
Console's latest releases/bundles view showed no uploaded artifact before preparation. Production
shrinking stays enabled. Normal release signing uses the existing external upload key; this is
separate from Google-managed installed-app signing. Native/physical acceptance limits below are
not removed by signing.

All124 Material vectors with upstream headers are hash-inventoried and listed in the bundled
Apache asset notice; current project artwork and all302 resource/branding files are re-inventoried.
Upstream licence remains Apache-2.0; original copy revisions remain unrecorded. Existing MET and
GeoNames credits/full terms/source inputs remain. The twelve previously approved unused historical
art/prompt exclusions remain explicit in public source exports; originals stay private. No installed
communication-app glyph is bundled; those are queried locally at runtime.

Privacy policy and Data safety describe the same optional contact handoffs, local settings,
offline cities and opted-in MET forecast processing. This UI/interop refresh adds no new network
provider, telemetry, advertising, compulsory account or payment flow. Weather traffic/proxy review
remains a growth gate before expanding beyond this small selected test group.

## Checks and distribution boundary

Fresh verification:231 core and1,094 app unit tests, zero failures/errors/skips;88 Python and4 client
checks pass. Debug, test and optimized release APKs build; debug/release lint each reports zero
errors,170 warnings and1 hint. Final package verification checks version3, permission/dependency
bytes, all offline legal documents, GeoNames inputs/index and native ELF LOAD/ZIP16KB alignment.
The privacy gate passes against113 resolved runtime modules. GitHub Actions was previously unable
to start because of account budget; new remote status is recorded separately from these local checks.

Seven native methods were validated on the disposable API36 ARM64/4KB emulator across corrected
runs: source-copy/privacy behaviour; three expanded-settings checks; real search drag/drop,
cancel/locked-layout/tap behaviour; HOME workspace/dock rendering; and native initialization/conic
conversion. The first run had two fixture failures (duplicate public app labels and incomplete
onboarding); the second passed those and found a source-dialog composition wait race. A final
isolated source-dialog rerun passed after waiting for the old tree to disappear. These were test
fixture corrections, not product changes; the failing logs are preserved. This is not a claim of
one clean seven-test run or physical-device coverage.

Exact signed/source/deployment/Console results are recorded in the external
`chikku-release-evidence/2026-10-17/main-refresh/` evidence, including the immutable final candidate
manifest. Do not infer live distribution from this source document's plan.
The unchanged ARM64 native dependency previously passed actual16KB initialization/conic conversion
with fallback disabled. Other ABI/stricter GNU_RELRO-header, physical/TalkBack/OEM/credential-lock,
complete derived-profile-surface and performance/soak coverage remain limited or unavailable.
These limits are distinct from the targeted closed-test authorization; production remains NO-GO.

Only the selected Alpha list is in scope. Keep the source route with tester opt-in/download
instructions, retain earlier source versions and recipient redistribution rights, and preserve exact
AAB/source/mapping/certificate hashes. Google review/processing and each tester's affirmative opt-in
are external steps; three saved addresses do not satisfy the production-access12-testers/14-days gate.
