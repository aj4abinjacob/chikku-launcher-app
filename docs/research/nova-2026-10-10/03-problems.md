# Supplement: problems, unmet needs, and proposed fixes

Date: 2026-10-10. [Sources](04-sources.md) distinguish official evidence from user reports. NP/NU identifiers belong to this supplement and do not replace the main product/backlog identifiers. These are problems to prevent or workflows to improve in **Chikku**, not changes made to Nova.

Priority P0 = availability/data/privacy failure; P1 = valuable daily-driver improvement; P2 = later customisation. Priority is our judgment, not measured complaint frequency.

## Evidence-backed problem register

| ID | Signal and context | Evidence / present status | Chikku response | Priority / acceptance |
| --- | --- | --- | --- | --- |
| NP01 | Ads interrupt launcher use; some paid users reported ads | Official free-tier ads; January Prime reports, mixed outcomes [NS02, NS13] | No ad SDK, sponsored entry, or entitlement dependency | P0; offline/dependency audit |
| NP02 | Concern about collection and ownership changes | Published policy and user concerns; no runtime audit [NS03, NS21] | Local core, auditable dependencies, no telemetry by default | P0; local-only data and dependency review |
| NP03 | Search crashes during app selection | Historical 8.1.5/Android 16 thread [NS14]; not established in latest Nova | Isolate providers; stale-query cancellation; never crash core search | P0; rapid-query and faulty-provider tests |
| NP04 | Widget adding/resizing and startup crashes | Owner reported fixes in February [NS12] | Recover provider failures and interruption without losing workspace | P0; real widget/lifecycle tests |
| NP05 | Wallpaper picker/action widget crash | May author edit says fixed in 8.7.7 [NS15, C] | Keep as regression case, not a currently unresolved accusation | P1; regression picker/provider tests |
| NP06 | A backup fails across channels/versions | Older mismatch reports [NS18, NS19, C] | Explicit compatibility inspection and staged restoration | P0; version validation and rollback |
| NP07 | Backup applies but layout differs | Cross-device restoration report [NS19, C] | Preview adaptation; preserve original snapshot; remap widget bindings | P0; cross-device preview/rebind tests |
| NP08 | Private Space cannot be reached through launcher | July and September reports [NS16, NS17]; APK untested | Correct separate private-profile container and system authentication | P0 for supported feature; lock/unlock/race and cache tests |
| NP09 | Hidden apps still appear elsewhere | Documented launcher-only hiding [NS02] | Explicit per-surface hide policy; explain its scope | P1; per-surface visibility tests |
| NP10 | User wants manual drawer sorting | Explicit FAQ limitation; adjacent-project request [NS02, NS47] | Manual drag order, folder/tab ordering, reset to alphabetic | P1; persistent order and new-app tests |
| NP11 | Different apps need different badge styles | Explicit FAQ limitation [NS02] | Per-app override after correct notification data model | P2; style and aggregation tests |
| NP12 | Badges disappear following OS update | March report, cause unverified [NS20] | Permission/service state indicator and reversible recovery flow | P1; permission revoke/reconnect tests |
| NP13 | Unclear changelog and version changes | Indexed changelog complaint and stale web history [NS24, NS06] | Link release notes, commits, migrations, limitations, artifact identity | P1; source/artifact/release-note checks |
| NP14 | Public requests are difficult to follow | Official tracker is internal [NS05] | Public issue/discussion status after open-source publication | P1; visible public issue lifecycle |
| NP15 | Alternatives lose combinations of features | January replacement discussion [NS21] | Preserve configurable workflows; offer presets without forced simplification | P1; representative setup tasks |
| NP16 | Dock width/indicator/context menu gaps in alternatives | Specific Lawnchair migration discussion [NS22] | Reachable dock layouts and explicit edit/remove/store controls | P1; reachability and context actions |
| NP17 | Overlapping widgets have unstable layering in an alternative | Direct Lawnchair discussion [NS22]; not a Nova complaint | Persist z-order and define interaction priority | P2; persisted z-order across reloads |
| NP18 | Users want reliable small icons and custom widgets | September alternative request [NS23] | Dense visual layouts with usable hit targets and real KWGT tests | P1; KWGT, dense-grid and hit-target tests |
| NP19 | Drawer/folder blur missing | FAQ exclusion [NS02] | Optional platform-compatible blur with readable fallback | P2; blur-disabled/opaque fallback tests |
| NP20 | Per-page/separate drawer wallpaper missing | FAQ exclusion [NS02] | Prototype later; do not imply universal system-wallpaper support | P2; capability and wallpaper-permission tests |
| NP21 | Gesture/overview bugs with third-party launchers | Platform-owned integration boundaries [NS41] | Test and publish per-device behaviour; improve owned transitions | P1; named-device navigation matrix |
| NP22 | Expectation that numeric badge means all unread items | Notification-based model and retired provider [NS09, NS10] | Label as notification count; no SMS/call-log scraping | P1; notification semantics tests |

## Product proposals, not established Nova defects

| ID | Proposed improvement | Why consider it | Validation needed |
| --- | --- | --- | --- |
| NU01 | Undo/redo layout edits | Drag mistakes are costly when a home screen is heavily personalised | Observe editing tasks and accidental moves |
| NU02 | Automatic local snapshots | Manual backups do not cover every interrupted edit/upgrade | Storage budget and process-kill recovery tests |
| NU03 | Preview theme/grid changes | Users should see the effect before committed placement changes | User comprehension and no clipping at dense grids |
| NU04 | Searchable settings with aliases | Features can exist yet remain difficult to find | Find-this-control tasks; e.g. badges/unread/dots |
| NU05 | New-app placement policy | Avoid surprising rearrangement after installation | Test append/new section/prompt options |
| NU06 | One-handed folder placement | Useful on large phones | Test reachability without harming tablets/landscape |
| NU07 | Portable setup format | Removes long-term dependence on one application | Schema evolution and cross-device import tests |
| NU08 | Simple/power-user presets | Different users need different detail levels | Verify presets do not remove access to advanced settings |
| NU09 | Permission health page | Optional integrations can fail when access is revoked | Permission deny/revoke and service reconnect tests |
| NU10 | Reduced motion/high-contrast modes | A highly themed launcher still needs predictable interaction | TalkBack, enlarged text, contrast and motion tests |

These proposals may be useful even when Nova already has parts of them. Do not market them as unique without verifying competitors.

## Prioritisation decisions

- Ship reliable launching, widget hosting, recoverable layout and offline operation before blur, cards, or feed integrations.
- Private Space is a security-sensitive parity gap on supported systems. Unsupported devices receive a clear unavailable state, not a substitute “secure folder” illusion.
- Nova migration is valuable but remains conditional on real fixture support. Manual rebuilding must remain possible.
- Preserve icon/folder/dock gestures, but provide visible accessible alternatives. Gesture-only discoverability is insufficient.
- Manual sorting is a small, understandable addition once identity and persistence are sound.
- Native stock animations, stock Recents replacement, screen wake, and total unread counts across arbitrary apps are not universal acceptance requirements.

## What “not being considered” means here

Public evidence can establish an unsupported feature or a user's unanswered request. It cannot establish the developer's private priorities or motives. Nova says requests are handled internally [NS05]. Chikku's response is to make decisions and status observable.

## Research-to-backlog discipline

Each issue implemented later should include: affected workflow, evidence/observation, device and version, owning layer, supported behaviour, acceptance test, and data-loss/privacy risk. Update this register when reproduction or resolution changes the conclusion.

