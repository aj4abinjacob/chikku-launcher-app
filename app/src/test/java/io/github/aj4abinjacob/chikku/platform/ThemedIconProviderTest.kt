package io.github.aj4abinjacob.chikku.platform

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.platform.icons.IconOverride
import io.github.aj4abinjacob.chikku.platform.icons.IconRequest
import io.github.aj4abinjacob.chikku.platform.icons.IconStyle
import io.github.aj4abinjacob.chikku.platform.icons.IconTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemedIconProviderTest {

    private val a = AppKey("com.a", "com.a.Main", 0)
    private val b = AppKey("com.b", "com.b.Main", 0)

    private class RecordingLoader : (IconRequest) -> Drawable? {
        val requests = mutableListOf<IconRequest>()
        override fun invoke(request: IconRequest): Drawable? {
            requests += request
            return ColorDrawable(Color.RED)
        }
    }

    private val changedPackages = mutableListOf<String>()

    private fun TestScope.provider(loader: RecordingLoader, following: Boolean = false) = CachingIconProvider(
        UnconfinedTestDispatcher(testScheduler), 1 shl 20, loader, { ColorDrawable(Color.GRAY) },
        onPackChanged = { changedPackages += it }, following = following,
    )

    @Test
    fun cacheKeyIncludesStyleAndThemeChangeReloadsEverything() = runTest {
        val loader = RecordingLoader()
        val provider = provider(loader)
        provider.icon(a)
        provider.icon(a)
        assertThat(loader.requests).hasSize(1)
        assertThat(loader.requests.single().style).isEqualTo(IconStyle.DEFAULT)

        val revision = provider.iconRevision.value
        val circle = IconStyle(shape = IconShape.CIRCLE)
        provider.setTheme(IconTheme(circle))
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1)
        assertThat(provider.cachedCount()).isEqualTo(0)

        provider.icon(a)
        assertThat(loader.requests.last()).isEqualTo(IconRequest(a, circle, null))

        // Same theme again: nothing reloads.
        provider.setTheme(IconTheme(circle))
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1)
    }

    @Test
    fun perAppOverrideReachesTheLoaderForThatAppOnly() = runTest {
        val loader = RecordingLoader()
        val provider = provider(loader)
        val override = IconOverride("pack.x", "a_icon")
        provider.setTheme(IconTheme(overrides = mapOf(a to override)))
        provider.icon(a)
        provider.icon(b)
        assertThat(loader.requests.map { it.override }).containsExactly(override, null).inOrder()
    }

    @Test
    fun changingAPackInUseDropsAllIconsOtherPackagesOnlyTheirOwn() = runTest {
        val loader = RecordingLoader()
        val provider = provider(loader)
        provider.setTheme(IconTheme(IconStyle(iconPack = "pack.x")))
        provider.icon(a)
        provider.icon(b)
        val revision = provider.iconRevision.value

        provider.invalidate("com.a")
        assertThat(provider.cachedCount()).isEqualTo(1)
        assertThat(provider.iconRevision.value).isEqualTo(revision)

        provider.invalidate("pack.x")
        assertThat(provider.cachedCount()).isEqualTo(0)
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1)
        assertThat(changedPackages).containsExactly("com.a", "pack.x").inOrder()
    }

    @Test
    fun followingProviderWaitsForStoredThemeAndTracksSettings() = runTest {
        val loader = RecordingLoader()
        val provider = provider(loader, following = true)
        val settings = MutableStateFlow(LauncherSettings(lookFeel = LookFeelSettings(iconPackPackage = "pack.y")))
        val customizations = MutableStateFlow<Map<AppKey, AppCustomization>>(emptyMap())
        provider.follow(backgroundScope, settings, customizations)

        provider.icon(a)
        assertThat(loader.requests.single().style.iconPack).isEqualTo("pack.y")

        customizations.value = mapOf(a to AppCustomization(a, iconPackPackage = "pack.z", iconDrawableName = "n"))
        testScheduler.runCurrent() // backgroundScope work is not covered by advanceUntilIdle
        provider.icon(a)
        assertThat(loader.requests.last().override).isEqualTo(IconOverride("pack.z", "n"))
    }

    @Test
    fun packageInvalidationIsAnnouncedWithoutBumpingTheGlobalRevision() = runTest {
        val provider = provider(RecordingLoader())
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { provider.packageInvalidations.collect { seen += it } }
        val revision = provider.iconRevision.value
        provider.invalidate("com.a")
        assertThat(seen).containsExactly("com.a")
        assertThat(provider.iconRevision.value).isEqualTo(revision)
    }

    @Test
    fun failedLoadIsReportedAsFallbackAndHealthyLoadIsNot() = runTest {
        var ok = false
        val provider = CachingIconProvider(UnconfinedTestDispatcher(testScheduler), 16,
            { _: IconRequest -> if (ok) ColorDrawable(Color.RED) else null }, { ColorDrawable(Color.GRAY) })
        assertThat(provider.resolvedIcon(a).isFallback).isTrue()
        ok = true
        assertThat(provider.resolvedIcon(a).isFallback).isFalse()
    }

    @Test
    fun firstRealThemeAfterADefaultedLoadCountsAsAChange() = runTest {
        val provider = provider(RecordingLoader(), following = true)
        provider.icon(a) // the stored theme never arrives within the wait: system icons
        val revision = provider.iconRevision.value
        provider.setTheme(IconTheme(IconStyle(shape = IconShape.CIRCLE)))
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1)
    }

    @Test
    fun firstThemeWithoutADefaultedLoadDoesNotReload() = runTest {
        val provider = provider(RecordingLoader(), following = true)
        val revision = provider.iconRevision.value
        provider.setTheme(IconTheme(IconStyle(shape = IconShape.CIRCLE)))
        assertThat(provider.iconRevision.value).isEqualTo(revision)
    }

    @Test
    fun cancelledRequestsFinishedLoadIsStillCached() = runTest {
        lateinit var requester: kotlinx.coroutines.Job
        val provider = CachingIconProvider(UnconfinedTestDispatcher(testScheduler), 4 * 1024 * 1024,
            { _: IconRequest -> requester.cancel(); ColorDrawable(Color.RED) }, { ColorDrawable(Color.GRAY) })
        requester = launch(UnconfinedTestDispatcher(testScheduler), start = kotlinx.coroutines.CoroutineStart.LAZY) { provider.icon(a) }
        requester.start()
        requester.join()
        assertThat(requester.isCancelled).isTrue()
        assertThat(provider.cachedCount()).isEqualTo(1)
    }

    @Test
    fun cancelledRequestsLoadThatRacedAnInvalidationIsNotCached() = runTest {
        lateinit var requester: kotlinx.coroutines.Job
        lateinit var provider: CachingIconProvider
        provider = CachingIconProvider(UnconfinedTestDispatcher(testScheduler), 4 * 1024 * 1024,
            { _: IconRequest -> requester.cancel(); provider.invalidate("com.a"); ColorDrawable(Color.RED) },
            { ColorDrawable(Color.GRAY) })
        requester = launch(UnconfinedTestDispatcher(testScheduler), start = kotlinx.coroutines.CoroutineStart.LAZY) { provider.icon(a) }
        requester.start()
        requester.join()
        assertThat(provider.cachedCount()).isEqualTo(0)
    }

    @Test
    fun overrideOnlyThemeChangeInvalidatesJustTheAffectedApps() = runTest {
        val loader = RecordingLoader()
        val provider = provider(loader)
        val seen = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { provider.packageInvalidations.collect { seen += it } }
        provider.icon(a)
        provider.icon(b)
        val revision = provider.iconRevision.value

        val override = IconOverride("pack.x", "a_icon")
        provider.setTheme(IconTheme(overrides = mapOf(a to override)))
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1) // Compose consumers reload
        assertThat(provider.cachedCount()).isEqualTo(1)
        assertThat(seen).containsExactly("com.a")
        assertThat(changedPackages).isEmpty()

        loader.requests.clear()
        provider.icon(a)
        provider.icon(b)
        assertThat(loader.requests.map { it.key }).containsExactly(a)
        assertThat(loader.requests.single().override).isEqualTo(override)

        // Removing it again reloads that app only; a style change still drops everything.
        provider.setTheme(IconTheme())
        assertThat(seen).containsExactly("com.a", "com.a")
        assertThat(provider.iconRevision.value).isEqualTo(revision + 2)
        provider.setTheme(IconTheme(IconStyle(shape = IconShape.CIRCLE)))
        assertThat(provider.iconRevision.value).isEqualTo(revision + 3)
        assertThat(provider.cachedCount()).isEqualTo(0)
    }
}
