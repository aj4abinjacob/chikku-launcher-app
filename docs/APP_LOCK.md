# App lock

Status: implemented on branch `feat/app-lock` (2026-10-11). Local unit tests, lint and the privacy
gate pass; it has **not** been exercised on an emulator or physical device yet.

## What it does

Settings → System → **App lock** lists installed apps. A locked app asks the device owner to confirm
with the system screen lock before Chikku opens it. "Lock again" picks how long an unlock lasts:
every time (default), after 1 minute, after 5 minutes, or until the screen turns off. Every unlock
also ends when the screen turns off, when the lock settings change, or when the Chikku process dies.

## Threat model

App lock targets someone who picks up an unlocked phone and uses the launcher, for example to open a
chat or banking app. It is a launcher-level lock, not app sandboxing. Hiding apps is still only
cosmetic (see [PRIVACY_SECURITY.md](PRIVACY_SECURITY.md)).

| Easy bypass | Countermeasure |
|---|---|
| Guess or reset an app PIN | Chikku has no PIN. Android's `BiometricPrompt` checks Class 3 (strong) biometrics or the device PIN, pattern or password, and the system limits attempts. Android 11+ rejects weak (Class 2) face unlock. Android 10 uses the platform's `setDeviceCredentialAllowed` policy. |
| Turn the lock off in settings | While any app is locked, the App lock page needs the same confirmation. The page locks again when settings leaves the screen. The first lock also needs confirmation, which proves that authentication works before an app is locked. |
| Open the app another way inside Chikku | The check is in the platform launch classes (`LauncherAppsLauncher`, `LauncherAppsShortcuts`, `ShortcutStarter`, `AppMenuPlatform`, `GuardedIntentLauncher`), not in each screen. Covered: home, dock, folders, drawer, search (including contact actions and deep links), IME "go", gestures, cards, pinned and long-press shortcuts, the hidden-apps screen and **App info**, because the system App info screen has an Open button. A launch path without unlock handling fails closed. |
| Read a locked app's shortcuts (chat names, contacts) | Long-press and search shortcuts of locked apps are hidden. |
| Restore an old backup or reset settings | Locks are stored in a separate SharedPreferences file (`chikku_app_lock`) that Chikku backup, restore and reset never touch. Platform backup is disabled for the app. |
| Rename the app, update it, or use a second launcher activity | Locks key on package + profile, so every launcher activity and shortcut of that package is covered. Updates, reinstalls and custom labels don't change this. A work-profile copy is a separate app. |
| Change the clock to extend an unlock | Unlock grants use `SystemClock.elapsedRealtime()` and live only in memory. |
| Remove the screen lock | Locked apps stay locked. Chikku fails closed and points to the screen lock settings. |
| Launch before the lock list has loaded, or after a storage error | Unknown means locked. An unreadable store locks every app and is never overwritten. |
| Tap repeatedly to stack prompts | One prompt at a time per activity. |

## Platform limits (not app defects)

A normal, non-root launcher can only control what it starts. A locked app can still be reached from
its notifications, Recents, its home-screen widgets, the Assistant, another launcher, or Android
Settings → Apps. Clearing Chikku's storage or switching the default launcher removes Chikku's locks.
For apps that must stay sealed, use Android's Private Space (Android 15+), a work profile, or the
device maker's app lock. The settings page states these limits under "Learn more".

## Permissions

`android.permission.USE_BIOMETRIC` is a normal, install-time permission required by the framework
`BiometricPrompt`. Chikku never receives biometric data, only whether the owner was confirmed. No
dependency was added: the framework API covers minSdk 29.

## Code

- `applock/AppLockManager.kt`: lock list, relock policy, in-memory grants, search shortcut filter.
- `applock/AppUnlockPrompt.kt`: system authentication (`AppUnlocker`), screen-lock-missing flow.
- `data/SharedPreferencesAppLockStore.kt`: persistence, kept apart from backups.
- `platform/PlatformContracts.kt`: `AppLockGuard`, `LaunchResult.Locked`.
- `settings/sections/AppLockSettingsSection.kt`: settings page and owner confirmation gate.

## Validation

| Check | Result |
|---|---|
| `AppLockManagerTest` (JVM): one-time and timed grants, screen-off relock, settings-change relock, fail-closed load and read errors, failed writes, search shortcut filter | Pass |
| `SharedPreferencesAppLockStoreTest` (Robolectric): round trip, malformed entries, separation from settings | Pass |
| `PlatformAdaptersTest`: launcher, App info and shortcut starter return `Locked` before starting anything | Pass |
| `GestureActionExecutorTest`, `HomeViewModelTest`: locked results reach the UI, and retry relaunches the same target | Pass |
| System prompt, PIN fallback (including Android 10's separate credential activity), lockout and screen-off relock on a device | **Not run** |
| TalkBack, large font and RTL on the settings page | **Not run** |

## Search handoff bypass correction (2026-10-12)

The original coverage claim above missed ordinary search intents: contact search → WhatsApp
started a contacts-provider deep link directly, without checking app lock. `SearchHandoff` now
only builds intents. The shared HOME/standalone `SearchResultActions` uses `GuardedIntentLauncher`
to resolve handlers off the UI thread, pin a concrete component, and check its package plus the
current user serial immediately before starting it. Successful authentication retries that exact
action, URI and component. Cancellation opens nothing, and a locked handler never triggers the
next fallback. This also covers contact open/call/message, web/store results and micro-actions.

When a choice is needed, Chikku shows its own app-selection sheet and checks the selected app.
It never starts the system resolver/chooser as if that were the receiving app. These search
handoffs support current-profile apps only; system resolver/profile-forwarding entries are excluded.
Missing or unknown handlers/profiles fail closed. Other apps can still forward a link after Chikku
opens them; this correction does not make launcher app lock system-wide.

Narrow manifest `queries` entries make contact/WhatsApp, map, store and HTTP handlers visible
for resolution. These are package visibility declarations, not permissions. Android documents
that starting an activity does not require package visibility whereas inspecting its handlers
does: [package visibility use cases](https://developer.android.com/training/package-visibility/use-cases).
No dependency, exported component, stored field, backup change or third-party code was added.

Validation results are recorded in
[implementation status](IMPLEMENTATION_STATUS.md#search-app-lock-bypass--2026-10-12).
Real WhatsApp and system-authentication checks on a phone remain pending.
