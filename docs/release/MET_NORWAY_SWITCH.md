# MET Norway migration — 16 October 2026

Owner authorized implementation and testing on 16 October. **Implemented and tested on
`feat/met-weather-migration`; not uploaded to Play.** This replaces the Open-Meteo forecast and
geocoder in this branch. Existing location/configuration and backup format 1 are preserved.
The historical plan below explains the reviewed design. Publication of the revised policy,
Data safety finalization and recipient-source/acceptance gates remain separate.

## Reviewed implementation design (15 October plan)

Use MET Norway Locationforecast2.0 compact for forecasts. Keep existing WeatherLocation label,
coordinates, opt-in and Celsius/Fahrenheit values unchanged in backups (format1). Truncate only
outgoing coordinates to at most four decimals; do not mutate saved precision. Keep default-off,
explicit consent and visible-card/lifecycle cancellation. Celsius is the API unit; convert locally.
The existing day-high/low semantics need a conscious migration: compute and label a next24-hour
range from instant temperatures, rather than call it today's local high/low without a timezone.
Map documented symbol codes into Chikku's own short condition descriptions; import no provider
logos or icon artwork. MET/CC BY attribution must link to the source/licence and explain rounding,
condition summarization and the derived temperature range.

City search also needs replacement: Open-Meteo geocoding is a separate use of its free service.
A concrete offline alternative is a bundled GeoNames cities500 gazetteer (cities >500 population
or administrative seats), with names/alternate names, country/admin labels and coordinates.
Search must run off main, normalize diacritics, bound results and support ambiguous city/country
input. Bundle the derived input and regeneration recipe, dated original ZIP/hash and CC BY4.0
notices. Compare actual supported place coverage before replacing the current geocoder; smaller
villages and newly renamed locations may differ. Never discard a restored location just because
it is absent from the new search catalog. Consider an explicit manual-coordinate fallback only
as a separately tested UI change. No new online geocoding service/account is assumed.

## Service obligations checked against primary documentation

- Identify requests with Chikku name/version and the public website/contact, not per-user IDs.
- Respect server Expires; retain Last-Modified exactly and use If-Modified-Since after expiry.
  Handle304 with the cached response. Refresh must not bypass server freshness or throttling.
  Persist only a bounded provider cache in private cache storage if needed across app restarts;
  list it as intentionally excluded from backup (re-fetchable provider data, not user settings).
- Serialize/deduplicate requests, bound transfer/decompressed body/timeouts, cancel on stop,
  support gzip and bounded secure redirects, and treat203 as a visible provider-version warning.
  Handle429/Retry-After and errors without retry loops or displaying malformed data as a forecast.
- MET's terms state no SLA and require special agreement above20 requests/second for the whole
  application, across installations. Their preferred architecture is a caching proxy; growth
  needs a proxy/traffic agreement rather than an unsupported scaling promise. No service bought
  or new backend deployed here.
- Commercial reuse of the default open data is permitted under the offered CC BY4.0/NLOD2.0
  licences with attribution. This is distinct from endpoint traffic/identification conditions.
- Direct API calls reveal client IP and requested coordinates in MET's Oslo access logs. No
  request-only retention period is established. Do not mark this ephemeral or anonymous, or
  promise Chikku can erase provider logs. No proxy currently masks the client's address.

## Verification before activation

1. Unit tests for fixture parsing, temperature conversion, unknown symbols, invalid values,
   chosen/old-backup coordinates, next24h boundaries, four-decimal formatting and offline city
   ambiguity/diacritics/empty results. Restore keeps every existing preference unchanged.
2. Transport/cache tests: HTTPS origin/redirect validation, gzip and byte bounds, cancellation,
   Expires/Last-Modified/304,203 warning,429 backoff, concurrent requests and process restart.
3. Synthetic on-device requests against approved fixed HTTPS origins: default-off sends nothing;
   city lookup sends nothing if offline data is chosen; forecast sends only chosen coordinates
   plus documented identification; detach/stop cancels. Do not use personal location/query data.
4. Update actual in-app disclosure, attribution, offline/privacy HTML, Data safety and store copy
   together. Review the revised concrete public policy before deploying that revision. Rebuild,
   inspect final APK/AAB, regenerate exact source/asset inventories and matching-source archive.

## Retrieved review material

Original GeoNames archives and one documented public Greenwich forecast example are downloaded
under ignored build/weather-provider-review solely for this review; they are not app assets or
proof of a completed provider migration. No real user location was queried. Retrieval and a
successful HTTP request are distinct from app integration, load acceptance and full terms review.

Primary sources checked15October2026: [MET terms](https://api.met.no/doc/TermsOfService),
[licences](https://api.met.no/doc/License), [client guide](https://api.met.no/doc/GettingStarted),
[Locationforecast](https://api.met.no/weatherapi/locationforecast/2.0/documentation),
[GeoNames data/readme](https://download.geonames.org/export/dump/readme.txt).


## Implemented and verified — 16 October 2026

- Forecasts use MET Locationforecast 2.0 compact; city search scans the bundled GeoNames index
  entirely offline. No Open-Meteo endpoint remains in production app sources. The 15 October
  cities500 snapshot contains **236,192 entries**, including administrative seats. Small villages
  and recent name changes can be absent. Old/restored locations remain valid without reselecting.
- The raw ZIP, country/admin inputs, original readme, full CC BY 4.0 and hashes are tracked under
  `data/weather/geonames-2026-10-15`. Original upstream whitespace/CRLF and licence end lines
  are intentionally preserved byte-for-byte, rather than normalized to satisfy a diff style check. `scripts/build-weather-cities.py` regenerates a deterministic
  gzip index (13,835,366 bytes; 33,251,801 decoded bytes). App notices credit both providers and
  describe the coordinate reduction, condition summaries and next-24-hour derived range.
- One serialized source enforces Expires/If-Modified-Since/304, a persisted minimum 60-second
  installation cooldown, 429 Retry-After, 403 backoff and visible 203 warnings. Units convert
  locally. A bounded private cache holds at most 24 responses, with 512 KiB decoded-body and
  1 MiB record limits. Cache/throttle data is deliberately excluded from portable backups.
- Fixed app/version/public-contact User-Agent, HTTPS origin/path/query validation, at most two
  same-origin redirects, gzip/decoded byte limits, timeouts and structured cancellation are
  implemented without a new permission, SDK or backend. No automatic background polling.

Verification: **230 core + 1,062 app tests**, zero failures/errors/skips in the full run;
**79 Python regressions**; debug/test/optimized release assembly; debug and release lint each
170 warnings and one hint, zero errors; manifest/tracking gate and exact dependency-byte audit.
The final packaging rebuild and focused weather tests passed. Static release APK checks verify
all eight legal documents, the exact gzip index/hash and all recorded raw-input hashes, existing
permission allowlist, nondebuggable R8 package identity, ELF LOAD and ZIP 16 KiB alignment.

On the owner-authorized disposable `chikku_play_preparation_20261015` ARM64 Android 16/API 36 AVD,
**three native tests passed in 58.760 seconds**. The real packaged index resolves Paris/France
and São Paulo/Brazil; default-off and offline lookup make zero weather-client calls. An opt-in
public Greenwich fixture obtains a real HTTPS forecast; Refresh and Fahrenheit conversion reuse
that response. Editor selection before opt-in, preserved choice after disable and stop cancellation
also pass. This is native emulator/source instrumentation, not a packet capture, physical test,
aggregate load acceptance or Play-delivered installation. The AVD uses actual 4096-byte pages.
No daily device or launcher data was touched; the AVD was stopped after testing.

The first native attempt caught a real packaging defect: AAPT stripped `.gz` from the asset name
and decompressed it, so the requested path was missing. The asset now uses `cities.index` while
retaining gzip bytes. A new packaged-file checksum/gzip check passes, followed by the successful
native rerun. Both failure and success logs remain in the local 16 October evidence directory.
Host fixture success alone did not establish packaged-asset correctness.

## Remaining distribution boundaries

MET's open-data licence permits commercial reuse with attribution; this removes reliance on
Open-Meteo's noncommercial free endpoint in this branch. It does not confer unlimited API capacity.
Before broader rollout, review aggregate request volume, MET's 20 requests/second threshold and
preferred caching proxy architecture; no proxy, paid service or provider agreement was created.
Direct client requests expose IP and chosen coordinates. MET's 26 November 2025 privacy policy
includes api.met.no, security/traffic/troubleshooting/statistics and up to 90-day IP retention for
no-login meteorological services; it does not establish every associated field's retention.
The revised offline policy and static HTML describe this. Specific approval to publish that
revision is pending; the public page still contains the previous approved Open-Meteo version.
Data safety must match the final binary and public policy before final Save/review. Numerical
four-decimal request precision is not a promise that a selected point is an imprecise location.
Source recipient access, contributor/reference rights, physical/OEM/restore and genuine 16 KiB
runtime gates remain open. No external test distribution, upload or production submission occurred.

Primary sources rechecked 16 October: [MET terms](https://api.met.no/doc/TermsOfService),
[licence](https://api.met.no/doc/License), [privacy](https://www.met.no/en/About-us/privacy),
[Play Data safety definitions](https://support.google.com/googleplay/android-developer/answer/10787469).


## Owner-approved publication and saved declarations — 16 October follow-through

The owner approved the exact revised MET policy on 16 October. Website commit `12a1c45`
publishes only the privacy route; Cloudflare version `af202e61-c3f2-446c-bd56-f85d0119d1a0`
serves HTTPS200 at https://www.abinjacob.in/chikku/privacy/ with approved SHA256
`ed8fad8f4da99110ce910bc1acb5f099439e4a2040191ba334d4d85540a74a1e`.
The website build passes; exact public bytes match the in-app policy's companion HTML. No scripts,
forms, cookies or analytics were added to this page. Original dirty portfolio edits were preserved.

This closes the pending policy publication recorded above. Data safety is now saved with four
optional, non-ephemeral collected/shared categories, and the saved listing names offline GeoNames
and MET weather. See [exact declaration/export reasoning](DATA_SAFETY.md). No Play review or upload
occurred; source/provenance, traffic/acceptance and future release gates remain unchanged.
