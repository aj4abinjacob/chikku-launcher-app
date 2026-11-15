# Android APIs, permissions and platform feasibility

**Baseline as of 2026-10-10:** proposed minSdk 29 (Android 10), compileSdk 37 (Android 17), targetSdk 36 (Android 16) for first Play-eligible build; revisit target 37 after widget/memory validation. New Play submissions and updates must target API 36+ since 2026-08-31 [S25]. Android 17 shipped in June 2026 [S30]. Source IDs: [RESEARCH_SOURCES.md](RESEARCH_SOURCES.md).

## Public API map

| Feature | Public API / library | Caveats |
|---|---|---|
| Default launcher | MAIN + HOME + DEFAULT intent filter, RoleManager.ROLE_HOME | Role is user-granted; must remain revertible |
| Enumerate and launch apps | LauncherApps.getActivityList, startMainActivity | Use UserHandle, profile-aware identity; not PackageManager-only |
| App changes | LauncherApps.Callback | Coalesce updates; handle locked/quiet profiles |
| Work profiles | LauncherApps.getProfiles, UserManager | Some enterprise policies limit visibility |
| Android 15+ Private Space | ROLE_HOME, ACCESS_HIDDEN_PROFILES, LauncherApps, UserManager | Separate container, lock/unlock, no locked content in search [S22] |
| Pinned/deep shortcuts | LauncherApps.getShortcuts, pinShortcuts, startShortcut; ShortcutManager | Host permission, shortcut availability and profile state [S33] |
| Widgets | AppWidgetHost, AppWidgetManager, AppWidgetHostView | System binding consent, config flow, ID lifecycle [S21] |
| Widget providers | AppWidgetProviderInfo, AppWidgetManager | Third-party RemoteViews may fail; don't parse internals |
| Wallpaper | WallpaperManager and system wallpaper intents | Some OEM restrictions and live wallpaper paths |
| Adaptive/themed icons | AdaptiveIconDrawable, LauncherActivityInfo icons, resources | Theme and icon-pack protocol compatibility varies |
| Drag and gestures | MotionEvent, GestureDetector, VelocityTracker, OverScroller, ViewGroup | Must respect system gesture insets and accessibility |
| Insets and Back | WindowInsetsCompat, OnBackInvokedDispatcher/AndroidX back APIs | Predictive back and gesture exclusions have constraints [S34] |
| Large screen/foldables | Jetpack WindowManager, WindowMetrics, FoldingFeature | Screen posture changes without stable display IDs |
| Persistent workspace | Room, SQLite transactions | Data migrations and snapshots required |
| Settings | Proto/Preferences DataStore | Typed migrations, no blocking reads |
| Backup | Storage Access Framework: ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT | No broad storage permission; validate external ZIP |
| Notification badges | NotificationListenerService | User must explicitly enable sensitive access; avoid content retention |
| Search contacts | ContactsContract + READ_CONTACTS | Optional, explicit consent; never required for core search |
| Performance | Macrobenchmark, JankStats, Baseline Profiles, StrictMode | Benchmark on real devices and release-like builds |
| App info/uninstall | LauncherApps.startAppDetailsActivity / documented intents | Availability varies by profile/policy |

## HOME activity skeleton (illustrative, not committed code)

    <activity
        android:name=".HomeActivity"
        android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.DEFAULT" />
            <category android:name="android.intent.category.HOME" />
        </intent-filter>
    </activity>

Request HOME role through RoleManager on supported devices; let Android handle default app selection. Do not programmatically override the user's chosen HOME app.

## Widget binding state machine

1. User chooses widget provider from AppWidgetManager.
2. Allocate ID from a persistent AppWidgetHost host ID.
3. Call bindAppWidgetIdIfAllowed. If false, launch system ACTION_APPWIDGET_BIND consent flow.
4. Launch provider configuration activity if required.
5. Persist provider, placement and widget ID transactionally.
6. Create AppWidgetHostView and start listening at appropriate lifecycle point.
7. On resize, update widget options and layout.
8. On delete/cancel, release ID; on backup/restore, allocate **new** IDs and rebind.

Do not assume widget IDs, provider packages or profile handles transfer between devices. Test with calendar, clock, media and third-party widgets [S21].

## Android 15–17 specifics

**Android 15 (API 35):** To show Private Space apps, launcher must be default HOME and declare ACCESS_HIDDEN_PROFILES. Provide separate Private Space container with hide/show and lock/unlock controls; locked apps must not appear in search [S22]. If this cannot be implemented safely, disclose incompatibility and block release rather than leaking data.

**Android 16 (API 36):** Target at least API 36 for Play submissions as of August 2026 [S25]. Validate edge-to-edge, gesture insets and profile behavior on OEM builds.

**Android 17 (API 37):** Test memory limits and process behavior on Android 17 even when targetSdk=36 [S24]. Before raising targetSdk to 37, test the new RemoteViews bitmap/icon memory budget; exceeding it can cause a fatal exception [S23]. Android 17 also emphasizes large-screen resizing and orientation; design layout as adaptive [S30].

## Permission strategy

| Permission/capability | Default | Reason / guardrail |
|---|---|---|
| INTERNET | **Not in core** | Offline launcher; separate optional network feature flavor only after review |
| ACCESS_HIDDEN_PROFILES | API-gated where supported | Required for Private Space with HOME role; ensure UI privacy |
| BIND_APPWIDGET | Manifest declaration + system consent | Host widget binding; never bypass dialog [S21] |
| Notification Listener access | **Off by default** | Only if user enables badges; service declaration + system settings |
| READ_CONTACTS | **Not in MVP** | Only if user opts into contact search |
| AccessibilityService | **Not in MVP** | Optional actions only, prominent disclosure, user-granted; policy review |
| Device Administrator | **Not in MVP** | Optional lock-screen action only if justified |
| QUERY_ALL_PACKAGES | **Avoid** | Use LauncherApps; broad visibility has Play policy restrictions |
| Usage access | **Avoid** | Not needed for deterministic local app search |
| Broad file/media access | **Avoid** | Use Storage Access Framework |

A launcher can see sensitive usage context. Do not persist unnecessary app usage histories or transmit package inventories.

## Privileged / OEM-only capabilities

| Desired behavior | Stock unrooted third-party launcher? | Approach |
|---|---|---|
| Fully integrated system Recents/Quickstep animation | **Not reliably available via public API** | Document limitation; no hidden/system API dependency |
| OEM swipe-home animation parity | Varies by Android/OEM | Compatibility tests and graceful fallback |
| System status bar/Quick Settings actions | Limited/varies | Use documented actions, optionally user-granted accessibility for a specific action |
| Google Discover feed embedded as stock Pixel page | No guaranteed public API | Experimental, opt-in AIDLBridge overlay implemented locally; Google/bridge required, live compatibility unverified. See [ADR-0004](decisions/0004-optional-google-discover.md) |
| Lock screen on double tap | Some approaches need special user grant | Offer supported action with clear permission flow |
| Access apps in locked Private Space | **No** | Never bypass lock; no search results |
| Modify another app's widget internals | **No** | Host RemoteViews through framework only |

Historical Nova gesture-navigation FAQ [S18] explains the origin of some restrictions; revalidate on Android 17 rather than extrapolating Android 10 behavior unchanged.

## API implementation checklist

- [ ] HOME role request and revoke flow
- [ ] AppKey includes UserHandle identity, not just package name
- [ ] Register/unregister LauncherApps.Callback safely
- [ ] Shortcut host permission handling and invalid-shortcut fallback
- [ ] Widget consent/configuration/ID release on every failure path
- [ ] Private Space isolation on API 35+ and work-profile quiet mode
- [ ] Insets-aware gestures, predictive back and accessibility
- [ ] Android 17 RemoteViews/memory stress tests
- [ ] Manifest/dependency permission audit in CI
- [ ] Play target policy and latest platform docs reviewed before release
