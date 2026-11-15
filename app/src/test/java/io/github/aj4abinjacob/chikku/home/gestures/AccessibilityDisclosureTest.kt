package io.github.aj4abinjacob.chikku.home.gestures

import android.provider.Settings
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class AccessibilityDisclosureTest {
    private fun host(): ComponentActivity =
        Robolectric.buildActivity(ComponentActivity::class.java).setup().get().apply {
            // ComponentActivity installs the view-tree owners through setContentView, as the
            // real Compose SettingsActivity does before presenting a Chikku prompt.
            setContentView(FrameLayout(this))
        }

    @Test
    fun showingAndRefusingDisclosureDoesNotOpenSystemSettings() {
        val activity = host()
        val prompts = ChikkuPrompts(activity)
        prompts.showAccessibilityExplanation(activity)
        assertThat(prompts.isShowing).isTrue()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        prompts.dismiss()
        assertThat(prompts.isShowing).isFalse()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        activity.finish()
    }

    @Test
    fun onlyAffirmativeActionOpensSystemSettingsAndDisclosureNamesItsScope() {
        val activity = host()
        val disclosure = accessibilityDisclosure(activity)
        assertThat(disclosure.message).isEqualTo(activity.getString(R.string.prompt_accessibility_message))
        assertThat(disclosure.message).contains("stores and sends nothing")
        assertThat(disclosure.dismissLabel).isEqualTo(activity.getString(R.string.prompt_not_now))
        disclosure.onDismiss()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        disclosure.primary.onClick()
        assertThat(shadowOf(activity).nextStartedActivity.action).isEqualTo(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        activity.finish()
    }
}
