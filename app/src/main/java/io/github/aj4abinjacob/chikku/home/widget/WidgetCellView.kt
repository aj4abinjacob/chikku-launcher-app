package io.github.aj4abinjacob.chikku.home.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import kotlin.math.abs

/**
 * One widget cell on the workspace: either a live [AppWidgetHostView] or the "Widget unavailable"
 * placeholder. A long press anywhere on it is intercepted before the widget's own children see it,
 * so widgets with clickable content still open Chikku's widget options. Moving the finger past the
 * touch slop after the long press calls [onDragStart] while the finger is still down, which is
 * what `startDragAndDrop` needs (a drag started later, e.g. from a menu tap, ends at once).
 */
class WidgetCellView(context: Context) : FrameLayout(context) {

    /** Long press on the widget or placeholder. */
    var onLongPress: ((WidgetCellView) -> Unit)? = null

    /** Finger moved after a long press: start moving the widget. Called at most once per gesture. */
    var onDragStart: ((WidgetCellView) -> Unit)? = null

    /** Reports a new size or a fresh binding into an already measured cell to the provider. */
    var onSizeChanged: ((WidgetCellView, Int, Int) -> Unit)? = null

    var cornerRadiusPx: Float = 0f
        set(value) {
            field = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
            clipToOutline = field > 0f
            invalidateOutline()
        }

    var hostView: AppWidgetHostView? = null
        private set

    /** The id currently hosted, or 0 while the placeholder shows. */
    var hostedId: Int = 0
        private set

    val placeholder: TextView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(ContextCompat.getColor(context, R.color.home_on_panel))
        setBackgroundResource(R.drawable.bg_home_panel)
        val pad = resources.getDimensionPixelSize(R.dimen.home_panel_padding) / 2
        setPadding(pad, pad, pad, pad)
        minHeight = resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
        text = context.getString(R.string.widgets_unavailable)
        visibility = GONE
    }

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var longPressed = false
    private var dragStarted = false
    private val longPressRunnable = Runnable {
        if (isAttachedToWindow && hasWindowFocus()) {
            longPressed = true
            // The rest of the gesture (menu, then maybe a drag) belongs to this widget, not the pager.
            parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke(this)
        }
    }

    init {
        addView(placeholder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        clipToPadding = false
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val right = (width - paddingRight).coerceAtLeast(paddingLeft)
                val bottom = (height - paddingBottom).coerceAtLeast(paddingTop)
                val radius = cornerRadiusPx.coerceAtMost(minOf(right - paddingLeft, bottom - paddingTop) / 2f)
                outline.setRoundRect(paddingLeft, paddingTop, right, bottom, radius)
            }
        }
    }

    fun showWidget(view: AppWidgetHostView, appWidgetId: Int) {
        if (hostView === view) return
        hostView?.let(::removeView)
        (view.parent as? FrameLayout)?.removeView(view)
        view.setPadding(0, 0, 0, 0) // Chikku's widget padding is applied on this container
        addView(view, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        hostView = view
        hostedId = appWidgetId
        placeholder.visibility = GONE
        // Binding is asynchronous and may finish after the placeholder's final layout. A fresh
        // widget ID still needs its size options even when the cell's geometry never changes.
        if (isLaidOut && width > 0 && height > 0) onSizeChanged?.invoke(this, width, height)
    }

    fun showPlaceholder(text: CharSequence, description: CharSequence) {
        clearWidget()
        placeholder.text = text
        placeholder.contentDescription = description
        placeholder.visibility = VISIBLE
    }

    fun clearWidget() {
        hostView?.let(::removeView)
        hostView = null
        hostedId = 0
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) onSizeChanged?.invoke(this, w, h)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        trackLongPress(ev)
        // After a long press, take the rest of the gesture so the widget does not also get a click.
        return longPressed
    }

    @SuppressLint("ClickableViewAccessibility") // options are exposed as an accessibility action
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // DOWN was already seen by onInterceptTouchEvent; later events of a gesture this view
        // consumes itself (empty widget area) only arrive here.
        if (event.actionMasked != MotionEvent.ACTION_DOWN) trackLongPress(event)
        return true
    }

    private fun trackLongPress(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                dragStarted = false
                downX = ev.rawX
                downY = ev.rawY
                removeCallbacks(longPressRunnable)
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.rawX - downX) > touchSlop || abs(ev.rawY - downY) > touchSlop) {
                removeCallbacks(longPressRunnable)
                if (longPressed && !dragStarted) {
                    dragStarted = true
                    onDragStart?.invoke(this)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN ->
                removeCallbacks(longPressRunnable)
        }
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }

    override fun getAccessibilityClassName(): CharSequence = View::class.java.name
}
