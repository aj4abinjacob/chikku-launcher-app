# ADR-0005: Optional web autocomplete and discoverable dock search

- **Status:** implemented locally; validation recorded in [implementation status](../IMPLEMENTATION_STATUS.md)
- **Date:** 2026-10-10
- **Scope:** user-requested below-dock web search and suggestions; does not settle ADR-0001/0002

## Decision

Expose **Search bar below dock**, the shared web engine selector and **Online search suggestions**
under Dock settings. Search settings edit the same preferences. Existing top/hidden positions and
existing users' visibility choices are preserved. The bar identifies the selected engine with a
right-side mark and an accessible name; tapping it opens universal search. Tapping a suggestion opens that text in the selected engine through the
existing browser handoff. Google, DuckDuckGo, Bing, Brave, Startpage and Ecosia remain available for
explicit searches; live autocomplete supports Google and DuckDuckGo only and never silently uses
another provider.

The network switch defaults to off, including decoding settings/backups without the new key.
Its summary discloses that typed text goes to the selected provider. Android's normal `INTERNET`
permission is installed with the APK; it cannot be requested when enabling the feature. The switch
is the application-level opt-in. This explicitly revises the no-INTERNET bootstrap decision in
[ADR-0003](0003-m0-project-foundation.md). Core home/app/contact/shortcut search still works offline.
The privacy gate retains its other permission and SDK exclusions; no dependency or exported
component is added.

`AppContainer` injects a narrow source receiving only the engine and typed text. Suggestions have
an independent lifecycle-collected flow so the network never delays local results. A new query,
provider/switch change or loss of the active collector cancels the previous request and clears its
results. Requests wait 300 ms, require 2–200 characters, use fixed HTTPS origins and percent-encode
the whole query. Socket work and cancellation cleanup run on IO; connect/read limits are 3 seconds,
with a 4-second cancellation deadline and a 32 KiB response cap. Responses are plain text,
deduplicated, length/control-character filtered and limited to six suggestions. No cookies,
identifiers, retries, redirects, history, logging, disk cache or local search results are sent.
Providers necessarily receive the typed query and network address; their own privacy policies apply.
Failure shows a brief message while local search and explicit browser search remain available.

## Evidence and reuse boundary

Checked 2026-10-10:

- [Android networking documentation](https://developer.android.com/develop/connectivity/network-ops/connecting)
  confirms INTERNET is a normal install-time permission and networking must run off the UI thread.
- [DuckDuckGo deployment instructions](https://duckduckgo.com/duckduckgo-help-pages/get-duckduckgo/deploy-duckduckgo)
  document the `https://duckduckgo.com/ac/?q={searchTerms}&type=list` browser suggestion URL.
- [Google Chrome policy documentation](https://support.google.com/chrome/a/answer/2657289?hl=en)
  documents Google's `/complete/search` suggestion endpoint. This adapter uses `client=chrome` and
  the browser JSON format. This is browser interoperability, not a guaranteed Android launcher SDK.
- Live HTTPS requests from the development host using the non-personal query `weather` returned
  HTTP 200 and JSON arrays of suggestions from both origins. This is endpoint evidence, not device
  workflow or latency evidence.

All implementation is original Kotlin using Android/JDK networking and existing JSON support.
No provider code/assets, third-party client, SDK, credentials or dependency is copied or added.
Endpoint availability and formats may change; contain failures and revisit the adapter.

## Verification

Regression checks cover default-off/codec roundtrip, fixed URL encoding, unsupported providers,
malformed/bounded responses, fast typing, provider changes, disable cancellation, local search while
network work is pending, offline failure and clear-query behavior. Robolectric HOME checks cover
bar positions, the provider label and handoff. Physical-device evidence and remaining checks live
in [implementation status](../IMPLEMENTATION_STATUS.md). TalkBack, large-font, RTL and broader OEM
coverage require named workflow evidence before compatibility claims.

## Visual revision — 2026-10-11

The user-provided phone reference informed a solid navy capsule with a blank middle, an original
app-search glyph on the left and a selected-engine monogram on the right. Both home bar positions
share this view. Provider marks use original typography and colours; no Nova or provider assets
are copied. Disabling web search hides the provider mark and retains local search. The entire bar
remains one labelled accessible action. This changes appearance only; the network opt-in and
suggestion/browser behavior above are unchanged.
