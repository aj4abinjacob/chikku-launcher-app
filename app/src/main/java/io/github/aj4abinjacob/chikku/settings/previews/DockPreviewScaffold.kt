package io.github.aj4abinjacob.chikku.settings.previews

import android.content.Context
import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.DockBackgroundStyle
import io.github.aj4abinjacob.chikku.home.DockImageDrawable
import io.github.aj4abinjacob.chikku.home.DockImageLoader
import kotlinx.coroutines.Dispatchers

// The pieces every dock preview frame (spacing, appearance, home bar, dock + search) shares.

/** The dock's background image bitmap while the dock shows one, loaded off the main thread; null until then. */
@Composable
internal fun rememberDockBackgroundImage(settings: LauncherSettings): State<Bitmap?> {
    val context = LocalContext.current
    val selectedImage = settings.dock.backgroundImageUri.takeIf { settings.dock.enabled && settings.dock.backgroundEnabled }
    return produceState<Bitmap?>(null, selectedImage) {
        value = null
        value = DockImageLoader(context, Dispatchers.IO).load(selectedImage)
    }
}

/** Draws the dock's background (with its image, when one is loaded) on [backdrop], as the home dock does. */
internal fun bindDockBackdrop(backdrop: View, context: Context, dock: DockSettings, image: Bitmap? = null) {
    val shape = DockBackgroundStyle.create(context, dock)
    backdrop.background = shape?.let { if (image != null) DockImageDrawable(it, image) else it }
}

/** Height of a sample dock: its cell row plus its own padding. */
internal val CellLayout.contentHeightWithPadding: Int get() = minCellHeight + paddingTop + paddingBottom

/** Adds [children] to a preview frame as inert views: no click, focus or (optionally) accessibility node. */
internal fun ViewGroup.addInertPreviewChildren(children: List<View>, hideDescendants: Boolean) {
    children.forEach { child ->
        child.isClickable = false
        child.isFocusable = false
        if (hideDescendants) child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(child, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

/** The pale band and dashed edge that shade a measured gap or padding in the dock previews. */
internal class GapMarkPaints(density: Float) {
    val band = Paint().apply { color = 0x47FFFFFF }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
        color = 0xCCFFFFFF.toInt()
        pathEffect = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)
    }
}
