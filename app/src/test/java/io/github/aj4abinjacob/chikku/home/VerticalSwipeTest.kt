package io.github.aj4abinjacob.chikku.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VerticalSwipeTest {
    private val slop = 10f

    @Test
    fun movementWithinSlopIsNotASwipe() {
        assertThat(classifyVerticalSwipe(0f, -10f, slop)).isEqualTo(SwipeDirection.NONE)
        assertThat(classifyVerticalSwipe(3f, 9f, slop)).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    fun mostlyVerticalMovementIsClassifiedByDirection() {
        assertThat(classifyVerticalSwipe(4f, -30f, slop)).isEqualTo(SwipeDirection.UP)
        assertThat(classifyVerticalSwipe(-4f, 30f, slop)).isEqualTo(SwipeDirection.DOWN)
    }

    @Test
    fun mostlyHorizontalMovementIsLeftToThePager() {
        assertThat(classifyVerticalSwipe(40f, -30f, slop)).isEqualTo(SwipeDirection.NONE)
        assertThat(classifyVerticalSwipe(-40f, 30f, slop)).isEqualTo(SwipeDirection.NONE)
    }

    @Test
    fun flingDecidesByItsDirection() {
        assertThat(shouldSettleOpen(progress = 0.05f, velocityY = -2000f, minFlingVelocity = 1000f, startedOpen = false)).isTrue()
        assertThat(shouldSettleOpen(progress = 0.95f, velocityY = 2000f, minFlingVelocity = 1000f, startedOpen = true)).isFalse()
        // Pulled most of the way up, then flung back down: stays closed.
        assertThat(shouldSettleOpen(progress = 0.9f, velocityY = 2000f, minFlingVelocity = 1000f, startedOpen = false)).isFalse()
    }

    @Test
    fun slowReleaseNeedsAQuarterOfTheTravelFromTheStartingState() {
        assertThat(shouldSettleOpen(progress = 0.2f, velocityY = 0f, minFlingVelocity = 1000f, startedOpen = false)).isFalse()
        assertThat(shouldSettleOpen(progress = 0.3f, velocityY = -500f, minFlingVelocity = 1000f, startedOpen = false)).isTrue()
        assertThat(shouldSettleOpen(progress = 0.8f, velocityY = 0f, minFlingVelocity = 1000f, startedOpen = true)).isTrue()
        assertThat(shouldSettleOpen(progress = 0.7f, velocityY = 500f, minFlingVelocity = 1000f, startedOpen = true)).isFalse()
    }
}
