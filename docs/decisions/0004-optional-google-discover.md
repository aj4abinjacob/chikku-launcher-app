# ADR-0004: Optional Google Discover cards without a companion app

- **Status:** experimental integration implemented locally; embedded overlay verified on one device for a debug build and for a release build with the optional bridge app (2026-10-12); app-opening fallback verified
- **Date:** 2026-10-10
- **Scope:** user-requested home-screen cards; does not complete M3/FR-021 or settle ADR-0001/0002

## First Play release decision — 2026-10-15

The owner selected Google-managed Play app signing with a separate upload key and **app-opening
fallback only for the first release**. Release `AppContainer` disables experimental overlay
resolution/health checks; it does not bind Google or an installed same-signer bridge. Debug builds
retain the experiment below. Restored enabled settings stay enabled, and the Google app is the
relevant restore dependency. No bridge-install recommendation is shown by release status.
[Distribution/signing implications](../release/DISTRIBUTION.md) and the
[current review](../release/PLAY_PREPARATION.md) govern the first release. The prior sections
are historical experiment evidence, not the current Play distribution choice. The companion
remains debuggable and outside Play; no guaranteed Google compatibility is claimed.

## Decision

Offer an off-by-default **Google Discover feed** (Google Now cards) switch under Home screen settings.
Swiping right from desktop page 0 (left in RTL) requests Google's own overlay window through a
direct connection to the current user’s installed Google app. If that overlay is not ready or Google
rejects the connection, the explicit swipe/action opens the existing Google app instead. The user
authorized this fallback on 2026-10-10. No companion app is required. An accessibility action opens it
without swiping. The feed takes
precedence over infinite-scroll wrapping at that edge while enabled; workspace page indices,
stored placements, default-page behavior and widget IDs remain independent of the external feed.

Google does not offer a guaranteed public launcher feed API. This is an explicit experimental
exception to FR-021's documented-API acceptance criterion, rather than a stock Pixel compatibility
promise. Direct overlay acceptance may depend on Google app version, device and build type.
Release compatibility is unverified; do not make release builds debuggable or impersonate another
launcher to obtain access. The app-opening fallback uses the public Android launch intent API.
Chikku does not bundle, install or suggest a companion app.

The platform adapter uses public Android binding/Binder/window APIs with an unofficial external
wire protocol. Resolver queries run on IO and inspect only the current user's Google app. Cards and their network access belong to Google. Chikku has no added
permission,
exported component, dependency or network access; it does not scrape or cache feed contents.
Only window parameters/configuration and lifecycle/open/close commands are sent to Google.

HOME can render without the Google app. Missing, disabled, incompatible or failed providers
produce an explanatory message on the user's open action. Disable, background/stop, window
detachment and safe/recovery mode tear down the overlay; stale callbacks are ignored. Back/Home
and opening the drawer close it. The preference persists through the existing settings codec,
process restart and backup/restore; old settings without the field retain the off default.

## Evidence and source reuse boundary

Checked 2026-10-10:

- [Google Discover help](https://support.google.com/websearch/answer/2819496?hl=en) documents access
  in the Google app and on some devices' home screens; this does not specify an embedding API.
- [AIDLBridge](https://github.com/amirzaidi/AIDLBridge), inspected at
  `a031d3785d4034dd2129d69536fd94532e288bdb`: bridge service and Binder contract used to understand
  interoperability. No license file or per-file license header was found in the inspected bridge
  implementation/IDL files. **No code, IDL, assets or dependencies from this project are copied.**
- [Lawnchair](https://github.com/LawnchairLauncher/lawnchair), inspected at
  `4793ba3a238bb1a3e786a3471a6d84767ed05267`: overlay interfaces and launcher-client protocol
  negotiation. The root Apache-2.0 notice is not a per-file/dependency audit; inspected client/IDL
  files have no individual license header. **No upstream client/IDL implementation is bundled.**

The Kotlin adapter is written here around wire descriptors, transaction ordering and parcel
formats needed for interoperability. This is not a dependency audit or a license clearance to
copy either upstream project later.

## Verification and remaining acceptance

JVM/Robolectric checks exercise default-off and persistence, first-page swipe and RTL, disabled
infinite scrolling, cancellation, modern/legacy parcel contracts, missing providers, direct Binder
failure/reconnect, disable/stop cleanup and stale callbacks. These use fake services, not Google's
live service. See [implementation status](../IMPLEMENTATION_STATUS.md).

Before claiming live support, record Google app version and Chikku build type on a named physical device:
enable → swipe → read cards → Back/Home → disable → restart; disable/update Google (and remove it where supported);
offline behavior; screen rotation/process recreation; TalkBack/large fonts; personal/work/private
profile isolation. The app-opening fallback was tested on one physical device with screenshot
inspection; embedded-overlay acceptance and performance remain unverified. See the verification log
in [implementation status](../IMPLEMENTATION_STATUS.md).
If Google stops accepting the protocol, contain the failure and revisit this adapter; do not add
hidden system APIs, scraping or a network SDK to keep the launcher functional.

## Revision: no additional installation (2026-10-10)

The earlier external AIDLBridge requirement is superseded by the user’s no-companion constraint.
The direct service connection receives Google’s overlay Binder without bridge transactions; the
bridge package query, callback protocol and download/setup links have been removed. Existing
settings and backups retain the same preference and off default. Unit checks include direct Binder
attachment, fallback with no overlay, bind rejection, launch failure and disabled-feature behavior.
No upstream code, IDL, assets or dependencies were added. The pinned Lawnchair source above also
contains a direct `LauncherClientService.onServiceConnected` path; it was inspected as protocol
evidence only. Google’s help describes access in its app, not a guaranteed third-party embedding API.

## Revision: embedded cards and the optional bridge app (2026-10-12)

**Window token.** The adapter sent `decorView.windowToken` (the view-root token). Google adds an
application window, which needs the activity token, so the window manager rejected every attach
("Attempted to add application window with unknown token") and the swipe always fell back to the
Google app. The adapter now sends `window.attributes.token`.

**Release builds.** With that fixed, a debug build shows the cards. A signed, non-debuggable release
build is refused at bind time: the Google app logs "Only system and debuggable apps are allowed to
connect". Making the release debuggable is still ruled out above. The user approved an optional
helper on 2026-10-12, superseding the earlier no-companion constraint for this path only:

- `:discover-bridge` (`io.github.aj4abinjacob.chikku.discoverbridge`) is a separate app, debuggable in
  every build type, with no permissions, data or launcher activity. Its one exported service binds
  Google's overlay under its own identity and relays Chikku's overlay transactions.
- Google also checks each call (`ensureValidCaller`). Handing Google's Binder to Chikku failed that
  check, as did relaying with Chikku's original interface header; relaying with a header rewritten by
  the bridge passed. Window parameters are relayed unchanged.
- Both sides check signatures: the bridge relays only for callers signed with its key, and Chikku
  uses a bridge only if it is signed with Chikku's key. The bridge must be signed with the same key as
  Chikku. Chikku prefers the bridge when present, else connects directly (debug builds), else opens
  the Google app.
- Play does not accept debuggable apps, so the bridge must be distributed outside Play. Google can
  close this path in any update; the app-opening fallback remains.
- Nova Launcher's installed companion was inspected only to confirm that a relaying companion still
  works with this Google app version (manifest and class/method names). No code or assets were copied.

Device check, Samsung Galaxy S24 Ultra (SM-S928B), Android 16, Google app `17.64.15.ve.arm64`, release
Chikku (R8, not debuggable) and release bridge, both signed with a throwaway local key: swipe showed
live cards in Google's window over Chikku, Back closed them, the feed reattached after leaving and
returning to HOME, and with the bridge disabled the swipe opened the Google app. Not verified:
rotation, process death of Google, TalkBack, offline behaviour, work/private profiles, other devices
or Google app versions, and a finger-tracking swipe (the feed opens when the gesture ends).

**Bridge failures are explained, not hidden (2026-10-12).** With an earlier launcher, the user's
helper app was battery-optimised and the feed silently stopped working while the launcher looked
fine; finding the cause took forum searches. On the S24 Ultra, Samsung's process freezer froze the
bridge whenever nothing was bound to it (`FreecessController … FZ … reason: Bg`); while Chikku was in
front and bound, the bridge ran at process state 3 and was not frozen. A second, unexpected failure
appeared: with Nova's Google companion running, Google rejected the bridge's calls
(`ensureValidCaller failed`); after stopping Nova, 3 of 3 swipes reached the overlay. Google appears
to serve one launcher client at a time, and Android reuses a cached bind without re-registering.

- Chikku binds the bridge with `BIND_IMPORTANT`, so it keeps HOME's priority while bound.
- A bridge that dies, cannot be bound or is refused triggers up to three automatic reconnects
  (1 s, 2 s, 3 s) instead of waiting for the next swipe.
- If the overlay stays silent for 5 s, Chikku asks the bridge to bind Google again with a fresh
  intent (a nonce query parameter) once per connection, then reattaches. Fresh intents leave a small
  system record each until Google's service restarts, so this only runs after a failure.
- While a bridge is connecting, a swipe shows "not ready" instead of opening the Google app. After a
  bridge failure, a swipe shows a dialog with the cause from a live health check (not installed,
  wrong key, disabled, not starting, battery-restricted, refused by Google, Google serving another
  client), a **Bridge app settings** button (app info, where OEMs put battery and sleep controls),
  **Open Google app** and **Try again**. Settings → Home screen shows the same status and re-checks
  on return.
- The bridge reports its own `isBackgroundRestricted` and standby bucket (no permission needed).
  OEM lists such as Samsung's "deep sleeping apps" cannot be read by apps; they show up as a bridge
  that does not start, and the message names them.

Unit-tested only (Robolectric fakes). Not yet verified on a device: the dialog and status texts, the
battery "Restricted" setting, Samsung deep sleep, automatic reconnect after the bridge is killed,
and whether re-registering actually wins the feed back from another launcher's companion.

## Restore on a new phone (2026-10-12)

A backup keeps `home.googleDiscoverEnabled`. Restoring it (from Settings or the first onboarding
step) on a phone without the Google app or without the bridge keeps the setting on; the restore
preview and a one-time card after the restore explain what is missing (Google app, bridge, or a
bridge that is not working) and link to Settings → Home screen, which shows the live status. The
swipe keeps its existing behaviour (open the Google app, or explain the bridge failure). Installing
the bridge later needs no further action. Unit-tested only.
