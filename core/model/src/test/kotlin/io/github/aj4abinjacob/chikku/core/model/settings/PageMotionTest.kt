package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageMotionTest {
    @Test fun sensitivityChangesStartDistanceWithoutAcceptingDiagonalOrVerticalInput() {
        assertThat(PageMotion.isHorizontal(12f, 0f, 8f, PageSwipeSensitivity.HIGH)).isTrue()
        assertThat(PageMotion.isHorizontal(12f, 0f, 8f, PageSwipeSensitivity.LOW)).isFalse()
        PageSwipeSensitivity.entries.forEach {
            assertThat(PageMotion.isHorizontal(100f, 90f, 8f, it)).isFalse()
            assertThat(PageMotion.isHorizontal(20f, 100f, 8f, it)).isFalse()
        }
    }

    @Test fun workspaceOnlyPagesOnClearlySidewaysMovementSoUpSwipesWithDriftAreNotTaken() {
        // A curved thumb swipe up: 30px sideways against 20px up is accepted by the drawer's
        // looser rule but must not page the home workspace.
        assertThat(PageMotion.isHorizontal(30f, -20f, 8f, PageSwipeSensitivity.HIGH)).isTrue()
        PageSwipeSensitivity.entries.forEach {
            assertThat(PageMotion.isHorizontal(30f, -20f, 8f, it, PageMotion.WORKSPACE_HORIZONTAL_DOMINANCE)).isFalse()
            assertThat(PageMotion.isHorizontal(60f, -10f, 8f, it, PageMotion.WORKSPACE_HORIZONTAL_DOMINANCE)).isTrue()
        }
    }

    @Test fun mostlyVerticalReleaseIsNotAPageFling() {
        assertThat(PageMotion.pageFlingVelocity(1500f, -3000f)).isEqualTo(0f)
        assertThat(PageMotion.pageFlingVelocity(-2500f, 600f)).isEqualTo(-2500f)
    }

    @Test fun settlingRespectsCancellationVelocityDirectionAndVirtualWrapSlots() {
        assertThat(PageMotion.settle(-.7f, 0, -280f, -2000f, 200f, true)).isEqualTo(0)
        assertThat(PageMotion.settle(-.2f, 0, -80f, -2000f, 200f, false)).isEqualTo(-1)
        assertThat(PageMotion.settle(2.2f, 2, 80f, 2000f, 200f, false)).isEqualTo(3)
        assertThat(PageMotion.settle(.2f, 0, 80f, -2000f, 200f, false)).isEqualTo(0)
        assertThat(PageMotion.settle(.6f, 0, 240f, 0f, 200f, false)).isEqualTo(1)
    }

    @Test fun fastAndShortSettlesFinishEarlierAndEffectsResetForReducedMotion() {
        assertThat(PageMotion.duration(400, 400, 400, 4000f)).isLessThan(PageMotion.duration(400, 400, 400))
        assertThat(PageMotion.duration(40, 400, 400)).isLessThan(PageMotion.duration(400, 400, 400))
        PageTransitionEffect.entries.forEach { effect ->
            assertThat(PageMotion.transform(.7f, effect, 1f, false)).isEqualTo(PageTransform())
            val weak = PageMotion.transform(.7f, effect, .3f, true)
            val strong = PageMotion.transform(.7f, effect, 1f, true)
            assertThat(weak.alpha).isAtLeast(strong.alpha)
            assertThat(weak.scale).isAtLeast(strong.scale)
        }
    }

    @Test fun oldAndDamagedSettingsHaveSafeDefaultsAndBoundedMotion() {
        val old = LauncherSettingsCodec.decode("""{"home":{"transitionEffect":"CUBE"},"drawer":{"infiniteScroll":true}}""")
        assertThat(old.home.transitionEffect).isEqualTo(PageTransitionEffect.CUBE)
        assertThat(old.home.pageSnapDurationMs).isEqualTo(250)
        assertThat(old.drawer.pageTransitionEffect).isEqualTo(PageTransitionEffect.SLIDE)
        assertThat(old.drawer.infiniteScroll).isTrue()
        val bad = LauncherSettingsCodec.decode("""{"home":{"pageSnapDurationMs":-1,"pageEffectStrength":9,"pageSwipeSensitivity":"UNKNOWN"},"drawer":{"pageSnapDurationMs":99999,"pageEffectStrength":-2}}""")
        assertThat(bad.home.pageSnapDurationMs).isEqualTo(100)
        assertThat(bad.home.pageEffectStrength).isEqualTo(1f)
        assertThat(bad.home.pageSwipeSensitivity).isEqualTo(PageSwipeSensitivity.NORMAL)
        assertThat(bad.drawer.pageSnapDurationMs).isEqualTo(500)
        assertThat(bad.drawer.pageEffectStrength).isEqualTo(0f)
    }
}
