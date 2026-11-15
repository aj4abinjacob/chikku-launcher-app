# Matching source delivery

## Current continuation — 17 October 2026

The approved1840777 export is now published and a genuine Firefox public download matched its checksum. The owner confirms project/current-branding distribution rights and approves exactly12 listed historical-art exclusions. A corrected committed app/source pair is being prepared separately; the old source page does not match the later product fixes. See [dated evidence](SOURCE_PUBLICATION_2026-10-17.md). Historical sections below retain their dates and scope.

**No external build distribution is authorized by this document.** The private repositories alone
are not a source-access route for recipients. Apply this to internal/closed tests as well as the
later public release. GPL does not require publishing unrelated old Git history.

Use GPLv3 section6(d)'s network-distribution route: an equally accessible, no-extra-charge download
of exact Corresponding Source beside, or clearly linked from, the binary distribution. A separate
public source-download page can coexist with private GitHub repositories. Give testers the source
route with the opt-in/download instructions, and link it from About and release notes. Recipients
retain GPL redistribution/modification rights; do not add an NDA or “no sharing” condition.
[GPLv3](https://www.gnu.org/licenses/gpl.en.html),
[GNU source delivery FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#AnonFTPAndSendSources).

## Prepared source archive procedure

1. Review a clean source revision and its exact release runtime inventory. Preserve code, resources,
   schemas, Gradle wrapper/scripts, catalog, R8 rules, licence texts and credits. The fresh root
   retains all942 snapshot files; no missing historical PNG was silently excluded.
2. Run the audit task and licence script, fetch matching published dependency source JARs, and
   fetch graphics-path native/build sources omitted by its source JAR. The checked-in metadata
   records coordinates, origins, POM/parent licences, embedded legal files, hashes and exceptions.
   The MET branch also preserves original GeoNames cities/country/admin inputs, readme, CC BY 4.0,
   exact hashes, deterministic generator and bundled gzip index under tracked data/app/scripts paths.
   Source archives must include all of them; APK inspection checks packaged bytes against the manifest.
   A source JAR/metadata licence is evidence, not proof that a binary can be reproduced bit for bit.
3. `python3 scripts/prepare-source-archive.py --revision HEAD` requires a clean checkout and
   refuses missing/hash-mismatched dependency sources or absent native source. It creates a
   **preparation archive**, an inventory and checksums under ignored `build/release-source/`.
   Verify the archive can be read, required files/modes are present and no key, token, personal
   backup, local.properties or .git directory appears. Build from an extracted clean copy.
4. Resolve the source/provenance/legal review gates before distribution. The full snapshot includes
   older non-shipped design concepts with unresolved reference rights; archive preparation is not
   permission to distribute those files. If removing non-required art is chosen, make the exclusions
   explicit and reviewable first. Preserve every licence/credit for anything distributed.
5. Before each external build, publish its exact source download and instructions. Proposed
   `/chikku/source/` is staged and verified locally, awaiting the owner's concrete publication and
   ownership answers. The working tree replaces the bundled source placeholder with that route
   and adds an explicit copy button; this is not a claim that the route is already published. Rebuild, verify
   and associate the final artifact's SHA-256, version, source revision/tree, archive SHA-256 and
   R8 mapping checksum. Avoid a self-referential “archive includes its own checksum” design:
   the distribution manifest is a separate file.
6. For Play, verify the installed/downloaded APK's signing certificate and any Play transformation;
   installer/anti-tamper code is not enabled in the proposed setup. Keep the unprotected Play-signed
   APK and source pair. For direct APK testing, the same matching-source obligation applies.

The archive includes build instructions and version-pinned dependency source material rather than
private signing credentials. Never put keys/passwords into Corresponding Source. Whether additional
installation information is applicable needs the actual distribution circumstances; do not promise
rights or ability to update a Play-signed installation with an unrelated key. A developer can build
and sign a separate copy using their own key, respecting Android's update/signature restrictions.

## Current audit boundary

All resolved external runtime components have Apache-2.0 declarations in inspected POMs (including
Guava's parent). Every actual runtime artifact's source JAR has been retrieved and hash-inventoried;
metadata/BOM-only components have no runtime binary of their own. Native headers/build files were
retrieved at the official graphics-path1.0.1 release commit-range endpoint and verified against Git
blob IDs. This does not audit every line of every upstream history or establish bit-reproducible
native builds. Preserve unresolved ownership questions from [the release review](PLAY_PREPARATION.md).

## Download setup verified locally — 2026-10-16

`scripts/prepare-source-download.py` stages a static download from the recorded clean candidate
and source manifests. It refuses revision/hash mismatches, unsafe archive members and overwriting
an existing version. It retains a page and downloads for every source revision when adding later
versions. No repository visibility change, deployment, signing or app distribution is performed.
The website's individual static-file limit is handled with parts of at most20MiB. The browser
checks each SHA-256 and the joined archive before offering a normal download; separate parts,
checksums and macOS/Linux/PowerShell instructions are also available without the browser client.
The client, generator and full project licence are available from the page. No server processing,
account, cookies, analytics or upload is added.

The reviewed local export for preparation revision `1840777b38415c2907c12be04b1f62e7c2f7dbd7`
matches candidate AAB SHA-256 `3f71828534b8c91beaf96c65db0fee8f5fd20953d00c7c5912be1cd0e31604d7`.
Joined source SHA-256 is `7328ea6e82a966aac96d2e0ffe074a8db7f93980b400ced78ba49b118c3a24bd`.
All1,100 retained project files were compared to that revision's Git blobs; all84 dependency-source
files, current editable nose SVG, GeoNames inputs/generator and executable wrapper are retained.
The proposed export explicitly excludes12 unused historical design PNGs/prompts whose reference
rights remain unrecorded. The public manifest and archive's `SOURCE_EXPORT.json` list every path,
hash and reason. The private repository and original full preparation archive are preserved.
These exclusions and public exposure are awaiting owner approval, not silently authorized here.

A genuine Firefox download joined and verified the38.7MB archive; the resulting file's SHA-256
matched independently. A clean extracted copy built offline in10s (27 executed/27 cached tasks),
and its packaged APK passed the static verifier. Five generator regressions pass, including
tampering/traversal rejection and preserving older downloads; four browser-client unit checks cover
successful assembly, corrupt parts, wrong final hash and HTTP failure. Website build passes.
These are separate forms of evidence; the unit client checks are not browser tests.

Only the exact12 reviewed historical paths can be excluded; a new root-level PNG is retained.
The filtered-export regression checks that boundary, so future artwork is not silently dropped.

The new source-link reader/copy, recovery-layout and fresh-widget-size fixes are **uncommitted** and are not
in the1840777 snapshot. The old candidate must not be distributed as the corrected build.
Its native API36 copy/readback/stay-in-app check passes. It introduces no setting, backup field,
permission or dependency. After an allowed commit, generate a new clean archive and signed
candidate, add their own source entry and verify public downloads before any external distribution.
The owner requires new app-repository commits outside weekday09:00–18:59 Asia/Kolkata; no
timestamp workaround, upload or deferred job is authorized by this document.

Raw local evidence: `chikku-release-evidence/2026-10-16/source-download-final/`,
`public-source-integrity-review.json`, `public-source-build.log`,
`public-source-apk-verification.json`, `source-client-tests.log`,
`source-download-host-tests.log` and `source-website-final-build.log`.
