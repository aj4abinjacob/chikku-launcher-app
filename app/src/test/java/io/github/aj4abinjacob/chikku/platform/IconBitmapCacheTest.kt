package io.github.aj4abinjacob.chikku.platform

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.platform.icons.IconBitmapDrawable
import io.github.aj4abinjacob.chikku.platform.icons.IconRequest
import io.github.aj4abinjacob.chikku.platform.icons.IconTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The icon cache keeps one immutable bitmap per icon, bounded in bytes, shared by every view. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconBitmapCacheTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    /** Raster size at scale 1: the base size lifted to the 64dp header minimum. */
    private val size = kotlin.math.ceil(CachingIconProvider.DEFAULT_TEST_ICON_PX * CachingIconProvider.MIN_RASTER_SCALE.toDouble()).toInt()
    private val iconBytes = size * size * 4

    private val loads = mutableListOf<AppKey>()
    private val fallback = ColorDrawable(Color.GRAY)

    private fun key(pkg: String, serial: Long = 0) = AppKey(pkg, "$pkg.Main", serial)

    private fun TestScope.provider(maxIcons: Int = 16, loader: (AppKey) -> Drawable? = { ColorDrawable(Color.RED) }) =
        CachingIconProvider(UnconfinedTestDispatcher(testScheduler), maxIcons * iconBytes,
            { request: IconRequest -> loads += request.key; loader(request.key) }, { fallback })

    private fun Drawable.bitmap(): Bitmap = (this as IconBitmapDrawable).bitmap

    @Test
    fun everyCallerGetsItsOwnDrawableOverOneSharedImmutableBitmap() = runTest {
        val provider = provider()
        val first = provider.icon(key("com.a"))
        val second = provider.icon(key("com.a"))

        assertThat(first).isNotSameInstanceAs(second)
        assertThat(first.bitmap()).isSameInstanceAs(second.bitmap())
        assertThat(first.bitmap().isMutable).isFalse()
        assertThat(first.bitmap().config).isEqualTo(Bitmap.Config.ARGB_8888)
        assertThat(first.bitmap().width).isEqualTo(size)
        assertThat(loads).hasSize(1)

        // Per-view state stays per view: bounds and alpha do not leak into the other instance.
        first.setBounds(0, 0, 10, 10)
        first.alpha = 40
        assertThat(second.bounds.isEmpty).isTrue()
        assertThat(second.alpha).isEqualTo(255)
        assertThat(first.constantState?.newDrawable()?.bitmap()).isSameInstanceAs(first.bitmap())
    }

    @Test
    fun cacheIsBoundedInBytesAndEvictsLeastRecentlyUsed() = runTest {
        val provider = provider(maxIcons = 3)
        provider.icon(key("com.a"))
        provider.icon(key("com.b"))
        provider.icon(key("com.c"))
        provider.icon(key("com.a")) // a is now the most recently used
        provider.icon(key("com.d")) // evicts b
        assertThat(provider.cachedCount()).isEqualTo(3)
        assertThat(provider.cachedBytes()).isEqualTo(3 * iconBytes)

        loads.clear()
        provider.icon(key("com.a"))
        provider.icon(key("com.b"))
        assertThat(loads).containsExactly(key("com.b"))
    }

    @Test
    fun trimMemoryKeepsIconsWhenHiddenHalvesWhenLowAndEmptiesInTheBackground() = runTest {
        val provider = provider()
        repeat(8) { provider.icon(key("com.p$it")) }

        @Suppress("DEPRECATION")
        provider.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        assertThat(provider.cachedCount()).isEqualTo(8)

        // Every app launch from HOME hides the UI; the icons must survive it for the return.
        provider.trimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
        assertThat(provider.cachedCount()).isEqualTo(8)

        @Suppress("DEPRECATION")
        provider.trimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertThat(provider.cachedCount()).isEqualTo(4)

        val revision = provider.iconRevision.value
        provider.trimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        assertThat(provider.cachedCount()).isEqualTo(0)
        assertThat(provider.cachedBytes()).isEqualTo(0)
        // Bound views keep drawing their bitmaps, so trimming does not make them reload.
        assertThat(provider.iconRevision.value).isEqualTo(revision)
    }

    @Test
    fun largerIconScaleReRasterisesAtTheNewSizeAndReloadsViews() = runTest {
        val provider = provider()
        assertThat(provider.icon(key("com.a")).bitmap().width).isEqualTo(size)
        val revision = provider.iconRevision.value

        provider.setTheme(IconTheme.DEFAULT, scale = 1.5f)
        assertThat(provider.iconRevision.value).isEqualTo(revision + 1)
        assertThat(provider.cachedCount()).isEqualTo(0)
        assertThat(provider.icon(key("com.a")).bitmap().width).isEqualTo(72)

        provider.setBaseIconSize(40)
        assertThat(provider.icon(key("com.a")).bitmap().width).isEqualTo(60)
    }

    @Test
    fun largestIconScaleCoversEverySurface() {
        val settings = LauncherSettings(dock = DockSettings(iconScale = 1.3f))
        assertThat(CachingIconProvider.largestIconScale(settings)).isEqualTo(1.3f)
        assertThat(CachingIconProvider.largestIconScale(LauncherSettings())).isEqualTo(1f)
    }

    @Test
    fun shortcutIconsShareTheCacheAndAreDroppedWithTheirPackageOrProfile() = runTest {
        val provider = provider()
        var reads = 0
        val read: suspend () -> Drawable? = { reads++; ColorDrawable(Color.BLUE) }
        val chat = ShortcutIconKey("com.a", "chat", 0)
        val work = ShortcutIconKey("com.a", "chat", 10)

        val first = provider.shortcutIcon(chat, read)!!
        val second = provider.shortcutIcon(chat, read)!!
        provider.shortcutIcon(work, read)
        assertThat(reads).isEqualTo(2)
        assertThat(first.bitmap()).isSameInstanceAs(second.bitmap())

        provider.invalidateShortcuts("com.a", 0)
        provider.shortcutIcon(chat, read)
        provider.shortcutIcon(work, read)
        assertThat(reads).isEqualTo(3)

        provider.icon(key("com.a"))
        provider.invalidate("com.a")
        assertThat(provider.cachedCount()).isEqualTo(0)

        // A missing shortcut is not cached, so it can appear later.
        assertThat(provider.shortcutIcon(ShortcutIconKey("com.b", "gone", 0)) { null }).isNull()
        assertThat(provider.cachedCount()).isEqualTo(0)
    }

    @Test
    fun calendarDayAndFallbackKeepTheirSemantics() = runTest {
        val provider = provider { if (it.packageName == "com.missing") null else ColorDrawable(Color.RED) }
        assertThat(provider.icon(key("com.missing"))).isSameInstanceAs(fallback)
        assertThat(provider.cachedCount()).isEqualTo(0)
        provider.icon(key("com.a"))
        val revision = provider.iconRevision.value
        provider.refreshCalendarDate() // same day: nothing changes
        assertThat(provider.iconRevision.value).isEqualTo(revision)
        assertThat(provider.cachedCount()).isEqualTo(1)
    }

    @Test
    fun viewsOfTheSameAppShareOneBitmap() = runTest {
        val provider = provider()
        val binder = IconBinder(context, backgroundScope, provider)
        val views = List(3) { AppIconView(context, AppIconView.Style.WORKSPACE) }
        views.forEach { binder.bindApp(it, key("com.a"), "Sample", false, Availability.AVAILABLE) }
        runCurrent()

        val bitmaps = views.map { it.icon!!.bitmap() }.toSet()
        assertThat(bitmaps).hasSize(1)
        assertThat(views.map { it.icon }.toSet()).hasSize(3)
        assertThat(loads).hasSize(1)
    }
}
