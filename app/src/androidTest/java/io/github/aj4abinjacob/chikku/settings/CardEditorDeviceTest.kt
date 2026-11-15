package io.github.aj4abinjacob.chikku.settings

import android.app.KeyguardManager
import android.os.Bundle
import android.os.UserManager
import android.view.WindowManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppCustomizationRepository
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerGroupRepository
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardTimeRange
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.cards.cardClock
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetCatalogSource
import io.github.aj4abinjacob.chikku.home.cards.widget.CardWidgetProviderOption
import io.github.aj4abinjacob.chikku.settings.sections.CardsSettingsSection
import io.github.aj4abinjacob.chikku.platform.AppLauncher
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Proxy
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

/** Production Cards UI, entirely in-memory repositories; never writes saved settings or placements. */
@RunWith(AndroidJUnit4::class)
class CardEditorDeviceTest {
    private val context get() = DeviceTestSupport.targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun requireUnlockedScreen() {
        val arguments = InstrumentationRegistry.getArguments()
        val dismiss = arguments.getString("cloudDismissPlatformAnr")
        require(dismiss == null || dismiss == "true" || dismiss == "false") {
            "cloudDismissPlatformAnr must be explicitly true or false"
        }
        if (dismiss == "true") {
            // This optional environment preparation must never close a dialog on a real phone,
            // another acceptance scenario, or a locked/non-system user.
            require(context.packageName == "io.github.aj4abinjacob.chikku.acceptance.cards")
            val users = checkNotNull(context.getSystemService(UserManager::class.java))
            require(users.isSystemUser && users.isUserUnlocked &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("getprop ro.kernel.qemu").use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { input ->
                        require(input.readBytes().toString(Charsets.UTF_8).trim() == "1")
                    }
                }
            dismissForeignPlatformAnr()
        }
        device.wakeUp()
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val unlocked = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) { !keyguard.isKeyguardLocked }
        assumeFalse("Unlock the device to exercise the editor", !unlocked)
    }

    private fun dismissForeignPlatformAnr() {
        val closeSelector = By.pkg("android").res("android:id/aerr_close")
        val waitSelector = By.pkg("android").res("android:id/aerr_wait")
        val closes = device.findObjects(closeSelector)
        val waits = device.findObjects(waitSelector)
        if (closes.isEmpty() && waits.isEmpty()) {
            recordDiagnostics(Bundle().apply { putBoolean("chikkuCardsPlatformAnrDismissed", false) })
            return
        }
        check(closes.size == 1 && waits.size == 1) { "Ambiguous or incomplete platform ANR dialog; refusing dismissal" }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val brand = Pattern.compile(".*chikku.*", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
        val root = instrumentation.uiAutomation.rootInActiveWindow
        val rootIsAndroid = root?.packageName?.toString() == "android"
        val window = root?.window
        val ownTitle = window?.title?.toString()?.let { brand.matcher(it).matches() } == true
        val ownText = device.hasObject(By.pkg("android").text(brand))
        val ownDescription = device.hasObject(By.pkg("android").desc(brand))
        recordDiagnostics(Bundle().apply {
            putBoolean("chikkuCardsPlatformAnrRootIsAndroid", rootIsAndroid)
            putBoolean("chikkuCardsPlatformAnrMentionsChikku", ownTitle || ownText || ownDescription)
        })
        @Suppress("DEPRECATION")
        root?.recycle()
        @Suppress("DEPRECATION")
        window?.recycle()
        check(rootIsAndroid && !ownTitle && !ownText && !ownDescription) {
            "ANR belongs to Chikku or cannot be classified as a foreign platform dialog; refusing dismissal"
        }
        val close = closes.single()
        check(close.isEnabled && close.isClickable) { "Known platform ANR close control is not actionable" }
        close.click() // Only the actual named system action; no coordinate fallback.
        check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            !device.hasObject(closeSelector) && !device.hasObject(waitSelector)
        }) { "Foreign platform ANR dialog did not close" }
        recordDiagnostics(Bundle().apply { putBoolean("chikkuCardsPlatformAnrDismissed", true) })
    }

    /** Final-result extras do not fabricate test lifecycle events or alter parser test counts. */
    private fun recordDiagnostics(values: Bundle) =
        InstrumentationRegistry.getInstrumentation().addResults(values)

    @Test fun createScheduleRecreateCancelDeleteUndoAndReorderSyntheticCards() = runBlocking {
        val firstTitle = "Synthetic evening card"
        val secondTitle = "Synthetic morning card"
        fixture { repository, deps, scenario ->
            createBlank(R.string.cards_intro_create)
            editorNameField().text = firstTitle
            scrollTo(By.desc(context.getString(R.string.cards_action_add_description))).click()
            text(R.string.gesture_action_open_search).click()
            scrollTo(context.getString(R.string.cards_time_enabled)).click()
            // Prove the toggle opened both time buttons before continuing.
            scrollTo(By.desc(context.getString(R.string.cards_time_pick_start, cardClock(context, 480))))
            scrollTo(By.desc(context.getString(R.string.cards_time_pick_end, cardClock(context, 1080))))
            scrollTo(context.getString(R.string.cards_charging_yes)).click()
            val monday = DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, context.resources.configuration.locales[0])
            scrollTo(By.desc(context.getString(R.string.cards_day_chip, monday,
                context.getString(R.string.cards_day_not_selected))), down = false).click()
            text(R.string.cards_save).click()
            assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                runBlocking { repository.settings.first().cards.cards.singleOrNull()?.title == firstTitle }
            }).isTrue()
            val saved = repository.settings.first().cards.cards.single()
            assertThat(saved.actions.first()).isEqualTo(GestureAction.OpenSearch)
            assertThat(saved.conditions.chargingOnly).isTrue()
            assertThat(saved.conditions.weekdays).containsExactly(1)
            assertThat(saved.conditions.timeRange).isEqualTo(CardTimeRange(480, 1080))

            scenario.recreate()
            render(scenario, repository, deps)
            text(firstTitle).click()
            editorNameField().text = "Cancelled synthetic change"
            description(context.getString(R.string.settings_cancel)).click()
            text(R.string.cards_discard).click()
            assertThat(repository.settings.first().cards.cards.single()).isEqualTo(saved)
            assertThat(device.wait(Until.hasObject(By.text(firstTitle)), DeviceTestSupport.UI_TIMEOUT_MS)).isTrue()

            createBlank(R.string.cards_add)
            editorNameField().text = secondTitle
            text(R.string.cards_save).click()
            assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                runBlocking { repository.settings.first().cards.cards.size == 2 }
            }).isTrue()
            description(context.getString(R.string.cards_list_more, secondTitle)).click()
            text(R.string.cards_move_up_short).click()
            assertThat(DeviceTestSupport.waitUntil {
                runBlocking { repository.settings.first().cards.cards.map { it.title } == listOf(secondTitle, firstTitle) }
            }).isTrue()
            val beforeDelete = repository.settings.first().cards
            description(context.getString(R.string.cards_list_more, secondTitle)).click()
            text(R.string.cards_delete_short).click()
            assertThat(DeviceTestSupport.waitUntil {
                runBlocking { repository.settings.first().cards.cards.map { it.title } == listOf(firstTitle) }
            }).isTrue()
            text(R.string.cards_undo).click()
            assertThat(DeviceTestSupport.waitUntil {
                runBlocking { repository.settings.first().cards == beforeDelete }
            }).isTrue()
        }
    }

    @Test fun contentKindsPersistAcrossRecreationAndCancelledChoiceDoesNotWrite() = runBlocking {
        fixture { repository, deps, scenario ->
            val kinds = listOf(R.string.card_content_weather to CardContent.Weather(),
                R.string.card_content_media to CardContent.Media,
                R.string.card_content_widget to CardContent.Widget())
            kinds.forEachIndexed { index, (label, expected) ->
                val title = "Synthetic content card $index"
                createBlank(if (index == 0) R.string.cards_intro_create else R.string.cards_add)
                editorNameField().text = title
                scrollTo(context.getString(label)).click()
                // Setup-free defaults deliberately avoid any provider access or online request.
                text(R.string.cards_save).click()
                assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    runBlocking { repository.settings.first().cards.cards.any { it.title == title } }
                }).isTrue()
                val saved = repository.settings.first().cards.cards.single { it.title == title }
                when (expected) {
                    is CardContent.Weather -> assertThat(saved.content).isEqualTo(expected)
                    CardContent.Media -> assertThat(saved.content).isEqualTo(expected)
                    is CardContent.Widget -> {
                        assertThat(saved.content is CardContent.Widget).isTrue()
                        assertThat((saved.content as CardContent.Widget).provider).isNull()
                    }
                    else -> error("Unexpected synthetic content")
                }
                scenario.recreate()
                render(scenario, repository, deps)
                text(title).click()
                editorNameField() // The row click schedules a new Dialog; wait for its real input.
                scrollTo(context.getString(R.string.card_content_actions)).click()
                description(context.getString(R.string.settings_cancel)).click()
                text(R.string.cards_discard).click()
                assertThat(repository.settings.first().cards.cards.single { it.title == title }).isEqualTo(saved)
            }
        }
    }

    @Test fun namedCreateActionsActivateAndDismissWithoutWritingInEmptyAndPopulatedLists() = runBlocking {
        for (cards in listOf(emptyList(), listOf(AutomationCard("synthetic-existing", "Synthetic existing card")))) {
            fixture(CardsSettings(cards)) { repository, _, _ ->
                val before = repository.state.value
                val label = if (cards.isEmpty()) R.string.cards_intro_create else R.string.cards_add
                createBlank(label)
                editorNameField()
                description(context.getString(R.string.settings_cancel)).click()
                namedCreate(label)
                assertThat(repository.state.value).isEqualTo(before)
                // Activate again after dismissal; a stale button/dialog owner must not consume it.
                createBlank(label)
                editorNameField().text = "Synthetic activation"
                text(R.string.cards_save).click()
                assertThat(DeviceTestSupport.waitUntil {
                    repository.state.value.cards.cards.size == cards.size + 1
                }).isTrue()
            }
        }
    }

    @Test fun maximumCardsKeepsNamedActionDisabledAndDoesNotActivate() = runBlocking {
        val cards = (0 until CardsSettings.MAX_CARDS).map { AutomationCard("synthetic-$it", "Synthetic card $it") }
        fixture(CardsSettings(cards)) { repository, _, _ ->
            val action = namedCreate(R.string.cards_add, enabled = false)
            action.click() // A tap on the named, disabled action must not open an editor.
            device.waitForIdle()
            assertThat(device.hasObject(By.clazz("android.widget.EditText"))).isFalse()
            assertThat(repository.state.value.cards.cards).isEqualTo(cards)
        }
    }

    @Test fun pendingCardMutationDisablesTheNamedAddActionUntilCommit() = runBlocking {
        val card = AutomationCard("synthetic-pending", "Synthetic pending card")
        fixture(CardsSettings(listOf(card))) { repository, _, _ ->
            val release = CompletableDeferred<Unit>()
            repository.holdUpdate = release
            description(context.getString(R.string.cards_list_more, card.title)).click()
            text(R.string.cards_delete_short).click()
            namedCreate(R.string.cards_add, enabled = false).click()
            device.waitForIdle()
            assertThat(device.hasObject(By.clazz("android.widget.EditText"))).isFalse()
            assertThat(repository.state.value.cards.cards).containsExactly(card)
            release.complete(Unit)
            assertThat(DeviceTestSupport.waitUntil { repository.state.value.cards.cards.isEmpty() }).isTrue()
            namedCreate(R.string.cards_intro_create)
        }
    }

    private class InMemorySettings(cards: CardsSettings) : SettingsRepository {
        val state = MutableStateFlow(LauncherSettings(cards = cards))
        override val settings = state
        private val mutex = Mutex()
        var holdUpdate: CompletableDeferred<Unit>? = null
        override suspend fun update(transform: (LauncherSettings) -> LauncherSettings) {
            mutex.withLock {
                holdUpdate?.await()
                state.value = transform(state.value).coerced()
            }
        }
        override suspend fun reset(): Unit = error("No reset in a synthetic card fixture")
        override suspend fun exportJson(): String = error("No export in a synthetic card fixture")
        override suspend fun importJson(json: String): SettingsImportResult = error("No import in a synthetic card fixture")
    }

    private suspend fun fixture(cards: CardsSettings = CardsSettings(),
        block: suspend (InMemorySettings, HomeDependencies, ActivityScenario<SettingsActivity>) -> Unit) {
        val real = context.homeDependencies()
        val savedPreferences = context.getSharedPreferences(SharedPreferencesSettingsRepository.PREFS_NAME, 0)
        val before = savedPreferences.all.toMap()
        val settingsFile = File(context.applicationInfo.dataDir,
            "shared_prefs/${SharedPreferencesSettingsRepository.PREFS_NAME}.xml")
        val beforeBytes = settingsFile.takeIf { it.isFile }?.readBytes()
        val repository = InMemorySettings(cards)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var prohibitedCalls = 0
        fun <T : Any> frozen(type: Class<T>, property: String, value: Any): T {
            @Suppress("UNCHECKED_CAST")
            return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
                when (method.name) {
                    property -> value
                    "toString" -> "Synthetic card repository"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === arguments?.firstOrNull()
                    else -> { prohibitedCalls++; error("A card fixture called a non-synthetic operation") }
                }
            } as T
        }
        val initial = repository.state.value
        val deps = object : HomeDependencies by real {
            override val appScope = scope
            override val settingsRepository = repository
            override val appsRepository = frozen(AppsRepository::class.java, "getApps",
                MutableStateFlow<AppsState>(AppsState.Loaded(emptyList())))
            override val workspaceRepository = frozen(WorkspaceRepository::class.java, "getWorkspace",
                MutableStateFlow<WorkspaceState>(WorkspaceState.Ready(Workspace(initial.desktopGrid, initial.dockStorageGrid, 1, emptyList()))))
            override val appCustomizationRepository = frozen(AppCustomizationRepository::class.java, "getCustomizations",
                MutableStateFlow(emptyMap<AppKey, AppCustomization>()))
            override val drawerGroupRepository = frozen(DrawerGroupRepository::class.java, "getGroups",
                MutableStateFlow(emptyList<DrawerGroup>()))
            override val cardWeatherSource = io.github.aj4abinjacob.chikku.platform.cards.weather.NoopCardWeatherSource
            override val cardMediaSource = io.github.aj4abinjacob.chikku.platform.cards.media.NoopCardMediaSource
            override val cardWidgetBindingStore: io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore? = null
            override val discoverHealthChecker: io.github.aj4abinjacob.chikku.platform.DiscoverHealthChecker? = null
            override val appLauncher = object : AppLauncher {
                override fun launch(key: AppKey, sourceBounds: android.graphics.Rect?): LaunchResult {
                    prohibitedCalls++; error("A card fixture tried to launch an app")
                }
                override fun openAppDetails(key: AppKey, sourceBounds: android.graphics.Rect?): LaunchResult {
                    prohibitedCalls++; error("A card fixture tried to open app details")
                }
            }
        }
        try {
            // Start the harmless overview, then replace its composition with the real Cards route
            // and synthetic dependencies before looking up or interacting with any card control.
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                render(scenario, repository, deps)
                block(repository, deps, scenario)
                assertThat(prohibitedCalls).isEqualTo(0)
            }
        } finally {
            scope.cancel()
            // Includes raw legacy JSON and damage entries. Boolean equality prevents a failing
            // assertion from printing saved settings. No cleanup rewrites real preferences.
            assertThat(savedPreferences.all == before).isTrue()
            val afterBytes = settingsFile.takeIf { it.isFile }?.readBytes()
            assertThat(if (beforeBytes == null) afterBytes == null
                else afterBytes != null && beforeBytes.contentEquals(afterBytes)).isTrue()
        }
    }

    private fun render(scenario: ActivityScenario<SettingsActivity>, repository: InMemorySettings, deps: HomeDependencies) {
        val committed = AtomicBoolean(false)
        val frames = CountDownLatch(1)
        val catalog = object : CardWidgetCatalogSource {
            override fun personalSerial(): Long = 0L
            override fun load(hidden: Set<AppKey>): List<CardWidgetProviderOption> = emptyList()
        }
        scenario.onActivity { activity ->
            // Scoped to this Activity; does not change the emulator/phone timeout setting.
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.setContent {
                val settings by repository.state.collectAsState()
                ChikkuSettingsTheme {
                    CompositionLocalProvider(LocalHomeDependencies provides deps,
                        LocalActivityResultRegistryOwner provides activity) {
                        Box(Modifier.fillMaxSize().systemBarsPadding()) {
                            CardsSettingsSection(settings, { error("Cards writes must use the synthetic repository") },
                                widgetCatalogSource = catalog)
                        }
                        SideEffect { committed.set(true) }
                    }
                }
            }
            val decor = activity.window.decorView
            decor.postOnAnimation { decor.postOnAnimation { frames.countDown() } }
        }
        val drawn = frames.await(30, TimeUnit.SECONDS)
        var attached = false
        var shown = false
        var focused = false
        var positiveBounds = false
        var current = false
        val ready = DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                attached = decor.isAttachedToWindow
                shown = decor.isShown
                focused = decor.hasWindowFocus()
                positiveBounds = decor.width > 0 && decor.height > 0
                current = !activity.isFinishing && !activity.isDestroyed
            }
            drawn && committed.get() && attached && shown && focused && positiveBounds && current
        }
        recordDiagnostics(Bundle().apply {
            putBoolean("chikkuCardsCompositionCommitted", committed.get())
            putBoolean("chikkuCardsTwoFramesDrawn", drawn)
            putBoolean("chikkuCardsDecorAttached", attached)
            putBoolean("chikkuCardsDecorShown", shown)
            putBoolean("chikkuCardsDecorFocused", focused)
            putBoolean("chikkuCardsDecorHasBounds", positiveBounds)
            putBoolean("chikkuCardsActivityCurrent", current)
            putBoolean("chikkuCardsFixtureReady", ready)
        })
        check(ready) { "Synthetic Cards composition did not reach a drawn, focused Activity" }
    }

    private fun namedCreate(label: Int, enabled: Boolean = true): UiObject2 {
        val title = context.getString(label)
        val selector = if (label == R.string.cards_add) By.desc(title) else By.text(title)
        val found = device.wait(Until.findObject(selector), DeviceTestSupport.UI_TIMEOUT_MS)
        if (found == null) {
            recordDiagnostics(Bundle().apply {
                putBoolean("chikkuCardsActivePackageIsOwned", device.currentPackageName == context.packageName)
                putInt("chikkuCardsResumedSettingsCount", DeviceTestSupport.resumedActivities().count { it is SettingsActivity })
                putInt("chikkuCardsIntroTextCount", device.findObjects(By.text(context.getString(R.string.cards_intro_create))).size)
                putInt("chikkuCardsAddDescriptionCount", device.findObjects(By.desc(context.getString(R.string.cards_add))).size)
            })
            error("Named card creation action not exposed through Android accessibility after fixture readiness")
        }
        val action = clickableParent(found)
        assertThat(action.isClickable).isTrue()
        assertThat(DeviceTestSupport.waitUntil {
            device.findObject(selector)?.let { clickableParent(it).isEnabled == enabled } == true
        }).isTrue()
        val current = clickableParent(checkNotNull(device.findObject(selector)))
        val density = context.resources.displayMetrics.density
        assertThat(current.visibleBounds.width() / density).isAtLeast(48f)
        assertThat(current.visibleBounds.height() / density).isAtLeast(48f)
        return current
    }

    /** "Add card" opens the starting-point chooser first; these flows start from a blank card. */
    private fun createBlank(label: Int) {
        namedCreate(label).click()
        text(R.string.cards_template_blank).click()
    }

    private fun editorNameField(): UiObject2 = checkNotNull(device.wait(
        Until.findObject(By.clazz("android.widget.EditText")), DeviceTestSupport.UI_TIMEOUT_MS,
    )) { "Editor name field not visible" }

    private fun text(id: Int) = text(context.getString(id))
    private fun text(label: String): UiObject2 {
        // A control can enable one frame after the input that allows it (Save after the name is
        // typed), so look again until it is ready instead of judging the first snapshot.
        var action: UiObject2? = null
        DeviceTestSupport.waitUntil(DeviceTestSupport.UI_TIMEOUT_MS) {
            val found = device.findObject(By.text(label)) ?: device.findObject(By.desc(label))
            action = try {
                found?.let(::clickableParent)?.takeIf { it.isEnabled && it.isClickable }
            } catch (_: androidx.test.uiautomator.StaleObjectException) {
                null
            }
            action != null
        }
        return action ?: error("Required synthetic card control was not visible, enabled and clickable")
    }

    private fun description(label: String): UiObject2 = checkNotNull(device.wait(
        Until.findObject(By.desc(label)), DeviceTestSupport.UI_TIMEOUT_MS,
    )) { "Requested synthetic card action not visible" }

    private fun scrollTo(label: String, down: Boolean = true): UiObject2 = scrollTo(By.text(label), down)

    private fun scrollTo(selector: BySelector, down: Boolean = true): UiObject2 {
        fun visible(node: UiObject2?): Boolean = node?.visibleBounds?.let {
            it.width() > 0 && it.height() > 0
        } == true
        fun scroller(): UiObject2? = device.findObjects(By.scrollable(true)).lastOrNull { visible(it) }
        fun awaitEditorContent() {
            check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                visible(device.findObject(selector)) || scroller() != null
            }) { "Neither the named editor control nor its scroll container became visible" }
        }
        awaitEditorContent()
        var searchDown = down
        var unseenMoves = 0
        repeat(30) {
            val target = device.findObject(selector)
            val scrollContainer = scroller()
            val bounds = target?.visibleBounds
            if (scrollContainer == null) {
                // A fitting editor has no native scrollable range. A newly opening Dialog can
                // also expose its named control before its scroll semantics; use that actual
                // control instead of requiring or inventing a scroll gesture.
                if (visible(target)) {
                    val refreshed = device.findObject(selector) ?: return@repeat
                    val action = clickableParent(refreshed)
                    if (action.isEnabled && action.isClickable) return action
                }
                awaitEditorContent()
                return@repeat
            }
            val viewport = scrollContainer.visibleBounds
            val margin = (32 * context.resources.displayMetrics.density).toInt()
            if (bounds != null && bounds.top >= viewport.top + margin && bounds.bottom <= viewport.bottom - margin) {
                // Swiping can trigger lazy recomposition. Reacquire after the idle barrier, then
                // click the semantic parent rather than a non-clickable text/description child.
                device.waitForIdle()
                val refreshed = device.findObject(selector) ?: return@repeat
                return clickableParent(refreshed)
            }
            // A row can pass the viewport during a swipe and stop being exposed to UIAutomator.
            // Search back through the form rather than continuing towards the same end forever.
            if (bounds == null) {
                unseenMoves++
                if (unseenMoves == 8) {
                    searchDown = !searchDown
                    unseenMoves = 0
                }
            } else {
                unseenMoves = 0
            }
            val moveDown = if (bounds == null) searchDown else bounds.centerY() >= viewport.centerY()
            // Once found, centre the row with a short correction instead of overshooting it again.
            val distance = if (bounds == null) viewport.height() / 2 else
                kotlin.math.abs(bounds.centerY() - viewport.centerY())
                    .coerceIn(margin / 2, viewport.height() / 2)
            val lower = viewport.centerY() + distance / 2
            val upper = viewport.centerY() - distance / 2
            device.swipe(viewport.centerX(), if (moveDown) lower else upper,
                viewport.centerX(), if (moveDown) upper else lower, 24)
            device.waitForIdle()
        }
        error("Requested editor control was not fully inside its scroll viewport")
    }

    private fun clickableParent(node: UiObject2): UiObject2 {
        var current = node
        while (!current.isClickable && current.parent != null) current = current.parent
        return current
    }
}
