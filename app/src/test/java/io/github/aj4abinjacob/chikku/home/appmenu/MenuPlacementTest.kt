package io.github.aj4abinjacob.chikku.home.appmenu

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuPlacementTest {
    // 1080 x 2340 screen; visible frame excludes a 100px status bar and 60px navigation bar.
    private fun place(anchorTop: Int, anchorBottom: Int, menuHeight: Int, anchorLeft: Int = 200, anchorRight: Int = 400) =
        MenuPlacement.place(
            anchorLeft, anchorTop, anchorRight, anchorBottom,
            frameLeft = 0, frameTop = 100, frameRight = 1080, frameBottom = 2280,
            menuWidth = 600, menuHeight = menuHeight,
            overlap = 40, margin = 20,
        )

    @Test
    fun `goes below an anchor near the top`() {
        val pos = place(anchorTop = 300, anchorBottom = 500, menuHeight = 700)
        assertThat(pos.above).isFalse()
        assertThat(pos.y).isEqualTo(460)
        assertThat(pos.height).isEqualTo(700)
    }

    @Test
    fun `flips above a dock icon instead of running off the bottom`() {
        val pos = place(anchorTop = 2050, anchorBottom = 2250, menuHeight = 700)
        assertThat(pos.above).isTrue()
        assertThat(pos.y + pos.height).isEqualTo(2090)
        assertThat(pos.y).isAtLeast(120)
    }

    @Test
    fun `caps a menu taller than either side and keeps it inside the frame`() {
        val pos = place(anchorTop = 1100, anchorBottom = 1300, menuHeight = 3000)
        assertThat(pos.y).isAtLeast(120)
        assertThat(pos.y + pos.height).isAtMost(2260)
    }

    @Test
    fun `stays inside the side margins at the screen edge`() {
        val left = place(anchorTop = 300, anchorBottom = 500, menuHeight = 200, anchorLeft = 0, anchorRight = 150)
        assertThat(left.x).isEqualTo(20)
        val right = place(anchorTop = 300, anchorBottom = 500, menuHeight = 200, anchorLeft = 930, anchorRight = 1080)
        assertThat(right.x + 600).isEqualTo(1060)
    }

    private fun aligned(iconLeft: Int, iconRight: Int) = MenuPlacement.place(
        iconLeft - 20, 300, iconRight + 20, 500,
        frameLeft = 0, frameTop = 100, frameRight = 1080, frameBottom = 2280,
        menuWidth = 600, menuHeight = 200,
        overlap = 0, margin = 20,
        alignToEdge = true, alignLeft = iconLeft, alignRight = iconRight, edgeOutset = 30,
    )

    @Test
    fun `aligned menu starts at a left-half icon's left edge`() {
        assertThat(aligned(iconLeft = 250, iconRight = 400).x).isEqualTo(220)
    }

    @Test
    fun `aligned menu ends at a right-half icon's right edge`() {
        val pos = aligned(iconLeft = 700, iconRight = 850)
        assertThat(pos.x + 600).isEqualTo(880)
    }

    @Test
    fun `aligned menu is still clamped inside the frame`() {
        assertThat(aligned(iconLeft = 10, iconRight = 160).x).isEqualTo(20)
        assertThat(aligned(iconLeft = 920, iconRight = 1070).x + 600).isEqualTo(1060)
    }
}
