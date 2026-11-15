# ADR-0003: M0 project foundation (toolchain, identity, modules, persistence units)

- **Status:** accepted for the M0 spike; revisit at the M0 architecture checkpoint
- **Date:** 2026-10-10
- **Related:** [ADR-0001](0001-kotlin-first-hybrid-ui.md), [ADR-0002](0002-build-vs-fork.md), [ARCHITECTURE.md](../ARCHITECTURE.md), [DATA_MODEL.md](../DATA_MODEL.md)

This ADR records the bootstrap choices that the spike needed. It does **not** settle ADR-0002
(clean-room vs Launcher3/Lawnchair fork). That decision still waits on spike evidence: widget
hosting, drag latency and device testing.

## Decisions

| Topic | Decision | Reason |
|---|---|---|
| Application ID | `io.github.aj4abinjacob.chikku` (`.debug` suffix for debug builds) | Suggested in DEVELOPMENT_SETUP.md; tied to the owner's GitHub namespace; debug suffix lets a debug build coexist with a future release build |
| SDK levels | minSdk 29, compileSdk 37, targetSdk 36 | As proposed in README/ANDROID_APIS. RoleManager.ROLE_HOME exists from API 29. Target 37 waits for RemoteViews memory validation |
| Toolchain | AGP 9.4.1 (built-in Kotlin), Kotlin 2.4.21, KSP 2.3.12, Gradle 9.7.1 | Latest stable releases on Google Maven / Maven Central / services.gradle.org, checked 2026-10-10 |
| JVM target | Bytecode 17; JDK 21+ runs Gradle and the tests (amended 2026-10-13: Robolectric needs Java 21 for the SDK 36 unit tests, and CI on JDK 17 failed every Robolectric class). Android Studio's bundled JBR 25 is verified locally | No Gradle toolchain auto-provisioning, so builds don't download JDKs implicitly |
| Modules | `:app` + `:core:model` (pure Kotlin/JVM) | ARCHITECTURE.md says to start small; layout engine and search stay Android-free and fast to test. Other modules are extracted when boundaries prove useful |
| DI | Manual constructor injection through `AppContainer` | The dependency graph is small; Hilt adds annotation processing and build time without benefit yet |
| Persistence | Room (KSP), schema exported to `app/schemas`, never destructive migration | DATA_MODEL.md invariants; schema files enable migration tests |
| Grid units | Domain/engine use whole cells; Room stores **half-cell units** (cells × 2) | Subgrid (FR-012) later needs no column changes. Conversion happens only in the repository |
| Preferences | DataStore deferred; tiny startup flags use SharedPreferences on a background dispatcher | M0 has no user-facing settings. DataStore is added with the first real preference |
| UI | View-based HOME (custom ViewGroups) + Compose settings/onboarding | ADR-0001 |
| Network | Initial bootstrap omitted INTERNET; revised by [ADR-0005](0005-optional-web-search-suggestions.md) for off-by-default autocomplete. CI continues to reject other forbidden permissions and tracking SDKs | Core stays offline; optional typed-query network access is disclosed |
| Platform backup | `allowBackup=false` / extraction rules exclude data | Until Chikku's own versioned backup (FR-015) exists, platform restore could reintroduce device-local IDs |
| Private Space | `ACCESS_HIDDEN_PROFILES` not declared in M0, so hidden profiles are not surfaced | ANDROID_APIS.md: surfacing them requires a dedicated locked container (FR-017, M1). Not declaring is the safe default |
| Licence | Initially unselected at bootstrap; now **GPL-3.0-or-later** for project-owned code | Root LICENSE retained; third-party obligations and recipient source access remain separate (LICENSING_AND_LEGAL.md) |

## Consequences

- Room stores half-cell units, so the schema is ready for subgrid. Partial-cell placement still
  needs engine work.
- If the spike pivots to a fork (ADR-0002 option B/C), the `:core:model` contracts and tests are
  the most reusable part. The View code may be replaced.

## Rollback / review triggers

- AGP/Kotlin incompatibilities or CI instability → pin to previous stable minor.
- Graph grows past ~15 injected components or needs scoped lifetimes → evaluate Hilt.
- First user setting → introduce DataStore with migration from any SharedPreferences flags.
