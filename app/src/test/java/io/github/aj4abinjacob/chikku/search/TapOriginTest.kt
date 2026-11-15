package io.github.aj4abinjacob.chikku.search

import android.graphics.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TapOriginTest {
    private var now = 10_000L
    private val origin = TapOrigin { now }

    @Test
    fun noTapGivesNoBounds() {
        assertThat(origin.bounds(48)).isNull()
    }

    @Test
    fun recentTapGivesBoundsCentredOnIt() {
        origin.record(200, 600)
        now += 300
        assertThat(origin.bounds(48)).isEqualTo(Rect(176, 576, 224, 624))
    }

    @Test
    fun staleTapIsIgnoredSoKeyboardLaunchesDoNotRevealFromIt() {
        origin.record(200, 600)
        now += 5_000
        assertThat(origin.bounds(48)).isNull()
    }
}
