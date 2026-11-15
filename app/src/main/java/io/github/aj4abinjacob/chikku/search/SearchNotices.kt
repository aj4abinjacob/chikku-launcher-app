package io.github.aj4abinjacob.chikku.search

import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A short message for the search screen's snackbar; [id] lets the same text show again. */
data class SearchNotice(@StringRes val message: Int, val id: Long)

/**
 * One-shot notices from [SearchResultActions] ("No app can open this", "Copied"), rendered by
 * [SearchScreen] as an in-screen Material snackbar rather than an OEM-skinned toast. Main thread only.
 */
internal class SearchNotices {
    private var nextId = 0L
    private val _current = MutableStateFlow<SearchNotice?>(null)

    /** The notice to show, or null. A newer notice replaces one still on screen. */
    val current: StateFlow<SearchNotice?> = _current.asStateFlow()

    fun show(@StringRes message: Int) {
        _current.value = SearchNotice(message, nextId++)
    }

    /** Called by the screen once [notice] has been shown and dismissed. */
    fun shown(notice: SearchNotice) {
        _current.compareAndSet(notice, null)
    }
}
