# Implementation review and additional verification cases

Dated supplement, 2026-10-10. Primary documents: [architecture](../../ARCHITECTURE.md), [data model](../../DATA_MODEL.md), [roadmap](../../ROADMAP.md), [test strategy](../../TEST_STRATEGY.md), [build-vs-fork ADR](../../decisions/0002-build-vs-fork.md).

This is a research review, not an accepted replacement architecture. No Android build or device test was executed.

## Decisions to retain

Keep the main Kotlin-first/hybrid-UI direction provisional. Compare its actual widget/drag/recovery spike against a pinned licensed reuse candidate. Do not change the selected engine just because this supplement favours comparing reuse earlier.

Retain a single owner for workspace state. For an original implementation, the proposed Room model can be appropriate. For a reused engine, map its schema before adding another database that competes to own placements.

Current-platform validation includes Android 17/API 37 [NS48 in the supplement source register]. Minimum Android 10/API 29 remains a proposal. Store/SDK requirements must be rechecked when building.

## Licensing correction

Current Lawnchair 16-dev LICENSE.txt contains an Apache-2.0 root notice [NS26]. The historical GPL reference in the initial planning set should not classify the whole current candidate. Conversely, a root notice does not prove every dependency or asset is Apache-2.0.

Pin the exact revision and inspect source/asset/dependency licences, preserve notices, and choose compatible distribution terms. Kvaesitso, Neo and Fossify have different licence evidence; they cannot all be treated as interchangeable permissive code.

## Reliability requirements that deserve explicit design

**Identity:** component plus profile; independent workspace ID; shortcut publisher/ID. Labels, positions and package name alone are insufficient. On-device profile serials cannot be blindly reused on a different phone.

**Widget operations:** OS allocation/binding/configuration and database updates are not one atomic transaction. Use a journal or equivalent staged recovery state. Fresh widget IDs are needed after device migration. Clean orphan IDs after interruption while preserving logical placeholders.

**Visibility:** apply one consistent policy before search, ranking, folders, suggestions, badges and export previews. When a private profile locks, invalidate pending query results and sensitive cached representations. Recheck availability at launch time.

**Restore:** parse into staging, bound resource use, validate schema/coordinates/container graph/actions, preview differences, snapshot the current setup and then commit. Never turn a parsing failure into a silent default layout.

**Package reconciliation:** distinguish component removal from transient profile lock, suspension, archive and package update. Preserve unresolved placements until a deliberate user action.

**Recovery:** a repeated-crash route must preserve the original database/snapshot. Provide recoverable last-known-good state or widget-safe mode; do not automatically wipe layouts.

## Additional acceptance checklist

These CV identifiers complement the main test strategy; they are not claims of execution.

| Case | Workflow | Required outcome |
| --- | --- | --- |
| CV01 | Two activities in one package, copies in different profiles | Correct activity/profile identity and launch |
| CV02 | Profile locks while a search provider is returning results | No stale private result/icon/label appears |
| CV03 | Cached private shortcut tapped after lock | Launch rejected/handled without bypass or disclosure |
| CV04 | Private profile hidden in system settings | Launcher honours container visibility and clears derived surfaces |
| CV05 | Widget binding interrupted by kill or denied configuration | Recoverable placeholder; no orphan host-ID accumulation |
| CV06 | Widget provider updated/removed/force-stopped | Stable placement, explicit resolution state, no silent deletion |
| CV07 | Dense overlapping widgets, rotation and reboot | Persisted layering and predictable touch/accessibility order |
| CV08 | Backup moved to a phone with different size/profile serials | Deliberate remapping, preview, new widget IDs |
| CV09 | Newer schema, malformed action, cyclic folder or invalid coordinates | Reject safely before live state changes |
| CV10 | Archive traversal, excess entries or decompression expansion | Bounded parser; no arbitrary file writes |
| CV11 | Restore interrupted between DB and system binding operations | Journal recovery and rollback snapshot remain valid |
| CV12 | Manual drawer order followed by new app install/update | Existing order unchanged; explicit placement policy |
| CV13 | Badge group summary plus child notifications | Documented count without duplicated summaries |
| CV14 | Permission/service revoked during badge/contact/usage feature | Core apps remain usable; feature-specific status |
| CV15 | Many rapid search queries with slow/failed optional provider | Latest query only; local results unaffected |
| CV16 | Immediate icon tap after Home using OEM gestures | App-owned dead zone eliminated; system limitations recorded |
| CV17 | Large text, tiny visual icons, TalkBack and reduced motion | Essential actions readable and reachable without gestures |
| CV18 | Android 17 large-screen resize/orientation changes | No frozen layout assumption; actual target/API behaviour tested |
| CV19 | Crash loop, low storage, boot before credential state available | Recovery preserves original data and provides usable state |
| CV20 | Release, upgrade, export and offline privacy audit | Source/variant/signature traceability; no hidden networking |

## Nova migration gate

No real Nova backup fixture was provided in this task. Do not promise universal import. Define supported versions/channels, sanitise fixtures, compare expected logical layouts, report unsupported entries and preserve the current workspace when import fails.

Widget-provider internal state is not generally owned by the launcher. Restore can require user reconfiguration; do not claim a copied host ID makes the widget portable.

## Proposed privacy boundary

Core can omit INTERNET permission; users can open external search in their browser. Other applications and hosted widgets have their own network permissions. That distinction must appear in test reports and privacy wording.

Do not add broad storage/SMS/call-log permissions for wallpaper or badge effects. Use scoped user-selected assets and active-notification semantics. Cosmetic hiding must be labelled separately from Android Private Space.

## Evidence reporting

Report actual unit, instrumentation, emulator, physical-device and benchmark coverage separately. A compile or screenshot is not proof of workflow correctness. Publish benchmark workload, device, OS, refresh rate, sample count and p95; proposed latency budgets are not measurements.

## Outstanding implementation work

Engine comparison, real fixtures, device matrix, dependency licence audit, signing/distribution, interviews and beta usage. This supplement completes desk research; those execution gates remain open.

