package io.github.aj4abinjacob.chikku.settings.previews

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import io.github.aj4abinjacob.chikku.home.PagerMath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PageEdgeFeedback
import io.github.aj4abinjacob.chikku.core.model.settings.PageIndicatorStyle
import io.github.aj4abinjacob.chikku.core.model.settings.PageMotion
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.PageIndicatorView
import io.github.aj4abinjacob.chikku.home.WorkspacePager
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.delay

/** Three synthetic pages using the real pager, icon cells, page transforms and indicator. No wallpaper service or apps. */
internal class HorizontalPagingPreviewView(context: Context) : LinearLayout(context) {
    val pager = WorkspacePager(context).apply { wallpaperUpdatesEnabled = false }
    val indicator = PageIndicatorView(context)
    var onPreviewPageChanged: ((Int) -> Unit)? = null
    private var effectFrameShown = false
    private var appliedBackground: Int? = null

    init {
        orientation = VERTICAL
        // Rounded like the other pinned samples; clipping the view itself keeps native children inside the corners.
        val corner = 16 * resources.displayMetrics.density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, corner)
        }
        clipToOutline = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(indicator, LayoutParams(LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt()))
        repeat(3) { page ->
            val grid = GridLayout(context).apply { columnCount = 3; rowCount = 1 }
            repeat(3) { index ->
                grid.addView(AppIconView(context, AppIconView.Style.DRAWER).apply {
                    icon = previewSampleIcon(context, page * 3 + index)
                    label = context.getString(PREVIEW_SAMPLES[(page * 3 + index) % PREVIEW_SAMPLES.size].second)
                    iconScale = .75f
                    isClickable = false
                    isLongClickable = false
                    isFocusable = false
                }, GridLayout.LayoutParams(GridLayout.spec(0, 1f), GridLayout.spec(index, 1f)).apply {
                    width = 0; height = LayoutParams.MATCH_PARENT
                })
            }
            pager.addView(grid)
        }
        pager.onPageChanged = { current, count -> indicator.setPages(current, count); onPreviewPageChanged?.invoke(current) }
        pager.onPageScrolled = indicator::setScrollPosition
        pager.onUserScroll = indicator::revealWhileScrolling
        indicator.onPageSelected = { pager.snapToPage(it) }
        indicator.isFocusable = false
        indicator.isFocusableInTouchMode = false
        indicator.setPages(0, 3)
    }

    fun apply(settings: LauncherSettings, drawer: Boolean, backgroundColor: Int, contentColor: Int, showEffectFrame: Boolean = false) {
        val home = settings.home
        val apps = settings.drawer
        val scale = Motion.current(settings.lookFeel.animationSpeed).scale
        pager.snapDurationScale = scale
        pager.swipeSensitivity = if (drawer) apps.pageSwipeSensitivity else home.pageSwipeSensitivity
        pager.horizontalDominance = if (drawer) PageMotion.HORIZONTAL_DOMINANCE else PageMotion.WORKSPACE_HORIZONTAL_DOMINANCE
        pager.snapDurationMs = if (drawer) apps.pageSnapDurationMs else home.pageSnapDurationMs
        pager.effectStrength = if (drawer) apps.pageEffectStrength else home.pageEffectStrength
        pager.edgeFeedback = if (drawer) apps.pageEdgeFeedback else home.pageEdgeFeedback
        pager.transitionEffect = if (drawer) apps.pageTransitionEffect else home.transitionEffect
        pager.infiniteScroll = if (drawer) apps.infiniteScroll else home.infiniteScroll
        indicator.motionScale = scale
        indicator.style = if (drawer) apps.pageIndicatorStyle else home.pageIndicatorStyle
        indicator.autoHide = if (drawer) apps.pageIndicatorAutoHide else home.pageIndicatorAutoHide
        indicator.setColor(contentColor)
        if (showEffectFrame && scale > 0f && pager.width > 0) {
            pager.snapToPage(pager.currentPage, animate = false)
            val logical = pager.currentPage + if (pager.currentPage == 2) -.5f else .5f
            val visual = if (pager.layoutDirection == View.LAYOUT_DIRECTION_RTL) 2 - logical else logical
            pager.scrollTo((visual * pager.width).toInt(), 0)
            effectFrameShown = true
        } else if (effectFrameShown) {
            effectFrameShown = false
            pager.snapToPage(pager.currentPage, animate = false)
        }
        if (backgroundColor != appliedBackground) {
            appliedBackground = backgroundColor
            background = LayerDrawable(arrayOf(wallpaperBackground(), ColorDrawable(backgroundColor)))
        }
        for (page in 0 until pager.childCount) {
            val grid = pager.getChildAt(page) as GridLayout
            for (cell in 0 until grid.childCount) (grid.getChildAt(cell) as AppIconView).setLabelColor(contentColor)
        }
    }
}

/**
 * Framed like the Home → Pages sample: a rounded scene inset from the page edges, over the stand-in
 * wallpaper (Home) or the drawer's effective background (Drawer), with Previous/Next under it.
 */
@Composable
internal fun HorizontalPagingLivePreview(settings: LauncherSettings, drawer: Boolean, showEffectFrame: Boolean = false) {
    val sensitivity = if (drawer) settings.drawer.pageSwipeSensitivity else settings.home.pageSwipeSensitivity
    val duration = if (drawer) settings.drawer.pageSnapDurationMs else settings.home.pageSnapDurationMs
    val effect = if (drawer) settings.drawer.pageTransitionEffect else settings.home.transitionEffect
    var preview by remember { mutableStateOf<HorizontalPagingPreviewView?>(null) }
    var page by remember { mutableIntStateOf(0) }
    val strength = if (drawer) settings.drawer.pageEffectStrength else settings.home.pageEffectStrength
    val edge = if (drawer) settings.drawer.pageEdgeFeedback else settings.home.pageEdgeFeedback
    val loops = if (drawer) settings.drawer.infiniteScroll else settings.home.infiniteScroll
    val indicatorStyle = if (drawer) settings.drawer.pageIndicatorStyle else settings.home.pageIndicatorStyle
    val autoHide = if (drawer) settings.drawer.pageIndicatorAutoHide else settings.home.pageIndicatorAutoHide
    val indicatorDescription = stringResource(R.string.paging_preview_indicator, stringResource(pagingIndicatorLabel(indicatorStyle)),
        stringResource(if (autoHide) R.string.settings_state_on else R.string.settings_state_off))
    val description = stringResource(R.string.paging_preview_description, page + 1,
        stringResource(pagingSensitivityLabel(sensitivity)), duration, stringResource(pagingEffectLabel(effect)),
        (strength * 100).toInt(), stringResource(if (loops) R.string.paging_preview_loop else R.string.paging_preview_no_loop),
        stringResource(pagingEdgeLabel(edge)), indicatorDescription,
        stringResource(if (showEffectFrame && Motion.current(settings.lookFeel.animationSpeed).enabled) R.string.paging_preview_draft else R.string.paging_preview_ready))
    val context = LocalContext.current
    val colors = if (drawer) drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, settings.night, context), settings.drawer)
        else DrawerSampleColors(android.graphics.Color.TRANSPARENT, android.graphics.Color.WHITE)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val move: (Int) -> Unit = { delta -> preview?.pager?.let { it.snapToPage(PagerMath.step(it.currentPage, delta, PREVIEW_PAGES, loops)) } }
    // Duration and effect only show while a page moves, so each change turns one page by itself.
    // The delay waits for a dragged duration to settle instead of restarting the snap every step.
    var shownMotion by remember { mutableStateOf(duration to effect) }
    LaunchedEffect(duration, effect) {
        if (shownMotion == duration to effect) return@LaunchedEffect
        delay(PREVIEW_SETTLE_MS)
        shownMotion = duration to effect
        move(if (!loops && page == PREVIEW_PAGES - 1) -1 else 1)
    }
    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(148.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
                .clearAndSetSemantics { contentDescription = description },
            factory = { HorizontalPagingPreviewView(it).also { view ->
                preview = view
                view.onPreviewPageChanged = { selected -> page = selected }
            } },
            update = { it.apply(settings, drawer, colors.background, colors.content, showEffectFrame) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val previous = stringResource(R.string.preview_page_previous)
            IconButton(onClick = { move(-1) }, enabled = loops || page > 0,
                modifier = Modifier.semantics { contentDescription = previous }) {
                Icon(painterResource(R.drawable.ic_settings_back), null, Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f })
            }
            Text(stringResource(R.string.paging_preview_caption, page + 1, PREVIEW_PAGES),
                Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
            val next = stringResource(R.string.preview_page_next)
            IconButton(onClick = { move(1) }, enabled = loops || page < PREVIEW_PAGES - 1,
                modifier = Modifier.semantics { contentDescription = next }) {
                Icon(painterResource(R.drawable.ic_settings_back), null, Modifier.graphicsLayer { scaleX = if (rtl) 1f else -1f })
            }
        }
    }
}

private const val PREVIEW_PAGES = 3
private const val PREVIEW_SETTLE_MS = 250L

internal fun pagingIndicatorLabel(value: PageIndicatorStyle): Int = when (value) {
    PageIndicatorStyle.DOTS -> R.string.settings_page_indicator_dots
    PageIndicatorStyle.LINE -> R.string.settings_page_indicator_line
    PageIndicatorStyle.NONE -> R.string.settings_page_indicator_none
}

internal fun pagingEdgeLabel(value: PageEdgeFeedback): Int =
    if (value == PageEdgeFeedback.NONE) R.string.paging_edge_none else R.string.paging_edge_resistance

internal fun pagingSensitivityLabel(value: io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity): Int = when (value) {
    io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity.LOW -> R.string.paging_sensitivity_low
    io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity.NORMAL -> R.string.paging_sensitivity_normal
    io.github.aj4abinjacob.chikku.core.model.settings.PageSwipeSensitivity.HIGH -> R.string.paging_sensitivity_high
}

internal fun pagingEffectLabel(value: io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect): Int = when (value) {
    io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect.SLIDE -> R.string.settings_transition_slide
    io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect.FADE -> R.string.settings_transition_fade
    io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect.ZOOM -> R.string.settings_transition_zoom
    io.github.aj4abinjacob.chikku.core.model.settings.PageTransitionEffect.CUBE -> R.string.settings_transition_cube
}
