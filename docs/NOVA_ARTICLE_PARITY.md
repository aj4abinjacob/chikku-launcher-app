# Nova article comparison and Chikku controls

Reviewed 2026-10-11 against the current local implementation. These are independent Chikku
implementations; no Nova code, graphics or article screenshots are bundled. A control being present
does not establish device compatibility. Architecture ADRs and release gates remain provisional.

References:

- [SeerOfSouls: My Favorite Nova Launcher Features](https://www.seerofsouls.com/my-favorite-nova-launcher-features/), published 2024-06-25. Retrieved directly when the browsing service could not open it.
- [Android Authority: Nova Launcher guide](https://www.androidauthority.com/nova-launcher-885296/), article dated 2025-11-04; its Look & feel photograph informed the compact settings design.

This page introduces no NS/NF/NP/NU/CV or main research identifiers.

## Feature-to-control audit

| Article capability | Chikku entry point | Implementation / boundary |
|---|---|---|
| Layout backups, restore, sharing | Backup & restore | Existing versioned Chikku JSON, preview, undo and widget rebind; not Nova backup import |
| Icon swipe up/down | Icon long press → Swipe actions | Existing app/shortcut/launcher actions, component/profile identity |
| Calculator | Search → Calculator | Existing offline arithmetic |
| Unit conversion | Search → Search micro-results | Added temperature, length, mass, US cooking measures and time |
| URL/browser choice | Paste HTTP(S) URL in search | Added explicit Open link chooser |
| Map addresses | Search street address or `maps Bengaluru` | Added encoded map intent |
| Phone actions | Search phone number | Added dialer and SMS composer; user reviews the call/message |
| Package links | Search `track` and an identifier; UPS/postal formats recognized | Added explicit 17TRACK browser handoff |
| Flight links | Search `flight AI101` or `AI101` | Added Flightradar24 handoff; digit-leading airline codes need `flight` |
| Bitcoin/Ethereum links | Paste address in search | Added explorer handoff; pattern recognition, not address validation or financial operations |
| Discover | Home screen → Google Discover feed | Existing opt-in overlay/app-opening fallback, no companion app; experimental |
| Desktop grid, padding, labels, pages | Home screen | Existing controls |
| Layout lock | Home screen → Lock desktop | Existing edit guard |
| Widget placement, overlap and corners | Home screen → Widgets; long-press widget | Existing placement/resize; overlap now discoverable and corner clipping added |
| Dock icon count, size, labels, pages and background | Dock | Existing controls; background image picker and preview added |
| Quick search and engines | Home search bar, drawer, search gesture; Search settings | Existing local search and explicit engine/store handoffs |
| Contacts and app actions | Search → Contacts / App shortcuts | Existing opt-in contacts and public static/dynamic shortcuts |
| Learned app suggestions | Search → Suggestions | Opt-in on-device search history: suggested apps and recent contacts, shortcuts, folders and searches; separate learn/show switches |
| Private service content | Search → App shortcuts | App-published shortcuts can expose actions; no direct Spotify/Slack/Reddit/Netflix/Sesame account connector |
| Gestures and folder covers | Gestures; folder editor | Existing home gestures and folder tap/swipe actions |
| Badges | Notification badges | Existing dot/count/dynamic styles with listener consent |
| Drawer grids, style, search placement, opacity | App drawer | Existing controls |
| Hidden apps and groups | App drawer → Hidden apps / Drawer groups | Existing cosmetic hiding and tabs |
| Icon packs, menus, orientation, status bar, motion | Style | Existing controls |
| Dark surfaces | Night mode | Existing per-surface theme controls |
| Experiments | Tap the version in About 7 times → Labs | Existing Labs discovery; individual experiments retain their own status |
| Onboarding, fresh setup and reset | Backup & restore → Quick start / Reset | Existing recoverable setup workflows |
| Settings appearance | All settings pages | Compact accent toolbar, flat rows, section bands, selected-value dialogs and original Chikku icons |

## New behavior and limits

Micro-results are computed offline and can be disabled independently of Calculator. Conversions
copy only on a tap. HTTP(S) URLs reject embedded credentials; URI actions and tracking identifiers
are bounded and encoded. Phone actions use `ACTION_DIAL` and `ACTION_SENDTO`, without call/SMS
permissions. Status, explorer data and map results belong to the receiving app or website; Chikku
never retrieves them while parsing a query. Web autocomplete remains a separate off-by-default opt-in.

Search history ("Learn from my searches") defaults to off and replaces frequently opened apps
(existing counts carry over). See docs/PRIVACY_SECURITY.md for what is stored. Personal apps only;
hidden, app-locked, unlisted, work and other profiles are excluded from suggestions and recording.
Turning off or choosing Clear erases the counts. Counts are not included in portable backups.
No system usage permission, account, new exported component, dependency or permission was added.
This does not learn private routines or service playlists.

Dock images use a user-selected persistable document grant. Decoding runs on IO with an 8 MiB
encoded-file cap and a sampled bitmap no larger than 1024 pixels on its longest edge. An ungranted
URI from a backup cannot be opened. The photo shares background opacity, shapes, insets and the
navigation-area backdrop. References persist through settings/backups; image bytes and grants do
not migrate. Lost access leaves the chosen reference and color fallback with a Replace/Remove
workflow. Old settings retain no image, zero widget radius and disabled learning. Widget clipping
changes appearance only; it does not resize provider content or move saved placements.

## Platform evidence checked 2026-10-11

- [Android common intents](https://developer.android.com/guide/components/intents-common): map, dialer, message and browser handoffs.
- [LauncherApps](https://developer.android.com/reference/android/content/pm/LauncherApps): app-published shortcut access requires the HOME host role. It does not authorize reading arbitrary account content.
- [Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files): user-selected documents and persistable grants; deletion/movement can invalidate access.
- [17TRACK](https://www.17track.net/en) and its tracking web entry point, [Flightradar24 AI101](https://www.flightradar24.com/data/flights/ai101), [Blockchain.com explorer](https://www.blockchain.com/explorer), and [Etherscan](https://etherscan.io/) identify the named handoff providers. Etherscan returned HTTP 403 to the browsing service, so its live availability was not verified. Endpoint availability can change; no status/availability guarantee is made.

## Verification

Local test and physical-device results are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#nova-article-additions--2026-10-11).
Real third-party image selection/provider deletion, third-party widget hosting, external-app
handoffs, default-HOME shortcut integration, learning over extended use, TalkBack, large fonts,
RTL and broader OS/OEM coverage require separate workflow evidence. Fake providers and rendering
checks do not establish those results.
