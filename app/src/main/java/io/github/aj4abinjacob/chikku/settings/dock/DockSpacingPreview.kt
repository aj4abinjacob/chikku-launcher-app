package io.github.aj4abinjacob.chikku.settings.dock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DockBackgroundStyle
import io.github.aj4abinjacob.chikku.home.DockImageDrawable
import io.github.aj4abinjacob.chikku.home.DockImageLoader
import io.github.aj4abinjacob.chikku.home.HomeLayoutMetrics
import io.github.aj4abinjacob.chikku.settings.previews.GapMarkPaints
import io.github.aj4abinjacob.chikku.settings.previews.addInertPreviewChildren
import io.github.aj4abinjacob.chikku.settings.previews.bindDockBackdrop
import io.github.aj4abinjacob.chikku.settings.previews.contentHeightWithPadding
import io.github.aj4abinjacob.chikku.settings.previews.rememberDockBackgroundImage
import io.github.aj4abinjacob.chikku.settings.previews.wallpaperBackground
import kotlinx.coroutines.Dispatchers
import kotlin.math.roundToInt

/**
 * The real dock at true size and full screen width, with its spacing shaded, so each spacing step
 * is visible even when the dock has no background. Original sample icons only.
 */
@Composable
fun DockSpacingPreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val image by rememberDockBackgroundImage(settings)
    val description = stringResource(R.string.preview_dock_spacing_description,
        HomeLayoutMetrics.paddingDp(settings.dock.horizontalPadding ?: settings.home.horizontalPadding),
        HomeLayoutMetrics.paddingDp(settings.dock.verticalPadding))
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { DockSpacingPreviewFrame(it) },
            update = { it.show(settings, image) },
        )
        Text(stringResource(R.string.preview_dock_spacing_caption),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
    }
}

/** Height is sized for the largest spacing step, so stepping never moves the controls below. */
internal class DockSpacingPreviewFrame(context: Context) : ViewGroup(context) {
    private val backdrop = View(context)
    internal val dock = CellLayout(context)
    private var bound: LauncherSettings? = null
    private val density = resources.displayMetrics.density
    private val marks = GapMarkPaints(density)
    private val band = marks.band
    private val edge = marks.edge

    init {
        background = wallpaperBackground()
        setWillNotDraw(false)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addInertPreviewChildren(listOf(backdrop, dock), hideDescendants = false)
    }

    fun show(settings: LauncherSettings, image: Bitmap?) {
        if (settings != bound) {
            bound = settings
            bindSampleDock(dock, settings, 0)
        }
        bindDockBackdrop(backdrop, context, settings.dock, image)
        requestLayout()
        invalidate()
    }

    private val dockHeight get() = dock.contentHeightWithPadding

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val largest = HomeLayoutMetrics.paddingPx(PaddingSize.entries.last(), density)
        val envelope = dock.minCellHeight + 2 * largest + 2 * (12 * density).roundToInt()
        setMeasuredDimension(width, resolveSize(envelope, heightMeasureSpec))
        val widthSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val heightSpec = MeasureSpec.makeMeasureSpec(dockHeight, MeasureSpec.EXACTLY)
        dock.measure(widthSpec, heightSpec)
        backdrop.measure(widthSpec, heightSpec)
    }

    /** Top of the dock inside the frame; the dock is centred so spacing grows evenly. */
    internal val dockTop get() = (measuredHeight - dockHeight) / 2

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val y = dockTop
        backdrop.layout(0, y, measuredWidth, y + dockHeight)
        dock.layout(0, y, measuredWidth, y + dockHeight)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Shade the padding the dock applies around its cells, and outline the dock and its cells.
        val l = dock.left.toFloat()
        val t = dock.top.toFloat()
        val r = dock.right.toFloat()
        val b = dock.bottom.toFloat()
        val innerL = l + dock.paddingLeft
        val innerR = r - dock.paddingRight
        val innerT = t + dock.paddingTop
        val innerB = b - dock.paddingBottom
        canvas.drawRect(l, t, r, innerT, band)
        canvas.drawRect(l, innerB, r, b, band)
        canvas.drawRect(l, innerT, innerL, innerB, band)
        canvas.drawRect(innerR, innerT, r, innerB, band)
        val half = edge.strokeWidth / 2
        canvas.drawRect(l + half, t + half, r - half, b - half, edge)
        canvas.drawRect(innerL + half, innerT + half, innerR - half, innerB - half, edge)
    }
}
