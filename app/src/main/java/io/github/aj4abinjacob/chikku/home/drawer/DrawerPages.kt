package io.github.aj4abinjacob.chikku.home.drawer

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.DrawerAppearance
import io.github.aj4abinjacob.chikku.home.DrawerItemListener
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.attachDrawerActions
import io.github.aj4abinjacob.chikku.home.bindDrawerApp

/** One page of the paged drawer: a fixed [columns] x [rows] grid of app cells, row-major, mirrored in RTL. */
@SuppressLint("ViewConstructor") // created in code only
internal class DrawerPageView(
    context: Context,
    private val iconBinder: IconBinder,
    private val listener: DrawerItemListener,
) : ViewGroup(context) {

    private var columns = 1
    private var rows = 1
    private val cells = mutableListOf<Cell>()

    private class Cell(val view: AppIconView) {
        var app: LauncherApp? = null
    }

    fun bind(apps: List<LauncherApp>, columns: Int, rows: Int, appearance: DrawerAppearance) {
        val gridChanged = this.columns != columns || this.rows != rows
        this.columns = columns.coerceAtLeast(1)
        this.rows = rows.coerceAtLeast(1)
        while (cells.size < apps.size) {
            val cell = Cell(AppIconView(context, AppIconView.Style.DRAWER))
            cell.view.attachDrawerActions(listener) { cell.app }
            cells += cell
            addView(cell.view)
        }
        while (cells.size > apps.size) {
            val cell = cells.removeAt(cells.lastIndex)
            iconBinder.cancel(cell.view)
            removeView(cell.view)
        }
        apps.forEachIndexed { index, app ->
            val cell = cells[index]
            cell.app = app
            appearance.applyTo(cell.view)
            iconBinder.bindDrawerApp(cell.view, app)
        }
        if (gridChanged) requestLayout()
    }

    fun recycle() {
        cells.forEach { iconBinder.cancel(it.view) }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val cellWidth = (width - paddingLeft - paddingRight) / columns
        val cellHeight = (height - paddingTop - paddingBottom) / rows
        val wSpec = MeasureSpec.makeMeasureSpec(cellWidth.coerceAtLeast(0), MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(cellHeight.coerceAtLeast(0), MeasureSpec.AT_MOST)
        cells.forEach { it.view.measure(wSpec, hSpec) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellWidth = (width - paddingLeft - paddingRight) / columns
        val cellHeight = (height - paddingTop - paddingBottom) / rows
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        cells.forEachIndexed { index, cell ->
            val column = index % columns
            val row = index / columns
            val visualColumn = if (rtl) columns - 1 - column else column
            val left = paddingLeft + visualColumn * cellWidth
            // Center each cell vertically in its slot so short cells do not stick to the top.
            val top = paddingTop + row * cellHeight + (cellHeight - cell.view.measuredHeight) / 2
            cell.view.layout(left, top, left + cell.view.measuredWidth, top + cell.view.measuredHeight)
        }
    }
}

/** Pages of the horizontal drawer, one full-size [DrawerPageView] each. */
internal class DrawerPagesAdapter(
    private val iconBinder: IconBinder,
    private val listener: DrawerItemListener,
) : RecyclerView.Adapter<DrawerPagesAdapter.Holder>() {

    private var pages: List<List<LauncherApp>> = emptyList()
    private var columns = 1
    private var rows = 1
    private var appearance = DrawerAppearance()
    private var wrapping = false

    /** [io.github.aj4abinjacob.chikku.theme.Motion.scale]: 0 swaps pages at once, otherwise they cross-fade. */
    var motionScale: Float = 1f

    val pageCount: Int get() = pages.size

    @SuppressLint("NotifyDataSetChanged") // used only when the page structure itself changes (wrap on/off, no motion)
    fun submit(pages: List<List<LauncherApp>>, columns: Int, rows: Int, appearance: DrawerAppearance, infiniteScroll: Boolean = false) {
        val nextWrapping = infiniteScroll && pages.size > 1
        if (pages == this.pages && columns == this.columns && rows == this.rows && appearance == this.appearance && nextWrapping == wrapping) return
        val before = itemCount
        val wrapChanged = nextWrapping != wrapping
        this.pages = pages
        wrapping = nextWrapping
        this.columns = columns
        this.rows = rows
        this.appearance = appearance
        val after = itemCount
        if (wrapChanged || motionScale <= 0f || before == 0) {
            notifyDataSetChanged()
            return
        }
        // Re-chunked pages cross-fade (the pager's item animator) instead of swapping in one frame.
        val common = minOf(before, after)
        notifyItemRangeChanged(0, common)
        if (after > before) notifyItemRangeInserted(common, after - common)
        else if (before > after) notifyItemRangeRemoved(common, before - after)
    }

    // A copy of the last page before the first, and the first after the last, allow a real
    // swipe through either edge. At idle the controller moves to the equivalent central page.
    override fun getItemCount(): Int = pages.size + if (wrapping) 2 else 0

    fun logicalPage(position: Int): Int = when {
        pages.isEmpty() -> 0
        !wrapping -> position.coerceIn(0, pages.lastIndex)
        else -> Math.floorMod(position - 1, pages.size)
    }

    fun adapterPosition(page: Int): Int =
        page.coerceIn(0, (pages.size - 1).coerceAtLeast(0)) + if (wrapping) 1 else 0

    fun normalizedPosition(position: Int): Int = adapterPosition(logicalPage(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = DrawerPageView(parent.context, iconBinder, listener)
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.page.bind(pages[logicalPage(position)], columns, rows, appearance)
    }

    override fun onViewRecycled(holder: Holder) = holder.page.recycle()

    class Holder(val page: DrawerPageView) : RecyclerView.ViewHolder(page)
}
