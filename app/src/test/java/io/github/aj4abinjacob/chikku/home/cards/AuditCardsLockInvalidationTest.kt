package io.github.aj4abinjacob.chikku.home.cards

import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardEnvironment
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.app
import io.github.aj4abinjacob.chikku.platform.GestureActionExecutor
import io.github.aj4abinjacob.chikku.platform.GestureHost
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.platform.cards.CardEnvironmentSource
import io.github.aj4abinjacob.chikku.platform.cards.CardValidation
import io.github.aj4abinjacob.chikku.platform.cards.CardsTargetValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class AuditCardsLockInvalidationTest {
    @Test fun lockingOwnerWithdrawsAnAlreadyOpenShortcutCardAndUnlockRevalidates() = verify(false)

    @Test fun blockedValidationCannotDelayWithdrawingAlreadyVisiblePrivateMetadata() = verify(true)

    private fun verify(blockDuringLock: Boolean) {
        val activityController = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = activityController.get()
        val root = FrameLayout(activity)
        activity.setContentView(root)
        val deps = FakeHomeDependencies()
        val owner = app("Synthetic")
        val shortcut = CardFeatured.Shortcut(owner.key.packageName, "private-chat", owner.key.userSerial)
        val card = AutomationCard("private", "Card", featured = shortcut)
        deps.appsRepository.state.value = AppsState.Loaded(listOf(owner))
        deps.workspaceRepository.state.value =
            WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))
        deps.settingsRepository.state.value = LauncherSettings(cards = CardsSettings(listOf(card)))
        val environmentState = MutableStateFlow(CardEnvironment(1, 600, isCharging = false, isWifi = false))
        val environment = object : CardEnvironmentSource {
            override fun current() = environmentState.value
            override fun observe() = environmentState
        }
        val blockNext = AtomicBoolean(false)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = GestureActionExecutor(
            activity,
            deps.appLauncher,
            object : GestureHost {
                override fun openDrawer() = Unit
                override fun openSearch() = Unit
                override fun goToDefaultPage() = Unit
            },
            shortcuts = { _, _, _, _ -> LaunchResult.Started },
        )
        // Deliberately permissive validator verifies the UI independently withholds stale metadata.
        val validator = CardsTargetValidator { _, apps, hidden, _ ->
            if (blockNext.getAndSet(false)) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            CardValidation(
                CardTargetSnapshot(apps, hidden, owner.key.userSerial, shortcuts = setOf(shortcut)),
                shortcutLabels = mapOf(shortcut to "Private conversation"),
            )
        }
        val controller = CardsController(activity, root, deps, executor, {}, environment, validator)
        fun shown() = root.findViewWithTag<View>("card:private") != null
        try {
            controller.open()
            waitUntil { shown() }
            if (blockDuringLock) {
                blockNext.set(true)
                environmentState.value = CardEnvironment(1, 601, isCharging = false, isWifi = false)
                waitUntil { entered.count == 0L }
                assertThat(shown()).isTrue()
            }
            runBlocking { deps.appLock.setLocked(LockTarget(owner.key), true) }
            waitUntil { !shown() }
            assertThat(root.findViewWithTag<View>("card_featured:private")).isNull()
            release.countDown()
            runBlocking { deps.appLock.setLocked(LockTarget(owner.key), false) }
            waitUntil { shown() }
            assertThat(deps.appLauncher.launched).isEmpty()
        } finally {
            release.countDown()
            controller.onDestroy()
            activityController.pause().stop().destroy()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Card privacy transition did not complete" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
