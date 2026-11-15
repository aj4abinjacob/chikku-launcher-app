package io.github.aj4abinjacob.chikku.home

import android.app.WallpaperManager
import android.view.View

/**
 * Forwards HOME touches to the live wallpaper ([WallpaperManager.sendWallpaperCommand]), so
 * wallpapers that ripple on taps or react to drops work under Chikku. Static wallpapers ignore it.
 */
internal object WallpaperCommands {
    fun tap(view: View, screenX: Int, screenY: Int) = send(view, WallpaperManager.COMMAND_TAP, screenX, screenY)

    /** An item was dropped at ([x], [y]) in [view]'s coordinates. */
    fun drop(view: View, x: Float, y: Float) {
        val location = IntArray(2).also(view::getLocationOnScreen)
        send(view, WallpaperManager.COMMAND_DROP, location[0] + x.toInt(), location[1] + y.toInt())
    }

    private fun send(view: View, command: String, x: Int, y: Int) {
        val token = view.windowToken ?: return
        try {
            WallpaperManager.getInstance(view.context).sendWallpaperCommand(token, command, x, y, 0, null)
        } catch (_: RuntimeException) {
            // No wallpaper service, or the window just went away: nothing to tell.
        }
    }
}
