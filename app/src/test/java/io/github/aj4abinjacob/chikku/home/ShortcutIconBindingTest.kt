package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShortcutIconBindingTest {
    @Test fun cancelledShortcutIconCannotRepopulateAReboundView() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val late = CompletableDeferred<android.graphics.drawable.Drawable>()
        val provider = object : IconProvider {
            override suspend fun icon(key: AppKey) = ColorDrawable(Color.BLUE)
            override fun invalidate(packageName: String) = Unit
        }
        val binder = IconBinder(context, backgroundScope, provider,
            shortcutIcons = { _, _, _, _, _ -> late.await() })
        val view = AppIconView(context, AppIconView.Style.WORKSPACE)
        binder.bindShortcut(view, ItemContent.Shortcut("com.browser", "id", 0, "Site", Availability.AVAILABLE))
        runCurrent()
        binder.bindPlaceholder(view, "Unavailable", "Unavailable", R.drawable.ic_app_unavailable)
        val safeIcon = view.icon
        late.complete(ColorDrawable(Color.RED))
        runCurrent()
        assertThat(view.icon).isSameInstanceAs(safeIcon)
        assertThat(view.contentDescription.toString()).isEqualTo("Unavailable")
    }
}
