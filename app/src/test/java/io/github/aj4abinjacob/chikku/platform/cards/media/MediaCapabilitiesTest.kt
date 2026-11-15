package io.github.aj4abinjacob.chikku.platform.cards.media

import android.media.session.PlaybackState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaCapabilitiesTest {
    @Test fun unsupportedTransportNeverAppearsAsAvailable() {
        assertThat(supportedMediaCommands(0L)).isEmpty()
        assertThat(supportedMediaCommands(PlaybackState.ACTION_PLAY)).containsExactly(MediaCardCommand.PLAY)
        assertThat(supportedMediaCommands(PlaybackState.ACTION_PAUSE)).containsExactly(MediaCardCommand.PAUSE)
        assertThat(supportedMediaCommands(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT))
            .containsExactly(MediaCardCommand.PLAY, MediaCardCommand.PAUSE, MediaCardCommand.NEXT)
        assertThat(supportedMediaCommands(PlaybackState.ACTION_SKIP_TO_PREVIOUS)).containsExactly(MediaCardCommand.PREVIOUS)
    }
}
