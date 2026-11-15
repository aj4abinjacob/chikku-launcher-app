package io.github.aj4abinjacob.chikku

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.badges.BadgeAccess
import io.github.aj4abinjacob.chikku.badges.BadgeRepository
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.backup.BackupService
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAccess
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreEnvironment
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import io.github.aj4abinjacob.chikku.core.model.search.WebSuggestionSource
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.ChikkuDatabaseFactory
import io.github.aj4abinjacob.chikku.data.RoomAppCustomizationRepository
import io.github.aj4abinjacob.chikku.data.RoomDrawerGroupRepository
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.data.SharedPreferencesAppLockStore
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.data.WorkspaceGrids
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import io.github.aj4abinjacob.chikku.data.restore.RecoveryCustomizations
import io.github.aj4abinjacob.chikku.data.restore.RecoveryGroups
import io.github.aj4abinjacob.chikku.data.restore.RecoverySettings
import io.github.aj4abinjacob.chikku.data.restore.RecoveryWorkspace
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.platform.AndroidDiscoverHealthChecker
import io.github.aj4abinjacob.chikku.platform.AndroidGoogleDiscoverResolver
import io.github.aj4abinjacob.chikku.platform.AndroidRestoreEnvironment
import io.github.aj4abinjacob.chikku.platform.AppLauncher
import io.github.aj4abinjacob.chikku.platform.CachingIconProvider
import io.github.aj4abinjacob.chikku.platform.DefaultDockAppsResolver
import io.github.aj4abinjacob.chikku.platform.DiscoverHealthChecker
import io.github.aj4abinjacob.chikku.platform.GoogleDiscoverResolver
import io.github.aj4abinjacob.chikku.platform.HomeRoleController
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.LauncherAppsLauncher
import io.github.aj4abinjacob.chikku.platform.LauncherAppsRepository
import io.github.aj4abinjacob.chikku.platform.RoleManagerHomeRoleController
import io.github.aj4abinjacob.chikku.search.HttpsWebSuggestionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual constructor-injection root. Each dependency is created lazily on first use; activities
 * reach it through [ChikkuApplication.container]. Hilt is not used until the graph justifies it.
 */
class AppContainer(context: Context) : HomeDependencies {
    private val appContext = context.applicationContext

    /** Lives as long as the process; children fail independently. */
    override val widgetOperations = io.github.aj4abinjacob.chikku.home.widget.WidgetOperationCoordinator()
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val iconPackRepository: io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository by lazy {
        io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository(appContext)
    }

    private val cachingIconProvider = lazy {
        // Icons follow the icon pack/shape settings and per-app icon overrides (look-feel, G15).
        CachingIconProvider(appContext, Dispatchers.IO, iconPacks = iconPackRepository).also {
            it.follow(appScope, settingsRepository.settings, appCustomizationRepository.customizations)
        }
    }
    override val iconProvider: IconProvider by cachingIconProvider

    /**
     * Memory pressure ([android.content.ComponentCallbacks2] level): trims process-wide caches that
     * exist already, without creating any.
     */
    fun trimMemory(level: Int) {
        if (cachingIconProvider.isInitialized()) cachingIconProvider.value.trimMemory(level)
    }

    override val appsRepository: AppsRepository by lazy {
        object : AppsRepository {
            private val source = LauncherAppsRepository(appContext, appScope, Dispatchers.IO, onPackageChanged = iconProvider::invalidate)
            override val apps = restoreAccess.observe(source.apps, AppsState.Failed("Restore recovery is required"))
            override fun refresh() = source.refresh()
        }
    }

    override val webSuggestionSource: WebSuggestionSource by lazy {
        HttpsWebSuggestionSource(Dispatchers.IO)
    }

    override val cardWeatherSource by lazy {
        io.github.aj4abinjacob.chikku.platform.cards.weather.MetNorwayWeatherSource(
            cities = io.github.aj4abinjacob.chikku.platform.cards.weather.OfflineCitySearch(
                { appContext.assets.open("weather/cities.index") },
            ),
            cache = io.github.aj4abinjacob.chikku.platform.cards.weather.FileForecastCache(
                { java.io.File(appContext.cacheDir, "weather-met-v1") },
            ),
        )
    }
    override val cardMediaSource by lazy {
        io.github.aj4abinjacob.chikku.platform.cards.media.AndroidCardMediaSource(
            appContext, appsRepository, appCustomizationRepository, settingsRepository, Dispatchers.IO,
        )
    }
    override val cardWidgetBindingStore by lazy {
        io.github.aj4abinjacob.chikku.data.cards.FileCardWidgetBindingStore(appContext.noBackupFilesDir)
    }

    override val appSearcher: AppSearcher by lazy { DefaultAppSearcher() }

    override val searchHistoryRepository by lazy {
        io.github.aj4abinjacob.chikku.data.LocalSearchHistoryRepository(
            appContext, settingsRepository, appsRepository, appCustomizationRepository, appLock::isLocked, appScope, Dispatchers.IO,
        )
    }

    override val appLauncher: AppLauncher by lazy {
        io.github.aj4abinjacob.chikku.platform.RememberingAppLauncher(LauncherAppsLauncher(appContext, appLock), searchHistoryRepository, appScope)
    }

    override val appLock: AppLockManager by lazy {
        AppLockManager(SharedPreferencesAppLockStore(appContext, Dispatchers.IO), appScope, SystemClock::elapsedRealtime).also { lock ->
            // Every unlock ends when the screen turns off. Registered for the process lifetime.
            ContextCompat.registerReceiver(appContext, object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = lock.relockAll()
            }, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    override val googleDiscoverResolver: GoogleDiscoverResolver by lazy {
        AndroidGoogleDiscoverResolver(appContext, Dispatchers.IO, experimentalOverlay = BuildConfig.DEBUG)
    }

    override val discoverHealthChecker: DiscoverHealthChecker by lazy {
        AndroidDiscoverHealthChecker(appContext, Dispatchers.IO, experimentalOverlay = BuildConfig.DEBUG)
    }

    override val restoreEnvironment: RestoreEnvironment by lazy {
        AndroidRestoreEnvironment(appContext, discoverHealthChecker, Dispatchers.IO)
    }

    private val roleController by lazy { RoleManagerHomeRoleController(appContext) }

    override val homeRoleController: HomeRoleController get() = roleController

    private val corruptLayoutPending = MutableStateFlow(false)

    override val corruptLayoutNotice: StateFlow<Boolean> = corruptLayoutPending.asStateFlow()

    private val rawSettings by lazy { SharedPreferencesSettingsRepository(appContext, Dispatchers.IO) }
    private val restoreAccess: RestoreAccess = RestoreAccess { roomWorkspaceRepository.recoverPendingPageEdit() && backupService.recoverPending() }
    override val backupService: BackupService by lazy {
        BackupService(rawSettings, roomWorkspaceRepository, rawCustomizations, rawGroups,
            BuildConfig.VERSION_NAME, journal = FileRestoreJournal({ appContext.noBackupFilesDir }, Dispatchers.IO), access = restoreAccess)
    }
    override val settingsRepository: SettingsRepository by lazy { RecoverySettings(rawSettings, restoreAccess) }

    /** One database for workspace, customizations and drawer groups. Room opens it lazily on first query (IO). */
    private val database: ChikkuDatabase by lazy {
        ChikkuDatabaseFactory.create(appContext, onCorruptCopyPreserved = { corruptLayoutPending.value = true })
    }

    private val rawCustomizations by lazy { RoomAppCustomizationRepository(database, Dispatchers.IO) }
    override val appCustomizationRepository: AppCustomizationRepository by lazy { RecoveryCustomizations(rawCustomizations, restoreAccess) }

    private val rawGroups by lazy { RoomDrawerGroupRepository(database, Dispatchers.IO) }
    override val drawerGroupRepository: DrawerGroupRepository by lazy { RecoveryGroups(rawGroups, restoreAccess) }

    private val roomWorkspaceRepository: RoomWorkspaceRepository by lazy {
        val grids = rawSettings.settings
            .map { WorkspaceGrids(it.desktopGrid, it.dockStorageGrid, dockPageColumns = it.dockGrid.columns) }
            .distinctUntilChanged()
        RoomWorkspaceRepository(database, Dispatchers.IO, grids, pageSettings = rawSettings,
            onPageRecoveryRequired = { restoreAccess.requireRecovery() })
    }

    override val workspaceRepository: WorkspaceRepository by lazy { RecoveryWorkspace(roomWorkspaceRepository, restoreAccess) }

    private fun seedDefaults() {
        appScope.launch {
            restoreAccess.run(false) {
                val dockApps = runCatching { DefaultDockAppsResolver(appContext, Dispatchers.IO).resolve() }.getOrDefault(emptyList())
                if (dockApps.isEmpty()) return@run false
                val safeToSeed = withContext(Dispatchers.IO) {
                    database.openHelper.writableDatabase
                    ChikkuDatabaseFactory.corruptCopies(appContext).isEmpty()
                }
                if (safeToSeed) roomWorkspaceRepository.seedDefaultsOnce(dockApps) else false
            }
        }
    }

    override val badgeRepository: BadgeRepository by lazy { BadgeRepository() }

    /** Enables the shared listener for chosen badges or saved media cards; badge OFF clears badge data. */
    private fun observeBadgeStyle() {
        val access = BadgeAccess(appContext)
        appScope.launch {
            settingsRepository.settings.map { settings -> settings.badges.style to
                settings.cards.cards.any { it.enabled && it.content == io.github.aj4abinjacob.chikku.core.model.cards.CardContent.Media }
            }.distinctUntilChanged().collect { (style, mediaEnabled) ->
                access.apply(style, badgeRepository, Dispatchers.IO, mediaEnabled)
            }
        }
    }

    override fun homeRoleRequestIntent(): Intent? = roleController.createRequestIntent()

    override fun homeSettingsIntent(): Intent = roleController.homeSettingsIntent()

    override suspend fun acknowledgeCorruptLayout() {
        withContext(Dispatchers.IO) { ChikkuDatabaseFactory.acknowledgeCorruptCopies(appContext) }
        corruptLayoutPending.value = false
    }

    init {
        seedDefaults()
        observeBadgeStyle()
        io.github.aj4abinjacob.chikku.platform.NewAppsController(
            appContext, appScope, settingsRepository, { workspaceRepository },
            { appCustomizationRepository }, Dispatchers.IO,
            canEdit = {
                withContext(Dispatchers.IO) {
                    !io.github.aj4abinjacob.chikku.home.StartupGuard.create(appContext).isSafeMode() &&
                        !corruptLayoutPending.value && !ChikkuDatabaseFactory.hasPreservedCorruptCopy(appContext)
                }
            },
        )
        appScope.launch {
            if (withContext(Dispatchers.IO) { ChikkuDatabaseFactory.hasPreservedCorruptCopy(appContext) }) {
                corruptLayoutPending.value = true
            }
        }
    }
}
