package io.github.aj4abinjacob.chikku.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.LaunchResult.Reason
import io.github.aj4abinjacob.chikku.platform.icons.IconRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlatformAdaptersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val unknownSerial = 987_654L

    private fun key(pkg: String, serial: Long = 0L) = AppKey(pkg, "$pkg.Main", serial)

    // --- LauncherAppsLauncher -------------------------------------------------------------

    @Test
    fun launcherReturnsUnavailableForUnknownProfile() {
        val launcher = LauncherAppsLauncher(context, AppLockGuard.NONE)
        assertThat(launcher.launch(key("com.a", unknownSerial), null))
            .isEqualTo(LaunchResult.Unavailable(Reason.PROFILE_UNAVAILABLE))
        assertThat(launcher.openAppDetails(key("com.a", unknownSerial), null))
            .isEqualTo(LaunchResult.Unavailable(Reason.PROFILE_UNAVAILABLE))
    }

    @Test
    fun lockedAppsAreRefusedBeforeAnythingStarts() {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        val asked = mutableListOf<Pair<String, Long>>()
        val guard = AppLockGuard { pkg, user -> asked += pkg to user; false }
        val launcher = LauncherAppsLauncher(context, guard)
        assertThat(launcher.launch(key("com.locked", serial), null)).isEqualTo(LaunchResult.Locked("com.locked", serial))
        // App info is locked too: the system App info screen has an "Open" button.
        assertThat(launcher.openAppDetails(key("com.locked", serial), null)).isEqualTo(LaunchResult.Locked("com.locked", serial))
        assertThat(LauncherAppsShortcuts(context, guard).start("com.locked", "chat", serial, null))
            .isEqualTo(LaunchResult.Locked("com.locked", serial))
        assertThat(asked).containsExactly("com.locked" to serial, "com.locked" to serial, "com.locked" to serial)
    }

    @Test
    fun launcherNeverThrowsForPersonalProfile() {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())
        // Robolectric does not implement startMainActivity; the result must still be a value.
        val result = LauncherAppsLauncher(context, AppLockGuard.NONE).launch(key("com.missing", serial), null)
        assertThat(result).isNotNull()
    }

    // --- CachingIconProvider --------------------------------------------------------------

    private class CountingLoader(var result: (AppKey) -> Drawable? = { ColorDrawable(Color.RED) }) : (AppKey) -> Drawable? {
        val calls = mutableListOf<AppKey>()

        override fun invoke(key: AppKey): Drawable? {
            calls += key
            return result(key)
        }
    }

    private val fallback = ColorDrawable(Color.GRAY)

    /** Bytes of one cached test icon (48 px square, ARGB_8888). */
    private val rasterPx = kotlin.math.ceil(CachingIconProvider.DEFAULT_TEST_ICON_PX * CachingIconProvider.MIN_RASTER_SCALE.toDouble()).toInt()
    private val iconBytes = rasterPx * rasterPx * 4

    private fun TestScope.iconProvider(loader: CountingLoader, maxIcons: Int = 16) =
        CachingIconProvider(UnconfinedTestDispatcher(testScheduler), maxIcons * iconBytes, loader) { fallback }

    @Test
    fun iconsAreCachedPerKey() = runTest {
        val loader = CountingLoader()
        val provider = iconProvider(loader)
        provider.icon(key("com.a"))
        provider.icon(key("com.a"))
        provider.icon(key("com.a", 10))
        assertThat(loader.calls).containsExactly(key("com.a"), key("com.a", 10))
    }

    @Test
    fun missingOrThrowingIconsFallBackAndAreNotCached() = runTest {
        val loader = CountingLoader { if (it.packageName == "com.null") null else error("bad icon") }
        val provider = iconProvider(loader)
        assertThat(provider.icon(key("com.null"))).isSameInstanceAs(fallback)
        assertThat(provider.icon(key("com.throw"))).isSameInstanceAs(fallback)
        assertThat(provider.cachedCount()).isEqualTo(0)
    }

    @Test
    fun invalidateDropsEveryProfileOfThePackageOnly() = runTest {
        val loader = CountingLoader()
        val provider = iconProvider(loader)
        provider.icon(key("com.a", 0))
        provider.icon(key("com.a", 10))
        provider.icon(key("com.b", 0))
        provider.invalidate("com.a")
        assertThat(provider.cachedCount()).isEqualTo(1)
        loader.calls.clear()
        provider.icon(key("com.a", 10))
        provider.icon(key("com.b", 0))
        assertThat(loader.calls).containsExactly(key("com.a", 10))
    }

    @Test
    fun loadRacingWithInvalidateIsNotCached() = runTest {
        lateinit var provider: CachingIconProvider
        val loader = CountingLoader { provider.invalidate("com.a"); ColorDrawable(Color.BLUE) }
        provider = iconProvider(loader)
        provider.icon(key("com.a"))
        assertThat(provider.cachedCount()).isEqualTo(0)
    }

    @Test
    fun cacheIsBounded() = runTest {
        val provider = iconProvider(CountingLoader(), maxIcons = 2)
        repeat(5) { provider.icon(key("com.p$it")) }
        assertThat(provider.cachedCount()).isEqualTo(2)
    }

    @Test
    fun monochromeGlyphsComeFromTheirOwnLoaderAndFailuresAreNull() = runTest {
        val glyph = ColorDrawable(Color.WHITE)
        val provider = CachingIconProvider(
            UnconfinedTestDispatcher(testScheduler), 16 * iconBytes, { _: IconRequest -> fallback }, { fallback },
            monochromeLoader = { if (it.packageName == "com.glyph") glyph else error("bad icon") },
        )
        assertThat(provider.monochromeIcon(key("com.glyph"))).isSameInstanceAs(glyph)
        assertThat(provider.monochromeIcon(key("com.throw"))).isNull()
        assertThat(iconProvider(CountingLoader()).monochromeIcon(key("com.glyph"))).isNull()
    }

    @Test
    fun productionIconProviderFallsBackForUnknownProfile() = runTest {
        val provider = CachingIconProvider(context, StandardTestDispatcher(testScheduler))
        assertThat(provider.icon(key("com.a", unknownSerial))).isNotNull()
        assertThat(provider.cachedCount()).isEqualTo(0)
    }

    // --- RoleManagerHomeRoleController ----------------------------------------------------

    @Test
    @Suppress("DEPRECATION") // Robolectric 4.17 deprecates these setters without a role-availability replacement.
    fun homeRoleStateAndRequestIntent() {
        val roleManager = context.getSystemService(RoleManager::class.java)
        val controller = RoleManagerHomeRoleController(context)

        assertThat(controller.isDefaultHome()).isFalse()
        assertThat(controller.createRequestIntent()).isNull()

        shadowOf(roleManager).addAvailableRole(RoleManager.ROLE_HOME)
        assertThat(controller.isDefaultHome()).isFalse()
        assertThat(controller.createRequestIntent()).isNotNull()

        shadowOf(roleManager).addHeldRole(RoleManager.ROLE_HOME)
        assertThat(controller.isDefaultHome()).isTrue()
        assertThat(controller.createRequestIntent()).isNull()
        assertThat(controller.homeSettingsIntent().action).isEqualTo(Settings.ACTION_HOME_SETTINGS)
    }

    // --- DefaultDockAppsResolver ----------------------------------------------------------

    @Test
    fun dockResolverDeduplicatesSkipsFailuresAndCapsAtFour() = runTest {
        val handlers = mapOf(
            Intent.ACTION_DIAL to "com.phone",
            Intent.ACTION_SENDTO to "com.phone", // same app handles SMS: de-duplicated
            Intent.ACTION_VIEW to "com.boom", // lookup throws: skipped
        )
        val resolver = DefaultDockAppsResolver(
            UnconfinedTestDispatcher(testScheduler),
            defaultHandlerFor = { handlers[it.action] },
            launcherActivityFor = { pkg -> if (pkg == "com.boom") error("gone") else key(pkg) },
        )
        assertThat(resolver.resolve()).containsExactly(key("com.phone"))

        val many = DefaultDockAppsResolver(
            UnconfinedTestDispatcher(testScheduler),
            defaultHandlerFor = { it.action + ".app" },
            launcherActivityFor = { key(it) },
        )
        assertThat(many.resolve()).hasSize(DefaultDockAppsResolver.MAX_DOCK_APPS)
    }

    @Test
    fun resolverOrChooserIsNotTreatedAsDefault() {
        assertThat(defaultHandlerPackage("android", setOf("com.a", "com.b"))).isNull()
        assertThat(defaultHandlerPackage(null, setOf("com.a"))).isNull()
        assertThat(defaultHandlerPackage("com.a", setOf("com.a", "com.b"))).isEqualTo("com.a")
    }

    @Test
    fun productionDockResolverNeverThrows() = runTest {
        val resolver = DefaultDockAppsResolver(context, StandardTestDispatcher(testScheduler))
        assertThat(resolver.resolve()).isEmpty()
    }
}
