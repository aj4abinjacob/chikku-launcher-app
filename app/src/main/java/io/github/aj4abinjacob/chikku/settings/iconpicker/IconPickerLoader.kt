package io.github.aj4abinjacob.chikku.settings.iconpicker

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.IconProvider
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.platform.icons.IconPack
import io.github.aj4abinjacob.chikku.platform.icons.IconPackInfo
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import io.github.aj4abinjacob.chikku.platform.icons.IconStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the picker changes the icon of: an app (stored in its `AppCustomization`), or one pinned
 * shortcut on HOME ([Shortcut.folderId] null) or in a folder (stored on that workspace entry).
 */
internal sealed interface IconTarget {
    /** Stable per target and its current override, for the icon cache. */
    val cacheKey: String

    data class App(val key: AppKey) : IconTarget {
        override val cacheKey: String get() = "app|${key.packageName}/${key.className}#${key.userSerial}"
    }

    /** [override] is the icon it shows now; null is the shortcut's own. Packs map apps, not shortcuts. */
    data class Shortcut(
        val itemId: String,
        val folderId: String?,
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val override: CurrentOverride?,
    ) : IconTarget {
        override val cacheKey: String
            get() = "shortcut|$folderId|$itemId|${override?.packPackage}|${override?.drawableName}"
    }
}

/** A pack opened in the icon grid: its readable drawables and the one it maps to the target app. */
internal class PackContent(val pack: IconPack?, val names: List<String>, val suggested: String?)

/**
 * Everything the picker reads from the platform. Every call does package-manager or resource I/O on
 * [Dispatchers.IO] and turns failures (missing apps, locked profiles, malformed third-party packs)
 * into null/empty results instead of throwing into the UI. Nothing here logs.
 */
internal class IconPickerLoader(
    context: Context,
    private val packs: IconPackRepository,
    private val iconProvider: IconProvider,
    private val shortcutIcons: ShortcutIconLoader,
) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    suspend fun installedPacks(): List<IconPackInfo> = io(emptyList()) { packs.installedPacks() }

    /** The pack app's own launcher icon. */
    suspend fun packAppIcon(packageName: String): Drawable? = io(null) { IconShaper.systemSafe(packageManager.getApplicationIcon(packageName)) }

    /** The pack's icon for [target] if it maps the app, for previews in the pack list. */
    suspend fun packIconFor(packageName: String, target: IconTarget): Drawable? = io(null) {
        val key = (target as? IconTarget.App)?.key ?: return@io null
        val pack = packs.load(packageName) ?: return@io null
        suggestedName(pack, key)?.let(pack::drawable)
    }

    /** Opens a pack for the grid. The suggested drawable is the app's static mapping, else its daily calendar icon. */
    suspend fun packContent(packageName: String, target: IconTarget): PackContent = io(PackContent(null, emptyList(), null)) {
        val pack = packs.load(packageName) ?: return@io PackContent(null, emptyList(), null)
        PackContent(pack, pack.drawableNames(), (target as? IconTarget.App)?.let { suggestedName(pack, it.key) })
    }

    suspend fun packDrawable(pack: IconPack, name: String): Drawable? = io(null) { pack.drawable(name) }

    /** A few of the pack's own icons in one read, so a long pack list does not reload each pack per icon. */
    suspend fun packSamples(packageName: String, count: Int): List<Drawable> = io(emptyList()) {
        val pack = packs.load(packageName) ?: return@io emptyList()
        sampleDrawableNames(pack.filter.components, count * 2).asSequence()
            .mapNotNull(pack::drawable)
            .take(count)
            .toList()
    }

    /** The icon the launcher currently shows for [target], its override included. */
    suspend fun currentIcon(target: IconTarget): Drawable? = when (target) {
        is IconTarget.App -> currentIcon(target.key)
        is IconTarget.Shortcut -> shortcutIcons.load(
            target.packageName, target.shortcutId, target.userSerial, target.override?.packPackage, target.override?.drawableName,
        )
    }

    /** The icon the launcher currently shows for [key], per-app override included. */
    suspend fun currentIcon(key: AppKey): Drawable? = try {
        iconProvider.icon(key)
    } catch (e: CancellationException) {
        throw e
    } catch (_: RuntimeException) {
        null
    }

    /**
     * What [key]'s icon looks like with no per-app override: the global pack's icon or themed
     * fallback, else the system icon in the chosen shape (mirrors the launcher's icon loader, minus
     * daily calendar icons). Null for apps that cannot be resolved, e.g. in a locked profile.
     */
    suspend fun defaultIcon(target: IconTarget, style: IconStyle, sizePx: Int): Drawable? = when (target) {
        is IconTarget.App -> defaultIcon(target.key, style, sizePx)
        // A shortcut's own icon does not follow the icon style (see ShortcutIconLoader).
        is IconTarget.Shortcut -> shortcutIcons.load(target.packageName, target.shortcutId, target.userSerial)
    }

    private suspend fun defaultIcon(key: AppKey, style: IconStyle, sizePx: Int): Drawable? = io(null) {
        val user = userManager?.getUserForSerialNumber(key.userSerial) ?: return@io null
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(key.packageName, key.className))
        val activity = launcherApps?.resolveActivity(intent, user) ?: return@io null
        if (style.isSystemDefault) return@io IconShaper.systemSafe(activity.getBadgedIcon(0))
        val base = activity.getIcon(0)
        val pack = style.iconPack?.let(packs::load)
        val themed = pack?.iconFor(key.packageName, key.className)
            ?: pack?.themedFallback(base, key.packageName.hashCode() * 31 + key.className.hashCode(), sizePx)
            ?: IconShaper.apply(base, style.shape, style.reshapeLegacy)
        packageManager.getUserBadgedIcon(themed, user)
    }

    private fun suggestedName(pack: IconPack, key: AppKey): String? =
        listOfNotNull(
            pack.filter.drawableFor(key.packageName, key.className),
            pack.filter.calendarDrawableFor(key.packageName, key.className, java.time.LocalDate.now().dayOfMonth),
        ).firstOrNull { pack.drawable(it) != null }

    private suspend fun <T> io(fallback: T, block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            fallback
        }
    }
}

/** Rendered icons for this screen, bounded by bytes so a long scroll through a big pack cannot grow without limit. */
internal class IconBitmapCache(maxBytes: Int = DEFAULT_MAX_BYTES) : LruCache<String, ImageBitmap>(maxBytes) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.asAndroidBitmap().allocationByteCount

    companion object {
        const val DEFAULT_MAX_BYTES = 12 * 1024 * 1024
    }
}

/** The pack drawable stored as this app's icon, if any. */
internal data class CurrentOverride(val packPackage: String, val drawableName: String)

/** Human-readable drawable name for search and TalkBack: `ic_google_maps` -> `ic google maps`. */
internal fun displayName(drawableName: String): String = drawableName.replace('_', ' ')

/** Grid caption: [displayName] with its first letter capitalized, e.g. `google_maps` -> `Google maps`. */
internal fun iconLabel(drawableName: String): String =
    displayName(drawableName).trim().replaceFirstChar { it.uppercaseChar() }

/** Case-insensitive match on the drawable name with `_` treated as a space. */
internal fun filterIconNames(names: List<String>, query: String): List<String> {
    val q = query.trim().lowercase().replace('_', ' ')
    if (q.isEmpty()) return names
    return names.filter { displayName(it).lowercase().contains(q) }
}

/** Packs whose label or package name contains [query], ignoring case; a blank query keeps them all. */
internal fun filterPacks(packs: List<IconPackInfo>, query: String): List<IconPackInfo> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return packs
    return packs.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
}

/**
 * Up to [limit] drawable names that show a pack's style: its icons for the same everyday apps first,
 * so rows compare like for like, then whatever else it maps. The apps are a fixed list, never the
 * user's installed apps.
 */
internal fun sampleDrawableNames(components: Map<String, String>, limit: Int): List<String> {
    if (limit <= 0) return emptyList()
    val byPackage = LinkedHashMap<String, String>()
    for ((component, drawable) in components) byPackage.putIfAbsent(component.substringBefore('/'), drawable)
    val common = SAMPLE_APPS.mapNotNull { candidates -> candidates.firstNotNullOfOrNull(byPackage::get) }
    return (common + components.values).distinct().take(limit)
}

/** One entry per everyday app, each listing the package names it ships under on different phones. */
private val SAMPLE_APPS = listOf(
    listOf("com.google.android.dialer", "com.android.dialer", "com.samsung.android.dialer"),
    listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"),
    listOf("com.google.android.GoogleCamera", "com.android.camera2", "com.android.camera", "com.sec.android.app.camera"),
    listOf("com.android.chrome"),
    listOf("com.android.settings"),
    listOf("com.google.android.apps.photos", "com.google.android.gm", "com.google.android.apps.maps"),
)
