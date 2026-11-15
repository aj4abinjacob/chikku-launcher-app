package io.github.aj4abinjacob.chikku.settings.dock

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DockBackground
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DockBackgroundStyle
import io.github.aj4abinjacob.chikku.home.DockImageDrawable
import io.github.aj4abinjacob.chikku.home.DockImageLoader
import io.github.aj4abinjacob.chikku.settings.previews.addInertPreviewChildren
import io.github.aj4abinjacob.chikku.settings.previews.bindDockBackdrop
import io.github.aj4abinjacob.chikku.settings.previews.contentHeightWithPadding
import io.github.aj4abinjacob.chikku.settings.previews.rememberDockBackgroundImage
import io.github.aj4abinjacob.chikku.settings.previews.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/** What a dock page edits, which decides the fixed height its preview reserves. */
enum class DockAppearancePreviewMode { ICONS, BACKGROUND }

/**
 * The real dock at actual size and full screen width, sitting on a sample navigation bar as on the
 * home screen, so icon and name sizes, label font and shadow, background shape, opacity, corners,
 * inset and "behind navigation bar" are all legible. Original sample icons only; nothing launches.
 */
@Composable
fun DockAppearancePreview(settings: LauncherSettings, mode: DockAppearancePreviewMode, modifier: Modifier = Modifier) {
    val dock = settings.dock
    val image by rememberDockBackgroundImage(settings)
    val percent = (dock.backgroundAlpha * 100).roundToInt()
    val description = when (mode) {
        DockAppearancePreviewMode.ICONS -> stringResource(R.string.preview_dock_icons_description,
            (dock.effectiveIconScale(settings.home) * 100).roundToInt(),
            stringResource(if (dock.showLabels) R.string.settings_state_on else R.string.settings_state_off))
        DockAppearancePreviewMode.BACKGROUND -> stringResource(R.string.preview_dock_background_description, when {
            !dock.backgroundEnabled || dock.background == DockBackground.NONE -> stringResource(R.string.preview_dock_background_off)
            dock.drawBehindNavigationBar -> stringResource(R.string.preview_dock_background_behind_navigation, percent)
            else -> stringResource(R.string.preview_dock_background_on, percent)
        })
    }
    AndroidView(
        modifier = modifier.fillMaxWidth().clipToBounds().clearAndSetSemantics { contentDescription = description },
        factory = { DockAppearancePreviewFrame(it) },
        update = { it.show(settings, image, mode) },
    )
}

/**
 * Lays the dock out bottom-up like the home screen: navigation bar, then the dock above it. The
 * height is reserved for the largest value the page can set, so dragging never moves controls.
 */
internal class DockAppearancePreviewFrame(context: Context) : ViewGroup(context) {
    internal val backdrop = View(context)
    internal val dock = CellLayout(context)
    private val navigation = SampleNavigation(context)
    private var bound: LauncherSettings? = null
    private var settings = LauncherSettings()
    private var mode = DockAppearancePreviewMode.ICONS
    private val density = resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).roundToInt()

    init {
        background = wallpaperBackground()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addInertPreviewChildren(listOf(backdrop, dock, navigation), hideDescendants = false)
    }

    fun show(next: LauncherSettings, image: Bitmap?, nextMode: DockAppearancePreviewMode) {
        settings = next
        mode = nextMode
        if (next != bound) {
            bound = next
            bindSampleDock(dock, next, 0)
            // One long sample name makes the single-line and label-size choices visible.
            (dock.getChildAt(dock.childCount - 1) as? AppIconView)?.label = context.getString(R.string.preview_dock_long_label)
        }
        bindDockBackdrop(backdrop, context, next.dock, image)
        requestLayout()
        invalidate()
    }

    private val dockHeight get() = dock.contentHeightWithPadding
    internal val navigationHeight get() = dp(NAVIGATION_DP)

    /** Height the icon and name sliders can reach: largest icon and two lines of the largest name. */
    private fun envelopeDockHeight(): Int = when (mode) {
        DockAppearancePreviewMode.BACKGROUND -> dockHeight
        DockAppearancePreviewMode.ICONS -> maxOf(dockHeight, AppIconView.desiredHeight(context, AppIconView.Style.WORKSPACE,
            LauncherSettings.MAX_SCALE, LauncherSettings.MAX_SCALE, showLabel = true, singleLine = false,
            font = DockLabelFont.MEDIUM) + dock.paddingTop + dock.paddingBottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val envelope = dp(TOP_DP) + envelopeDockHeight() + navigationHeight
        setMeasuredDimension(width, resolveSize(envelope, heightMeasureSpec))
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        dock.measure(widthSpec, MeasureSpec.makeMeasureSpec(dockHeight, MeasureSpec.EXACTLY))
        // As on home, a background drawn behind the navigation bar runs to the bottom of the screen.
        val backdropHeight = dockHeight + if (settings.dock.drawBehindNavigationBar) navigationHeight else 0
        backdrop.measure(widthSpec, MeasureSpec.makeMeasureSpec(backdropHeight, MeasureSpec.EXACTLY))
        navigation.measure(widthSpec, MeasureSpec.makeMeasureSpec(navigationHeight, MeasureSpec.EXACTLY))
    }

    /** Top of the dock inside the frame; it grows upwards from the navigation bar. */
    internal val dockTop get() = measuredHeight - navigationHeight - dockHeight

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val y = dockTop
        backdrop.layout(0, y, measuredWidth, y + backdrop.measuredHeight)
        dock.layout(0, y, measuredWidth, y + dockHeight)
        navigation.layout(0, measuredHeight - navigationHeight, measuredWidth, measuredHeight)
        val visible = if (settings.dock.enabled) VISIBLE else INVISIBLE
        dock.visibility = visible
        backdrop.visibility = visible
    }

    internal val backdropHeight get() = backdrop.measuredHeight

    private companion object {
        const val TOP_DP = 12
        const val NAVIGATION_DP = 24
    }
}
