# ADR-0006: Optional Bluetooth, weather, media and embedded widget cards

- **Status:** implemented locally; validation tracked in [card content](../CARD_CONTENT_EXTENSIONS.md)
- **Date:** 2026-10-11
- **Scope:** user-requested continuation; does not accept or replace ADR-0001/0002

## Decision

Extend the existing Cards editor and explicit panel with independent Chikku implementations.
Conditions affect visibility and never execute unattended actions. Reuse theme surface colours,
rounded fields, motion preferences, strings and accessible controls. No library, SDK, account,
subscription, advertising asset or provider source code is added.

Bluetooth means **Bluetooth audio connected**, aggregated from Android's public HEADSET/A2DP
profile connection states. It does not identify a named device or promise all Bluetooth profiles.
Unknown or denied state withholds a conditioned card. Request `BLUETOOTH_CONNECT` on Android 12+
only after an explicit condition-enable tap; declare legacy normal `BLUETOOTH` through API 30.
No scan, device names/addresses, location, pairing or automatic connection. Observations exist
only while an enabled card requires the condition and the panel is open and STARTED.

Weather has a separate default-off online opt-in and a manually selected city. Explicit city
search sends typed city text to Open-Meteo geocoding; forecasts send the selected coordinates.
The provider necessarily sees the network address. No GPS, device identifier, app data, automatic
location discovery or weather history. Existing normal INTERNET permission is reused. Fixed HTTPS
origins, bounded responses/timeouts, no redirects/cookies, lifecycle cancellation, a 15-minute
memory cache and refresh throttling constrain requests. The editor and card link weather/data
attribution. Open-Meteo's public free service is noncommercial; choose an appropriate provider or
licence before commercial distribution. This prototype does not claim a purchased service plan.

Media reuses Chikku's existing optional notification-listener component, requiring Android's
user-granted notification access for public active-session APIs. Saving an enabled media card
makes the access entry available; opening the grant page requires a deliberate access-button tap.
No privileged MEDIA_CONTENT_CONTROL permission is requested. Badge OFF clears badge data even
when media needs the listener. Only current-user, visible, nonhidden personal app owners may expose
metadata or receive commands, with fresh checks on transport taps. Metadata stays ephemeral;
no artwork URI fetch, inventory log, session archive or background playback automation.

Embedded card widgets use their own AppWidgetHost and device-local no-backup binding store.
Portable settings hold provider component/profile, height and logical instance identity only.
Backup/export/restore renew the logical instance so IDs are never reused from an archive, even
on the same device. Allocate/persist pending state before system consent/configuration; cleanup
may delete only IDs proven owned by this host. Missing providers remain recoverable. Damaged
binding storage is preserved and requires explicit recovery. Hidden or nonpersonal providers
withhold the entire card; provider labels are read only after identity filtering. Close/stop
releases hosted views and listening. Home widget bindings remain independently owned.

## Owning-layer evidence

Checked 2026-10-11 using primary documentation:

- [Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
  and [BluetoothAdapter](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter)
  define API-dependent access and public profile connection queries.
- [MediaSessionManager](https://developer.android.com/reference/android/media/session/MediaSessionManager)
  allows an enabled notification listener to access sessions through its component.
- [AppWidgetHost](https://developer.android.com/reference/android/appwidget/AppWidgetHost)
  and [widget hosting](https://developer.android.com/develop/ui/views/appwidgets/host)
  define host allocation, consent, listening and hosted views.
- [Forecast](https://open-meteo.com/en/docs), [geocoding](https://open-meteo.com/en/docs/geocoding-api),
  [licence](https://open-meteo.com/en/licence) and [terms](https://open-meteo.com/en/terms)
  define the endpoint formats, attribution and free-service usage boundary.

Physical audio transitions, genuine active media sessions and third-party widget consent/configure/
provider loss require named device evidence; unit fakes and synthetic UI runs do not establish them.


## Weather provider revision — 16 October 2026

Owner explicitly authorized implementation/testing of [MET Norway/offline GeoNames](../release/MET_NORWAY_SWITCH.md).
This supersedes the Open-Meteo weather paragraph above for the release migration branch. A narrow
injected MET-only HTTPS transport and bounded private provider cache retain Expires/Last-Modified,
conditional 304, 203 warnings, gzip, bounded secure redirects and persisted request/429 throttling.
City search is offline and available before opt-in; only opted-in visible forecasts connect.
Portable locations/units/opt-in and backup format are unchanged. Next-24-hour range is explicitly
labeled rather than implying a local calendar-day high/low. CC BY 4.0 GeoNames source/derivative
and full terms are recorded/shipped; no provider icon artwork, new library or permission.

Commercial-compatible data licensing does not remove endpoint conditions: direct-client IP and
coordinates reach MET logs; public scale still requires aggregate-traffic/proxy review. This is
not permission to deploy a backend, contact providers, publish an app or accept unrelated terms.
Acceptance evidence is recorded separately in the migration report.
