package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.settings.IconShape
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads only the requested shortcut, with fresh profile/host checks before and after I/O.
 *
 * With [icons], the queried and rendered icon is cached there (rasterised once and shared by every
 * view); the metadata, profile and host checks still run on every request, so a cached icon is
 * never returned once the shortcut's app is locked, its profile is paused or the host permission
 * is gone.
 */
class ShortcutIconLoader(
    context: Context,
    private val metadata: ShortcutMetadataPolicy = ShortcutMetadataPolicy.DENY,
    private val icons: IconProvider? = null,
    /** Pass the app-wide repository to avoid parsing icon-pack appfilters a second time. */
    sharedPacks: IconPackRepository? = null,
) {
    private val packs: IconPackRepository = sharedPacks ?: IconPackRepository(context)
    private val apps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(context)
    private val density = context.resources.displayMetrics.densityDpi

    suspend fun load(packageName: String, id: String, serial: Long, iconPack: String? = null, iconName: String? = null): Drawable? = withContext(Dispatchers.IO) {
        try {
            if (!metadata.mayRead(packageName, serial)) return@withContext null
            val manager = apps ?: return@withContext null
            val user = users?.getUserForSerialNumber(serial) ?: return@withContext null
            if (!profiles.isAvailable(user) || !manager.hasShortcutHostPermission()) return@withContext null
            val read = suspend { read(manager, user, packageName, id, serial, iconPack, iconName) }
            val cache = icons
            val icon = if (cache != null) cache.shortcutIcon(ShortcutIconKey(packageName, id, serial, iconPack, iconName), read) else read()
            if (metadata.mayRead(packageName, serial) && profiles.isAvailable(user) && manager.hasShortcutHostPermission()) icon else null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: RuntimeException) { null }
    }

    private fun read(manager: LauncherApps, user: UserHandle, packageName: String, id: String, serial: Long,
        iconPack: String?, iconName: String?): Drawable? {
        val info = manager.getShortcuts(shortcutQuery(packageName, listOf(id)), user)
            ?.singleOrNull { it.id == id && it.isEnabledFor(packageName, user) }
            ?: return null
        if (!metadata.mayRead(packageName, serial)) return null
        val override = if (iconPack != null && iconName != null) packs.load(iconPack)?.drawable(iconName) else null
        if (!metadata.mayRead(packageName, serial)) return null
        // Icon-pack drawables are complete icons; the app's own may be a bare glyph that needs a plate.
        return override ?: manager.getShortcutIconDrawable(info, density)
            ?.let { IconShaper.normalizeShortcut(it, IconShape.SYSTEM) }
    }
}
