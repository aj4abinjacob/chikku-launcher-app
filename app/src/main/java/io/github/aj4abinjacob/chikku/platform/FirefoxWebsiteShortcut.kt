package io.github.aj4abinjacob.chikku.platform

import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * Older Firefox website shortcuts and PWA shortcuts use the page/start URL as their ID. Android
 * owns the pinned intent and can discard it when this launcher is uninstalled. Recover that
 * Firefox convention as an ordinary page in the same browser, not a recreated PWA. Newer basic
 * shortcuts use UUIDs and cannot be recovered this way. See docs/RESTORED_SHORTCUTS.md for sources.
 * Never interpret another publisher's opaque ID, or a non-web URI, as an executable intent.
 */
internal fun firefoxWebsiteIntent(packageName: String, shortcutId: String): Intent? {
    if (packageName != "org.mozilla.firefox") return null
    val uri = try { URI(shortcutId).parseServerAuthority() } catch (_: Exception) { return null }
    if ((!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) ||
        uri.isOpaque || uri.host.isNullOrBlank()
    ) return null
    return Intent(Intent.ACTION_VIEW, Uri.parse(shortcutId).normalizeScheme())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
