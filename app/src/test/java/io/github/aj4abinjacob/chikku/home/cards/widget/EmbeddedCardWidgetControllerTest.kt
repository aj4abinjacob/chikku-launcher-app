package io.github.aj4abinjacob.chikku.home.cards.widget

import android.app.Activity
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingState
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore
import io.github.aj4abinjacob.chikku.data.cards.MemoryCardWidgetBindingStore
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import java.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class EmbeddedCardWidgetControllerTest {
    private lateinit var activityController: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var controller: EmbeddedCardWidgetController
    private val platform = FakePlatform()
    private val deps = FakeHomeDependencies()
    private val catalog = object : CardWidgetCatalogSource {
        override fun personalSerial() = 0L
        override fun load(hidden: Set<AppKey>) = emptyList<CardWidgetProviderOption>()
    }
    private val card = AutomationCard("widget-card", "Widget", content = CardContent.Widget())

    @Before fun setup() {
        // Home constructs during onCreate; controllers can also be replaced after STARTED.
        activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = activityController.get()
        controller = makeController()
    }
    @After fun cleanup() { controller.onDestroy(); activityController.pause().stop().destroy() }
    private fun makeController(store: CardWidgetBindingStore = MemoryCardWidgetBindingStore()) =
        EmbeddedCardWidgetController(activity, deps, store, platform, catalog)
    private fun cell(): FrameLayout {
        controller.openPanel()
        return (controller.createView(card, SurfaceColors.LIGHT) as FrameLayout).also { activity.setContentView(it) }
    }
    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Embedded widget surface did not finish" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
    private fun View.buttons(): List<Button> = when (this) {
        is Button -> listOf(this)
        is android.view.ViewGroup -> (0 until childCount).flatMap { getChildAt(it).buttons() }
        else -> emptyList()
    }

    @Test fun constructingAndReplacingAfterStartedRegistersConsentWithoutLifecycleException() {
        controller.onDestroy()
        controller = makeController()
        val cell = cell()
        waitUntil { cell.buttons().isNotEmpty() }
        assertThat(cell.buttons().single().text.toString()).isEqualTo(activity.getString(R.string.card_widget_choose))
    }

    @Test fun unconfiguredCardShowsChooseActionThatOpensTheCardsEditor() {
        val cell = cell()
        waitUntil { cell.buttons().isNotEmpty() }
        assertThat(platform.allocations).isEqualTo(0)
        cell.buttons().single().performClick()
        val intent = shadowOf(activity).nextStartedActivity
        assertThat(intent.component?.className).isEqualTo(SettingsActivity::class.java.name)
        assertThat(intent.getStringExtra(SettingsActivity.EXTRA_SECTION)).isEqualTo(SettingsSection.CARDS.name)
    }

    @Test fun stoppingAndClosingReleaseViewsAndListeningWithoutDeletingNativeBindings() {
        val cell = cell()
        waitUntil { cell.childCount > 0 }
        assertThat(platform.listening).isTrue()
        val oldChooseButton = cell.buttons().single()
        activityController.pause().stop()
        assertThat(cell.childCount).isEqualTo(0)
        assertThat(platform.listening).isFalse()
        oldChooseButton.performClick()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        activityController.start().resume()
        waitUntil { cell.childCount > 0 }
        controller.closePanel()
        assertThat(cell.childCount).isEqualTo(0)
        assertThat(platform.listening).isFalse()
        assertThat(platform.deleted).isEmpty()
    }

    @Test fun storageFailureLeavesAnExplicitRetryInsteadOfCrashingTheActivity() {
        controller.onDestroy()
        val failing = object : CardWidgetBindingStore by MemoryCardWidgetBindingStore() {
            override suspend fun load(): CardWidgetBindingState = throw IOException("unavailable")
        }
        controller = makeController(failing)
        val cell = cell()
        waitUntil { cell.buttons().isNotEmpty() }
        assertThat(cell.buttons().single().text.toString()).isEqualTo(activity.getString(R.string.card_widget_retry))
        cell.buttons().single().performClick()
        waitUntil { cell.buttons().isNotEmpty() }
        assertThat(platform.allocations).isEqualTo(0)
        assertThat(activity.isFinishing).isFalse()
    }

    private class FakePlatform : EmbeddedCardWidgetPlatform {
        var listening = false
        var allocations = 0
        val deleted = mutableListOf<Int>()
        override fun ownedIds() = emptySet<Int>()
        override fun allocateId(): Int { allocations++; return 1 }
        override fun deleteId(id: Int) { deleted += id }
        override fun providerAvailable(provider: ComponentKey) = false
        override fun bindIfAllowed(id: Int, provider: ComponentKey) = false
        override fun boundProvider(id: Int): ComponentKey? = null
        override fun needsConfiguration(id: Int) = false
        override fun info(id: Int): AppWidgetProviderInfo? = null
        override fun createView(context: Context, id: Int, info: AppWidgetProviderInfo) = AppWidgetHostView(context)
        override fun clearViews() = Unit
        override fun startListening() { listening = true }
        override fun stopListening() { listening = false }
        override fun consentIntent(bindingId: Int, provider: ComponentKey) = Intent("widget.consent")
        override fun configure(activity: Activity, id: Int, requestCode: Int) = Unit
        override fun updateSize(id: Int, widthDp: Int, heightDp: Int) = Unit
    }
}
