package io.github.aj4abinjacob.chikku.home.drawer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.TextPaint
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerSection

/**
 * Group letters for the List drawer style: a leading column shows each letter once, beside the
 * first app of its group, so a long list reads in chapters. The group at the top stays pinned at
 * the top of the column until the next group's first row pushes it up, so the column always names
 * the letter the fast scroller's bubble shows. The list draws the letters; rows stay one accessible
 * button each, and TalkBack users jump by letter through the fast scroller.
 */
internal class DrawerListGroups(context: Context) : RecyclerView.ItemDecoration() {

    private val gutter = context.resources.getDimensionPixelSize(R.dimen.drawer_list_section_gutter)
    private val edgeGap = 8 * context.resources.displayMetrics.density
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = context.resources.getDimension(R.dimen.drawer_list_section_text_size) // sp: follows font scale
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }
    private var letters: Map<Int, String> = emptyMap()
    /** Group start positions, ascending. */
    private var starts: IntArray = IntArray(0)
    private var enabled = false

    /** Letter at each group's first adapter position, for tests. */
    internal val shownLetters: Map<Int, String> get() = if (enabled) letters else emptyMap()

    /** Letter color; the caller invalidates the list. */
    var color: Int = 0
        set(value) { field = value; paint.color = ColorUtils.setAlphaComponent(value, LETTER_ALPHA) }

    /**
     * Shows [groups]; none hides the column and its indent. Returns whether the rows' indent
     * changed, so the caller can lay the list out again.
     */
    fun update(groups: List<DrawerSection>): Boolean {
        letters = groups.associate { it.position to it.letter }
        starts = groups.map { it.position }.sorted().toIntArray()
        val indentChanged = enabled != groups.isNotEmpty()
        enabled = groups.isNotEmpty()
        return indentChanged
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        if (!enabled) return
        if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) outRect.right = gutter else outRect.left = gutter
    }

    /** Start position of the group containing [position], or -1 before the first group. */
    private fun groupStart(position: Int): Int {
        val index = starts.binarySearch(position).let { if (it >= 0) it else -it - 2 }
        return if (index >= 0) starts[index] else -1
    }

    /** The topmost row still showing, the same row the fast scroller's bubble names. */
    private fun topChild(parent: RecyclerView): View? = (0 until parent.childCount)
        .map(parent::getChildAt)
        .filter { parent.getChildAdapterPosition(it) != RecyclerView.NO_POSITION && it.bottom + it.translationY > parent.paddingTop }
        .minByOrNull { it.top + it.translationY }

    /**
     * The pinned letter and its centre, for tests; null when nothing is pinned. It stays level with
     * the top row while that row is in view enough to hold it, then rests at the top edge.
     */
    internal fun pinned(parent: RecyclerView): Pair<String, Float>? {
        if (!enabled) return null
        val top = topChild(parent) ?: return null
        val start = groupStart(parent.getChildAdapterPosition(top))
        val letter = letters[start] ?: return null
        val half = (paint.descent() - paint.ascent()) / 2f
        val resting = parent.paddingTop + half + edgeGap
        var centre = maxOf(top.top + top.translationY + top.height / 2f, resting)
        // The next group's first row pushes the pinned letter up as it arrives.
        val next = starts.firstOrNull { it > start }
        val nextRow = next?.let { parent.findViewHolderForAdapterPosition(it)?.itemView }
        if (nextRow != null) centre = minOf(centre, nextRow.top + nextRow.translationY - half - edgeGap)
        return letter to centre
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        if (!enabled) return
        val rtl = parent.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val centreOffset = (paint.descent() + paint.ascent()) / 2f
        val pinned = pinned(parent)
        val pinnedStart = topChild(parent)?.let { groupStart(parent.getChildAdapterPosition(it)) }
        val anyRow = parent.getChildAt(0) ?: return
        fun x(child: View) = if (rtl) child.right + child.translationX + gutter / 2f else child.left + child.translationX - gutter / 2f
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            // The pinned letter stands in for its group's own mark.
            if (pinned != null && position == pinnedStart) continue
            val letter = letters[position] ?: continue
            val y = child.top + child.translationY + child.height / 2f - centreOffset
            val restore = paint.alpha
            paint.alpha = (restore * child.alpha).toInt()
            canvas.drawText(letter, x(child), y, paint)
            paint.alpha = restore
        }
        if (pinned != null) canvas.drawText(pinned.first, x(anyRow), pinned.second - centreOffset, paint)
    }

    private companion object {
        const val LETTER_ALPHA = 0xB3 // ~70%: a quiet chapter mark, not another label
    }
}
