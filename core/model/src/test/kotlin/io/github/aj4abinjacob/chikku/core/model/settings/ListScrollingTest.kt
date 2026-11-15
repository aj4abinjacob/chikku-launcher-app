package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListScrollingTest {
    @Test fun speedScalesFlingsInBothDirections() {
        assertThat(ListScrolling.flingVelocity(1_000, ListScrollSpeed.SLOW, 8_000)).isEqualTo(600)
        assertThat(ListScrolling.flingVelocity(1_000, ListScrollSpeed.NORMAL, 8_000)).isEqualTo(1_000)
        assertThat(ListScrolling.flingVelocity(-1_000, ListScrollSpeed.FAST, 8_000)).isEqualTo(-1_600)
    }

    @Test fun fastNeverExceedsThePlatformMaximum() {
        assertThat(ListScrolling.flingVelocity(7_000, ListScrollSpeed.FAST, 8_000)).isEqualTo(8_000)
        assertThat(ListScrolling.flingVelocity(-7_000, ListScrollSpeed.FAST, 8_000)).isEqualTo(-8_000)
        assertThat(ListScrolling.flingVelocity(500, ListScrollSpeed.FAST, -1)).isEqualTo(0)
    }
}
