package io.github.aj4abinjacob.chikku.settings.components

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserManager
import android.view.Choreographer
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiFrameClock
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.AuditShortcutMetadataPrivacyTest.Shortcuts
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.sections.CardsSettingsSection
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [29],
    application = TestHomeApplication::class,
    shadows = [Shortcuts::class],
    qualifiers = "w360dp-h800dp-xhdpi",
)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditPickerLockInvalidationTest {
    @Test fun lockingOwnerRemovesPrivateLabelsFromAnAlreadyOpenPicker() = verify(false)

    @Test fun cardSettingsImmediatelyWithholdsLockedShortcutCards() = verify(true)

    private fun verify(cardSettings: Boolean) {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        val users = activity.getSystemService(UserManager::class.java)
        shadowOf(users).setUserUnlocked(true)
        shadowOf(
            users,
        ).setUserState(
            Process.myUserHandle(),
            org.robolectric.shadows.ShadowUserManager.UserState.STATE_RUNNING_UNLOCKED,
        )
        assertThat(users.isUserUnlocked(Process.myUserHandle())).isTrue()
        val serial = users.getSerialNumberForUser(Process.myUserHandle())
        val owner = LauncherApp(AppKey(activity.packageName, "Synthetic", serial), "Synthetic", ProfileType.PERSONAL)
        Shortcuts.fixture = ShortcutInfo.Builder(activity, "chat").setShortLabel("Private conversation")
            .setIntent(Intent(Intent.ACTION_VIEW)).build()
        Shortcuts.reads = 0
        Shortcuts.icons = 0
        Shortcuts.afterQuery = {}
        val deps = FakeHomeDependencies()
        deps.appsRepository.state.value = AppsState.Loaded(listOf(owner))
        deps.workspaceRepository.state.value =
            WorkspaceState.Ready(Workspace(GridSpec(4, 5), GridSpec(5, 1), 1, emptyList()))
        val settings = LauncherSettings(
            cards = CardsSettings(
                listOf(
                    AutomationCard(
                        "private",
                        "Private card title",
                        featured = CardFeatured.Shortcut(owner.key.packageName, "chat", serial),
                    ),
                ),
            ),
        )
        deps.settingsRepository.state.value = settings
        val privateText = if (cardSettings) "Private card title" else "Private conversation"
        val frameContext =
            Handler(Looper.getMainLooper()).asCoroutineDispatcher() + AndroidUiFrameClock(Choreographer.getInstance())
        val recomposer = activity.window.decorView.createLifecycleAwareWindowRecomposer(
            frameContext,
            activity.lifecycle,
        )
        activity.setContent(parent = recomposer) {
            CompositionLocalProvider(LocalHomeDependencies provides deps) {
                MaterialTheme {
                    if (cardSettings) {
                        CardsSettingsSection(settings, {})
                    } else {
                        GestureActionPickerBody(
                            state = rememberGestureActionPickerState().apply {
                                page = PickerPage.SHORTCUTS
                                shortcutApp =
                                    owner
                            },
                            current = GestureAction.None,
                            apps = listOf(owner),
                            onPick = {},
                            allowDefault = false,
                            actions = GestureAction.simpleActions,
                        )
                    }
                }
            }
        }
        shadowOf(Looper.getMainLooper()).idle()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        fun texts(): List<String> {
            // Deliver queued StateFlow emissions before notifying Compose's snapshot system.
            // Otherwise the frame below can precede the lock collector's state write.
            shadowOf(Looper.getMainLooper()).idle()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            root.measureAndLayoutForTest()
            return root.semanticsOwner.rootSemanticsNode.flatten().flatMap {
                it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text }
            }
        }
        try {
            val deadline = System.currentTimeMillis() + 5_000
            while (privateText !in texts()) {
                check(System.currentTimeMillis() < deadline) { "Synthetic shortcut did not render" }
                Thread.sleep(10)
            }
            assertThat(runBlocking { deps.appLock.setLocked(LockTarget(owner.key), true) }).isTrue()
            assertThat(deps.appLock.state.value.mayRead(owner.key.packageName, serial)).isFalse()
            assertThat(texts()).doesNotContain(privateText)
            val readsAfterLock = Shortcuts.reads
            repeat(3) { assertThat(texts()).doesNotContain(privateText) }
            assertThat(Shortcuts.reads).isEqualTo(readsAfterLock)
            assertThat(runBlocking { deps.appLock.setLocked(LockTarget(owner.key), false) }).isTrue()
            val unlockedDeadline = System.currentTimeMillis() + 5_000
            while (privateText !in texts()) {
                check(System.currentTimeMillis() < unlockedDeadline) { "Unlocked shortcut did not revalidate" }
                Thread.sleep(10)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }
}
