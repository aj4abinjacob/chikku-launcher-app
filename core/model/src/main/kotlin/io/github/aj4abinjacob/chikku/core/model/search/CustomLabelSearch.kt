package io.github.aj4abinjacob.chikku.core.model.search

import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppSearcher
import io.github.aj4abinjacob.chikku.core.model.LauncherApp

/**
 * App search that also finds apps by the user's custom label (`AppCustomization.customLabel`).
 * Uses the best match across the custom and original label; each app appears
 * once. Returned apps carry the custom label so results show the name the user chose.
 */
object CustomLabelSearch {

    fun customLabels(customizations: Map<AppKey, AppCustomization>): Map<AppKey, String> =
        customizations.values.mapNotNull { c -> c.customLabel?.takeIf { it.isNotBlank() }?.let { c.key to it } }.toMap()

    fun search(searcher: AppSearcher, apps: List<LauncherApp>, query: String, customLabels: Map<AppKey, String>): List<LauncherApp> {
        if (customLabels.isEmpty()) return searcher.search(apps, query)
        // Search both names in one ranking pass; display names never erase original provenance.
        val variants = apps.flatMap { app -> listOfNotNull(app, customLabels[app.key]?.let { app.copy(label = it) }) }
        val byKey = apps.associateBy { it.key }
        val seen = HashSet<AppKey>()
        return searcher.search(variants, query).mapNotNull { hit ->
            if (seen.add(hit.key)) byKey.getValue(hit.key).withLabel(customLabels) else null
        }
    }

    private fun LauncherApp.withLabel(customLabels: Map<AppKey, String>): LauncherApp =
        customLabels[key]?.let { copy(label = it) } ?: this
}
