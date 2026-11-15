package io.github.aj4abinjacob.chikku.home

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.PaddingSize

/** Immutable snapshot of everything HomeActivity renders. */
data class HomeUiState(
    val workspace: WorkspaceUi = WorkspaceUi.Loading,
    val drawer: DrawerUi = DrawerUi.Loading,
    /**
     * The query [drawer] was computed for. It lags [query] by the search debounce and compute
     * time; the previous content stays on screen meanwhile (never [DrawerUi.Loading]).
     */
    val drawerQuery: String = "",
    val query: String = "",
    val drawerOpen: Boolean = false,
    /** Null until the startup guard has decided; the workspace is not rendered before then. */
    val safeMode: Boolean? = null,
    val showCorruptLayoutNotice: Boolean = false,
    val showDefaultHomeBanner: Boolean = false,
    /** Current preferences; defaults until the stored value has loaded. */
    val settings: LauncherSettings = LauncherSettings(),
)

sealed interface WorkspaceUi {
    data object Loading : WorkspaceUi

    /** Safe mode: the workspace is intentionally not loaded or rendered. */
    data object Disabled : WorkspaceUi

    data class Failed(val reason: String) : WorkspaceUi

    data class Ready(
        val desktopGrid: GridSpec,
        val dockGrid: GridSpec,
        /** One entry per desktop page; never fewer pages than any item references. */
        val pages: List<List<HomeItemUi>>,
        val dock: List<HomeItemUi>,
    ) : WorkspaceUi
}

/** A workspace item resolved against the current app snapshot. */
data class HomeItemUi(
    val id: String,
    val placement: CellPlacement,
    val content: ItemContent,
    val actions: ItemActions = ItemActions(),
    val zIndex: Int = 0,
    /** Widget inner space override; null follows the global setting. */
    val widgetPadding: PaddingSize? = null,
)

/** What a cell shows. New [ItemTarget] kinds (folders, shortcuts, widgets) add cases here. */
sealed interface ItemContent {
    data class App(
        val key: AppKey,
        /** Null when the app list is not available, so the label is unknown. */
        val label: String?,
        val isWork: Boolean,
        val availability: Availability,
    ) : ItemContent

    /** [entries] in display order; each is [App] or [Shortcut]. */
    data class Folder(val title: String, val entries: List<FolderEntryUi>) : ItemContent

    /**
     * A widget placement. [appWidgetId] is 0 (`INVALID_APPWIDGET_ID`) when it needs rebinding.
     * [availability] reflects whether the provider's profile/app list confirms it is installed.
     */
    data class Widget(val appWidgetId: Int, val provider: ComponentKey, val availability: Availability) : ItemContent

    data class Shortcut(
        val packageName: String,
        val shortcutId: String,
        val userSerial: Long,
        val label: String?,
        val availability: Availability,
        val iconPackPackage: String? = null,
        val iconDrawableName: String? = null,
    ) : ItemContent
}

data class FolderEntryUi(val id: String, val content: ItemContent)

enum class Availability {
    AVAILABLE,

    /** The app list loaded and this key is not in it: render a placeholder, never auto-delete. */
    UNAVAILABLE,

    /**
     * The app list is loading or failed, or the item's profile was not listed (paused, locked or
     * failed). Launch attempts are still allowed and removal is not suggested.
     */
    UNKNOWN,
}

sealed interface DrawerUi {
    data object Loading : DrawerUi
    data class Failed(val reason: String) : DrawerUi
    data object NoApps : DrawerUi
    data object NoMatches : DrawerUi
    data class Apps(val apps: List<LauncherApp>) : DrawerUi
}

/** Resolves persisted workspace items against the current app snapshot. Pure and deterministic. */
fun buildWorkspaceUi(workspace: Workspace, apps: AppsState): WorkspaceUi.Ready {
    val loaded = apps as? AppsState.Loaded
    val byKey: Map<AppKey, LauncherApp>? = loaded?.apps?.associateBy { it.key }
    val listed = loaded?.listedProfiles.orEmpty()
    val resolved = workspace.items.map { item ->
        HomeItemUi(item.id, item.placement, resolveTarget(item.target, byKey, listed), item.actions, item.zIndex, item.widgetPadding)
    }
    val desktop = resolved.filter { it.placement.container == ContainerType.DESKTOP }
    val maxReferencedPage = desktop.maxOfOrNull { it.placement.page } ?: -1
    val pageCount = maxOf(workspace.pageCount, maxReferencedPage + 1, 1)
    val pages = List(pageCount) { page -> desktop.filter { it.placement.page == page } }
    val dock = resolved.filter { it.placement.container == ContainerType.DOCK }
    return WorkspaceUi.Ready(workspace.desktopGrid, workspace.dockGrid, pages, dock)
}

private fun resolveTarget(target: ItemTarget, byKey: Map<AppKey, LauncherApp>?, listed: Set<Long>): ItemContent =
    when (target) {
        is ItemTarget.App -> resolveApp(target.key, byKey, listed)
        is ItemTarget.Folder -> ItemContent.Folder(
            target.title,
            target.entries.map { FolderEntryUi(it.id, resolveTarget(it.target, byKey, listed)) },
        )
        is ItemTarget.Widget -> ItemContent.Widget(
            target.appWidgetId,
            target.provider,
            // Widget-only packages have no launcher activity, so absence is not proof of uninstall.
            packageAvailability(target.provider.packageName, target.provider.userSerial, byKey, listed, Availability.UNKNOWN),
        )
        is ItemTarget.Shortcut -> ItemContent.Shortcut(
            target.packageName,
            target.shortcutId,
            target.userSerial,
            target.label,
            packageAvailability(target.packageName, target.userSerial, byKey, listed, Availability.UNAVAILABLE),
            target.iconPackPackage, target.iconDrawableName,
        )
    }

/** Whether any launchable activity of [packageName] is listed in that profile. */
private fun packageAvailability(
    packageName: String,
    userSerial: Long,
    byKey: Map<AppKey, LauncherApp>?,
    listed: Set<Long>,
    whenMissing: Availability,
): Availability = when {
    byKey == null || userSerial !in listed -> Availability.UNKNOWN
    byKey.keys.any { it.packageName == packageName && it.userSerial == userSerial } -> Availability.AVAILABLE
    else -> whenMissing
}

private fun resolveApp(key: AppKey, byKey: Map<AppKey, LauncherApp>?, listedProfiles: Set<Long>): ItemContent.App {
    if (byKey == null || key.userSerial !in listedProfiles) {
        return ItemContent.App(key, label = null, isWork = false, availability = Availability.UNKNOWN)
    }
    val app = byKey[key]
        ?: return ItemContent.App(key, label = null, isWork = false, availability = Availability.UNAVAILABLE)
    return ItemContent.App(key, app.label, app.profileType == ProfileType.WORK, Availability.AVAILABLE)
}
