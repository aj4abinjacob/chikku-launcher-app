# Nova Launcher feature inventory and Chikku parity matrix

**Research snapshot: 2026-10-10.** This is a **feature checklist**, not a claim that all entries are currently shipped by Nova. Nova's published descriptions verify only some capabilities; the rest need hands-on verification against a specific Nova version. Sources: [S02][S03][S14][S16][S35][S36] in [RESEARCH_SOURCES.md](RESEARCH_SOURCES.md).

**Nova evidence:** D = officially documented; R = user-reported behavior; V = historical/plausible but unverified in the current build; N = new Chikku proposal (not attributed to Nova). **Chikku:** M0 foundation, M1 essential parity, M2 extended parity, M3 optional. These are proposed priorities, not completion states; this matrix does not track implementation. What has been built (for example the Nova-guide features G01–G22 on branch `feat/nova-guide`, implemented but not yet verified on a device) and how it was verified is recorded in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

## A. Desktop / workspace (21)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| H01 | Multiple home-screen pages | V | M0 |
| H02 | Choose default home page | V | M1 |
| H03 | Configurable rows and columns | V | M0 |
| H04 | Subgrid / half-cell placement | D [S36] | M1 |
| H05 | Independent horizontal/vertical padding | V | M1 |
| H06 | Edge margins and spacing | V | M1 |
| H07 | Drag to reorder icons | V | M0 |
| H08 | Drag across pages and create page | V | M0 |
| H09 | Move icon or folder to dock | V | M0 |
| H10 | Home-screen layout lock | V | M1 |
| H11 | Add/delete/reorder home pages | V | M1 |
| H12 | Hide or customize page indicator | V | M2 |
| H13 | Infinite/circular page scroll | V | M2 |
| H14 | Scroll transition animations | D [S03] | M2 |
| H15 | Wallpaper scrolling | V | M2 |
| H16 | Icon/widget overlap behavior | V | M2 |
| H17 | Resize widgets on the desktop | V | M0 |
| H18 | Desktop search bar placement | R [S14] | M1 |
| H19 | Long-press blank desktop for editing | V | M0 |
| H20 | Desktop orientation/landscape options | V | M2 |
| H21 | Desktop edit-mode preview and undo | N | M1 |

## B. App icons, labels, and shortcuts (18)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| I01 | Third-party icon pack selection | D [S02] | M1 |
| I02 | Adaptive icon shape selection | D [S02] | M1 |
| I03 | Per-app custom icon override | V | M1 |
| I04 | Rename desktop shortcut labels | V | M1 |
| I05 | Icon size scaling | V | M1 |
| I06 | Icon label font size | V | M1 |
| I07 | Icon label color | V | M1 |
| I08 | Show/hide labels per surface | V | M1 |
| I09 | Label shadows and line wrapping | V | M2 |
| I10 | Icon normalization / legacy icon masking | V | M2 |
| I11 | Themed/monochrome icon support | V | M2 |
| I12 | Dynamic calendar icon support | V | M2 |
| I13 | Dynamic clock icon support | V | M2 |
| I14 | Long-press app shortcut menu | V | M0 |
| I15 | Pinned/deep shortcuts on desktop | V | M1 |
| I16 | Per-icon swipe-up action | D [S03] | M1 |
| I17 | Per-icon swipe-down action | D [S03] | M1 |
| I18 | Per-icon separate shape by surface | N [S15] | M2 |

## C. Dock / hotseat (15)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| D01 | Persistent bottom dock | V | M0 |
| D02 | Configurable dock icon count | V | M1 |
| D03 | Multiple dock pages | R [S35] | M1 |
| D04 | Swipe between dock pages | R [S35] | M1 |
| D05 | Dock background transparency | V | M1 |
| D06 | Dock background shape/color | V | M2 |
| D07 | Dock padding and side margins | V | M1 |
| D08 | Dock icon size independent of desktop | V | M1 |
| D09 | Dock label visibility | V | M1 |
| D10 | Dock search bar / search below dock | R [S14][S35] | M1 |
| D11 | Hide dock on selected pages | V | M2 |
| D12 | Infinite dock page scrolling | R [S13] | M2 |
| D13 | Folder placement in dock | R [S13] | M1 |
| D14 | Multi-row dock | N [S15] | M2 |
| D15 | Dynamic dock layout per screen/posture | N | M2 |

## D. App drawer (20)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| A01 | All-apps drawer | V | M0 |
| A02 | Vertical scrolling drawer | V | M0 |
| A03 | Horizontal/paged drawer | V | M2 |
| A04 | Drawer grid row/column density | V | M1 |
| A05 | Alphabetical ordering | V | M0 |
| A06 | Alphabetical fast scroller | V | M1 |
| A07 | Drawer app search | V | M0 |
| A08 | Open keyboard when drawer opens | V | M1 |
| A09 | Drawer tabs / custom groups | D [S03] | M1 |
| A10 | Folders inside app drawer | D [S03] | M1 |
| A11 | Hide selected apps without uninstall | D [S03] | M1 |
| A12 | Separate personal and work tabs | R [S35] | M1 |
| A13 | Swipe between drawer tabs | R [S35] | M1 |
| A14 | Recent/frequent apps suggestions | V | M2 |
| A15 | Drawer background opacity | V | M1 |
| A16 | Drawer background color | V | M1 |
| A17 | Drawer icon size independent of home | V | M1 |
| A18 | Drawer label styling | V | M1 |
| A19 | Drawer scroll/transition effects | D [S03] | M2 |
| A20 | Drawer tabs with independent sort modes | N | M2 |

## E. Folders (13)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| F01 | Create folder by drag/drop | V | M0 |
| F02 | Rename folder | V | M0 |
| F03 | Reorder items within folder | V | M0 |
| F04 | Folder background/color | V | M1 |
| F05 | Folder preview icon layout | V | M1 |
| F06 | Folder opening animation | V | M2 |
| F07 | Folder grid layout and spacing | V | M1 |
| F08 | Folder icon customization | R [S16] | M1 |
| F09 | Folder cover: tap first app, swipe folder | R [S16][S32] | M1 |
| F10 | Reverse cover: tap folder, swipe app | R [S16] | M1 |
| F11 | Swipe actions on folder icon | R [S16] | M1 |
| F12 | Folder inside app drawer | D [S03] | M1 |
| F13 | Large folders / nested folders | N [S15] | M2/M3 |

## F. Gestures and actions (16)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| G01 | Swipe up on desktop | D [S03] | M1 |
| G02 | Swipe down on desktop | D [S03] | M1 |
| G03 | Double tap on desktop | D [S03] | M1 |
| G04 | Pinch in/out | D [S03] | M2 |
| G05 | Two-finger swipe variants | V | M2 |
| G06 | Double tap then swipe variants | V | M3 |
| G07 | Map gesture to open app | D [S03] | M1 |
| G08 | Map gesture to app drawer/search | R [S17] | M1 |
| G09 | Map gesture to notification shade | R [S17] | M1, API/OEM check |
| G10 | Map gesture to Quick Settings | V | M2, API/OEM check |
| G11 | Map gesture to screen lock | R [S14] | M1, opt-in privilege |
| G12 | Map gesture to launch shortcut/activity | V | M2 |
| G13 | Gesture sensitivity / conflict settings | V | M2 |
| G14 | Icon swipe actions | D [S03] | M1 |
| G15 | Folder tap/swipe actions | R [S16] | M1 |
| G16 | User-visible capability warning for blocked actions | N | M1 |

## G. Search and discovery (14)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| S01 | Search installed apps | V | M0 |
| S02 | Search from app drawer | V | M0 |
| S03 | Search from home/dock | R [S14][S17] | M1 |
| S04 | Instant keyboard focus | V | M1 |
| S05 | Search app shortcuts | V | M2 |
| S06 | Search contacts (permission-gated) | V | M2 |
| S07 | Search device settings/deep links | R [S17] | M2 |
| S08 | Web search handoff | V | M2 |
| S09 | Configurable web provider | V | M2 |
| S10 | Sesame-like external integration | R [S10] | M3, legal/API review |
| S11 | Search suggestions/history | V | M2, local only |
| S12 | App name prefix matching | V | M0 |
| S13 | Substring/fuzzy/acronym matching | N [S15] | M1 |
| S14 | Offline, cancellable search with no network | N | M0 |

## H. Widgets and wallpaper (12)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| W01 | Android app widget picker | V | M0 |
| W02 | Host widgets on desktop | V | M0 |
| W03 | Widget binding permission flow | V | M0 |
| W04 | Widget provider configuration flow | V | M0 |
| W05 | Resize widgets | V | M0 |
| W06 | Widget move/reorder | V | M0 |
| W07 | Wallpaper picker / apply wallpaper | V | M1 |
| W08 | Wallpaper scrolling | V | M2 |
| W09 | Widget padding adjustments | V | M2 |
| W10 | Launcher-owned action/search widgets | V | M2 |
| W11 | Widget stacks | N [S15] | M2 |
| W12 | Recover/rebind missing widgets after restore | N | M1 |

## I. Appearance, theming and animation (11)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| T01 | Extensive color controls | D [S02] | M1 |
| T02 | Light/dark mode | V | M1 |
| T03 | Follow system theme | V | M1 |
| T04 | Material You dynamic colors | V | M2 |
| T05 | Drawer transparency | V | M1 |
| T06 | Folder background appearance | V | M1 |
| T07 | Home/drawer animation speed | V | M2 |
| T08 | Page transition styles | D [S03] | M2 |
| T09 | Scroll indicators | V | M2 |
| T10 | Blur/glass effects | N [S15] | M3 |
| T11 | Separate icon appearance by surface | N [S15] | M2 |

## J. Notifications, badges and cards (8)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| B01 | Notification badges/dots | D [S03] | M1, opt-in |
| B02 | Badge appearance/color/size | V | M2 |
| B03 | Numeric unread badges | V | M2, permission caveats |
| B04 | Dynamic notification badge preview | V | M2, privacy review |
| B05 | Optional notification listener | D [S02] | M1 |
| B06 | Media playback controls/card | V | M3 |
| B07 | Optional weather/card content | V | M3 |
| B08 | Never index notification text without explicit opt-in | N | M1 |

## K. Backup, settings and portability (9)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| P01 | Export launcher layout/settings | D [S02] | M1 |
| P02 | Restore launcher layout/settings | D [S02] | M1 |
| P03 | Local backup file | D [S02] | M1 |
| P04 | Cloud storage via user-selected provider | D [S02] | M2 (SAF, no own cloud) |
| P05 | Transfer settings to new phone | D [S02] | M1 |
| P06 | Import another launcher layout | V | M3, format/legal review |
| P07 | Versioned schema and migrations | N | M0 |
| P08 | Backup import preview and undo | N | M1 |
| P09 | Backup validation and corruption repair | N | M1 |

## L. Integrations, profiles, accessibility (13)

| ID | Feature / configuration | Nova | Chikku |
|---|---|---|---|
| X01 | Default HOME app selection | V | M0 |
| X02 | App install/uninstall observation | V | M0 |
| X03 | Work-profile app launching | V | M1 |
| X04 | Android 15+ Private Space container | V | M1, API 35+ |
| X05 | Optional Google feed/Discover integration | R [S14] | M3, no undocumented hooks |
| X06 | Third-party icon pack discovery | D [S02] | M1 |
| X07 | Accessibility-based optional actions | D [S02] | M2, user consent |
| X08 | Optional device admin for lock action | D [S02] | M2, security review |
| X09 | App info/uninstall shortcuts | V | M1 |
| X10 | Hidden apps (visual only, not a vault) | D [S03] | M1 |
| X11 | Tablet/landscape responsive layout | V | M2 |
| X12 | Independent foldable cover/inner layout | N [S12][S13] | M2 |
| X13 | Keyboard, TalkBack, reduced-motion support | N | M0–M2 |

## Chikku-only differentiation candidates (do not label Nova features)

- Separate profiles for foldable cover/inner, portrait/landscape and tablets, with explicit per-profile migration.
- Fast substring/fuzzy search with reproducible ranking and local index.
- Offline-first, zero ad/analytics SDKs, auditable network permissions.
- Crash-safe workspace snapshots and repair UI; undo for destructive edits.
- Export/import with format versioning, integrity checks, dry-run preview and widget placeholders.
- Public compatibility dashboard, user-submitted anonymized reproduction recipes, quarterly API audits.
- Widget stacks, independent surface theming, large folders, accessible gesture customization.

## Verification protocol

For each **V** and **R** entry: record Nova build, Android version, OEM, exact settings path, screenshot/video, availability in Free vs Prime, required permission, limitations, and whether the feature still works. Do not implement an undocumented proprietary protocol simply because it appears in a historical screenshot.

**Size:** 170 individual checklist items across 12 categories. This is a backlog of things to verify/consider, **not** a promise of Nova parity or a claim of 170 currently available Nova features.
