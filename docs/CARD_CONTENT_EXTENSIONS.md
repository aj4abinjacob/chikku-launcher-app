# Card content extensions

Dated implementation: 2026-10-11. Continuation of [local cards](FOLDER_PAGING_CARDS.md), authorized
by the user and implemented with agents in original Kotlin. Chikku surface colours, rounded
panels, typography, resource strings and motion settings are retained.

## Features

Settings → Cards → Add/Edit → Content chooses Actions, Weather, Media or Embedded widget.
Existing featured targets and six deliberate action slots are preserved. Draft content switches,
Cancel, Save, reorder, delete/undo and activity recreation use the existing settings repository.

- **Bluetooth audio connected:** an optional condition using connected HEADSET/A2DP profiles.
  Permission denial or unknown state hides the conditioned card. Enable requests access only
  on Android versions that require it. No named-device, scanning or all-profile support claim.
- **Weather (updated 16 October 2026):** default-off online switch, offline GeoNames city search,
  city choice, Celsius/Fahrenheit, MET current model estimate/condition and a derived next-24-hour
  high/low range. Refresh respects provider expiry and throttling. Failed requests remain
  recoverable; saved/restored cities are not discarded. No GPS; only chosen coordinates reduced
  to four decimals, IP/network metadata and fixed app identification reach MET. Up to 24 private
  cache records preserve freshness across restart. Attribution and full CC BY terms are bundled.
  See [migration evidence and remaining traffic/privacy gates](release/MET_NORWAY_SWITCH.md).
- **Media:** notification-access setup, current visible personal-player title/artist and supported
  play/pause/previous/next controls. Empty, denied and unavailable states stay actionable.
  Transport taps revalidate owner, access, session and supported action. Media can work with
  badges off; badge data remains cleared. No artwork network fetch or session backup.
- **Embedded widgets:** choose a visible personal provider and height, then configure inside the
  panel through Android bind consent/provider configuration. Dedicated host IDs and atomic
  no-backup binding storage preserve pending setup and recover provider loss. Missing providers
  remain configured; hidden/nonpersonal providers withhold the whole card. Restore renews each
  logical widget instance and requires new binding. Local setup recovery preserves damaged data.

The existing Home-widget featured target remains a navigation reference. Embedded content is a
separate host. No automatic background actions, weather predicate, named Wi-Fi/SSID, named Bluetooth
device, authenticated service integration or universal playback/provider compatibility is claimed.

## Platform, storage and privacy

[ADR-0006](decisions/0006-optional-card-content.md) records verified Android/provider documentation,
permission reasons and commercial-service boundary. New Bluetooth declarations serve only the
optional condition; existing INTERNET/notification-listener machinery is reused. No new exported
component, dependency, account, advertising SDK or Room migration. The 16 October revision bundles
licensed GeoNames data with original inputs, attribution and a reproducible generator.

Content variants and Bluetooth condition extend strict version-1 settings JSON. Old cards decode
as Actions with Bluetooth false. Invalid content rejects the whole import and preserves damaged
stored settings. Embedded widget IDs and ephemeral weather/media data are excluded from backup.
Native reads and network/storage work run off main; views and observations release on stop/close.
Settings/target changes revalidate visibility. Locked/nonpersonal and hidden identities must not
leak through titles, provider choices, media metadata or controls. Cosmetic hiding is not security.

## Verification

Implementation is awaiting this batch's serial build, unit, lint, privacy and device checks. Earlier
folder/card evidence remains in its dated document and does not validate these extensions.


## Weather revision — 16 October 2026

The owner-authorized [MET migration](release/MET_NORWAY_SWITCH.md) supersedes the weather provider,
city-query and daily-range descriptions above. Search is now offline against a236,192-place
GeoNames snapshot with aliases/country/admin labels; it works before enabling online weather.
Smaller/renamed places may be missing; existing/restored selections are kept. Only opted-in visible
forecasts send chosen coordinates to MET. Units convert locally; Refresh cannot override Expires,
conditional caching or rate limits. The range is labeled Next24hours. Cache/backup boundaries are
in [data model](DATA_MODEL.md#met-weather-migration-16october2026). Native/host results are tracked
in the migration report; implementation does not establish physical/OEM or production acceptance.
