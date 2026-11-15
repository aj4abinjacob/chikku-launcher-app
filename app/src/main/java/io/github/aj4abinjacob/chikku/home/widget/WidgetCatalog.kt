package io.github.aj4abinjacob.chikku.home.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.platform.ProfileAvailability
import java.text.Collator

/** One installable widget, with its default span on the current grid. */
data class WidgetProviderEntry(
    val key: ComponentKey,
    val label: String,
    val spanX: Int,
    val spanY: Int,
)

/** Providers of one app in one profile, as shown under one picker header. */
data class WidgetGroup(
    val packageName: String,
    val userSerial: Long,
    val appLabel: String,
    val isOtherProfile: Boolean,
    val entries: List<WidgetProviderEntry>,
)

/**
 * Groups providers by app and profile, sorting groups by app label (then profile) and entries by
 * label, with the locale's collation. Pure, for testing.
 */
fun groupWidgetProviders(
    entries: List<WidgetProviderEntry>,
    appLabel: (packageName: String, userSerial: Long) -> String,
    mainSerial: Long,
    collator: Comparator<in String> = Collator.getInstance(),
): List<WidgetGroup> =
    entries.groupBy { it.key.packageName to it.key.userSerial }
        .map { (k, list) ->
            WidgetGroup(
                packageName = k.first,
                userSerial = k.second,
                appLabel = appLabel(k.first, k.second),
                isOtherProfile = k.second != mainSerial,
                entries = list.sortedWith(compareBy(collator) { it.label }),
            )
        }
        .sortedWith(compareBy<WidgetGroup, String>(collator) { it.appLabel }.thenBy { it.userSerial }.thenBy { it.packageName })

/**
 * Reads installed widget providers for the given profiles. Does package-manager and binder work:
 * call off the main thread. Profiles not in [allowedSerials] (locked, paused, or not listed by
 * the app list) are skipped so their providers never appear.
 */
class WidgetCatalog(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context.applicationContext)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val profiles = ProfileAvailability(context.applicationContext)

    fun mainSerial(): Long = userManager?.getSerialNumberForUser(Process.myUserHandle()) ?: 0L

    /** Provider infos keyed by component, so the picker can size and bind without re-querying. */
    fun load(allowedSerials: Set<Long>, sizeOf: (AppWidgetProviderInfo) -> Pair<Int, Int>): Pair<List<WidgetGroup>, Map<ComponentKey, AppWidgetProviderInfo>> {
        val um = userManager ?: return emptyList<WidgetGroup>() to emptyMap()
        val infos = HashMap<ComponentKey, AppWidgetProviderInfo>()
        val entries = mutableListOf<WidgetProviderEntry>()
        val users = try { um.userProfiles } catch (_: RuntimeException) { emptyList() }
        for (user in users) {
            val serial = try { um.getSerialNumberForUser(user) } catch (_: RuntimeException) { continue }
            if (serial !in allowedSerials) continue
            if (!profiles.isAvailable(user)) continue
            val providers = try {
                manager?.getInstalledProvidersForProfile(user).orEmpty()
            } catch (_: RuntimeException) {
                emptyList()
            }
            for (info in providers) {
                if (!profiles.isAvailable(user)) break
                val key = ComponentKey(info.provider.packageName, info.provider.className, serial)
                val label = try {
                    info.loadLabel(context.packageManager)
                } catch (_: RuntimeException) {
                    null
                } ?: info.provider.shortClassName
                val (sx, sy) = sizeOf(info)
                infos[key] = info
                entries += WidgetProviderEntry(key, label, sx, sy)
            }
        }
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val groups = groupWidgetProviders(entries, { pkg, serial ->
            try {
                val user = um.getUserForSerialNumber(serial)
                val app = if (user != null) launcherApps?.getApplicationInfo(pkg, 0, user) else null
                app?.loadLabel(context.packageManager)?.toString() ?: pkg
            } catch (_: PackageManager.NameNotFoundException) {
                pkg
            } catch (_: RuntimeException) {
                pkg
            }
        }, mainSerial())
        val visible = groups.filter { profiles.isAvailable(it.userSerial) }
        val serials = visible.map { it.userSerial }.toSet()
        return visible to infos.filterKeys { it.userSerial in serials }
    }

    /** The provider info for [key] if it is still installed and its profile is allowed. */
    fun find(key: ComponentKey): AppWidgetProviderInfo? {
        val um = userManager ?: return null
        return try {
            val user = um.getUserForSerialNumber(key.userSerial) ?: return null
            if (!profiles.isAvailable(user)) return null
            manager?.getInstalledProvidersForProfile(user)?.firstOrNull {
                it.provider.packageName == key.packageName && it.provider.className == key.className
            }?.takeIf { profiles.isAvailable(user) }
        } catch (_: RuntimeException) {
            null
        }
    }
}
