package io.github.aj4abinjacob.chikku.search

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProvider
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ProviderEntryPoint
import io.github.aj4abinjacob.chikku.platform.AndroidContactEntryPointProbe
import io.github.aj4abinjacob.chikku.platform.ContactEntryPointProbe
import io.github.aj4abinjacob.chikku.core.model.search.ContactLookup
import io.github.aj4abinjacob.chikku.core.model.search.ContactSource
import io.github.aj4abinjacob.chikku.core.model.search.ContactNameMatcher
import io.github.aj4abinjacob.chikku.core.model.search.LabelMatcher
import io.github.aj4abinjacob.chikku.core.model.search.UniversalSearch
import io.github.aj4abinjacob.chikku.core.model.search.TypoSuggestions
import io.github.aj4abinjacob.chikku.core.model.search.normalizeForSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext

/**
 * Personal-profile contacts matching a query, read from ContactsContract off the main thread.
 *
 * Only called when the user turned contact search on (see `UniversalSearch`). Without
 * READ_CONTACTS it returns [ContactLookup.PermissionMissing] and touches nothing. Results are held
 * only for the current query; names and queries are never stored or logged. A fresh, streamed
 * name scan supplements the provider filter so misspellings are not discarded by Android. Only
 * the bounded best candidates survive the scan; there is no retained address-book index. A stale query is
 * cancelled through a [CancellationSignal] so the provider stops work early.
 */
class ContactsSearchSource(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val providers: List<CommunicationProvider> = CommunicationProviders.KNOWN,
    private val probe: ContactEntryPointProbe = AndroidContactEntryPointProbe(context),
) : ContactSource {
    private val appContext = context.applicationContext

    /**
     * The provider's own filter matches (which also cover phone numbers and e-mail) merged with a
     * scan of every name, so a misspelling the provider discards is still found. Best matches first.
     */
    override suspend fun contacts(query: String, limit: Int): ContactLookup {
        val trimmed = query.trim()
        return read(trimmed, limit) { resolver, signal, ensureActive ->
            val prepared = LabelMatcher.prepare(trimmed)
            val capacity = minOf(limit, UniversalSearch.MAX_CONTACT_CANDIDATES)
            val best = ArrayList<RankedContact>(capacity)
            fun offer(hit: ContactHit, providerMatch: Boolean) {
                val rank = ContactNameMatcher.rank(hit.name, prepared)
                    ?: if (providerMatch) ContactNameMatcher.PROVIDER_ONLY else return
                if (best.any { it.hit.id == hit.id }) return
                val insertion = best.indexOfFirst { it.rank > rank }.let { if (it < 0) best.size else it }
                if (insertion < capacity) {
                    best.add(insertion, RankedContact(hit, rank))
                    if (best.size > capacity) best.removeAt(best.lastIndex)
                }
            }
            // Name scan: rank on the name alone, and only read the remaining columns (lookup key,
            // photo) and build a hit for a row that actually enters the bounded candidate list.
            fun offerScanned(c: android.database.Cursor) {
                val id = c.getLong(0)
                if (Contacts.isEnterpriseContactId(id)) return
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: return
                val rank = ContactNameMatcher.rank(name, prepared) ?: return
                if (best.any { it.hit.id == id }) return
                val insertion = best.indexOfFirst { it.rank > rank }.let { if (it < 0) best.size else it }
                if (insertion >= capacity) return
                val key = c.getString(1) ?: return
                best.add(insertion, RankedContact(ContactHit(id, key, name, photoUri = c.getString(3)), rank))
                if (best.size > capacity) best.removeAt(best.lastIndex)
            }
            val filterUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(trimmed))
            val cursor = resolver.query(filterUri, PROJECTION, null, null, null, signal) ?: return@read null
            cursor.use { c ->
                var validContacts = 0
                while (validContacts < capacity && c.moveToNext()) {
                    ensureActive()
                    // Invalid/enterprise rows must not consume the provider-match
                    // budget: later phone/email matches cannot be recovered by name.
                    readContact(c)?.let {
                        validContacts++
                        offer(it, providerMatch = true)
                    }
                }
            }
            try {
                // CONTENT_URI is the calling user's contacts. Never use an ENTERPRISE
                // URI, remote directory or cross-profile query for this personal source.
                resolver.query(Contacts.CONTENT_URI, PROJECTION, null, null, Contacts._ID, signal)?.use { c ->
                    while (c.moveToNext()) {
                        ensureActive()
                        offerScanned(c)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (cancelled: OperationCanceledException) { throw cancelled }
            catch (revoked: SecurityException) { throw revoked }
            catch (_: RuntimeException) { /* Keep the provider's existing matches. */ }
            best.map { it.hit }
        }
    }

    /**
     * Scans every contact name and keeps the [limit] closest to [query] by typo distance. Only asked
     * when the normal search found nothing; names live in memory for this query only.
     */
    override suspend fun suggestions(query: String, limit: Int): ContactLookup {
        val trimmed = query.trim()
        val needle = normalizeForSearch(trimmed)
        if (TypoSuggestions.maxTypos(needle.length) == 0) return ContactLookup.Found(emptyList())
        return read(trimmed, limit) { resolver, signal, ensureActive ->
            val cursor = resolver.query(Contacts.CONTENT_URI, PROJECTION, null, null, Contacts._ID, signal) ?: return@read null
            val scored = ArrayList<Pair<Int, ContactHit>>()
            cursor.use { c ->
                while (c.moveToNext()) {
                    ensureActive()
                    val hit = readContact(c) ?: continue
                    val distance = TypoSuggestions.distance(normalizeForSearch(hit.name), needle) ?: continue
                    scored += distance to hit
                }
            }
            scored.sortedBy { it.first }.map { it.second }
        }
    }

    /** Contacts remembered in search history, looked up by key so merges and renames still resolve. */
    override suspend fun byLookupKeys(lookupKeys: List<String>): ContactLookup {
        val keys = lookupKeys.filter { it.isNotBlank() }.distinct().take(MAX_LOOKUP_KEYS)
        val found = HashMap<String, ContactHit>()
        for (key in keys) {
            val lookup = read(key, 1) { resolver, signal, _ ->
                // Lookup keys are already URI-encoded by the provider (see Contacts.getLookupUri).
                val uri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI, key)
                val cursor = try {
                    resolver.query(uri, PROJECTION, null, null, null, signal)
                } catch (_: IllegalArgumentException) {
                    return@read emptyList() // a key the provider no longer understands is a deleted contact
                } ?: return@read null
                cursor.use { c -> if (c.moveToFirst()) listOfNotNull(readContact(c)) else emptyList() }
            }
            when (lookup) {
                // History keeps the remembered key even when the provider now reports a newer one.
                is ContactLookup.Found -> lookup.contacts.firstOrNull()?.let { found[key] = it.copy(lookupKey = key) }
                else -> return lookup // permission revoked or provider failing: no partial list
            }
        }
        return ContactLookup.Found(keys.mapNotNull { found[it] })
    }

    /**
     * Runs [find] off the main thread, then adds call/message actions to the first [limit] hits it
     * returns (best first). [find] returns null when the provider gave no cursor. A stale query
     * cancels the provider through the [CancellationSignal] handed to [find].
     */
    private suspend fun read(
        trimmed: String,
        limit: Int,
        find: (ContentResolver, CancellationSignal, ensureActive: () -> Unit) -> List<ContactHit>?,
    ): ContactLookup {
        val permitted = try { hasPermission() } catch (_: RuntimeException) { return ContactLookup.Unavailable }
        if (!permitted) return ContactLookup.PermissionMissing
        if (trimmed.isEmpty() || limit <= 0) return ContactLookup.Found(emptyList())
        val signal = CancellationSignal()
        return try {
            withContext(ioDispatcher) {
                val queryContext = coroutineContext
                suspendCancellableCoroutine { continuation ->
                    // Register on cancellation, not job completion: a blocked provider must receive
                    // the signal before the IO coroutine has finished unwinding.
                    continuation.invokeOnCancellation { runCatching { signal.cancel() } }
                    val found = find(appContext.contentResolver, signal) { queryContext.ensureActive() }
                    if (found == null) {
                        continuation.resume(ContactLookup.Unavailable)
                    } else {
                        queryContext.ensureActive()
                        if (!hasPermission()) throw SecurityException()
                        val enriched = withActions(found.take(minOf(limit, UniversalSearch.MAX_CONTACTS)), signal)
                        queryContext.ensureActive()
                        if (!hasPermission()) throw SecurityException()
                        continuation.resume(ContactLookup.Found(enriched))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e // a newer query superseded this one
        } catch (_: OperationCanceledException) {
            ContactLookup.Found(emptyList())
        } catch (_: SecurityException) {
            ContactLookup.PermissionMissing // revoked between the check and the query
        } catch (_: RuntimeException) {
            ContactLookup.Unavailable
        }
    }

    /**
     * Adds each hit's primary (else first) phone number and the synced rows of communication
     * providers whose entry points this device can start for Chikku, in one provider query. A failure
     * here keeps the name-only hits; the person card then offers only "open contact".
     */
    private fun withActions(hits: List<ContactHit>, signal: CancellationSignal): List<ContactHit> {
        if (hits.isEmpty()) return hits
        // Probed per lookup: an app update can add or remove an entry point or its permission guard.
        val entryPoints = HashMap<String, Pair<CommunicationProvider, ProviderEntryPoint>>()
        providers.forEach { provider ->
            provider.entryPoints.filter { probe.supports(provider, it) }.forEach { entryPoints[it.mimeType] = provider to it }
        }
        val phones = HashMap<Long, String>()
        val channels = HashMap<Long, MutableList<ContactChannelAction>>()
        try {
            val ids = hits.joinToString(",") { "?" }
            val mimeTypes = listOf(Phone.CONTENT_ITEM_TYPE) + entryPoints.keys
            appContext.contentResolver.query(
                Data.CONTENT_URI,
                DATA_PROJECTION,
                "${Data.CONTACT_ID} IN ($ids) AND ${Data.MIMETYPE} IN (${mimeTypes.joinToString(",") { "?" }})",
                (hits.map { it.id.toString() } + mimeTypes).toTypedArray(),
                "${Data.IS_SUPER_PRIMARY} DESC, ${Data.IS_PRIMARY} DESC, ${Data._ID}",
                signal,
            )?.use { c ->
                while (c.moveToNext()) {
                    val contactId = c.getLong(1)
                    val mimeType = c.getString(2) ?: continue
                    if (mimeType == Phone.CONTENT_ITEM_TYPE) {
                        c.getString(3)?.takeIf { it.isNotBlank() }?.let { phones.putIfAbsent(contactId, it) }
                    } else {
                        val (provider, entryPoint) = entryPoints[mimeType] ?: continue
                        channels.getOrPut(contactId) { ArrayList() }
                            .add(ContactChannelAction(provider.id, entryPoint.type, c.getLong(0), mimeType))
                    }
                }
            }
        } catch (e: OperationCanceledException) {
            throw e
        } catch (revoked: SecurityException) {
            throw revoked
        } catch (_: RuntimeException) {
            return hits
        }
        return hits.map { hit ->
            hit.copy(
                phoneNumber = phones[hit.id],
                // One row per provider and action, in the provider's primary-first order.
                channelActions = channels[hit.id].orEmpty().distinctBy { it.providerId to it.type },
            )
        }
    }

    private data class RankedContact(val hit: ContactHit, val rank: Int)

    private fun readContact(cursor: android.database.Cursor): ContactHit? {
        val id = cursor.getLong(0)
        if (Contacts.isEnterpriseContactId(id)) return null
        val name = cursor.getString(2)?.takeIf { it.isNotBlank() } ?: return null
        val key = cursor.getString(1) ?: return null
        return ContactHit(id, key, name, photoUri = cursor.getString(3))
    }

    private fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MAX_LOOKUP_KEYS = 8
        val PROJECTION = arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_THUMBNAIL_URI)
        val DATA_PROJECTION = arrayOf(Data._ID, Data.CONTACT_ID, Data.MIMETYPE, Phone.NUMBER)
    }
}
