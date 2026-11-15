# Contributing to Chikku Launcher

**Current phase:** M0 implementation in progress. The Android Gradle project (`:app`, `:core:model`) is bootstrapped and M0 features are being built; see [implementation status](docs/IMPLEMENTATION_STATUS.md) for what exists and what has been verified. Research contributions to the [feature matrix](docs/NOVA_FEATURE_MATRIX.md), [pain points](docs/USER_PAIN_POINTS.md) and Android compatibility notes remain welcome. Build instructions are in [README.md](README.md#build-and-run) and [DEVELOPMENT_SETUP.md](docs/DEVELOPMENT_SETUP.md).

## Before contributing

- Read [README.md](README.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md), and [licensing](docs/LICENSING_AND_LEGAL.md).
- Do not submit copied or decompiled proprietary code, third-party branded assets without permission, or undocumented privileged API hacks.
- Cite versioned evidence for claims about other launchers. Distinguish observed bugs from hypotheses and requests.
- The repository is currently private; external contributions become practical after separately authorized publication. Project-owned code already uses GPL-3.0-or-later.

## Coding standards

- Kotlin for new code; idiomatic null-safety, Coroutines/Flow, structured concurrency.
- Small cohesive modules and constructor-injected dependencies.
- Pure Kotlin tests for layout/search/backup logic; instrumentation for widgets, profiles and HOME role.
- Never block UI thread, silently discard workspace state or add tracking dependencies.
- Accessible touch targets, TalkBack labels, keyboard and RTL support.
- No new permissions without rationale and privacy review.
- Formatting: official Kotlin style; [.editorconfig](.editorconfig) holds the ktlint settings used by CI.

## Pull request checklist

Use the [pull request template](.github/PULL_REQUEST_TEMPLATE.md). In short:

- Linked issue, requirement ID and acceptance criteria.
- `./gradlew assembleDebug :core:model:test testDebugUnitTest lintDebug` and `scripts/check-privacy.sh` pass (CI runs the same).
- Unit/instrumentation tests where applicable; device or emulator, Android version and OEM build recorded for runtime changes.
- Manifest, permission and dependency changes justified; privacy/security/accessibility impact noted.
- Room schema changes include a version bump, the exported schema in `app/schemas/` and a migration test.
- No secrets, keystores or personal logs.
- [CHANGELOG.md](CHANGELOG.md) and [implementation status](docs/IMPLEMENTATION_STATUS.md) updated.

Releases follow the [release checklist](docs/RELEASE_CHECKLIST.md).

## Research contributions

Use source links, capture app build/Android version, label confidence and indicate whether an issue is resolved in later releases. Never present one Reddit report as a population statistic.

## Community conduct

Be respectful, focus on reproducible evidence, and avoid personal attacks or unsupported claims about competitors. See [SECURITY.md](SECURITY.md) for vulnerabilities.
