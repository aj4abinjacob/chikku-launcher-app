package io.github.aj4abinjacob.chikku.badges

import android.content.Context
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeStyle
import io.github.aj4abinjacob.chikku.core.model.settings.BadgePosition
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import java.util.WeakHashMap

/**
 * Puts notification badges on bound [AppIconView]s (desktop, dock, drawer, folders) and keeps them
 * current. Main thread only. Views register through [bindApp]/[bindFolder] when they are bound and
 * [unbind] when they show something else; [update] re-applies every registered view.
 *
 * The badge count is appended to the content description ("Maps, 3 notifications").
 */
class BadgeBinder(private val context: Context) {
    private sealed class Binding(val baseDescription: CharSequence?) {
        /** The description this binder last set, to restore [baseDescription] only if nobody changed it since. */
        var applied: CharSequence? = baseDescription

        /** The badge this binder last drew on the view (null: none), to skip unchanged views. */
        var shown: BadgeInfo? = null

        class App(val key: AppKey, base: CharSequence?) : Binding(base)

        class Folder(val keys: List<AppKey>, base: CharSequence?) : Binding(base)
    }

    private val views = WeakHashMap<AppIconView, Binding>()
    private var badges: Map<BadgeKey, BadgeInfo> = emptyMap()
    private var style: BadgeStyle = BadgeStyle.OFF
    private var color: Int = CHIKKU_DEFAULT_ACCENT
    private var position = BadgePosition.TOP_END
    private var badgeScale = 1f

    /**
     * New badges or settings. A settings change redraws every bound view; a badge change redraws
     * only the views whose own badge changed (badges compare by content).
     */
    fun update(badges: Map<BadgeKey, BadgeInfo>, settings: LauncherSettings) {
        val newColor = badgeColor(settings)
        val settingsChanged = settings.badges.style != style || newColor != color ||
            settings.badges.position != position || settings.badges.badgeScale != badgeScale
        if (!settingsChanged && badges == this.badges) return
        this.badges = badges
        style = settings.badges.style
        color = newColor
        position = settings.badges.position
        badgeScale = settings.badges.badgeScale
        for ((view, binding) in views.entries.toList()) {
            val unchanged = !settingsChanged && view.contentDescription === binding.applied &&
                infoFor(binding) == binding.shown
            if (!unchanged) apply(view, binding)
        }
    }

    /** Call after the view's label and content description are set for [key]. */
    fun bindApp(view: AppIconView, key: AppKey) {
        val binding = Binding.App(key, view.contentDescription)
        views[view] = binding
        apply(view, binding)
    }

    /**
     * For folder icons: an aggregate dot when any entry has notifications (for the folders feature).
     * Call after the view's content description is set.
     */
    fun bindFolder(view: AppIconView, entries: List<AppKey>) {
        val binding = Binding.Folder(entries, view.contentDescription)
        views[view] = binding
        apply(view, binding)
    }

    /** The view no longer shows an app or folder; removes its badge. */
    fun unbind(view: AppIconView) {
        val binding = views.remove(view) ?: return
        view.badge = null
        // The caller may already have set a new description; only undo our own suffix.
        if (view.contentDescription === binding.applied) view.contentDescription = binding.baseDescription
    }

    /** Badge currently known for [key], or null (also null while badges are off). */
    fun badgeFor(key: AppKey): BadgeInfo? = if (style == BadgeStyle.OFF) null else badges[BadgeKey.of(key)]

    private fun infoFor(binding: Binding): BadgeInfo? = when {
        style == BadgeStyle.OFF -> null
        binding is Binding.App -> badges[BadgeKey.of(binding.key)]
        binding is Binding.Folder -> folderBadge(badges, binding.keys)
        else -> null
    }

    private fun apply(view: AppIconView, binding: Binding) {
        val info = infoFor(binding)
        val overlay = info?.let {
            when (binding) {
                is Binding.App -> BadgeOverlay(style, it.count, color, it.avatar, position, badgeScale)
                is Binding.Folder -> BadgeOverlay(BadgeStyle.DOT, it.count, color, position = position, badgeScale = badgeScale)
            }
        }
        binding.shown = info
        view.badge = overlay
        val description = describe(binding.baseDescription, info?.count ?: 0)
        binding.applied = description
        view.contentDescription = description
    }

    private fun describe(base: CharSequence?, count: Int): CharSequence? {
        if (count <= 0 || base.isNullOrEmpty()) return base
        val suffix = context.resources.getQuantityString(R.plurals.badge_notifications_description, count, count)
        return context.getString(R.string.badge_description_format, base, suffix)
    }
}

/**
 * Badge color: the chosen dot color, else the Style accent, else Chikku's own accent. Never the
 * window theme's accent, which on OEM builds (One UI, MIUI) is the device skin's color.
 */
internal fun badgeColor(settings: LauncherSettings): Int =
    settings.badges.dotColor ?: settings.lookFeel.accentColor ?: CHIKKU_DEFAULT_ACCENT
