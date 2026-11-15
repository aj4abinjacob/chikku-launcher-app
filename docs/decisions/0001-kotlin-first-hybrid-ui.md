# ADR-0001: Kotlin-first hybrid UI for the launcher

- **Status:** proposed, subject to M0 spike
- **Date:** 2026-10-10
- **Decision owner:** project maintainer

## Context

A launcher must host Android AppWidgetHostView/RemoteViews, perform fast drag/drop, handle icon grids and render first frame reliably. Jetpack Compose is productive for settings, but widget hosting and highly custom workspace measurement require Android View interop and performance validation.

## Decision

Use Kotlin for new application/domain code. Build desktop, dock, folder and widget surface primarily with custom Views/ViewGroups. Use Compose Material 3 for settings, onboarding and suitable overlays. Use Room, DataStore, Coroutines/Flow and explicit module boundaries.

## Alternatives

1. Compose-only HOME: simpler shared UI toolkit but unproven for all widget/drag interactions at target complexity.
2. View-only app: proven low-level control but slower for modern settings/accessibility UX.
3. Launcher3 fork: mature HOME foundation but inherits substantial AOSP Java and system integration complexity.

## Consequences

- Two UI systems require disciplined lifecycle and theme coordination.
- Better control of AppWidgetHostView and hit testing.
- Pure Kotlin layout engine remains UI-toolkit-independent.
- Must benchmark and revisit after a working prototype; this is not dogma.

## Validation

Build HOME + 6x8 grid + drag/drop + 3 third-party widgets + Compose settings. Measure latency/jank and test process death on API 35–37 and one OEM. If interop complexity dominates, revise ADR.

Related: [ARCHITECTURE.md](../ARCHITECTURE.md).
