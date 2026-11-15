package io.github.aj4abinjacob.chikku.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Follows whether the home wallpaper asks for dark text ([WallpaperColors.HINT_SUPPORTS_DARK_TEXT]),
 * so status and navigation bar icons stay readable over a light wallpaper. Reads the colours off
 * the main thread (IPC) and listens for wallpaper changes while [owner] is alive.
 */
class WallpaperTextColor(
    private val owner: LifecycleOwner,
    /** Called on create, once the activity has a context. */
    private val managerProvider: () -> WallpaperManager?,
    private val onChanged: () -> Unit,
) : DefaultLifecycleObserver {
    var darkText: Boolean = false
        private set

    private var manager: WallpaperManager? = null

    private val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
        if (which and WallpaperManager.FLAG_SYSTEM != 0) update(colors)
    }

    init {
        owner.lifecycle.addObserver(this)
    }

    override fun onCreate(owner: LifecycleOwner) {
        val manager = runCatching(managerProvider).getOrNull() ?: return
        this.manager = manager
        owner.lifecycleScope.launch {
            val colors = withContext(Dispatchers.IO) {
                runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
            }
            update(colors)
        }
        runCatching { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        runCatching { manager?.removeOnColorsChangedListener(listener) }
    }

    private fun update(colors: WallpaperColors?) {
        val dark = supportsDarkText(colors)
        if (dark == darkText) return
        darkText = dark
        onChanged()
    }

    companion object {
        fun supportsDarkText(colors: WallpaperColors?): Boolean = when {
            colors == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            // Android 10-11 keep the hints hidden; judge by the wallpaper's main colour instead.
            else -> colors.primaryColor.luminance() > LIGHT_LUMINANCE
        }

        private const val LIGHT_LUMINANCE = 0.5f
    }
}
