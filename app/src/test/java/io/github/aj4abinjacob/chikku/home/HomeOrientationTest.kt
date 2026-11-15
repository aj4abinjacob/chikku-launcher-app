package io.github.aj4abinjacob.chikku.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeOrientationTest {
    @Test
    fun phonesLockToPortraitButTabletsAndUnfoldedFoldablesRotate() {
        assertTrue(HomeLayoutMetrics.locksToPortrait(360)) // phone
        assertTrue(HomeLayoutMetrics.locksToPortrait(411)) // folded cover screen
        assertTrue(HomeLayoutMetrics.locksToPortrait(599))
        assertFalse(HomeLayoutMetrics.locksToPortrait(600))
        assertFalse(HomeLayoutMetrics.locksToPortrait(840)) // unfolded foldable / tablet
    }
}
