package io.github.aj4abinjacob.chikku.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.search.CalculationResult
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.MicroResult
import io.github.aj4abinjacob.chikku.core.model.search.QueryLearning
import io.github.aj4abinjacob.chikku.core.model.search.RecentItem
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistory
import io.github.aj4abinjacob.chikku.core.model.search.SearchRow
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.platform.ContactActionLauncher
import io.github.aj4abinjacob.chikku.platform.ContactActionResult
import io.github.aj4abinjacob.chikku.platform.GuardedIntentLauncher
import io.github.aj4abinjacob.chikku.platform.IntentHandoffResult
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.settings.SettingsActivity
import io.github.aj4abinjacob.chikku.settings.SettingsSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Shared explicit result actions for HOME's search layer and the standalone search activity.
 * Failures and "Copied" never finish search, so they go to [notify], which the host shows in the
 * search screen's snackbar (see [SearchNotices]).
 */
internal class SearchResultActions(
    private val activity: ComponentActivity,
    private val deps: HomeDependencies,
    private val unlocker: AppUnlocker,
    private val onComplete: () -> Unit,
    private val notify: (Int) -> Unit,
    private val isActive: () -> Boolean = { true },
    private val intentLauncher: GuardedIntentLauncher = GuardedIntentLauncher(activity, deps.appLock, mayLaunch = isActive),
    private val actionScope: CoroutineScope = activity.lifecycleScope,
    private val contactActions: ContactActionLauncher = ContactActionLauncher(activity, deps.appLock, Dispatchers.IO),
    private val prepareContact: suspend (ContactHit, ContactChannelAction) -> ContactActionLauncher.Prepared = contactActions::prepare,
    private val startContact: (ContactActionLauncher.Prepared.Ready) -> ContactActionResult = contactActions::start,
    /** The text in the search field, read when a result is tapped, so opening it teaches [QueryLearning]. */
    private val currentQuery: () -> String = { "" },
    private val openFolder: (String) -> Unit,
) {
    private val shortcutStarter = ShortcutStarter(activity, deps.appLock)
    private var serviceLaunch: Job? = null
    private var closed = false
    private var generation = 0L
    private var authenticatedRetry: (() -> Unit)? = null
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            val retry = authenticatedRetry
            authenticatedRetry = null
            if (mayAct()) retry?.invoke()
        }
        override fun onStop(owner: LifecycleOwner) = cancelPending()
        override fun onDestroy(owner: LifecycleOwner) = close()
    }

    private fun ownsSession(): Boolean = !closed && isActive() && !activity.isFinishing && !activity.isDestroyed

    private fun mayAct(): Boolean = ownsSession() && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    /** Device credentials can stop standalone search (Android 10). A completed authentication
     * belongs to this session, unlike a provider lookup that must be invalidated on stop. */
    private fun retryAfterAuthentication(retry: () -> Unit) {
        if (!ownsSession()) return
        if (mayAct()) retry() else authenticatedRetry = retry
    }

    private fun cancelPending() {
        generation++
        serviceLaunch?.cancel()
        serviceLaunch = null
        handoffJob?.cancel()
        handoffJob = null
        chooser.dismiss()
    }

    fun close() {
        closed = true
        authenticatedRetry = null
        cancelPending()
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    /** Fed by the search screen; app and shortcut launches reveal from the tapped spot. */
    val tapOrigin = TapOrigin()

    private var handoffJob: Job? = null
    private val chooser = SearchIntentChooser(activity, deps.settingsRepository.settings)

    init { activity.lifecycle.addObserver(lifecycleObserver) }

    fun perform(row: SearchRow) = perform(row, currentQuery())

    private fun perform(row: SearchRow, query: String) {
        if (!mayAct()) return
        when (row) {
            is SearchRow.Micro -> when (val result = row.result) {
                is MicroResult.Conversion -> copy(result.display)
                is MicroResult.Action -> SearchHandoff.microIntent(activity, result)?.let { handOff(listOf(it)) }
                    ?: notify(R.string.search_open_failed)
            }
            is SearchRow.Folder -> {
                remember(HistoryTarget.Folder(row.folder.itemId))
                learn(HistoryTarget.Folder(row.folder.itemId), query)
                onComplete()
                openFolder(row.folder.itemId)
            }
            // Apps are remembered by the launcher itself (every successful launch, not only search).
            is SearchRow.App -> finishIfStarted(deps.appLauncher.launch(row.app.key, tapBounds()), row, query) {
                learn(HistoryTarget.App(row.app.key), query)
            }
            is SearchRow.Shortcut -> finishIfStarted(shortcutStarter.start(row.shortcut, tapBounds()), row, query) {
                val target = HistoryTarget.Shortcut(row.shortcut.packageName, row.shortcut.shortcutId, row.shortcut.userSerial)
                remember(target)
                learn(target, query)
            }
            is SearchRow.Contact -> handOff(listOf(SearchHandoff.contactIntent(row.contact)), HistoryTarget.Contact(row.contact.lookupKey), query)
            is SearchRow.SuggestedApp -> perform(SearchRow.App(row.app), query)
            is SearchRow.SuggestedContact -> perform(SearchRow.Contact(row.contact), query)
            is SearchRow.Person -> performPerson(row.contact, PersonAction.Open, query)
            is SearchRow.WebSearch -> handOff(listOf(SearchHandoff.webSearchIntent(row.engine, row.query)), webQuery(row.query))
            is SearchRow.PlayStore -> handOff(SearchHandoff.playStoreIntents(row.query), webQuery(row.query))
            is SearchRow.Recent -> when (val item = row.item) {
                is RecentItem.Contact -> perform(SearchRow.Contact(item.contact), query)
                is RecentItem.Shortcut -> perform(SearchRow.Shortcut(item.shortcut), query)
                is RecentItem.Folder -> perform(SearchRow.Folder(item.folder), query)
                is RecentItem.Query -> Unit // the search screen puts the text back in the field
            }
            is SearchRow.Calculation -> (row.result as? CalculationResult.Value)?.let { copy(it.display) }
            SearchRow.ContactsPermissionNeeded -> activity.startActivity(
                Intent(activity, SettingsActivity::class.java)
                    .putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.SEARCH.name),
            )
        }
    }

    fun performPerson(contact: ContactHit, action: PersonAction) = performPerson(contact, action, currentQuery())

    private fun performPerson(contact: ContactHit, action: PersonAction, query: String) {
        if (!mayAct()) return
        if (action is PersonAction.Service) return performService(contact, action, query)
        SearchHandoff.personIntent(contact, action)?.let { handOff(listOf(it), HistoryTarget.Contact(contact.lookupKey), query) } ?: notify(R.string.search_open_failed)
    }

    /** The empty-search controls: remove one item, clear all, pause or resume learning, open settings. */
    val historyControls = SearchHistoryControls(
        onForget = { target ->
            deps.searchHistoryRepository?.let { history -> deps.appScope.launch { history.forget(target) } }
            notify(R.string.search_history_removed)
        },
        onClear = { deps.searchHistoryRepository?.let { history -> deps.appScope.launch { history.clear() } } },
        onSetLearning = { on ->
            deps.appScope.launch { deps.settingsRepository.update { it.copy(search = it.search.copy(historyLearningEnabled = on)) } }
        },
        onOpenSettings = {
            activity.startActivity(
                Intent(activity, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_SECTION, SettingsSection.SEARCH.name),
            )
        },
    )

    /** Remembers [target] (when learning is on; the store decides) on the app scope, so closing search cannot cancel it. */
    private fun remember(target: HistoryTarget) {
        val history = deps.searchHistoryRepository ?: return
        deps.appScope.launch { history.record(target) }
    }

    /** Teaches [QueryLearning] that [target] was opened after typing [query] (the store decides, like [remember]). */
    private fun learn(target: HistoryTarget, query: String) {
        if (SearchHistory.learnedPrefixes(query).isEmpty()) return
        val history = deps.searchHistoryRepository ?: return
        deps.appScope.launch { history.learn(target, query) }
    }

    private fun webQuery(query: String): HistoryTarget? = SearchHistory.rememberedQuery(query)?.let(HistoryTarget::WebQuery)

    /** Validates off the main thread (the row may be stale), then starts on an explicit tap only. */
    private fun performService(contact: ContactHit, action: PersonAction.Service, query: String) {
        if (!mayAct() || serviceLaunch?.isActive == true) return
        val requestGeneration = generation
        serviceLaunch = actionScope.launch {
            val prepared = prepareContact(contact, action.action)
            // Provider cancellation can be slow/non-cooperative. Check ownership immediately
            // before the platform start, even when the coroutine eventually returns a value.
            if (!mayAct() || generation != requestGeneration) return@launch
            val result = when (prepared) {
                is ContactActionLauncher.Prepared.Failed -> prepared.result
                is ContactActionLauncher.Prepared.Ready -> startContact(prepared)
            }
            if (mayAct() && generation == requestGeneration) report(result, contact, action, query)
        }
    }

    private fun report(result: ContactActionResult, contact: ContactHit, action: PersonAction.Service, query: String) {
        when (result) {
            ContactActionResult.Started -> {
                remember(HistoryTarget.Contact(contact.lookupKey))
                learn(HistoryTarget.Contact(contact.lookupKey), query)
                onComplete()
            }
            is ContactActionResult.Locked -> unlocker.unlock(result.launch) {
                retryAfterAuthentication { performService(contact, action, query) }
            }
            ContactActionResult.PermissionMissing -> notify(R.string.search_contact_action_permission)
            ContactActionResult.ContactChanged -> notify(R.string.search_contact_action_changed)
            ContactActionResult.Unavailable -> notify(R.string.search_contact_action_unavailable)
        }
    }

    private fun finishIfStarted(result: LaunchResult, row: SearchRow, query: String, onStarted: () -> Unit = {}) {
        when (result) {
            LaunchResult.Started -> { onStarted(); onComplete() }
            is LaunchResult.Locked -> unlocker.unlock(result) {
                retryAfterAuthentication { perform(row, query) }
            }
            is LaunchResult.Unavailable -> notify(
                if (result.reason == LaunchResult.Reason.SHORTCUT_UNAVAILABLE)
                    R.string.shortcut_needs_recreation else R.string.home_launch_unavailable,
            )
        }
    }

    /** [learnQuery]: the typed text to learn [target] for; null for web and store handoffs. */
    private fun handOff(intents: List<Intent>, target: HistoryTarget? = null, learnQuery: String? = null) {
        if (!mayAct() || handoffJob?.isActive == true || chooser.isShowing) return
        handoffJob = actionScope.launch {
            val result = intentLauncher.startFirst(intents)
            handleHandoff(result, target, learnQuery)
        }
    }

    private fun handleHandoff(result: IntentHandoffResult, target: HistoryTarget? = null, learnQuery: String? = null) {
        if (!mayAct()) return
        val requestGeneration = generation
        when (result) {
            IntentHandoffResult.Started -> {
                target?.let(::remember)
                if (target != null && learnQuery != null) learn(target, learnQuery)
                onComplete()
            }
            IntentHandoffResult.Unavailable -> notify(R.string.search_open_failed)
            is IntentHandoffResult.Locked -> unlocker.unlock(result.lock) {
                retryAfterAuthentication { handleHandoff(intentLauncher.start(result.target), target, learnQuery) }
            }
            is IntentHandoffResult.Choose -> chooser.show(result.targets) { chosen ->
                if (mayAct() && generation == requestGeneration) handleHandoff(intentLauncher.start(chosen), target, learnQuery)
            }
        }
    }

    private fun tapBounds(): Rect? = tapOrigin.bounds((ICON_SIZE_DP * activity.resources.displayMetrics.density).toInt())

    private fun copy(text: String) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.search_section_calculator), text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) notify(R.string.search_calc_copied)
    }
}

/** Size of the square a search launch reveals from, about one result icon. */
private const val ICON_SIZE_DP = 48f
