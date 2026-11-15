package io.github.aj4abinjacob.chikku.settings

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.MutableState
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.components.rememberPermissionGranted
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * A permission granted from the system dialog or Android settings must redraw settings pages even
 * when no saved setting changes (e.g. contacts search already on, access granted later).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class PermissionStateTest {
    @Test
    fun grantWhileAwayIsSeenOnResumeWithoutAnyOtherRedraw() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity).denyPermissions(Manifest.permission.READ_CONTACTS)
        // Compose state: any change to it redraws the pages that read it.
        var state: MutableState<Boolean>? = null
        activity.setContent { state = rememberPermissionGranted(Manifest.permission.READ_CONTACTS) }
        ShadowLooper.idleMainLooper()
        assertThat(state!!.value).isFalse()

        // The permission dialog pauses the screen; the grant lands before it resumes.
        controller.pause()
        shadowOf(activity).grantPermissions(Manifest.permission.READ_CONTACTS)
        controller.resume()
        ShadowLooper.idleMainLooper()
        assertThat(state!!.value).isTrue()

        controller.pause()
        shadowOf(activity).denyPermissions(Manifest.permission.READ_CONTACTS)
        controller.resume()
        ShadowLooper.idleMainLooper()
        assertThat(state!!.value).isFalse()
    }
}
