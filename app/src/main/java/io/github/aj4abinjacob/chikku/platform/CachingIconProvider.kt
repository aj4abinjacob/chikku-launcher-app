package io.github.aj4abinjacob.chikku.platform

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.LruCache
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.icons.IconBitmapDrawable
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.platform.icons.IconRasterizer
import io.github.aj4abinjacob.chikku.platform.icons.IconRequest
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import io.github.aj4abinjacob.chikku.platform.icons.IconStyle
import io.github.aj4abinjacob.chikku.platform.icons.IconTheme
import io.github.aj4abinjacob.chikku.platform.icons.calendarIcon
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [IconProvider] that loads badged activity icons on [ioDispatcher], rasterises each one once into
 * an immutable bitmap at the largest size the launcher currently shows icons at, and keeps those
 * bitmaps in a byte-bounded LRU cache of [maxBytes]. Every caller gets its own light
 * [IconBitmapDrawable] over the shared bitmap, so N views of one app hold one bitmap and no
 * adaptive/vector drawable state. Missing or failing icons resolve to [fallback] and are not
 * cached, so a later request can succeed after the app finishes installing.
 *
 * Icons follow the current [IconTheme] (icon pack, shape, per-app overrides). The cache key is the
 * full [IconRequest] plus the raster size; a theme or size change, or a change/removal of a pack in
 * use, drops the cache and bumps [iconRevision] so bound views reload. Pinned shortcut icons share
 * the same cache and budget ([shortcutIcon]).
 */
class CachingIconProvider internal constructor(
    private val ioDispatcher: CoroutineDispatcher,
    maxBytes: Int,
    private val loader: (IconRequest) -> Drawable?,
    private val fallback: () -> Drawable,
    /** Called with every changed/removed package so a loaded icon pack can be dropped. */
    private val onPackChanged: (String) -> Unit = {},
    following: Boolean = false,
    /** Loads [monochromeIcon]s; uncached, as only contact cards ask for a few of them. */
    private val monochromeLoader: (AppKey) -> Drawable? = { null },
    /** Icon size at scale 1 (the home icon size); multiplied by the largest icon scale in use. */
    baseIconSizePx: Int = DEFAULT_TEST_ICON_PX,
) : IconProvider {

    /** Unthemed loader (tests and callers that never theme icons). */
    internal constructor(
        ioDispatcher: CoroutineDispatcher,
        maxBytes: Int,
        loader: (AppKey) -> Drawable?,
        fallback: () -> Drawable,
    ) : this(ioDispatcher, maxBytes, { request: IconRequest -> loader(request.key) }, fallback)

    constructor(
        context: Context,
        ioDispatcher: CoroutineDispatcher,
        maxBytes: Int = defaultCacheBytes(context),
        iconPacks: IconPackRepository = IconPackRepository(context),
    ) : this(
        ioDispatcher,
        maxBytes,
        themedIconLoader(context.applicationContext, iconPacks),
        { context.applicationContext.packageManager.defaultActivityIcon },
        { iconPacks.invalidate(it) },
        following = true,
        monochromeLoader = monochromeIconLoader(context.applicationContext),
        baseIconSizePx = context.applicationContext.resources.getDimensionPixelSize(R.dimen.home_icon_size),
    ) {
        val app = context.applicationContext
        // Memory pressure reaches [trimMemory] through ChikkuApplication -> AppContainer, once;
        // this callback only follows density/display-size changes.
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = Unit

            override fun onConfigurationChanged(newConfig: Configuration) =
                setBaseIconSize(app.resources.getDimensionPixelSize(R.dimen.home_icon_size))

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit
        })
    }

    private val cache = object : LruCache<CacheKey, Bitmap>(maxBytes.coerceAtLeast(1)) {
        override fun sizeOf(key: CacheKey, value: Bitmap): Int = value.allocationByteCount
    }

    /** Bumped on every invalidation so a load that raced with it is not cached. */
    private val generation = AtomicLong()

    /** Null until [follow] delivers the stored theme (only when constructed to follow settings). */
    private val theme = MutableStateFlow(if (following) null else IconTheme.DEFAULT)

    @Volatile private var baseSizePx = baseIconSizePx.coerceAtLeast(1)

    /** Largest icon scale of home, dock, drawer and folders; icons are rasterised for it. */
    @Volatile private var iconScale = 1f

    private val revision = MutableStateFlow(0L)

    /** Set when an icon was loaded with [IconTheme.DEFAULT] because the stored theme was slow to arrive. */
    @Volatile private var loadedWithDefaultedTheme = false

    private val packageChanges = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val packageInvalidations: SharedFlow<String> = packageChanges
    private var calendarDate = java.time.LocalDate.now()

    /** Day of month stamped on every request, read once per date change rather than per lookup. */
    @Volatile private var calendarDay = calendarDate.dayOfMonth

    override fun refreshCalendarDate() {
        val now = java.time.LocalDate.now()
        if (now != calendarDate) {
            calendarDate = now
            calendarDay = now.dayOfMonth
            dropAll()
        }
    }

    override val iconRevision: StateFlow<Long> = revision.asStateFlow()

    /** Pixel size of cached icons; views draw them scaled into their own (equal or smaller) bounds. */
    /**
     * One raster per icon, at the largest size any surface draws it: `home_icon_size` × the largest
     * icon scale, and never below the 64dp edit-sheet and icon-picker headers ([MIN_RASTER_SCALE]).
     */
    internal val rasterSizePx: Int get() = ceil(baseSizePx * maxOf(iconScale, MIN_RASTER_SCALE)).toInt().coerceAtLeast(1)

    /** Keeps the icon theme in sync with settings and per-app customizations for the life of [scope]. */
    fun follow(
        scope: CoroutineScope,
        settings: Flow<LauncherSettings>,
        customizations: Flow<Map<AppKey, AppCustomization>>,
    ) {
        scope.launch {
            combine(
                settings.catch { emit(LauncherSettings()) },
                customizations.onStart { emit(emptyMap()) }.catch { emit(emptyMap()) },
            ) { prefs, custom ->
                IconTheme(IconStyle.from(prefs.lookFeel), IconTheme.overridesOf(custom)) to largestIconScale(prefs)
            }
                .distinctUntilChanged()
                .collect { (next, scale) -> setTheme(next, scale) }
        }
    }

    internal fun setTheme(next: IconTheme, scale: Float = iconScale) {
        val previous = theme.value
        val resized = scale != iconScale
        iconScale = scale
        theme.value = next
        // The first real theme after icons were handed out with the default one is a change too.
        val lateFirstTheme = previous == null && loadedWithDefaultedTheme && next != IconTheme.DEFAULT
        when {
            lateFirstTheme -> dropAll()
            previous == null -> Unit
            // A new raster size needs every icon again.
            resized -> dropAll()
            previous == next -> Unit
            // Only per-app icons differ (typically the real customizations arriving after a cold
            // start): reload just those apps and keep the rest of the cache.
            previous.style == next.style && changedOverrideKeys(previous, next).size <= MAX_TARGETED_INVALIDATIONS ->
                invalidateKeys(changedOverrideKeys(previous, next))
            else -> dropAll()
        }
        if (previous == null) loadedWithDefaultedTheme = false
    }

    private fun changedOverrideKeys(previous: IconTheme, next: IconTheme): Set<AppKey> =
        (previous.overrides.keys + next.overrides.keys).filterTo(HashSet()) { previous.overrides[it] != next.overrides[it] }

    /** Drops cached icons of [keys] and tells views bound to their packages to reload. */
    private fun invalidateKeys(keys: Set<AppKey>) {
        if (keys.isEmpty()) return
        generation.incrementAndGet()
        cache.snapshot().keys.filter { it is CacheKey.App && it.request.key in keys }.forEach(cache::remove)
        // Compose icon consumers key on the revision only; warm entries of other apps stay cached.
        revision.value = revision.value + 1
        // Folder caches follow the per-package signal; if a collector's buffer is full, drop everything.
        val delivered = keys.mapTo(LinkedHashSet()) { it.packageName }.map { packageChanges.tryEmit(it) }
        if (delivered.any { !it }) dropAll()
    }

    /** The display's icon size changed (display size/density setting): re-rasterise everything. */
    internal fun setBaseIconSize(px: Int) {
        val next = px.coerceAtLeast(1)
        if (next == baseSizePx) return
        baseSizePx = next
        dropAll()
    }

    override suspend fun icon(key: AppKey): Drawable = resolvedIcon(key).drawable

    override suspend fun resolvedIcon(key: AppKey): ResolvedIcon {
        val current = theme.value
            ?: withTimeoutOrNull(THEME_WAIT_MS) { theme.filterNotNull().first() }
            ?: IconTheme.DEFAULT.also { loadedWithDefaultedTheme = true }
        val request = IconRequest(key, current.style, current.overrides[key], calendarDay)
        val bitmap = cachedOrLoad(CacheKey.App(request, rasterSizePx)) { loader(request) }
        return bitmap?.let { ResolvedIcon(IconBitmapDrawable(it), isFallback = false) }
            ?: ResolvedIcon(fallback(), isFallback = true)
    }

    override suspend fun shortcutIcon(key: ShortcutIconKey, load: suspend () -> Drawable?): Drawable? =
        cachedOrLoad(CacheKey.Shortcut(key, rasterSizePx), load)?.let(::IconBitmapDrawable)

    /** Cached bitmap for [cacheKey], else [load] rasterised once (off the main thread) and cached. */
    private suspend fun cachedOrLoad(cacheKey: CacheKey, load: suspend () -> Drawable?): Bitmap? {
        cache.get(cacheKey)?.let { return it }
        val startGeneration = generation.get()
        return withContext(ioDispatcher) {
            try {
                val drawable = load() ?: return@withContext null
                val bitmap = IconRasterizer.rasterize(drawable, cacheKey.sizePx)
                // Cache here, not after withContext returns: a blocking load cannot be interrupted, and a
                // requester cancelled meanwhile (a recycled cell) would otherwise discard finished work.
                if (generation.get() == startGeneration) cache.put(cacheKey, bitmap)
                bitmap
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun monochromeIcon(key: AppKey): Drawable? = withContext(ioDispatcher) {
        try {
            monochromeLoader(key)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override fun invalidate(packageName: String) {
        onPackChanged(packageName)
        val inUse = theme.value?.packPackages.orEmpty()
        if (packageName in inUse) {
            dropAll()
            return
        }
        generation.incrementAndGet()
        cache.snapshot().keys.filter { key ->
            when (key) {
                is CacheKey.App -> key.request.key.packageName == packageName || key.request.override?.iconPack == packageName
                is CacheKey.Shortcut -> key.shortcut.packageName == packageName || key.shortcut.iconPack == packageName
            }
        }.forEach(cache::remove)
        packageChanges.tryEmit(packageName)
    }

    override fun invalidateShortcuts(packageName: String, userSerial: Long) {
        generation.incrementAndGet()
        cache.snapshot().keys
            .filter { it is CacheKey.Shortcut && it.shortcut.packageName == packageName && it.shortcut.userSerial == userSerial }
            .forEach(cache::remove)
    }

    private fun dropAll() {
        generation.incrementAndGet()
        cache.evictAll()
        revision.value = revision.value + 1
    }

    /**
     * Releases cached icons under memory pressure ([ComponentCallbacks2] levels). Bound views keep
     * the bitmaps they draw; the next load of an evicted icon re-rasterises it. Hidden UI (every
     * app launch from HOME, and pre-Android 14 "running low") halves the cache; a backgrounded
     * process, critical pressure or low memory empties it. Lighter levels keep it.
     */
    fun trimMemory(level: Int) {
        @Suppress("DEPRECATION") // RUNNING_* levels are still delivered before Android 14.
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> cache.evictAll()
            // HOME gets UI_HIDDEN on every app launch; keep the icons for the return.
            level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> Unit
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.trimToSize(cache.size() / 2)
        }
    }

    internal fun cachedCount(): Int = cache.snapshot().size

    internal fun cachedBytes(): Int = cache.size()

    private sealed interface CacheKey {
        val sizePx: Int

        data class App(val request: IconRequest, override val sizePx: Int) : CacheKey

        data class Shortcut(val shortcut: ShortcutIconKey, override val sizePx: Int) : CacheKey
    }

    internal companion object {
        /** Base icon size of the internal constructors (tests). */
        const val DEFAULT_TEST_ICON_PX = 48

        /** 64dp headers over the 52dp `home_icon_size`: the largest icon surface in the app. */
        const val MIN_RASTER_SCALE = 64f / 52f

        /** More changed per-app icons than this reload everything; it also bounds bursts on [packageInvalidations]. */
        const val MAX_TARGETED_INVALIDATIONS = 32

        /** How long a first icon request waits for the stored theme before using system icons. */
        const val THEME_WAIT_MS = 750L

        private const val MIB = 1024 * 1024
        private const val MIN_CACHE_BYTES = 16 * MIB
        private const val MAX_CACHE_BYTES = 64 * MIB

        /**
         * An eighth of the app's heap class, kept within 16–64 MiB: on a 256 MiB-class phone that
         * holds ~390 icons at the default size (52dp at 2.8x density, ~85 KiB each), so a full
         * drawer stays cached. Pixels live in native memory and are shared with the views showing
         * them, so the cache adds little beyond icons that are no longer on screen.
         */
        fun defaultCacheBytes(context: Context): Int {
            val memoryClassMb = context.getSystemService(ActivityManager::class.java)?.memoryClass ?: 0
            return (memoryClassMb.toLong() * MIB / 8).coerceIn(MIN_CACHE_BYTES.toLong(), MAX_CACHE_BYTES.toLong()).toInt()
        }

        /** Largest icon scale any launcher surface uses; folders and drawer may match the desktop size. */
        fun largestIconScale(settings: LauncherSettings): Float =
            maxOf(settings.home.iconScale, settings.dock.iconScale, settings.drawer.iconScale, settings.folders.iconScale)
                .takeIf { it.isFinite() && it > 0f }
                ?.coerceIn(LauncherSettings.MIN_SCALE, LauncherSettings.MAX_SCALE)
                ?: 1f
    }
}

/**
 * Loads an icon for [IconRequest]: per-app override, else the icon pack's icon or its themed
 * fallback, else the system icon in the chosen shape; work-profile icons keep their badge.
 * The untouched badged system icon is used when nothing is themed.
 */
private fun themedIconLoader(context: Context, iconPacks: IconPackRepository): (IconRequest) -> Drawable? {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    val packageManager = context.packageManager
    val iconSizePx = (context.resources.getDimensionPixelSize(R.dimen.home_icon_size) * LauncherSettings.MAX_SCALE).toInt()
    return loader@{ request ->
        val key = request.key
        val user = userManager?.getUserForSerialNumber(key.userSerial) ?: return@loader null
        val activity = launcherApps?.resolveLauncherActivity(key, user) ?: return@loader null
        val style = request.style
        // PackageManager resources belong to this process's user. Other profiles retain their
        // LauncherApps icons, rather than accidentally reading the personal app's metadata.
        val daily = if (user == android.os.Process.myUserHandle()) {
            calendarIcon(packageManager, activity.componentName, request.calendarDay)
        } else null
        if (style.isSystemDefault && request.override == null) {
            return@loader daily?.let { packageManager.getUserBadgedIcon(IconShaper.systemSafe(it), user) }
                ?: IconShaper.systemSafe(activity.getBadgedIcon(0))
        }

        val fromOverride = request.override?.let { iconPacks.load(it.iconPack)?.drawable(it.drawableName) }
        val pack = style.iconPack?.let(iconPacks::load)
        val themed = fromOverride
            ?: pack?.iconFor(key.packageName, key.className, request.calendarDay)
            ?: pack?.themedFallback(daily ?: activity.getIcon(0), key.packageName.hashCode() * 31 + key.className.hashCode(), iconSizePx)
        val icon = themed ?: IconShaper.apply(daily ?: activity.getIcon(0), style.shape, style.reshapeLegacy)
        packageManager.getUserBadgedIcon(icon, user)
    }
}

/** The launcher activity's unthemed adaptive icon's monochrome layer; null before Android 13 or without one. */
private fun monochromeIconLoader(context: Context): (AppKey) -> Drawable? {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    val userManager = context.getSystemService(UserManager::class.java)
    return loader@{ key ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@loader null
        val user = userManager?.getUserForSerialNumber(key.userSerial) ?: return@loader null
        val icon = launcherApps?.resolveLauncherActivity(key, user)?.getIcon(0)
        (icon as? AdaptiveIconDrawable)?.monochrome
    }
}

private fun LauncherApps.resolveLauncherActivity(key: AppKey, user: UserHandle): LauncherActivityInfo? =
    resolveActivity(
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(key.packageName, key.className)),
        user,
    )
