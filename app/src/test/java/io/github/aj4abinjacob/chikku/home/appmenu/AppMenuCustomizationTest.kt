package io.github.aj4abinjacob.chikku.home.appmenu

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.platform.AppMenuDetails
import io.github.aj4abinjacob.chikku.platform.AppShortcut
import io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real popup construction and action routing with an injected app capability boundary. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppMenuCustomizationTest {
    private val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private val activity = activityController.get()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val deps = FakeHomeDependencies(appScope = scope)
    private var prefs = LauncherSettings()
    private var canUninstall = true
    private var shortcuts: List<AppShortcut> = emptyList()
    private var shortcutResult: LaunchResult = LaunchResult.Started
    private val launchedTargets = mutableListOf<AppMenuTarget>()
    private val anchor = View(activity)
    private val key = AppKey("example.test", "example.test.Main", 0)
    private val storeRequests = mutableListOf<List<Intent>>()
    private val editRequests = mutableListOf<AppEditRequest>()
    private val removedFromHome = mutableListOf<String>()
    private val removedFromFolder = mutableListOf<Pair<String, String>>()
    private val controller = AppMenuController(activity, scope, deps, { prefs }, { removedFromHome += it },
        onRemoveFromFolder = { folderId, entryId -> removedFromFolder += folderId to entryId }, unlocker = { _, _ -> },
        onEditSwipeActions = {}, showEditor = { editRequests += it }, detailsLoader = { AppMenuDetails(shortcuts, canUninstall) },
        onShortcutLaunched = launchedTargets::add, shortcutLauncher = { _, _ -> shortcutResult },
        externalIntents = ExternalIntentStarter { intents -> storeRequests += intents.map { Intent(it) }; true })

    init {
        activity.setContentView(FrameLayout(activity).apply { addView(anchor, FrameLayout.LayoutParams(120, 80)) })
        anchor.layout(200, 300, 320, 380)
    }

    @After
    fun tearDown() {
        controller.dismissAll()
        scope.cancel()
        activityController.pause().stop().destroy()
    }

    private fun show(drawer: Boolean): View {
        assertThat(controller.show(anchor, AppMenuTarget(key, "Example", if (drawer) null else "home-id", drawer))).isNotNull()
        val popup = AppMenuController::class.java.getDeclaredField("popup").apply { isAccessible = true }
            .get(controller) as PopupWindow
        return popup.contentView
    }

    private fun View.descendants(): List<View> = listOf(this) +
        if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    private fun View.actionLabels(): Set<String> = descendants().mapNotNull { it.contentDescription?.toString() }.toSet()

    private fun label(resource: Int) = activity.getString(resource)

    @Test
    fun independentlyConfiguredMenusKeepEssentialActionsAndProviderUninstallLimit() {
        prefs = prefs.copy(lookFeel = prefs.lookFeel.copy(
            menuShowHideHome = true, menuShowStoreHome = true, menuShowUninstallHome = false, menuShowRemoveHome = false,
            menuShowHideDrawer = false, menuShowStoreDrawer = false, menuShowUninstallDrawer = true,
        ))
        val home = show(drawer = false).actionLabels()
        assertThat(home).containsAtLeast(label(R.string.app_menu_app_info), label(R.string.app_menu_edit),
            label(R.string.app_menu_hide), label(R.string.app_menu_store))
        assertThat(home).doesNotContain(label(R.string.app_menu_uninstall))
        assertThat(home).doesNotContain(label(R.string.app_menu_remove))
        val drawer = show(drawer = true).actionLabels()
        assertThat(drawer).containsAtLeast(label(R.string.app_menu_app_info), label(R.string.app_menu_edit), label(R.string.app_menu_uninstall))
        assertThat(drawer).doesNotContain(label(R.string.app_menu_hide))
        assertThat(drawer).doesNotContain(label(R.string.app_menu_store))
        canUninstall = false
        assertThat(show(drawer = true).actionLabels()).doesNotContain(label(R.string.app_menu_uninstall))
    }

    @Test
    fun folderEntryMenuRemovesFromItsFolderAndHasNoSwipeActions() {
        assertThat(controller.show(anchor, AppMenuTarget(key, "Example", homeItemId = "entry-id", folderId = "folder-id"))).isNotNull()
        val menu = (AppMenuController::class.java.getDeclaredField("popup").apply { isAccessible = true }
            .get(controller) as PopupWindow).contentView
        assertThat(menu.actionLabels()).containsAtLeast(label(R.string.app_menu_app_info), label(R.string.app_menu_edit), label(R.string.app_menu_remove))
        assertThat(menu.actionLabels()).doesNotContain(label(R.string.app_menu_swipe_actions))
        menu.descendants().single { it.contentDescription == label(R.string.app_menu_remove) }.performClick()
        assertThat(removedFromFolder).containsExactly("folder-id" to "entry-id")
        assertThat(removedFromHome).isEmpty()
        // A workspace icon still removes itself from home.
        show(drawer = false).descendants().single { it.contentDescription == label(R.string.app_menu_remove) }.performClick()
        assertThat(removedFromHome).containsExactly("home-id")
    }

    @Test
    fun optionalStoreActionBuildsOnlyThePublicListingIntent() {
        prefs = prefs.copy(lookFeel = prefs.lookFeel.copy(menuShowStoreDrawer = true, menuShowHideDrawer = false))
        val menu = show(drawer = true)
        menu.descendants().single { it.contentDescription == label(R.string.app_menu_store) }.performClick()
        // This fixture inspects the handoff contract. Guard/authentication and exact-target retry
        // are exercised separately by AuditExternalLaunchGuardTest and AuditExternalActionsRetryTest.
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(storeRequests).hasSize(1)
        val intents = storeRequests.single()
        assertThat(intents).hasSize(2)
        val intent = intents.first()
        assertThat(intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(intent.`package`).isEqualTo("com.android.vending")
        assertThat(intent.data!!.scheme).isEqualTo("https")
        assertThat(intent.data!!.host).isEqualTo("play.google.com")
        assertThat(intent.data!!.getQueryParameter("id")).isEqualTo(key.packageName)
        assertThat(intent.extras).isNull()
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        val fallback = intents.last()
        assertThat(fallback.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(fallback.`package`).isNull()
        assertThat(fallback.data).isEqualTo(intent.data)
        assertThat(fallback.extras).isNull()
        assertThat(fallback.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun shortcutsReportSuccessfulLaunchWithSurfaceIdentityOnlyAfterPlatformSuccess() {
        val info = ShortcutInfo.Builder(activity, "example_shortcut")
            .setShortLabel("Open shortcut")
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
            .build()
        shortcuts = listOf(AppShortcut(info.id, "Open shortcut", null, info))
        fun clickShortcut(drawer: Boolean) {
            val title = show(drawer).descendants().filterIsInstance<TextView>()
                .single { it.text.toString() == "Open shortcut" }
            (title.parent.parent as View).performClick()
        }
        clickShortcut(drawer = true)
        assertThat(launchedTargets).containsExactly(AppMenuTarget(key, "Example", fromDrawer = true))
        shortcutResult = LaunchResult.Unavailable(LaunchResult.Reason.UNKNOWN)
        clickShortcut(drawer = true)
        assertThat(launchedTargets).hasSize(1)
        shortcutResult = LaunchResult.Started
        clickShortcut(drawer = false)
        assertThat(launchedTargets.last()).isEqualTo(AppMenuTarget(key, "Example", "home-id", fromDrawer = false))
    }

    @Test
    fun fullActionSetWrapsWithoutClippingTouchTargetsOnNarrowMenu() {
        prefs = prefs.copy(lookFeel = prefs.lookFeel.copy(menuShowSwipeHome = true, menuShowRemoveHome = true, menuShowUninstallHome = true))
        val menu = show(drawer = false)
        val narrow = (180 * activity.resources.displayMetrics.density).toInt()
        menu.measure(View.MeasureSpec.makeMeasureSpec(narrow, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        menu.layout(0, 0, narrow, menu.measuredHeight)
        val actions = menu.descendants().filter { it.contentDescription != null && it.isClickable }
        assertThat(actions.size).isEqualTo(4)
        val touch = (48 * activity.resources.displayMetrics.density).toInt()
        assertThat(actions.map { it.top }.distinct().size).isAtLeast(2)
        actions.forEach { button ->
            val parent = button.parent as ViewGroup
            // Labelled buttons share the row's width and keep at least a 48dp touch target.
            assertThat(button.width).isAtLeast(touch)
            assertThat(button.height).isAtLeast(touch)
            assertThat(button.left).isAtLeast(0)
            assertThat(button.right).isAtMost(parent.width)
            assertThat(button.descendants().filterIsInstance<TextView>().single().text.toString())
                .isEqualTo(button.contentDescription.toString())
        }
        menu.layoutDirection = View.LAYOUT_DIRECTION_RTL
        menu.measure(View.MeasureSpec.makeMeasureSpec(narrow, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        menu.layout(0, 0, narrow, menu.measuredHeight)
        assertThat((actions.first().parent as ViewGroup).layoutDirection).isEqualTo(View.LAYOUT_DIRECTION_RTL)
        assertThat(actions.first().left).isGreaterThan(actions[1].left)
    }

    @Test
    fun customPopupStylingCanResetToDefaultOnNextOpen() {
        prefs = prefs.copy(lookFeel = prefs.lookFeel.copy(menuBackgroundColor = Color.BLUE, menuCornerRadiusDp = 0f))
        val coloredCard = show(drawer = false).descendants().first { it.background is GradientDrawable }
        assertThat((coloredCard.background as GradientDrawable).color!!.defaultColor).isEqualTo(Color.BLUE)
        assertThat((coloredCard.background as GradientDrawable).cornerRadius).isEqualTo(0f)
        prefs = LauncherSettings()
        val defaultCard = show(drawer = false).descendants().first { it.background is GradientDrawable }
        assertThat((defaultCard.background as GradientDrawable).color!!.defaultColor).isNotEqualTo(Color.BLUE)
        assertThat((defaultCard.background as GradientDrawable).cornerRadius).isEqualTo(14f * activity.resources.displayMetrics.density)
    }

    @Test
    fun shortcutsAndActionsAreSeparateCardsWithAnArrowAndTheScreenBehindIsDimmed() {
        val info = ShortcutInfo.Builder(activity, "example_shortcut")
            .setShortLabel("Open shortcut")
            .setIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))
            .build()
        shortcuts = listOf(AppShortcut(info.id, "Open shortcut", null, info))
        val menu = show(drawer = true)
        val cards = menu.descendants().filter { it.background is GradientDrawable }
        assertThat(cards).hasSize(2)
        val (shortcutCard, actionCard) = cards.partition { card ->
            card.descendants().filterIsInstance<TextView>().any { it.text.toString() == "Open shortcut" }
        }.let { it.first.single() to it.second.single() }
        assertThat(actionCard.actionLabels()).contains(label(R.string.app_menu_app_info))
        assertThat(shortcutCard.actionLabels()).doesNotContain(label(R.string.app_menu_app_info))
        assertThat(menu.descendants().filterIsInstance<MenuArrowView>()).hasSize(1)
        val params = menu.rootView.layoutParams as WindowManager.LayoutParams
        assertThat(params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND).isNotEqualTo(0)
        assertThat(params.dimAmount).isEqualTo(LauncherMenuStyle.SCRIM_DIM)
    }

    private fun openPopup() = AppMenuController::class.java.getDeclaredField("popup").apply { isAccessible = true }.get(controller)

    @Test
    fun editFromASearchResultKeepsTheCustomNameBecauseTheOriginalIsLookedUpByKey() {
        deps.appsRepository.state.value = AppsState.Loaded(listOf(LauncherApp(key, "Original", ProfileType.PERSONAL)))
        deps.appCustomizationRepository.state.value = mapOf(key to AppCustomization(key, customLabel = "Mine"))
        // Search and drawer results carry the custom name as the target's label.
        assertThat(controller.show(anchor, AppMenuTarget(key, "Mine", fromDrawer = true))).isNotNull()
        val menu = (openPopup() as PopupWindow).contentView
        menu.descendants().single { it.contentDescription == label(R.string.app_menu_edit) }.performClick()
        val request = editRequests.single()
        assertThat(request.originalLabel).isEqualTo("Original")
        assertThat(request.customLabel).isEqualTo("Mine")
    }

    @Test
    fun menuClosesWhenTheAppIsLockedWhileOpen() {
        show(drawer = true)
        assertThat(openPopup()).isNotNull()
        runBlocking { deps.appLock.setLocked(LockTarget(key.packageName, key.userSerial), true) }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(openPopup()).isNull()
    }

    @Test
    fun menuClosesWhenTheAppLeavesTheAppList() {
        deps.appsRepository.state.value = AppsState.Loaded(listOf(LauncherApp(key, "Example", ProfileType.PERSONAL)))
        show(drawer = true)
        assertThat(openPopup()).isNotNull()
        deps.appsRepository.state.value = AppsState.Loaded(emptyList())
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(openPopup()).isNull()
    }
}
