package io.github.aj4abinjacob.chikku.platform.icons

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.util.Xml
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs
import kotlin.math.roundToInt

/** An installed icon pack as offered in settings. The label is shown to the user only, never logged. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded third-party icon pack. Icons and fallback layers stay in the pack's own APK and are read
 * through its [Resources]; Chikku bundles none of them.
 */
class IconPack internal constructor(
    val packageName: String,
    private val resources: Resources,
    val filter: AppFilter,
) {
    /** Drawable by resource name, or null if the pack does not contain it. */
    @SuppressLint("DiscouragedApi") // Icon packs are addressed by resource name; there is no id contract.
    fun drawable(name: String): Drawable? {
        val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: resources.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        return try {
            resources.getDrawable(id, null)
        } catch (_: Resources.NotFoundException) {
            null
        } catch (_: RuntimeException) {
            null // malformed drawable in a third-party pack
        }
    }

    /** The pack's own icon for an activity, if it maps it. */
    fun iconFor(packageName: String, className: String, day: Int = java.time.LocalDate.now().dayOfMonth): Drawable? =
        filter.calendarDrawableFor(packageName, className, day)?.let(::drawable)
            ?: filter.drawableFor(packageName, className)?.let(::drawable)

    /**
     * Themes an unmapped icon with the pack's `iconback` / `iconmask` / `iconupon` / `scale`, or
     * returns null if the pack defines no such fallback. [seed] picks a stable `iconback`.
     */
    fun themedFallback(original: Drawable, seed: Int, sizePx: Int): Drawable? {
        if (!filter.hasFallbackTheme || sizePx <= 0) return null
        val back = filter.iconBacks.takeIf { it.isNotEmpty() }?.let { it[abs(seed % it.size)] }?.let(::drawable)
        val mask = filter.iconMask?.let(::drawable)
        val upon = filter.iconUpon?.let(::drawable)
        if (back == null && mask == null && upon == null) return null

        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val scaled = (sizePx * filter.scale).roundToInt().coerceIn(1, sizePx)
        val offset = (sizePx - scaled) / 2
        canvas.drawBitmap(original.toBitmap(scaled, scaled), offset.toFloat(), offset.toFloat(), null)
        mask?.let {
            // Opaque mask pixels cut the original icon away.
            canvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            })
        }
        back?.let {
            canvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
            })
        }
        upon?.let { canvas.drawBitmap(it.toBitmap(sizePx, sizePx), 0f, 0f, null) }
        return bitmap.toDrawable(resources)
    }

    /**
     * Every drawable the pack offers for manual selection: `res/xml/drawable.xml` when present,
     * otherwise the drawables named in `appfilter.xml`. Only names that resolve are returned.
     */
    @SuppressLint("DiscouragedApi")
    fun drawableNames(): List<String> {
        val listed = readXml(resources, packageName, "drawable") { AppFilterParser.parseDrawableList(it) }
            ?.takeIf { it.isNotEmpty() }
            ?: filter.components.values.distinct()
        return listed.filter { resources.getIdentifier(it, "drawable", packageName) != 0 }
    }
}

/**
 * Discovers and loads icon packs. All methods do package-manager or resource I/O: call them off the
 * main thread. Loaded packs are kept in a small cache until [invalidate] (package changed/removed).
 */
class IconPackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val packs = LruCache<String, IconPack>(MAX_LOADED_PACKS)
    private val loadLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    private val invalidations = java.util.concurrent.atomic.AtomicLong()

    /** Installed icon packs, de-duplicated and sorted by label. */
    fun installedPacks(): List<IconPackInfo> {
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in ICON_PACK_ACTIONS) {
            val matches = try {
                packageManager.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (_: RuntimeException) {
                emptyList()
            }
            for (info in matches) {
                val pkg = info.activityInfo?.packageName ?: continue
                if (pkg == appContext.packageName || pkg in found) continue
                val label = try {
                    info.activityInfo.applicationInfo.loadLabel(packageManager).toString()
                } catch (_: RuntimeException) {
                    pkg
                }
                found[pkg] = IconPackInfo(pkg, label)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /** Loads (or returns the cached) pack; null if it is not installed or cannot be read. */
    fun load(packageName: String): IconPack? {
        packs.get(packageName)?.let { return it }
        // One parse per pack: parallel icon loads wait on this pack's lock, other packs are unaffected.
        synchronized(loadLocks.getOrPut(packageName) { Any() }) {
            packs.get(packageName)?.let { return it }
            val startVersion = invalidations.get()
            val pack = parse(packageName) ?: return null
            // An invalidate that ran during the parse may have made it stale: use it, but do not keep it.
            // Put first, then re-check, so an invalidate between the two cannot leave it cached.
            packs.put(packageName, pack)
            if (invalidations.get() != startVersion) packs.remove(packageName)
            return pack
        }
    }

    private fun parse(packageName: String): IconPack? {
        val resources = try {
            packageManager.getResourcesForApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        } catch (_: RuntimeException) {
            return null
        }
        val filter = readXml(resources, packageName, "appfilter") { AppFilterParser.parse(it) } ?: AppFilter.EMPTY
        return IconPack(packageName, resources, filter)
    }

    /** Forget a pack after it was updated or uninstalled. Returns true if it was loaded. */
    fun invalidate(packageName: String): Boolean {
        invalidations.incrementAndGet()
        return packs.remove(packageName) != null
    }

    companion object {
        private const val MAX_LOADED_PACKS = 4

        /**
         * Intent actions icon packs declare so compatible launchers can find them. Keep these
         * third-party protocol identifiers unchanged; mirrored by `<queries>` in the manifest.
         */
        val ICON_PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.gau.go.launcherex.theme",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.anddoes.launcher.THEME",
            "com.actionlauncher.playstore.ICON_PACK",
            "com.fede.launcher.THEME_ICONPACK",
            "com.dlto.atom.launcher.THEME",
        )
    }
}

/** Reads `res/xml/<name>.xml`, falling back to `assets/<name>.xml`. Null if absent or unreadable. */
@SuppressLint("DiscouragedApi")
private fun <T> readXml(resources: Resources, packageName: String, name: String, read: (XmlPullParser) -> T): T? {
    try {
        val id = resources.getIdentifier(name, "xml", packageName)
        if (id != 0) {
            resources.getXml(id).use { return read(it) }
        }
    } catch (_: Exception) {
        // fall through to assets
    }
    return try {
        resources.assets.open("$name.xml").use { stream ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(stream, null)
            read(parser)
        }
    } catch (_: Exception) {
        null
    }
}
