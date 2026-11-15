# Consolidated phone update — 21 October 2026

The owner requested consolidating the pending changes and updating the existing phone installation,
without making commits. The integration lives on `codex/consolidated-phone-update` in the separate
`chikku-consolidated-update` checkout. Original working folders, their indexes and branches are
preserved. No commit, push, public source deployment or tester/production rollout is part of this task.

The release baseline is `5a83402a5e739128bc0622812e54586855dca165`. Contributions are frozen as
patches, base/current files and SHA-256 records under
`chikku-update-evidence/2026-10-21/consolidated/` outside Git:

- Development main through `ba304ead41f012292504acd8c2b687e9d32dc5eb`, including the folder tap
  fallback fix and its persistence/undo/backup coverage.
- Uncommitted Home return fix and its lifecycle regressions, plus the debug app-name distinction.
- `chikku-memory`'s integrated icon/page/drawer/cache/badge/folder-search work. Its input paths cover
  all three memory subfolders; the integrated version includes their later corrections.
- `chikku-search-bar-actions`'s bar tap behavior, independently placed buttons, synthetic live
  preview and three backwards-compatible settings/backup fields.
- `chikku-shortcut-pin`'s view-tree-owner fix and contentless-activity prompt regression.

One HomeActivity setup conflict was resolved by retaining both the drawer visibility callback and
external-intent-aware search entry points. The Home state remains lazily subscribed for its
ViewModel lifetime while folder search shares the resolved workspace. Release-only MET/offline
cities, legal/source notices, Google-app-only handoff, recovery/widget behavior and signing guards
are retained.

The candidate is `io.github.aj4abinjacob.chikku`, `0.1.0-beta.5`, versionCode 6, minSdk 29,
targetSdk 36 and compileSdk 37. Room schema 4 and portable backup format 1 are unchanged. The three
new bar preferences have defaults, both codec directions, legacy decode and round-trip coverage;
they add no device-local reference. No new dependency, permission or exported component is added.

Verification passed: 233 core and 1,172 app JVM tests, debug/test APK assembly, debug/release lint,
privacy and signed release APK/bundle/dependency/notice/alignment checks. On the Galaxy S24 Ultra
(Android 16/API 36), the isolated native Home return and graphics-path checks passed 2/2 in
18.809 seconds. The first native run could not reach Settings with the screen asleep; the awake
rerun passed. The existing memory numbers describe the earlier individual debug build, not a new
benchmark of this combined release.

The Google-signed universal build 6 was downloaded from the upload-only bundle explorer and its
certificate matched installed build 5 exactly. Package/version, unchanged permissions and release
checks passed before `adb install -r` returned Success. The installed version is now beta.5/code 6
with the same UID 10653 and unchanged Home role. The existing custom layout is visible, Home is
foreground, and its running process has no fatal crash. No onboarding/recovery appeared. App data
was not cleared and the existing launcher was never uninstalled. Play Protect performed its
one-time scan; its global preference was not changed. Only the temporary isolated acceptance app
and test package created for this validation were removed. Play lists build 6 as Draft with zero
releases; it was not assigned to a track. Full hashes, before/after metadata and test logs live in the
external evidence directory. The matching uncommitted source snapshot is archived locally for the owner. Existing public source pages describe their earlier revisions;
this private phone update does not claim a new public source publication or broader distribution.
