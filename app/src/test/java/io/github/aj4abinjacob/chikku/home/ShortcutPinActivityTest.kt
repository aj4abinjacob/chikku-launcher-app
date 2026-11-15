package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import android.content.pm.LauncherApps
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShortcutPinActivityTest {
    @Test fun missingFrameworkPinTokenIsRejectedBeforeOpeningOrEditingTheWorkspace() {
        val controller = Robolectric.buildActivity(ShortcutPinActivity::class.java,
            Intent(LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT)).create()
        assertThat(controller.get().isFinishing).isTrue()
        controller.destroy()
    }

    @Test fun arbitraryParcelableCannotBecomeAPinRequest() {
        val intent = Intent(LauncherApps.ACTION_CONFIRM_PIN_SHORTCUT)
            .putExtra(LauncherApps.EXTRA_PIN_ITEM_REQUEST, Intent("untrusted"))
        val controller = Robolectric.buildActivity(ShortcutPinActivity::class.java, intent).create()
        assertThat(controller.get().isFinishing).isTrue()
        controller.destroy()
    }
}
