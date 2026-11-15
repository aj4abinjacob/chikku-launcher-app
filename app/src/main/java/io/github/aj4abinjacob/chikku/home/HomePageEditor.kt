package io.github.aj4abinjacob.chikku.home

import android.app.Activity
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessageBarView
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone

/** Adding and removing desktop pages from the home menu. */
internal class HomePageEditor(
    private val activity: Activity,
    private val viewModel: HomeViewModel,
    private val workspacePager: WorkspacePager,
    private val prompts: ChikkuPrompts,
    private val messageBar: ChikkuMessageBarView,
) {
    /** A page just added by the home menu, shown as soon as the layout has it; -1 if none. */
    private var pageToShow = -1

    fun canEditPages(): Boolean {
        val state = viewModel.uiState.value
        return state.workspace is WorkspaceUi.Ready && state.safeMode == false && !state.settings.home.layoutLocked
    }

    fun removeCurrentPage() {
        if (!canEditPages()) return
        val ready = viewModel.uiState.value.workspace as? WorkspaceUi.Ready ?: return
        if (ready.pages.size <= 1) {
            messageBar.show(R.string.home_remove_page_last)
            return
        }
        val page = workspacePager.currentPage
        val count = ready.pages.getOrNull(page)?.size ?: return
        if (count == 0) {
            workspacePager.animatePageRemoval(page) { viewModel.removePage(page) }
            return
        }
        prompts.show(
            PromptSpec(
                icon = R.drawable.ic_menu_remove_page,
                title = activity.getString(R.string.home_remove_page_confirm_title),
                message = activity.resources.getQuantityString(R.plurals.home_remove_page_confirm_message, count, count),
                tone = PromptTone.DESTRUCTIVE,
                primary = PromptAction(activity.getString(R.string.home_menu_remove_page)) {
                    workspacePager.animatePageRemoval(page) { viewModel.removePage(page, deleteItems = true) }
                },
                dismissLabel = activity.getString(R.string.home_cancel),
            ),
        )
    }

    /** Slide to the new page so it is obvious where it went. */
    fun onPageAdded(page: Int) {
        pageToShow = page
        showAddedPage()
    }

    fun showAddedPage() {
        val page = pageToShow
        if (page < 0 || page >= workspacePager.pageCount) return
        pageToShow = -1
        workspacePager.showAddedPage(page)
    }
}
