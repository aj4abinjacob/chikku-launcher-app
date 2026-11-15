package io.github.aj4abinjacob.chikku.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

const val SOURCE_DOWNLOAD_URL = "https://www.abinjacob.in/chikku/source/"

/** Explicit copy of a public URL. No clipboard read, browser handoff or network request. */
fun copySourceDownloadLink(context: Context, label: String): Boolean = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (clipboard == null) false else {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, SOURCE_DOWNLOAD_URL))
        true
    }
} catch (_: RuntimeException) {
    false
}
