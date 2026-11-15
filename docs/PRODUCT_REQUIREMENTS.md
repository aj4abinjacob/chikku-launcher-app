# Product requirements document (PRD)

**Project:** Chikku Launcher. **Stage:** implementation in progress; release acceptance remains pending. **Primary user:** Android power user who values extensive customization, reliability, privacy and long-term maintenance.

## Product principles

1. **Never strand the user:** HOME must load after corrupted settings or a broken widget.
2. **No dark patterns:** no ads, forced account or third-party tracking in core.
3. **Predictable control:** preview, undo/reset and portable export where feasible.
4. **Platform honesty:** explain unavailable actions and OEM/system restrictions.
5. **Accessibility and maintenance:** test TalkBack, keyboard, large font, reduced motion and each Android release.
6. **Independence:** original implementation and branding; no copied proprietary code or art, or misleading branding.

## Goals and non-goals

**Goals:** default HOME, reliable apps/profiles/widgets, editable grid, dock, folders, fast offline search, icon packs, gestures, portable backups, documented updates, eventually foldable layouts.

**Not in initial scope:** privileged system Recents/Quickstep on stock phones; complete feature parity with other launchers; scraping Google Discover; cloud account/sync; AI tracking; Nova backup import without legal/format verification.

## Functional requirements

| ID | Requirement | Acceptance | Milestone |
|---|---|---|---|
| FR-001 | Register as HOME; guide role selection | Can select and revert via system settings | M0 |
| FR-002 | Enumerate launchable apps/profiles | Reacts to install/uninstall/quiet-mode events | M0 |
| FR-003 | Launch app and show app details | Uses correct UserHandle; graceful errors | M0 |
| FR-004 | Persistent workspace, pages and dock | Survives process death/reboot | M0 |
| FR-005 | Drag/drop and collision resolution | Deterministic movement/undo tests | M0 |
| FR-006 | Folder create/rename/reorder | No lost items on interrupted drag | M0 |
| FR-007 | Widget picker/bind/config/resize/delete | Resize from every edge/corner on provider-supported axes; consent respected; IDs managed | M0 |
| FR-008 | Offline app search | Works without INTERNET; rapid typing safe; current typed-query Go target visibly highlighted; selectable compact-row priority direction | M0 |
| FR-009 | Recovery snapshots and schema migration | No crash loop on corrupt optional state | M0 |
| FR-010 | Icon packs and per-app overrides | Correct cache invalidation/fallback | M1 |
| FR-011 | Drawer groups, tabs and hidden apps | Persist; drawer-style multi-selection with alphabetical navigation; clear security limitations | M1 |
| FR-012 | Subgrid and configurable padding | Half-cell placement; collision tests | M1 |
| FR-013 | Dock pages, search location, folder covers | Support dock paging, search placement and folder-cover workflows; dock preview/customization and below-dock search controls available in Dock settings | M1 |
| FR-014 | Desktop/icon/folder gesture mappings | Preview, conflict warning and reset | M1 |
| FR-015 | Versioned backup export/import | Validate, preview, rollback, widget placeholders | M1 |
| FR-016 | Optional notification badges | Explicit listener consent; minimal retention | M1 |
| FR-017 | Android 15+ Private Space | Locked apps never appear in search | M1 |
| FR-018 | Foldable/large-screen layout profiles | Independent cover/inner layouts | M2 |
| FR-019 | Substring/fuzzy search and aliases | Deterministic ranking, offline; bounded app/contact typo matching implemented locally, with literal matches first | M1/M2; [scope and validation](SEARCH_FOLDER_CUSTOMIZATION.md#offline-typo-matching--2026-10-12) |
| FR-020 | Widget stacks and folder extensions | Accessibility/performance prototype first | M2 |
| FR-021 | Optional external search/feed integration | Documented APIs and consent; experimental direct Discover overlay/app-opening fallback exception described in [ADR-0004](decisions/0004-optional-google-discover.md), embedded-overlay validation pending; app-opening fallback verified on one device | M3; Discover experiment implemented locally |
| FR-022 | Accessible settings, preview/reset | TalkBack/keyboard/large-font tested | All |
| FR-023 | Offline micro-results and explicit handoffs | Bounded unit conversions; URL/map/phone/tracking actions; no requests while parsing | M1; implemented locally |
| FR-024 | Optional local search suggestions | Opt-in on-device history of apps, contacts, shortcuts, folders and sent web queries; results picked repeatedly for the same first typed letters become the top result; separate learn and show switches; pausing learning keeps or clears history on request; clear and per-item removal; hidden/locked/profile filtering; excluded from backups | M1; implemented locally |

## Non-functional requirements (targets, not measured results)

- **Reliability:** aim for at least 99.9% crash-free sessions in opt-in testing; never silently delete workspace data.
- **Performance:** no disk/network I/O on UI thread; benchmark startup, resume, drawer open, drag and search. Set P95 thresholds after testing a prototype on named reference devices.
- **Privacy:** INTERNET permission justified for off-by-default city weather and online autocomplete ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)); no ad/analytics SDK; export only on user action.
- **Security:** minimize exported components, sanitize backup input, respect profile boundaries, never call hidden apps an encrypted vault.
- **Compatibility:** proposed minSdk 29, compileSdk 37, targetSdk 36 initially (2026 Play requirement), test Android 10/12/14/15/16/17 and common OEMs.
- **Maintainability:** aim for 80%+ pure-logic coverage; widget/role instrumentation, migration fixtures, release notes.
- **Accessibility:** touch targets, focus order, high contrast, TalkBack, keyboard and reduced-motion.
- **Localization:** externalized strings, RTL and large font.

## Critical journeys

1. Install → onboarding → select HOME → app drawer → launch app.
2. Drag icon → new page → create folder → reboot → layout persists.
3. Add widget → grant bind permission → configure → resize → process death → widget recovers.
4. Change icon pack/grid/dock/gesture → export → import on second device → review missing items → commit.
5. Work profile/Private Space locks → verify no profile leakage.
6. Android upgrade → schema migration → preserve layout → recovery bundle on failure.

## Release gates

- **Alpha:** FR-001–009, no P0 crash/data-loss bug, debug APK, basic tests.
- **Beta:** FR-010–017, OEM compatibility, backup fixtures, accessible setup.
- **1.0:** stability, recovery and migration proven; security/privacy review; compatibility dashboard; release notes. Feature count alone does not qualify a release.

## Metrics without surveillance

Use opt-in volunteer testing and public issues to track setup success, backup restore success, P95 interaction latency, crash/ANR incidence, regressions after OS upgrades, time to acknowledge P0 issues and percentage of platform changes tested before stable release.

## Decisions pending

License, public-repo timing, F-Droid/Play distribution, Kotlin-first vs Launcher3 fork, whether any Nova backup format can be imported, and third-party integration terms. See [pain points](USER_PAIN_POINTS.md), [feature matrix](NOVA_FEATURE_MATRIX.md), [architecture](ARCHITECTURE.md).

## Article-requested additions (2026-10-11)

The [article comparison](NOVA_ARTICLE_PARITY.md) records the new search controls, widget overlap/
corners, dock images and settings style. FR-023/024 and appearance controls have local tests;
full device acceptance is tracked separately. No authenticated third-party account connector or
private routine/playlist learning is claimed. Image files and launch counts do not travel in backups.

## Phone-inspected settings expansion (2026-10-11)

The user authorized deeper settings parity after direct Nova 8.9.4 inspection on the available
phone. The [phone inventory](NOVA_PHONE_SETTINGS.md) is the dated scope/evidence record;
it adds no main or research-supplement identifiers and does not change the release gates.

Local additions extend existing requirements: independent label and surface styling, drawer
list/navigation/launch controls (FR-010/011/022), folder presentation controls (FR-006/020),
additional gesture mappings and Home-button scope (FR-014), badge appearance (FR-016),
offline settings and opt-in personal-folder search (FR-008/022), and optional install placement
(FR-004/005). Existing component/profile identity, recoverable edits and backup behavior apply.

Acceptance must cover old settings/backup defaults, changing controls without losing placements,
hidden/nonpersonal folder exclusion, successful versus failed launches, reduced-motion resets,
RTL/large-font/touch-target behavior, and install events while disabled, locked or in recovery.
Drawer position memory is session-only; the private-keyboard flag is advisory. Smart folders,
learned shortcut ranking, unrestricted overlap/subgrid, wallpaper zoom, external feeds/cards and
authenticated integrations remain open. See [implementation status](IMPLEMENTATION_STATUS.md)
for named checks; feature count does not imply completed M0/M1 or a published application.

## Folder paging and local cards continuation (2026-10-11)

The [continuation](FOLDER_PAGING_CARDS.md) implements horizontal folder pages and a bounded local cards editor/panel with day, time, charging and Wi-Fi visibility conditions. Existing profile isolation, recovery, deliberate action and backup requirements apply. Embedded widget hosting, Bluetooth/weather/media cards and background action automation remain open. This supersedes the earlier blanket cards gap only for the explicitly described local subset.

## Search and folder usability fixes (2026-10-11)

The [search/folder changes](SEARCH_FOLDER_CUSTOMIZATION.md) extend FR-005/006/008/013/022 with persistent search dimensions, continuous result-to-workspace drag, folder long-press editing and per-folder app icon covers. Save/cancel, lock/profile filtering, missing-member fallback and backward-compatible backups remain acceptance requirements.


## Card content extension (2026-10-11)

The [card content extension](CARD_CONTENT_EXTENSIONS.md) implements Bluetooth audio visibility, opt-in city weather, public media-session controls and separate embedded widget hosting. The earlier remaining-gap wording is superseded for this subset. Background automation, authenticated services and broad physical/OEM acceptance remain open.

## Drawer row spacing (2026-10-11)

FR-011/022 now include a visible row-count control for vertical grid/list drawers, alongside
horizontal rows per page. Automatic spacing preserves existing users' density by default.
Acceptance covers durable preferences/backups, resizing/insets, and readable large icons/labels
without changing app identity, filtering or stored placements. Evidence is tracked in
[implementation status](IMPLEMENTATION_STATUS.md#drawer-row-spacing--2026-10-11).

The [appearance control audit](SETTINGS_CLARITY_AUDIT.md) extends FR-012/013/022 with explicit inside/outside spacing labels, actual dp amounts and independent folder preview/window colors. Old colors and placements must remain intact after upgrade.

## Reliability acceptance update (2026-10-11)

FR-009/015 now have local journaled interruption recovery and durable restore/reset undo;
FR-008 separates local results from optional providers and contains their failures;
FR-002/003 revalidate profile availability and invalidate displayed snapshots on changes.
Optional media failures are contained within the card. Implemented safeguards and unit results
are recorded in [implementation status](IMPLEMENTATION_STATUS.md#priority-reliability-fixes--2026-10-11);
real process death, work/private-profile transitions and full physical restore remain separate
acceptance requirements. FR-017 is still deliberately excluded, not completed by profile filtering.

## Further reliability implementation (2026-10-11)

FR-005/009/022 now warn before desktop/dock changes hide placements, preserve the original rows, and offer atomic relocation with one Undo. Oversized widgets remain recoverable through a larger grid. FR-007 rechecks widget provider/profile identity, preserves old bindings needed for Undo and defers uncertain cleanup. FR-016 invalidates stale notification reads and distinguishes permission from an actually connected listener. Privacy copy explicitly describes opt-in search queries and weather locations; offline core and no advertising remain invariants. [Named validation](IMPLEMENTATION_STATUS.md#remaining-reliability-work--2026-10-11) does not establish reboot, battery, Private Space or broad OEM acceptance.

## Personal home reference implementation (2026-10-11)

The requested personal home-screen match adds optional minimal search-bar styling and an original
offline clock within existing home customization and widget requirements. Existing styles stay
the default; the user’s original placements must remain recoverable. Physical acceptance is
recorded separately in [home reference notes](HOME_REFERENCE_MATCH.md).

The user expanded the personal reference to the drawer. Optional wide pills, a slim scroll thumb,
manual row spacing and dynamic calendar artwork use existing drawer/icon requirements. Browser
shortcut pin consent and shortcut rendering/launching now make the confirmed x.com destination
functional. [Scope and limits](HOME_REFERENCE_MATCH.md) distinguish close appearance matching
from pixel-identical acceptance; cosmetic hiding remains unrelated to app security.

## App lock (2026-10-11, user request)

Chikku can lock chosen apps behind the system screen lock wherever it opens them. Acceptance requires
these properties. No Chikku PIN exists. Turning locks off requires authentication. Backups can't
remove locks. When the lock state is unknown, apps stay locked. Settings must state the platform
limits honestly. This is a launcher-level lock: it does not complete FR-017 (Private Space) and is
not app sandboxing. See [APP_LOCK.md](APP_LOCK.md).


## Live settings preview continuation (2026-10-12)

FR-022/014 now have [implemented sample previews and safe practice](SETTINGS_LIVE_PREVIEWS.md),
including geometry drafts before slider release, combined dock/search placement, finite motion,
effective gesture defaults and pinned/collapsible editing samples. Acceptance distinguishes
synthetic content from device fidelity and requires saved state/layout preservation, reduced motion,
accessible alternatives and missing-artwork fallback. Named checks are in implementation status;
TalkBack, broader OEM/OS acceptance and the existing release gates remain pending.

## Recovery acceptance continuation (2026-10-12)

FR-009 now requires semantic validation before ordinary Undo replaces persisted content, retention
of rejected snapshots, and safe startup when recovery flags are malformed or cannot be committed.
FR-007 must defer ID cleanup if any retained snapshot is unreadable or invalid. FR-009/015 settings
replacement must preserve damaged input durably first, including first-operation reset/import.
No new user setting, permission, dependency, schema or portable backup version is introduced.
[Implemented safeguards and actual verification](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12)
remain separate from real app-version upgrade/reboot and broader compatibility acceptance.

The 2026-10-12 accepted Redmi native run proves controlled four-store restore recovery across
real process termination and native older-schema fixtures. Real production-version upgrade/reboot
and third-party widget/notification-access acceptance remain open.

## Horizontal scrolling acceptance (2026-10-13)

The user-requested first batch extends FR-004/011/022 with independent home/drawer paging
controls, same-direction home looping, gesture accuracy, interactive indicators and synthetic
live previews. Preserve layouts/widget identity and Discover precedence; respect reduced motion,
legacy backups, cancellation and RTL. On-device accessibility, gesture feel and performance
remain separate acceptance gates.
[Implemented scope and remaining work](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).


The 16 October [MET weather migration](release/MET_NORWAY_SWITCH.md) replaces online city search with an offline catalogue while retaining chosen/restored locations, units and opt-in. Forecasts remain optional/visible-only. A smaller-place coverage limit and next-24-hour range are explicit; production and broad device acceptance remain separate.
