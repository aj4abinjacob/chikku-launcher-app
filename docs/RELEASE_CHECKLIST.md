# Release checklist

## Current closed-test update — 18 October 2026

Beta2/build3 is available to selected Alpha testers in Play Console. Beta3/build4 integrates the
newly pushed development main, preserving the reviewed release setup. Build, matching source and
Console outcomes are recorded in [the dated update](release/MAIN_REFRESH_2026-10-18.md).
Production remains NO-GO; historical entries below retain their original dates and scope.

## Current continuation — 17 October 2026

Approved source publication and the missing ARM64/API36 16KB runtime smoke check are complete. The corrected bundle/source pair and broader release gates remain separate; no Play upload or test rollout occurred. See [dated evidence](release/SOURCE_PUBLICATION_2026-10-17.md). Historical sections below retain their dates and scope.

**Production NO-GO.** The selected closed test is active; broader production acceptance remains open.
Use [the current review](release/PLAY_PREPARATION.md), [QA readiness](QA_RELEASE_READINESS.md)
and [artifact evidence](release/ARTIFACT_VERIFICATION.md). Checkboxes describe actual gates, not promises.

## Repository and rights

- [x] Original repository renamed privately with identity/history/branches/issues/PRs preserved;
      35 accessible development checkout remotes updated before the old URL was reused.
- [x] New private repository initialized from all942 reviewed local-main files with fresh ancestry.
- [x] Full GPL/Apache texts and accessible bundled third-party notices prepared.
- [x] Exact resolved release runtime/POM/transitive/native/source inventory prepared.
- [ ] Resolve remaining branding/contributor/employment/reference rights before distribution.
- [ ] Verify corresponding-source completeness, clean extracted-source build and equally accessible
      recipient downloads for every external test or release. Full source and proposed filtered
      export both rebuild; genuine local browser download/checksums and all retained source blobs
      verified. Publication/ownership answers and final changed-app source/candidate pair remain open.
      A private GitHub link is insufficient.
- [ ] Public GitHub visibility/release only after Play release and separate explicit owner authorization.

## Privacy, service rights and Console

- [x] Full offline privacy policy and matching HTML prepared; privacy contact confirmed.
- [x] Owner approved the concrete policy content/route and confirmed domain control.
- [x] Original approved policy published; canonical HTTPS200 and exact approved content verified; no login.
- [x] Publish the exact revised MET policy after specific approval; HTTPS200/bytes verified16October.
- [x] Implement/test owner-approved MET forecast and offline GeoNames migration with attribution.
- [ ] Review aggregate MET traffic/proxy needs before broader rollout; data licence is not unlimited capacity.
- [x] Save reviewed Data safety against MET behavior, provider retention/sharing and conservative location/IP categories.
      Four optional categories saved/exported; changes are not sent for review.
- [ ] Verify optional contacts/notification/Bluetooth/accessibility install, denial, revocation and reboot.
- [x] Publish owner-approved consent/refusal/grant/action demo; verify public page/video/captions.
- [ ] Complete non-accessibility-tool declaration when Console exposes it, and obtain Play review.
      Current App content has no pending declarations; no specific Accessibility form is yet listed.
- [ ] Confirm account type and all requested identity/contact/device verification steps.
- [x] Save listing/app access/ads/content rating/ad-ID/target audience and genuine screenshots/graphic.
      Listing ready to send for review; IARC Completed. Distribution/rights/Data safety gates remain separate.
- [ ] Owner reviews legal/export/signing agreements; verify automatic installer protection is off.
      Protection explicitlyOFF16October; managed app-signing key in use, no key change by agent.

## Candidate, recovery and acceptance

- [x] Record fresh core/app tests, host regressions, debug/release lint, build, privacy gate and CI status.
      Local checks pass; remote Android CI cannot start because of the account budget.
- [ ] Inspect actual production APK/AAB: stable package, unique increasing code, nondebuggable R8,
      permissions/exported components, native/page alignment, notices and no debug fixtures/secrets.
      Signed1840777 AAB/generated APK pass static/signature/bundletool checks; genuine16KB runtime/RELRO
      acceptance and Console greatest uploaded code remain unverified.
- [x] Create external upload key and local recovery instructions with explicit owner approval.
- [ ] Owner secures key backup and enrols selected Google-managed Play app signing/upload certificate.
- [ ] Preserve artifact/source/R8 mapping checksums and exact source revision; verify Play-signed APK identity.
      Exact1840777 AAB/source/mapping hashes are preserved; new uncommitted copy-link changes require
      their own clean committed pair. No Play-delivered APK exists yet.
- [x] Actual same-key historical APK upgrade, real credential-free emulator reboot and fresh-install
      service restore preserve synthetic production workspace/settings/Room state.
- [ ] Secure PIN/biometric first unlock and representative physical upgrade/reboot acceptance.
- [ ] Actual SAF export/import/fresh-install/interrupted restore and durable Undo; missing apps,
      icon packs, document grants and widget providers remain recoverable on smaller displays/grids.
      API36 real SAF preview/cancel/restore and synthetic recovery/Undo passed16October; broader
      dependency-loss layouts/physical acceptance remain open.
- [x] Genuine configured third-party widget consent/recreation/provider loss/fresh-ID rebind passes
      on the nondebuggable API36 acceptance pair; fresh corrected sequence9/9, including fresh-install
      restore/configuration. Earlier failures preserved. Physical/OEM and Play-delivery checks are separate.
- [ ] Managed quiet/lock/remove and API35+ private exclusion pass; no locked contents in derived surfaces.
      Six API36 native identity/quiet/resume/remove/private-running/stopped enumeration stages pass,
      with exact owned-profile cleanup. Actual credential/Private Space UI, derived surfaces and DPC/OEM remain open.
- [ ] Cards/editor native semantics, real TalkBack/keyboard, font scale, RTL and representative OEM checks pass.
- [ ] Measured startup/frame/memory/battery/soak evidence and known compatibility limits recorded.
- [ ] Internal testing and Play-delivered upgrade/pre-launch report reviewed after distribution gates.
- [x] Save all supported closed-test countries (178), with monitored feedback contact.
- [ ] Owner provides tester accounts/group; closed test meets dashboard's12 testers continuously opted in14 days; feedback/fixes recorded.
- [ ] Owner applies for production access; production submission only with explicit authorization.

Every persisted-state change must preserve backup/restore or explain exclusions in DATA_MODEL.
No silent reset, live widget-ID reuse, compulsory account/subscription or universal Recents claim.
Keep main integration reviewed; no automatic development-main merge or force push.

Latest source-download/acceptance evidence and preserved failed attempts:
[16October follow-through](release/SOURCE_AND_ACCEPTANCE_2026-10-16.md).
