# Play upload build 7 — 21 October 2026

The owner requested signing and uploading the latest pending changes to Google Play. This uses
the upload-only All app bundles route. No tester/production rollout, source website deployment,
phone installation, commit or push is included.

The previous combined build 6 is preserved in its local source archive. This checkout adds the
22 frozen paths from `chikku-shortcut-drag`, based on development revision
`4ba391fc377af4936bef4b0764cad9cccde99982`. All three-way merges applied without source conflicts.
The prior Home return, memory, search-bar and shortcut-pin fixes and release-specific behavior
remain included. Original working folders and their staged/unstaged changes are untouched.

The new work adds shortcut icon selection on Home and inside folders using the existing persisted
shortcut icon fields, plus drag highlights, touch-point shadows, drop settling and haptics. It
does not change Room schema 4, backup format 1, dependencies, permissions or exported components.
Portable restore coverage now exercises edits to both Home and folder shortcut icons through the
new repository API. Their existing icon references remain stored when the pack is unavailable.

Integration review also prevents the shortcut icon editor displaying stored labels when the
filtered app/profile list no longer contains the owning package/profile. Regression coverage
includes loading, missing, mismatched and unsupported profiles.

Candidate: `io.github.aj4abinjacob.chikku`, versionCode 7, `0.1.0-beta.6`. Verification and Play
upload results are recorded in `chikku-update-evidence/2026-10-21/play-code7/` outside Git. The
exact uncommitted source is archived locally for the owner; previous public source downloads
continue to describe their earlier revisions. Uploading does not imply release acceptance or
public source publication.

Local validation passed: debug and androidTest APK assembly, 233 core and 1,181 app JVM tests
(0 failures/errors/skips), debug/release lint and the privacy gate (113 resolved modules). Native
checks were not rerun because no phone/emulator was connected. Earlier build 6 device evidence
is historical, not a test result for this upload.
