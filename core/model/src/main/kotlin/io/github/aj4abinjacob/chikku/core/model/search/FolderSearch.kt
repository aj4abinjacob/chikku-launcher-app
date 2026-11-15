package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem

/** An explicit open-folder target; no member names or profile details are carried into results. */
data class FolderHit(val itemId: String, val title: String, val itemCount: Int)

/** Personal folders only; a folder title is withheld if any member is hidden or unavailable. */
object FolderSearch {
    fun visibleFolders(items: List<WorkspaceItem>, apps: List<LauncherApp>, hidden: Set<AppKey>): List<FolderHit> {
        val personal = apps.filter { it.profileType == ProfileType.PERSONAL && it.key !in hidden }
        val keys = personal.mapTo(HashSet()) { it.key }
        val owners = personal.mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
        return items.mapNotNull { item ->
            val folder = item.target as? ItemTarget.Folder ?: return@mapNotNull null
            if (folder.title.isBlank() || folder.entries.isEmpty()) return@mapNotNull null
            val fullyVisible = folder.entries.all { entry ->
                when (val target = entry.target) {
                    is ItemTarget.App -> target.key in keys
                    is ItemTarget.Shortcut -> (target.packageName to target.userSerial) in owners
                    else -> false
                }
            }
            if (fullyVisible) FolderHit(item.id, folder.title, folder.entries.size) else null
        }
    }

    fun search(items: List<WorkspaceItem>, apps: List<LauncherApp>, hidden: Set<AppKey>, query: String): List<FolderHit> {
        val needle = normalizeForSearch(query)
        if (needle.isBlank()) return emptyList()
        return visibleFolders(items, apps, hidden)
            .mapIndexedNotNull { index, folder ->
                DefaultAppSearcher.rank(normalizeForSearch(folder.title), needle)?.let { Triple(it, index, folder) }
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
            .take(MAX_FOLDERS)
    }

    private const val MAX_FOLDERS = 6
}
