# Nova Launcher — initial research synthesis

**Date:** 2026-10-10. This is an evidence-linked baseline for planning, not the final exhaustive audit. See [source register](RESEARCH_SOURCES.md).

## Current status matters

Nova was acquired by Branch in 2022 [S04], experienced team uncertainty in 2024 [S05], and was acquired by Instabridge in January 2026 [S01]. **It is inaccurate to say Nova is currently abandoned.** The new owner has resumed updates; a February 2026 team post reports crash fixes [S11], and the English Play listing shows an update dated 7 October 2026 as checked on 10 October [S02]. Historical failures are useful **regression cases**, not necessarily unresolved bugs.

The official Play listing now labels the free app as containing ads [S02]. Instabridge says existing Prime purchases will be honored and Prime will remain ad-free [S01]. Some users dispute privacy/SDK practices, but public tracker tests conflict [S31]; do not assert spyware or data selling without reproducible evidence.

## Major user problems / engineering opportunities

| Area | Evidence | Chikku response |
|---|---|---|
| Search crashes and blank search UI | 2025 [S07][S10]; some fixes claimed 2026 [S11] | Pure local index, crash isolation, deterministic ranking, fuzz tests, graceful empty/error UI |
| Home/setup crashes, icons disappearing | 2026 reporting [S06], 2025 [S10] | Transactional layout model, recovery snapshot, package/profile event reconciliation |
| Widgets disappear/freeze or crash picker | [S08][S29]; some reports resolved | Correct host lifecycle, bind/resize state machine, placeholder and rebind UX |
| Backup/restore fails | [S09][S10] | Versioned export, preview, migration, cross-device reconciliation, import rollback |
| Gesture and Recents incompatibility | [S18] plus OEM reports | Honest platform capability matrix, graceful fallback; do not promise privileged Quickstep |
| Maintenance and trust uncertainty | [S01][S05][S31] | Public release cadence, changelog, opt-in diagnostics, reproducible builds |
| Foldable independent layouts | 2026 [S12][S13] | Layout profiles by display class/posture with deliberate migration |
| Power-user muscle memory | [S14][S16][S17][S35] | Subgrid, folder covers, icon swipes, dock pages, gesture mapping, fast search |

## Under-served / requested improvements (not confirmed absent from Nova)

A March 2026 community suggestion asks for widget stacks, free resizing, glass effects, multi-row docks, large folders, substring search and different icon shapes by surface [S15]. Foldable users ask for separate cover/inner layouts [S13]. These are **demand signals**, not proof that Nova's team has no plans. Chikku should validate demand and feasibility before implementing.

## What Nova users value most

From official feature descriptions [S02][S03] and replacement discussions [S14][S16][S17][S35]:
- Fine-grained home-screen grid and subgrid positioning.
- Icon packs, icon shape/size, per-icon overrides and labels.
- Drawer tabs/folders and hidden apps.
- Gesture mapping, including per-icon swipes and folder cover tap/swipe semantics.
- Paged/scrollable dock and a configurable search entry point.
- Backup/restore of years of personalized layouts.

A Nova alternative without these behaviors will not feel like a true replacement even if it has polished visuals.

## Architectural recommendation

Build a **Kotlin-first, public-API launcher**, with a custom View-based workspace/widget surface and Compose settings. Room stores transactional workspace data; DataStore stores preferences; LauncherApps provides apps/profiles; AppWidgetHost handles widgets. Use AOSP Launcher3/Lawnchair for **architectural reference and feasibility comparison**, not a blind fork. A fork reduces bootstrap risk but adds a large inherited Java/AOSP/Quickstep codebase and license obligations [S26][S27][S28].

Android 15 Private Space requires HOME role, ACCESS_HIDDEN_PROFILES and dedicated locked/unlocked UI [S22]. Android 17 has additional memory and widget restrictions [S23][S24]. New Play submissions as of August 2026 must target API 36+ [S25]. System Recents/gesture animation integration can require privileged/OEM/root paths; avoid claiming a universal fix [S18][S26].

## Positioning and differentiation

**Chikku should be:** reliable, offline-first, privacy-verifiable, deeply customizable, community-governed, and clear about Android limits. It should **not** simply clone Nova branding or promise feature parity in v1.

Proposed delivery order:
1. **Foundation:** selectable HOME, app list, persistent workspace, drag/drop, folders, widget host, crash-safe startup.
2. **Nova essentials:** icon packs, subgrid, drawer organization, dock customization, gestures, fast search, backup.
3. **Differentiation:** foldable layouts, widget stacks, deeper search, import tooling, accessible settings.
4. **Polish:** animations, theming, optional extensions, extensive OEM compatibility.

## Open research questions

- Which Nova 8.x/9.x settings are present in the current stable release? Inventory includes unverified rows.
- Can Nova backups be imported legally and safely, and which fields can be mapped? No compatibility claim yet.
- What OEMs have the highest launcher breakage? Needs real-device data, not just Reddit.
- Which extensions can work entirely through public APIs? Google feed, Sesame-like search and Quickstep may require external agreements or privileged hooks.
- What is the chosen open-source license and distribution model?
- How much effort can be sustained for a multi-year Android compatibility program?

See [pain points](USER_PAIN_POINTS.md), [feature matrix](NOVA_FEATURE_MATRIX.md), [architecture](ARCHITECTURE.md), and [roadmap](ROADMAP.md).

