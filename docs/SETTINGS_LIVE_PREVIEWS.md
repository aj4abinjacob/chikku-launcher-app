# Settings live previews

## Drawer grid overview correction (2026-10-13)

App drawer → Grid & icons has a full-grid overview: native `AppIconView` cells are measured
using the same drawer geometry, then the entire visible grid is uniformly scaled into the fixed
preview frame, so five fitted rows remain visible together. That is about 25% scale, too small to
read icon and label size steps, so the preview opens at actual size and follows the edit: row
count and Fit rows drafts switch to the full grid, icon and label size drafts return to actual
size. An accessible Actual size / Full grid button switches by hand without changing preferences
or the preview height.
Captions and the single scene description distinguish scaled geometry from actual-size detail.
Canvas clipping happens after fitting; the native frame respects its parent's height constraints.

`DrawerTrueScalePreviewsTest` covers the reported 5×5 configuration including pixels in the last
row, row drafts, unchanged inspection height, large-font/RTL and constrained-height layouts.
`DrawerGridOverviewTest` exercises the actual-size default, switching on row and icon-size drafts,
the Compose mode button and scene descriptions. Test results are recorded in implementation status.
No emulator/physical-device or TalkBack verification was performed for this correction.

Implemented locally on 2026-10-12. This extends the earlier size/color samples; it does not
complete accessibility, device compatibility, M0 acceptance or a release gate.

## Covered controls

| Settings | Preview behavior |
|---|---|
| Home grid, spacing, icons and labels | Pinned samples follow dragged values; spacing bands are illustrative rather than pixel-identical wallpaper screenshots. |
| Home shading and widgets | Samples show system-bar shading, widget corners/inner space and allowed overlap without editing placements. |
| Home pages | Swipe or use labelled buttons to try transitions, wallpaper scrolling, indicators/auto-hide, wrapping and the default page. Shows the actual stored page count and newly added page; an explicit three-page demonstration makes motion testable without adding real pages. |
| Drawer | Density reflects columns, row spacing/minimum content height, first-row trim, tabs and search placement. Opening samples demonstrate close-after-launch and remembered scroll position; group tabs/member order use stable synthetic names. Samples demonstrate opening, scrolling/paging/wrapping, arrow/keyboard/pull-to-search, and fast-scroller color/bubble/side/gap. |
| Folders | Two labelled tiles, "On home screen" and "Opened", drawn with the real `FolderIconDrawable`, `AppIconView` cells and `FolderGridLayout`: preview style/count/background, window style, colour/opacity, corners, shadow, name, columns, rows/pages, sorting, icon size and labels. The open panel is built at phone size and scaled to fit. Behaviour switches (close after launch, folder swipes) have no preview; their summaries explain them. `FolderScenePreviewTest`. |
| Dock | Native sample icons/background show sizing/padding/corners/images/navigation extension. Increasing the page count demonstrates the new available page during the drag. Numbered page buttons, swipes and Previous/Next make every available page selectable; the preview explains when the storage limit prevents the requested count. Paging, remembered position, overlay and automatic closing have safe sample interactions. Dock → Spacing shows the native dock edge to edge at true size with the side and above/below spacing shaded, so every step is visible without a background; its height is fixed for the largest step (`DockSpacingPreviewTest`). |
| Search | Both Dock → Search bar and Search → Home search bar show dock/bar position and the outside gap during dragging. Presentation and source pages show synthetic compact/expanded results, real contact-card rendering, visible folder/shortcut/Store rows. Search → Suggestions shows ranked synthetic Suggested apps and a Recent query; showing and learning are independent, and pausing learning keeps the sample rows. Dock Search shows offline autocomplete examples for supported engines. |
| Style | Five focused subpages (Accent, Icons, Motion, Screen, App menus) each pin their own sample above the relevant controls. The overview stays a short navigation list. Editing drawer menu actions selects the Drawer sample; the custom HSV dialog themes sample controls during dragging, before Done. |
| Night mode | Drawer, folder, search and menu samples use effective surface colors/contrast, including custom colors and pure black. |
| Gestures | Safe touch practice and labelled alternatives show the effective home/icon/app-default/folder action without performing it. Loading/unavailable defaults are explicit. |
| Home Discover and Labs | Offline Discover illustrates swipe/feed/fallback; Labs shows the actual debug TextView style with fixed synthetic measurements. Neither binds a companion app nor observes live data. |
| Badges and cards | Badge sample stays pinned and reflects Off. Card preview can collapse to make room for controls; its content sample stays pinned while editing. Weather/media/widget artwork are synthetic. A temporary day/time/charging/Wi-Fi/Bluetooth simulator uses HOME’s visibility evaluator and reports why a card is hidden. |

## State, safety and rendering boundaries

Preview navigation, animation progress, selected preview context and practice output are temporary UI
state. Settings sliders keep their existing release-to-save behavior; card changes remain in the
editor draft until Save. No new preference fields, Room schema, backup format, permission, dependency,
exported component or source/asset licensing change was introduced.

Samples use original Chikku glyphs and synthetic names, not a user's installed-app inventory or
profile contents. Icon-pack samples inspect only artwork in the chosen pack on IO. Card previews
replace card titles, targets, city names and widget providers with synthetic content; requested widget
height/reference remains in the draft, without allocating, binding or configuring a widget. Weather/media/search examples issue no requests,
read no contacts or notifications, and never execute sample launch, lock or system actions.

PreviewPlayback is finite and explicitly triggered. It uses the existing Motion policy, respects
Chikku animation speed and system animation disabling, cancels superseded requests, and clears
stale transforms when settings change. Gesture cancellation does not perform a preview action.
Previous/Next/Replay/Try controls provide alternatives to pointer gestures. Compact frames and
scrollable/collapsible preview panels retain space for controls at large font sizes.

Miniature scenes use representative insets and synthetic content; they cannot establish exact
wallpaper/provider fidelity, physical gesture feel, frame pacing or universal system animation
integration.

Dock paging uses the same effective page count as HOME. Its existing 12-cell storage row fits
three pages at four apps per page, but only two at five apps per page. The requested count stays
saved; a limited preview explains that reducing apps per page makes room for more pages. Preview
navigation never changes the saved dock position. The caption and navigation controls reserve the
same height at one, two and three pages, keeping the slider still during a held drag.

## Verification

`PreviewDrawerFolderTest`, `PreviewHomeDockSearchTest` and `PreviewAppearanceGesturesTest` cover
geometry/fitting, paging boundaries, draw-state policies, native dock/bar layout, effective inherited
gesture actions, cancellations, missing artwork and menu composition. `LivePreviewsDeviceTest`
uses the actual Compose settings controls with in-memory preferences and repositories that reject
writes/launches. It checks held-drag updates, pinned position, night colors, large-font practice and
unsaved card-widget height. Device results and limitations are recorded separately in
[implementation status](IMPLEMENTATION_STATUS.md#settings-live-previews--2026-10-12).

The three older drawer row-spacing tests now assert density/resize/IME behavior and adapter/view
agreement, preserving the newer first-row trimming behavior from `cc2bf2a3` rather than copying its
formula or reverting concurrent drawer changes.

## Completing missing scenes — 2026-10-12

`SearchPreviewGapsTest`, `DrawerFolderPreviewGapsTest`, `ExtraPreviewGapsTest` and
`CardPreviewGapsTest` cover source rows/ranking, panel envelopes, drawer/folder behavior, structural
group order, actual/demo page counts, menu context, Discover, synthetic debug styling, condition
boundaries and featured/widget accessibility descriptions. `PreviewGapsDeviceTest` drives the actual
settings controls through in-memory repositories. No contacts or Bluetooth permissions are granted;
network, launching and real repository writes are rejected. Physical results are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#missing-live-previews--2026-10-12).

## Integration with current main

The original frequent-app setting was replaced on main by independent history learning/showing
settings. The merged preview follows those controls on Search → Suggestions; typed Apps/source
samples remain independent. Contact previews reuse main’s configurable contact-card renderer,
including selected/reordered buttons, wrapping labels and left-to-right digits inside RTL layouts.
Sample focus is disabled and icons/history remain synthetic. The dated main verification is recorded
in implementation status.

## Focused appearance pages — 2026-10-12

The former Look & feel preview occupied 320 dp and showed icons while the controls underneath
edited unrelated settings. Style (then called Look & feel) now opens five task pages (Accent, Icons, Motion, Screen,
App menus); its overview is a short navigation list. Each page reserves at most 208 dp (32% of
its available body height) for its own preview. The envelope stays fixed while settings change;
oversized sample content can scroll independently at large font sizes. Settings search opens the
owning page directly. Menu sample navigation retains the independent Home/Drawer action settings.

Existing preferences, defaults and portable backups retain their meaning. Page selection uses
existing temporary SavedState navigation, with no new stored settings, dependencies or permissions.
Executed checks and remaining accessibility limits are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#focused-look--feel-pages--2026-10-12).

## Horizontal scrolling sample (2026-10-13)

Home screen → Pages → Horizontal scrolling and App drawer → Horizontal scrolling pin a native
three-page sample above scrolling controls. Slider drafts change duration and strength before
save; a held strength drag pauses the real pager halfway so the effect is immediately visible; toggles and effect/sensitivity/edge choices update the sample. Generic Chikku glyphs and
labels are drawn by AppIconView, with the real WorkspacePager and PageIndicatorView. The native
scene has one Compose description, excludes sample descendants from accessibility focus, and
provides separate accessible Previous/Next buttons. The scene uses the same framing as Home → Pages:
a rounded inset over the stand-in wallpaper, with the drawer's effective background layered over it
for the drawer page. Sensitivity, end-of-pages feedback, effect and indicator are inline thumbnail
tiles (no dialogs); the settling and strength sliders name their ends; strength and auto-hide stay
in place, dimmed, when they don't apply. The envelope stays fixed when indicators hide.
No personal apps, contacts, wallpaper updates or launches are used. Drawer uses the workspace
pager to demonstrate shared effects/settings rather than exact RecyclerView physics; it does
not sample tabs or the external Discover overlay. `HorizontalPagingPreviewTest` verifies setting
and held-draft rendering. Native TalkBack, large font/RTL and finger feel remain unverified.
[Executed checks](IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).

## Vertical scrolling sample (2026-10-13)

App drawer → Vertical scrolling pins a rounded sample of 48 synthetic Chikku glyph cells on the
drawer's real `VerticalDrawerRecyclerView`, over the stand-in wallpaper with the drawer's effective
background. Scroll speed and the end-of-list effect are the drawer's own behavior, not an imitation;
the grid becomes one column for the List style. Flick up/down buttons send the same standard flick
every time, so the only difference between tries is the setting. Choices are inline thumbnail tiles.
The scene has one Compose description and hides sample descendants from accessibility; samples are
not focusable and launch nothing. `VerticalScrollingPreviewTest` covers speed, edge effect, List
style, colours and accessibility; `VerticalDrawerScrollingTest` covers fling scaling, the
platform cap, restoring the system edge mode and the drawer applying saved values.

