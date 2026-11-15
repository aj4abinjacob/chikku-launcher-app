# Privacy and security design

**Policy (partly implemented):** offline-first core, no advertisements, no third-party analytics/tracking SDKs, no forced accounts. A launcher is highly trusted software; its app inventory, shortcuts, widgets and profile state must be treated as sensitive. Known ad/analytics SDKs and forbidden permissions are rejected by `scripts/check-privacy.sh` in CI; `INTERNET` is an explicit exception for off-by-default city weather and web autocomplete ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)); the rest of this page is design guidance unless it says otherwise.

## First Play candidate — 2026-10-15

[Full bundled policy](../app/src/main/assets/legal/privacy-policy.txt),
[approved HTML source](release/privacy-policy.html) (published at [the public privacy URL](https://www.abinjacob.in/chikku/privacy/)), and
[Data safety working answers](release/DATA_SAFETY.md) describe actual local/optional-provider
handling. The release opens Google separately; debug-only overlay evidence below is historical.
Weather is an additional reviewed use of INTERNET; it sends city text/coordinates only when enabled.
No “no collection” or ephemeral-provider declaration is inferred from an offline core.

## Threat model

| Asset | Threat | Mitigation |
|---|---|---|
| Installed app list | Behavioral profiling | Keep on-device; never pass local results to web autocomplete or telemetry |
| Workspace layout | Loss/corruption after update | Transactional Room, snapshots, backups, migration tests |
| Private/work profiles | Locked app leakage | UserHandle-aware identity, strict lock-state filtering |
| Notification contents | Unwanted access/storage | Notification Listener optional; badge counts only where possible |
| Widget content | Third-party RemoteViews failures | Framework host only, lifecycle isolation, no introspection |
| Backup archives | Malicious ZIP, arbitrary actions | Size limits, traversal defense, typed schema, atomic import |
| Gesture actions | Abuse of accessibility/admin grants | Request only when needed, explicit scope, clear revocation |
| Icon packs / assets | Untrusted providers, huge bitmaps | Resource limits, fallbacks, no arbitrary code execution |

## Data collection and network rules

- Core launcher should work fully in airplane mode.
- No ads, attribution SDK, behavioral analytics or silent remote-config service.
- Network integrations must be isolated, reviewed and explicitly enabled. `INTERNET` supports optional web autocomplete and city weather; the default search flow makes no network requests.

Implemented locally: the Discover switch defaults to off. Only after opt-in and a ready workspace
does Chikku inspect the current user's Google app and bind its overlay directly. The
Google app owns its feed, networking and personalization (including any Google-provided sponsored
content); Chikku does not download, scrape, cache or log cards, account details or profile contents.
Window metadata/configuration is sent to Google for rendering; no app inventory or search
query is sent. Binding is removed when disabled, stopped or in safe/recovery mode. Settings disclose
the external provider and experimental protocol before use. If the overlay is unavailable, a user swipe opens the installed Google app instead. No companion
app is required. This uses one specific Google package-visibility query, no permissions, exported components or dependencies.
Historical experimental release builds could only embed the feed through the optional, separately installed **Chikku Discover
bridge** (`:discover-bridge`). It is debuggable because Google only accepts debuggable or system
overlay clients, holds no data, requests no permissions, and exports one service that relays
overlay calls only for callers signed with its own key. Chikku uses it only after the same signature
check, and queries it by intent action alone. See [ADR-0004](decisions/0004-optional-google-discover.md).
- Never transmit package inventories, app launch history, contact data or widget content. Typed search text goes to the selected provider only when online suggestions are enabled, or through an explicit browser handoff.
- Crash diagnostics should be **user-triggered export or clearly opt-in**, redacted and reviewable before sharing.
- Avoid logging contact names, app queries, backup paths and locked-profile labels.

## Profile isolation

On Android 15+ a HOME app with ACCESS_HIDDEN_PROFILES must implement a separate Private Space container and locked-state semantics [S22]. Never expose locked apps in drawer, search, suggestions, thumbnails, exported backups or crash reports. Work-profile data must not be mixed with personal-profile data. Hidden apps are a **visual convenience**, not a secure vault. [App lock](APP_LOCK.md) uses the system screen lock to guard every launch Chikku performs. It still can't stop notifications, Recents, widgets or other launchers from opening an app, so it doesn't replace Private Space.

## Backup and restore security

- Use Storage Access Framework; no broad filesystem permissions.
- Validate archive entry paths, counts, decompressed sizes and compression ratios.
- Use explicit versioned data types; never execute imported intents or deserialize arbitrary classes.
- Strip local widget IDs and device-specific profile handles from portable export.
- Show a preview; require user confirmation; roll back on failure.
- Optional encrypted backups require a separate security design and cryptographic review.

## Permissions and disclosures

Implemented permission inventory on branch `feat/nova-guide` (2026-10-10; implemented, not yet verified on a device). The manifest now declares `INTERNET` for optional web suggestions ([ADR-0005](decisions/0005-optional-web-search-suggestions.md)).

| Permission / component | Kind | Why | When it is used |
|---|---|---|---|
| `INTERNET` | Normal (install-time) | Optional Google/DuckDuckGo autocomplete | Off by default; sends only typed text while Search is visible and online suggestions are enabled. No local results, identifiers, cookies, logs or history are sent. |
| `READ_CONTACTS` | Runtime (dangerous), **optional** | Contact results in universal search (G06) | Requested only when the user turns on contact results in Search settings. Contacts are read on-device per query and never stored, logged or transmitted. |
| `BIND_ACCESSIBILITY_SERVICE` on `ChikkuAccessibilityService` | User-enabled service, **optional** | "Lock screen" and "Recents" gesture actions (G10) | The user enables it in system settings, only after choosing such an action; Chikku explains why first. It performs those global actions only and does not inspect window content. Play's AccessibilityService policy disclosure must be reviewed before any store release. |
| `BIND_NOTIFICATION_LISTENER_SERVICE` on `ChikkuNotificationListener` | User-granted notification access, **optional** | Notification badges (G08) | The service is disabled until the user picks a badge style, and the system binds it only after the user grants notification access. Counts and avatars stay in memory; notification text is not stored. |
| `EXPAND_STATUS_BAR` | Normal (install-time) | "Show notifications" / "Quick settings" gesture actions (G10) | Used only when the user binds a gesture to one of these actions. It relies on a non-public status-bar call that some OEM builds may block; failures are reported to the user, not retried. |
| `REQUEST_DELETE_PACKAGES` | Normal (install-time) | "Uninstall" in the app long-press menu (G15) | Opens the system uninstall confirmation; the user confirms every removal. Chikku never uninstalls silently. |
| `USE_BIOMETRIC` | Normal (install-time) | App lock (2026-10-11) | The framework `BiometricPrompt` confirms the device owner before a locked app opens. Chikku only learns whether confirmation succeeded and never receives biometric data. See [APP_LOCK.md](APP_LOCK.md). |
| `<queries>` for icon-pack intent actions | Package visibility | Listing installed icon packs (G15) | Reads `appfilter.xml` and drawables of the pack the user picks; no `QUERY_ALL_PACKAGES`. |

- `READ_CONTACTS` and `BIND_ACCESSIBILITY_SERVICE` were removed from the privacy gate's forbidden list (`scripts/check-privacy.sh`) on 2026-10-10 with this rationale; neither is requested when its feature is off.
- Notification Listener only for optional badges/media; explain access and revocation.
- Device admin is **not** used.
- QUERY_ALL_PACKAGES and usage access should be avoided unless demonstrably necessary and policy-compliant.
- Publish a permission inventory and third-party dependency audit with each release.

## Secure engineering process

- Dependabot/Renovate and dependency/license scans; pinned dependencies.
- Code review for manifest changes, exported components, backup parsers and profile handling.
- Never commit signing keys, tokens, test backups containing real personal data or device logs.
- Use reproducible builds where practical; publish source-to-release provenance.
- Provide private security disclosure path (see [SECURITY.md](../SECURITY.md)).
- Threat-model review before public beta and before adding optional network integrations.

## Trust claims must be auditable

Nova's official owner says Prime remains ad-free [S01]; user concerns about trackers have conflicting reports [S31]. Chikku's differentiator should be **verifiable implementation choices**, not allegations about competitors. Public source and a network/dependency audit are stronger than marketing promises.

## Article additions (2026-10-11)

Search micro-results parse on-device. Maps, URLs, package/flight tracking and explorer addresses
leave the launcher only through a selected action (including the explicit IME Go action).
Dialer/composer intents do not make calls or send texts. No new permission is declared.

Search history ("Learn from my searches") is off by default. When on, it stores on this phone:
successful personal-app launches through Chikku, and contacts, shortcuts, folders and web or Play
Store queries opened from search. Entries hold identities only (component/profile, contact lookup
key, shortcut ID, folder ID) and the query text the user actually sent, plus a decayed use count,
an hour-of-day histogram and the last-use time. When a result is opened from typed search, its
entry also counts the first one to four normalized characters typed (for example "c", "ca", "cam",
"came"), so a result picked again and again for those letters becomes the top result; the rest of
the typed text is never stored. Entries never hold labels, contact names or numbers. Digit-only
queries (numbers, codes) are not kept, and typed prefixes without a letter are not learned. Hidden, app-locked and
non-personal apps and their shortcuts are never recorded or suggested. Entries unused for 90
days are dropped, with at most 200 entries and 30 queries. "Show suggestions" and learning are
separate: turning learning off pauses it and asks whether to keep or clear history; Clear and
per-item removal are always available. History is excluded from backups and is never logged. No
system usage access, analytics SDK or account connection.

Dock images require an existing persistable grant from the user's document selection; an imported
URI alone cannot authorize access. Loading runs on IO with byte/dimension bounds. Portable backups
contain only the reference, not the file or grant, and missing access falls back to color. The
selected document provider controls whether its files are local or cloud-backed. See
[article audit](NOVA_ARTICLE_PARITY.md) for implementation and verification boundaries.

## Phone settings expansion (2026-10-11)

The [phone inspection inventory](NOVA_PHONE_SETTINGS.md) records independently implemented
appearance and interaction controls. No new permission, exported component, dependency,
advertising SDK or required account is introduced. Inspection screenshots stay in ignored local
verification storage; they may contain personal information and are not bundled or exported.

Settings search indexes static localized titles/summaries only, without app inventories or stored
queries. Optional folder search excludes an entire folder title when membership is hidden,
unresolved or nonpersonal; HOME validates again before opening a result. Install placement is
off by default, personal-profile only, respects hidden/lock/recovery state, and rejects stale
enable-generation events and duplicate placements. No package/label/profile details are logged.

Private keyboard requests Android's advisory no-personalized-learning flag; it does not prove
that an external keyboard retains no input. Popup Hide is cosmetic drawer filtering, with existing
home placements preserved. Uninstall still opens Android confirmation and honors provider
capabilities. Optional Play Store actions hand the selected package's public listing URL to the
store or browser only after a tap; they do not add Chikku networking or export a package inventory.
Additional gestures use existing action/permission paths, and badge previews are synthetic with
no notification access or posted notification required. Named verification and remaining profile/
accessibility coverage are tracked in [implementation status](IMPLEMENTATION_STATUS.md).

## Local card conditions (2026-10-11)

[Local cards](FOLDER_PAGING_CARDS.md) add normal `ACCESS_NETWORK_STATE` solely for optional default-network Wi-Fi conditions. No runtime prompt, SSID/location access, network probe, new SDK, exported component or background action execution. Observation runs only while the panel is open and STARTED. App/shortcut/widget targets are validated off main against current personal profile and hidden state; inaccessible cards withhold titles. Configuration remains local and travels only through the existing user-initiated backup.


## Card content extension (2026-10-11)

[Card content](CARD_CONTENT_EXTENSIONS.md) adds optional Bluetooth audio access (`BLUETOOTH_CONNECT` at explicit enable on API31+, legacy `BLUETOOTH` capped at API30); no scanning/location/device identities. Opt-in weather reuses INTERNET with city-query/coordinate disclosure and provider attribution. Media shares optional user-granted notification access; badge OFF retains no badge data. Embedded widgets use separate host IDs and no-backup bindings with fresh restore identities. [ADR-0006](decisions/0006-optional-card-content.md) records reasons and limits.

## Notification and disclosure reliability (2026-10-11)

Clearing/disconnecting badges invalidates pending notification reads before any asynchronous rebuild can republish them. Profile changes clear immediately and final visibility checks withhold unavailable contents. Listener permission and listener connection are displayed separately; Reconnect asks Android to rebind without granting access. Badge OFF can still leave an explicitly enabled media feature using the same optional notification access.

Settings now disclose optional autocomplete queries and enabled weather city/coordinates leaving the device. Core launch/layout remain offline and ad-free; launcher contents and backups are not uploaded. Third-party apps/widgets retain their own privacy policies. The synthetic widget provider used for native lifecycle tests exists only in the test APK; no production exported component or permission was added. Private phone snapshots remain local in ignored verification storage.

## Contact communication services (2026-10-12)

No new permission (in particular no `CALL_PHONE`), dependency or exported component. Provider
support is checked with `PackageManager` for already-visible launcher packages; contact data rows
are read only for the final hits of an opt-in contact search, and nothing is sent to another app
before an explicit tap. Intents are pinned to the provider package. Hidden provider apps never
appear, locked ones require unlock, and only personal-profile contacts and apps are used. Backups
and restore checks hold card-button ids and a count-free attention only. See [CONTACT_SERVICES.md](CONTACT_SERVICES.md).


The 16 October [MET migration](release/MET_NORWAY_SWITCH.md) keeps city-search text offline. Only opted-in visible forecasts send chosen city coordinates with public app/contact identification; MET still receives client IP. Bounded provider cache/throttle metadata is intentionally excluded from backups. Current in-app/full policy and Data safety must match the final candidate and approved public policy.
