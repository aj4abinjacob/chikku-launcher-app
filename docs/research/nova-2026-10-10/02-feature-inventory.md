# Nova reference feature inventory

Research snapshot: **2026-10-10**. Source IDs: [register](04-sources.md).

This is a detailed capability inventory and validation checklist, not a verified dump of every current setting. No Nova handset inspection was performed. Stable, beta, historical, and user-reported capabilities differ. Exact option limits and current entitlement boundaries require an installed-version audit.

**149 entries:** 31 V, 48 C, 19 U, 42 H, 8 B, 1 L. Small controls are separate entries because their combinations matter to migration. Do not interpret the entry count as a count of independent major features.

This supplement uses NF identifiers, distinct from the main [feature matrix](../../NOVA_FEATURE_MATRIX.md). It adds historical and recent evidence without replacing the main backlog.

## How to read it

- **C:** current official site/FAQ/listing documents the capability. This still does not prove every build works correctly.
- **H:** official historical release evidence. Recheck whether it remains available.
- **B:** explicitly documented in a beta announcement; current stable inclusion unverified.
- **U:** direct user observation or preference. Verify exact behaviour.
- **V:** setting/behaviour to inspect; this research did not independently verify it. A dash means there is no adequate item-specific citation.
- **L:** retired legacy integration.

Prime = Yes only where a reviewed current source explicitly identifies the paid feature. A question mark means entitlement was not established. Historical paid/free status may have changed. Chikku's core and customisation are proposed to have no feature paywall.

Scope labels: Core = basic daily-driver behaviour; Essential = familiar customisation; Later = additional parity. Investigate/Deferred/Excluded are not release promises. These scope labels do not replace the milestone assignments in the main [roadmap](../../ROADMAP.md).

## Home screen

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF001 | Grid dimensions | V | ? | NS01 | Core |
| NF002 | Subgrid placement | C | ? | NS11 | Essential |
| NF003 | Multiple pages | U | ? | NS21 | Core |
| NF004 | Default home page | V | ? | — | Core |
| NF005 | Reorder pages | V | ? | — | Essential |
| NF006 | Wallpaper scrolling | U | ? | NS13 | Essential |
| NF007 | Infinite home scroll | H | ? | NS07 | Later |
| NF008 | Page indicators | U | ? | NS22 | Essential |
| NF009 | Persistent indicator | U | ? | NS22 | Essential |
| NF010 | Screen margins | V | ? | — | Essential |
| NF011 | Icon spacing | V | ? | — | Essential |
| NF012 | Desktop layout lock | H | ? | NS07 | Essential |
| NF013 | Temporary unlock | H | ? | NS07 | Essential |
| NF014 | Rotation controls | V | ? | — | Later |
| NF015 | Blank home setup | U | ? | NS21 | Essential |
| NF016 | Home label colour | C | ? | NS02 | Essential |
| NF017 | Add/remove home items | U | ? | NS22 | Core |

## Dock

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF018 | Dock toggle | H | ? | NS07 | Core |
| NF019 | Multiple dock pages | H | ? | NS07 | Essential |
| NF020 | Dock looping | H | ? | NS07 | Later |
| NF021 | Dock width | U | ? | NS22 | Essential |
| NF022 | Dock icon count | V | ? | — | Essential |
| NF023 | Dock background | H | ? | NS06 | Essential |
| NF024 | Dock labels | V | ? | — | Essential |
| NF025 | Dock folders | U | ? | NS21 | Essential |
| NF026 | Dock icon gestures | U | ? | NS21 | Essential |
| NF027 | Dock widgets | H | ? | NS07 | Later |
| NF028 | Dock landscape placement | V | ? | — | Later |

## App drawer

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF029 | Vertical scrolling | C | ? | NS11 | Core |
| NF030 | Horizontal scrolling | C | ? | NS11 | Essential |
| NF031 | Alphabetic sorting | C | ? | NS02 | Core |
| NF032 | Drawer tabs | C | Yes | NS11 | Essential |
| NF033 | Drawer folders | C | Yes | NS01 | Essential |
| NF034 | Hide drawer apps | C | Yes | NS11 | Essential |
| NF035 | Drawer label colour | C | ? | NS02 | Essential |
| NF036 | Drawer grid | V | ? | — | Essential |
| NF037 | Drawer background opacity | V | ? | — | Essential |
| NF038 | Fast scrollbar | H | ? | NS07 | Essential |
| NF039 | Drawer button | C | ? | NS02 | Core |
| NF040 | Swipe-open drawer | H | ? | NS07 | Core |
| NF041 | Drawer keyboard auto-open | H | ? | NS06 | Essential |
| NF042 | Frequent apps | H | ? | NS07 | Later |
| NF043 | Recent/new app sections | H | ? | NS07 | Later |
| NF044 | Restore drawer scroll | V | ? | — | Essential |
| NF045 | Drawer transition choice | H | ? | NS07 | Later |
| NF046 | Separate work tab | H | ? | NS07 | Essential |

## Folders

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF047 | Home folders | C | ? | NS02 | Core |
| NF048 | Drag-to-create folder | C | ? | NS02 | Core |
| NF049 | Folder naming | V | ? | — | Core |
| NF050 | Folder item order | V | ? | — | Core |
| NF051 | Folder icon edit | V | ? | — | Essential |
| NF052 | First-item tap action | U | ? | NS21 | Essential |
| NF053 | Swipe-open folder | U | ? | NS21 | Essential |
| NF054 | Windowed folders | H | ? | NS07 | Essential |
| NF055 | Immersive folders | H | ? | NS07 | Essential |
| NF056 | Bottom-aligned folder | H | ? | NS06 | Essential |
| NF057 | Folder scrolling direction | H | ? | NS07 | Essential |
| NF058 | Folder grid | V | ? | — | Essential |
| NF059 | Folder preview styles | V | ? | — | Later |
| NF060 | Folder background shapes | H | ? | NS07 | Essential |
| NF061 | Folder colour/opacity | H | ? | NS07 | Essential |
| NF062 | Folder preview priority | H | ? | NS07 | Later |
| NF063 | Nested folders | U | ? | NS22 | Investigate |

## Icons and theme

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF064 | Icon packs | C | ? | NS11 | Essential |
| NF065 | Icon reshaping | C | ? | NS11 | Essential |
| NF066 | Icon size | C | ? | NS11 | Essential |
| NF067 | Individual icon edit | U | ? | NS22 | Essential |
| NF068 | App label rename | V | ? | — | Essential |
| NF069 | Label hide | V | ? | — | Essential |
| NF070 | Label fonts | V | ? | — | Later |
| NF071 | Label shadows | V | ? | — | Essential |
| NF072 | Icon shadows | H | ? | NS07 | Later |
| NF073 | Per-icon reshape toggle | H | ? | NS07 | Later |
| NF074 | Dynamic calendar icons | V | ? | — | Later |
| NF075 | Dynamic clock icons | V | ? | — | Later |
| NF076 | Monochrome themed icons | V | ? | — | Essential |
| NF077 | Fallback icon masking | V | ? | — | Essential |
| NF078 | Custom colours | C | ? | NS11 | Essential |
| NF079 | Material You colours | C | ? | NS11 | Essential |
| NF080 | Light/dark modes | C | ? | NS11 | Core |
| NF081 | System theme following | C | ? | NS11 | Core |
| NF082 | Sunrise/sunset mode | C | ? | NS11 | Later |
| NF083 | Always-dark mode | C | ? | NS11 | Core |
| NF084 | Transparent system bars | V | ? | — | Essential |

## Widgets

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF085 | Third-party widgets | U | ? | NS23 | Core |
| NF086 | Widget resize | V | ? | — | Core |
| NF087 | Widget subgrid placement | C | ? | NS11 | Essential |
| NF088 | Widget overlap | H | ? | NS07 | Later |
| NF089 | Bring front/send back | H | ? | NS07 | Later |
| NF090 | Widget padding toggle | H | ? | NS07 | Essential |
| NF091 | Widget corner radius | H | ? | NS07 | Later |
| NF092 | Widget colour override | H | ? | NS06 | Later |
| NF093 | Widget reconfigure | V | ? | — | Core |
| NF094 | KWGT compatibility | U | ? | NS23 | Core |
| NF095 | KLWP interaction | H | ? | NS07 | Later |

## Gestures and actions

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF096 | Desktop swipe | C | Yes | NS01 | Essential |
| NF097 | Pinch gestures | C | Yes | NS01 | Essential |
| NF098 | Two-finger swipe | C | Yes | NS01 | Later |
| NF099 | Double tap | C | Yes | NS11 | Essential |
| NF100 | Double-tap swipe | H | ? | NS07 | Later |
| NF101 | Icon swipe up | C | Yes | NS11 | Essential |
| NF102 | Icon swipe down | C | Yes | NS11 | Essential |
| NF103 | Left/right home gesture | H | ? | NS06 | Later |
| NF104 | Launch app action | C | Yes | NS01 | Essential |
| NF105 | Launcher action | C | Yes | NS01 | Essential |
| NF106 | Shortcut action | U | ? | NS21 | Essential |
| NF107 | Screen-off action | C | ? | NS02 | Essential |
| NF108 | Invoke system overview | C | ? | NS11 | Later |
| NF109 | Home button mapping | V | ? | — | Later |

## Search

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF110 | App search | C | ? | NS11 | Core |
| NF111 | Contact search | C | ? | NS11 | Essential |
| NF112 | App-content integrations | C | ? | NS11 | Later |
| NF113 | Calculations | C | ? | NS11 | Later |
| NF114 | Unit conversion | C | ? | NS11 | Later |
| NF115 | Package tracking | C | ? | NS11 | Deferred |
| NF116 | Search bar placement | C | ? | NS11 | Essential |
| NF117 | Search bar styling | V | ? | — | Essential |
| NF118 | Package-name search | H | ? | NS06 | Essential |
| NF119 | Direct URL opening | H | ? | NS06 | Essential |
| NF120 | Web suggestions | H | ? | NS06 | Deferred |
| NF121 | Sesame integration | H | ? | NS07 | Investigate |
| NF122 | Shortcut search | B | ? | NS08 | Essential |
| NF123 | Search keyboard handling | V | ? | — | Core |

## Badges, backup and integrations

| ID | Capability | Evidence | Prime | Source | Suggested scope |
| --- | --- | --- | --- | --- | --- |
| NF124 | Notification dots | C | Yes | NS02 | Essential |
| NF125 | Numeric notification dots | C | Yes | NS02 | Essential |
| NF126 | Dynamic notification badges | C | Yes | NS02 | Later |
| NF127 | Badge presentation controls | C | Yes | NS01 | Essential |
| NF128 | Local backup/restore | C | ? | NS11 | Core |
| NF129 | Cloud document backup | C | ? | NS11 | Essential |
| NF130 | Shareable setups | U | ? | NS21 | Essential |
| NF131 | Backup version compatibility | C | ? | NS02 | Core |
| NF132 | Google Discover companion | C | ? | NS02 | Deferred |
| NF133 | Discover dark mode | H | ? | NS09 | Deferred |
| NF134 | Smartspacer feed | H | ? | NS06 | Deferred |
| NF135 | Nova Now feed | B | ? | NS08 | Deferred |
| NF136 | Custom cards | B | ? | NS08 | Deferred |
| NF137 | Contact/app cards | B | ? | NS08 | Deferred |
| NF138 | Bluetooth card conditions | B | ? | NS08 | Deferred |
| NF139 | Pinned cards | B | ? | NS08 | Deferred |
| NF140 | Usage-based suggestions | B | ? | NS08 | Later |
| NF141 | Smart categorised folders | B | ? | NS08 | Later |
| NF142 | Media controls | H | ? | NS06 | Later |
| NF143 | Weather/map cards | H | ? | NS06 | Deferred |
| NF144 | Searchable settings | H | ? | NS07 | Essential |
| NF145 | Per-app language | H | ? | NS06 | Essential |
| NF146 | Labs options | C | ? | NS02 | Investigate |
| NF147 | App-store context link | U | ? | NS22 | Essential |
| NF148 | APK export | U | ? | NS22 | Investigate |
| NF149 | TeslaUnread provider | L | Historical | NS10 | Excluded |

## Documented gaps and system boundaries

| Item | Nova evidence / platform evidence | Chikku treatment |
| --- | --- | --- |
| Manual drawer sorting | FAQ excludes it [NS02] | Add a persistent user order with alphabetic reset |
| Per-app badge style | FAQ excludes mixed styles [NS02] | Add overrides after reliable notification aggregation |
| Drawer/folder blur | FAQ excludes it [NS02] | Optional, capability-gated; solid fallback |
| Per-page wallpaper | FAQ excludes it [NS02] | Later experiment; distinguish app-rendered backgrounds from system wallpaper |
| Separate drawer wallpaper | FAQ excludes it [NS02] | Later optional custom drawer background |
| Android Private Space | Recent reports of missing support [NS16, NS17] | Native profile integration on supported devices; device verification required |
| Screen wake by double tap | OS/device-owned [NS02] | Do not promise |
| System navigation/Recents replacement | Privileged system integration [NS41] | Do not promise in the non-root app |
| Persistent all-app unread counts | Removed legacy integration [NS09, NS10] | Active-notification counts with honest labels |

## Installed-version audit to perform

For every claimed parity capability record: launcher versionCode/versionName, installation channel, Android version, device, Prime state, setting path, default, allowed values, permission requirement, incompatibility, and reproduction evidence.

Prioritise: icon swipe actions inside folders and dock; folder tap-versus-swipe; widget overlap hit targets; layout locking; horizontal drawer; home/dock looping; work/private profile separation; renamed-app search; icon-pack fallback; backup after upgrade.

Do not silently promote V, U, H, or B entries to verified-current status. Retain removed entries so their historical existence does not become an accidental implementation requirement.

