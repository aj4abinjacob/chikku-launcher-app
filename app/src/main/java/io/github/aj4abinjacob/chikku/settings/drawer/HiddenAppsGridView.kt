package io.github.aj4abinjacob.chikku.settings.drawer

import android.content.Context
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.CheckBox
import android.widget.FrameLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.home.IconBinder
import io.github.aj4abinjacob.chikku.home.drawer.DrawerFastScroller
import io.github.aj4abinjacob.chikku.platform.LocaleSectionIndex
import io.github.aj4abinjacob.chikku.theme.Motion
import kotlin.math.roundToInt

/**
 * A touch-and-hold on a picker cell. [anchor] is the cell's bounds in [HiddenAppsGridView]
 * coordinates so the Compose screen can place Chikku's own menu over it; [icon] is the drawable
 * the cell is already showing (null while it is still loading).
 */
internal data class HiddenAppMenuRequest(
    val key: AppKey,
    val label: String,
    val icon: Drawable?,
    val anchor: Rect,
)

/** Reuses the drawer's icon rendering, icon provider and alphabet rail inside Compose settings. */
internal class HiddenAppsGridView(
    context: Context,
    iconBinder: IconBinder,
    onHiddenChanged: (AppKey, Boolean) -> Unit,
    onOpen: (AppKey) -> Unit,
    onLongPress: (HiddenAppMenuRequest) -> Unit,
    /** Whether the grid is scrolled to its top; the screen shows its hint only then. */
    onAtTopChanged: (Boolean) -> Unit = {},
) : FrameLayout(context) {
    /** Follows the user's animation speed; with animations off, cells change and leave without motion. */
    var motion: Motion = Motion.DEFAULT
        set(value) {
            field = value
            applyAnimator()
        }

    internal val grid = GridLayoutManager(context, 3)
    internal val list = RecyclerView(context).apply {
        layoutManager = grid
        clipToPadding = false
    }
    private val optimistic = HashMap<AppKey, Boolean>()
    private var lastApps: List<LauncherApp> = emptyList()
    private var lastHidden: Set<AppKey> = emptySet()
    private var lastColors = Colors(0, 0, 0, 0)
    private var lastLabels: Map<AppKey, String> = emptyMap()
    internal val fastScroller = DrawerFastScroller(context)
    private val sectionIndex = LocaleSectionIndex()
    private val changeHidden = onHiddenChanged

    /** Main-thread timers; a view's own post queue holds work until it is attached. */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pickerAdapter = HiddenAppsAdapter(iconBinder, ::toggled, onOpen, { motion }) { cell, cellView, drawable ->
        val anchor = Rect(0, 0, cellView.width, cellView.height)
        offsetDescendantRectToMyCoords(cellView, anchor)
        onLongPress(HiddenAppMenuRequest(cell.app.key, cell.label, drawable, anchor))
    }
    private var settings = DrawerSettings()
    private val sidePadding = resources.getDimensionPixelSize(R.dimen.home_page_padding)
    private val railWidth = (48 * resources.displayMetrics.density).roundToInt()

    init {
        applyAnimator()
        list.adapter = pickerAdapter
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(fastScroller, LayoutParams(railWidth, LayoutParams.MATCH_PARENT, Gravity.END))
        fastScroller.onSectionSelected = { grid.scrollToPositionWithOffset(it, 0) }
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateColumns() }
        var atTop = true
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Collapsing the hint hands its height to the grid. If the list overflows by less than
                // that it would stop being scrollable, snap back to the top and bring the hint back
                // mid-gesture, so the hint only gives way for a list that stays scrollable.
                val overflow = recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent()
                val next = !recyclerView.canScrollVertically(-1) ||
                    overflow < HINT_COLLAPSE_SLACK_DP * resources.displayMetrics.density
                if (next != atTop) {
                    atTop = next
                    onAtTopChanged(next)
                }
            }
        })
    }

    fun render(
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        settings: DrawerSettings,
        labelColor: Int,
        accentColor: Int,
        customLabels: Map<AppKey, String> = emptyMap(),
        onAccentColor: Int = Color.WHITE,
        surfaceColor: Int = Color.TRANSPARENT,
    ) {
        this.settings = settings
        lastApps = apps
        lastHidden = hidden
        lastColors = Colors(labelColor, accentColor, onAccentColor, surfaceColor)
        lastLabels = customLabels
        // A write that has landed no longer needs its optimistic mark.
        optimistic.entries.removeAll { (key, value) -> (key in hidden) == value }
        val sections = DrawerModel.sections(apps, sectionIndex::sectionOf)
        fastScroller.visibility = if (sections.size > 1) View.VISIBLE else View.GONE
        fastScroller.setSections(sections, labelColor)
        list.setPaddingRelative(sidePadding, sidePadding, sidePadding + if (sections.size > 1) railWidth else 0, sidePadding)
        updateColumns()
        submit()
    }

    private fun submit() {
        val colors = lastColors
        pickerAdapter.submitList(lastApps.map { app ->
            HiddenAppCell(app, optimistic[app.key] ?: (app.key in lastHidden), settings.iconScale, colors.label, colors.accent,
                colors.onAccent, colors.surface, lastLabels[app.key] ?: app.label)
        })
    }

    /** A cell was tapped: it already shows the new state; the store catches up (or the mark returns). */
    private fun toggled(key: AppKey, hidden: Boolean) {
        optimistic[key] = hidden
        changeHidden(key, hidden)
        handler.postDelayed({
            if (optimistic.remove(key) == hidden) {
                submit()
                pickerAdapter.refresh(key)
            }
        }, OPTIMISTIC_TIMEOUT_MS)
    }

    private fun applyAnimator() {
        // Filtering and unhiding in "hidden only" move and remove cells; a check toggle is a change
        // on the same cell and must not fade or flash it.
        list.itemAnimator = if (!motion.enabled) null else DefaultItemAnimator().apply {
            supportsChangeAnimations = false
            addDuration = motion.duration(ITEM_MS)
            removeDuration = motion.duration(ITEM_MS)
            moveDuration = motion.duration(ITEM_MS)
        }
    }

    private data class Colors(val label: Int, val accent: Int, val onAccent: Int, val surface: Int)

    private companion object {
        const val ITEM_MS = 200L
        const val OPTIMISTIC_TIMEOUT_MS = 2_000L
        const val HINT_COLLAPSE_SLACK_DP = 140
    }

    private fun updateColumns() {
        val usable = list.width - list.paddingStart - list.paddingEnd
        if (usable <= 0) return
        val cellWidth = (resources.getDimensionPixelSize(R.dimen.home_drawer_column_width) * settings.iconScale.coerceAtLeast(0.85f)).roundToInt()
        val desired = DrawerModel.columns(settings.columns, usable, cellWidth)
        // Always leave a touch target per cell, even with an extended grid on a narrow window.
        val maxColumns = (usable / resources.getDimensionPixelSize(R.dimen.home_min_touch_target)).coerceAtLeast(1)
        val next = desired.coerceAtMost(maxColumns)
        if (grid.spanCount != next) grid.spanCount = next
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        list.adapter = null // Recycles cells and cancels their pending icon loads.
    }
}

private data class HiddenAppCell(
    val app: LauncherApp,
    val hidden: Boolean,
    val iconScale: Float,
    val labelColor: Int,
    val accentColor: Int,
    val onAccentColor: Int,
    val surfaceColor: Int,
    val label: String,
)

private class HiddenAppsAdapter(
    private val binder: IconBinder,
    private val onHiddenChanged: (AppKey, Boolean) -> Unit,
    private val onOpen: (AppKey) -> Unit,
    private val motion: () -> Motion,
    private val onLongPress: (HiddenAppCell, View, Drawable?) -> Unit,
) : ListAdapter<HiddenAppCell, HiddenAppsAdapter.Holder>(Diff) {
    /** Rebinds the cell for [key] from the current list. */
    fun refresh(key: AppKey) {
        val position = currentList.indexOfFirst { it.app.key == key }
        if (position >= 0) notifyItemChanged(position)
    }

    init {
        stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(parent.context)

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) {
        binder.cancel(holder.icon)
        holder.cell = null
    }

    inner class Holder(context: Context) : RecyclerView.ViewHolder(SelectableCell(context)) {
        private val container = itemView as SelectableCell
        val icon = AppIconView(context, AppIconView.Style.DRAWER).apply {
            isClickable = false
            isLongClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = null
        }
        var cell: HiddenAppCell? = null

        init {
            container.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // Breathing room between rows, so a label does not sit right on the icon below it.
            val rowGap = (8 * context.resources.displayMetrics.density).roundToInt()
            container.setPadding(0, rowGap, 0, rowGap)
            container.isFocusable = true
            container.addView(icon, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            container.icon = icon
            container.foreground = selectableForeground(context)
            container.setOnClickListener {
                cell?.let { current ->
                    // Show the result at once; the store and the next render confirm it.
                    val next = !current.hidden
                    cell = current.copy(hidden = next)
                    container.motion = motion()
                    container.setMark(next, current.accentColor, current.onAccentColor, current.surfaceColor, animate = true)
                    Haptics.toggle(container, next)
                    onHiddenChanged(current.app.key, next)
                }
            }
            container.setOnLongClickListener {
                // The menu itself is Chikku's Compose menu, shown by HiddenAppsScreen.
                cell?.let { onLongPress(it, container, icon.icon) }
                true
            }
            ViewCompat.setAccessibilityDelegate(container, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = CheckBox::class.java.name
                    info.isCheckable = true
                    info.isChecked = cell?.hidden == true
                }
            })
            ViewCompat.addAccessibilityAction(container, context.getString(R.string.drawer_hidden_open)) { _, _ ->
                cell?.let { onOpen(it.app.key) }
                true
            }
        }

        fun bind(next: HiddenAppCell) {
            cell = next
            val app = next.app
            icon.iconScale = next.iconScale
            // Keep names visible in the picker even when drawer labels are disabled.
            icon.showLabel = true
            icon.setLabelColor(next.labelColor)
            binder.bindApp(icon, app.key, next.label, app.profileType == ProfileType.WORK, Availability.AVAILABLE)
            container.contentDescription = icon.contentDescription
            container.motion = motion()
            // Only the same app's own toggle animates; a recycled cell shows its state at once.
            container.setMark(next.hidden, next.accentColor, next.onAccentColor, next.surfaceColor, animate = container.boundKey == app.key)
            container.boundKey = app.key
        }
    }

    private object Diff : DiffUtil.ItemCallback<HiddenAppCell>() {
        override fun areItemsTheSame(oldItem: HiddenAppCell, newItem: HiddenAppCell) = oldItem.app.key == newItem.app.key
        override fun areContentsTheSame(oldItem: HiddenAppCell, newItem: HiddenAppCell) = oldItem == newItem
    }
}

/**
 * A picker cell that marks its state on the icon itself rather than with a separate checkbox: a
 * hidden app gets a filled check on the icon's top-end corner; a shown app is left plain (empty
 * rings read as loading spinners, and tinted tiles ran together between rows).
 */
private class SelectableCell(context: Context) : FrameLayout(context) {
    var icon: AppIconView? = null
    var motion: Motion = Motion.DEFAULT
    var boundKey: AppKey? = null
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var fillAlpha = 255
    private var ringAlpha = 255
    private var tickAlpha = 255
    private val density = resources.displayMetrics.density
    private var checked = false
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f * density
    }
    private val bounds = Rect()
    private val tickPath = Path()

    fun setMark(checked: Boolean, accent: Int, onAccent: Int, surface: Int, animate: Boolean = false) {
        val changed = this.checked != checked
        this.checked = checked
        animator?.cancel()
        val duration = motion.duration(MARK_MS)
        if (changed && animate && duration > 0L && isAttachedToWindow) {
            animator = ValueAnimator.ofFloat(progress, if (checked) 1f else 0f).apply {
                this.duration = duration
                interpolator = if (checked) OvershootInterpolator(2f) else DecelerateInterpolator()
                addUpdateListener { progress = it.animatedValue as Float; invalidate() }
                start()
            }
        } else progress = if (checked) 1f else 0f
        fillAlpha = Color.alpha(accent)
        tickAlpha = Color.alpha(onAccent)
        ringAlpha = Color.alpha(surface)
        fill.color = accent
        tick.color = onAccent
        // Separates the mark from the icon art beneath it.
        ring.color = surface
        ring.strokeWidth = 2f * density
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        val icon = icon ?: return
        if (progress <= 0f) return
        icon.iconBounds(bounds)
        bounds.offset(icon.left, icon.top)
        val fade = progress.coerceIn(0f, 1f)
        fill.alpha = (fillAlpha * fade).roundToInt()
        ring.alpha = (ringAlpha * fade).roundToInt()
        tick.alpha = (tickAlpha * fade).roundToInt()
        val r = 11f * density * progress.coerceAtMost(1.15f)
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // Centred just inside the corner so it reads as part of the icon, not a stray control, but
        // kept wholly inside the cell: the cell clips what it draws, and the icon can sit at its very
        // top (large text shrinks the vertical padding) or edge (narrow columns).
        val outer = r + ring.strokeWidth
        val cx = (if (rtl) bounds.left + r * 0.4f else bounds.right - r * 0.4f).coerceIn(outer, width - outer)
        val cy = (bounds.top + r * 0.4f).coerceAtLeast(outer)
        canvas.drawCircle(cx, cy, r + ring.strokeWidth / 2, ring)
        canvas.drawCircle(cx, cy, r, fill)
        tickPath.reset()
        tickPath.moveTo(cx - r * 0.45f, cy + r * 0.02f)
        tickPath.lineTo(cx - r * 0.12f, cy + r * 0.35f)
        tickPath.lineTo(cx + r * 0.48f, cy - r * 0.3f)
        canvas.drawPath(tickPath, tick)
    }
}

private const val MARK_MS = 180L

/** The platform's pressed ripple over a whole cell, in the current theme. */
private fun selectableForeground(context: Context): Drawable? {
    val value = android.util.TypedValue()
    if (!context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)) return null
    return androidx.core.content.ContextCompat.getDrawable(context, value.resourceId)
}
