package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchAnimationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun iconBoundsProduceRevealOptions() {
        assertThat(launchAnimationOptions(context, Rect(100, 200, 260, 360))).isNotNull()
    }

    @Test
    fun missingOrEmptyBoundsLeaveTheSystemDefault() {
        assertThat(launchAnimationOptions(context, null)).isNull()
        assertThat(launchAnimationOptions(context, Rect(10, 10, 10, 10))).isNull()
    }
}
