package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.home.folder.FolderIconBinder
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.ResolvedIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Icons refresh in place for an app update, a fallback load, an app-lock change and view reuse. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class IconRefreshTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val a = AppKey("com.a", "com.a.Main", 0)
    private val b = AppKey("com.b", "com.b.Main", 0)

    private class Provider : IconProvider {
        val colors = HashMap<String, Int>()
        var fallback = false
        val loads = mutableListOf<String>()
        val changes = MutableSharedFlow<String>(extraBufferCapacity = 8)
        override val packageInvalidations: SharedFlow<String> = changes
        override suspend fun icon(key: AppKey): Drawable = resolvedIcon(key).drawable
        override suspend fun resolvedIcon(key: AppKey): ResolvedIcon {
            loads += key.packageName
            return ResolvedIcon(ColorDrawable(colors[key.packageName] ?: Color.GRAY), fallback)
        }
        override fun invalidate(packageName: String) = Unit
    }

    private fun colorOf(view: AppIconView) = (view.icon as ColorDrawable).color

    private fun folderOf(key: AppKey) =
        ItemContent.Folder("F", listOf(FolderEntryUi("e", ItemContent.App(key, "A", false, Availability.AVAILABLE))))

    @Test fun packageInvalidationReloadsOnlyThatPackagesViews() = runTest {
        val provider = Provider().apply { colors["com.a"] = Color.RED; colors["com.b"] = Color.GREEN }
        val binder = IconBinder(context, backgroundScope, provider)
        val va = AppIconView(context, AppIconView.Style.WORKSPACE)
        val vb = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindApp(va, a, "A", false, Availability.AVAILABLE)
        binder.bindApp(vb, b, "B", false, Availability.AVAILABLE)
        runCurrent()
        assertThat(colorOf(va)).isEqualTo(Color.RED)
        provider.loads.clear()

        provider.colors["com.a"] = Color.BLUE // the update shipped a new icon
        provider.changes.emit("com.a")
        runCurrent()

        assertThat(colorOf(va)).isEqualTo(Color.BLUE)
        assertThat(colorOf(vb)).isEqualTo(Color.GREEN)
        assertThat(provider.loads).containsExactly("com.a")
    }

    @Test fun fallbackIconIsRetriedOnTheNextBindAndWhenTheAppBecomesAvailable() = runTest {
        val provider = Provider().apply { fallback = true }
        val binder = IconBinder(context, backgroundScope, provider)
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindApp(view, a, "A", false, Availability.AVAILABLE)
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.GRAY)

        provider.fallback = false
        provider.colors["com.a"] = Color.RED
        binder.bindApp(view, a, "A", false, Availability.AVAILABLE)
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.RED)

        // UNKNOWN -> AVAILABLE retries too, even though the earlier load did not fall back.
        val other = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindApp(other, b, "B", false, Availability.UNKNOWN)
        runCurrent()
        val loadsBefore = provider.loads.size
        binder.bindApp(other, b, "B", false, Availability.AVAILABLE)
        runCurrent()
        assertThat(provider.loads.size).isGreaterThan(loadsBefore)
    }

    @Test fun shortcutIconFollowsTheAppLockAndRestoresOnUnlock() = runTest {
        var locked = true // cold start: not readable until the lock store loads
        val access = MutableStateFlow(0)
        val binder = IconBinder(context, backgroundScope, Provider(),
            shortcutIcons = { _, _, _, _, _ -> if (locked) null else ColorDrawable(Color.RED) },
            shortcutAccess = access)
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindShortcut(view, ItemContent.Shortcut("com.a", "id", 0, "Chat", Availability.AVAILABLE))
        runCurrent()
        assertThat(view.icon).isNotInstanceOf(ColorDrawable::class.java) // placeholder

        locked = false
        access.value = 1
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.RED)

        locked = true
        access.value = 2
        runCurrent()
        assertThat(view.icon).isNotInstanceOf(ColorDrawable::class.java)
    }

    @Test fun iconThemeReloadKeepsAPinnedShortcutIcon() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val binder = IconBinder(context, backgroundScope, Provider(), shortcutIcons = { _, _, _, _, _ ->
            if (!first) gate.await()
            first = false
            ColorDrawable(Color.RED)
        })
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindShortcut(view, ItemContent.Shortcut("com.a", "id", 0, "Chat", Availability.AVAILABLE))
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.RED)
        binder.reloadIcons()
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.RED) // still the shortcut icon while reloading
        gate.complete(Unit)
    }

    @Test fun appBoundAfterAFolderDoesNotKeepTheFolderSignature() = runTest {
        val provider = Provider().apply { colors["com.a"] = Color.RED }
        val binder = IconBinder(context, backgroundScope, provider)
        val folders = FolderIconBinder(context, backgroundScope, provider)
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        folders.bind(view, folderOf(a), FolderSettings())
        runCurrent()
        assertThat(view.getTag(R.id.folder_icon_signature)).isNotNull()

        binder.bindApp(view, a, "A", false, Availability.AVAILABLE)
        assertThat(view.getTag(R.id.folder_icon_signature)).isNull()
        runCurrent()
        assertThat(colorOf(view)).isEqualTo(Color.RED)

        // Back to the folder: it draws again rather than assuming the old drawing is still there.
        binder.releaseApp(view)
        folders.bind(view, folderOf(a), FolderSettings())
        assertThat(view.icon).isNotInstanceOf(ColorDrawable::class.java)
    }

    @Test fun folderPreviewRedrawsOnlyWhenAPreviewedPackageIsInvalidated() = runTest {
        val provider = Provider().apply { colors["com.a"] = Color.RED }
        val folders = FolderIconBinder(context, backgroundScope, provider)
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        folders.bind(view, folderOf(a), FolderSettings())
        runCurrent()
        val drawn = view.icon
        provider.loads.clear()

        folders.invalidatePackage("com.unrelated")
        folders.bind(view, folderOf(a), FolderSettings())
        runCurrent()
        assertThat(provider.loads).isEmpty()
        assertThat(view.icon).isSameInstanceAs(drawn)

        folders.invalidatePackage("com.a")
        folders.bind(view, folderOf(a), FolderSettings())
        runCurrent()
        assertThat(provider.loads).containsExactly("com.a")
    }
}
