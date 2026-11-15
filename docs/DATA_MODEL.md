# Data model and backup contract (design draft)

## Cloud upgrade/restore evidence — 2026-10-14

Historical development revision `f69121e08e04c64d0c3541304484893877c9788f` was verified as
Room3 without `widgetPadding`; current main `1c081bb9fdd7df4d854d2de7e31ba8e8128587d4`
is Room4 with nullable `widgetPadding`. An actual isolated same-key APK replacement
910001→910002 retained synthetic production settings, workspace, customizations and groups
on API29. No published production upgrade baseline was identified.

Portable backup format remains **1** and widget IDs normalize to0 on import. Full service
export/fresh-install restore, missing dependency attention without discarding saved values,
durable Undo after external restart, malformed/future no-write rejection and four external
interruption/new-process recovery checks passed. This does not establish SAF UI, secure
reboot/first unlock or genuine external widget rebind. Schemas, identity contracts and normal
app versions/signing are unchanged. See the [cloud validation report](CLOUD_RELEASE_VALIDATION_2026-10-14.md) for exact hashes and limitations.

**Status:** proposal, not an implemented schema, except where a section says it is implemented. The Room schema in the build (v4) is simpler than the tables proposed here. Schema must be reviewed before M0 database migration is frozen.

## Domain identities

- **AppKey:** ComponentName (package + launcher activity) plus userSerial. Package alone is insufficient; personal/work/private profiles can have the same package.
- **ShortcutKey:** AppKey + shortcutId, with provider/profile visibility checks.
- **WorkspaceItemId:** stable UUID, independent of position and app installation.
- **LayoutProfileId:** stable UUID for a logical form factor/posture, not a raw display ID.
- **WidgetInstance:** device-local AppWidgetHost ID, provider ComponentName and user/profile. IDs never survive cross-device import.
- **GestureAction:** typed, versioned action descriptor; never arbitrary executable code from a backup.

## Proposed Room tables

| Table | Important columns | Notes |
|---|---|---|
| layout_profiles | id, name, formFactor, posture, orientation, gridCols2x, gridRows2x, revision | Half-cell grid uses integer 2x units |
| workspace_pages | id, profileId, orderIndex, isHomePage | Unique order per profile |
| workspace_items | id, profileId, pageId?, parentFolderId?, kind, x2, y2, width2, height2, appComponent?, userSerial?, shortcutId?, widgetRecordId?, labelOverride?, iconOverrideRef? | Exactly one parent; stable UUID |
| folders | itemId, title, iconMode, tapAction, swipeAction | Folder is a workspace item; contents reference parentFolderId |
| widgets | id, localAppWidgetId?, providerComponent, userSerial, bindState, optionsJson | localAppWidgetId never exported |
| icon_overrides | id, sourceType, resourceRef, contentUri?, hash | Avoid huge bitmaps in DB |
| gesture_bindings | id, scope, targetId?, gestureType, actionType, actionPayload | Typed action routing |
| app_drawer_groups | id, title, orderIndex, profileScope, filterMode | User-managed tabs/folders |
| app_drawer_members | groupId, appComponent, userSerial, orderIndex | Composite identity |
| workspace_snapshots | id, createdAt, schemaVersion, revision, localSnapshotRef | Bounded recovery snapshots |
| schema_metadata | schemaVersion, importVersion, createdAt, updatedAt | Explicit migrations |

DataStore stores visual preferences, theme, search settings and non-relational flags. Room stores workspace relationships and atomic edits.

## Invariants

1. No two non-overlapping-capable items occupy the same grid cells on one page (unless overlap is explicitly enabled and modeled).
2. Every workspace item has exactly one parent: page **or** folder, never both.
3. Folder graph is acyclic; set maximum nesting depth if nested folders ship.
4. Every widget item refers to one widget record; deleting it deallocates local widget ID.
5. AppKey is profile-aware; locked Private Space apps must not enter visible search caches.
6. Invalid/missing app provider does not delete placement; show a repair placeholder.
7. All drag/drop edits are one Room transaction with undo snapshot.
8. Backup import either fully commits or leaves existing layout unchanged.

## Search model

Index launchable app labels, permitted shortcuts, aliases and optionally user-approved providers in memory. Never index locked Private Space content. A search result carries AppKey, match reason, stable sort key and capability state. Cancellation discards stale result batches. No network or contacts by default.

The 2026-10-12 typo matcher adds transient app match ranks and suggested-result flags only.
Contact-name candidates are read per query after the existing opt-in and permission checks, with
no retained address-book index. These computed results, names and scores are never persisted or
included in backups. No settings field, Room migration or backup-format change is needed.

Implemented 2026-10-25: `search.appPriorityDirection` persists `FOLLOW_LANGUAGE` (default),
`LEFT_TO_RIGHT` or `RIGHT_TO_LEFT` for compact app rows. Both settings codec directions and
portable backups preserve it; missing/unknown values keep the previous language-based ordering.
It changes only physical tile placement, retaining ranked app identity, Go selection and text
direction. No device-local dependency check, Room migration or backup-version change is needed.

## Backup format v1 (implemented on `feat/nova-guide`)

The build writes **one versioned JSON file**, not the ZIP proposed below (`core/model/.../backup/BackupCodec.kt`, Nova-guide G18; implemented, not yet verified on a device):

    {"format":"chikku-backup","formatVersion":1,"createdAt":…,"appVersion":"…",
     "settings":{…},"workspace":{"pageCount":n,"items":[…]},
     "customizations":[…],"drawerGroups":[…]}

- Saved and opened through the Storage Access Framework, or shared through the system share sheet (a `FileProvider` URI for a copy in the app cache).
- Bounded input: at most 4 MiB of text and 20,000 entries per list. A single file has no archive entries, paths or compression, so the ZIP traversal and zip-bomb checks below do not apply to it.
- Strict structure: wrong types, missing keys, invalid placements, duplicate IDs, unknown item kinds or unknown gesture actions reject the whole file; unknown keys are ignored. A newer `formatVersion` is rejected.
- No integrity checksum: the format has no `manifest.json`/hash. Validation plus the restore preview and undo are the safeguards; a checksum would only detect accidental damage, not tampering.
- Widget IDs are never written; restored widgets have no ID and show a "needs rebind" placeholder until the user rebinds them with a fresh ID.
- Not included: user-owned icon image files (per-app icon choices are stored as icon-pack references), notification contents, profile handles beyond the user serial in each `AppKey`.
- Restore can also start from the first onboarding step ("Restore from a backup"), using the same preview, journal and undo as Settings → Backup.
- Restored deep-shortcut IDs/parent component/profile remain verbatim in backups. Android's pinned
  intent is device state and is not exported. At launch, personal stable-Firefox HTTP(S) URL IDs may
  open an ordinary page; UUID IDs need re-adding. No schema/format change or new persisted fallback
  exists. [Recovery contract](RESTORED_SHORTCUTS.md).
- Restore checks (`core/model/.../backup/RestoreChecks.kt`, implemented, unit-tested only): settings that depend on this device are kept as restored and explained in the preview and once after the restore, with a link to the setting. Current checks: Google Discover without the Google app, without the Chikku Discover bridge (normal builds), or with a bridge that is not working; a missing icon pack; a dock background image whose content URI cannot be opened here. A check that cannot get an answer (timeout, error) reports nothing.
- Excluded from backups on purpose: app lock state (security; see APP_LOCK.md), search history (`chikku_search_history`: usage history, contact lookup keys, sent query text and the first one to four typed characters each result was opened with; device-local and privacy-sensitive), the startup guard and restore journal (device recovery state), card widget bindings and widget IDs (device-local AppWidgetHost IDs); MET forecast cache and request-throttle metadata (`cache/weather-met-v1`: re-fetchable provider data and device-local traffic control).
- Preview navigation/playback/context, card condition samples, accent-dialog drafts, practice output
  and collapsed preview panels are temporary
  UI/SavedState only and excluded from portable backups. They never become LauncherSettings fields,
  repository content or widget bindings; existing saved values and backup compatibility are unchanged.
- Restore shows a preview with the count of missing apps. Each Room replacement is transactional; the settings/customization/group/workspace operation is protected by a device-local recovery journal and shared access gate. Interrupted operations roll back before normal repository access. The last successful restore/reset can be undone after process restart.

The ZIP layout below remains the proposal for a later format version (for example to carry custom icon images):

    chikku-backup.zip
      manifest.json       formatVersion, appVersion, exportedAt, checksums
      workspace.json      profiles, pages, items, folders, groups
      preferences.json    settings with explicit defaults
      icons/              optional user-owned icon overrides
      README.txt          compatibility and restore instructions

- Use a deterministic, documented schema and bounded ZIP entry count, path depth, uncompressed size and compression ratio (zip-bomb defense).
- Do not include live widget IDs, raw UserHandle tokens, account secrets, notification contents or private profile app lists.
- On import, map userSerial/profile roles to available profiles with explicit user confirmation; map missing app components to placeholders.
- Rebind widgets with new local IDs via system consent.
- Verify integrity hashes and version compatibility before touching current Room state.
- Stage import, show preview and missing-item count, commit transactionally, keep pre-import snapshot for undo.
- Consider optional password-encrypted exports only after threat-model and key-management review; never claim encryption when none exists.

## Migration strategy

Every schema version has forward migration tests using real fixture files. Never destructively reset the database on migration failure. Safe mode can read a last-known-good snapshot; provide local export of recovery data. Maintain a small collection of golden backups from old versions.

## Security/privacy tests

- Locked private apps absent from search, drawer, backup and logs.
- Cross-profile shortcuts cannot accidentally launch the personal equivalent.
- Malicious backup with traversal paths, oversized entries, unknown action types or duplicate IDs is rejected.
- Imported icons cannot execute scripts or untrusted intents.
- No silent backup upload or usage analytics.

Related: [ANDROID_APIS.md](ANDROID_APIS.md), [PRIVACY_SECURITY.md](PRIVACY_SECURITY.md), [TEST_STRATEGY.md](TEST_STRATEGY.md).

## Dock customization (implemented locally, 2026-10-11)

The settings JSON/backup now includes dock label font/scale/shadow/line mode, desktop-size
matching, independent padding, background enable/inset/corner options, navigation-area background,
infinite scroll, overlay/automatic-close mode and remembered page. Missing fields retain the old
sizing/padding and normal dock behavior. Numeric values and the remembered page are bounded.
Dock placements retain their component/profile identity and existing side-by-side storage grid;
no Room schema change or widget ID change is involved. See [dock settings](DOCK_SETTINGS.md).

## Article additions (implemented locally, 2026-10-11)

Settings/backup JSON adds `home.widgetCornerRadiusDp` (0–48), `dock.backgroundImageUri`,
`search.microResultsEnabled` (default true), `search.historyLearningEnabled` (default false; read
from the older `search.frequentAppsEnabled` when missing) and `search.historySuggestionsEnabled`
(default true).
Missing fields preserve old widget rendering and no image/learning. Image bytes and grants are
excluded; restored ungranted references use the color fallback and can be replaced or removed.
Counts store component, package, profile serial and bounded count in a separate local preferences
file, never labels, queries or private/work-profile data; they are excluded from backup exports.
The same toggle also counts personal-app shortcut launches (package, shortcut ID, profile serial and
count, at most 128 entries, in the same file under `shortcuts`); a count only surfaces while its
owning app is visible and the shortcut still exists and is enabled.
Disabling learning or clearing suggestions erases both. No Room schema migration is needed.

## Phone settings data (implemented locally, 2026-10-11)

The [phone inventory](NOVA_PHONE_SETTINGS.md) records the controls represented by these additions.
The existing settings JSON/backup codec carries them; decoding missing keys retains defaults,
unknown enums fall back and numeric geometry is bounded. No Room schema migration is needed.

| Settings owner | Added state and persistence boundary |
|---|---|
| Home | Label font/shadow/line/color, page effect, install-placement opt-in and edge shadow |
| Drawer | Independent labels, desktop-size matching, tab/scroller style, wrapping, search/launch behavior and opening transition; scroll and selected-tab memory is session-only, and queries clear on close |
| Folders | Content icon/label styling, panel mode, columns, preview count, display sort, closing, name/shadow/corners; alphabetical sorting preserves stored manual order |
| Gestures | Left/right, double-tap-plus-swipe and two-finger rotations default to no action; optional Home-button scope preserves the configured default-page identity |
| Search | Presentation/color/corners, bounded app-result limit, keyboard-learning request, home-bar styling and off-by-default local folder results |
| Badges / Look and feel | Badge corner/scale, popup colors/corners and independent optional actions; previews use synthetic data |

Home and drawer labels retain their prior normal font/single-line defaults and their respective
shadow defaults (home on, drawer off). Folder label shadow and home search-bar shadow default off;
these explicitly modeled defaults can change the previous implicit appearance. New app placement
is off by default and stores ordinary
recoverable component/profile placements; observation events and generation tokens are not backup
data. The private-keyboard request defaults on and is not a guarantee about an external IME.
Popup defaults preserve home Hide off, drawer Hide on and uninstall capability checks; Store is off.
Settings-search queries and folder-search result lists remain transient, without learned queries.

## Folder paging and cards settings (2026-10-11)

[Continuation](FOLDER_PAGING_CARDS.md): `folders.scrollStyle` (VERTICAL default/HORIZONTAL_PAGED) and `folders.rows` (three by default, 1–6) extend settings v1. `cards.items` stores up to 24 unique card IDs, titles up to 80 characters, enable state, featured app/shortcut/widget reference, six action slots and local weekday/time/charging/Wi-Fi conditions. Component+user serial identify apps; stable placement IDs identify widgets. No live inventory, conditions snapshot, device-local widget ID or network name is persisted. Missing card settings yield an empty list; malformed structured cards reject the settings/backup recoverably rather than truncating. No Room migration.

## Search dimensions and folder covers (2026-10-11)

[Search/folder customization](SEARCH_FOLDER_CUSTOMIZATION.md) adds bounded width/height/padding and outside dock-gap preferences and a folder-ID-to-cover map to LauncherSettings. Cover selection stores a mode and, for a chosen app, its full component/profile AppKey; first/last derive from stored folder membership. Missing members retain placements and render the normal preview. Existing settings/backup codecs carry the choices; malformed structured covers reject recoverably. The contextual open_folder action uses existing gesture storage; no Room migration is required.


## Card content extension (2026-10-11)

Cards now append typed Actions/Weather/Media/Widget content and a default-false Bluetooth audio condition. Weather stores opt-in, selected city coordinates/label and units; Widget stores provider component/profile, bounded height and logical instance UUID. Backup sanitization renews logical widget UUIDs, while native IDs/pending setup stay solely in an atomic no-backup local store. Bad nested data rejects the whole settings document. See [card content](CARD_CONTENT_EXTENSIONS.md).

## Drawer row spacing (2026-10-11)

`drawer.automaticRowSpacing` extends settings/backup JSON with a `true` default for missing keys,
preserving content-sized vertical/list rows. When false, existing bounded `drawer.rows` (2–12;
settings normally offer 2–8) requests the visible scrolling-row count. Horizontal pages always
use `rows`. Viewport-derived pixel heights are transient and are never persisted. No Room migration.

The [settings clarity audit](SETTINGS_CLARITY_AUDIT.md) adds optional `folders.iconBackgroundColor` independently of the folder window color. Decode of a missing new key inherits the old shared color once; explicit null retains the icon default. Both settings exports and backups preserve the two values.

## Restore recovery journal (implemented locally, 2026-10-11)

`RestoreCheckpoint` records raw settings, workspace content, customizations and drawer groups.
`FileRestoreJournal` stores version 1 in `noBackupFilesDir/restore-journal.json` with an atomic,
synced write and a last-written commit marker. This private record preserves live workspace widget
IDs and embedded-widget logical identities; it is never exported as a portable backup. Its input
is bounded to 16 MiB. A damaged record is preserved and prevents further store access rather than
silently resetting layouts. Completed records retain one full-state undo; undo itself is journaled.

The shared access gate serializes normal repository mutations with restore/reset/undo, pauses
store observations during replacement, and runs pending recovery before exposing production stores.
Recovery does not put the half-applied workspace onto the normal edit-undo stack. Widget orphan
cleanup shares the gate and retains IDs referenced by the workspace or durable restore undo,
including out-of-viewport placements. Embedded card widgets may require rebinding if their separate
host bindings have since been replaced. The portable JSON format remains version 1 with fresh widget
identities; cross-device user-serial remapping remains a separate acceptance gap.

## Hidden placement recovery (2026-10-11)

Grid shrinking retains item rows, component/profile identity, widget IDs, spans, actions and folder members. `relocateHiddenItems` plans all fitting hidden placements, then updates them and page count in one Room transaction with one `relocateHidden` snapshot. A late failure rolls back the entire operation and snapshot. Oversized placements stay unchanged. `undoHiddenRelocation` only consumes a matching latest snapshot, preserving newer unrelated edits. Existing ordinary edit Undo remains available. No schema or portable backup-version change. Rebound old widget IDs remain live until cleanup proves neither current content nor ordinary/durable Undo needs them.

## Home reference appearance (2026-10-11)

`SearchSettings.widgetStyle` persists Provider badge or Minimal with Provider as the backwards
compatible default, including portable backups. The search-bar setting needs no Room schema or
backup-version change. Original clock placements keep component/profile provider identity and use
fresh IDs after restore. See [home reference notes](HOME_REFERENCE_MATCH.md).

## Home search bar actions (2026-10-21)

`SearchSettings.widgetTapAction` (Chikku search or web search, default Chikku search) is what a
tap on the home bar itself opens. `widgetChikkuButton` and `widgetWebButton` place the bar's two
buttons at the start or end side, or hide them. The defaults are start and end. Start is the
right side in right-to-left layouts. All three are written to settings backups. Older settings
and backups have no button keys. For them, a Provider bar whose web result was off decodes its web
button as hidden, so the bar looks as it did before. Unknown values fall back to the defaults. The
web action hands the selected engine to another app only on a tap. For Google it first tries the
Google app's search screen and otherwise opens the engine's home page. App lock applies either
way. No query is sent, and nothing new is device-local, so no restore check is needed. There is
no Room schema or backup format version change.

Widget rebind identity is compared again inside the Room update transaction, through the restore access gate. `WidgetBindFlowTest.editBetweenRebindPreflightAndCommitCannotReplaceTheNewerBinding` injects an intervening edit and verifies rejection, retained newer binding, no extra Undo snapshot and release of the unreferenced fresh ID. Repositories without an atomic rebind implementation reject this operation.

Widget-reference cleanup also reads all bounded retained edit snapshots, in addition to current content and durable restore Undo. Cancellation and production orphan sweeping share the restore gate. Unreadable/malformed snapshots defer deletion; a late-cancel regression verifies that an ID used only by ordinary Undo stays live. No Room schema change.

## Shortcut appearance and drawer reference (2026-10-11)

Implemented Room v3 adds nullable `shortcutIconPack`/`shortcutIconDrawable` columns to workspace
and folder entries through an additive v2→v3 migration. They are appearance metadata, independent
of owning package/provider shortcut ID/profile identity, and survive portable backup, folder moves
and recovery snapshots. Existing v1/v2 schemas and decoding remain. Home gap/offset and drawer
pill/accent/scroll-thumb preferences use tolerant settings JSON; no new backup version is needed.
See [reference evidence](HOME_REFERENCE_MATCH.md).

## Contact communication services (2026-10-12)

`ContactHit.whatsAppDataId` is replaced by `channelActions: List<ContactChannelAction>` (provider
id, action type, data-row id, mimetype); hits remain per-query and are never stored.
`SearchSettings` gains `contactCardButtons: List<String>` (ordered person-card buttons as
`"<provider>:<action>"` ids; default `phone:voice_call`, `sms:message`, `whatsapp:message`; max 4).
It is in `LauncherSettingsCodec` and backups; old backups decode to the default, malformed entries
are dropped, unknown provider ids are kept, and an empty list falls back to the default.
`RestoreChecks.contactServices` reports `CONTACT_SERVICE_APP_MISSING` when a changed list names an
app that is absent and never changes the restored value. See [CONTACT_SERVICES.md](CONTACT_SERVICES.md).

## Recovery validation continuation (2026-10-12)

The existing local workspace snapshot format (versions 1/2) and portable backup format (version 1)
are unchanged. Before ordinary Undo replaces current rows, its complete snapshot must have valid
identities, positive spans, nonnegative raw coordinates, coherent pages/containers, supported
actions and valid folder ownership/member types. Off-grid placements and historical overlaps
remain valid recoverable content. A malformed present folder-members array rejects rather than
decoding as empty; missing legacy arrays still decode. A rejected snapshot stays intact and prevents uncertain
widget-reference cleanup. Older valid snapshots remain decodable.

A failed settings damage-copy commit now blocks update/reset/import from replacing the original
input until preservation succeeds; the pending-copy flag is transient and excluded from portable
backups. The existing damaged-settings copy and startup guard are device-local recovery evidence,
not portable preferences. No new store, Room column, user preference or backup version is added.
[Checks and limitations](IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

## Horizontal scrolling preferences (2026-10-13)

Home and drawer each persist `pageSwipeSensitivity` (LOW/NORMAL/HIGH; NORMAL default),
`pageSnapDurationMs` (250 default, 100–500), `pageEffectStrength` (1 default, 0–1), and
`pageEdgeFeedback` (RESISTANCE default/NONE). Drawer also adds `pageTransitionEffect`
(SLIDE default; FADE/ZOOM/CUBE), `pageIndicatorStyle` (DOTS default; LINE/NONE), and
`pageIndicatorAutoHide` (false default). Home reuses its existing effect/indicator/loop fields.
Both codec directions and the settings backup-coverage/roundtrip tests include these fields.
Older backups keep decoding with defaults; no format or Room schema change. Gesture velocity,
virtual wrap coordinates, edge feedback offsets and indicator progress are ephemeral UI state,
excluded from backups because they are live interaction state. Restoring preferences cannot
change component/profile identity, layout placements or widget IDs.
[Implementation and verification](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).

## Vertical scrolling preferences (2026-10-13)

Drawer persists `listScrollSpeed` (SLOW/NORMAL/FAST; NORMAL default, a 0.6×/1×/1.6× fling
multiplier capped at the platform maximum) and `listEdgeEffect` (SYSTEM default/NONE) for the
Vertical list and List styles. Both are in both codec directions and the settings round-trip test;
older backups decode with the defaults. No format, Room schema or restore-check change: neither
value refers to anything device-local. Fling velocity and edge-effect progress are live interaction
state and are not stored. Remember scroll position and pull-to-search keep their existing fields;
the Vertical scrolling page only shows them again.


## Android audit recovery boundaries (2026-10-15)

Portable backups and newly grown workspaces support up to 256 pages. This is an explicit bound
because HOME currently materializes page models/views eagerly. Portable encoding validates the
result before reporting export success; its existing 4 MiB-character and 20,000-entry limits remain.
Oversized existing local state is preserved and export reports a failure rather than emitting an
unreadable archive. Private restore checkpoints use a distinct local decoder, retain device widget
IDs and historical page counts, and keep the existing 16 MiB file limit. Private reset Undo uses
recovery replacement so historical state beyond the portable cap remains recoverable.

Page deletion snapshots include optional before/after default-page indices. The device-local
`pendingPageEdit` Room metadata record preserves the workspace and Undo stack before a cross-store
page/default-setting edit. Startup/access recovery rolls incomplete edits back before observations
resume. Settings changes are conditional: a currently different later default selection wins.
Undo cannot distinguish an explicit away-and-back selection ending at the same index. This record
and ordinary Undo snapshots are excluded from portable backups, like the existing restore journal;
no backup-format or Room schema version change is required.

## First Play distribution restore boundary (2026-10-15)

The saved Discover preference is unchanged. In this release, opening the installed Google app
is the selected behavior; the environment reports READY for the app-only distribution when
Google is available, with no bridge attention. Missing Google still produces the existing
restore attention without disabling the preference. Offline policy/licence documents and dialog
navigation add no persisted user state; there is no backup/schema version change.


### MET weather migration — 16 October 2026

Portable `CardContent.Weather` is unchanged: selected label/latitude/longitude, Celsius/Fahrenheit
and default-false online opt-in keep their exact version1 backup meaning. Stored/restored values
are never rounded or looked up against the new catalogue; only outgoing request coordinates are
reduced to four decimal places. An absent smaller place remains a valid restored choice.

Excluded from backups on purpose: up to 24 MET response bodies/Expires/Last-Modified/received-time/
203-warning records and one request-throttle deadline in private `cache/weather-met-v1`. Each
response is bounded to 512 KiB decoded, each encoded cache record at most 1 MiB; obsolete responses
are evicted. Android cache eviction or user Clear cache permits re-fetching. No weather-history
store, new Room table, `LauncherSettings` field or backup version is added. The bundled offline
GeoNames index is immutable app data, reproduced from matching source, not user backup state.


## Latest-main search presentation — 17 October 2026

`SearchSettings.resultsAnchor` (default TOP) and `widgetIcon` (default NOSE) are portable
appearance choices. Both codec directions write/read them, old/missing values fall back to
defaults, and settings/backup roundtrip coverage includes non-default values. They introduce no
Room table, device-local reference or backup format change. Telegram/Threema use the existing
ordered contact-button identifiers and existing missing-provider restore attention; contact data
and installed app glyphs are looked up locally and are not added to backups.
