package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp

/**
 * Normalized forms ([normalizeForSearch]) of the labels in one app-list snapshot (app labels plus
 * the user's custom labels), computed at most once per distinct label however many keystrokes are
 * searched. Build one per snapshot and drop it when the app list or the custom labels change; it is
 * a plain in-memory value, never persisted, and holds label text only for as long as its owner
 * (the open search session) keeps it. A label that is not in the snapshot is normalized on demand.
 *
 * [searcher] ranks exactly like [DefaultAppSearcher], using this table instead of re-normalizing.
 * [normalize] is a hook so tests can count calls.
 */
class PreparedLabels(
    apps: List<LauncherApp>,
    customLabels: Map<AppKey, String> = emptyMap(),
    private val normalize: (String) -> String = ::normalizeForSearch,
) {
    private val labels: List<String> = apps.map { it.label } + customLabels.values

    private val table: Map<String, String> by lazy {
        HashMap<String, String>(labels.size * 2).also { map -> labels.forEach { map.getOrPut(it) { normalize(it) } } }
    }

    fun normalized(label: String): String = table[label] ?: normalize(label)

    val searcher: AppSearcher = AppSearcher { apps, query -> DefaultAppSearcher.search(apps, query, ::normalized) }
}
