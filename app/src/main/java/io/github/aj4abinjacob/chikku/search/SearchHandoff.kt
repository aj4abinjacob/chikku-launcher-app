package io.github.aj4abinjacob.chikku.search

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import androidx.core.net.toUri
import io.github.aj4abinjacob.chikku.core.model.search.MicroResult
import io.github.aj4abinjacob.chikku.core.model.search.MicroResults
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.WebSearchUrls
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import io.github.aj4abinjacob.chikku.platform.AndroidGoogleDiscoverResolver

/** The actions on a search person card. */
sealed interface PersonAction {
    /** Opens the contact in the contacts app. */
    data object Open : PersonAction

    /** Opens the dialer prefilled; never places the call. */
    data object Call : PersonAction

    /** Opens the SMS composer prefilled; never sends. */
    data object TextMessage : PersonAction

    /** A communication service's validated entry point (see [ContactActionLauncher][io.github.aj4abinjacob.chikku.platform.ContactActionLauncher]). */
    data class Service(val action: ContactChannelAction) : PersonAction
}

/**
 * Intents that hand a query or result to another app on an explicit action. Online autocomplete
 * is a separate opt-in setting. Handoffs use the receiving app for network access. Each intent starts a new task so the other app does not join HOME's task.
 */
object SearchHandoff {

    fun webSearchIntent(engine: WebSearchEngine, query: String): Intent =
        view(WebSearchUrls.webSearch(engine, query)).addCategory(Intent.CATEGORY_BROWSABLE)

    /**
     * The home search bar's web action, in the order to try: for Google, the Google app's own search
     * screen; then [engine]'s home page in a browser. No query is sent.
     */
    fun webHomeIntents(engine: WebSearchEngine): List<Intent> = buildList {
        if (engine == WebSearchEngine.GOOGLE) {
            add(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH).setPackage(AndroidGoogleDiscoverResolver.GOOGLE_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        add(view(WebSearchUrls.homePage(engine)).addCategory(Intent.CATEGORY_BROWSABLE))
    }

    /** Store app first, then the store website. */
    fun playStoreIntents(query: String): List<Intent> = listOf(
        view(WebSearchUrls.playStoreMarket(query)),
        view(WebSearchUrls.playStoreWeb(query)).addCategory(Intent.CATEGORY_BROWSABLE),
    )

    fun contactIntent(contact: ContactHit): Intent =
        Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * The intent for a phone or contacts person-card action, or null when the contact lacks what it
     * needs or the action is a service action. Call opens the dialer prefilled (no CALL_PHONE).
     */
    fun personIntent(contact: ContactHit, action: PersonAction): Intent? = when (action) {
        PersonAction.Open -> contactIntent(contact)
        PersonAction.Call -> contact.phoneNumber?.let {
            Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", it, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        PersonAction.TextMessage -> contact.phoneNumber?.let {
            Intent(Intent.ACTION_SENDTO, android.net.Uri.fromParts("smsto", it, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        is PersonAction.Service -> null
    }

    /** Opens a dialer or SMS composer; never calls or sends a message itself. */
    fun microIntent(context: Context, action: MicroResult.Action): Intent? {
        val value = action.value
        if (value.isBlank() || value.length > MicroResults.MAX_QUERY_LENGTH || value.any { it.isISOControl() }) return null
        return when (action.kind) {
            MicroResult.Kind.DIAL, MicroResult.Kind.SMS -> {
                if (!Regex("^\\+?[0-9]{7,15}$").matches(value)) return null
                Intent(
                    if (action.kind == MicroResult.Kind.DIAL) Intent.ACTION_DIAL else Intent.ACTION_SENDTO,
                    android.net.Uri.fromParts(if (action.kind == MicroResult.Kind.DIAL) "tel" else "smsto", value, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            MicroResult.Kind.MAP -> view("geo:0,0?q=${WebSearchUrls.encode(value)}")
            else -> MicroResults.webUrl(action)?.let { url ->
                val target = view(url).addCategory(Intent.CATEGORY_BROWSABLE)
                if (action.kind == MicroResult.Kind.URL) {
                    Intent.createChooser(target, context.getString(R.string.search_micro_open_with))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } else target
            }
        }
    }

    private fun view(url: String) = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * The single entry point to Chikku's search UI, for the home search bar, the drawer search field
 * and the [GestureAction.OpenSearch][io.github.aj4abinjacob.chikku.core.model.GestureAction.OpenSearch]
 * gesture. Opens the non-exported [SearchActivity] over HOME.
 */
object SearchLauncher {
    internal const val EXTRA_QUERY = "io.github.aj4abinjacob.chikku.search.QUERY"

    fun intent(context: Context, initialQuery: String? = null): Intent =
        Intent(context, SearchActivity::class.java).apply {
            if (!initialQuery.isNullOrBlank()) putExtra(EXTRA_QUERY, initialQuery)
        }

    /** Opens search from [activity], prefilled with [initialQuery]. False if it could not start. */
    fun open(activity: Activity, initialQuery: String? = null): Boolean = try {
        if (activity is io.github.aj4abinjacob.chikku.home.HomeActivity) {
            activity.showUniversalSearch(initialQuery)
        } else {
            activity.startActivity(intent(activity, initialQuery))
            true
        }
    } catch (_: ActivityNotFoundException) {
        false
    }
}
