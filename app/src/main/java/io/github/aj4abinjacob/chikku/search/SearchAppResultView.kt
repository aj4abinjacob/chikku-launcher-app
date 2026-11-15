package io.github.aj4abinjacob.chikku.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.MenuAnchorIcon
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.theme.Motion

/** Native touch source keeps the original finger-down stream for Android's local drag. */
internal class SearchAppResultView(context: Context, private val compact: Boolean) : LinearLayout(context), MenuAnchorIcon {
    val icon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val title = TextView(context).apply {
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setTextSize(if (compact) 12f else 16f)
    }
    private val suggestion = TextView(context).apply {
        text = context.getString(R.string.search_suggested_match)
        maxLines = 2
        setTextSize(11f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }
    private val priority = TextView(context).apply {
        text = context.getString(R.string.search_top_result)
        maxLines = 2
        setTextSize(11f)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }
    private var app: LauncherApp? = null
    private var onLaunch: ((LauncherApp) -> Unit)? = null
    private var onDrag: ((LauncherApp, View) -> Boolean)? = null
    private var onMenu: ((LauncherApp, View) -> AppMenuHandle?)? = null
    private var pointerDown = false
    private var footprint: Drawable? = null

    init {
        orientation = if (compact) VERTICAL else HORIZONTAL
        gravity = if (compact) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPaddingRelative(dp(if (compact) 4 else 20), dp(if (compact) 12 else 8), dp(if (compact) 4 else 20), dp(if (compact) 12 else 8))
        addView(icon, LayoutParams(dp(40), dp(40)))
        val labels = LinearLayout(context).apply {
            orientation = VERTICAL
            if (compact) gravity = Gravity.CENTER_HORIZONTAL
            addView(priority, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(title, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(suggestion, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        addView(labels, if (compact) LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
            title.gravity = Gravity.CENTER
            suggestion.gravity = Gravity.CENTER
            priority.gravity = Gravity.CENTER
        } else LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        background = attrs.getDrawable(0)
        attrs.recycle()
        setOnClickListener { app?.let { onLaunch?.invoke(it) } }
        // Long-press opens the app menu; moving the finger afterwards closes it and starts the drag.
        LongPressMenuDrag.attach(
            this,
            showMenu = { view -> app?.let { onMenu?.invoke(it, view) } },
            startDrag = { startDrag() },
        )
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.app_menu_open)) { view, _ ->
            app?.let { onMenu?.invoke(it, view) } != null
        }
    }

    private fun startDrag(): Boolean {
        val current = app ?: return false
        // Accessibility/keyboard long-clicks have no continuing pointer to drag. A rejected
        // touch drag still consumes the long press, so releasing cannot also launch the app.
        if (!pointerDown || onDrag == null) return false
        onDrag?.invoke(current, icon)
        return true
    }

    fun bind(app: LauncherApp, drawable: Drawable?, textColor: Int, onLaunch: (LauncherApp) -> Unit, onDrag: (LauncherApp, View) -> Boolean, onMenu: ((LauncherApp, View) -> AppMenuHandle?)? = null, suggested: Boolean = false, topResult: Boolean = false, reserveTopLabel: Boolean = false, motion: Motion = Motion.DEFAULT) {
        val sameApp = this.app?.key == app.key
        if (this.app?.key != null && !sameApp) {
            // A recycled/updated result must not turn a held old row into a different app drag.
            cancelPendingInputEvents()
            isPressed = false
            pointerDown = false
        }
        this.app = app
        this.onLaunch = onLaunch
        this.onDrag = onDrag
        this.onMenu = onMenu
        title.text = app.label
        title.setTextColor(textColor)
        suggestion.visibility = if (suggested) VISIBLE else GONE
        suggestion.setTextColor(textColor)
        // INVISIBLE keeps the line's height when tiles share a row; the text fades with the highlight.
        priority.visibility = if (topResult || reserveTopLabel) VISIBLE else GONE
        priority.animate().cancel()
        val target = if (topResult) 1f else 0f
        val fade = motion.duration(160L)
        if (!sameApp || !isAttachedToWindow || fade == 0L) priority.alpha = target
        else priority.animate().alpha(target).setDuration(fade).start()
        priority.setTextColor(textColor)
        ViewCompat.setStateDescription(this, context.getString(R.string.search_top_result_description).takeIf { topResult })
        contentDescription = if (suggested) "${app.label}, ${suggestion.text}" else app.label
        // Keep the shown icon while the same app's drawable reloads instead of flashing blank.
        if (drawable != null || !sameApp) icon.setImageDrawable(drawable)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) pointerDown = true
        val handled = super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) pointerDown = false
        return handled
    }


    override fun iconBounds(out: Rect) {
        out.set(icon.left, icon.top, icon.right, icon.bottom)
    }

    override fun contentBottom(): Int = height

    override fun setFootprint(footprint: Drawable?) {
        this.footprint = footprint
        invalidate()
    }

    override fun dispatchDraw(canvas: Canvas) {
        footprint?.draw(canvas) // under the icon and labels
        super.dispatchDraw(canvas)
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
