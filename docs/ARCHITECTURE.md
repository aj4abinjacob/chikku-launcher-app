# Technical architecture — Kotlin-first Android launcher

## Play distribution boundary — 2026-10-15

Release wiring selects Google-app handoff only; debug retains the optional overlay experiment.
About reads bundled policy/licence/source notices off main in an offline dialog. No new persisted
setting, permission, component, dependency or module is introduced. Existing restore attention
still handles a missing Google app and does not recommend a bridge for this release.
[Current preparation](release/PLAY_PREPARATION.md) does not accept the provisional foundation ADRs.
## Memory footprint (implemented locally, 2026-10-21)

Measured on a Galaxy S24 Ultra (Android 16) the HOME process held about 240–300 MB PSS while on
screen, most of it native icon layers and GPU textures, because every bound view owned a private
`AdaptiveIconDrawable` copy, every workspace page was fully bound, and the closed drawer kept its
viewport bound. The accessibility service keeps the process bound while enabled, so this memory is
never reclaimed by caching. Changes:

- `CachingIconProvider` rasterises each icon once (software, ARGB_8888, at `home_icon_size` × the
  largest icon scale in use) into an immutable bitmap in a byte-bounded `LruCache`; views draw a
  per-instance `IconBitmapDrawable` over the shared pixels. Shortcut icons go through the same
  cache (`IconProvider.shortcutIcon`); every shortcut policy check still runs per load. A density,
  scale, theme, icon-pack or calendar-date change drops the cache and bumps `iconRevision`.
- `WorkspaceController` keeps a bind window (`PagerMath.bindWindow`: current page ±1, plus pages
  entering the screen while scrolling, plus the drag source page). Pages outside it hold
  `CellLayout.Placeholder`s with the same ids, model tags and cells, so occupancy, drops and edits
  are unchanged. Widget host views are parked and reused because `AppWidgetHost` holds every view it
  created until the widget is deleted.
- `DrawerController` detaches its adapters one second after the drawer is fully hidden
  (`DrawerMotion.onShownChanged`), keeps data and scroll position, and rebinds as the open gesture
  starts.
- Memory pressure: `ChikkuApplication.onTrimMemory` → `AppContainer.trimMemory` → icon cache;
  `HomeActivity.onTrimMemory` (UI_HIDDEN and above) releases the dock photo and the hidden drawer.
- Smaller: the dock photo is masked once per size instead of through a per-draw `saveLayer`;
  `ScreenEdgeShadow` caches its gradients; badge avatars are cached per notification and compared by
  content so unchanged views are not re-applied; `onNotificationRankingUpdate` only rebuilds when a
  ranking change affects a shown badge; folder search derives from `HomeViewModel.searchableFolderItems`.

Limits: platform widget views stay resident; the bind window is not shrunk under memory pressure
because a narrower window would pop in on swipe. Device before/after numbers are recorded in the
changelog when measured.

## Cloud validation implementation — 2026-10-14

Cards gives the extended FAB an explicit localized parent name and disabled semantics; the
Material3 1.4.0 text Row clears child semantics. An optional widget-catalog source defaults to
the normal production path and permits isolated empty catalogs in Cards fixtures. The production
profile adapter revalidates serial, type, quiet/unlocked and accessibility before and after label
reads, including the calling profile; known private or stale identities fail closed.

Opt-in acceptance IDs/build/signing are isolated under
`io.github.aj4abinjacob.chikku.acceptance.<scenario>`. Test-only R8 API keeps make separately
compiled instrumentation compatible with the minified target without changing normal release
rules. Public/protected ABI preservation means acceptance shrink output is not byte-equivalent
production shrink evidence. No production permission/dependency, schema or backup-format change
was introduced. The [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) records resource limits, actual results and open gates.

**Decision status:** proposed baseline (2026-10-10); validate with a two-week spike. Related: [ADR-0001](decisions/0001-kotlin-first-hybrid-ui.md), [ADR-0002](decisions/0002-build-vs-fork.md), [Android APIs](ANDROID_APIS.md).

**As-built status (2026-11-12):** the sections down to "Architecture review checkpoints" were
checked against the source tree on this date and now separate what is implemented from what is
still proposed. Describing the code as built does not accept ADR-0001 or ADR-0002; both stay
provisional until the spike evidence is reviewed. The dated sections after the checkpoints are the
per-change record and are unchanged.

## Why launchers are unusual Android apps

HOME is the most frequently visited UI, starts early, hosts third-party RemoteViews widgets, tracks apps across profiles and must survive OEM/Android changes. A standard Compose-only CRUD architecture is insufficient for all drag/drop, widget and windowing behaviors. Android's system Recents/Quickstep is not a public launcher API.

## Recommended approach

**Kotlin-first clean-room implementation using public Android APIs.** Use **custom Views/ViewGroups** for latency-sensitive desktop, dock, icon grid, drag/drop, folder popups and AppWidgetHostView integration. Use **Jetpack Compose** for settings, onboarding, search UI where appropriate, and accessibility-friendly dialogs. This is not a ban on Compose for home UI; benchmark before choosing it for the workspace.

Implemented:

- Kotlin + Coroutines + Flow for application logic and state.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`) and AndroidX.
- Room (KSP, exported schemas, currently version 4) for the workspace, folders, per-app
  customizations and drawer groups, with migrations.
- SharedPreferences for settings (`SharedPreferencesSettingsRepository`) and app-lock state,
  encoded by the pure `LauncherSettingsCodec`. DataStore was proposed and is not used.
- Hand-written `MiniJson` in `:core:model` for settings, cards and backups, so the core module
  depends only on kotlinx-coroutines. Kotlin serialization was proposed and is not used.
- Manual constructor injection in `AppContainer`, with every dependency created lazily. Hilt/Dagger
  is not used.
- Compose Material 3 for settings, onboarding and search; custom Views and RecyclerView for the
  workspace, dock, drawer, folders and widget hosting.
- AndroidX Lifecycle and ViewModel.
- No analytics, ads or remote config. Core has no network dependency; optional web autocomplete has an explicit INTERNET permission exception ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)), as does city-based weather for cards ([ADR-0006](decisions/0006-optional-card-content.md)).

Proposed, not implemented: ProfileInstaller/Baseline Profiles, JankStats and Macrobenchmark. There
is no `:benchmark` module.

## Module boundaries

### As built

    :core:model        Pure Kotlin/JVM, no Android imports. Domain types (AppKey, Workspace*,
                       LauncherSettings), repository contracts, WorkspaceLayoutEngine, search
                       matching/ranking, settings and backup codecs, BackupService, card model.
    :app               The launcher. Packages under io.github.aj4abinjacob.chikku:
                         platform/    LauncherApps, icons, launching, HOME role, shortcuts,
                                      Discover overlay, gesture execution, card sources
                         data/        Room database and DAOs, SharedPreferences stores,
                                      restore journal and recovery decorators
                         home/        HomeActivity and the View-based workspace, dock, drawer,
                                      folders, widgets, gestures, cards, app menu
                         search/      Compose search overlay, SearchActivity fallback, sources
                         settings/    Compose settings, live previews, icon picker
                         onboarding/  Compose first-run flow
                         applock/, badges/, backup/, theme/, labs/, message/, prompt/
                         AppContainer.kt   manual injection root
    :discover-bridge   Optional companion app that relays Google Discover overlay calls for
                       release builds (ADR-0004). No dependency on the other modules.

`:app` depends on `:core:model`; `:core:model` depends on nothing in the project. Inside `:app` the
layering is by package and is not enforced by the build: `data/` and `platform/` implement
`:core:model` contracts, and the UI packages receive them through `HomeDependencies`.

### Proposed further split (not created)

Extract a module only when its boundary is earning its keep.

    :app                    Manifest, HOME activity, onboarding, DI wiring
    :core:model             AppKey, Item, GridPlacement, LayoutProfile, action models
    :core:database          Room entities/DAO, migrations, transactional workspace
    :core:preferences       DataStore, theme and gesture settings
    :core:launcher-api      LauncherApps, UserManager, RoleManager, package events
    :core:ui                Reusable View primitives, icons, insets, accessibility
    :feature:workspace      Pages, grid engine, drag controller, dock, folders
    :feature:drawer         All apps, tabs, work/private containers
    :feature:search         Offline index, matching/ranking, search UI
    :feature:widgets        AppWidgetHost, picker, bind/config/resize/rebind
    :feature:gestures       Gesture recognition, action routing, capability checks
    :feature:backup         Export/import, schema, validation, rollback
    :feature:settings       Compose settings and onboarding
    :feature:profiles       Layout profiles for foldable/large screens
    :benchmark              Macrobenchmark and Baseline Profile tests

**Dependency rule:** feature modules depend on core contracts; core never depends on features. The :app module wires implementations. Avoid feature-to-feature imports; use interfaces and domain events.

## Runtime architecture

    ChikkuApplication ── lazy ──> AppContainer (implements HomeDependencies)
                                      │ constructs
         ┌────────────────────────────┼──────────────────────────────┐
     platform/                      data/                       :core:model
     LauncherAppsRepository         RoomWorkspaceRepository     contracts, domain types,
     CachingIconProvider            RoomAppCustomization…       layout engine, search,
     LauncherAppsLauncher           RoomDrawerGroupRepository   BackupService, codecs
     LauncherAppsShortcuts          SharedPreferencesSettings…
         │                            │ each wrapped by a Recovery* decorator
         │                            │ sharing one RestoreAccess gate
         └──────────────┬─────────────┘
                 Flows of immutable state
                        │
                  HomeViewModel ── combine ──> StateFlow<HomeUiState>
                        │
                  HomeActivity (HOME intent, View tree)
         ┌──────────┬───┴──────┬───────────┬────────────┬───────────┐
     Workspace   Drawer     Folder      Widgets      HomeGesture   HomeSearch
     Controller  Controller Controller  Controller   Controller    Overlay (Compose)
     + WorkspacePager / CellLayout

    SettingsActivity, OnboardingActivity, SearchActivity (fallback) and IconPickerActivity are
    separate Compose activities that read the same container.

There is no single coordinator class. `HomeViewModel` combines the workspace, apps, drawer,
settings and chrome flows into one `HomeUiState`; `HomeActivity` owns the view tree and hands each
area to a controller.

Data flow is unidirectional: user/system event → repository call or typed platform call →
repository emits new immutable state → `HomeViewModel` → main-thread render. Views never own
positions: a drop only requests a move, and items move when the repository emits new state.
Transient drag state stays in `WorkspaceController`, separate from the persisted layout.

Process-scoped work started by `AppContainer` on `appScope`: default dock seeding, following the
badge/media setting for notification access, `NewAppsController`, and the corrupt-layout notice.

## Domain and persistence

- **AppKey:** packageName + className + userSerial (not package name alone); the serial maps to a runtime UserHandle via UserManager. App identities differ across personal and work profiles. `ComponentKey` is the same shape for widget providers and package-only targets.
- **WorkspaceItem:** stable string id, `CellPlacement` (container DESKTOP or DOCK, page, position, span), an `ItemTarget` (App, Shortcut, Folder or Widget), optional per-item gesture actions, zIndex and a widget padding override. Folder entries have their own stable ids and a rank.
- **Placement:** the domain and engine use whole cells; Room stores coordinates in integer half-cell units (cells × 2). Interactive subgrid placement remains FR-012 backlog. The proposed normalized half-cell engine is not implemented.
- **Widgets:** `ItemTarget.Widget` holds the device-local appWidgetId and the provider `ComponentKey`. Backups write the provider and placement with the ID cleared, so a restore always allocates fresh IDs; a lost provider leaves a resolvable placement. Widgets embedded in cards keep their bindings in a no-backup file store.
- **Room tables:** workspace items, folder items, workspace meta, edit snapshots (ordinary and restore Undo), app customizations, drawer groups and their members, in one database.
- **Settings:** one immutable `LauncherSettings` value in SharedPreferences, including card configuration and folder covers.
- **Outside the backup:** opt-in search history (`LocalSearchHistoryRepository`), app-lock state, card widget bindings and the restore journal.
- **Backup:** one versioned JSON file (`BackupCodec`, format version 1) holding settings, workspace, customizations and drawer groups, chosen through the Storage Access Framework. Decoding is strict about structure and tolerant of unknown keys, and never deserializes arbitrary classes. There is no ZIP container or integrity hash; both were proposed and are not implemented.
- **LayoutProfile** (phone, cover, inner, tablet, orientation): proposed, not implemented. There is one layout per device.

Detailed schema: [DATA_MODEL.md](DATA_MODEL.md).

## Core components and responsibilities

### AppsRepository (`platform/LauncherAppsRepository`)
Uses LauncherApps to enumerate launcher activities and observe package/profile changes; emits immutable `AppsState` snapshots keyed by AppKey and never persists an app inventory. `CachingIconProvider` follows the icon pack/shape settings and per-app overrides and is invalidated on package changes. `LauncherAppsLauncher` starts activities and returns a typed `LaunchResult`, checking app lock first; `RememberingAppLauncher` wraps it to record opt-in search history. Profile lock state is security-sensitive, and Private Space is excluded.

### WorkspaceLayoutEngine (`:core:model` `layout/`)
Pure Kotlin placement rules: validate against a grid, overlap and free-cell checks, nearest and first free slot, overlap listing. `HiddenLayoutRecovery` plans relocation of items that a smaller grid would hide. Undo and rollback are not in the engine: `RoomWorkspaceRepository` keeps bounded edit snapshots and applies each edit in one transaction. Folder drop targeting is `home/folder/FolderDropPlanner`. Covered by `WorkspaceLayoutEngineTest` and `ReliabilityLayoutStressTest`.

### Workspace views and drag (`home/`)
`WorkspacePager` scrolls pages of `CellLayout`; `WorkspaceController` renders `WorkspaceUi.Ready` into the pager and dock and runs drag and drop. Long-press starts a drag; the drop requests a repository edit, and a rejected or cancelled drop leaves the previous state. `VerticalSwipeLayout` and `HomeGestureController` recognize home gestures. `DrawerController` (RecyclerView) and `FolderController`/`FolderPopup` own their surfaces.

### Widgets (`home/widget/`)
`ChikkuWidgetHost` owns the AppWidgetHost; `WidgetBindFlow` runs allocate/bind/configure through the `WidgetPlatform` boundary; `WidgetsController` binds `WidgetCellView`s, resizing and the orphan-ID sweep. The process-wide `WidgetOperationCoordinator` serializes allocation, commit and cleanup across HOME instances. Third-party RemoteViews render in AppWidgetHostView, never a Compose reimplementation. A missing provider or failed bind shows a placeholder and must not take down HOME.

### Search (`:core:model` `search/` and `search/`)
There is no durable index. `UniversalSearch` ranks apps (original and custom labels), shortcuts, contacts, folders and micro results per query with the shared `LabelMatcher`; `SearchViewModel` debounces and cancels with Flow and never blocks on optional providers. Contacts are permission-gated; file search is not implemented.

Implemented locally (2026-10-12): app and contact names use a shared, bounded Unicode token
matcher with literal matches before typo suggestions. Original and custom app labels compete in
one ranking pass, retaining component/profile identity and match provenance. Contact lookup
supplements Android's filter with a cancellable per-query scan of personal contact names, retaining
only top candidates in memory and fetching actions for the final hits. There is no durable contact
index, new permission, dependency or foundation decision. See [matching scope](SEARCH_FOLDER_CUSTOMIZATION.md#offline-typo-matching--2026-10-12).

Optional web autocomplete is a separately collected `SearchViewModel` flow with an injected
`WebSuggestionSource`; it receives only typed text and the engine. It is off by default, cancels on
query/provider/lifecycle changes, bounds HTTPS responses and never blocks local results. See
[ADR-0005](decisions/0005-optional-web-search-suggestions.md).

### Gestures (`home/gestures/` and `platform/GestureActionExecutor`)
Recognition is separate from execution. `HomeGestureController` resolves a recognized gesture to a `GestureAction`; `GestureActionExecutor` runs it and returns `Performed`, `NoAction`, `NeedsAccessibilityService`, `Unavailable` or `Locked`. The optional `ChikkuAccessibilityService` is requested only for the specific actions that need it (lock screen, recents, and the shade when the status-bar call is blocked).

### BackupService (`:core:model` `backup/`)
Exports settings, workspace, customizations and drawer groups as one `ChikkuBackup`, and previews a file before applying it (`BackupSummary`, plus `RestoreCheck`s answered by `AndroidRestoreEnvironment` for device-local dependencies). Restore does not use staging tables: it journals the previous state in `FileRestoreJournal`, replaces the four stores in turn and marks completion last. `RestoreAccess` blocks edits through the `Recovery*` decorators while recovery is pending. This is recovery across four stores, not one transaction spanning Room and SharedPreferences. File selection uses the Storage Access Framework; there is no storage permission.

Implemented shortcut-reinstall handling (2026-10-18): Home, Cards, gestures and Search share
`LauncherAppsShortcuts`. Native launch remains first; an absent Firefox URL-ID shortcut can open
as an HTTP(S) page in the same personal-profile browser after app-lock/profile checks. Opaque IDs
remain recoverable placements with explicit re-add instructions. Android's pinned intent is outside
Chikku's portable backup. [Scope and verification](RESTORED_SHORTCUTS.md) include newer Firefox UUIDs.

## Threading and lifecycle rules

- Never query packages, parse icon packs, decode large bitmaps or read Room on main thread.
- Main thread owns view mutation; use Dispatchers.Default for layout/search and IO for persistence.
- Use structured concurrency tied to Lifecycle/ViewModel; no GlobalScope. Process-lifetime work uses the injected `AppContainer.appScope` (SupervisorJob, main-immediate).
- Conflate/coalesce package events; avoid reloading every icon on each resume.
- After its first UI subscription, `HomeViewModel.uiState` keeps observing within `viewModelScope`
  until the ViewModel is cleared. Backgrounding HOME must not restart Room/app observations with
  transient loading states on return. Package/profile changes still invalidate immediately while
  HOME is hidden; view rendering remains STARTED-only. This uses the existing event callbacks,
  without polling, a service or a wake lock.
- Keep home rendering functional if search, widgets or icon pack providers fail.
- Recovery removes the dock's layout reservation so safe-mode/failed-layout actions remain
  reachable before a workspace has loaded. Widget host attachment reports an already measured
  cell's dimensions as well as later size changes; asynchronous rebind must not rely on a resize.
- Register/unregister callbacks and widget listening at correct lifecycle points; verify process-death recovery.
- Use strict resource limits for icon caches, bitmap pools and RemoteViews (especially Android 17 [S23][S24]).

## Android compatibility strategy

- Implemented: **minSdk 29**, **compileSdk 37**, **targetSdk 36**. Play requires API 36+ for new submissions since 2026-08-31 [S25].
- Android 15+ Private Space requires HOME role, ACCESS_HIDDEN_PROFILES and a separate locked container [S22]. Chikku does not declare that permission, and Private Space is excluded from every surface.
- Android 17 target-37 widgets have RemoteViews bitmap/icon memory constraints; test before raising target [S23].
- Test system gesture navigation on OEM devices but do not implement hidden Quickstep hooks in the stock build.
- Edge-to-edge, predictive Back, foldable posture, keyboard, RTL and accessibility are architectural concerns, not afterthoughts.

## Failure containment

The optional Google Discover overlay is an activity-scoped external integration, off by default.
`AppContainer` injects a provider resolver; package inspection runs on IO. HOME owns binding,
window attachment and cleanup through a lifecycle observer, and disables the overlay in recovery
or safe mode. The Google process owns card rendering and networking; no card content or
profile inventory is stored by Chikku. The unofficial Binder protocol is isolated under
`platform/`, with no bundled upstream client. The resolver also captures the current user’s Google
app launch intent on IO; an explicit open action uses it if the overlay is not ready or is rejected.
No companion app is required. For release builds, which Google does not accept as overlay clients,
the optional `:discover-bridge` app (same signing key) binds Google and relays the overlay calls;
the resolver prefers it when present. See [ADR-0004](decisions/0004-optional-google-discover.md).

- HOME activity never depends on network or optional plugins to draw first frame.
- Migrations are atomic, idempotent and covered by fixtures.
- `StartupGuard` counts unfinished startups and enters safe mode without deleting data; safe mode withholds the Discover overlay and install-event edits until the user leaves it.
- A corrupt database is preserved as a copy and reported through `corruptLayoutNotice`; default seeding is withheld while a preserved copy exists.
- A broken widget shows placeholder; missing app retains its position until user chooses repair/remove.
- Proposed, not implemented: exporting a redacted diagnostics bundle locally on user request.

## Key risks and spike exit criteria

| Risk | Spike | Pivot condition |
|---|---|---|
| Widget host + Compose/View interop | Add/resize/configure 3 real widgets | If unstable, strengthen View-only workspace or evaluate Launcher3 fork |
| Drag/drop latency | 6x8 grid, 3 pages, 100 icons, gesture benchmarks | Reconsider layout/animation strategy if sustained jank |
| Work/private profiles | Test Android 15–17 locked/unlocked states | Delay advanced search until isolation is correct |
| AOSP gesture restrictions | Test Pixel/Samsung/OnePlus unrooted | Document unsupported actions; do not ship root-only hacks |
| Cross-device backup | Export/import on two emulators with different grids | Redesign schema before adding parity features |

## Architecture review checkpoints

After M0 spike, after widgets, before backup format freeze, before targeting API 37, and before 1.0. Each checkpoint records measured data, accepted tradeoffs and an ADR update.

## Article-requested additions (implemented locally, 2026-10-11)

`core:model/search/MicroResults` owns bounded offline recognition and conversion; Android search
owns localized rows and explicit intents. `core:model/search/SearchHistory` holds the pure history
rules (identity-only targets, 14-day half-life frequency, hour-of-day affinity, caps, codec);
`LocalSearchHistoryRepository` stores opt-in history separately from backup state.
`RememberingAppLauncher` records successful app launches and `SearchResultActions` records
contacts, shortcuts, folders and sent web queries opened from search, both asynchronously.
`UniversalSearch.history` resolves ranked entries live for an empty query, and the search
ViewModel re-filters them against visible, hidden, app-locked and turned-off sources.
`AppContainer` wires these constructor-injected services. No service account connector is implied.

Widget corners live in `WidgetCellView`; workspace preference changes update existing views.
Dock photo decoding uses `DockImageLoader` on IO and requires an existing persisted document grant;
`DockSurfaceController` uses its host lifecycle scope and cancels obsolete loads. The preview uses
its Compose scope. `DockImageDrawable` masks a sampled photo through the ordinary background shape.
Missing files/grants retain color fallback and the recoverable reference. No module, dependency,
permission, Room schema or provisional foundation decision changes. See [article audit](NOVA_ARTICLE_PARITY.md).

## Phone-inspected controls (implemented locally, 2026-10-11)

The [phone inventory](NOVA_PHONE_SETTINGS.md) describes the expanded settings scope. Shared
`LabelSettingsControls` edits independent model fields, while `AppIconView` owns label measurement,
two-line layout and RTL drawer rows. Drawer/folder controllers own their navigation/presentation
policies; sorting changes display order without rewriting folder storage. `WorkspacePager` and
`DrawerMotion` own optional transforms and reset them when effects or reduced motion change.
`VerticalSwipeLayout` recognizes additional bounded gestures and `HomeGestureController` resolves
actions; only assigned empty-space horizontal swipes supersede desktop paging. Popup menus retain
their platform capability loader and separate accessible launcher actions from app shortcuts.

`NewAppsController` observes enabled, unlocked personal-profile install callbacks through an
injected platform boundary. Registration, unregistration and component resolution run on IO;
generation checks reject obsolete events, hidden state is refreshed, and the repository's
`addInstalledApp` performs duplicate rejection atomically, including folder entries. Recovery/safe
mode gates edits. This is process-scoped observation, not a persistent install-catching service.

Pure `FolderSearch` filters local folder results; the search ViewModel and HOME revalidate visibility
before opening. `SettingsSearchIndex` uses static localized resource content and opens containing
sections. New preferences use the existing settings/backup codec. No module, dependency,
permission, exported component or Room schema is added; ADR-0001/0002 remain provisional.

## Local cards and horizontal folder paging (2026-10-11)

[Implementation boundaries](FOLDER_PAGING_CARDS.md): pure `core:model/cards` owns immutable card configuration, strict nested decoding, condition evaluation and target-visibility policy. Existing settings/backup storage owns persistence; no database change. `settings/cards` owns durable draft mutations and recoverable editing. `platform/cards` owns public battery/connectivity and personal target resolution. `home/cards` observes only during an open STARTED panel, revalidates before taps and uses the existing action executor. Widget features navigate to existing placements. Folder paging remains within the existing popup presentation and preserves stored order. The provisional foundation decision remains unchanged.

## HOME search and folder covers (2026-10-11)

[HOME search](SEARCH_FOLDER_CUSTOMIZATION.md) now uses an activity-scoped Compose overlay inside the existing drag window; standalone SearchActivity remains a non-exported fallback. Local drag sources remain attached until drag end. Immediate app/profile filtering and fresh drop checks complement query debouncing. Folder covers are stable-folder-ID settings; contextual OpenFolder resolves inside FolderController. No module, dependency, database schema or provisional ADR decision changes.


## Card content extension (2026-10-11)

[Card content](CARD_CONTENT_EXTENSIONS.md) adds narrow injected weather/media sources, a settings-driven optional Bluetooth observation and a dedicated widget host with atomic no-backup bindings. Content views own attached/STARTED lifetimes; shared notification access follows badges or saved media cards. [ADR-0006](decisions/0006-optional-card-content.md) records the platform/provider boundaries.

## Drawer row spacing (2026-10-11)

`DrawerController` computes scrolling-row height from the list viewport after search/tab layout
and bottom insets. `DrawerAdapter` applies it to recycled cells; content measurement and minimum
touch-target height take precedence over a dense requested count. Layout callbacks recalculate
after resizing; horizontal paging retains its existing slots and content-sized search results.
No new module, dependency, permission or Room migration is required.

## Reliability hardening (implemented locally, 2026-10-11)

Production now wires one `BackupService`, `RestoreAccess` and private `FileRestoreJournal` in
`AppContainer`. Recovery decorators cover settings, workspace, customizations and drawer groups;
app observations are also withheld if recovery fails. The service uses underlying stores to avoid
recursive initialization, persists the previous state before replacement and marks completion last.
Cancellation attempts noncancellable rollback; failed recovery keeps the journal and blocks edits.
This is recovery across four stores, not a single SQLite transaction spanning SharedPreferences.
Settings imports now reject unsuccessful disk commits. Directory resolution/file I/O runs on IO.

Media uses a rendezvous channel flow, independently owned collector callbacks and lifecycle cleanup.
Source or transport exceptions are contained in the optional card view. Search computes offline
results separately from optional provider enrichment so uncooperative Binder cancellation cannot
hold up newer local queries. Provider failures/cooperative deadlines are typed; stale input
identities cannot merge into new results. Contacts cancellation reaches the provider immediately.

Shortcut caches revalidate HOME permission and unlocked/nonquiet profile state before and after
reads, including cache hits. App/profile events clear the replayed snapshot before debounced reload;
stopped observation expires replay. Enumeration, app/shortcut launch and shortcut menus use fresh
profile checks. Private Space remains deliberately excluded; no hidden-profile permission, new
module, dependency or foundation decision is introduced. Named evidence and remaining physical
acceptance are in [implementation status](IMPLEMENTATION_STATUS.md#priority-reliability-fixes--2026-10-11).

## Further reliability boundaries (2026-10-11)

`HiddenLayoutRecovery` is a pure placement planner. Room applies its complete plan in one transaction with one snapshot; recovery Undo requires the most recent snapshot to have the relocation reason so it cannot consume an intervening edit. Settings compare current and proposed footprints before saving and warn without deleting rows. Recovery decorators serialize both operations with restore.

Widget metadata/profile checks run off main, and cell binding jobs validate identity again before displaying their result. Cancellation releases a newly allocated ID only when readable storage proves it unreferenced; uncertain IDs remain for the next orphan sweep. Production rebind retirement is deferred to preserve ordinary and durable Undo. Notification publication uses a connection/generation token: disconnects, style and profile changes invalidate in-flight reads. Optional media continues sharing notification access; badge OFF clears badge data. No production permission, exported component, dependency, module or ADR change.

## Original home clock and minimal search appearance (2026-10-11)

The optional home bar remains an accessible local-search entry; changing its decoration does not
change its search provider or network opt-ins. The original clock uses framework TextClock views in
a RemoteViews provider with a non-exported lifecycle receiver, no periodic job or new permission.
It uses existing widget binding/recovery and does not revise the foundation ADRs. See
[implementation and evidence](HOME_REFERENCE_MATCH.md).

Widget rebind identity is compared again inside the Room update transaction, through the restore access gate. `WidgetBindFlowTest.editBetweenRebindPreflightAndCommitCannotReplaceTheNewerBinding` injects an intervening edit and verifies rejection, retained newer binding, no extra Undo snapshot and release of the unreferenced fresh ID. Repositories without an atomic rebind implementation reject this operation.

Widget-reference cleanup also reads all bounded retained edit snapshots, in addition to current content and durable restore Undo. Cancellation and production orphan sweeping share the restore gate. Unreadable/malformed snapshots defer deletion; a late-cancel regression verifies that an ID used only by ordinary Undo stays live. No Room schema change.

The expanded [personal drawer reference](HOME_REFERENCE_MATCH.md) adds optional tab/scroll-thumb
presentation and runtime calendar resources. A lifecycle-scoped date receiver refreshes icons; it
is not a manifest component. Native shortcut pin consent is an exported framework-token entry
point, validated for the current personal user and editable/default HOME. Shortcut launch identity
and nullable pack/resource presentation remain separate; Room v3 preserves the latter through
folder moves, recovery and portable backups. No foundation/module/dependency/permission change.


## Settings preview implementation (2026-10-12)

[Live settings samples](SETTINGS_LIVE_PREVIEWS.md) stay in the existing Android app/settings layer.
Temporary draft geometry feeds pinned Compose scenes or inert native AppIconView/SearchWidgetView/
menu renderers. PreviewPlayback owns finite, explicitly requested, cancellable motion; practice
resolves existing gesture policies to data and has no executor. Native preview roots expose one
current description without exposing interactive sample descendants. Icon-pack/static personal
widget artwork loads on IO; samples neither launch nor allocate widgets nor observe live
weather/media/search sources. No new module, repository contract or provisional ADR decision.

## Contact communication services (implemented locally, 2026-10-12)

The search person card separates provider description (`core/model` `CommunicationProviders`,
app-neutral entry points), capability discovery (`platform` `AndroidContactEntryPointProbe`, runtime
`resolveActivity` that rejects permission-guarded handlers), per-contact action resolution
(`ContactsSearchSource`, provider-synced data rows for the final hits), selection and card-button
resolution (`ContactServices`, pure; user-ordered `ContactCardButton`s), rendering
(`search/PersonCard.kt`, original one-row card) and validated launching
(`ContactActionLauncher`, typed `ContactActionResult`). Details, evidence and extension points:
[CONTACT_SERVICES.md](CONTACT_SERVICES.md); decision: [ADR-0007](decisions/0007-contact-communication-services.md).

## Search app-lock boundary (2026-10-12)

Search contact/deep-link handoffs now use `platform/GuardedIntentLauncher`: current-profile
handler resolution on IO, component-pinned launches and a last-moment package/profile lock check.
`SearchResultActions` owns authentication retries and a Chikku destination chooser shared by HOME
and standalone search. Missing handlers fail closed; system resolver/profile forwarding is excluded.
This corrects the contact-to-WhatsApp bypass without changing persistence, backups or the
provisional foundation decision. See [app lock](APP_LOCK.md#search-handoff-bypass-correction-2026-10-12).

## Recovery validation continuation (2026-10-12)

Ordinary workspace Undo validates the complete decoded snapshot before replacing any live rows.
A malformed present folder array rejects instead of being treated as empty legacy data.
The same validation gates retained widget references: unreadable or semantically damaged snapshots
defer orphan-ID cleanup. Validation checks identities, raw geometry, page/container relationships,
actions and folder ownership/member types. It deliberately permits off-grid placements and
historical overlaps, preserving layouts after grid changes. Rejected snapshots remain in storage.

Settings keep a failed damage-copy request pending and refuse replacement until the copy commits.
This applies even when restore/reset is the first operation on a new repository instance. Defaults
can still render without authorizing destruction of the damaged input. Startup recovery tolerates
bad optional preference types, negative counters and integer overflow; an uncertain startup commit
uses safe mode. Damaged flags remain until explicit exit. These changes add no module, permission,
dependency or foundation decision. [Executed evidence](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

## Horizontal paging implementation (2026-10-13)

Pure `core:model/settings/PageMotion` owns sensitivity classification, distance/velocity settling,
bounded durations and effect transforms. `WorkspacePager` owns transient native home scrolling
and virtual-slot wrapping of original page views; it never clones widget hosts. Drawer remains
a RecyclerView with sentinel wrapping, direction gating in `HorizontalDrawerRecyclerView`, and
`DrawerPageSnapHelper` for duration/reduced-motion policy. A shared `PageIndicatorView` serves
home and drawer with continuous progress, tap/scrub and accessible range actions.
Settings use synthetic native cells/pager/indicator without wallpaper calls; drawer preview shares
effects through the workspace pager, not exact RecyclerView physics. Existing constructor
injection, persistence/profile boundaries, modules and provisional ADRs are unchanged.
[Scope and evidence](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).


## Android audit boundary fixes (2026-10-15)

External Store, Discover and voice/assistant actions share the existing component-pinned intent
launcher through an activity-owned handoff coordinator. Authentication preserves the exact target
and waits for STARTED; a locked result never enables a fallback. Embedded Discover remains withheld
while its owning app is configured locked. A separate non-consuming shortcut metadata policy
withholds label/icon reads while locks load or are unreadable, rechecks asynchronous reads, and
invalidates open pickers/cards. It never spends a one-time launch grant to inspect private metadata.

Application-owned widget coordination serializes allocation/commit steps and orphan reference
reads/deletion across HOME instances. Pending consent/configuration IDs are reserved across steps.
No widget binding is deleted merely because the controller that allocated it was recreated.

Optional restore dependency checks run behind two bounded workers without a work queue. Their
cancellable await can return UNKNOWN without waiting for an uncooperative Binder/provider call;
late results are discarded. Content lookup passes CancellationSignal; its remote cancellation is
also isolated behind one bounded worker. Saturation fails unknown instead of adding threads or
blocking restore completion. This isolates hung calls; it does not claim to forcibly terminate a
provider that ignores cancellation. See [validation](ANDROID_AUDIT_FIXES_2026-10-15.md).

## Audit follow-ups (implemented locally, 2026-11-12)

Structure changes from the code audit; unit tests, lint and the privacy gate only, no device run.
No new module, dependency, permission, exported component, Room schema or backup-format change, and
ADR-0001/0002 remain provisional.

- **Launch guards.** `platform/GuardedLaunch.kt` holds the one sequence "app lock, resolve user,
  profile available, launch, map failure" used by `LauncherAppsLauncher`, `LauncherAppsShortcuts`
  and `AppMenuPlatform.startShortcut` (which previously skipped the profile check), plus the shared
  shortcut query/filter and `personalUserSerial`. `platform/BoundedHttpsGet.kt` is the hardened
  GET behind web suggestions; on the release line weather keeps its own MET client
  (`BoundedWeatherHttpClient`: redirects, gzip, conditional requests), which it does not replace.
- **Dock background ownership.** `DockSurfaceController` alone sets the dock and backdrop
  backgrounds and rebuilds them only when their inputs change; `WorkspaceController` no longer
  assigns `dock.background`.
- **Appearance rules shared with previews.** `home/drawer/DrawerChrome`, `home/IconLabelStyle`,
  `home/HomeSearchBarMetrics`, `home/DockContainerStyle` and `home/folder/FolderPanelRules` are
  called by both the launcher and its settings previews, so a preview cannot drift from the rule.
- **Item motion.** `home/ItemMotion` holds the drop bounce and the "settle this view to rest"
  routine used by `CellLayout`, `FolderGridMotion`, `FolderReorderMotion` and `WorkspaceController`.
  A controller that cancels a child's animator and takes over its alpha calls
  `CellLayout.forgetFadeIn` first.
- **HomeActivity.** `onCreate` is an ordered list of setup steps (the order is load-bearing). The
  four hosts are `HomeWorkspaceActions`, `HomeGestureHost`, `HomeFolderHost` and
  `HomeDrawerListener`; `HomeSearchFolderOpener`, `HomePageEditor` and `HomeDiscoverOverlay` hold
  their clusters. Cards opening and system-bar/motion application stay in the activity.
- **Icons.** `AppContainer.iconPackRepository` is the one icon-pack repository, shared by the icon
  provider and shortcut icon loaders, so a pack update invalidates all of them. A finished icon load
  is cached even if its requester was cancelled; a change of per-app overrides invalidates only the
  affected keys but still bumps `iconRevision`.
- **Search.** `SearchHostWiring` builds the sources for both hosts; in `SearchActivity` its lifetime
  follows the ViewModel. `PreparedLabels` normalises labels once per app-list snapshot, in memory
  for the search session only. The provider pass reads only shortcuts and contacts.
- **Settings codec.** `LauncherSettingsCodec` encodes and decodes per section;
  `LauncherSettingsCodecGoldenTest` pins the exact encoded output and about 2,400 decode cases.
- **Evaluated and not changed.** Sharing repository flows inside `RestoreAccess` (it weakened
  read-your-writes for one-shot readers), a non-flipping gate for undo/remove-page (the status flip
  hides a half-applied page edit), parallel cold-start reads (a pre-fetched settings value could be
  stale after recovery) and, on development main, a pre-rendered bitmap icon cache.
- **Release-line port (beta 19, 2026-11-12).** The release line already had its own bitmap icon
  cache, badge avatar cache with content-equal badges, dock photo trimming and MET weather client.
  The port keeps those and layers the audit changes on top: targeted override invalidation and
  caching a cancelled requester's finished load now work on the bitmap cache; the notification
  listener gains only the 150 ms rebuild coalescing (no `AvatarKey`); `ShortcutIconLoader` takes
  both the icon cache and the shared icon-pack repository; `HomeSearchFolderOpener` collects
  `HomeViewModel.searchableFolderItems` instead of building the workspace a second time; the
  extracted hosts carry the pinned-shortcut menu; the split settings codec includes the search-bar
  tap action and button slots, and its goldens were regenerated from the release line's previous
  codec before the split was applied.

The 16 October owner-authorized [MET weather migration](release/MET_NORWAY_SWITCH.md) replaces the provider within the existing injected Android boundary. Offline streaming GeoNames search, bounded HTTPS/cache adapters and unchanged portable card settings add no module or global singleton.
