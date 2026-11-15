package io.github.aj4abinjacob.chikku.home.widget

import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil

/**
 * In-app widget picker: a sheet over HOME with providers grouped by app (and profile), each with
 * a preview, label and default size. Tapping an entry adds it at a free spot; touching and holding
 * it starts a drag onto the workspace. The sheet lives in HOME's own window (not a dialog) because
 * a drag's local state never reaches another window.
 *
 * The preview is, in order of preference, the platform-generated preview (API 35+), the provider's
 * `previewLayout` (API 31+), its `previewImage`, or its icon. Layout previews are inflated at the
 * widget's default size on the grid ([cellWidth] × [cellHeight] per cell) and scaled down, so they
 * look like the placed widget. Lookups run off the main thread; RemoteViews are applied on it.
 */
class WidgetPicker(
    private val root: FrameLayout,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val cellWidth: Int,
    private val cellHeight: Int,
    private val onPick: (WidgetProviderEntry) -> Unit,
    /** Touch and hold on an entry: start a drag from [View] (the preview). True if it started. */
    private val onDragOut: (WidgetProviderEntry, View) -> Boolean,
    private val onClosed: () -> Unit,
    /** Style accent; null uses Chikku's default accent (never the device skin's). */
    accentColor: Int? = null,
) {
    private val context: Context = root.context
    private val widgetManager = AppWidgetManager.getInstance(context.applicationContext)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val onPanel = ContextCompat.getColor(context, R.color.home_on_panel)
    private val onPanelVariant = ContextCompat.getColor(context, R.color.home_on_panel_variant)

    private val adapter = Adapter()
    private var contentWidth = context.resources.displayMetrics.widthPixels - dp(32)
    private val grid = GridLayoutManager(context, columnCount(contentWidth)).apply {
        spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int = when (val row = adapter.rows[position]) {
                is Row.Header -> spanCount
                is Row.Entry -> entrySpans(row.entry)
            }
        }
    }
    private val list = object : RecyclerView(context) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            contentWidth = (MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight).coerceAtLeast(1)
            grid.spanCount = columnCount(contentWidth)
            super.onMeasure(widthSpec, heightSpec)
        }
    }.apply {
        layoutManager = grid
        adapter = this@WidgetPicker.adapter
        clipToPadding = false
    }
    private val accent = accentColor ?: CHIKKU_DEFAULT_ACCENT
    private val progress = ProgressBar(context, null, 0, R.style.Widget_Chikku_ProgressBar).apply {
        indeterminateTintList = ColorStateList.valueOf(accent)
    }
    private val empty = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(onPanel)
        setText(R.string.widgets_picker_empty)
        val pad = dp(24)
        setPadding(pad, pad, pad, pad)
        visibility = View.GONE
    }
    private var scrim: View? = null
    private var panel: View? = null
    private var infos: Map<ComponentKey, AppWidgetProviderInfo> = emptyMap()

    val isShowing: Boolean get() = panel != null

    fun show() {
        if (isShowing) return
        val insets = ViewCompat.getRootWindowInsets(root)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val radius = context.resources.getDimension(R.dimen.home_panel_radius)
        val scrimView = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.widgets_picker_scrim))
            contentDescription = context.getString(R.string.widgets_picker_close)
            isFocusable = true
            setOnClickListener { dismiss() }
        }
        val title = TextView(context).apply {
            setText(R.string.widgets_picker_title)
            setTextColor(onPanel)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.home_title_text_size))
            isAccessibilityHeading = true
        }
        val hint = TextView(context).apply {
            setText(R.string.widgets_picker_hint)
            setTextColor(onPanelVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val close = Button(context, null, 0, R.style.Widget_Chikku_Button_Borderless).apply {
            setText(R.string.widgets_picker_close)
            isAllCaps = false
            setTextColor(onPanel)
            minHeight = context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
            setOnClickListener { dismiss() }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPaddingRelative(dp(24), dp(16), dp(8), dp(4))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(hint)
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val body = FrameLayout(context).apply {
            (list.parent as? ViewGroup)?.removeView(list)
            (progress.parent as? ViewGroup)?.removeView(progress)
            (empty.parent as? ViewGroup)?.removeView(empty)
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(progress, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(empty, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
        list.setPadding(dp(16), 0, dp(16), insets?.bottom ?: 0)
        val panelView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.home_panel_background))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            isClickable = true // taps on the sheet's own area do not close it
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        ViewCompat.setAccessibilityPaneTitle(panelView, context.getString(R.string.widgets_picker_title))
        val top = (insets?.top ?: 0) + dp(SHEET_TOP_GAP_DP)
        root.addView(scrimView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(
            panelView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.BOTTOM).apply {
                topMargin = top
                leftMargin = insets?.left ?: 0
                rightMargin = insets?.right ?: 0
            },
        )
        scrim = scrimView
        panel = panelView
        panelView.post { panelView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED) }
    }

    fun setContent(groups: List<WidgetGroup>, providerInfos: Map<ComponentKey, AppWidgetProviderInfo>) {
        infos = providerInfos
        progress.visibility = View.GONE
        empty.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
        adapter.rows = groups.flatMap { g -> listOf<Row>(Row.Header(g)) + g.entries.map { Row.Entry(it) } }
    }

    /**
     * Hides the sheet while a drag from it runs (it must stay attached: the drag started from one
     * of its views). [hidden] false shows it again, e.g. when the drag could not start.
     */
    fun setHiddenForDrag(hidden: Boolean) {
        val visibility = if (hidden) View.GONE else View.VISIBLE
        scrim?.visibility = visibility
        panel?.visibility = visibility
    }

    fun dismiss() {
        if (!isShowing) return
        for (i in 0 until list.childCount) (list.getChildViewHolder(list.getChildAt(i)) as? EntryHolder)?.recycle()
        scrim?.let(root::removeView)
        panel?.let(root::removeView)
        scrim = null
        panel = null
        onClosed()
    }

    private sealed interface Row {
        data class Header(val group: WidgetGroup) : Row
        data class Entry(val entry: WidgetProviderEntry) : Row
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var rows: List<Row> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged") // the list is set once per picker
                notifyDataSetChanged()
            }

        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == 0) HeaderHolder(TextView(context)) else EntryHolder(LinearLayout(context))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).bind(row.group)
                is Row.Entry -> (holder as EntryHolder).bind(row.entry)
            }
        }

        override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
            (holder as? EntryHolder)?.recycle()
        }
    }

    private inner class HeaderHolder(private val text: TextView) : RecyclerView.ViewHolder(text) {
        init {
            text.setPaddingRelative(dp(8), dp(12), dp(8), dp(4))
            text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            text.setTypeface(text.typeface, android.graphics.Typeface.BOLD)
            text.setTextColor(onPanel)
            text.isFocusable = false
            text.isAccessibilityHeading = true
        }

        fun bind(group: WidgetGroup) {
            text.text = if (group.isOtherProfile) {
                context.getString(R.string.widgets_picker_work_group, group.appLabel)
            } else {
                group.appLabel
            }
        }
    }

    private inner class EntryHolder(private val row: LinearLayout) : RecyclerView.ViewHolder(row) {
        private var naturalW = 1
        private var naturalH = 1
        private var remotePreview: View? = null
        /** Holds the scaled preview; swallows touches so a preview's own click targets never fire. */
        private val preview = object : FrameLayout(context) {
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val scale = minOf(
                    MeasureSpec.getSize(widthSpec).coerceAtLeast(1).toFloat() / naturalW,
                    dp(PREVIEW_MAX_HEIGHT_DP).toFloat() / naturalH,
                    1f,
                )
                remotePreview?.apply {
                    scaleX = scale
                    scaleY = scale
                }
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec((naturalW * scale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((naturalH * scale).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
                )
            }
        }.apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            clipChildren = true
        }
        private val label = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(onPanel)
        }
        private val size = TextView(context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(onPanelVariant)
        }
        private var job: Job? = null

        init {
            row.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            row.orientation = LinearLayout.VERTICAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            row.minimumHeight = context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target)
            row.setPaddingRelative(dp(8), dp(8), dp(8), dp(8))
            row.isClickable = true
            row.isFocusable = true
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            row.setBackgroundResource(ripple.resourceId)
            row.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            val texts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPaddingRelative(0, dp(8), 0, 0)
                addView(label)
                addView(size)
            }
            row.addView(texts, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun bind(entry: WidgetProviderEntry) {
            label.text = entry.label
            size.text = context.getString(R.string.widgets_picker_size, entry.spanX, entry.spanY)
            row.contentDescription = context.getString(R.string.widgets_picker_entry_description, entry.label, entry.spanX, entry.spanY)
            row.setOnClickListener {
                dismiss()
                onPick(entry)
            }
            row.setOnLongClickListener { onDragOut(entry, preview) }
            job?.cancel()
            remotePreview = null
            preview.removeAllViews()
            // The widget's natural size on the grid, scaled to fit the preview box.
            naturalW = (entry.spanX * cellWidth).coerceAtLeast(1)
            naturalH = (entry.spanY * cellHeight).coerceAtLeast(1)
            preview.requestLayout()
            val info = infos[entry.key] ?: return
            job = scope.launch {
                val remote = withContext(io) { loadRemotePreview(info, entry.key.userSerial) }
                val applied = remote?.let { applyRemotePreview(it, naturalW, naturalH) }
                if (applied != null) {
                    remotePreview = applied
                    preview.addView(applied)
                    return@launch
                }
                val drawable = withContext(io) { loadImagePreview(info) }
                preview.addView(
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setImageDrawable(drawable)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
            }
        }

        fun recycle() {
            job?.cancel()
            job = null
            remotePreview = null
            preview.removeAllViews()
        }

        /** Inflates [views] at the widget's natural size and scales it into the preview box; null on failure. */
        private fun applyRemotePreview(views: RemoteViews, naturalW: Int, naturalH: Int): View? = try {
            views.apply(context, preview).apply {
                layoutParams = FrameLayout.LayoutParams(naturalW, naturalH)
                pivotX = 0f
                pivotY = 0f
            }
        } catch (_: RuntimeException) {
            null // a broken third-party layout falls back to the image preview
        } catch (_: OutOfMemoryError) {
            null
        }

    }

    private fun columnCount(width: Int): Int {
        val minWidth = (dp(160) * context.resources.configuration.fontScale.coerceAtLeast(1f)).toInt()
        return (width / minWidth).coerceIn(1, 4)
    }

    private fun entrySpans(entry: WidgetProviderEntry): Int {
        val naturalW = (entry.spanX * cellWidth).coerceAtLeast(1)
        val naturalH = (entry.spanY * cellHeight).coerceAtLeast(1)
        val previewWidth = naturalW * minOf(dp(PREVIEW_MAX_HEIGHT_DP).toFloat() / naturalH, 1f)
        val columnWidth = contentWidth.toFloat() / grid.spanCount
        return ceil((previewWidth + dp(16)) / columnWidth).toInt().coerceIn(1, grid.spanCount)
    }

    /** The generated (API 35+) or layout (API 31+) preview, if the provider declares one. */
    private fun loadRemotePreview(info: AppWidgetProviderInfo, userSerial: Long): RemoteViews? = try {
        generatedPreview(info, userSerial)
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.previewLayout != 0) {
                RemoteViews(info.provider.packageName, info.previewLayout)
            } else {
                null
            }
    } catch (_: RuntimeException) {
        null
    }

    private fun generatedPreview(info: AppWidgetProviderInfo, userSerial: Long): RemoteViews? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        val category = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        if (info.generatedPreviewCategories and category == 0) return null
        val user: UserHandle = userManager?.getUserForSerialNumber(userSerial) ?: return null
        return widgetManager?.getWidgetPreview(info.provider, user, category)
    }

    private fun loadImagePreview(info: AppWidgetProviderInfo): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        return try {
            info.loadPreviewImage(context, density) ?: info.loadIcon(context, density)?.let { IconShaper.systemSafe(it) }
        } catch (_: RuntimeException) {
            null
        } catch (_: OutOfMemoryError) {
            null // an oversized third-party preview must not take HOME down
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val PREVIEW_MAX_HEIGHT_DP = 160
        const val SHEET_TOP_GAP_DP = 48
    }
}
