# Chikku Launcher

Chikku Launcher is an independent, offline-first Android home-screen replacement with no ads,
tracking SDKs or compulsory account. Licensed GPL-3.0-or-later.

**Get it:** APKs are attached to [GitHub Releases](https://github.com/aj4abinjacob/chikku-launcher-app/releases).
An [F-Droid listing](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57488) has been requested. Beta builds also go to Google Play closed testing.
This is beta software: read the [changelog](CHANGELOG.md) for known issues before making it your
daily launcher. Report bugs in [Issues](https://github.com/aj4abinjacob/chikku-launcher-app/issues).

This repository is the public source snapshot of the release line. Historical release records in
`docs/release/` refer to the private repositories it was prepared from.

**Status:** M0 in progress (12 October 2026). A working launcher foundation exists with HOME, offline search, a persistent workspace/dock, customization and recovery. Narrow physical checks are recorded on Xiaomi Redmi 9 (Android 11/MIUI) and Samsung Galaxy S24 Ultra (Android 16), including default-HOME navigation on the S24 Ultra. Interactive system-role selection/reversion, real app-version upgrade/reboot and broader release acceptance remain open. See [implementation status](docs/IMPLEMENTATION_STATUS.md) for exact workflows, dates and verification limits.

Chikku Launcher is an in-development **Kotlin-first Android home-screen replacement**, intended to become open source, with an editable workspace, offline search and extensive customization. It is an independent project with its own branding and implementation.

## Mission

Build a fast, customizable, accessible launcher that users can trust to keep working across Android releases. Priorities: reliability > user control > feature depth > visual novelty.

**Product promises (proposed, not implemented):**
- No ads, tracking SDKs, or network dependency in the launcher core.
- Offline-first search, local-first settings, transparent permissions.
- Portable, versioned backups with recovery and migration tests.
- A public roadmap, regression tests, documented releases, and community contribution process.
- Clear distinction between platform restrictions and bugs Chikku can fix.

> **Licensing:** Project-owned source code is licensed under the [GNU General Public License v3.0 or later](LICENSE) (`GPL-3.0-or-later`). The repository remains **private**; third-party dependency and asset license checks are still required before public distribution. See [licensing and legal notes](docs/LICENSING_AND_LEGAL.md).

## Read this before writing code

1. [Research summary and current Nova status](docs/RESEARCH_SUMMARY.md)
2. [Nova feature inventory (verified vs unverified)](docs/NOVA_FEATURE_MATRIX.md)
3. [Reported user pain points and opportunities](docs/USER_PAIN_POINTS.md), [unmet requests](docs/UNMET_REQUESTS.md), and [competitor analysis](docs/COMPETITOR_ANALYSIS.md)
4. [Product requirements and acceptance criteria](docs/PRODUCT_REQUIREMENTS.md)
5. [Kotlin-first architecture](docs/ARCHITECTURE.md) and [Android API feasibility](docs/ANDROID_APIS.md)
6. [MVP roadmap and backlog](docs/ROADMAP.md)
7. [macOS setup, build commands and first-device checks](docs/DEVELOPMENT_SETUP.md)
8. [Testing, privacy, release process](docs/TEST_STRATEGY.md), [privacy](docs/PRIVACY_SECURITY.md), [maintenance](docs/MAINTENANCE_POLICY.md)
9. [Sources and evidence grading](docs/RESEARCH_SOURCES.md)

## Additional research and implementation handoff

- [Dated Nova research supplement](docs/research/nova-2026-10-10/README.md): 149 evidence-labelled capability entries, 48 sources, current limitations and additional workflow checks.
- [Development handoff](docs/DEVELOPMENT_HANDOFF.md): concrete instructions for the first feasibility spike.
- [Repository instructions](AGENTS.md): identity, privacy, recovery and verification invariants.

The supplement adds evidence without replacing the main provisional architecture or milestone definitions.

## Technical baseline

- Kotlin; Gradle Kotlin DSL with a version catalog; AndroidX; Coroutines/Flow.
- **Hybrid UI:** custom Android Views/ViewGroups for the latency-sensitive home screen, dock, drag-and-drop, folders and widget hosting; Jetpack Compose for settings/onboarding and suitable overlays.
- Room for transactional workspace state; modular, testable interfaces.
- **minSdk 29**, **compileSdk 37**, **targetSdk 36** (2026 Play requirement), with Android 17/API 37 validation before targeting 37.
- Use public launcher APIs; do **not** promise privileged Recents/Quickstep integration on stock unrooted devices.

These values are configured in the bootstrapped build. The overall architecture is still provisional until the feasibility spike confirms it. See [ADRs](docs/decisions/0001-kotlin-first-hybrid-ui.md) and [build-vs-fork](docs/decisions/0002-build-vs-fork.md).

## Build and run

Prerequisites:

- Android Studio (current stable) with **Android SDK Platform 37** (`platforms;android-37.0`) and platform-tools installed.
- JDK 21 or newer (Robolectric needs Java 21 to run the SDK 36 unit tests). Android Studio's bundled
  JBR works:

      export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

- A `local.properties` file in the repository root (gitignored) pointing at your SDK, for example:

      sdk.dir=/Users/<you>/Library/Android/sdk

Commands:

    ./gradlew assembleDebug              # debug APK in app/build/outputs/apk/debug/
    ./gradlew :core:model:test           # pure Kotlin unit tests
    ./gradlew testDebugUnitTest          # app unit tests (Robolectric, Room)
    ./gradlew lintDebug                  # Android lint
    scripts/check-privacy.sh             # forbidden permissions and ads/analytics SDK gate
    adb install -r app/build/outputs/apk/debug/app-debug.apk

The debug build installs as `io.github.aj4abinjacob.chikku.debug`, so it can sit beside a future release build.

**Selecting Chikku as the home app:** open Android **Settings → Apps → Default apps → Home app** and choose Chikku (wording varies by OEM), or tap "Set as default" in Chikku's banner or on the "Chikku settings" screen, which opens the system role dialog. To switch back, use "Choose a different home app" on the Chikku settings screen, or the same system setting. Pressing Home afterwards may also show a chooser.

**Switching back to your stock launcher:** go to the same **Settings → Apps → Default apps → Home app** screen and pick your previous launcher (for example Pixel Launcher or One UI Home). If Chikku is unusable, open Settings from the notification shade's gear icon to reach that screen, or run `adb uninstall io.github.aj4abinjacob.chikku.debug`; Android then falls back to the remaining launcher. Keep the stock launcher installed while testing.

Continuous integration runs the same build, tests, lint and privacy gate on every pull request ([workflow](.github/workflows/android.yml)). Detailed setup lives in [DEVELOPMENT_SETUP.md](docs/DEVELOPMENT_SETUP.md).

**Optional Google app handoff:** the first Play release opens the installed Google app after
an enabled outward swipe from the first home page. It does not embed Discover or require a bridge.
Debug builds retain the experimental overlay described in [ADR-0004](docs/decisions/0004-optional-google-discover.md).
Google controls availability and feed content. [Signing/distribution decisions](docs/release/DISTRIBUTION.md)
explain why the upload key is distinct from Play's app signing key and how later GitHub updates work.

**Dock customization:** **Chikku settings → Dock** has a pinned preview, desktop-matched icon
sizing, label styling, independent padding, background/corner controls, pages, infinite scrolling,
and overlay/automatic-close controls. [Dock settings](docs/DOCK_SETTINGS.md) describes behavior
and compatibility; device evidence is in [implementation status](docs/IMPLEMENTATION_STATUS.md).

**Live settings previews:** home, drawer, folder, dock/search, appearance and night-mode controls
show their combined result while editing. Swipe or use Replay/Try controls to sample motion and
gestures safely. Badge/card previews stay visible alongside editing controls.
[Preview coverage and limits](docs/SETTINGS_LIVE_PREVIEWS.md) distinguish synthetic samples from
device acceptance.

**Search below the dock:** open **Chikku settings → Dock → Search bar below dock**. Choose Google,
DuckDuckGo or another web engine there. Enable **Online search suggestions** for Google/DuckDuckGo
autocomplete; this is off by default and sends typed text to the selected provider. Apps, contacts
and shortcuts remain searchable offline. See [search integration decision](docs/decisions/0005-optional-web-search-suggestions.md).

**Contact card:** with contact search on (Search → Contacts), the best match shows Call, Message
and WhatsApp buttons. Swap, add (up to four) or reorder them in Search → Contacts → Contact card
buttons, e.g. Signal instead of Call. See [contact services](docs/CONTACT_SERVICES.md).

## First milestone

**M0: a launchable, selectable HOME app** with an app list, app launching, deterministic layout persistence, safe default-home switching, and crash recovery. Complete the foundation before expanding the customization feature set.

M0 is **in progress**: per-requirement status, known platform limitations and the device verification log are in [IMPLEMENTATION_STATUS.md](docs/IMPLEMENTATION_STATUS.md); changes are recorded in the [changelog](CHANGELOG.md).

## Project navigation

- [Research sources](docs/RESEARCH_SOURCES.md) · [Open questions](docs/OPEN_QUESTIONS.md) · [Contributing](CONTRIBUTING.md) · [Security](SECURITY.md)
- [Implementation status](docs/IMPLEMENTATION_STATUS.md) · [Changelog](CHANGELOG.md) · [Release checklist](docs/RELEASE_CHECKLIST.md)
- [Bug report template](.github/ISSUE_TEMPLATE/bug_report.md) · [Feature request template](.github/ISSUE_TEMPLATE/feature_request.md)

## Naming / intellectual property

The app uses the original "Nose notch / Ink" icon selected by the owner on 2026-10-15.
[Its branding notes](design/branding/nose-notch-ink/README.md) record the adaptive, themed and
Google Play layers. Earlier concepts in [design/branding](design/branding/README.md) are not installed.

Chikku Launcher is an independent project. Competitor references in the research documentation do not imply affiliation or endorsement. Verify third-party code and icon-pack licenses before reuse.


**Search and customization additions:** Search now offers offline unit conversions and explicit URL/map/phone/
tracking/explorer actions, plus opt-in on-device search history suggestions. Home screen settings
expose widget overlap and corner radius. Dock settings offer a user-selected background image.
Settings use compact accent headers and preference lists. See the
[article-to-control audit](docs/NOVA_ARTICLE_PARITY.md) for existing features, additions and integration limits.

**Phone settings expansion (2026-10-11):** additional controls use Chikku's own appearance
and implementation. The reference version and device are recorded in the linked inventory.
The [phone settings inventory](docs/NOVA_PHONE_SETTINGS.md) lists observed settings, implemented
additions, narrower equivalents and remaining work.

- **Home screen:** independent label font/color/shadow/line controls, page effects, optional edge
  shadow and opt-in placement of newly installed personal apps while Chikku is running.
- **App drawer:** true list rows, desktop-matched icon size, independent labels, tab placement,
  fast-scroller color, paged wrapping, pull-to-search, opening effects and launch/position controls.
- **Folders:** independent content icon/label styling, floating or immersive panels, columns,
  preview count, name/shadow/corners, alphabetical display order and close-after-launch control.
- **Search and gestures:** settings search, optional local folder search, window/fullscreen search,
  app-result limits, keyboard-learning request, home search-bar styling, horizontal actions,
  double-tap-plus-swipe, rotations and Home-button scope.
- **Appearance:** badge position/size with synthetic previews, and popup colors/corners plus
  separate home/drawer Hide, Uninstall and Play Store choices. App info and Edit stay available.

Preferences travel in Chikku backups; drawer position memory lasts only for the current session.
Existing layouts are preserved. These additions use Chikku's own code and presentation; no new
permissions, dependencies or account integrations were added. Build and named device evidence are tracked in the inventory and
[implementation status](docs/IMPLEMENTATION_STATUS.md); these additions do not complete the
provisional architecture review or the release gates.

Horizontal folder paging and local contextual cards are covered in [the continuation notes](docs/FOLDER_PAGING_CARDS.md).

**Search and folder fixes:** Search settings now include bar width, height, independent padding and an adjustable gap above the below-dock bar; HOME search supports dragging app results. Long-press folders for a refreshed editor with first/last/chosen-app icon covers and explicit Open folder gestures. See [behavior and verification boundaries](docs/SEARCH_FOLDER_CUSTOMIZATION.md).

**Drawer row spacing:** in **Chikku settings → App drawer**, turn off **Automatic row spacing**
and adjust **Number of rows**. More rows reduce vertical spacing in the vertical grid or list;
large icons/text keep their required height. Horizontal pages retain **Rows per page**.

Appearance controls now identify grid edges, widget inner space, dock app padding and painted background insets, with concrete dp amounts. Folder icon and window colors are independent. See [settings clarity audit](docs/SETTINGS_CLARITY_AUDIT.md).

**Reliability fixes (2026-10-11):** Restore/reset/undo now use a private recovery journal; media
failures are contained; offline search stays responsive to new queries independently of optional
providers; shortcut caches and launch actions recheck profile availability. See
[verification and remaining phone acceptance](docs/IMPLEMENTATION_STATUS.md#priority-reliability-fixes--2026-10-11).

**Further reliability fixes (2026-10-11):** Grid changes warn before hiding placements; recovery moves fitting items together with one Undo. Widget rebinding keeps IDs needed by Undo, and stale notification reads cannot republish cleared badges. Notification-access settings show disconnected service status and provide Reconnect. Privacy copy describes optional search/weather requests accurately. See [current checks and limits](docs/IMPLEMENTATION_STATUS.md#remaining-reliability-work--2026-10-11).

## Personal home appearance reference

The requested home layout and drawer appearance are applied on the S24 Ultra, with an offline
clock, matching installed-pack artwork and a working x.com shortcut. Original placements remain
recoverable. See [implementation, evidence and remaining visual differences](docs/HOME_REFERENCE_MATCH.md).

**Reliability continuation (2026-10-12):** ordinary Undo rejects damaged snapshots before replacing
the current layout, failed recovery-copy writes cannot authorize erasing damaged settings, and
corrupt or overflowing startup flags enter safe mode. Native migration and process-death fixtures
use separate synthetic storage. See [actual verification and remaining gates](docs/IMPLEMENTATION_STATUS.md#reliability-recovery-continuation--2026-10-12).

**Production reliability verification (2026-10-12):** the latest batch adds overflow-safe layout validation and adversarial layout/search/restore regression coverage. Disposable API 36 recovery checks and isolated physical preservation checks are recorded with their limits in the [QA results](docs/QA_RESULTS.md) and [regression strategy](docs/QA_REGRESSION_STRATEGY.md#executed-evidence--2026-10-12). Native acceptance remains open; the [risk register](docs/QA_RISK_REGISTER.md) records an unresolved Cards accessibility issue.

**Horizontal scrolling (2026-10-13):** Home screen → Pages → Horizontal scrolling and
App drawer → Horizontal scrolling (Horizontal pages style) offer independent sensitivity, settling
time, effects/strength, edge feedback, looping and indicator controls with a synthetic live sample.
Indicators support tap and slide-to-scrub. Home loops using the existing page/widget views.
[Implemented behavior and verification limits](docs/IMPLEMENTATION_STATUS.md#horizontal-scrolling-controls--2026-10-13).

**Vertical scrolling (2026-10-13):** App drawer → Vertical scrolling (Vertical list and List styles)
sets scroll speed and the end-of-list effect, and gathers fast scroller, remember-position and
pull-to-search, with a live sample on the drawer's real list view.
