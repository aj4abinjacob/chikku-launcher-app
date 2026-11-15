# Nova settings inspected on the phone — 2026-10-11

Direct inspection: Samsung Galaxy S24 Ultra (SM-S928B), Android 16/API 36, Nova
`89400 (8.9.4)`, version code 89400, with Prime shown as activated. Chikku remained the default
HOME. This is a dated observation of the installed version, not a claim about every Nova release.
The earlier [article comparison](NOVA_ARTICLE_PARITY.md) and [research supplement](research/nova-2026-10-10/README.md)
are separate evidence. This document creates no main or NS/NF/NP/NU/CV identifiers.

The audit traversed the visible main sections, expanded label/advanced controls, icon style,
popup menus, feed settings, integrations and a blank card editor. Screenshots are local evidence in
ignored `build/verification/nova-phone-settings/`; they can contain personal information and are
not bundled in Chikku. No Nova source or assets were copied. During exploration a drawer label
checkbox was changed accidentally; its original Single line = on state was restored and captured.

**Existing** means the local code already exposed that capability before this pass. **Added** means
implemented in this pass, subject to the verification below. **Partial** states the narrower behavior.
**Open** means unimplemented and remains backlog; no nonfunctional switch was added. These statuses
are implementation scope, not physical-device acceptance for every row.

## Home screen and dock

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Desktop columns and rows | Existing independent grid controls |
| Desktop icon size | Existing scale control |
| Desktop labels on/off | Existing |
| Desktop label size | Existing; expanded alongside the new label controls |
| Desktop label font | Added normal, medium, condensed and light |
| Desktop label shadow | Added |
| Desktop single-line labels | Added; off permits two lines |
| Desktop label color | Added optional theme override |
| Horizontal desktop padding | Existing |
| Vertical desktop padding | Existing |
| Dock enable/disable | Existing |
| Dock icon count | Existing |
| Dock icon size | Existing |
| Dock match desktop size | Existing |
| Dock labels on/off | Existing |
| Dock label size/font/shadow/single line | Existing |
| Dock horizontal and vertical padding | Existing |
| Dock background enable/style/color/transparency | Existing |
| Dock background corners | Existing independent round/cut corners |
| Dock background image | Existing SAF reference with provider-loss fallback |
| Dock background inset | Existing |
| Dock pages | Existing up to storage bounds |
| Dock infinite scroll | Existing |
| Dock display as overlay | Existing |
| Dock close after launch | Existing |
| Dock remember position | Existing persisted page preference |
| Dock draw behind navigation bar | Existing within system inset rules |
| Search bar desktop placement | Existing top, below dock, or none; no every-row widget replacement |
| Wallpaper scrolling | Existing offsets on supported live/static wallpaper configurations |
| Widget corner radius | Existing provider-preserving zero or launcher clipping |
| Desktop transition effect | Added slide, fade, zoom and cube |
| Infinite desktop scroll | Existing |
| Page indicator | Existing dots, line or none |
| Add newly installed apps to desktop | Added opt-in personal-profile install callback, free-cell placement and recoverable edit; process must be alive |
| Lock desktop | Existing; also blocks automatic install placement |
| Screen edge shadow | Added optional foreground gradient |
| Widget overlap | Existing explicit opt-in |
| Allow overlap when placing | Partial: widget overlap policy; no unrestricted icon collision policy |
| Wallpaper zoom | Open: no hidden system animation API or universal zoom guarantee |
| Subgrid placement | Open: current persistent layout uses integer cells |

## App drawer

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Icon size | Existing |
| Match desktop icon size | Added |
| Labels on/off | Existing |
| Label size | Added independent drawer scale |
| Label font | Added |
| Label shadow | Added |
| Single-line labels | Added |
| Label color | Added |
| Drawer grid | Existing auto/explicit columns and paged rows |
| Drawer background color/transparency | Existing |
| Vertical drawer | Existing |
| Horizontal drawer | Existing |
| List drawer | Added horizontal app rows with one column |
| Open transition | Added slide, fade and scale; follows reduced-motion preference |
| Search bar placement | Existing top, bottom or hidden |
| Tab bar placement | Added top or bottom |
| Frequent apps | Partial: existing opt-in universal-search suggestions; no separate frequent drawer header |
| Frequent app shortcuts | Open: static/dynamic actions searchable, no learned shortcut ranking |
| Smart folders | Open: manual drawer groups available; no inferred categories |
| Help improve Nova | Outside scope: Chikku has no advertising or behavioral-upload control |
| Swipe/button drawer opening | Existing |
| Pull down to search | Added when at top of open drawer and enabled |
| Show keyboard on open | Existing |
| Fast scrollbar | Existing |
| Fast scrollbar color | Added |
| Hidden apps | Existing component/profile customization; cosmetic hiding is not security |
| Drawer groups | Existing named app groups, tabs and editing |
| Folders first | Open: groups are tabs, not folder cells interleaved with apps |
| Work-profile tab | Existing opt-in; platform availability applies |
| Horizontal infinite scroll | Added wrap pages |
| Close drawer after successful launch | Added configurable behavior; failures keep it open |
| Remember drawer position | Added session memory of scroll; query clears on close, selected tab is remembered independently; not restored after process death |

## Folders

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Folder icon size | Added independent scale |
| Match desktop size | Added |
| Folder labels on/off | Added |
| Folder label size | Added |
| Folder label font | Added |
| Folder label shadow | Added |
| Folder single-line labels | Added |
| Folder label color | Added |
| Folder background color/transparency | Existing |
| Window/immersive mode | Added floating and immersive panels |
| Window shadow | Added |
| Window corner radius | Added 0–48 dp |
| Show folder name | Added; rename remains accessible in edit flow |
| Folder grid | Added auto or 1–6 columns |
| Horizontal/vertical folder scrolling | Added horizontal pages in the [continuation](FOLDER_PAGING_CARDS.md); vertical remains default |
| Folder preview shape | Existing circle, rounded square or none |
| Folder preview arrangement | Existing grid, stack, fan and line |
| Folder preview count | Added 1–9 for grid; other styles retain their compositional limit |
| Folder swipe/tap actions | Existing per-folder actions |
| Close folder after launching | Added toggle |
| Alphabetical folder sorting | Added presentation-only sort; manual order remains stored |

## Search

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Window/fullscreen search | Added |
| Search background color | Added independent theme override |
| Search window corner radius | Added |
| Limit displayed app results | Added one-row option (five results), otherwise the existing normal app limit (12); other result types preserved |
| Folder search | Added opt-in local personal folders; hidden, unresolved or non-personal membership excludes the whole title |
| App shortcut search | Existing public LauncherApps static/dynamic actions |
| Contact search | Existing opt-in READ_CONTACTS request and disable flow |
| Contact card | Partial: existing local contact results/actions, no Nova-branded card |
| API integrations | Open: no token/account collection or pretend connectors |
| Web provider | Existing six explicit browser providers |
| Maps/YouTube/Nova AI provider actions | Partial: explicit maps/link micro-results; no AI service or separate YouTube provider |
| Recent search shortcuts | Open: search queries are not persistently learned |
| Private keyboard | Added standard no-personalized-learning request to search and drawer input; keyboards decide whether to honor it |
| Home search bar appearance | Added background, radius and shadow; existing provider mark retained |
| Drawer search bar appearance | Partial: follows drawer styling; no independent bar-style model |
| Nova Now search bar | Open: no Nova Now clone |
| Settings search | Added offline section navigation by indexed setting names; results open the containing section |

## Gestures and appearance

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Home button action | Existing |
| Home action only on main page | Added; other pages return to configured default page |
| Swipe up/down | Existing |
| Swipe left/right | Added optional empty-desktop gestures; unmapped swipes retain paging |
| Double tap | Existing |
| Double tap then swipe up/down | Added |
| Two-finger swipe up/down | Existing |
| Pinch in/out | Existing |
| Rotate clockwise/counterclockwise | Added two-finger gesture actions |
| Desktop previews action | Open; current page management is available through edit controls |
| Recents and lock actions | Existing platform/accessibility paths; no universal system-gesture animation promise |
| Icon pack | Existing public pack support |
| Auto-generated theme pack | Open: no Nova icon-generation service |
| Default/round/squircle/rounded square shapes | Existing |
| Flower/custom icon shapes | Open |
| Reshape legacy icons | Existing |
| Adaptive icon wiggle/shadow/old 7.1 icon policy | Open |
| Screen rotation | Existing orientation preference |
| App opening animation | Partial: launcher animation speed and drawer/page transitions; system owns cross-app animation |
| Drop-target appearance | Open: current edit/drop affordances remain |
| Undo bar | Existing recoverable workspace edits |
| Disable animations | Existing animation speed None; new transitions honor it |
| Status bar shown/hidden/transparent | Existing |
| Navigation bar transparency/dark icon policy | Partial: existing system/theme contrast policy; no independent Nova-equivalent controls |
| Popup menu background/text colors | Added |
| Popup menu radius | Added |
| Popup shortcut cards/grouping | Partial: existing shortcut rows and two menu modes; no separate grouping/background switch |
| Home/drawer uninstall visibility | Added independent switches; capability checks still apply |
| Home/drawer store shortcut visibility | Added explicit public Play listing handoff with browser fallback |
| Home/drawer hide visibility | Added independent switches |
| Close app/relaunch/save APK menu actions | Open; no force-stop or APK export emulation |

## Badges, backup, feeds and cards

| Observed Nova setting or feature | Chikku status and behavior |
|---|---|
| Badge enable/style | Existing off, dot, count and dynamic; notification access requested only when enabled |
| Badge corner | Added four corners, mirrored appropriately in RTL |
| Badge size | Added small, medium and large presets |
| Badge color | Existing |
| Badge preview/test | Added synthetic in-settings samples, no notification posted or access required |
| Notifications in popup | Partial: badge state only; no notification-content popup |
| Save/share/restore backup | Existing versioned Chikku archive and restore preview; no Nova backup import |
| Quick start | Existing onboarding; no silently applied Nova preset |
| Reset | Existing recovery tools; this pass never resets user data |
| Nova Now extra first page | Open |
| Nova Now frequent apps/shortcuts, colors, keyboard, rows | Open; local frequent-app search is narrower |
| Google feed | Existing experimental Discover overlay/app fallback |
| Feed transition/theme/companion | Open as separate controls; no proprietary companion bundled |
| Calendar/GitHub/Spotify/Deezer/Twitch/Slack/Discord/Dropbox/OneDrive/Sesame | Open account integrations; public installed-app shortcuts may already exist |
| Cards: media/weather | Added opt-in city weather and visible personal media playback; see [card content](CARD_CONTENT_EXTENSIONS.md) |
| Cards: named app/shortcut/widget plus six actions | Added local editor/panel and six action slots; Home-widget reference plus separate embedded widget content in [extension](CARD_CONTENT_EXTENSIONS.md) |
| Card conditions: day/time/Wi-Fi/weather/Bluetooth | Added day/time/default-Wi-Fi, charging and Bluetooth audio conditions; weather predicates remain open. Permission boundaries in [card extension](CARD_CONTENT_EXTENSIONS.md) |

## Data and platform boundaries

All added preferences use the existing versioned settings codec and backup payload. Missing fields
use the documented defaults; existing placement and identity stay intact. Unknown enums fall back, numeric geometry is bounded. There is
no Room schema migration for these preferences. The original phone pass used independently written Kotlin with no new dependency, permission,
exported component, advertising SDK or required account. The subsequent [cards continuation](FOLDER_PAGING_CARDS.md)
adds one normal `ACCESS_NETWORK_STATE` permission for local Wi-Fi conditions.

Automatic install placement listens only while enabled and unlocked, filters the current personal
user, checks recovery and hidden state, and uses an atomic duplicate check including folder entries.
Enabling it does not scan existing apps into the workspace. Callback registration and inventory
queries run off the main thread. It is not a persistent background service and cannot promise to
catch installs while the process is stopped.

Folder search omits any title whose contents are hidden, unresolved or not personal. HOME validates
again before opening a search result. No labels, app inventory, profile contents or queries are
logged. Settings search indexes static resource names only and keeps queries in memory.

The private-keyboard request uses Android's public
[IME_FLAG_NO_PERSONALIZED_LEARNING](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_PERSONALIZED_LEARNING).
Install observation uses public
[LauncherApps.Callback](https://developer.android.com/reference/android/content/pm/LauncherApps.Callback).
Store links follow Android's public
[linking guidance](https://developer.android.com/distribute/marketing-tools/linking-to-google-play).
Platform-owned wallpaper zoom and stock Recents transitions are not implemented using hidden APIs.

## Verification

Final Android Studio JBR/Gradle checks passed: `assembleDebug :core:model:test testDebugUnitTest
lintDebug assembleDebugAndroidTest`, **107 core + 466 app unit tests**, zero failures/errors/skips.
Lint completed with warnings; the privacy gate passed against 113 runtime modules, four forbidden
permissions and 49 SDK rules. No new permission, dependency or exported component was introduced.
The installed APK hash matches the verified build. Source remains working changes on
`feat/nova-phone-settings`, preserving the earlier article work; no publication or merge occurred.

On the named Samsung/Android 16 device, **3/3 NovaPhoneSettingsDeviceTest tests passed** in 29.913 s:
real settings search, section navigation, window/one-row preference persistence and recreation;
synthetic app result limits in both search modes; real list drawer, bottom tab placement and live
label updates; a synthetic immersive folder panel with real view geometry and accessible close.
The sample search and folder contents were generated fixtures, not the user's actual folder data.
**3/3 HomeActivityDeviceTest + 1/1 StrictModeStartupTest passed** in 14.84 s, checking drawer
search/launch/recreation, empty results/back behavior, startup and the narrow data/platform disk-I/O
rule. The older HOME tests now explicitly enable the button/grid/search configuration they need
and restore the original drawer settings afterward. Their first attempt failed on the user's
swipe-only preference. The new settings test's initial selector failure was corrected to select
“Open [section]” rather than its own query field; its final three-test run passed.

All touched search/drawer preferences were restored. A private before/after comparison confirms
identical workspace database rows and every original settings field unchanged; 63 new default
preference fields were persisted. Default HOME remains Chikku. Settings root, search navigation,
folder controls and synthetic search-mode screenshots were inspected visually. Build/privacy/device
logs, screenshots, verified APK and aggregate checks are local ignored evidence under
`build/verification/nova-phone-settings/`. Nova screenshots establish observed controls only.

No emulator, performance benchmark, reboot/process-death acceptance, physical multi-pointer gesture
feel, TalkBack, large-font or physical RTL pass was run. Real new-package auto-placement, third-party
shortcut capability changes, private/work-profile locking, actual widget-provider loss, full
on-device archive restore and external keyboard learning compliance remain unverified. Injected
unit tests cover install-generation/lock/hidden/recovery races and folder visibility; they do not
replace those physical checks. Horizontal folder paging and the local card subset were subsequently implemented in the
[continuation](FOLDER_PAGING_CARDS.md); service integrations and remaining Open/Partial rows remain backlog. The provisional architecture/release gates are unchanged.
