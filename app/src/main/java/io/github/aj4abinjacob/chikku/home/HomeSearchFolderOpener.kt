package io.github.aj4abinjacob.chikku.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView
import io.github.aj4abinjacob.chikku.search.SearchActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Opens a folder that universal search asked for. A result carries only a stable item ID; fresh
 * personal visibility is validated before the folder opens.
 *
 * @param foldersReady false until the folder controller exists (a request may arrive earlier)
 * @param closeFolders closes the open folder popup
 * @param showFolder dismisses the other overlays and opens the verified, rendered folder
 */
internal class HomeSearchFolderOpener(
    private val activity: ComponentActivity,
    /** Folders a search result may open ([HomeViewModel.searchableFolderItems]). */
    private val searchableItems: Flow<Map<String, HomeItemUi>?>,
    private val messageBar: ChikkuMessageBarView,
    private val uiState: () -> HomeUiState,
    private val foldersReady: () -> Boolean,
    private val closeFolders: () -> Unit,
    private val showFolder: (HomeItemUi) -> Unit,
) {
    var pendingFolderId: String? = null
        private set
    private var openedSearchFolderId: String? = null
    private var folderSearchValidation: Job? = null
    private var searchableFolderItems: Map<String, HomeItemUi>? = null

    fun restore(savedInstanceState: Bundle?) {
        pendingFolderId = savedInstanceState?.getString("search_pending_folder")
        if (pendingFolderId == null) queueFolderSearchIntent(activity.intent)
    }

    /** A folder chosen in the in-window search overlay. */
    fun request(id: String) {
        pendingFolderId = id
        openedSearchFolderId = null
        tryOpenSearchFolder(uiState())
    }

    /** HOME pressed: forget any request and its validation. */
    fun reset() {
        pendingFolderId = null
        openedSearchFolderId = null
        folderSearchValidation?.cancel()
        folderSearchValidation = null
        searchableFolderItems = null
    }

    fun queueFolderSearchIntent(request: Intent?): Boolean {
        if (request?.action != SearchActivity.ACTION_OPEN_FOLDER) return false
        pendingFolderId = request.getStringExtra(SearchActivity.EXTRA_FOLDER_ID)?.takeIf { it.isNotBlank() && it.length <= 200 }
        request.removeExtra(SearchActivity.EXTRA_FOLDER_ID) // recreation must not replay a consumed request
        // Every request needs a fresh emission even when the repositories have not changed since
        // the previous folder. Reusing the active collector after clearing its snapshot can stall.
        folderSearchValidation?.cancel()
        folderSearchValidation = null
        searchableFolderItems = null
        if (foldersReady()) tryOpenSearchFolder(uiState())
        return true
    }

    fun tryOpenSearchFolder(state: HomeUiState) {
        if (pendingFolderId == null && openedSearchFolderId == null) {
            folderSearchValidation?.cancel()
            folderSearchValidation = null
            searchableFolderItems = null
            return
        }
        if (state.safeMode == true || state.workspace is WorkspaceUi.Disabled || state.workspace is WorkspaceUi.Failed) {
            pendingFolderId = null
            openedSearchFolderId = null
            folderSearchValidation?.cancel()
            folderSearchValidation = null
            return
        }
        if (state.safeMode != false || state.workspace !is WorkspaceUi.Ready) return
        if (folderSearchValidation == null) {
            // Ordinary HOME and safe mode do not add a second workspace subscription.
            folderSearchValidation = activity.lifecycleScope.launch {
                activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    // Derived from the ViewModel's resolved workspace; no second workspace build.
                    searchableItems.collect { safeItems ->
                        searchableFolderItems = safeItems
                        if (openedSearchFolderId != null && (safeItems == null || openedSearchFolderId !in safeItems)) {
                            closeFolders()
                            openedSearchFolderId = null
                        }
                        tryOpenSearchFolder(uiState())
                    }
                }
            }
        }
        val id = pendingFolderId ?: return
        val safeItems = searchableFolderItems ?: return
        val verified = safeItems[id]
        if (verified == null) {
            pendingFolderId = null
            messageBar.show(R.string.search_folder_unavailable)
            folderSearchValidation?.cancel()
            folderSearchValidation = null
            searchableFolderItems = null
            return
        }
        val ready = state.workspace
        val rendered = (ready.pages.flatten() + ready.dock).firstOrNull { it.id == id }
        if (rendered != verified) return // let the matching resolved workspace render first
        pendingFolderId = null
        openedSearchFolderId = id
        showFolder(rendered)
    }
}
