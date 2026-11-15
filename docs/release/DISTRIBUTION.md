# Distribution decisions — 15 October 2026

## Current weather decision — 16 October 2026

Owner authorized implementation/testing. `feat/met-weather-migration` now uses MET forecasts
and bundled offline GeoNames search, with full CC BY attribution, provider freshness and limits.
See [implementation, native evidence and remaining traffic/privacy gates](MET_NORWAY_SWITCH.md).
Open-Meteo options below are the historical 15 October review, not the branch's active endpoints.
No paid service, proxy or provider contact was authorized or created. Public-policy revision and
Data safety remain pending; do not externally distribute the old candidate as the migrated build.


## Weather: service access and data licences are separate

Runtime uses `api.open-meteo.com` and `geocoding-api.open-meteo.com`, no key. Existing UI credit
for Open-Meteo, GeoNames and CC BY4.0 was already present. Do not remove a working feature or
reset saved weather settings without a deliberate product decision.

The free service is noncommercial and has rate limits. Its terms explicitly include promotional
activities among commercial examples, so a free download alone does not settle this portfolio use.
IP/coordinate logs may last 90 days; the app's memory cache does not make provider processing
ephemeral. [Terms/privacy](https://open-meteo.com/en/terms).

Concrete options before distribution:

1. Retain the free endpoints only after obtaining appropriate confirmation that this distribution
   is noncommercial and fits aggregate limits. Disclose retention; keep attribution. Do not
   silently assume future paid features are allowed on this tier.
2. Choose Open-Meteo **Standard** commercial service (1M calls/month), customer endpoint and key.
   Get a current quote; do not purchase automatically. An embedded APK key is extractable; choose
   a provider-approved client-key model or a separately reviewed proxy with new privacy/security
   implications. [Plans](https://open-meteo.com/en/pricing).
3. Evaluate a commercial-compatible alternative such as MET Norway Locationforecast. It requires
   identifying app/contact User-Agent, attribution, caching/expiry handling and coordinated traffic
   limits. It logs direct-client IP/coordinates too; it is not an automatic privacy improvement.
   Geocoding still needs its own provider/rights review. New endpoint/client behavior needs implementation,
   updated disclosure and tests. [Terms](https://api.met.no/doc/TermsOfService),
   [data policy](https://api.met.no/doc/License).

The owner has not selected/purchased a commercial provider or authorized contacting one. Weather
remains implemented and off by default. Service rights remain a distribution gate.

## Signing and Discover: owner selected the first option

**Selected:** Google creates/manages the app signing key; a separate local upload key signs AAB
uploads. The first release opens Google after the user's outward swipe; it does not use the
unofficial overlay or companion. Release resources explain this; debug integration remains experimental.

Google signs the APK users install. The upload key is an upload credential, **not** that installed
identity. An APK signed locally with only the upload key cannot update a Google-app-signing-key
installation. For future GitHub release updates, obtain the **unprotected Play-signed universal APK**
for the exact same release from Console and verify its certificate against the Play app signing
certificate, package and increasing code. Preserve those certificate fingerprints and test
cross-channel updates. Do not confuse internal-app-sharing test certificates with production.
[Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756).

**Deferred alternative:** a developer-controlled app signing key transferred to Play plus separate
upload key could support self-signing the off-Play bridge with the same installed certificate.
Losing/rotating that key and certificate lineage have update implications. With a Google-generated
non-exportable signing key, this project cannot simply sign the arbitrary bridge with its upload key.
No key enrolment or consequential agreement was accepted by the agent.

The bridge is deliberately debuggable and relies on an unofficial Google protocol that can stop
working. Same-certificate guards are necessary but do not make the protocol a supported Google API.
Never submit the companion as the production app or make the main launcher debuggable to obtain a feed.

## Installer protection and future purchases

Turn off automatic installer protection/Require installation from Google Play and verify its saved
state for **each** release. It adds runtime installer checks that can route GitHub APK recipients
back to Play. Play signing is separate and remains selected. Do not add undocumented post-upload
code that breaks source correspondence, recipient freedoms or the offline core. Check the final
Play-processed APK, not only the local AAB.
[Installer check](https://support.google.com/googleplay/android-developer/answer/15621622?hl=en-gb).

Free download can later offer optional in-app purchases; it cannot be converted to a paid download
under the same listing once offered free. Future digital unlocks generally require Play Billing
unless a relevant regional program/exception applies. No billing SDK or purchase feature is added
now. Keep the offline core usable without payment or compulsory accounts/subscriptions, and revisit
weather rights, GPL/source compatibility and privacy before monetizing.
[Pricing](https://support.google.com/googleplay/android-developer/answer/6334373),
[payments](https://support.google.com/googleplay/android-developer/answer/9858738).

## Owner follow-through15October

Adults18+ chosen; new external upload key created with local recovery instructions, public
fingerprint in [Console progress](CONSOLE_PROGRESS_2026-10-15.md). Owner chose [MET Norway
migration preparation](MET_NORWAY_SWITCH.md); this does not activate a provider or close weather
service-rights gates in the current Open-Meteo candidate. No service purchased.


## Console signing evidence —16October2026

The managed app-signing key is already **In use**. The downloaded deployment public certificate
matches Console SHA256 `43:DD:51:85:8D:5F:EF:2D:47:E8:5B:A0:D9:FC:8B:04:6B:5C:E8:CE:7F:B5:CE:78:53:B6:CB:9D:20:8E:ED:61`.
Its archive also includes hybrid classical/PQC certificates; all three parse. No key change or
new signing agreement was made. Upload certificate is absent until the first app-bundle upload,
so the newly created local upload key is not yet verified by Play. Latest bundles/releases is
empty. Automatic protection explicitly showsOFF; no protection toggle was made. Recheck the
actual Play-processed APK and future per-release state before off-Play distribution.


## Approved follow-through —16October2026

The revised MET privacy policy and accessibility demo are approved/published with exact public
bytes verified. Data safety final Save completed (four optional/non-ephemeral collected/shared
categories); saved listing names offline GeoNames/MET. Closed Alpha countries target178 currently
selectable regions; no tester list/release is present and the track is inactive. See Console progress.
Earlier pending approval/draft notes are historical. No source publication, Play upload/review,
provider agreement, public app repository or production release is authorized by these actions.
