package io.github.aj4abinjacob.chikku.platform

import android.app.Application
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.AppLockManager
import io.github.aj4abinjacob.chikku.applock.LockTarget
import io.github.aj4abinjacob.chikku.applock.StoredAppLock
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.home.InMemoryAppLockStore
import io.github.aj4abinjacob.chikku.platform.cards.AndroidCardsTargetValidator
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** Synthetic native ShortcutInfo and Binder seam; no real shortcut provider or biometric claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], shadows = [AuditShortcutMetadataPrivacyTest.Shortcuts::class])
class AuditShortcutMetadataPrivacyTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val serial get() = context.getSystemService(
        UserManager::class.java,
    ).getSerialNumberForUser(Process.myUserHandle())
    private val key get() = AppKey(context.packageName, "SyntheticActivity", serial)
    private val featured get() = CardFeatured.Shortcut(key.packageName, "chat", serial)

    @Before fun fixture() {
        val users = context.getSystemService(UserManager::class.java)
        shadowOf(users).setUserUnlocked(true)
        // The handle-taking API uses a separate per-user state in Robolectric.
        shadowOf(
            users,
        ).setUserState(
            Process.myUserHandle(),
            org.robolectric.shadows.ShadowUserManager.UserState.STATE_RUNNING_UNLOCKED,
        )
        assertThat(users.isUserUnlocked(Process.myUserHandle())).isTrue()
        Shortcuts.reads = 0
        Shortcuts.icons = 0
        Shortcuts.afterQuery = {}
        Shortcuts.fixture = ShortcutInfo.Builder(context, "chat")
            .setShortLabel("Private conversation")
            .setIntent(Intent(Intent.ACTION_VIEW)).build()
    }

    @Test fun lockedPickerAndCardsNeverReadShortcutMetadataOrArtwork() = runTest {
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(LockTarget(key)))), backgroundScope) { 0L }
        lock.awaitLoaded()
        assertThat(LauncherAppsShortcuts(context, lock).shortcutsFor(key)).isEqualTo(ShortcutListResult.Failed)
        val result = AndroidCardsTargetValidator(context, lock).validate(
            listOf(AutomationCard("card", "Card", featured = featured)),
            listOf(LauncherApp(key, "App", ProfileType.PERSONAL)),
            emptySet(),
            emptyList(),
        )
        assertThat(result.shortcutLabels).isEmpty()
        assertThat(ShortcutIconLoader(context, lock).load(key.packageName, "chat", serial)).isNull()
        assertThat(Shortcuts.reads).isEqualTo(0)
        assertThat(Shortcuts.icons).isEqualTo(0)
    }

    @Test fun pickerAndCardsDiscardLabelWhenLockChangesInsideNativeRead() = runTest {
        var allowed = true
        val policy = ShortcutMetadataPolicy { _, _ -> allowed }
        Shortcuts.afterQuery = { allowed = false }
        val reader = LauncherAppsShortcuts(context, AppLockGuard.NONE, policy)
        assertThat(reader.shortcutsFor(key)).isEqualTo(ShortcutListResult.Failed)
        allowed = true
        val result = AndroidCardsTargetValidator(context, policy).validate(
            listOf(AutomationCard("card", "Card", featured = featured)),
            listOf(LauncherApp(key, "App", ProfileType.PERSONAL)),
            emptySet(),
            emptyList(),
        )
        assertThat(result.shortcutLabels).isEmpty()
        allowed = true
        assertThat(ShortcutIconLoader(context, policy).load(key.packageName, "chat", serial)).isNull()
        assertThat(Shortcuts.icons).isEqualTo(0)
        assertThat(Shortcuts.reads).isEqualTo(3)
    }

    @Test fun cachedShortcutIconIsServedOnlyWhileThePolicyAllowsIt() = runTest {
        val icons = CachingIconProvider(
            kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler), 1 shl 20,
            { _: AppKey -> null }, { android.graphics.drawable.ColorDrawable() },
        )
        icons.shortcutIcon(ShortcutIconKey(key.packageName, "chat", serial)) {
            android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE)
        }
        var allowed = true
        val loader = ShortcutIconLoader(context, { _, _ -> allowed }, icons)
        assertThat(loader.load(key.packageName, "chat", serial)).isNotNull()
        assertThat(Shortcuts.reads).isEqualTo(0) // served from the cache, no shortcut query
        allowed = false
        assertThat(loader.load(key.packageName, "chat", serial)).isNull()
        assertThat(Shortcuts.reads).isEqualTo(0)
    }

    @Test fun explicitReadablePolicyReturnsNativeShortcutLabel() {
        val result = LauncherAppsShortcuts(
            context,
            AppLockGuard.NONE,
            ShortcutMetadataPolicy { _, _ ->
                true
            },
        ).shortcutsFor(key)
        assertThat((result as ShortcutListResult.Listed).shortcuts.single().label).isEqualTo("Private conversation")
    }

    @Test fun launchGrantNeverRevealsMetadataAndMetadataReadsNeverConsumeIt() = runTest {
        val target = LockTarget(key)
        val lock = AppLockManager(InMemoryAppLockStore(StoredAppLock(setOf(target))), backgroundScope) { 0L }
        lock.awaitLoaded()
        lock.grant(target)
        repeat(3) { assertThat(lock.mayRead(key.packageName, serial)).isFalse() }
        assertThat(lock.admit(key.packageName, serial)).isTrue()
        assertThat(lock.admit(key.packageName, serial)).isFalse()
        assertThat(lock.mayRead(key.packageName, serial + 1)).isTrue()
    }

    @Test fun loadingAndUnreadablePoliciesFailClosedEvenAfterGrant() = runTest {
        val lock = AppLockManager(InMemoryAppLockStore(failReads = true), backgroundScope) { 0L }
        assertThat(lock.mayRead(key.packageName, serial)).isFalse()
        lock.awaitLoaded()
        lock.grant(LockTarget(key))
        assertThat(lock.mayRead(key.packageName, serial)).isFalse()
        assertThat(lock.admit(key.packageName, serial)).isFalse()
    }

    @Implements(LauncherApps::class)
    class Shortcuts {
        @Implementation fun hasShortcutHostPermission() = true

        @Implementation fun isActivityEnabled(component: android.content.ComponentName, user: UserHandle) = true

        @Implementation fun getShortcuts(query: LauncherApps.ShortcutQuery, user: UserHandle): List<ShortcutInfo> {
            reads++
            afterQuery()
            return listOf(fixture)
        }

        @Implementation fun getShortcutIconDrawable(
            info: ShortcutInfo,
            density: Int,
        ): Drawable? {
            icons++
            return null
        }
        companion object {
            lateinit var fixture: ShortcutInfo
            var reads = 0
            var icons = 0
            var afterQuery: () -> Unit = {}
        }
    }
}
