package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.platform.BridgeGoogleLink
import io.github.aj4abinjacob.chikku.platform.DiscoverHealth

/**
 * User-facing explanation of [DiscoverHealth]. [afterFailure] is true when a swipe just failed, so
 * a bridge that reports itself healthy is explained as Google serving another client.
 */
@StringRes
fun DiscoverHealth.messageRes(afterFailure: Boolean): Int = when (this) {
    DiscoverHealth.MissingGoogle -> R.string.home_google_discover_missing_google
    DiscoverHealth.Direct -> R.string.discover_health_direct
    DiscoverHealth.AppOnly -> R.string.discover_health_app_only
    DiscoverHealth.BridgeMissing -> R.string.discover_health_bridge_missing
    is DiscoverHealth.BridgeWrongKey -> R.string.discover_health_wrong_key
    is DiscoverHealth.BridgeDisabled -> R.string.discover_health_disabled
    is DiscoverHealth.BridgeUnresponsive -> R.string.discover_health_unresponsive
    is DiscoverHealth.Bridge -> when {
        status.batteryRestricted -> R.string.discover_health_restricted
        status.google == BridgeGoogleLink.REJECTED -> R.string.discover_health_rejected
        status.google == BridgeGoogleLink.BIND_FAILED -> R.string.discover_health_google_unreachable
        status.google == BridgeGoogleLink.PENDING -> R.string.discover_health_pending
        afterFailure -> R.string.discover_health_not_shown
        else -> R.string.discover_health_ready
    }
}

/** The bridge app whose settings can fix this, if any. */
val DiscoverHealth.bridgePackage: String?
    get() = when (this) {
        is DiscoverHealth.BridgeDisabled -> packageName
        is DiscoverHealth.BridgeUnresponsive -> packageName
        is DiscoverHealth.Bridge -> packageName.takeIf { status.batteryRestricted }
        else -> null
    }

/** App info is where every OEM puts the per-app battery and sleep controls. */
fun Context.openAppDetails(packageName: String): Boolean = try {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
} catch (_: Exception) {
    false
}
