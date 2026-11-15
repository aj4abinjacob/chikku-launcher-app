package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.cards.CardContent
import java.util.UUID

/**
 * Everything a Chikku backup file holds: settings, workspace (items, folders, shortcuts and widget
 * placements), per-app customizations and drawer groups.
 *
 * Widget ids are device-local and never exported: every [ItemTarget.Widget] in a decoded backup has
 * `appWidgetId == 0` ("needs rebind"), so a restore always allocates fresh ids. Apps that are not
 * installed on the restoring device stay as placements (resolvable placeholders); nothing is dropped.
 */
data class ChikkuBackup(
    val createdAtMillis: Long,
    /** versionName of the app that wrote the backup; informational only. */
    val appVersion: String,
    val settings: LauncherSettings,
    val workspace: WorkspaceContent,
    val customizations: List<AppCustomization>,
    val drawerGroups: List<DrawerGroup>,
) {
    /** A copy with every widget id cleared, as stored in a backup file. */
    fun withoutWidgetIds(): ChikkuBackup = copy(
        settings = settings.copy(cards = settings.cards.copy(cards = settings.cards.cards.map { card ->
            val embedded = card.content as? CardContent.Widget
            if (embedded == null) card else card.copy(content = embedded.copy(instanceId = UUID.randomUUID().toString()))
        })),
        workspace = workspace.copy(
            items = workspace.items.map { item ->
                val target = item.target
                if (target is ItemTarget.Widget && target.appWidgetId != 0) item.copy(target = target.copy(appWidgetId = 0)) else item
            },
        ),
    )

    /** Every app referenced by the workspace (top level and inside folders), in first-seen order. */
    fun referencedApps(): List<AppKey> = workspace.items.flatMap { item ->
        when (val t = item.target) {
            is ItemTarget.App -> listOf(t.key)
            is ItemTarget.Folder -> t.entries.mapNotNull { (it.target as? ItemTarget.App)?.key }
            else -> emptyList()
        }
    }.distinct()

    /** Counts for the restore preview. [installed] null means "unknown" (missingApps is then null). */
    fun summary(installed: Set<AppKey>?): BackupSummary {
        val items = workspace.items
        val usedPages = items.filter { it.placement.container == ContainerType.DESKTOP }
            .maxOfOrNull { it.placement.page + 1 } ?: 1
        return BackupSummary(
            pages = maxOf(1, workspace.pageCount, usedPages),
            items = items.size,
            dockItems = items.count { it.placement.container == ContainerType.DOCK },
            folders = items.count { it.target is ItemTarget.Folder },
            widgets = items.count { it.target is ItemTarget.Widget },
            shortcuts = items.count { it.target is ItemTarget.Shortcut } +
                items.sumOf { item -> (item.target as? ItemTarget.Folder)?.entries?.count { it.target is ItemTarget.Shortcut } ?: 0 },
            customizations = customizations.size,
            drawerGroups = drawerGroups.size,
            missingApps = installed?.let { set -> referencedApps().count { it !in set } },
        )
    }
}

/** What a restore would change; shown to the user before confirming. Contains counts only, never names. */
data class BackupSummary(
    val pages: Int,
    val items: Int,
    val dockItems: Int,
    val folders: Int,
    val widgets: Int,
    val shortcuts: Int,
    val customizations: Int,
    val drawerGroups: Int,
    /** Apps on the layout that are not installed here; they restore as placeholders. Null if unknown. */
    val missingApps: Int?,
)

/** Why a backup file could not be read. A failed read never changes anything. */
sealed interface BackupError {
    /** Larger than [BackupCodec.MAX_BYTES] characters. */
    data object TooLarge : BackupError

    /** Not JSON at all (truncated, binary, wrong file). */
    data object NotJson : BackupError

    /** Valid JSON but not a Chikku backup. */
    data object NotChikkuBackup : BackupError

    /** Written by a newer Chikku whose format this version cannot read. */
    data class UnsupportedVersion(val found: Long, val supported: Int) : BackupError

    /** A Chikku backup whose content is damaged or inconsistent; [detail] is for tests, not user display. */
    data class Invalid(val detail: String) : BackupError
}

sealed interface BackupDecodeResult {
    data class Success(val backup: ChikkuBackup) : BackupDecodeResult
    data class Failure(val error: BackupError) : BackupDecodeResult
}
