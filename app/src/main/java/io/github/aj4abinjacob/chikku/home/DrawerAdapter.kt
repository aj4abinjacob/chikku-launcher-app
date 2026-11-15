package io.github.aj4abinjacob.chikku.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.LongPressMenuDrag

/** Callbacks from drawer items; the activity forwards them to the ViewModel. */
interface DrawerItemListener {
    fun onAppClick(app: LauncherApp, view: View)
    fun onAppLongClick(app: LauncherApp, view: View): Boolean
    fun onAddToHome(app: LauncherApp)
    fun onAppInfo(app: LauncherApp, view: View)

    /** Long-press menu for [app]; null means "no menu", and the long-press starts a drag at once. */
    fun onAppMenu(app: LauncherApp, view: View): AppMenuHandle? = null
}

/** How drawer cells look (from `LauncherSettings.drawer`). [labelColor] null keeps the style's color. */
data class DrawerAppearance(
    val iconScale: Float = 1f,
    val showLabels: Boolean = true,
    val labelColor: Int? = null,
    val labelScale: Float = 1f,
    val labelFont: DockLabelFont = DockLabelFont.NORMAL,
    val labelShadow: Boolean = false,
    val labelSingleLine: Boolean = true,
    val listMode: Boolean = false,
) {
    fun applyTo(view: AppIconView) = IconLabelStyle(
        iconScale = iconScale, showLabels = showLabels, labelScale = labelScale, labelFont = labelFont,
        labelShadow = labelShadow, labelSingleLine = labelSingleLine, labelColor = labelColor, listMode = listMode,
    ).applyTo(view)

    companion object {
        /**
         * Content height of one drawer cell (never below a touch target): the minimum row slot that
         * DrawerController and the Settings grid preview both size rows from.
         */
        fun cellContentHeight(context: Context, drawer: DrawerSettings, iconScale: Float): Int =
            AppIconView.desiredHeight(
                context, AppIconView.Style.DRAWER, iconScale, drawer.labelScale, drawer.showLabels,
                drawer.labelSingleLine, drawer.labelFont, drawer.style == DrawerStyle.LIST,
            ).coerceAtLeast(context.resources.getDimensionPixelSize(R.dimen.home_min_touch_target))
    }
}

/**
 * Wires click, long-press and accessibility actions of a drawer cell once; [current] returns the app
 * bound to the cell at the time of the event.
 */
internal fun AppIconView.attachDrawerActions(listener: DrawerItemListener, current: () -> LauncherApp?) {
    setOnClickListener { current()?.let { listener.onAppClick(it, this) } }
    // Long-press opens the app menu; moving the finger afterwards closes it and starts the drag.
    LongPressMenuDrag.attach(
        this,
        showMenu = { current()?.let { listener.onAppMenu(it, this) } },
        startDrag = { current()?.let { listener.onAppLongClick(it, this) } ?: false },
    )
    ViewCompat.addAccessibilityAction(this, resources.getString(R.string.home_action_add_to_home)) { _, _ ->
        current()?.let { listener.onAddToHome(it) }
        true
    }
    ViewCompat.addAccessibilityAction(this, resources.getString(R.string.home_action_app_info)) { _, _ ->
        current()?.let { listener.onAppInfo(it, this) }
        true
    }
    ViewCompat.addAccessibilityAction(this, resources.getString(R.string.app_menu_open)) { _, _ ->
        current()?.let { listener.onAppMenu(it, this) } != null
    }
}

internal fun IconBinder.bindDrawerApp(view: AppIconView, app: LauncherApp) =
    bindApp(view, app.key, app.label, app.profileType == ProfileType.WORK, Availability.AVAILABLE)

/** Vertical app grid (order comes from the repository or the tab). Stable ids are derived from [AppKey]. */
class DrawerAdapter(
    private val iconBinder: IconBinder,
    private val listener: DrawerItemListener,
) : ListAdapter<LauncherApp, DrawerAdapter.Holder>(Diff) {

    // Collision-free stable ids: each key gets the next number the first time it is seen.
    private val ids = HashMap<AppKey, Long>()

    var appearance: DrawerAppearance = DrawerAppearance()
        @SuppressLint("NotifyDataSetChanged") // every visible cell changes size or color
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    /** Fixed scrolling-row slot height, or WRAP_CONTENT for automatic spacing. */
    var rowHeightPx: Int = ViewGroup.LayoutParams.WRAP_CONTENT
        @SuppressLint("NotifyDataSetChanged") // every row receives the new slot height
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = ids.getOrPut(getItem(position).key) { ids.size.toLong() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = AppIconView(parent.context, AppIconView.Style.DRAWER)
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    override fun onViewRecycled(holder: Holder) {
        iconBinder.cancel(holder.view)
    }

    inner class Holder(val view: AppIconView) : RecyclerView.ViewHolder(view) {
        private var app: LauncherApp? = null

        init {
            view.attachDrawerActions(listener) { app }
        }

        fun bind(item: LauncherApp) {
            app = item
            appearance.applyTo(view)
            if (view.layoutParams.height != rowHeightPx) {
                view.layoutParams = view.layoutParams.apply { height = rowHeightPx }
            }
            iconBinder.bindDrawerApp(view, item)
        }
    }

    private object Diff : DiffUtil.ItemCallback<LauncherApp>() {
        override fun areItemsTheSame(oldItem: LauncherApp, newItem: LauncherApp) = oldItem.key == newItem.key
        override fun areContentsTheSame(oldItem: LauncherApp, newItem: LauncherApp) = oldItem == newItem
    }
}
