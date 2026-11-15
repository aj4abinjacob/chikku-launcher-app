# Data safety working answers — 15 October 2026

## Saved completion — 16 October 2026, after owner policy approval

**Final Save completed in Console; ready to send for review, not sent.** The prior draft/status
below is historical and superseded by this entry. Preview and the exported CSV confirm:

| Category | Collected/shared | Retention/control | Purposes (both collection and sharing) |
|---|---|---|---|
| Approximate location | Both | Non-ephemeral, optional | App functionality |
| Precise location | Both | Non-ephemeral, optional | App functionality |
| In-app search history | Both | Non-ephemeral, optional | App functionality |
| Device or other IDs | Both | Non-ephemeral, optional | App functionality; Analytics; Fraud prevention, security, and compliance |

Collection Yes, encrypted in transit Yes; no account creation/external login. The optional deletion
badge remains unanswered: no guarantee that Chikku can delete independent providers' logs.
No Families/security-audit/payment badge is claimed. Other local-only categories remain unselected.

Precise location is included conservatively because transmitted forecast points retain up to four
decimals and restored valid coordinates can refer to a small area. This covers the supported manual
selection/restore path; it is not a claim of GPS access or knowledge of the device's physical position.
Approximate location also covers city choice and provider IP-derived regional context. Numerical
precision alone does not prove physical-location inference; this is an explicit conservative
classification rather than an automatic equation of decimal places with a GPS permission.
Device/other IDs covers provider-observed/logged network addresses used for request handling,
traffic/security and usage statistics, rather than asserting IP is invariably a stable device ID.
The purposes reflect MET's documented processing, not an analytics SDK added to Chikku. The fixed
public app/version/contact User-Agent is not a per-user identifier. These are review judgments
under the linked Play definitions; reassess with provider-specific evidence or future changes.
Sharing stays Yes; no unsupported processor-contract or blanket user-action exception is asserted.
City-search text is now offline; In-app search history remains for optional Google/DuckDuckGo queries.

The exact owner-approved MET privacy page is published at
https://www.abinjacob.in/chikku/privacy/ (HTTP200, SHA256
`ed8fad8f4da99110ce910bc1acb5f099439e4a2040191ba334d4d85540a74a1e`).
Console displayed “Change saved. Send for review in Publishing overview.” The dashboard no longer
shows the incomplete app-setup section and now offers the closed-test tasks. Zero testers remain;
production access is still disabled. No review, bundle upload or rollout was performed.
Saved CSV SHA256: `1ae22b362202b11448b0e345bd89e95ef611e9080947b20faab79cb9f6a47016`.
Exact CSV/response snapshot and publication evidence are in the owner's 16 October evidence folder.


## Current MET branch review — 16 October 2026

The owner-authorized migration is implemented and tested; the Open-Meteo entries below are
historical evidence of the prior binary. Current branch behavior:

| Data | Current transfer and control | Working declaration |
|---|---|---|
| City-search text | Bundled GeoNames search, offline even before weather opt-in | No weather/geocoder collection; do not count this text as an online transfer |
| Chosen forecast coordinates | MET HTTPS only when enabled/visible; request values reduced to at most four decimals; prior/restored choices retained | Optional collected/shared, non-ephemeral, App functionality; retain location disclosure |
| Typed online suggestion queries | Google/DuckDuckGo, default-off separate control, unchanged | Optional In-app search history collected/shared, non-ephemeral, App functionality |
| Network address/metadata | Direct online providers see the client IP. MET documents usage/security/traffic/troubleshooting/statistics logs, with up to 90-day IP retention for no-login meteorological services | No ephemeral/anonymous promise. Review Device or other IDs and location inference according to actual provider use before finalizing |
| Forecast cache | At most 24 private re-fetchable records plus throttle metadata, no Chikku upload | Local-only storage, intentionally excluded from backups; not an additional off-device collection category |

The exact native public Greenwich request succeeded and local city search made zero weather-client
calls. Refresh and Fahrenheit reused the response. This is instrumentation/source evidence, not
a packet capture or proof of every Google/DuckDuckGo backend purpose. MET's service policy is
not a processor contract; retain the conservative sharing answer unless a documented exception
is established. The code sends no GPS reading, account/ad ID, cookies or local inventories.

The saved Console draft still has Approximate location and In-app search history. No final Save
or review was submitted. Precise-location/Device-ID categorization remains explicitly unresolved:
four-decimal points can be precise geographically, while a common chosen city centre is not
necessarily the user's physical location. Restored arbitrary valid coordinates must also be
considered. IP visibility alone is not proof of persistent device identification; MET uses its
logs for several stated operational purposes. Do not mark categories absent merely from missing
Android permissions, and do not invent account/advertising collection from a fixed app User-Agent.

Revised in-app policy and static HTML now name MET, local GeoNames search and provider retention.
The exact public-policy revision is awaiting specific publication approval; the live page remains
the prior approved version. Finalize the Console declaration only against the final binary and
matching public page. See [migration evidence](MET_NORWAY_SWITCH.md). No paid service, proxy,
new permission, tracking SDK, purchase feature or Chikku account was introduced.

Primary sources rechecked 16 October: [MET privacy](https://www.met.no/en/About-us/privacy),
[MET API terms](https://api.met.no/doc/TermsOfService),
[Play definitions](https://support.google.com/googleplay/android-developer/answer/10787469),
[Google privacy](https://policies.google.com/privacy), [DuckDuckGo privacy](https://duckduckgo.com/privacy).

## Historical Open-Meteo review and saved draft


**Draft, not submitted.** Use the final shipped build and all supported regions/features when
completing Console. Optional/default-off data transfers still count. Source review is not a
network capture. No blanket “no data collected” or “all processing ephemeral” answer is supported.

## Evidence and proposed form entries

| Feature / data | Leaves device? | Proposed answer / remaining decision | Purpose and control |
|---|---|---|---|
| App inventory, layouts, customizations, local history, contact search results | No Chikku upload in reviewed sources | Do not declare local-only processing as off-device collection. Describe access/use in privacy policy. Actual network/permission workflow check remains open. | Launcher functionality; local history/contacts opt-in; local deletion controls |
| Google/DuckDuckGo typed suggestion queries | Yes, HTTPS; provider also sees IP and network metadata | **Collects: Yes**, App activity → In-app search history, optional. Conservatively **not ephemeral** for the combined provider feature; Google retention is not established as request-only. | App functionality; Online search suggestions off by default; selected provider only |
| Weather city-search text | Yes, Open-Meteo geocoding | Include query handling with optional search/activity disclosure; it can also reveal chosen area. Do not omit because the value is city text. | Explicit city search in an online-enabled card |
| Selected city coordinates | Yes, Open-Meteo forecast; unrounded Double city-point values | **Location collected: Yes**, optional, app functionality, **not ephemeral**. Approximate location is a candidate category because this is a city selection, not device GPS. Precision needs a final decision: the actual transmitted point has finer numerical precision than a city-sized area. Do not equate “no location permission” with no location data or tick “no precise location” without reviewing this distinction. A conservative declaration can include both location categories until resolved. | Manual city, no GPS; disable weather/remove card stops new requests |
| IP/network metadata | Necessarily visible to online providers | Assess provider use under Device/other identifiers and inferred location, against Console definitions. Chikku adds no advertising/device/account identifier. That absence does not make a server-observed IP disappear. | Connectivity/security; Open-Meteo says logs may persist 90 days |
| Contact/dial/message/browser/store handoffs | Selected destination passed after a deliberate action | Evaluate each under the user-initiated transfer exception only where the recipient/purpose is apparent. Do not exempt all future integrations automatically. | User-selected action; no silent call/message |
| Discover | First Play build opens Google separately | No embedded feed SDK/bridge in this distribution. Treat the explicit app handoff separately; Google's later activity is governed by Google. Reassess if embedding ever returns. | Separate opt-in and explicit swipe |
| Widgets/document providers/shared backups | Data handled by a separate chosen provider/app | Review user-initiated exceptions per flow. Cloud-backed file providers may upload a chosen file; do not claim every destination is local. | System consent/picker/share selection |
| Notification/media/accessibility data | Reviewed Chikku handling is local/in-memory | No Chikku off-device collection identified. Accessibility metadata requests no events/window content; service declaration remains required. | Optional user access; revoke in Android settings |
| Support email | Only if user voluntarily sends it | Separate support correspondence, not an automatic app upload. Include content/contact handling in policy; never ask for real contact lists/backups by default. | Respond to request; user may request deletion |

For Google/DuckDuckGo/Open-Meteo, there is no repository evidence of a contract restricting them
to Chikku's instructions. Do not assume the “service provider” sharing exception. The conservative
working answer is **shared: Yes** for the optional query/location transfers unless a reviewer
establishes a specific user-consent/user-initiated exception against the actual disclosure and
provider purpose. Record that reasoning before changing the draft to No. Sharing is not synonymous
with sale; no sale or advertising SDK is introduced by Chikku.

All Chikku-owned online request URLs reviewed are fixed HTTPS; no cookies, app/contact results,
layout, profile inventory or persistent IDs are added. Propose encryption in transit only after
final runtime verification of the distributed variant. Browser/widget behavior is a separate
boundary, not evidence of Chikku HTTPS. No compulsory Chikku account exists, so account-creation
and account-deletion flows are not applicable. Local storage deletion and provider-held data
requests are explained separately; do not promise the developer can erase provider logs.

## Submission gates

- Confirm retained/linked query and IP handling for the exact provider endpoints; do not infer
  Google's service-specific retention from Chikku's lack of a disk cache. DuckDuckGo's published
  privacy claims still need mapping to this endpoint and Play's definitions.
- Resolve city-point precision and IP categorization. Validate restore cannot introduce an
  undisclosed coordinate use. Default-off and older-backup defaults remain covered by existing tests.
- Exercise default-off, opt-in, provider switch, denial/revocation, background/close cancellation
  and synthetic network captures without personal queries.
- Match public policy, in-app disclosures, listing and final manifest to these answers; recheck
  if billing/monetization, a proxy or any new provider is introduced.

References checked 15 October 2026: [Play definitions](https://support.google.com/googleplay/android-developer/answer/10787469),
[User Data/privacy policy](https://support.google.com/googleplay/android-developer/answer/10144311),
[Google privacy](https://policies.google.com/privacy), [DuckDuckGo privacy](https://duckduckgo.com/privacy),
[Open-Meteo terms/privacy](https://open-meteo.com/en/terms).


## Console draft saved —16October2026

Actual saved/exported draft: collectionYes, encrypted in transitYes, no account creation or
external login. Approximate location and In-app search history are each collected/shared,
optional, non-ephemeral, with App functionality for collection/sharing. The preview matches
these entries; no final Save/review submission occurred. Optional deletion-badge answer is blank.
CSV SHA256: `2c6b7c741c47355ff5a08c7e7c53d86671c53de279903bf5667bb6b56c65a9b2`.

This is a partial working declaration, not clearance to claim that other categories are absent.
Precise-location and Device/other ID entries are not selected pending the outstanding review.
Numerical digits of a geocoder's common city-centre point do not alone establish that the app
knows a user's actual physical position within3km²; that distinction informs the provisional
Approximate selection, not a categorical conclusion about every provider/restore path. Google
requires IP classification according to its particular use, not an automatic one-to-one IP/ID
rule. Review linked logs, inferred location and any identifier use/purposes before finalizing.
No Chikku location permission, advertising ID, account identifier or telemetry is added by this
form. No provider-deletion, ephemeral or all-processing-local promise is made.

The owner subsequently authorized implementing/testing the MET Norway/offline city-search
migration; see the current review above. No migration was activated by Console editing. Exact CSV and response snapshot remain outside Git in the
16October release-evidence directory. Recheck this form against the final binary/public policy.
