package io.github.aj4abinjacob.chikku.settings.previews

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.drawer.VerticalDrawerRecyclerView
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.SurfaceColors

/**
 * A synthetic drawer list on the real [VerticalDrawerRecyclerView], so flick distance and the end-of-list
 * effect are the drawer's own. No installed apps, launches or wallpaper are used.
 */
internal class VerticalScrollingPreviewView(context: Context) : FrameLayout(context) {
    val list = VerticalDrawerRecyclerView(context)
    private val adapter = SampleAdapter()
    private var appliedBackground: Int? = null
    private val density = resources.displayMetrics.density

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        val corner = 16 * density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, corner)
        }
        clipToOutline = true
        list.layoutManager = GridLayoutManager(context, COLUMNS)
        list.adapter = adapter
        list.clipToPadding = false
        val padding = (8 * density).toInt()
        list.setPadding(padding, padding, padding, padding)
        list.isFocusable = false
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun apply(speed: ListScrollSpeed, edge: ListEdgeEffect, listStyle: Boolean, backgroundColor: Int, contentColor: Int) {
        list.scrollSpeed = speed
        list.edgeEffect = edge
        (list.layoutManager as GridLayoutManager).spanCount = if (listStyle) 1 else COLUMNS
        adapter.update(listStyle, contentColor)
        if (backgroundColor != appliedBackground) {
            appliedBackground = backgroundColor
            background = LayerDrawable(arrayOf(wallpaperBackground(), ColorDrawable(backgroundColor)))
        }
    }

    /** Plays a changed setting: speed flicks into the list, an edge effect flicks against the nearest edge. */
    fun demonstrate(edge: Boolean) {
        val pastMiddle = list.computeVerticalScrollOffset() * 2 > list.computeVerticalScrollRange() - list.computeVerticalScrollExtent()
        flick(down = if (edge) pastMiddle else list.canScrollVertically(1))
    }

    /** The same standard flick every time, so the only difference between tries is the speed setting. */
    fun flick(down: Boolean) {
        list.stopScroll()
        val velocity = (SAMPLE_FLICK_DP_PER_S * density).toInt()
        list.fling(0, if (down) velocity else -velocity)
    }

    private inner class SampleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var listStyle = false
        private var contentColor = android.graphics.Color.WHITE

        fun update(listStyle: Boolean, contentColor: Int) {
            if (listStyle == this.listStyle && contentColor == this.contentColor) return
            this.listStyle = listStyle
            this.contentColor = contentColor
            notifyDataSetChanged()
        }

        override fun getItemCount() = SAMPLE_COUNT
        override fun getItemViewType(position: Int) = if (listStyle) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = AppIconView(parent.context, AppIconView.Style.DRAWER).apply {
                listMode = viewType == 1
                iconScale = .75f
                isClickable = false
                isLongClickable = false
                isFocusable = false
            }
            cell.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ((if (viewType == 1) 44 else 72) * density).toInt())
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder.itemView as AppIconView).apply {
                // Numbered, with each row's glyphs shifted, so rows differ and movement is easy to follow.
                icon = previewSampleIcon(context, position + position / COLUMNS)
                label = context.getString(R.string.vertical_scroll_preview_app, position + 1)
                setLabelColor(contentColor)
            }
        }
    }

    companion object {
        const val COLUMNS = 4
        const val SAMPLE_COUNT = 48
        const val SAMPLE_FLICK_DP_PER_S = 1_800f
    }
}

/** Framed like the horizontal sample: a rounded scene on the drawer's background, with flick buttons under it. */
@Composable
internal fun VerticalScrollingLivePreview(settings: LauncherSettings) {
    val drawer = settings.drawer
    val context = LocalContext.current
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, settings.night, context), drawer)
    var preview by remember { mutableStateOf<VerticalScrollingPreviewView?>(null) }
    var shownSpeed by remember { mutableStateOf(drawer.listScrollSpeed) }
    var shownEdge by remember { mutableStateOf(drawer.listEdgeEffect) }
    LaunchedEffect(drawer.listScrollSpeed, drawer.listEdgeEffect) {
        val edgeChanged = shownEdge != drawer.listEdgeEffect
        if (!edgeChanged && shownSpeed == drawer.listScrollSpeed) return@LaunchedEffect
        shownSpeed = drawer.listScrollSpeed; shownEdge = drawer.listEdgeEffect
        preview?.demonstrate(edge = edgeChanged)
    }
    val description = stringResource(R.string.vertical_scroll_preview_description,
        stringResource(listScrollSpeedLabel(drawer.listScrollSpeed)), stringResource(listEdgeEffectLabel(drawer.listEdgeEffect)))
    Column(Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(164.dp).padding(horizontal = 16.dp)
                .clearAndSetSemantics { contentDescription = description },
            factory = { VerticalScrollingPreviewView(it).also { view -> preview = view } },
            update = { it.apply(drawer.listScrollSpeed, drawer.listEdgeEffect, drawer.style == DrawerStyle.LIST, colors.background, colors.content) },
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val up = stringResource(R.string.vertical_scroll_preview_flick_up)
            IconButton(onClick = { preview?.flick(down = false) }, modifier = Modifier.semantics { contentDescription = up }) {
                Icon(painterResource(R.drawable.ic_settings_back), null, Modifier.graphicsLayer { rotationZ = 90f })
            }
            Text(stringResource(R.string.vertical_scroll_preview_caption), Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 2)
            val down = stringResource(R.string.vertical_scroll_preview_flick_down)
            IconButton(onClick = { preview?.flick(down = true) }, modifier = Modifier.semantics { contentDescription = down }) {
                Icon(painterResource(R.drawable.ic_settings_back), null, Modifier.graphicsLayer { rotationZ = -90f })
            }
        }
    }
}

internal fun listScrollSpeedLabel(value: ListScrollSpeed): Int = when (value) {
    ListScrollSpeed.SLOW -> R.string.vertical_scroll_speed_slow
    ListScrollSpeed.NORMAL -> R.string.vertical_scroll_speed_normal
    ListScrollSpeed.FAST -> R.string.vertical_scroll_speed_fast
}

internal fun listEdgeEffectLabel(value: ListEdgeEffect): Int = when (value) {
    // Android 12 replaced the edge glow with a stretch; name what this phone actually shows.
    ListEdgeEffect.SYSTEM -> if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) R.string.vertical_scroll_edge_system
        else R.string.vertical_scroll_edge_glow
    ListEdgeEffect.NONE -> R.string.vertical_scroll_edge_none
}
