package io.github.aj4abinjacob.chikku.home.cards.media

import android.os.Looper
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.cards.media.CardMediaSource
import io.github.aj4abinjacob.chikku.platform.cards.media.MediaCardCommand
import io.github.aj4abinjacob.chikku.platform.cards.media.MediaCardItem
import io.github.aj4abinjacob.chikku.platform.cards.media.MediaCardState
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class MediaCardViewTest {
    private class Source : CardMediaSource {
        val state = MutableStateFlow<MediaCardState>(MediaCardState.Active(MediaCardItem("opaque", "Synthetic player",
            "Synthetic title", null, false, setOf(MediaCardCommand.PLAY))))
        val gate = CompletableDeferred<Unit>()
        var active = 0
        var pending = 0
        var executed = 0
        var cancelled = 0
        var failObservation = false
        var failCommands = false
        override fun observe() = flow<MediaCardState> { if (failObservation) throw IllegalArgumentException("synthetic provider failure"); active++; try { emitAll(state) } finally { active-- } }
        override suspend fun command(sessionId: String, command: MediaCardCommand): Boolean {
            if (failCommands) throw IllegalArgumentException("synthetic command failure")
            pending++
            try { gate.await(); executed++; return true } finally { if (!gate.isCompleted) cancelled++ }
        }
        override suspend fun hasAccess() = false
        override suspend fun prepareAccess(): Boolean { pending++; gate.await(); executed++; return true }
    }

    @Test fun detachingCardCancelsPendingTransportAndMetadataObservation() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = FrameLayout(activity); activity.setContentView(root)
            val source = Source(); val view = MediaCardView(activity, source, SurfaceColors.DARK)
            root.addView(view); shadowOf(Looper.getMainLooper()).idle()
            val play = (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<Button>().single()
            assertThat(play.text.toString()).isEqualTo(activity.getString(R.string.card_media_play))
            play.performClick(); shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.pending).isEqualTo(1)
            root.removeView(view); shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.active).isEqualTo(0)
            assertThat(source.cancelled).isEqualTo(1)
            source.gate.complete(Unit); shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.executed).isEqualTo(0)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun brokenOptionalSourceAndTransportDoNotCrashTheHostActivity() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = FrameLayout(activity); activity.setContentView(root)
            val broken = MediaCardView(activity, Source().apply { failObservation = true }, SurfaceColors.DARK)
            root.addView(broken); shadowOf(Looper.getMainLooper()).idle()
            assertThat((broken.getChildAt(0) as TextView).text.toString()).isEqualTo(activity.getString(R.string.card_media_unavailable))
            val transport = MediaCardView(activity, Source().apply { failCommands = true }, SurfaceColors.DARK)
            root.addView(transport); shadowOf(Looper.getMainLooper()).idle()
            (0 until transport.childCount).map { transport.getChildAt(it) }.filterIsInstance<Button>().single().performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(activity.isFinishing).isFalse()
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun stoppingActivityCancelsPendingAccessAndReleasesMetadata() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val activity = controller.get()
            val root = FrameLayout(activity); activity.setContentView(root)
            val source = Source().apply { state.value = MediaCardState.NeedsAccess }
            val view = MediaCardView(activity, source, SurfaceColors.DARK)
            root.addView(view); shadowOf(Looper.getMainLooper()).idle()
            (0 until view.childCount).map { view.getChildAt(it) }.filterIsInstance<Button>().single().performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.pending).isEqualTo(1)
            controller.pause().stop(); shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.active).isEqualTo(0)
            source.gate.complete(Unit); shadowOf(Looper.getMainLooper()).idle()
            assertThat(source.executed).isEqualTo(0)
        } finally { controller.destroy() }
    }
}
