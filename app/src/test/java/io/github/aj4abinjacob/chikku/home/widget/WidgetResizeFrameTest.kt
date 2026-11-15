package io.github.aj4abinjacob.chikku.home.widget

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType.DESKTOP
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "mdpi")
class WidgetResizeFrameTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = CellPlacement(DESKTOP, 0, 1, 1, 2, 2)
    private val commits = mutableListOf<CellPlacement>()
    private var closed = false

    private fun frame(
        placement: CellPlacement = start,
        range: SpanRange = SpanRange(1, 5, 1, 5),
        rtl: Boolean = false,
        commit: (CellPlacement, (Boolean) -> Unit) -> Unit = { p, done -> commits += p; done(true) },
    ): WidgetResizeFrame {
        val col = if (rtl) 5 - placement.x - placement.spanX else placement.x
        return WidgetResizeFrame(
            context, RectF(col * 100f, placement.y * 100f, (col + placement.spanX) * 100f, (placement.y + placement.spanY) * 100f),
            100, 100, range, placement, GridSpec(5, 5), rtl, commit, { closed = true },
        ).apply { layout(0, 0, 500, 500) }
    }

    private fun WidgetResizeFrame.touch(action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(0, 100, action, x, y, 0)
        try {
            onTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun WidgetResizeFrame.drag(x: Float, y: Float, dx: Float, dy: Float) {
        touch(MotionEvent.ACTION_DOWN, x, y)
        touch(MotionEvent.ACTION_MOVE, x + dx, y + dy)
        touch(MotionEvent.ACTION_UP, x + dx, y + dy)
    }

    @Test
    fun allFourEdgesAndAllFourCornersKeepTheOppositeEdgesFixed() {
        data class Drag(val x: Float, val y: Float, val dx: Float, val dy: Float, val expected: CellPlacement)
        val drags = listOf(
            Drag(100f, 200f, -100f, 0f, start.copy(x = 0, spanX = 3)),
            Drag(300f, 200f, 100f, 0f, start.copy(spanX = 3)),
            Drag(200f, 100f, 0f, -100f, start.copy(y = 0, spanY = 3)),
            Drag(200f, 300f, 0f, 100f, start.copy(spanY = 3)),
            Drag(100f, 100f, -100f, -100f, start.copy(x = 0, y = 0, spanX = 3, spanY = 3)),
            Drag(300f, 100f, 100f, -100f, start.copy(y = 0, spanX = 3, spanY = 3)),
            Drag(100f, 300f, -100f, 100f, start.copy(x = 0, spanX = 3, spanY = 3)),
            Drag(300f, 300f, 100f, 100f, start.copy(spanX = 3, spanY = 3)),
        )
        for (d in drags) {
            commits.clear()
            frame().drag(d.x, d.y, d.dx, d.dy)
            assertThat(commits).containsExactly(d.expected)
        }
    }

    @Test
    fun rtlMirrorsModelOriginForBothEdgesAndCorners() {
        frame(rtl = true).drag(200f, 200f, -100f, 0f)
        frame(rtl = true).drag(400f, 200f, 100f, 0f)
        frame(rtl = true).drag(200f, 100f, -100f, -100f)
        frame(rtl = true).drag(400f, 300f, 100f, 100f)
        assertThat(commits).containsExactly(
            start.copy(spanX = 3), start.copy(x = 0, spanX = 3),
            start.copy(y = 0, spanX = 3, spanY = 3), start.copy(x = 0, spanX = 3, spanY = 3),
        ).inOrder()
    }

    @Test
    fun oppositeEdgesRemainFixedWhenShrinkingAndRepeatedlyResizing() {
        val view = frame()
        view.drag(100f, 100f, 100f, 100f)
        view.drag(200f, 200f, -100f, -100f)
        view.drag(300f, 300f, -100f, -100f)
        assertThat(commits).containsExactly(
            start.copy(x = 2, y = 2, spanX = 1, spanY = 1), start, start.copy(spanX = 1, spanY = 1),
        ).inOrder()
    }

    @Test
    fun gridBoundaryClampsBothOriginAndEndEdges() {
        frame().drag(100f, 100f, -900f, -900f)
        frame().drag(300f, 300f, 900f, 900f)
        assertThat(commits).containsExactly(
            start.copy(x = 0, y = 0, spanX = 3, spanY = 3), start.copy(spanX = 4, spanY = 4),
        ).inOrder()
    }

    @Test
    fun providerLimitsApplyToStartAndEndEdges() {
        val p = start.copy(spanX = 3, spanY = 3)
        val range = SpanRange(2, 3, 2, 3)
        frame(p, range).drag(100f, 100f, 900f, 900f)
        frame(p, range).drag(400f, 400f, -900f, -900f)
        frame(range = range).drag(100f, 100f, -900f, -900f)
        frame(range = range).drag(300f, 300f, 900f, 900f)
        assertThat(commits).containsExactly(
            p.copy(x = 2, y = 2, spanX = 2, spanY = 2), p.copy(spanX = 2, spanY = 2),
            start.copy(x = 0, y = 0, spanX = 3, spanY = 3), start.copy(spanX = 3, spanY = 3),
        ).inOrder()
    }

    @Test
    fun cancelledDragDoesNotCommitAndRestoresHandles() {
        val view = frame()
        view.touch(MotionEvent.ACTION_DOWN, 100f, 100f)
        view.touch(MotionEvent.ACTION_MOVE, 0f, 0f)
        view.touch(MotionEvent.ACTION_CANCEL, 0f, 0f)
        assertThat(commits).isEmpty()
        view.drag(100f, 100f, 100f, 100f)
        assertThat(commits).containsExactly(start.copy(x = 2, y = 2, spanX = 1, spanY = 1))
    }

    @Test
    fun rejectedEditRestoresHandlesAndNextDragUsesAcceptedPlacement() {
        val view = frame(commit = { p, done -> commits += p; done(commits.size > 1) })
        view.drag(100f, 100f, -100f, -100f)
        assertThat(ViewCompat.getStateDescription(view)).isEqualTo("2 × 2")
        view.drag(100f, 100f, 100f, 100f)
        assertThat(commits).containsExactly(
            start.copy(x = 0, y = 0, spanX = 3, spanY = 3), start.copy(x = 2, y = 2, spanX = 1, spanY = 1),
        ).inOrder()
    }

    @Test
    fun inFlightEditPreventsOverlappingCommitsAndResumesAfterCompletion() {
        var complete: ((Boolean) -> Unit)? = null
        val view = frame(commit = { p, done -> commits += p; complete = done })
        view.drag(100f, 100f, -100f, -100f)
        view.drag(300f, 300f, 100f, 100f)
        assertThat(commits).hasSize(1)
        complete!!(false)
        view.drag(300f, 300f, 100f, 100f)
        assertThat(commits.last()).isEqualTo(start.copy(spanX = 3, spanY = 3))
    }

    @Test
    fun fixedAxisDoesNotOfferHandlesOrChangeSize() {
        val range = SpanRange(2, 2, 1, 5)
        frame(range = range).drag(100f, 200f, -100f, 0f)
        assertThat(commits).isEmpty()
        frame(range = range).drag(200f, 100f, 100f, -100f)
        assertThat(commits).containsExactly(start.copy(y = 0, spanY = 3))
    }

    @Test
    fun fullGridHandlesStayInsideScreenAndCanShrinkFromBothCorners() {
        val p = start.copy(x = 0, y = 0, spanX = 5, spanY = 5)
        frame(p).drag(11f, 11f, 100f, 100f)
        frame(p).drag(489f, 489f, -100f, -100f)
        assertThat(commits).containsExactly(
            p.copy(x = 1, y = 1, spanX = 4, spanY = 4), p.copy(spanX = 4, spanY = 4),
        ).inOrder()
    }

    @Test
    fun releaseUsesFinalPositionAndNoOpDoesNotCreateAnEdit() {
        val view = frame()
        view.drag(100f, 200f, 10f, 0f)
        assertThat(commits).isEmpty()
        view.touch(MotionEvent.ACTION_DOWN, 100f, 200f)
        view.touch(MotionEvent.ACTION_UP, 200f, 200f)
        assertThat(commits).containsExactly(start.copy(x = 2, spanX = 1))
    }

    @Test
    fun tapOutsideClosesWithoutCommitting() {
        frame().touch(MotionEvent.ACTION_DOWN, 450f, 450f)
        assertThat(closed).isTrue()
        assertThat(commits).isEmpty()
    }
}
