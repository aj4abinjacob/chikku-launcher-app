package io.github.aj4abinjacob.chikku.settings.iconpicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The icon-pack lists refresh from this signal when an app is installed or removed while open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PackageChangeSignalTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun broadcast(action: String) {
        context.sendBroadcast(Intent(action, Uri.parse("package:example.pack")))
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    @Test fun installUpdateAndRemovalSignalUntilDisposed() {
        var changes = 0
        val handle = onPackagesChanged(context) { changes++ }
        broadcast(Intent.ACTION_PACKAGE_ADDED)
        broadcast(Intent.ACTION_PACKAGE_REPLACED)
        broadcast(Intent.ACTION_PACKAGE_REMOVED)
        assertThat(changes).isEqualTo(3)
        handle.dispose()
        broadcast(Intent.ACTION_PACKAGE_ADDED)
        assertThat(changes).isEqualTo(3)
    }
}
