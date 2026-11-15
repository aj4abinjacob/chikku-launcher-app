# macOS development setup and build commands

**Current repository state:** the Android Gradle project is bootstrapped (M0 in progress). Modules: `:app` (Android application) and `:core:model` (pure Kotlin/JVM). M0 features are being implemented and are not yet verified on a device; see [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md). CI is defined in [.github/workflows/android.yml](../.github/workflows/android.yml).

## Toolchain (as bootstrapped)

| Item | Value |
|---|---|
| Gradle wrapper | 9.7.1 |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.21 (KSP 2.3.12) |
| minSdk / compileSdk / targetSdk | 29 / 37 / 36 |
| SDK platform package | `platforms;android-37.0` |
| JDK | 21+ to run the unit tests (Robolectric needs Java 21 for SDK 36); Java 17 bytecode; CI uses Temurin 21 |
| Application ID | `io.github.aj4abinjacob.chikku`; debug builds add `.debug` |

Plugin and library versions live in [gradle/libs.versions.toml](../gradle/libs.versions.toml); they were verified against Google Maven and Maven Central on 2026-10-10 (latest stable at that date). Re-check before bumping; Dependabot proposes weekly updates.

## 1. Prepare the Mac

- Install the current stable **Android Studio** from [developer.android.com/studio](https://developer.android.com/studio).
- Install Git (Xcode Command Line Tools or Homebrew). Optional: run **xcode-select --install** if Git is missing.
- Android Studio → SDK Manager: install **Android SDK Platform 37** (`platforms;android-37.0`), **Platform 36**, platform-tools, build-tools and an Android 17 emulator image; also install an Android 15/16 image for compatibility. Command-line alternative: `sdkmanager "platforms;android-37.0" "platform-tools"`.
- JDK 21 or newer is required: Robolectric refuses to run the SDK 36 unit tests on Java 17 ("Android SDK 36 requires Java 21"). Android Studio's bundled JBR works and avoids a separate install:

      export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

  Add the export to your shell profile (for example `~/.zshrc`) if you build from the terminal.
- Have one real Android device with developer options/USB debugging for HOME, widget and gesture tests. Emulators alone are insufficient.
- Configure SSH key or GitHub credential helper for private-repo access.

## 2. Clone the private repository

    git clone git@github.com:aj4abinjacob/chikku-launcher.git
    cd chikku-launcher
    git status

If SSH is not configured, use the authenticated HTTPS clone URL. Never commit GitHub tokens, keystores or personal backups.

## 3. Point Gradle at your SDK

Create `local.properties` in the repository root (it is gitignored):

    sdk.dir=/Users/<you>/Library/Android/sdk

Use the SDK path shown in Android Studio → Settings → Languages & Frameworks → Android SDK. A Homebrew `android-commandlinetools` install lives at `/opt/homebrew/share/android-commandlinetools`. Alternatively export `ANDROID_HOME`. Opening the project in Android Studio creates `local.properties` automatically.

The module structure in [ARCHITECTURE.md](ARCHITECTURE.md) is a target; the project starts with `:app` and `:core:model` and extracts more modules only when a boundary earns its keep. Do not invent dependency version pins; verify new versions against official release notes or Maven repositories when adding them.

## 4. First vertical slice — M0 order of work (in progress)

1. Add a HOME activity with MAIN + HOME + DEFAULT intent filter (see [ANDROID_APIS.md](ANDROID_APIS.md)).
2. Onboarding explains what changing the default launcher does; use RoleManager and the system dialog.
3. Implement a LauncherApps-backed app list, keyed by ComponentName + UserHandle/userSerial.
4. Render a basic app drawer and launch an app via LauncherApps.startMainActivity.
5. Save a minimal workspace to Room and reload after process death.
6. Add a fallback HOME view if app enumeration or DB load fails.
7. Test install/uninstall and default-launcher switching on a physical device.

**Do not start with** 100 settings, animation packs, Quickstep hacks or widget stacks.

## 5. Build, test and install

    ./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
    ./gradlew :core:model:test           # pure Kotlin unit tests
    ./gradlew testDebugUnitTest          # app unit tests (Robolectric, Room)
    ./gradlew lintDebug                  # Android lint (fails the build on errors)
    scripts/check-privacy.sh             # merged-manifest permission and ads/analytics SDK gate
    ./gradlew connectedDebugAndroidTest  # instrumentation; needs a running emulator or device
    adb devices
    adb install -r app/build/outputs/apk/debug/app-debug.apk

The same build/test/lint/privacy commands run in CI on every pull request. Formatting is checked in CI with the ktlint CLI using the rules in [.editorconfig](../.editorconfig); that job is currently advisory. Locally, Android Studio's formatter with `kotlin.code.style=official` (set in `gradle.properties`) is sufficient.

If several Gradle builds run on the same machine at once (for example parallel worktrees), limit memory per build:

    ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx2g -Pkotlin.compiler.execution.strategy=in-process assembleDebug

Android Studio Run is fine for the first install. The debug build installs as `io.github.aj4abinjacob.chikku.debug`.

**Select Chikku as home:** Android Settings → Apps → Default apps → Home app (OEM wording varies), or the app's RoleManager request once the onboarding screen exists (in progress).

**Switch back:** return to Settings → Apps → Default apps → Home app and choose the stock launcher. If Chikku cannot draw a usable home screen, reach Settings from the notification shade, or run `adb uninstall io.github.aj4abinjacob.chikku.debug`. Keep the stock launcher installed as a fallback.

## 6. Initial branch and commit workflow

    git checkout -b feat/home-role
    # make changes and run tests
    git add .
    git commit -m "feat: add HOME role onboarding and app enumeration"
    git push -u origin feat/home-role

Open a PR, use the [template](../.github/PULL_REQUEST_TEMPLATE.md) and merge only after CI checks pass. Releases follow [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md). For solo work, PRs still create an auditable history.

## 7. Project files

Present: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper, `.gitignore`, `app/src/main/AndroidManifest.xml`, `.editorconfig`, [CI workflow](../.github/workflows/android.yml), [Dependabot config](../.github/dependabot.yml), [privacy gate script](../scripts/check-privacy.sh) and [CHANGELOG.md](../CHANGELOG.md) (Keep a Changelog, Semantic Versioning; debug builds use `versionName` `0.1.0-dev-debug`).

Still missing: a real LICENSE file, to be added **after** the legal/license decision ([LICENSING_AND_LEGAL.md](LICENSING_AND_LEGAL.md)), and release signing (never committed to the repository).

## 8. First-device test checklist

- [ ] HOME chooser shows Chikku; stock HOME can be restored.
- [ ] All launchable apps appear, and taps launch correct apps.
- [ ] App install/uninstall updates drawer without restarting.
- [ ] Rotation, dark mode, font scaling and process death do not crash.
- [ ] Empty/corrupt local data shows recovery, not blank screen.
- [ ] No INTERNET permission or tracking SDK appears in manifest/dependencies.
- [ ] Pixel-like emulator plus at least one Samsung/OnePlus/Motorola physical test is planned.

## 9. Debugging reminders

- Use Logcat with sensitive package/profile data redacted before sharing.
- Use StrictMode to catch main-thread I/O, Layout Inspector and Macrobenchmark for jank.
- Test widgets and gesture navigation on real OEM hardware early.
- Never use hidden/system APIs as a default solution to Quickstep integration.
- Update [OPEN_QUESTIONS.md](OPEN_QUESTIONS.md) as architectural choices are resolved.

**Next document:** [M0 ordered backlog](ROADMAP.md) and [architecture](ARCHITECTURE.md).
