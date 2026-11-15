# Console stages and listing draft

Status: **All app setup tasks saved complete**; see [Console execution record](CONSOLE_PROGRESS_2026-10-15.md).
Personal account/contact verification inspected; production remains locked behind the closed test.
No release was uploaded. Stage decisions below are distinct from agreement/release authorization.

## Ordered stages

1. **Account:** account details/verification dashboard: confirm personal/organization type,
   identity/address/contact and device-verification status. Resolve only actual prompts. The
   app dashboard already confirms production access is locked; do not infer account verification.
2. **App entry:** name Chikku Launcher; package `io.github.aj4abinjacob.chikku`; App; English
   (United States); Free. Future optional in-app purchases can coexist with a free download.
   Owner must review Developer Program Policies, export and signing agreements personally.
   Keep automatic installer protection off and verify its saved state before each release.
3. **App content/listing:** App / Personalization and Launcher tag are saved. Use
   contact `hello@abinjacob.in`. Complete privacy, Data safety, ads, app access, content rating,
   target audience and AccessibilityService declaration against the actual release.
4. **Signing/internal testing:** Google-managed app signing key, separate upload key. Protect
   credentials outside Git; back them up securely. Use internal testing only after source/privacy/
   service-rights gates, review Play-delivered install/update and pre-launch report. An AAB signed
   with an upload key is not the Play-installed signing identity.
5. **Closed testing:** finish setup first; recruit more than 12 people for resilience, keep at
   least 12 continuously opted in for 14 days, collect actual workflow feedback and record fixes
   with candidate version/source. The time threshold alone does not grant production access.
6. **Production:** apply for access and submit only after owner authorization and QA/source/legal
   gates. Then a separately authorized public GitHub release from the exact shipped source, with
   unprotected Play-signed APK and corresponding source. No repository is made public automatically.

## Saved copy (not sent for review)

**Title (15/30):** Chikku Launcher

**Short description (73/80):** Make Android home your own with offline app search, previews and backups.

**Full description:**

Make your home screen your own with Chikku Launcher, an independent Android launcher.

Arrange your apps, folders, dock and widgets. Adjust icon sizes, labels, spacing and colours,
and see many appearance changes in live settings previews. Choose drawer layouts and gestures
that fit how you use your phone.

Find apps offline, with optional local contact search. City selection also works offline using
GeoNames. Online search suggestions and MET Norway weather are separate choices, off by default.
When enabled, they send typed suggestion queries or chosen forecast coordinates to their providers.

Export your layout and settings through the system file picker. Restore with a preview, missing-app
attention and recoverable placements. Widgets require fresh Android binding after restore.

Chikku has no ads, tracking SDKs or compulsory account. The launcher core works offline.
Chikku's project code is licensed GPL-3.0-or-later. Third-party notices and the privacy policy are available in Settings.

Optional access is used only for relevant features: Contacts for local contact results;
notification access for badges and media; Nearby devices for a Bluetooth-audio card condition.
The optional AccessibilityService handles your Lock screen, Recent apps, notification shade or
Quick settings gestures. It cannot read screen content and does not record accessibility data.
You can refuse or revoke these grants and keep using the launcher.

Android and device manufacturers control system navigation and some launcher capabilities.
Chikku cannot provide stock system Recents gesture animations. This release can open the installed
Google app; it does not promise embedded or universally available Google Discover. Private Space
UI and broad foldable/OEM acceptance are outside this release's current verified scope.

This is a testing candidate. Read its release notes for tested devices, known issues and recovery
instructions before making it your daily launcher.

Before production, remove the testing-candidate sentence only after actual acceptance. The source
availability sentence must have a verified recipient download route; it is a release obligation,
not a claim that this private preparation branch is already publicly accessible.

## Assets

| Asset | Current state | Required next work |
|---|---|---|
| Selected icon | `design/branding/white-coat/play-512.png`, opaque full-square 512×512 RGBA PNG, 35,786 bytes, SHA-256 `a8f7f8d5…cdc1ab40`; matches the White Coat launcher icon shipped from beta 10 (build 11) | Uploaded and saved in Console on 5 November 2026 (Default store listing → App icon), replacing the superseded `design/branding/nose-notch-ink/play-512.png`; AI-created/edited label applied. The single change "Change app icon" was sent for Google review the same day (quick checks running); approval is pending. Rights gate remains |
| Feature graphic | Original1024×500 [SVG/PNG](store-assets/feature-graphic.svg) prepared, reviewed and saved in Console | AI-created/edited label applied; no provider/competitor artwork. Still draws the superseded Nose notch/Ink mark, so it needs a White Coat version before or with the icon upload |
| Phone screenshots | Four unedited1080×1920 genuine candidate captures prepared and visually reviewed; [manifest](store-assets/asset-manifest.json) | All four uploaded/saved16October; no tablet/OEM acceptance inferred |
| Accessibility video | Approved public page, original84.914-secondAPI36 video and captions published/verified16October | Future Console declaration after bundle recognition and physical acceptance remain open |

## Declarations and reviewer access

- **Ads:** Chikku has no ads/ad SDK. Third-party Google/provider apps can show their own content;
  the first Play release does not embed Discover. Do not market “no ads anywhere on your phone”.
- **App access:** Chikku has no account/login. Explain setting HOME and optional access routes,
  how to decline and switch back. The separately installed Google app is optional. Verify all
  reviewer-needed core workflows without credentials.
- **Accessibility:** classify as launcher/ordinary app, `isAccessibilityTool=false`; purpose is
  app functionality, optional explicit global actions. No event collection/window-content access,
  background automation or use of the service to perform purchases. Supply actual video; do not
  replace the in-app disclosure with the XML service description. Settings and gesture entry points now share the full disclosure before the grant screen;
  enabled-service settings remain available for revocation. Cancel/Not now/Back paths do not grant consent. Device refusal/retry/revoke and Console approval remain gates.
- **Content rating/target audience:** owner answers the questionnaire from final content and
  intended audience. Do not invent a rating, child-directed claim or ads/UGC exemption. Optional
  external apps/web search need accurate questionnaire consideration.
- **Permissions:** final APK inventory is recorded in artifact verification. Contacts, Bluetooth,
  notification and accessibility access must have install/deny/revoke/reboot workflow evidence.
  No QUERY_ALL_PACKAGES, usage access, device admin or advertising ID is requested.

## Privacy hosting — owner approved 15 October 2026

Proposed privacy URL: **https://abinjacob.in/chikku/privacy/**.
Concrete content: [static HTML](privacy-policy.html), matching the bundled offline policy; no
scripts, forms, cookies or analytics. Only this new route would be deployed to a site the owner
controls. The owner confirmed domain control and approved this exact route/content. Website
commit `2b682fd` adds only `public/chikku/privacy/index.html` from the latest remote main,
preserving unrelated local website edits. Cloudflare deployment succeeded, version `7ba68e12-0e02-4704-941a-8b614fc0d0be`. The public
URL redirects to `https://www.abinjacob.in/chikku/privacy/` and returns HTTP200 with bytes exactly
matching the approved file (SHA256 `6d1bd3726025c296067447f78901df9f4d84730eb4d4ae7bcd8352f9b6bf73ba`).
No login is required; the static page has no scripts/forms/cookies/analytics. Multi-region access
was not tested. Use the canonical www URL in Console. Exact headers/content and deployment log
are in the final local handoff evidence.

A sibling `/chikku/source/` index can provide exact release source downloads and checksums;
that is a separate unpublished proposal. Do not put a private GitHub link in Console as a privacy
or source-download substitute. Public privacy/source access does not require public repository visibility.

Official references checked 15 October 2026: [app setup](https://support.google.com/googleplay/android-developer/answer/9859152),
[assets](https://support.google.com/googleplay/android-developer/answer/9866151),
[AccessibilityService](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en),
[testing](https://support.google.com/googleplay/android-developer/answer/14151465).


## MET branch listing clarification — 16 October 2026

The saved copy above remains the exact Console text, not a claim that a new revision was saved.
For the migrated build, replace its online-feature paragraph with:

Find apps offline, with optional local contact search. City selection also works offline using
GeoNames. Online search suggestions and MET Norway weather are separate choices, off by default.
When enabled, they send typed suggestion queries or chosen forecast coordinates to their providers.

The revised full policy is prepared locally and the website build passes. Specific approval to
publish that exact revision is pending; do not treat the earlier Open-Meteo publication as MET
policy clearance. Data safety remains a draft and no bundle/review/rollout was submitted.


## Approved publication and final saved revision — 16 October 2026

The owner approved the exact revised MET policy on 16 October. Website commit `12a1c45`
publishes only the privacy route; Cloudflare version `af202e61-c3f2-446c-bd56-f85d0119d1a0`
serves HTTPS200 at https://www.abinjacob.in/chikku/privacy/ with approved SHA256
`ed8fad8f4da99110ce910bc1acb5f099439e4a2040191ba334d4d85540a74a1e`.
The website build passes; exact public bytes match the in-app policy's companion HTML. No scripts,
forms, cookies or analytics were added to this page. Original dirty portfolio edits were preserved.

The proposed MET listing paragraph above is now saved in Console (full description1,971 characters).
Data safety final Save completed and changes await review; no review or release submission occurred.
Earlier draft/pending-publication paragraphs are historical. See DATA_SAFETY.md for category rationale.


## Closed-test scope and demo publication — 16 October 2026

Owner selected all supported countries and approved the prepared demo's public route.
Closed testing - Alpha now targets **178 selectable countries/regions**, including Rest of World.
The country task is completed; track remains **Inactive**, **1 of 4** setup tasks completed.
Feedback is saved as `hello@abinjacob.in`; no tester list/accounts were invented, added or invited.
Owner will provide Google-account addresses or a Google Group. No release draft, upload or rollout
was created. Production still requires at least12 continuously opted-in testers for14 days.

The approved demo is live at https://www.abinjacob.in/chikku/accessibility/ . Website commit
`7a3d86d`, Cloudflare version `6d6fa74a-5352-41ac-b41e-33ebabde77ab` adds only the static page,
original84.914-second video and captions. Public HTTPS page/video/VTT bytes match the local
originals. Video SHA256 `b92fa454253d6e4c05eeb2494eb02421af9a1e3d34f4bbc30be991656fb7368f`.
Firefox loaded the1:25 duration, played with captions and reached100% loading; playback was paused.
This records the original15October disclosure/refusal/retry/grant/Recent-apps emulator flow,
not physical TalkBack/OEM acceptance or a Play-signed install. Revocation was checked after recording.
The current App content page says “You're all caught up”; no AccessibilityService-specific form
is currently listed, so its future declaration is **not** marked completed. Recheck after bundle
recognition. The no-new-permissions/service-data behavior remains the actual source boundary.

Publishing overview includes Data safety under “Changes not yet submitted for review”.
Send app for review is disabled pending required release steps. Managed publishing remains off.
This is saved setup, not policy approval by Google, external source clearance or production readiness.
