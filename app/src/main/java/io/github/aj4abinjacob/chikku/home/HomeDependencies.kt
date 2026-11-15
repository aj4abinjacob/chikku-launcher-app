package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.content.Intent
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.badges.BadgeRepository
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionResult
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.platform.AppLauncher
import io.github.aj4abinjacob.chikku.platform.GoogleDiscoverResolver
import io.github.aj4abinjacob.chikku.platform.HomeRoleController
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the HOME and settings UI needs from the rest of the app. `AppContainer` implements
 * this interface; the UI never reaches into the container's concrete type.
 */
interface HomeDependencies {
    /** Process-lifetime scope. Workspace edits run here so a configuration change cannot cancel a commit. */
    /** Shared across HOME recreation; absent test wiring disables destructive orphan cleanup. */
    val widgetOperations: io.github.aj4abinjacob.chikku.home.widget.WidgetOperationCoordinator? get() = null
    val appScope: CoroutineScope
    val appsRepository: AppsRepository
    val webSuggestionSource: WebSuggestionSource
        get() = WebSuggestionSource { _, _ ->
            WebSuggestionResult.Unavailable
        }
    val searchHistoryRepository: io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository? get() = null
    val cardWeatherSource: io.github.aj4abinjacob.chikku.platform.cards.weather.CardWeatherSource
        get() = io.github.aj4abinjacob.chikku.platform.cards.weather.NoopCardWeatherSource
    val cardMediaSource: io.github.aj4abinjacob.chikku.platform.cards.media.CardMediaSource
        get() = io.github.aj4abinjacob.chikku.platform.cards.media.NoopCardMediaSource
    val cardWidgetBindingStore: io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore? get() = null
    val appSearcher: AppSearcher
    val backupService: io.github.aj4abinjacob.chikku.core.model.backup.BackupService? get() = null
    val workspaceRepository: WorkspaceRepository
    val appLauncher: AppLauncher

    /** App lock (locked apps, unlock grants). The launchers in [appLauncher] already enforce it. */
    val appLock: AppLockManager
    val iconProvider: IconProvider

    /**
     * The one app-wide icon-pack repository (the same instance [iconProvider] uses), so icon packs are
     * parsed once and invalidated together. Null in fakes; callers then build their own.
     */
    val iconPackRepository: io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository? get() = null
    val homeRoleController: HomeRoleController
    val googleDiscoverResolver: GoogleDiscoverResolver? get() = null
    val discoverHealthChecker: io.github.aj4abinjacob.chikku.platform.DiscoverHealthChecker? get() = null

    /** Device facts restored settings depend on (Discover, icon packs, picked images); null skips the checks. */
    val restoreEnvironment: io.github.aj4abinjacob.chikku.core.model.backup.RestoreEnvironment? get() = null

    /** User preferences; HOME reads them through `HomeUiState.settings`. */
    val settingsRepository: SettingsRepository

    /** Per-app hidden flag, custom label, icon override and swipe actions. */
    val appCustomizationRepository: AppCustomizationRepository

    /** Drawer groups (tabs). */
    val drawerGroupRepository: DrawerGroupRepository

    /** In-memory notification badges (G08); null where badges are not wired (e.g. test fakes). */
    val badgeRepository: BadgeRepository? get() = null

    /** RoleManager request intent, null if unavailable or already held. */
    fun homeRoleRequestIntent(): Intent?

    /** `Settings.ACTION_HOME_SETTINGS`, the system screen where the user picks a HOME app. */
    fun homeSettingsIntent(): Intent

    /**
     * True while a damaged layout database has been preserved and the user has not been told yet.
     * Updates live: corruption is only detected when the database is first opened, which can be
     * after HOME has started.
     */
    val corruptLayoutNotice: StateFlow<Boolean>

    /** The user has seen the notice. Preserved copies are kept. */
    suspend fun acknowledgeCorruptLayout()
}

/**
 * Implemented by the `Application` to supply the dependencies.
 * Production uses `ChikkuApplication`, which hands out its `AppContainer`; Robolectric tests install a test Application that
 * implements this interface with fakes, so no global mutable state or service locator is needed.
 */
interface HomeDependenciesProvider {
    val homeDependencies: HomeDependencies
}

fun Context.homeDependencies(): HomeDependencies =
    (applicationContext as HomeDependenciesProvider).homeDependencies
