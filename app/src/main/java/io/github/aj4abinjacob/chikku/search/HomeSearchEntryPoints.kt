package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSearchBarTapAction
import io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter
import android.content.Context
import io.github.aj4abinjacob.chikku.home.HomeSearchBarMetrics

/**
 * Wires HOME's search entry points: the home search bar (top or below the dock, per
 * [HomeSettings.showSearchBar] and [HomeSettings.searchBarPosition]) with its Chikku and web
 * buttons, and the drawer's "search contacts, shortcuts and the web" button, which hands over the
 * drawer query. Chikku search goes through [SearchLauncher]; web search through [external], which
 * applies App lock to the receiving app. Keeps HomeActivity's part to one bind call and one render call.
 */
class HomeSearchEntryPoints(
    private val activity: Activity,
    private val drawerSearch: EditText,
    private val external: ExternalIntentStarter = ExternalIntentStarter.DENY,
) {
    private val topBar: SearchWidgetView = activity.findViewById(R.id.home_search_bar_top)
    private val bottomBar: SearchWidgetView = activity.findViewById(R.id.home_search_bar_bottom)
    private val originalMargins = listOf(topBar, bottomBar).associateWith { bar ->
        val params = bar.layoutParams as LinearLayout.LayoutParams
        Triple(params.marginStart, params.marginEnd, params.bottomMargin)
    }
    private val drawerMore: View = activity.findViewById(R.id.drawer_search_more)
    private var rendered: Pair<Boolean, HomeSearchBarPosition>? = null
    private var barSettings = SearchSettings()
    private var tapAction = HomeSearchBarTapAction.CHIKKU_SEARCH
    private var engine = SearchSettings().webEngine

    fun bind() {
        listOf(topBar, bottomBar).mapNotNull { it.parent as? View }.distinct().forEach { parent ->
            parent.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) applyBarSizes()
            }
        }
        listOf(topBar, bottomBar).forEach { bar ->
            bar.setOnClickListener {
                if (tapAction == HomeSearchBarTapAction.WEB_SEARCH) openWeb() else SearchLauncher.open(activity)
            }
            bar.chikkuButton.setOnClickListener { SearchLauncher.open(activity) }
            bar.webButton.setOnClickListener { openWeb() }
            listOf(bar.chikkuButton, bar.webButton).forEach { it.isFocusable = true }
        }
        drawerMore.setOnClickListener { openWithDrawerQuery() }
        drawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                drawerMore.visibility = if (s.isNullOrBlank()) View.GONE else View.VISIBLE
            }
        })
        drawerMore.visibility = if (drawerSearch.text.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Opens universal search with the drawer's current query (e.g. IME "Go" with no app match). */
    fun openWithDrawerQuery(): Boolean = SearchLauncher.open(activity, drawerSearch.text?.toString())

    /** The selected engine with nothing typed: the Google app's search for Google, else its home page. */
    private fun openWeb(): Boolean = external.start(SearchHandoff.webHomeIntents(engine))

    fun render(home: HomeSettings, search: SearchSettings = SearchSettings()) {
        barSettings = search
        tapAction = search.widgetTapAction
        engine = search.webEngine
        applyBarSizes()
        val description = homeSearchBarDescription(activity, search)
        listOf(topBar, bottomBar).forEach {
            it.applyAppearance(search)
            it.contentDescription = description
        }
        val state = home.showSearchBar to home.searchBarPosition
        if (state == rendered) return
        rendered = state
        val (show, position) = state
        topBar.visibility = if (show && position == HomeSearchBarPosition.TOP) View.VISIBLE else View.GONE
        bottomBar.visibility = if (show && position == HomeSearchBarPosition.BELOW_DOCK) View.VISIBLE else View.GONE
    }

    private fun applyBarSizes() {
        listOf(topBar, bottomBar).forEach { bar ->
            val parent = bar.parent as? View ?: return@forEach
            val params = bar.layoutParams as? LinearLayout.LayoutParams ?: return@forEach
            val density = bar.resources.displayMetrics.density
            val original = originalMargins.getValue(bar)
            val metrics = HomeSearchBarMetrics.of(barSettings, density, parent.width - parent.paddingLeft - parent.paddingRight,
                original.first, original.second, original.third, belowDock = bar === bottomBar)
            val startMargin = metrics.startMargin
            val endMargin = metrics.endMargin
            val bottomMargin = metrics.bottomMargin
            val marginsChanged = params.marginStart != startMargin || params.marginEnd != endMargin || params.bottomMargin != bottomMargin
            val width = metrics.width ?: ViewGroup.LayoutParams.MATCH_PARENT
            val height = metrics.height
            val topMargin = if (bar === bottomBar) metrics.dockGap else params.topMargin
            if (marginsChanged || params.width != width || params.height != height || params.topMargin != topMargin ||
                params.gravity != Gravity.CENTER_HORIZONTAL) {
                params.marginStart = startMargin
                params.marginEnd = endMargin
                params.bottomMargin = bottomMargin
                params.topMargin = topMargin
                params.width = width
                params.height = height
                params.gravity = Gravity.CENTER_HORIZONTAL
                bar.layoutParams = params
            }
        }
    }
}

/** What a tap on the bar itself does, for TalkBack; the buttons describe themselves. */
fun homeSearchBarDescription(context: Context, search: SearchSettings): String = when {
    search.widgetTapAction == HomeSearchBarTapAction.WEB_SEARCH ->
        context.getString(R.string.search_home_bar_web_description, WebSearchUrls.engineName(search.webEngine))
    search.showWebSuggestionRow ->
        context.getString(R.string.search_home_bar_engine_description, WebSearchUrls.engineName(search.webEngine))
    else -> context.getString(R.string.search_home_bar_description)
}
