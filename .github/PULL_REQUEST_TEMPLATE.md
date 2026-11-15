## Summary

Describe the user problem and solution; link issue, requirement ID (FR-xxx) and acceptance criteria.

## Build and automated checks

- [ ] `./gradlew assembleDebug :core:model:test testDebugUnitTest lintDebug` passes
- [ ] `scripts/check-privacy.sh` passes
- [ ] New or changed logic has unit tests; instrumentation tests added where HOME, widgets, profiles or gestures changed

## Device verification

- [ ] Not applicable (no runtime behaviour change), or:
- Device/emulator used:
- Android version / API level and OEM build:
- What was exercised and the result (emulator and physical-device results are different evidence):

## Manifest, permissions and dependencies

- [ ] No manifest change, or the merged-manifest diff is described below
- [ ] No new permission, exported component or dependency, or each has a stated reason below
- [ ] No ads, analytics, crash-reporting or tracking SDK; no network access added to the core
- [ ] Profile/Private Space behaviour reviewed if relevant
- [ ] No secrets, keystores, private logs or personal data

## Data and migrations

- [ ] No Room schema change, or: schema version bumped, exported schema in `app/schemas/` committed, migration test added
- [ ] Backup/restore fixtures updated if the persisted format changed

## Accessibility

- [ ] Content descriptions, font scale, RTL and dark mode checked for changed UI

## Documentation

- [ ] [CHANGELOG.md](../CHANGELOG.md) "Unreleased" entry added
- [ ] [docs/IMPLEMENTATION_STATUS.md](../docs/IMPLEMENTATION_STATUS.md) updated (status and evidence match what was actually verified)
- [ ] README/architecture/roadmap updated as needed

## Screenshots / notes

Attach only redacted test data.
