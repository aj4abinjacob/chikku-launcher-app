package io.github.aj4abinjacob.chikku.search

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProvider
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactCardButton
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ContactService
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.search.ProviderEntryPoint
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.IconProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper

/**
 * Lays out the real person card with native text measurement at the required widths and font
 * scales, then checks Compose's merged semantics tree (what TalkBack reads): labels, roles, order,
 * touch-target sizes, overlap and truncation. JVM evidence only; not a device run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonCardLayoutTest {

    private val future = CommunicationProvider(
        "future", "org.example.chat", "Example Chat",
        listOf(ProviderEntryPoint("vnd.android.cursor.item/vnd.org.example.chat", ContactActionType.MESSAGE)),
    )

    private fun service(provider: CommunicationProvider, label: String) =
        ContactService(provider, LauncherApp(AppKey(provider.packageName, "${provider.packageName}.Main", 0), label, ProfileType.PERSONAL))

    private val whatsApp = service(CommunicationProviders.WHATSAPP, "WhatsApp")
    private val signal = service(CommunicationProviders.SIGNAL, "Signal")

    private fun actions(provider: CommunicationProvider, vararg types: ContactActionType) = types.mapIndexed { i, type ->
        ContactChannelAction(provider.id, type, provider.hashCode().toLong() + i, provider.entryPoints.first { it.type == type }.mimeType)
    }

    private val all = arrayOf(ContactActionType.MESSAGE, ContactActionType.VOICE_CALL, ContactActionType.VIDEO_CALL)
    private val contact = ContactHit(
        1, "k", "Abin", "7994135896",
        channelActions = actions(CommunicationProviders.WHATSAPP, *all) + actions(CommunicationProviders.SIGNAL, *all),
    )

    private val icons = object : IconProvider {
        override suspend fun icon(key: AppKey): Drawable = ColorDrawable(AndroidColor.rgb(37, 211, 102))
        override fun invalidate(packageName: String) = Unit
    }

    private class Rendered(val nodes: List<SemanticsNode>, val unmerged: List<SemanticsNode>, val density: Float, val widthPx: Int)

    private fun render(
        widthDp: Int,
        fontScale: Float,
        hit: ContactHit = contact,
        buttons: List<ContactCardButton> = ContactCardButton.DEFAULT,
        services: List<ContactService> = listOf(whatsApp, signal),
        rtl: Boolean = false,
    ): Rendered {
        RuntimeEnvironment.setQualifiers((if (rtl) "ar-ldrtl-" else "") + "w${widthDp}dp-h2400dp-xhdpi")
        RuntimeEnvironment.setFontScale(fontScale)
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            SearchTheme(null) {
                // Scrolls vertically like the real results list, so nothing is clipped by the screen.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PersonCard(hit, ContactServices.resolve(buttons, hit, services), icons) { }
                }
            }
        }
        ShadowLooper.idleMainLooper()
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = (content.getChildAt(0) as ViewGroup).getChildAt(0) as ViewRootForTest
        root.measureAndLayoutForTest()
        val owner = root.semanticsOwner
        return Rendered(
            owner.rootSemanticsNode.flatten(),
            owner.unmergedRootSemanticsNode.flatten(),
            activity.resources.displayMetrics.density,
            content.width,
        )
    }

    /** Depth-first, i.e. the default accessibility traversal order. */
    private fun SemanticsNode.flatten(): List<SemanticsNode> = listOf(this) + children.flatMap { it.flatten() }

    private val SemanticsNode.description get() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
    private val SemanticsNode.isButton get() = config.getOrNull(SemanticsProperties.Role) == Role.Button
    private val SemanticsNode.isHeading get() = config.contains(SemanticsProperties.Heading)
    private fun Rect.intersects(other: Rect) = left < other.right - 0.5f && other.left < right - 0.5f && top < other.bottom - 0.5f && other.top < bottom - 0.5f

    private fun Rendered.buttons() = nodes.filter { it.isButton }

    /** Checks shared by every configuration; returns a short layout description for the report. */
    private fun Rendered.assertAccessibleLayout(label: String, expectedButtons: Int) {
        val buttons = buttons()
        assertWithMessage("$label button count").that(buttons).hasSize(expectedButtons)
        val minPx = 48 * density - 0.5f
        buttons.forEach { b ->
            val r = b.boundsInRoot
            assertWithMessage("$label ${b.description} width").that(r.width).isAtLeast(minPx)
            assertWithMessage("$label ${b.description} height").that(r.height).isAtLeast(minPx)
            assertWithMessage("$label ${b.description} inside screen").that(r.left >= -0.5f && r.right <= widthPx + 0.5f).isTrue()
            // The visible label is not also announced: one description, no separate text.
            assertWithMessage("$label ${b.description} text").that(b.config.getOrNull(SemanticsProperties.Text)).isNull()
            assertWithMessage("$label clickable").that(b.config.contains(SemanticsActions.OnClick)).isTrue()
        }
        val boxes = buttons + nodes.filter { it.isHeading && it.config.getOrNull(SemanticsProperties.Text) != null }
        for (i in boxes.indices) for (j in i + 1 until boxes.size) {
            assertWithMessage("$label: ${boxes[i].description ?: boxes[i].config.getOrNull(SemanticsProperties.Text)} overlaps ${boxes[j].description ?: boxes[j].config.getOrNull(SemanticsProperties.Text)}")
                .that(boxes[i].boundsInRoot.intersects(boxes[j].boundsInRoot)).isFalse()
        }
        assertWithMessage("$label distinct labels").that(buttons.map { it.description }).containsNoDuplicates()
        // No text anywhere is ellipsized or clipped.
        unmerged.forEach { node ->
            val results = ArrayList<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { layout ->
                // A sub-pixel difference is rounding, not clipping: require the text to fit within 1px.
                val widest = (0 until layout.lineCount).maxOfOrNull { layout.getLineRight(it) - layout.getLineLeft(it) } ?: 0f
                val clipped = widest > layout.size.width + 1 || layout.multiParagraph.height > layout.size.height + 1
                assertWithMessage("$label text ${layout.layoutInput.text} clipped").that(clipped).isFalse()
                (0 until layout.lineCount).forEach { line ->
                    assertWithMessage("$label text ${layout.layoutInput.text} ellipsized").that(layout.isLineEllipsized(line)).isFalse()
                }
            }
        }
    }

    private val four = listOf(
        ContactCardButton.CALL, ContactCardButton.TEXT,
        ContactCardButton("whatsapp", ContactActionType.MESSAGE), ContactCardButton("signal", ContactActionType.MESSAGE),
    )

    @Test
    fun defaultCardShowsCallMessageWhatsAppInOneRow() {
        val rendered = render(360, 1f)
        rendered.assertAccessibleLayout("default", expectedButtons = 3)
        val buttons = rendered.buttons()
        assertThat(buttons.map { it.description }).containsExactly(
            "Call Abin in your phone app", "Write a text message to Abin", "Open WhatsApp chat with Abin",
        ).inOrder()
        assertThat(buttons.map { it.boundsInRoot.top }.distinct()).hasSize(1)
        assertThat(buttons.zipWithNext().all { (a, b) -> a.boundsInRoot.right <= b.boundsInRoot.left + 0.5f }).isTrue()
    }

    @Test
    fun swappedAndReorderedButtonsFollowTheSetting() {
        val custom = listOf(ContactCardButton("signal", ContactActionType.MESSAGE), ContactCardButton("whatsapp", ContactActionType.MESSAGE), ContactCardButton.CALL)
        val rendered = render(360, 1f, buttons = custom)
        assertThat(rendered.buttons().map { it.description }).containsExactly(
            "Open Signal chat with Abin", "Open WhatsApp chat with Abin", "Call Abin in your phone app",
        ).inOrder()
    }

    @Test
    fun unusableButtonsLeaveNoEmptySlot() {
        val phoneOnly = contact.copy(channelActions = emptyList())
        val rendered = render(360, 1f, hit = phoneOnly)
        rendered.assertAccessibleLayout("phone-only", expectedButtons = 2)
        val (call, text) = rendered.buttons().map { it.boundsInRoot }
        assertThat(text.width).isWithin(1f).of(call.width) // the two remaining buttons share the row
        assertThat(render(360, 1f, hit = contact.copy(phoneNumber = null, channelActions = emptyList())).buttons()).isEmpty()
    }

    @Test
    fun anAdditionalProviderUsesTheSameButtons() {
        val hit = contact.copy(channelActions = contact.channelActions + actions(future, ContactActionType.MESSAGE))
        val rendered = render(360, 1f, hit = hit, buttons = ContactCardButton.DEFAULT + ContactCardButton("future", ContactActionType.MESSAGE),
            services = listOf(whatsApp, signal, service(future, "Example Chat")))
        rendered.assertAccessibleLayout("future", expectedButtons = 4)
        assertThat(rendered.buttons().last().description).isEqualTo("Open Example Chat chat with Abin")
    }

    @Test
    fun callButtonsForAppsAreLabelledAsCalls() {
        val hit = contact.copy(channelActions = actions(CommunicationProviders.WHATSAPP, ContactActionType.VOICE_CALL, ContactActionType.VIDEO_CALL))
        val custom = listOf(ContactCardButton("whatsapp", ContactActionType.VOICE_CALL), ContactCardButton("whatsapp", ContactActionType.VIDEO_CALL))
        val descriptions = render(360, 1f, hit = hit, buttons = custom).buttons().map { it.description }
        assertThat(descriptions).containsExactly("WhatsApp voice call with Abin", "WhatsApp video call with Abin").inOrder()
    }

    @Test
    fun fourButtonsAtEveryWidthAndFontScaleKeepLabelsWholeAndTargetsLarge() {
        for (width in listOf(320, 360, 412)) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
            render(width, scale, buttons = four).assertAccessibleLayout("${width}dp@${scale}x", expectedButtons = 4)
        }
    }

    @Test
    fun rightToLeftMirrorsTheButtonOrder() {
        val rendered = render(412, 1f, rtl = true)
        rendered.assertAccessibleLayout("rtl", expectedButtons = 3)
        val (call, text, chat) = rendered.buttons().map { it.boundsInRoot }
        assertThat(call.left).isGreaterThan(text.left)
        assertThat(text.left).isGreaterThan(chat.left)
    }

    @Test
    fun longNamesWrapInsteadOfTruncating() {
        val long = contact.copy(name = "Abinandanangal Kochuparambil Varghese Jacob")
        val longApp = service(CommunicationProviders.WHATSAPP, "WhatsApp Messenger für Geschäftskunden")
        render(320, 2f, hit = long, buttons = four, services = listOf(longApp, signal)).assertAccessibleLayout("long", expectedButtons = 4)
    }
}
