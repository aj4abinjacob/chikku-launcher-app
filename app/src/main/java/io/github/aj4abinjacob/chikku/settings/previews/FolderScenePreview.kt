package io.github.aj4abinjacob.chikku.settings.previews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.IconLabelStyle
import io.github.aj4abinjacob.chikku.home.folder.FolderPanelRules
import io.github.aj4abinjacob.chikku.home.folder.FolderGridLayout
import io.github.aj4abinjacob.chikku.home.folder.FolderIconDrawable
import io.github.aj4abinjacob.chikku.home.folder.FolderPopup
import io.github.aj4abinjacob.chikku.home.folder.folderPageWindow
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** What the open sample folder shows: the same column, paging and order rules as FolderPopup. */
internal data class FolderSceneLayout(val columns: Int, val order: List<Int>, val page: Int, val pages: Int)

internal fun folderSceneLayout(folders: FolderSettings, labels: List<String>): FolderSceneLayout {
    val columns = if (folders.columns == 0) FolderPopup.columnsFor(labels.size) else folders.columns.coerceIn(1, 6)
    val order = previewFolderOrder(folders.sortOrder, labels)
    if (folders.scrollStyle != FolderScrollStyle.HORIZONTAL_PAGED) return FolderSceneLayout(columns, order, 0, 1)
    val window = folderPageWindow(labels.size, columns, folders.rows, 0)
    return FolderSceneLayout(columns, order.subList(window.start, window.end), window.page, window.pages)
}

/**
 * Two labelled tiles over a stand-in wallpaper: the folder as it sits on the home screen, and the
 * same folder opened. Both are drawn by the launcher's own views ([FolderIconDrawable],
 * [AppIconView], [FolderGridLayout]) so every folder setting shows up where it applies.
 * [folders] carries the effective icon scale and may be a draft following a slider. The height
 * never depends on a setting, so the controls below stay put. Sample apps only.
 */
@Composable
fun FolderScenePreview(folders: FolderSettings, night: NightSettings, home: HomeSettings, modifier: Modifier = Modifier) {
    val config = LocalConfiguration.current
    val context = LocalContext.current
    val sceneHeight = previewSceneHeight(config.screenHeightDp, config.fontScale, 180f).dp
    val closedWidth = (96f * config.fontScale.coerceIn(1f, 1.3f)).dp
    val themePanel = folderThemePanelColor(context, night)
    val labels = folderSceneLabels(context)
    val layout = folderSceneLayout(folders, labels)
    val description = stringResource(R.string.folder_scene_description, layout.columns)
    val captionStyle = MaterialTheme.typography.labelMedium
    val captionColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.folder_scene_closed), Modifier.width(closedWidth), style = captionStyle,
                color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.folder_scene_open), Modifier.weight(1f), style = captionStyle,
                color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().height(sceneHeight), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AndroidView(
                factory = { FolderClosedTile(it) },
                update = { it.bind(folders, layout.order, home) },
                modifier = Modifier.width(closedWidth).height(sceneHeight),
            )
            AndroidView(
                factory = { FolderOpenStage(it) },
                update = { it.bind(folders, themePanel, labels, layout) },
                modifier = Modifier.weight(1f).height(sceneHeight),
            )
        }
    }
}

/** Shuffled generic names, so alphabetical and manual order look different. */
internal fun folderSceneLabels(context: Context): List<String> = intArrayOf(
    R.string.preview_gap_sample_zulu, R.string.preview_gap_sample_alpha, R.string.folder_scene_sample_kilo,
    R.string.preview_gap_sample_delta, R.string.folder_scene_sample_echo, R.string.preview_gap_sample_beta,
).map(context::getString)

/** A rounded wallpaper tile that keeps its children (and their shadows) inside. */
private fun FrameLayout.asWallpaperTile() {
    background = wallpaperBackground()
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    val radius = 16f * resources.displayMetrics.density
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, radius)
    }
    clipToOutline = true
}

private fun sampleCell(context: Context, style: AppIconView.Style) = AppIconView(context, style).apply {
    isClickable = false
    isLongClickable = false
    isFocusable = false
}

/** The closed folder as the home screen draws it, with the sample apps' icons inside. */
@SuppressLint("ViewConstructor")
internal class FolderClosedTile(context: Context) : FrameLayout(context) {
    val cell = sampleCell(context, AppIconView.Style.WORKSPACE)

    init {
        asWallpaperTile()
        addView(cell, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    /** [home] supplies the label and icon fields the workspace applies to every icon (see WorkspaceController.applySizing). */
    fun bind(folders: FolderSettings, order: List<Int>, home: HomeSettings) {
        val previews = order.take(folders.previewCount.coerceAtLeast(1)).map { previewSampleIcon(context, it) }
        cell.icon = FolderIconDrawable(previews, folders.previewStyle, folders.iconBackground,
            folders.iconBackgroundColor ?: ContextCompat.getColor(context, R.color.folder_icon_background))
        cell.label = context.getString(R.string.settings_folder_preview_name)
        IconLabelStyle.home(home).applyTo(cell)
    }
}

/**
 * The open folder panel built as FolderPopup builds it, at the size it has on this phone, then
 * scaled down to fit the tile. Immersive windows fill the tile; floating ones sit in the middle.
 */
@SuppressLint("ViewConstructor")
internal class FolderOpenStage(context: Context) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val inset = (8 * density).roundToInt()
    private var immersive = false
    internal var panel: LinearLayout? = null
        private set
    internal var grid: FolderGridLayout? = null
        private set
    internal var name: TextView? = null
        private set
    internal var pageIndicator: TextView? = null
        private set

    init { asWallpaperTile() }

    fun bind(folders: FolderSettings, themePanel: Int, labels: List<String>, layout: FolderSceneLayout) {
        removeAllViews()
        immersive = folders.windowStyle == FolderWindowStyle.IMMERSIVE
        val res = resources
        val padding = res.getDimensionPixelSize(R.dimen.home_panel_padding)
        val touch = res.getDimensionPixelSize(R.dimen.home_min_touch_target)
        val base = folders.backgroundColor ?: themePanel
        val alpha = folders.backgroundAlpha.coerceIn(0f, 1f)
        val dark = FolderPanelRules.isDark(base, alpha)
        val textColor = if (dark) SurfaceColors.DARK.onSurface else SurfaceColors.LIGHT.onSurface
        val iconStyle = FolderPanelRules.iconStyle(alpha)

        val cellHeight = FolderPanelRules.cellHeight(context, iconStyle, folders)
        val cellWidth = FolderPanelRules.cellWidth(res, folders, padding)
        val gridView = FolderGridLayout(context, cellWidth, cellHeight).apply { columns = layout.columns }
        layout.order.forEach { index ->
            gridView.addView(sampleCell(context, iconStyle).apply {
                icon = previewSampleIcon(context, index)
                label = labels[index]
                IconLabelStyle.folder(folders, folders.labelColor ?: textColor.takeIf { iconStyle == AppIconView.Style.DRAWER }).applyTo(this)
            })
        }
        val nameView = TextView(context).apply {
            text = context.getString(R.string.settings_folder_preview_name)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.folder_title_text_size))
            gravity = if (immersive) Gravity.START or Gravity.CENTER_VERTICAL else Gravity.CENTER
            isSingleLine = true
            minHeight = touch
            setPadding(padding, 0, padding, 0)
            visibility = if (folders.showName) View.VISIBLE else View.GONE
        }
        val pager = if (folders.scrollStyle == FolderScrollStyle.HORIZONTAL_PAGED) TextView(context).apply {
            text = context.getString(R.string.folder_page_indicator, layout.page + 1, layout.pages)
            setTextColor(ColorUtils.setAlphaComponent(textColor, 200))
            gravity = Gravity.CENTER
            minHeight = touch
        } else null
        val panelView = LinearLayout(context).apply {
            FolderPanelRules.styleFrame(this, res, folders, immersive, padding, FolderPanelRules.panelColor(base, alpha), alpha, textColor)
            val full = ViewGroup.LayoutParams.MATCH_PARENT
            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
            if (immersive) addView(nameView, LinearLayout.LayoutParams(full, wrap))
            addView(gridView, LinearLayout.LayoutParams(full, wrap))
            pager?.let { addView(it, LinearLayout.LayoutParams(full, wrap)) }
            if (!immersive) addView(nameView, LinearLayout.LayoutParams(full, wrap))
        }
        addView(panelView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        panel = panelView
        grid = gridView
        name = nameView
        pageIndicator = pager
    }

    /** The panel's width on this phone: the screen less the panel margins, as FolderPopup sizes it. */
    private fun phonePanelWidth(): Int =
        resources.displayMetrics.widthPixels - 2 * resources.getDimensionPixelSize(R.dimen.home_panel_margin)

    internal var scale = 1f
        private set

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        val panelView = panel ?: return
        val roomWidth = (width - 2 * inset).coerceAtLeast(1)
        val roomHeight = (height - 2 * inset).coerceAtLeast(1)
        val phoneWidth = phonePanelWidth().coerceAtLeast(roomWidth)
        if (immersive) {
            // Fills the phone's width; its height is whatever the tile leaves at that scale.
            scale = roomWidth.toFloat() / phoneWidth
            panelView.measure(MeasureSpec.makeMeasureSpec(phoneWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((roomHeight / scale).toInt(), MeasureSpec.EXACTLY))
        } else {
            panelView.measure(MeasureSpec.makeMeasureSpec(phoneWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            scale = min(1f, min(roomWidth.toFloat() / panelView.measuredWidth, roomHeight.toFloat() / panelView.measuredHeight))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val panelView = panel ?: return
        panelView.layout(0, 0, panelView.measuredWidth, panelView.measuredHeight)
        panelView.pivotX = 0f
        panelView.pivotY = 0f
        panelView.scaleX = scale
        panelView.scaleY = scale
        panelView.translationX = (width - panelView.measuredWidth * scale) / 2f
        panelView.translationY = (height - panelView.measuredHeight * scale) / 2f
    }
}
