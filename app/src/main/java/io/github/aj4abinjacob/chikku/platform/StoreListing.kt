package io.github.aj4abinjacob.chikku.platform

import android.app.Activity
import android.content.Intent
import android.net.Uri

/** Public listing handoff. Authentication/selection is handled without dropping the exact URI. */
fun Activity.openStoreListing(packageName: String, starter: ExternalIntentStarter = ExternalIntentStarter.DENY): Boolean =
    starter.start(storeListingIntents(packageName))

internal fun storeListingIntents(packageName: String): List<Intent> {
    val uri = Uri.Builder().scheme("https").authority("play.google.com")
        .path("/store/apps/details").appendQueryParameter("id", packageName).build()
    return listOf(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending"), Intent(Intent.ACTION_VIEW, uri))
        .map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
