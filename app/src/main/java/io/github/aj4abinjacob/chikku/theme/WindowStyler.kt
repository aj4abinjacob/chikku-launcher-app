package io.github.aj4abinjacob.chikku.theme

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.Window
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.aj4abinjacob.chikku.core.model.settings.ScreenOrientation
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode

/**
 * Window-level look of HOME: orientation and status bar. Edge-to-edge stays on in every mode, so
 * the activity's inset handling remains the single source of padding; a hidden status bar simply
 * reports a zero top inset, and it can still be revealed with a swipe.
 */
object WindowStyler {

    /** `ActivityInfo.SCREEN_ORIENTATION_*` for [orientation]. Large screens may ignore it (Android 16+). */
    fun requestedOrientation(orientation: ScreenOrientation): Int = when (orientation) {
        ScreenOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ScreenOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        ScreenOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun applyOrientation(activity: Activity, orientation: ScreenOrientation) {
        val requested = requestedOrientation(orientation)
        if (activity.requestedOrientation != requested) activity.requestedOrientation = requested
    }

    /**
     * Shows or hides the status bar. [scrim] paints a translucent band behind the status bar for
     * [StatusBarMode.SHOWN] (status bar colors are ignored by the platform when edge-to-edge).
     */
    fun applyStatusBar(window: Window, mode: StatusBarMode, scrim: StatusBarScrim) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (mode == StatusBarMode.HIDDEN) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        scrim.enabled = mode == StatusBarMode.SHOWN
    }

    /**
     * Whether HOME's status and navigation bar icons should be dark. An open drawer decides by its
     * own surface. Otherwise the icons sit on the wallpaper, except the status bar in
     * [StatusBarMode.SHOWN], which has a dark scrim behind it.
     */
    fun lightSystemBars(drawerOpen: Boolean, drawerLight: Boolean, mode: StatusBarMode, wallpaperDarkText: Boolean): LightBars =
        if (drawerOpen) LightBars(drawerLight, drawerLight)
        else LightBars(status = mode != StatusBarMode.SHOWN && wallpaperDarkText, navigation = wallpaperDarkText)

    data class LightBars(val status: Boolean, val navigation: Boolean)
}

/** A band of [color] over the status-bar inset of [root], drawn in the root's overlay. */
class StatusBarScrim(private val root: View, color: Int = DEFAULT_COLOR) {
    private val drawable = color.toDrawable()
    private var added = false

    var enabled: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                root.overlay.add(drawable)
                added = true
                updateBounds()
            } else if (added) {
                root.overlay.remove(drawable)
                added = false
            }
        }

    init {
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (enabled) updateBounds() }
    }

    private fun updateBounds() {
        val top = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        drawable.setBounds(0, 0, root.width, top)
    }

    companion object {
        const val DEFAULT_COLOR = 0x66000000
    }
}
