package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Launch identity of an app shortcut (static, dynamic or pinned): package, shortcut ID and profile. */
data class ShortcutKey(val packageName: String, val shortcutId: String, val userSerial: Long)

/**
 * An app shortcut published by a visible app (static or dynamic) or pinned to Chikku's home screen
 * (e.g. a browser site shortcut). [appKey] is its owning activity, for the icon.
 */
data class ShortcutHit(
    val packageName: String,
    val shortcutId: String,
    val userSerial: Long,
    val label: String,
    val appKey: AppKey?,
) {
    val key: ShortcutKey get() = ShortcutKey(packageName, shortcutId, userSerial)
}

/** Shortcuts the platform let the launcher read. Only the default HOME app may read them. */
sealed interface ShortcutLookup {
    data class Found(val shortcuts: List<ShortcutHit>) : ShortcutLookup

    /** Chikku is not the default HOME app (no shortcut host permission). */
    data object NotDefaultHome : ShortcutLookup

    /** The platform refused or failed; search continues without shortcuts. */
    data object Unavailable : ShortcutLookup
}

/**
 * A personal contact matched locally by name or by the platform filter. Never stored or logged.
 *
 * [phoneNumber] is the contact's primary (else first) number, [channelActions] the synced data rows
 * of communication apps (see [ContactServices]) whose entry points this device can start, and
 * [photoUri] a thumbnail content URI. A synced row is the app's own evidence that this contact is
 * reachable there; an installed app alone is not.
 */
data class ContactHit(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val phoneNumber: String? = null,
    val photoUri: String? = null,
    val channelActions: List<ContactChannelAction> = emptyList(),
)

sealed interface ContactLookup {
    data class Found(val contacts: List<ContactHit>) : ContactLookup

    /** Contacts are enabled in settings but READ_CONTACTS is not (or no longer) granted. */
    data object PermissionMissing : ContactLookup

    data object Unavailable : ContactLookup
}

/** Reads app shortcuts of [apps] (already filtered to visible, non-hidden apps). Never throws. */
fun interface ShortcutSource {
    suspend fun shortcuts(apps: List<LauncherApp>): ShortcutLookup
}

/** Queries contacts matching [query], at most [limit]. Never throws. */
fun interface ContactSource {
    suspend fun contacts(query: String, limit: Int): ContactLookup

    /**
     * Contacts whose name is a few typos from [query] ([TypoSuggestions.distance]), best first, at
     * most [limit]. Only asked when the normal search found nothing. Never throws.
     */
    suspend fun suggestions(query: String, limit: Int): ContactLookup = ContactLookup.Found(emptyList())

    /** Contacts by lookup key (from search history), in [lookupKeys] order; deleted ones are left out. Never throws. */
    suspend fun byLookupKeys(lookupKeys: List<String>): ContactLookup = ContactLookup.Found(emptyList())
}

/** A remembered search target resolved for display, shown in "Recent" when the query is blank. */
sealed interface RecentItem {
    val target: HistoryTarget

    data class Query(val text: String) : RecentItem {
        override val target get() = HistoryTarget.WebQuery(text)
    }
    data class Contact(val contact: ContactHit) : RecentItem {
        override val target get() = HistoryTarget.Contact(contact.lookupKey)
    }
    data class Shortcut(val shortcut: ShortcutHit) : RecentItem {
        override val target get() = HistoryTarget.Shortcut(shortcut.packageName, shortcut.shortcutId, shortcut.userSerial)
    }
    data class Folder(val folder: FolderHit) : RecentItem {
        override val target get() = HistoryTarget.Folder(folder.itemId)
    }
}

/** One row of the search UI, in display order. */
sealed interface SearchRow {
    data class Calculation(val expression: String, val result: CalculationResult) : SearchRow
    data class Micro(val result: MicroResult) : SearchRow
    data class App(val app: LauncherApp, val suggested: Boolean = false) : SearchRow
    data class Folder(val folder: FolderHit) : SearchRow
    data class Shortcut(val shortcut: ShortcutHit) : SearchRow
    data class Contact(val contact: ContactHit, val suggested: Boolean = false) : SearchRow

    /** The best contact match as a person card with phone actions and [SearchResults.contactServices] rows, shown first. */
    data class Person(val contact: ContactHit) : SearchRow

    /** Contacts are on but the permission was revoked; tapping leads to the place to grant it. */
    data object ContactsPermissionNeeded : SearchRow
    data class WebSearch(val engine: WebSearchEngine, val query: String) : SearchRow
    data class PlayStore(val query: String) : SearchRow

    /** "Did you mean": an app a few typos from a query that matched nothing. Never the "Go" row. */
    data class SuggestedApp(val app: LauncherApp) : SearchRow

    /** "Did you mean": a contact a few typos from a query that matched nothing. */
    data class SuggestedContact(val contact: ContactHit) : SearchRow

    /** Something opened from search before; only shown for a blank query. Tapping a query fills the field. */
    data class Recent(val item: RecentItem) : SearchRow
}

private fun HistoryTarget.Shortcut.matches(hit: ShortcutHit): Boolean =
    hit.packageName == packageName && hit.shortcutId == shortcutId && hit.userSerial == userSerial

/** Results for one query. [rows] is the merged display order. */
data class SearchResults(
    val query: String,
    val calculation: CalculationResult? = null,
    val apps: List<LauncherApp> = emptyList(),
    /** Null when shortcut search is off. */
    val shortcuts: ShortcutLookup? = null,
    /** Null when contact search is off (the source was not called). */
    val contacts: ContactLookup? = null,
    val webEngine: WebSearchEngine? = null,
    val playStore: Boolean = false,
    val microResults: List<MicroResult> = emptyList(),
    val folders: List<FolderHit> = emptyList(),
    /** Best original/custom label rank, independent of the displayed custom name. */
    val appMatchRanks: Map<AppKey, Int> = emptyMap(),
    /** Communication services a person card may show, in display order (see [ContactServices.available]). */
    val contactServices: List<ContactService> = emptyList(),
    /** Typo suggestions; only filled when nothing above matched (see [UniversalSearch.search]). */
    val suggestedApps: List<LauncherApp> = emptyList(),
    val suggestedContacts: List<ContactHit> = emptyList(),
    /** Blank query only: remembered searches, contacts, shortcuts and folders, best first. */
    val recent: List<RecentItem> = emptyList(),
    /** A result the user habitually opens for this query ([QueryLearning]); shown first when present. */
    val learnedTop: HistoryTarget? = null,
) {
    /**
     * Merge order: person card, calculation, apps, shortcuts, contacts, "did you mean" suggestions,
     * then the web and Play Store
     * handoff rows, which come last so a local match is always the first (IME "Go") result. The best
     * contact becomes a leading person card when [personFirst] holds. A [learnedTop] result goes
     * first instead: a learned contact becomes the person card, a learned app leads the apps, and a
     * learned shortcut or folder brings its section to the very top. Without a learned top, a
     * shortcut named exactly the query (a browser site shortcut "X") also leads, unless an app or
     * contact is named exactly the query too.
     */
    val rows: List<SearchRow> by lazy {
        // The provider orders by its own relevance (e.g. frequency), not by name: "Ann" must beat "Anne".
        val needle = normalizeForSearch(query)
        val found = (contacts as? ContactLookup.Found)?.contacts.orEmpty()
            .sortedBy { contactTier(it.name, needle) }.take(UniversalSearch.MAX_CONTACTS)
        val learned = learnedTop
        val person = when (learned) {
            null -> found.firstOrNull()?.takeIf { personFirst(it, needle) }
            is HistoryTarget.Contact -> found.firstOrNull { it.lookupKey == learned.lookupKey }
            else -> null
        }
        // A learned shortcut or folder brings its whole section to the top (itself first), so the
        // section keeps a single heading.
        val shortcutRows = (shortcuts as? ShortcutLookup.Found)?.shortcuts.orEmpty().map(SearchRow::Shortcut)
            .let { rows -> if (learned is HistoryTarget.Shortcut) rows.sortedByDescending { learned.matches(it.shortcut) } else rows }
        val folderRows = folders.map(SearchRow::Folder)
            .let { rows -> if (learned is HistoryTarget.Folder) rows.sortedByDescending { it.folder.itemId == learned.itemId } else rows }
        val leading: List<SearchRow> = when {
            learned is HistoryTarget.Shortcut && shortcutRows.any { learned.matches(it.shortcut) } -> shortcutRows
            learned == null && person == null && exactShortcutFirst(needle) -> shortcutRows
            learned is HistoryTarget.Folder && folderRows.any { it.folder.itemId == learned.itemId } -> folderRows
            else -> emptyList()
        }
        buildList {
            addAll(leading)
            person?.let { add(SearchRow.Person(it)) }
            calculation?.let { add(SearchRow.Calculation(query.trim(), it)) }
            microResults.forEach { add(SearchRow.Micro(it)) }
            apps.forEach { add(SearchRow.App(it, suggested = appMatchRanks[it.key]?.let { rank -> rank >= LabelMatcher.FUZZY } == true)) }
            if (leading !== folderRows) addAll(folderRows)
            if (leading !== shortcutRows) addAll(shortcutRows)
            when (contacts) {
                is ContactLookup.Found -> found.forEach { if (it != person) add(SearchRow.Contact(it, suggested = contactTier(it.name, needle) in (LabelMatcher.FUZZY + 1) until ContactNameMatcher.PROVIDER_ONLY)) }
                ContactLookup.PermissionMissing -> add(SearchRow.ContactsPermissionNeeded)
                ContactLookup.Unavailable, null -> Unit
            }
            suggestedApps.forEach { add(SearchRow.SuggestedApp(it)) }
            suggestedContacts.forEach { add(SearchRow.SuggestedContact(it)) }
            recent.forEach { add(SearchRow.Recent(it)) }
            webEngine?.let { add(SearchRow.WebSearch(it, query.trim())) }
            if (playStore) add(SearchRow.PlayStore(query.trim()))
        }
    }

    /**
     * True when [contact]'s name matches the query better than the best app label, so a search for a
     * person shows that person first. A whole word of the name ("cal" for "Cal Smith") counts as an
     * exact match; on a tie the app wins, keeping launch-by-name unchanged. Calculator expressions
     * never promote a contact.
     */
    /**
     * True when the first shortcut is named exactly [needle] and no app is, so "x" opens the "X"
     * site shortcut rather than an app that only has an "X" in its name. Shortcuts are already
     * ordered best match first.
     */
    private fun exactShortcutFirst(needle: String): Boolean {
        if (calculation is CalculationResult.Value || needle.isEmpty()) return false
        val shortcut = (shortcuts as? ShortcutLookup.Found)?.shortcuts?.firstOrNull() ?: return false
        if (DefaultAppSearcher.rank(normalizeForSearch(shortcut.label), needle) != DefaultAppSearcher.EXACT) return false
        return apps.none { appMatchRanks[it.key] == DefaultAppSearcher.EXACT }
    }

    private fun personFirst(contact: ContactHit, needle: String): Boolean {
        if (calculation is CalculationResult.Value || needle.isEmpty()) return false
        val tier = contactTier(contact.name, needle)
        if (tier >= LabelMatcher.FUZZY) return false
        val contactRank = when (tier) {
            TIER_FULL_NAME, TIER_WHOLE_WORD -> DefaultAppSearcher.EXACT
            else -> tier - 1 // the remaining tiers mirror DefaultAppSearcher's PREFIX..SUBSTRING, then no name match
        }
        val appRank = apps.firstOrNull()?.let { appMatchRanks[it.key] ?: LabelMatcher.prepare(needle).rank(it.label) } ?: return true
        return contactRank < appRank
    }

    companion object {
        val EMPTY = SearchResults(query = "")

        private const val TIER_FULL_NAME = 0
        private const val TIER_WHOLE_WORD = 1

        /**
         * How well a contact name matches [needle] (normalized), lower is better: full name, a whole
         * word ("Amma" in "Marykind Amma Friend"), then label prefix, word prefix, substring, and
         * last a contact the provider matched on another field (nickname, email, organisation).
         */
        internal fun contactTier(name: String, needle: String): Int {
            if (needle.isEmpty()) return 0
            return ContactNameMatcher.rank(name, LabelMatcher.prepare(needle)) ?: ContactNameMatcher.PROVIDER_ONLY
        }
    }
}

/**
 * Offline universal search (apps, contacts and shortcuts, without cloud integrations or usage
 * learning). Hidden apps and their shortcuts are excluded; contacts and shortcuts are only read when
 * enabled in [SearchSettings]. Callers cancel stale queries (e.g. `mapLatest`); this function is
 * cancellable and keeps no state, and never logs the query.
 */
class UniversalSearch(
    private val appSearcher: AppSearcher,
    private val shortcutSource: ShortcutSource,
    private val contactSource: ContactSource,
) {
    suspend fun search(
        query: String,
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        settings: SearchSettings,
        /** User-chosen app names (`AppCustomization.customLabel`); apps are found by these too. */
        customLabels: Map<AppKey, String> = emptyMap(),
        folderItems: List<WorkspaceItem> = emptyList(),
        /** Shortcut use from search history; more-used shortcuts rank first among equal matches. */
        shortcutCounts: Map<ShortcutKey, Int> = emptyMap(),
        /** False skips "did you mean" (a fuller pass with contacts follows and decides). */
        suggest: Boolean = true,
        /**
         * Normalized labels of this app-list snapshot. When given, apps are ranked from it (same
         * order as [DefaultAppSearcher]) instead of normalizing every label for every query.
         * When non-null, ranking goes through [PreparedLabels.searcher] and the [AppSearcher] this
         * search was constructed with is not used for apps.
         */
        labels: PreparedLabels? = null,
    ): SearchResults = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope SearchResults(query = query)
        val visible = if (hidden.isEmpty()) apps else apps.filterNot { it.key in hidden }

        val shortcuts = if (settings.shortcutsEnabled) async { provider(ShortcutLookup.Unavailable) { shortcutSource.shortcuts(visible) } } else null
        val contacts = if (settings.contactsEnabled) async { provider(ContactLookup.Unavailable) { contactSource.contacts(trimmed, MAX_CONTACT_CANDIDATES) } } else null

        val calculation = if (settings.calculatorEnabled) {
            Calculator.evaluate(trimmed).takeUnless { it is CalculationResult.NotAnExpression }
        } else {
            null
        }
        val matchedApps = CustomLabelSearch.search(labels?.searcher ?: appSearcher, visible, trimmed, customLabels).take(MAX_APPS)
        val prepared = LabelMatcher.prepare(trimmed)
        val normalizeLabel: (String) -> String = labels?.let { it::normalized } ?: ::normalizeForSearch
        val appRanks = matchedApps.mapNotNull { hit ->
            val original = visible.firstOrNull { it.key == hit.key } ?: return@mapNotNull null
            listOfNotNull(
                prepared.rankNormalized(normalizeLabel(original.label)),
                customLabels[hit.key]?.let { prepared.rankNormalized(normalizeLabel(it)) },
            )
                .minOrNull()?.let { hit.key to it }
        }.toMap()
        val results = SearchResults(
            query = query,
            calculation = calculation,
            microResults = if (settings.microResultsEnabled) MicroResults.recognize(trimmed) else emptyList(),
            apps = matchedApps,
            appMatchRanks = appRanks,
            folders = if (settings.foldersEnabled) FolderSearch.search(folderItems, apps, hidden, trimmed) else emptyList(),
            shortcuts = shortcuts?.await()?.let { filterShortcuts(it, trimmed, visible, shortcutCounts) },
            contacts = contacts?.await(),
            webEngine = settings.webEngine.takeIf { settings.showWebSuggestionRow },
            playStore = settings.playStoreRow,
        )
        if (!suggest || !results.foundNothing() || TypoSuggestions.maxTypos(normalizeForSearch(trimmed).length) == 0) {
            return@coroutineScope results
        }
        // Zero-results fallback: only now look for near misses, so a real match is never displaced.
        val suggestedContacts = if (results.contacts is ContactLookup.Found) {
            provider(ContactLookup.Unavailable) { contactSource.suggestions(trimmed, TypoSuggestions.MAX_CONTACTS) }
        } else {
            null
        }
        results.copy(
            suggestedApps = TypoSuggestions.apps(visible, trimmed, customLabels),
            suggestedContacts = (suggestedContacts as? ContactLookup.Found)?.contacts.orEmpty().take(TypoSuggestions.MAX_CONTACTS),
        )
    }

    /**
     * Only the optional providers' part of [search]: shortcuts and contacts for [query], everything
     * else empty. For a caller that already has the offline part from [search] (providers off) and
     * merges these two fields in, so no app, folder, calculator or typo work is repeated and no
     * "did you mean" lookup is made.
     */
    suspend fun providers(
        query: String,
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        settings: SearchSettings,
        shortcutCounts: Map<ShortcutKey, Int> = emptyMap(),
    ): SearchResults = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope SearchResults(query = query)
        val visible = if (hidden.isEmpty()) apps else apps.filterNot { it.key in hidden }
        val shortcuts = if (settings.shortcutsEnabled) async { provider(ShortcutLookup.Unavailable) { shortcutSource.shortcuts(visible) } } else null
        val contacts = if (settings.contactsEnabled) async { provider(ContactLookup.Unavailable) { contactSource.contacts(trimmed, MAX_CONTACT_CANDIDATES) } } else null
        SearchResults(
            query = query,
            shortcuts = shortcuts?.await()?.let { filterShortcuts(it, trimmed, visible, shortcutCounts) },
            contacts = contacts?.await(),
        )
    }

    /**
     * Blank-query suggestions from [ranked] history (best first): apps for the icon strip and other
     * targets for "Recent", each resolved live. Hidden, locked, uninstalled and other-profile apps,
     * shortcuts of such apps, missing contacts, invisible folders and turned-off sources are left
     * out; nothing here deletes history. Shortcuts and contacts are only read when [settings] enable
     * them and [readProviders] holds.
     */
    suspend fun history(
        ranked: List<HistoryEntry>,
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        locked: (AppKey) -> Boolean,
        settings: SearchSettings,
        customLabels: Map<AppKey, String> = emptyMap(),
        folderItems: List<WorkspaceItem> = emptyList(),
        readProviders: Boolean = true,
    ): SearchResults = coroutineScope {
        val visible = apps.filter { it.profileType == ProfileType.PERSONAL && it.key !in hidden && !locked(it.key) }
        val byKey = visible.associateBy { it.key }
        val targets = ranked.map { it.target }
        val suggestedApps = targets.filterIsInstance<HistoryTarget.App>().mapNotNull { byKey[it.key] }
            .map { app -> customLabels[app.key]?.let { app.copy(label = it) } ?: app }
            .take(MAX_HISTORY_APPS)
        val others = targets.filterNot { it is HistoryTarget.App }

        val shortcutTargets = others.filterIsInstance<HistoryTarget.Shortcut>()
        val shortcuts = if (readProviders && settings.shortcutsEnabled && shortcutTargets.isNotEmpty()) {
            async { provider(ShortcutLookup.Unavailable) { shortcutSource.shortcuts(visible) } }
        } else null
        val contactKeys = others.filterIsInstance<HistoryTarget.Contact>().map { it.lookupKey }.take(MAX_HISTORY_RECENT)
        val contacts = if (readProviders && settings.contactsEnabled && contactKeys.isNotEmpty()) {
            async { provider(ContactLookup.Unavailable) { contactSource.byLookupKeys(contactKeys) } }
        } else null
        val folders = if (settings.foldersEnabled) FolderSearch.visibleFolders(folderItems, apps, hidden).associateBy { it.itemId } else emptyMap()

        val owners = visible.mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
        val liveShortcuts = (shortcuts?.await() as? ShortcutLookup.Found)?.shortcuts.orEmpty()
            .filter { (it.packageName to it.userSerial) in owners && (it.appKey == null || it.appKey in byKey) }
            .associateBy { HistoryTarget.Shortcut(it.packageName, it.shortcutId, it.userSerial) }
        val liveContacts = (contacts?.await() as? ContactLookup.Found)?.contacts.orEmpty().associateBy { it.lookupKey }

        val recent = others.mapNotNull { target ->
            when (target) {
                is HistoryTarget.WebQuery -> RecentItem.Query(target.text)
                is HistoryTarget.Folder -> folders[target.itemId]?.let(RecentItem::Folder)
                is HistoryTarget.Shortcut -> liveShortcuts[target]?.let(RecentItem::Shortcut)
                is HistoryTarget.Contact -> liveContacts[target.lookupKey]?.let(RecentItem::Contact)
                is HistoryTarget.App -> null
            }
        }.take(MAX_HISTORY_RECENT)
        SearchResults(query = "", apps = suggestedApps, recent = recent)
    }

    private fun SearchResults.foundNothing(): Boolean =
        calculation == null && microResults.isEmpty() && apps.isEmpty() && folders.isEmpty() &&
            (shortcuts as? ShortcutLookup.Found)?.shortcuts.isNullOrEmpty() &&
            (contacts as? ContactLookup.Found)?.contacts.isNullOrEmpty()

    /** Emits offline matches immediately; optional providers enrich them within a bounded deadline. */
    fun results(
        query: String,
        apps: List<LauncherApp>,
        hidden: Set<AppKey>,
        settings: SearchSettings,
        customLabels: Map<AppKey, String> = emptyMap(),
        folderItems: List<WorkspaceItem> = emptyList(),
        shortcutCounts: Map<ShortcutKey, Int> = emptyMap(),
    ): Flow<SearchResults> = flow {
        val enriched = query.isNotBlank() && (settings.shortcutsEnabled || settings.contactsEnabled)
        // Suggestions wait for the enriched pass, so they never flash before a contact match arrives.
        val local = search(query, apps, hidden, settings.copy(shortcutsEnabled = false, contactsEnabled = false), customLabels, folderItems, suggest = !enriched)
        emit(local)
        if (enriched) {
            emit(search(query, apps, hidden, settings, customLabels, folderItems, shortcutCounts))
        }
    }

    private suspend fun <T> provider(unavailable: T, read: suspend () -> T): T = try {
        withTimeoutOrNull(PROVIDER_TIMEOUT_MILLIS) { read() } ?: unavailable
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { unavailable }

    companion object {
        const val PROVIDER_TIMEOUT_MILLIS = 3_000L
        const val MAX_APPS = 12
        const val MAX_SHORTCUTS = 6
        const val MAX_CONTACTS = 5
        const val MAX_HISTORY_APPS = 5
        const val MAX_HISTORY_RECENT = 6

        /** Read more than are shown: the provider's own order is not by name, so rank before trimming. */
        const val MAX_CONTACT_CANDIDATES = 20

        /**
         * Keeps shortcuts of a visible app (same package and profile) whose label matches [query],
         * ranked like app labels, then by [counts] (more launches first). Shortcuts of hidden apps or
         * unlisted profiles never surface, even if a source returns them.
         */
        fun filterShortcuts(
            lookup: ShortcutLookup,
            query: String,
            visible: List<LauncherApp>,
            counts: Map<ShortcutKey, Int> = emptyMap(),
        ): ShortcutLookup {
            if (lookup !is ShortcutLookup.Found) return lookup
            val needle = normalizeForSearch(query)
            if (needle.isEmpty()) return ShortcutLookup.Found(emptyList())
            val matches = visibleShortcuts(lookup.shortcuts, visible)
                .mapIndexedNotNull { index, hit ->
                    DefaultAppSearcher.rank(normalizeForSearch(hit.label), needle)?.let { Triple(it, index, hit) }
                }
                .sortedWith(compareBy({ it.first }, { -(counts[it.third.key] ?: 0) }, { it.second }))
                .map { it.third }
                .take(MAX_SHORTCUTS)
            return ShortcutLookup.Found(matches)
        }

        /** Drops shortcuts whose owning app is no longer in [visible]; keeps the order. */
        fun visibleShortcuts(lookup: ShortcutLookup, visible: List<LauncherApp>): ShortcutLookup =
            if (lookup is ShortcutLookup.Found) ShortcutLookup.Found(visibleShortcuts(lookup.shortcuts, visible)) else lookup

        private fun visibleShortcuts(shortcuts: List<ShortcutHit>, visible: List<LauncherApp>): List<ShortcutHit> {
            val keys = visible.mapTo(HashSet()) { it.key }
            val owners = visible.mapTo(HashSet()) { it.key.packageName to it.key.userSerial }
            return shortcuts.filter { hit -> hit.appKey?.let { it in keys } ?: ((hit.packageName to hit.userSerial) in owners) }
        }
    }
}
