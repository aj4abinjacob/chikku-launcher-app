package io.github.aj4abinjacob.chikku.data

import io.github.aj4abinjacob.chikku.core.model.GestureAction

/** Validate before replacing live rows or using a snapshot to authorize widget-ID deletion.
 * Grid bounds and overlaps are deliberately not checked: historical layouts can be off-screen
 * after a grid change, and overlapping widgets are supported. Error text contains no row data.
 */
internal fun validateRecoverySnapshot(payload: SnapshotPayload) {
    require(payload.pageCount > 0) { "Invalid recovery page count" }
    require(payload.items.all { it.id.isNotBlank() } && payload.items.map { it.id }.toSet().size == payload.items.size) {
        "Invalid recovery item identities"
    }
    require(payload.folderItems.all { it.id.isNotBlank() } &&
        payload.folderItems.map { it.id }.toSet().size == payload.folderItems.size) { "Invalid recovery member identities" }
    val folders = payload.items.filter { it.kind == WorkspaceItemEntity.KIND_FOLDER }.mapTo(HashSet()) { it.id }
    val members = payload.folderItems.groupBy { it.folderId }
    members.forEach { (owner, entries) ->
        require(owner in folders && entries.all { it.rank >= 0 } && entries.map { it.rank }.toSet().size == entries.size) {
            "Invalid recovery folder membership"
        }
    }
    payload.items.forEach { row ->
        require(row.x2 >= 0 && row.y2 >= 0 && row.width2 > 0 && row.height2 > 0) { "Invalid recovery geometry" }
        require(when (row.container) {
            WorkspaceItemEntity.CONTAINER_DESKTOP -> row.page in 0 until payload.pageCount
            WorkspaceItemEntity.CONTAINER_DOCK -> row.page == 0
            else -> false
        }) { "Invalid recovery container" }
        require(listOf(row.tapAction, row.swipeUpAction, row.swipeDownAction).all { it == null || GestureAction.decode(it) != null }) {
            "Invalid recovery action"
        }
        // Domain conversion checks app/shortcut/provider identity and member kinds before any DELETE.
        row.toDomain(members[row.id].orEmpty())
    }
}
