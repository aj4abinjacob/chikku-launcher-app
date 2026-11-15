package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCover
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverMode
import io.github.aj4abinjacob.chikku.core.model.settings.FolderCoverShortcut
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Draws a folder's icon on an [AppIconView]: label = title, preview of the first entries in the
 * configured style. Unavailable apps preview as the repair placeholder. Icons load in [scope]; the
 * load is tracked in [AppIconView.iconJob] so the workspace's normal recycling cancels it.
 *
 * The drawn icon is keyed on [IconProvider.iconRevision] and [shortcutRevision] as well as the
 * folder's content, so a rebind after an icon change redraws the preview instead of keeping it.
 */
class FolderIconBinder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val iconProvider: IconProvider,
    /** Pinned-shortcut icons; null when the host has none (tests), leaving the placeholder. */
    private val shortcutIcons: (suspend (String, String, Long, String?, String?) -> Drawable?)? = null,
) {

    /** Bumped by [invalidateShortcuts]; part of every preview's key. */
    private var shortcutRevision = 0L

    /** Per-package invalidation counts; folding the relevant ones into a preview's key redraws only affected folders. */
    private val packageRevisions = HashMap<String, Long>()

    /** [packageName]'s icons changed (an app update): previews showing it redraw on the next [bind]. */
    fun invalidatePackage(packageName: String) {
        packageRevisions[packageName] = (packageRevisions[packageName] ?: 0L) + 1
    }

    /** An app changed its pinned shortcuts: the next [bind] reloads previews instead of reusing them. */
    fun invalidateShortcuts() {
        shortcutRevision++
    }

    /** Shown label: the title, or "Folder" when it has none. */
    fun label(folder: ItemContent.Folder): String = folder.title.ifBlank { context.getString(R.string.home_folder_unnamed) }

    fun description(folder: ItemContent.Folder): String = context.resources.getQuantityString(
        R.plurals.home_folder_description, folder.entries.size, label(folder), folder.entries.size,
    )

    fun bind(view: AppIconView, folder: ItemContent.Folder, settings: FolderSettings, folderId: String? = null) {
        view.label = label(folder)
        view.contentDescription = description(folder)
        view.showWorkBadge = false
        view.alpha = 1f

        val coverApp = folderCoverEntry(folder, folderId?.let { settings.covers[it] })
        val previews = coverApp?.let { listOf(previewOf(it)) }
            ?: displayFolderEntries(folder.entries, settings.sortOrder).take(settings.previewCount.coerceIn(1, 9)).map { previewOf(it.content) }
        val color = settings.iconBackgroundColor ?: ContextCompat.getColor(context, R.color.folder_icon_background)
        val packageRevision = previews.sumOf { preview ->
            when (preview) {
                is Preview.Load -> packageRevisions[preview.key.packageName] ?: 0L
                is Preview.Shortcut -> packageRevisions[preview.packageName] ?: 0L
                else -> 0L
            }
        }
        val revision = Triple(
            iconProvider.iconRevision.value,
            if (previews.any { it is Preview.Shortcut }) shortcutRevision else 0L,
            packageRevision,
        )
        val signature = Signature(previews, settings.previewStyle, settings.iconBackground, color, coverApp != null, revision)
        val current = view.getTag(R.id.folder_icon_signature)
        // Already drawn, or still loading, for exactly this content and style.
        if (current == Bound(signature, done = true) || (current == Bound(signature, done = false) && view.iconJob?.isActive == true)) {
            return
        }
        // Only the icons' revision changed: keep the drawn preview until the new one is ready (no flash).
        // Shortcut previews are never kept: a lock change must hide a locked shortcut at once.
        val refresh = previews.none { it is Preview.Shortcut } && current is Bound && current.signature.copy(revision = revision) == signature
        view.iconJob?.cancel()
        view.iconJob = null
        view.boundKey = null
        view.setTag(R.id.folder_icon_signature, Bound(signature, done = false))

        fun drawable(icons: List<Drawable>): Drawable = if (coverApp != null) icons.single()
            else FolderIconDrawable(icons, settings.previewStyle, settings.iconBackground, color)
        if (!refresh) view.icon = drawable(previews.map { placeholder(it) })
        if (previews.none { it is Preview.Load || it is Preview.Shortcut }) {
            view.setTag(R.id.folder_icon_signature, Bound(signature, done = true))
            return
        }
        view.iconJob = scope.launch {
            val icons = previews.map { preview ->
                when (preview) {
                    is Preview.Load -> try {
                        iconProvider.icon(preview.key) // a new light drawable per call; pixels are shared
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        placeholder(preview)
                    }
                    is Preview.Shortcut -> shortcutIcons
                        ?.invoke(preview.packageName, preview.shortcutId, preview.userSerial, preview.iconPack, preview.iconName)
                        ?: placeholder(preview)
                    else -> placeholder(preview)
                }
            }
            if (view.getTag(R.id.folder_icon_signature) == Bound(signature, done = false)) {
                view.icon = drawable(icons)
                view.setTag(R.id.folder_icon_signature, Bound(signature, done = true))
            }
        }
    }

    private fun previewOf(content: ItemContent): Preview = when (content) {
        is ItemContent.App ->
            if (content.availability == Availability.UNAVAILABLE) Preview.Unavailable else Preview.Load(content.key)
        is ItemContent.Shortcut ->
            if (content.availability == Availability.UNAVAILABLE) Preview.Unavailable
            else Preview.Shortcut(content.packageName, content.shortcutId, content.userSerial, content.iconPackPackage, content.iconDrawableName)
        is ItemContent.Folder, is ItemContent.Widget -> Preview.Placeholder
    }

    private fun placeholder(preview: Preview): Drawable {
        val res = if (preview == Preview.Unavailable) R.drawable.ic_app_unavailable else R.drawable.ic_app_placeholder
        return checkNotNull(ContextCompat.getDrawable(context, res)).mutate()
    }

    private sealed interface Preview {
        data class Load(val key: AppKey) : Preview
        data class Shortcut(
            val packageName: String,
            val shortcutId: String,
            val userSerial: Long,
            val iconPack: String?,
            val iconName: String?,
        ) : Preview
        data object Unavailable : Preview
        data object Placeholder : Preview
    }

    private data class Signature(
        val previews: List<Preview>,
        val style: Any,
        val background: Any,
        val color: Int,
        val covered: Boolean,
        /** Icon theme, shortcut and affected-package revisions the icons were loaded at. */
        val revision: Triple<Long, Long, Long>,
    )

    private data class Bound(val signature: Signature, val done: Boolean)
}

/**
 * The entry a cover shows: an available app or pinned shortcut, resolved from stored manual
 * membership only, so alphabetical presentation never changes the cover.
 */
internal fun folderCoverEntry(folder: ItemContent.Folder, cover: FolderCover?): ItemContent? {
    val members = folder.entries.map { it.content }.filter { it is ItemContent.App || it is ItemContent.Shortcut }
    val selected = when (cover?.mode) {
        null, FolderCoverMode.PREVIEW -> null
        FolderCoverMode.FIRST_APP -> members.firstOrNull()
        FolderCoverMode.LAST_APP -> members.lastOrNull()
        FolderCoverMode.SELECTED_APP -> members.firstOrNull { it.matches(cover) }
    }
    // Removed/unavailable entries retain their placement, but cannot become a decorative icon.
    return selected?.takeIf { it.isAvailable() }
}

private fun ItemContent.matches(cover: FolderCover): Boolean = when (this) {
    is ItemContent.App -> cover.app != null && key == cover.app
    is ItemContent.Shortcut -> cover.shortcut == FolderCoverShortcut(packageName, shortcutId, userSerial)
    is ItemContent.Folder, is ItemContent.Widget -> false
}

private fun ItemContent.isAvailable(): Boolean = when (this) {
    is ItemContent.App -> availability == Availability.AVAILABLE
    is ItemContent.Shortcut -> availability == Availability.AVAILABLE
    is ItemContent.Folder, is ItemContent.Widget -> false
}
