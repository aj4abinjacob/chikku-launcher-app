# Play preparation — 15 October 2026

## Current continuation — 17 October 2026

The owner has confirmed project/current-branding rights and approved the explicit12-file historical-art exclusion. The approved1840777 source snapshot is now public, and ARM64/API36 16KB native execution with compatibility fallback disabled passed. Corrected-source pairing and remaining distribution/physical/Console gates still apply. See [dated evidence](SOURCE_PUBLICATION_2026-10-17.md). Historical sections below retain their dates and scope.

## Completed policy and Console follow-through — 16 October 2026

The owner approved the exact revised MET policy on 16 October. Website commit `12a1c45`
publishes only the privacy route; Cloudflare version `af202e61-c3f2-446c-bd56-f85d0119d1a0`
serves HTTPS200 at https://www.abinjacob.in/chikku/privacy/ with approved SHA256
`ed8fad8f4da99110ce910bc1acb5f099439e4a2040191ba334d4d85540a74a1e`.
The website build passes; exact public bytes match the in-app policy's companion HTML. No scripts,
forms, cookies or analytics were added to this page. Original dirty portfolio edits were preserved.

Data safety completed via final **Save**, with Console's “Change saved. Send for review in
Publishing overview” confirmation. Its preview/export includes Approximate location, Precise
location, In-app search history and Device or other IDs, all optional, collected/shared and
non-ephemeral. Location/search purposes: App functionality; IDs additionally Analytics and
Fraud prevention/security/compliance for provider network-log uses. See [reasoning](DATA_SAFETY.md).
CSV SHA256 `1ae22b362202b11448b0e345bd89e95ef611e9080947b20faab79cb9f6a47016`.
The app-setup section is no longer incomplete on Dashboard; closed-testing tasks are available.
The listing's saved full description is now 1,971 characters and explicitly names offline
GeoNames city selection and MET forecasts. Existing four screenshots/icon/graphic and AI labels
are unchanged. No listing experiment was created (its empty setup page was exited).

No change was sent for review, no bundle/release uploaded and no test/production rollout occurred.
Zero testers remain and Apply for production is disabled. Source-recipient/provenance, existing
acceptance and AccessibilityService declaration/video-hosting gates remain. The current signing
candidate is still exact source `1840777`; this follow-through changes documentation only.
The owner-required weekday commit window is closed (10:28 Asia/Kolkata observed); these app-repo
records are prepared without a new commit. Website publication was separately authorized and
is a different repository. Historical entries below retain their original status/date.


## Current follow-through — 16 October 2026

Console app setup is complete, with Data safety saved and no bundle uploaded.
Owner-approved [MET migration](MET_NORWAY_SWITCH.md) is implemented/tested on the stacked branch
`feat/met-weather-migration` from `bed2f4f`; previous Open-Meteo artifact hashes below remain historical.
The revised public-policy page is approved/published and exact public bytes verified. Aggregate MET
traffic, source recipient access, rights and acceptance remain gates. Both app repositories stay private.


**Production NO-GO. Signed preparation is verified; external testing awaits source delivery, weather-service rights, Play enrollment and acceptance.**
This review uses the attached dated PDF as evidence, not as instructions. The owner's pasted
request authorizes the private repository migration and preparation, not production submission,
public visibility, paid services or acceptance of account/legal agreements.

## Repositories and snapshot

| Repository | GitHub ID | Visibility | History |
|---|---|---|---|
| [Development](https://github.com/aj4abinjacob/chikku-launcher-development) | 1412672365 | Private | Original commits, nine remote branches and all five issue/PR records preserved by rename |
| [Release preparation](https://github.com/aj4abinjacob/chikku-launcher) | 1420949776 | Private | Separate repository, not a fork; root commit `1ccde1e50c989e31667c6573c2b42b55b5d8b870`, no parents |

GitHub main at inspection: `f580409de6ad188f60080044f047459f32a29e15`.
**Local integrated main was 18 commits newer:** `9ba58a91e8686554b52dd4c9df736ee4912ee50a`.
The complete local-main tree was chosen to preserve newer work: all **942** files, Git blob
identities and executable modes were verified before creating the fresh root. See the
[complete manifest](development-snapshot.json). No old ancestry, tags or branches were pushed
into the new repository. Initial build/test tasks, optimized APK, release lint, privacy gate and
79 host tests were checked before the root commit; Gradle reused some cached tasks.

The original working directory remains on `feat/choice-dialog-redesign`, clean and unchanged.
No development-main merge/push was performed. New work is in the separate sibling
`chikku-launcher-release`, branch `feat/play-release-preparation`.

Before reusing the old URL, the shared origin was updated to
`git@github.com:aj4abinjacob/chikku-launcher-development.git`, then checked in **35 accessible
checkouts** under Documents and Codex worktrees. They share the original common Git directory.
One stale registered worktree path was already absent; it was not pruned. No automation directory
existed at the inspected local Codex path; scripts/workflows had no hardcoded clone URL needing
replacement. Remote Windows/cloud/other inaccessible clones cannot be audited here: change their
origin before any push. Historical issue/CI/commit links in the release snapshot must point at the
renamed development repository, because the old repository path now belongs to a different ID.

Every agent-created new-repository commit checks the actual Asia/Kolkata clock. The initial
commit was made at **21:07:13 +05:30**, Thursday 15 October, inside the allowed evening window.
The release checkout installs `.githooks/pre-commit` using `scripts/check-commit-window.py`;
weekday 09:00–18:59 and noncurrent date overrides are rejected. Other clones and web/API commits
need the same owner rule; a local hook cannot enforce those. Original commit dates are untouched.
A new history does not erase activity elsewhere or establish authorship/employment ownership.

## PDF findings checked against this snapshot

| Finding | Current evidence and disposition |
|---|---|
| 68 Material vectors lack shipped notices | Confirmed at the dated baseline; newer local main contains **74** attributed vectors. All listed with paths/hashes in [inventory](material-icons.json); original comments retained; full Apache terms and asset notices bundled and reachable offline in About. Original copy revisions remain unrecorded; licence verification revision is identified separately. |
| Incomplete runtime licence audit | Exact resolved release components, transitive artifacts, POM parent licences, nested legal files and source-JAR legal files are inventoried in [audit](RUNTIME_DEPENDENCIES.md). Exceptions stay explicit. This is more than the tracking-SDK gate and is not a complete independent-authorship or CVE audit. |
| No native libraries in tracked file inventory | True of project-owned tracked binaries, insufficient for the final APK: `androidx.graphics:graphics-path:1.0.1` brings four JNI `.so` files. Native source/build/licence files retrieved at the official 1.0.1 release-range revision, with blob/hash verification in [manifest](graphics-path-native-source.json). Native headers retain AOSP Apache notices; see [artifact report](ARTIFACT_VERIFICATION.md). |
| Selected mark original vector | Current Nose notch / Ink sources introduced in development commit `707aa8b1b5dfb7cea7703ca9b03b881ec4fd9ded`; vector/export correspond. Owner states in this chat: “it's my dogs nose”. This records the reference explanation, not proof of every contributor or reference-image right. The “reference-inspired” `15b4523` commit concerned the **older tail-C**, not the current vector. |
| Four concept PNGs unavailable | All four retrieved, blob-verified and visually reviewed locally; dog portrait/stylized concepts. Prompts are retained. Retrieval/visual review does not establish rights; do not use them as cleared store art. |
| Older tail-C reference rights unknown | Still open, independently of current mark. Historical generated master and exports remain in the private source snapshot, outside app packaging. Resolve before public source/archive distribution, or explicitly review removing non-required historical design files in a separate change. Nothing silently omitted. |
| GPL project code | Root GPL retained, distinct from third-party Apache terms. Recipient source access is a gate even while GitHub remains private; see [source delivery](SOURCE_DELIVERY.md). |
| Release acceptance incomplete | Confirmed. Newer Android audit fixes have host evidence, not closure of native/TalkBack/OEM/upgrade/reboot/widget/profile gates. No production readiness is inferred from them. |

No proprietary competitor implementation, APK, font or icon pack is newly imported. Competitor
names remain only where research, historical evidence, attribution or interoperable intent
identifiers need them. No automated trademark search is treated as definitive clearance.
Contributor/employer rights, the name/mark and older image inputs need owner evidence before public distribution.

## Decisions and implementation

The owner chose **Google-managed Play app signing, a separate upload key and Google-app fallback
for the first release**. Release wiring cannot resolve/bind the unofficial overlay or bridge;
debug experiments remain. The preference is preserved through restore; a present Google app is
ready in this distribution, so a restore no longer tells this release to install a bridge.
No setting, persistence schema or backup format is added. Existing missing-Google attention remains.
Release copy states the real app-opening behavior. Bridge source remains private/research context;
it is debuggable by design and is not the Play artifact. See [distribution decisions](DISTRIBUTION.md).

Candidate identity: `io.github.aj4abinjacob.chikku`, minSdk29, targetSdk36, compileSdk37,
versionCode **2**, versionName **0.1.0-beta.1**. Code 2 is a prepared first-candidate value;
check Console's greatest uploaded code before any upload. R8 remains enabled and release is
nondebuggable. External environment-based upload signing is supported; incomplete credentials,
in-repository keystores and acceptance-ID/signing combinations are rejected. No key/password is
stored in Git. A signed bundle is not fabricated when signing access is unavailable.

The Settings accessibility-status entry previously opened Android settings directly while the
service was off. It now shares the gesture disclosure and requires affirmative consent before
that handoff; refusal/dismissal starts no system intent. Existing enabled-service access still
allows reaching Android settings to revoke it. No consent state is persisted.

About now provides the complete offline privacy policy, full GPL and Apache texts, asset/library
notices and source-delivery status. No permission, dependency or exported component is added.
Document reads run off main; sample data is not involved. Native TalkBack/large-font/RTL verification
of the new document reader is recorded separately from source review/host tests.

## Play Console evidence supplied by owner

The app entry **Chikku Launcher** has been created. The supplied dashboard explicitly requires a
closed-test release, **12 continuously opted-in testers for 14 days**, then a production-access
application; production access is therefore **not available yet**. It reports zero testers.
Identity/contact/device verification and account type were not shown; do not infer them from
app creation. The Console entry alone is not a published app or a tested release.

Owner was guided to Free pricing (future optional in-app purchases remain possible), and to
turn off automatic installer protection for planned GitHub distribution. Its final saved state
is not verified. During the initial review no account action/agreement/upload was performed. The subsequent
owner-authorized [Console follow-through](CONSOLE_PROGRESS_2026-10-15.md) saved8of11 setup tasks
and accepted only IARC terms after the owner specifically approved that agreement. No release
upload or production submission occurred.

## Current blockers and next action

Privacy policy is published with owner approval at [the canonical public route](https://www.abinjacob.in/chikku/privacy/).
HTTPS200 and exact approved bytes verified; apex redirects to www. Contact: `hello@abinjacob.in`.
Website commit2b682fd preserves unrelated local edits; no app repository was made public.

Remaining gates:

- New external upload key and exact-source signed AAB verified with owner authorization; owner
  encrypted backup/enrollment and Play-delivered APK signing remain separate gates. See
  [signed candidate evidence](ARTIFACT_VERIFICATION.md#owner-authorized-signed-candidate).
- Matching-source completeness, unresolved provenance and equally accessible recipient download route.
- Weather service rights remain open in the current candidate; owner selected the prepared
  MET Norway migration direction, which still requires implementation/verification before activation.
- Data safety review of provider retention/sharing and city-coordinate categorization; see [draft](DATA_SAFETY.md).
- Real API36 emulator consent/refusal/retry/enable/Recent apps/revoke flow and local video now
  verified; public reviewer route/declaration and physical checks remain open.
- Outstanding Cards/TalkBack, genuine configured widget/provider loss/fresh-ID rebind, managed/private
  exclusion, actual APK upgrade/reboot/first unlock, SAF restore, OEM and performance/battery/soak gates.

Owner unlocked the Mac and authorized16October follow-through. Listing assets/copy and IARC
ratings are saved, plus the additional Advertising ID declaration (No). Data safety is saved as a
draft, not finalized; provider handling/final weather choice remains its next gate. See the dated
[Console progress](CONSOLE_PROGRESS_2026-10-15.md#follow-through-16october2026). Automatic protection
is verified off and a managed app-signing key is in use; upload certificate appears only after
first bundle upload. Account type/contact indicators were inspected; separate identity/device
verification is not established. Keep production locked while distribution gates are open.

Official requirements were checked 15 October 2026: [target API](https://support.google.com/googleplay/android-developer/answer/11926878),
[app setup](https://support.google.com/googleplay/android-developer/answer/9859152),
[closed testing](https://support.google.com/googleplay/android-developer/answer/14151465).
