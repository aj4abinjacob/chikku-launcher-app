# Dock settings

Implemented locally on 2026-10-11, based on direct inspection of Nova Launcher **8.9.4**
(`versionName=89400 (8.9.4)`) on a Samsung Galaxy S24 Ultra (SM-S928B), Android 16/API 36.
The reference was Nova Settings → Home screen → Dock, including its expanded Label,
Dock background and Advanced controls. Screenshots are retained locally in ignored
`build/verification/dock-settings/`; Nova screenshots and artwork are not bundled with Chikku.
This is independent Kotlin implementation using existing Chikku assets and Android/Compose APIs.
No source, dependency, permission or exported component was added from Nova.

## Controls and behavior

| Control | Chikku behavior |
|---|---|
| Preview | Fixed preview above the scrolling controls; sample Chikku icons reflect size, labels, padding, count and background. Adding pages demonstrates the new available page during the drag; numbered buttons and swipes try each page. A caption explains requested pages that cannot fit at the current app count. No installed-app or profile inventory is read. |
| Enable | Hides the dock without removing its placements. |
| Icon size / Match desktop size | Independent size, or follows desktop changes while retaining the independent value. |
| Label | Expandable size, Normal/Medium/Condensed/Light font, shadow and one/two-line controls. App names remain available to accessibility when labels are hidden. |
| Padding | Auto horizontal padding follows the desktop; explicit horizontal and vertical choices are independent. |
| Dock background | Separate enable switch retains the chosen style/color/opacity. Rectangle, Rounded, Card and Custom corners are available. Custom corners have independent round/cut shapes and percentages. Optional extra inset and extension behind the navigation area. |
| Dock icons / pages | Existing placement-safe grid behavior; up to three pages, with effective pages capped by the 12-column storage grid. Shrinking the grid keeps placements recoverable. |
| Infinite scroll | Wraps in both directions, including dock accessibility actions; disabled for one effective page. |
| Display as overlay | Places the existing dock inside the workspace area without reserving a row; no system overlay permission. |
| Automatically close | Overlay-only. Successful app/shortcut launch hides the dock; a failed launch leaves it available. Toggle dock is available in gesture/folder pickers and as a workspace accessibility action. |
| Remember position | Stores the last visited page and restores it after recreation/process restart; otherwise returning HOME resets to the first dock page. |
| Search | Existing below-dock bar, engine and opt-in web suggestions remain at the bottom of this page. |

The page follows Nova's control flow within Chikku's own settings theme. It does not reproduce
Nova artwork or every numerical range. Existing Rectangle/Rounded/Card background insets and
existing users' icon sizing/padding remain compatible. The extra background inset adds padding
to those presets. Labels allow up to two lines. Navigation-bar appearance remains subject to
Android/OEM system-bar rendering; the control extends Chikku's own background beneath that area.

## State and boundaries

All preferences use the existing versioned settings codec and backup path, with numeric bounds,
unknown-value fallback and backward-compatible defaults. No Room migration is needed. The last
page is clamped when the icon count/page count changes. Local swipe state is not overwritten by
a delayed settings write. Overlay visibility survives activity recreation; toggled visibility
is transient and a cold start makes the enabled dock available again. Changing Enable or the
overlay mode also makes it available. Recovery/safe mode cannot show dock contents.

Verification evidence and remaining device checks are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#dock-settings-reference-and-workflow--2026-10-11).
This extends G03/FR-013 and does not accept the provisional architecture/build-versus-fork ADRs
or complete the broader M1 milestone.
