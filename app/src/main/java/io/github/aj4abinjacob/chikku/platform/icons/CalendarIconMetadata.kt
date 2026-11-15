package io.github.aj4abinjacob.chikku.platform.icons

import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle

/** Reads an app-declared daily icon; all artwork stays in that installed app. Call off main. */
internal fun calendarIcon(packageManager: PackageManager, component: ComponentName, day: Int): Drawable? =
    try {
        val activity = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
        // Almost no app declares daily icons: only open its resources when the key is present.
        if (day !in 1..31 || dailyIconsArrayId(activity.metaData, component.packageName) == 0) null
        else calendarIcon(packageManager.getResourcesForApplication(activity.applicationInfo),
            activity.metaData, component.packageName, day)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

internal fun calendarIcon(resources: Resources, metadata: Bundle?, packageName: String, day: Int): Drawable? {
    if (day !in 1..31) return null
    val arrayId = dailyIconsArrayId(metadata, packageName).takeIf { it != 0 } ?: return null
    return try {
        val days = resources.obtainTypedArray(arrayId)
        val iconId = try {
            if (day <= days.length()) days.getResourceId(day - 1, 0) else 0
        } finally {
            days.recycle()
        }
        if (iconId == 0) null else resources.getDrawable(iconId, null)
    } catch (_: RuntimeException) {
        null
    }
}

private fun dailyIconsArrayId(metadata: Bundle?, packageName: String): Int =
    metadata?.getInt("$packageName.dynamic_icons", 0) ?: 0
