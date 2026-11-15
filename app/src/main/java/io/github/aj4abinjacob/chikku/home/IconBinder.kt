package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.badges.BadgeBinder
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/**
 * Binds app content to [AppIconView]s and loads icons in [scope] (the activity's lifecycle scope).
 * A neutral placeholder shows while loading; a rebind cancels the previous load so a recycled view
 * never shows a stale icon.
 *
 * Bound app views follow icon theme changes ([IconProvider.iconRevision]) and the user's custom
 * labels ([customLabels], from app customizations) without being rebound.
 */
class IconBinder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val iconProvider: IconProvider,
    /** Notification badges; null when the host has none (tests). Folder binding uses it directly. */
    val badges: BadgeBinder? = null,
    customLabels: Flow<Map<AppKey, String>>? = null,
    private val shortcutIcons: (suspend (String, String, Long, String?, String?) -> Drawable?)? = null,
    /**
     * Emits whenever what the app lock lets shortcut icons show may have changed (locks loaded or
     * edited). Bound shortcut icons re-read through [shortcutIcons], which hides locked ones.
     */
    shortcutAccess: Flow<Any>? = null,
) {
    private val loadingIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_app_placeholder)

    private data class Binding(val key: AppKey, val label: String?, val isWork: Boolean, val availability: Availability)

    /** Live app bindings, so theme and label changes can refresh them in place. */
    private val bindings = WeakHashMap<AppIconView, Binding>()
    private var labels: Map<AppKey, String> = emptyMap()
    private val shortcutBindings = WeakHashMap<AppIconView, ItemContent.Shortcut>()

    /** Views whose app icon is the provider's fallback, so a later bind or invalidation retries them. */
    private val fallbackViews: MutableSet<AppIconView> = java.util.Collections.newSetFromMap(WeakHashMap())

    init {
        scope.launch { iconProvider.iconRevision.drop(1).collect { reloadIcons() } }
        scope.launch { iconProvider.packageInvalidations.collect { reloadPackage(it) } }
        if (shortcutAccess != null) {
            scope.launch { shortcutAccess.collect { reloadAllShortcuts() } }
        }
        if (customLabels != null) {
            scope.launch {
                customLabels.collect { next ->
                    labels = next
                    bindings.entries.toList().forEach { (view, binding) -> if (view.boundKey == binding.key) applyText(view, binding) }
                }
            }
        }
    }

    private fun applyText(view: AppIconView, binding: Binding) {
        val shownLabel = when (binding.availability) {
            Availability.UNAVAILABLE -> context.getString(R.string.home_unavailable_app)
            else -> labels[binding.key] ?: binding.label ?: context.getString(R.string.home_unknown_app)
        }
        view.label = shownLabel
        view.contentDescription =
            if (binding.isWork) context.getString(R.string.home_app_work_description, shownLabel) else shownLabel
        // Badges append to the description just set, so they rebind after every text change.
        if (binding.availability == Availability.UNAVAILABLE) badges?.unbind(view) else badges?.bindApp(view, binding.key)
    }

    fun bindApp(view: AppIconView, key: AppKey, label: String?, isWork: Boolean, availability: Availability) {
        val binding = Binding(key, label, isWork, availability)
        val previous = bindings[view]
        // A different app on a view that showed a folder must not keep the folder's cached preview.
        view.setTag(R.id.folder_icon_signature, null)
        // The app just became known to exist, or its last load only produced the fallback: try again.
        val retry = previous != null && previous.key == key && availability == Availability.AVAILABLE &&
            (previous.availability != Availability.AVAILABLE || view in fallbackViews)
        bindAppText(view, binding)
        bindAppIcon(view, key, availability, retry)
        bindings[view] = binding
    }

    private fun bindAppText(view: AppIconView, binding: Binding) {
        applyText(view, binding)
        view.showWorkBadge = binding.isWork
        view.alpha = if (binding.availability == Availability.UNAVAILABLE) UNAVAILABLE_ALPHA else 1f
    }

    private fun bindAppIcon(view: AppIconView, key: AppKey, availability: Availability, retry: Boolean = false) {
        if (availability == Availability.UNAVAILABLE) {
            fallbackViews.remove(view)
            cancel(view)
            view.boundKey = key
            view.icon = ContextCompat.getDrawable(context, R.drawable.ic_app_unavailable)
            return
        }
        if (retry && view.boundKey == key) { // keep the icon shown until the new one is ready
            view.iconJob?.cancel()
            loadIcon(view, key)
            return
        }
        if (view.boundKey == key && view.iconJob != null) return // already loaded or loading
        cancel(view)
        view.boundKey = key
        // Shares the vector's state: nothing changes a placeholder's alpha or tint, only its bounds.
        view.icon = loadingIcon?.constantState?.newDrawable() ?: loadingIcon
        loadIcon(view, key)
    }

    /** Reloads every bound app icon (icon pack, shape or a per-app icon changed); keeps the old icon until the new one is ready. */
    fun reloadIcons() {
        reloadAllShortcuts() // keeps each current icon rather than blinking to the placeholder
        bindings.entries.toList().forEach { (view, binding) ->
            if (binding.availability != Availability.UNAVAILABLE && view.boundKey == binding.key) {
                view.iconJob?.cancel()
                loadIcon(view, binding.key)
            }
        }
    }

    /** One package's icons changed (an app update): reloads only views bound to it, keeping the old icon meanwhile. */
    private fun reloadPackage(packageName: String) {
        bindings.entries.toList().forEach { (view, binding) ->
            if (binding.availability != Availability.UNAVAILABLE && view.boundKey == binding.key &&
                binding.key.packageName == packageName
            ) {
                view.iconJob?.cancel()
                loadIcon(view, binding.key)
            }
        }
        shortcutBindings.entries.toList()
            .filter { (_, content) -> content.packageName == packageName }
            .forEach { (view, content) -> reloadShortcut(view, content) }
    }

    private fun loadIcon(view: AppIconView, key: AppKey) {
        view.iconJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            var fallback = false
            val drawable = try {
                iconProvider.resolvedIcon(key).also { fallback = it.isFallback }.drawable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fallback = true
                null // keep the neutral placeholder; the provider is supposed to fall back itself
            }
            if (view.boundKey == key) {
                if (fallback) fallbackViews.add(view) else fallbackViews.remove(view)
            }
            // A new light drawable per call (IconProvider contract): pixels are shared, bounds are not.
            if (drawable != null && view.boundKey == key) view.icon = drawable
        }
    }

    /**
     * Re-reads bound shortcut icons of [packageName] in profile [userSerial] after the app changed
     * its shortcuts. The current icon stays until the new one loads; one the app removed or
     * disabled falls back to the placeholder, as when it was first bound.
     */
    fun reloadShortcuts(packageName: String, userSerial: Long) {
        iconProvider.invalidateShortcuts(packageName, userSerial)
        shortcutBindings.entries.toList()
            .filter { (_, content) -> content.packageName == packageName && content.userSerial == userSerial }
            .forEach { (view, content) -> reloadShortcut(view, content) }
    }

    /** Re-reads every bound shortcut icon, e.g. after the app lock changed what may be shown. */
    private fun reloadAllShortcuts() {
        shortcutBindings.entries.toList().forEach { (view, content) -> reloadShortcut(view, content) }
    }

    private fun reloadShortcut(view: AppIconView, content: ItemContent.Shortcut) {
        view.iconJob?.cancel()
        view.iconJob = scope.launch {
            val drawable = shortcutIcons?.invoke(content.packageName, content.shortcutId, content.userSerial, content.iconPackPackage, content.iconDrawableName)
            if (shortcutBindings[view] != content) return@launch
            view.icon = drawable?.let { it.constantState?.newDrawable(context.resources)?.mutate() ?: it }
                ?: ContextCompat.getDrawable(context, R.drawable.ic_app_placeholder)
        }
    }

    fun bindShortcut(view: AppIconView, content: ItemContent.Shortcut) {
        val label = content.label ?: context.getString(R.string.home_unknown_app)
        val previous = shortcutBindings[view]
        if (previous != null && content.availability != Availability.UNAVAILABLE && previous.isSameShortcut(content)) {
            // Re-rendered (moved, relabelled or given another icon): keep the icon on screen instead
            // of flashing the placeholder, and swap it only when a different one has loaded.
            view.label = label
            view.contentDescription = label
            // Still sets the resting alpha, like every other bind: the workspace reads it as the alpha
            // to return to after a drag, so a drag dim left here would stay after the drop.
            view.alpha = 1f
            if (previous != content) loadShortcut(view, content)
            return
        }
        bindPlaceholder(view, label, label, if (content.availability == Availability.UNAVAILABLE)
            R.drawable.ic_app_unavailable else R.drawable.ic_app_placeholder)
        if (content.availability == Availability.UNAVAILABLE) return
        loadShortcut(view, content)
    }

    private fun loadShortcut(view: AppIconView, content: ItemContent.Shortcut) {
        shortcutBindings[view] = content
        view.iconJob?.cancel()
        view.iconJob = scope.launch {
            val drawable = shortcutIcons?.invoke(content.packageName, content.shortcutId, content.userSerial, content.iconPackPackage, content.iconDrawableName)
            if (drawable != null && shortcutBindings[view] == content) view.icon = drawable
        }
    }

    private fun ItemContent.Shortcut.isSameShortcut(other: ItemContent.Shortcut) =
        packageName == other.packageName && shortcutId == other.shortcutId && userSerial == other.userSerial &&
            availability == other.availability

    /**
     * Static icon + label for content without an app icon yet (folder, widget, shortcut placeholders
     * until their feature renders them properly).
     */
    fun bindPlaceholder(view: AppIconView, label: String, description: String, @DrawableRes iconRes: Int) {
        cancel(view)
        badges?.unbind(view)
        view.label = label
        view.showWorkBadge = false
        view.contentDescription = description
        view.alpha = 1f
        view.icon = ContextCompat.getDrawable(context, iconRes)
    }

    /**
     * The view now shows something that is not an app (a folder): forget its app binding so label
     * and icon refreshes leave it alone, and drop its app badge. Unlike [cancel] it keeps
     * [AppIconView.iconJob], which the new owner manages.
     */
    fun releaseApp(view: AppIconView) {
        // Only an app binding leaves a stale folder signature; the folder binder's own rebind must keep its tag.
        val hadShortcut = shortcutBindings.remove(view) != null
        val hadApp = bindings.remove(view) != null
        if (hadShortcut || hadApp) view.setTag(R.id.folder_icon_signature, null)
        fallbackViews.remove(view)
        badges?.unbind(view)
    }

    fun cancel(view: AppIconView) {
        view.setTag(R.id.folder_icon_signature, null)
        fallbackViews.remove(view)
        shortcutBindings.remove(view)
        bindings.remove(view)
        view.iconJob?.cancel()
        view.iconJob = null
        view.boundKey = null
    }

    private companion object {
        const val UNAVAILABLE_ALPHA = 0.55f
    }
}
