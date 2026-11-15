# Play upload build 8 — 21 October 2026

The owner requested signing and uploading the latest pending changes to Google Play. This uses
the upload-only All app bundles route. No tester/production rollout, source website deployment,
phone installation, commit or push is included.

The previous combined build 7 remains preserved in its local source archive. This checkout adds
the frozen `chikku-choice-sheet` contribution based on development revision
`4ba391fc377af4936bef4b0764cad9cccde99982`. The changes replace settings choice/color dialogs and
gesture/card/widget selectors with shared bottom sheets, align their insets and neutral colors,
keep selected choices visible and searchable lists steady, and preserve nested back navigation.
Existing setting values, previews, Room schema 4 and portable backup format 1 remain unchanged.
No dependency, permission or exported component is added. Original work folders are preserved.

The prior Home return, memory, search-bar, shortcut-pin, shortcut icon and drag improvements and
release-specific behavior remain included. All three-way source merges applied without conflicts.

Candidate: `io.github.aj4abinjacob.chikku`, versionCode 8, `0.1.0-beta.7`. Exact input snapshots,
verification and Play upload results are recorded in
`chikku-update-evidence/2026-10-21/play-code8/` outside Git. The matching uncommitted source is
archived locally for the owner. Previous public source downloads describe their earlier revisions;
uploading does not imply release acceptance or public source publication.

Validation passed: debug/androidTest assembly, 233 core and 1,207 app JVM tests (zero failures,
errors or skips), debug/release lint, privacy, signed release package/bundle, dependency/notice and
native alignment checks. Two native Cards workflows passed on the Galaxy S24 Ultra, Android 16
(API 36), in 66.326 seconds, covering creation/action selection, scheduling, recreation, cancellation,
delete/undo/reorder and content persistence. They used an isolated package and in-memory fixtures;
the installed launcher version and Home role were unchanged, and the temporary packages were removed.

The first native run passed one workflow but its scroll helper overshot a scheduling control; the
same failure reproduced. The helper now searches back through the form and uses shorter corrections
once the control is visible. Both workflows then passed; production code was unchanged for this
test correction. Original attempts remain in the evidence directory. This is named workflow evidence,
not a full device matrix, TalkBack/RTL/font-scale acceptance or animation benchmark.
