package io.github.aj4abinjacob.chikku.platform

import android.content.Context
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsRepository
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import java.text.Collator
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

/**
 * [AppsRepository] backed by LauncherApps.
 *
 * While [apps] is collected, a LauncherApps.Callback and a profile broadcast receiver are
 * registered; bursts of events are coalesced ([debounceMillis]) into one re-enumeration on
 * [ioDispatcher]. Nothing is persisted. [onPackageChanged] is called immediately (on the main
 * thread) for every package event so icon caches can be invalidated; it must be cheap.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LauncherAppsRepository internal constructor(
    private val source: AppsPlatformSource,
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val onPackageChanged: (String) -> Unit,
    private val collatorFactory: () -> Collator,
    private val debounceMillis: Long,
) : AppsRepository {

    constructor(
        context: Context,
        scope: CoroutineScope,
        ioDispatcher: CoroutineDispatcher,
        onPackageChanged: (String) -> Unit = {},
    ) : this(
        AndroidAppsPlatformSource(context),
        scope,
        ioDispatcher,
        onPackageChanged,
        ::displayCollator,
        DEFAULT_DEBOUNCE_MILLIS,
    )

    private val refreshRequests =
        MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits true when the event can change which profiles/apps may be shown (must drop to Loading). */
    private val platformEvents: Flow<Boolean> = callbackFlow {
        val registration = try {
            source.observe { packageName ->
                if (packageName != null) runCatching { onPackageChanged(packageName) }
                // Only plain add/change/remove package events carry a name alone; profile,
                // availability and suspension events arrive as null and must invalidate.
                trySend(packageName == null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Without change events the list can still be loaded and refreshed manually.
            null
        }
        awaitClose { runCatching { registration?.close() } }
    }.buffer(Channel.CONFLATED)

    private var loadedOnce = false

    override val apps: SharedFlow<AppsState> =
        merge(platformEvents, refreshRequests)
            .onStart { emit(true) }
            .transformLatest { invalidate ->
                // Profile availability events invalidate immediately: profile metadata must not
                // survive the debounce window. Ordinary package add/change/remove events keep the
                // last Loaded value until the reload finishes, so surfaces do not flash.
                if (invalidate || !loadedOnce) emit(AppsState.Loading)
                if (loadedOnce) delay(debounceMillis)
                emit(load())
                loadedOnce = true
            }
            .distinctUntilChanged()
            .shareIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS, replayExpirationMillis = 0), replay = 1)

    override fun refresh() {
        refreshRequests.tryEmit(false)
    }

    private suspend fun load(): AppsState = withContext(ioDispatcher) {
        try {
            enumerate()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Generic on purpose: never surface package names or labels in states or logs.
            AppsState.Failed(FAILURE_REASON)
        }
    }

    private fun enumerate(): AppsState.Loaded {
        val listed = mutableSetOf<Long>()
        val apps = source.profiles().flatMap { profile ->
            val activities = try {
                source.activities(profile)
            } catch (e: RuntimeException) {
                // A secondary profile can disappear mid-enumeration; keep the personal apps, and
                // leave this profile out of [listed] so its items are not shown as uninstalled.
                if (profile.type == ProfileType.PERSONAL) throw e else return@flatMap emptyList()
            }
            listed += profile.serial
            activities.mapNotNull { it.toLauncherApp(profile) }
        }
        return AppsState.Loaded(sortForDisplay(apps.distinctBy { it.key }, collatorFactory()), listed)
    }

    internal companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 250L
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val FAILURE_REASON = "Installed apps could not be read"
    }
}

/** Locale-aware collator that ignores case and accents. A new instance per use: not thread-safe. */
internal fun displayCollator(): Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** Maps a platform activity to a [LauncherApp]; invalid components yield null instead of throwing. */
internal fun RawActivity.toLauncherApp(profile: SourceProfile): LauncherApp? {
    if (packageName.isBlank() || className.isBlank()) return null
    val key = AppKey(packageName, className, profile.serial)
    return LauncherApp(key, displayLabel(label, packageName), profile.type)
}

/** Trimmed label, or the package name when the label is null or blank. */
internal fun displayLabel(label: CharSequence?, packageName: String): String {
    val text = label?.toString()?.trim()
    return if (text.isNullOrEmpty()) packageName else text
}

/** Deterministic display order: collated label, then profile, then [AppKey] fields. */
internal fun sortForDisplay(apps: List<LauncherApp>, collator: Collator): List<LauncherApp> {
    val comparator = Comparator<LauncherApp> { a, b -> collator.compare(a.label, b.label) }
        .thenBy { it.profileType.ordinal }
        .thenBy { it.key.packageName }
        .thenBy { it.key.className }
        .thenBy { it.key.userSerial }
    return apps.sortedWith(comparator)
}
