package io.github.aj4abinjacob.chikku.settings

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.settings.previews.ContactCardLivePreview
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** The Contacts settings preview draws the chosen buttons, in order, for a synthetic contact. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ContactCardPreviewTest {

    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private fun descriptions(buttons: List<String>): List<String> {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { ContactCardLivePreview(LauncherSettings(search = SearchSettings(contactsEnabled = true, contactCardButtons = buttons))) }
        ShadowLooper.idleMainLooper()
        val root = (activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        return root.semanticsOwner.rootSemanticsNode.flatten().mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString() }
    }

    @Test
    fun defaultPreviewShowsCallMessageWhatsApp() {
        assertThat(descriptions(SearchSettings().contactCardButtons))
            .containsExactly("Contact card preview with buttons: Call, Message, WhatsApp")
    }

    @Test
    fun previewFollowsSwappedAndReorderedButtonsAndSkipsUnknownApps() {
        assertThat(descriptions(listOf("signal:message", "newer_app:message", "phone:voice_call")))
            .containsExactly("Contact card preview with buttons: Signal, Call")
    }
}
