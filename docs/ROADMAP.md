# Roadmap and engineering backlog

## Play-first preparation — 2026-10-15

The owner authorized the completed private development-repository rename and fresh private
release repository, not production/publication. [Release preparation](release/PLAY_PREPARATION.md)
and the [checklist](RELEASE_CHECKLIST.md) add recipient source delivery, provider service rights,
privacy hosting, signing and account-specific testing gates. Existing native acceptance gaps
remain; no milestone or provisional architecture decision is silently accepted.

## Release acceptance backlog — 2026-10-14

The [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) leaves all three requested areas partially verified and the production release
**NO-GO**. Cards naming/disabled behavior and genuine historical development-APK replacement/
service restore have API29 evidence. Remaining ordered work: passing full corrected Cards editor
and API36/TalkBack coverage; bounded actual reboot/first unlock; passing corrected SAF producer
and picker workflow; genuinely configured external-widget consent/restart/provider removal/
fresh-ID rebind and fresh restore; complete managed pause/resume/removal and API35+ private
exclusion; representative OEM/form-factor/performance/battery/soak and distribution gates.
Private Space UI remains deliberately unsupported in this milestone. This validation authorizes
no publication, repository visibility change, public repository creation or automatic main merge.

**Status:** implementation in progress; milestones remain acceptance gates. See [implementation status](IMPLEMENTATION_STATUS.md) for local code and evidence. The full customization backlog remains a multi-release project.

## Milestones

| Stage | Goal | Exit gate |
|---|---|---|
| M0 Foundation | Selectable HOME, app list, workspace/dock, folders, widgets, offline search, Room, recovery | Real-device HOME selection and persistence; no P0 bugs |
| M1 Essential customization | Icon packs, subgrid, drawer tabs/folders, hidden apps, dock pages, icon gestures, backups, work/private profiles | Five representative launcher workflows reproducible and migration tests green |
| M2 Modern differentiation | Independent foldable layouts, fuzzy search, widget stacks, large folders, surface-specific styles | Cross-posture/device test matrix and accessibility review |
| M3 Optional ecosystem | External integrations, advanced effects, community extensions | Privacy/API/legal review and opt-in design |
| M4 Maintenance | Android update validation, reproducible releases, documentation and contributor support | Sustained release process, not a one-time milestone |

## M0 ordered implementation backlog

| Order | Work item | Deliverable | Test |
|---|---|---|---|
| 01 | Initialize Android Studio Kotlin project | Gradle Kotlin DSL, version catalog, AndroidX, package name decision | CI assembleDebug |
| 02 | HOME manifest + onboarding | MAIN/HOME/DEFAULT activity and role request | Set/unset default HOME |
| 03 | Define app/profile domain model | UserHandle-aware app identity, repository interface | Unit tests |
| 04 | Integrate LauncherApps | Enumerate, launch, package/profile callbacks | Install/uninstall test |
| 05 | Build workspace layout engine | Grid occupancy, pages, collision resolution | Property tests |
| 06 | Add Room entities/DAO/migrations | Atomic workspace updates and recovery snapshot | Migration fixtures |
| 07 | Build View-based desktop/dock | Icons, pages, insets, accessibility labels | UI smoke tests |
| 08 | Implement drag/drop | Move/reorder, cross-page, cancel/undo | Gesture tests |
| 09 | Implement folders | Create, rename, reorder, open/close | Process-death tests |
| 10 | Build all-apps drawer | App list, sort, app launch, empty/error state | UI tests |
| 11 | Add offline search | Fast prefix search, cancellable Flow pipeline | Fuzz/race tests |
| 12 | Integrate AppWidgetHost | Picker, bind, configure, resize, remove | Real widget tests |
| 13 | Lifecycle hardening | Recreate, rotate, work profile, icon cache invalidation | Device matrix |
| 14 | Recovery mode | Safe startup, corrupt DB handling, restore last snapshot | Fault injection |
| 15 | Accessibility baseline | TalkBack, keyboard, large font, reduced motion | Manual audit |
| 16 | Benchmark | Startup, drawer, drag, search, memory | Macrobenchmark report |
| 17 | Security/privacy baseline | Permissions review, no tracking/network by default | Manifest/dependency scan |
| 18 | Alpha release | Signed test artifact, release notes, known issues | Release checklist |

**First vertical slice:** HOME role → LauncherApps list → tap to launch → survive process death. Complete that before implementing theme customization.

## M1 backlog, ordered by user value

1. Icon pack discovery, icon cache, fallback and per-app overrides.
2. Subgrid/half-cell positioning and independent spacing controls.
3. Drawer tabs/folders, hidden apps, personal/work tab.
4. Folder covers (tap app/swipe folder) and per-icon gesture actions.
5. Dock pages, dock icon sizing and search below/inside dock.
6. Versioned portable backup, import preview, missing-app mapping, widget rebind.
7. Notification badges behind explicit listener consent.
8. Android 15+ Private Space dedicated locked container and privacy tests.
9. System/OEM gesture capability matrix and graceful fallback.
10. Beta release with compatibility dashboard and migration fixtures.

## M2 candidates (validate demand before committing)

- Independent layouts for foldable cover/inner and tablet/landscape.
- Widget stacks, freely resized widget affordances and large folders.
- Substring/acronym/fuzzy app search with aliases. Bounded app/contact typo matching and custom-name
  ranking are implemented locally as a user-requested subset (2026-10-12); acronym matching and
  physical performance/accessibility acceptance remain open. See [matching scope](SEARCH_FOLDER_CUSTOMIZATION.md#offline-typo-matching--2026-10-12).
- Per-surface icon shapes and expanded theme controls.
- Additional gestures, accessibility and animation polish.

## Prioritization formula

User-requested early experiment: optional Google Discover cards through a direct connection to the installed Google app,
implemented locally with the switch off by default. This does not complete M3/FR-021 or change
the M0 release gates. Remaining acceptance work: current Google app on a physical device (including the app-opening fallback),
provider removal/update, offline behavior, TalkBack and process recreation. The unofficial
protocol and its limits are recorded in [ADR-0004](decisions/0004-optional-google-discover.md).

Use **user severity × frequency evidence × fixability × strategic value / engineering effort**, but do not turn Reddit vote counts into prevalence. Any P0 crash/data-loss/privacy bug blocks feature work until triaged.

## Rough planning envelope (not a delivery promise)

For one experienced Android developer, M0 is plausibly **several months** because widget hosting, drag/drop and recovery are complex; broad launcher customization can take **a year or more** with testing across OEMs. Re-estimate after a two-week technical spike and a running HOME prototype. AOSP Launcher3 fork may shorten initial feature work but increases inherited maintenance/licensing costs.

## Definition of done for every item

- Reproduction/user story and acceptance test linked.
- No new main-thread I/O or unbounded allocation.
- Unit/instrumentation test at appropriate level.
- Accessibility and profile/Private Space review.
- Device/API coverage recorded.
- Documentation/changelog updated.
- No unexplained new permission or network dependency.

## Release cadence proposal

- Weekly triage; security and P0 fixes immediately prioritized.
- Nightly CI; beta every 2–4 weeks when active; stable releases based on test gates, not calendar alone.
- Quarterly Android SDK/Play policy compatibility review.
- Public issue templates, roadmap and changelog once repository is public.
- Two maintainers and a handoff plan are long-term governance goals.

See [TEST_STRATEGY.md](TEST_STRATEGY.md) and [MAINTENANCE_POLICY.md](MAINTENANCE_POLICY.md).

## User-requested search addition (2026-10-10)

Dock settings now expose below-dock search, the shared web engine and off-by-default Google/DuckDuckGo
autocomplete. Implementation and validation are tracked in [implementation status](IMPLEMENTATION_STATUS.md)
and [ADR-0005](decisions/0005-optional-web-search-suggestions.md); this does not complete the optional
ecosystem milestone. Remaining acceptance includes TalkBack/large-font/RTL, airplane-mode workflow,
and a broader OEM matrix.

## User-requested hidden-app navigation (2026-10-11)

Hidden-app settings now provide a drawer-style icon grid for selecting/deselecting multiple apps,
with a tap/drag alphabet rail. Local checks and pending device/accessibility acceptance are recorded
in [implementation status](IMPLEMENTATION_STATUS.md#hidden-app-selection-grid--2026-10-11).
This adds the requested G14 interaction without completing M1 or changing hiding's cosmetic scope.

Dock customization was expanded locally on 2026-10-11 from a device-captured Nova dock reference:
preview, desktop-size matching, labels, independent padding, background corners, infinite scroll,
overlay/auto-close and remembered page. See [dock settings](DOCK_SETTINGS.md) and the named
verification in [implementation status](IMPLEMENTATION_STATUS.md). This does not complete M1
or change the provisional architecture decision.

## Article-requested search and settings (2026-10-11)

FR-023/024 add offline micro-results and optional local personal-app suggestions. Widget overlap/
corners and dock image selection are exposed in settings, with a compact preference-list design.
See [article comparison](NOVA_ARTICLE_PARITY.md). Remaining acceptance includes real document
selection/lost access, external handoffs, third-party widgets, long-term learning and accessibility.
Authenticated service integrations require supported provider APIs and separate consent/setup;
app-published shortcuts remain the available integration path.

## User-requested phone settings pass (2026-10-11)

The [device inspection inventory](NOVA_PHONE_SETTINGS.md) records the expanded home, drawer,
folder, search, gesture, badge and popup controls implemented locally. Existing M0/M1/M2 exit
gates and the provisional foundation decision remain unchanged. Named integration/device evidence
belongs in [implementation status](IMPLEMENTATION_STATUS.md), rather than treating every observed
reference control as a completed Chikku workflow.

Remaining acceptance includes TalkBack, large-font/RTL and other OEMs, real install events with
disable/lock/recovery transitions, process recreation and backup compatibility, folder-search
visibility changes, and physical gesture conflicts with widgets/icons/page navigation. Drawer
position memory remains session-only. Open parity work includes subgrid, horizontal folder pages,
smart/frequent drawer groups, learned shortcuts, independent system-bar policies, external feeds/cards,
wallpaper zoom and authenticated service integrations. These are recorded gaps, not placeholder
toggles or commitments to privileged Android behavior.

## Horizontal folders and local cards continuation (2026-10-11)

[Horizontal folder paging and local cards](FOLDER_PAGING_CARDS.md) are now implemented locally. Remaining card parity includes embedded widgets, Bluetooth/weather/media and authenticated service integrations. Physical acceptance and release gates remain separate from implementation; earlier blanket horizontal-folder/card gaps are superseded for this documented subset.

## Search and folder usability fixes (2026-10-11)

[Search sizing, result drag and folder editor/cover controls](SEARCH_FOLDER_CUSTOMIZATION.md) are implemented locally. Named validation belongs in implementation status; broader OEM, TalkBack, large-font/RTL, process-death and profile-lock acceptance remains pending. This does not complete milestone or release gates.


## Card content extension (2026-10-11)

[Card content extensions](CARD_CONTENT_EXTENSIONS.md) supersede the prior embedded-widget/Bluetooth/weather/media implementation gap for the documented local subset. Authenticated services, weather predicates, named devices and physical/OEM acceptance remain backlog; this does not publish or accept the provisional architecture.

## Drawer row spacing (2026-10-11)

Vertical/list drawer row-count controls are implemented locally under FR-011/022 with backwards
compatible automatic spacing. Named checks are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#drawer-row-spacing--2026-10-11).
Broader physical OEM, TalkBack and font-scale acceptance remains separate from local implementation.

## Priority reliability work (2026-10-11)

The user requested reliability fixes first. Journaled restore/reset/undo,
media-card failure containment, independent local/provider search and profile/shortcut revalidation
are implemented locally. See [current evidence](IMPLEMENTATION_STATUS.md#priority-reliability-fixes--2026-10-11).
Grid warnings/atomic recovery, widget/badge race protection and truthful privacy wording are now implemented locally. Physical default-HOME and widget checks are tracked in [further evidence](IMPLEMENTATION_STATUS.md#remaining-reliability-work--2026-10-11). Remaining priorities are real version migration/reboot, third-party configured widgets and notification-access transitions, then performance/battery measurements and wider device/accessibility coverage. Private Space support and
cross-device serial mapping remain open; these fixes do not complete M0/M1 or publish a release.

## Personal home reference (2026-10-11)

The requested home-only appearance pass adds minimal home-search decoration and an original
offline clock. Local checks pass; physical application and comparison remain pending until the
reliability run releases the phone. This does not complete a milestone or publish a release. See
[home reference evidence](HOME_REFERENCE_MATCH.md).

The expanded [personal home/drawer reference](HOME_REFERENCE_MATCH.md) is implemented and applied
on the S24 Ultra. Native personal shortcut pinning and optional shortcut artwork now work with an
additive Room v3 migration. This does not establish other-OEM, TalkBack, large-font, RTL or midnight
acceptance, and does not authorize publication.


Live settings preview follow-up (2026-10-12): [geometry/motion/practice samples](SETTINGS_LIVE_PREVIEWS.md)
are implemented with local and representative physical checks. This improves FR-022/014; it does
not close M0, provisional architecture review, full accessibility or release acceptance.

## Reliability-first continuation (2026-10-12)

The user requested the following gap order: reliability first, then subgrid/drawer folders and
cross-device restore, then foldable profiles, then modern theming/frequent-drawer polish. Recovery
snapshot validation, durable preservation before settings replacement, and corrupt/overflow-safe
startup handling are implemented in this pass. Native older-schema fixtures extend migration
coverage; their executed results and limits belong in
[implementation status](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

Remaining reliability gates, in order: real production-version upgrade/reboot; third-party widget
consent/configuration and notification-access transitions; repeatable startup/frame/memory/battery
measurements; broader Android/OEM, profile and accessibility coverage. Native schema fixtures do
not close the real APK-upgrade or reboot gate. Half-cell placement, drawer folder cells, profile
remapping, independent foldable layouts, monochrome themed icons and frequent drawer content
remain open. Local main integration is recorded in implementation status; no milestone completion
or publication is claimed.

Accepted native evidence now covers schema 1/2/3→4 fixtures and actual process kills at all four
restore-store boundaries on Redmi 9/Android 11. Real production-APK upgrade/reboot remains the
next acceptance gate, followed by third-party configuration/access changes and performance
measurement. This does not advance subgrid/foldables or complete a release milestone.

## Horizontal scrolling first batch (2026-10-13)

Independent home/drawer motion controls, original-view home wrapping, gesture direction gating,
tap/scrub indicators and a pinned synthetic preview are implemented locally.
[Verification and limitations](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13)
keep native gesture/accessibility/performance acceptance separate. Follow-ups include a paged
alphabet jump, overview, global manual drawer ordering, more effects/haptics and multi-page flings.
Folder/dock paging continues with its existing controls; no milestone, architecture or release
gate is closed by this batch.


The 16 October [MET migration](release/MET_NORWAY_SWITCH.md) implements the weather-provider change and offline city catalogue on its review branch. Aggregate public traffic/proxy review, source recipient delivery and physical acceptance remain release gates; no public release is authorized by this roadmap.
