package io.github.aj4abinjacob.chikku.core.model.backup

import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactCardButton
import io.github.aj4abinjacob.chikku.core.model.search.ContactServices
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import kotlinx.coroutines.CancellationException

/**
 * A restored setting that depends on something this device does not have (yet). Restore keeps the
 * setting as it was and tells the user once; it never silently turns a feature off. Counts and
 * kinds only: no package names or app labels.
 */
enum class RestoreAttention {
    /** Discover is on, but the Google app is missing or disabled: the swipe cannot open anything. */
    DISCOVER_NEEDS_GOOGLE_APP,

    /** Discover is on in a normal build without the Chikku Discover bridge: the swipe opens the Google app. */
    DISCOVER_NEEDS_BRIDGE,

    /** Discover is on and a bridge is installed, but it is disabled, signed differently or not responding. */
    DISCOVER_BRIDGE_NOT_WORKING,

    /** The restored icon pack is not installed; system icons are shown until it is. */
    ICON_PACK_MISSING,

    /** The dock background image was picked on another device (or its access was revoked). */
    DOCK_IMAGE_UNREADABLE,

    /**
     * Contact search is on and an app chosen for a contact-card button is not installed.
     * The choice is kept; the service appears once the app is installed and has synced contacts.
     */
    CONTACT_SERVICE_APP_MISSING,
}

/** How far Google Discover can work on this device. */
enum class DiscoverReadiness { READY, NO_GOOGLE_APP, NO_BRIDGE, BRIDGE_NOT_WORKING, UNKNOWN }

/**
 * Device facts a restore depends on. The app layer answers them with PackageManager and
 * ContentResolver; `null`/[DiscoverReadiness.UNKNOWN] means "could not tell" and never warns.
 */
interface RestoreEnvironment {
    suspend fun discoverReadiness(): DiscoverReadiness
    suspend fun installedIconPacks(): Set<String>?
    suspend fun canReadContent(uri: String): Boolean?

    /** Which of [packages] have a launcher activity in the personal profile; null when unknown. */
    suspend fun installedPackages(packages: Set<String>): Set<String>? = null
}

/**
 * One device-local dependency of restored data. Every feature that stores a reference to something
 * outside Chikku (a package, a content URI, a companion app) adds a check to [RestoreChecks.ALL].
 */
fun interface RestoreCheck {
    suspend fun check(settings: LauncherSettings, env: RestoreEnvironment): List<RestoreAttention>
}

object RestoreChecks {

    val discover = RestoreCheck { settings, env ->
        if (!settings.home.googleDiscoverEnabled) return@RestoreCheck emptyList()
        when (env.discoverReadiness()) {
            DiscoverReadiness.NO_GOOGLE_APP -> listOf(RestoreAttention.DISCOVER_NEEDS_GOOGLE_APP)
            DiscoverReadiness.NO_BRIDGE -> listOf(RestoreAttention.DISCOVER_NEEDS_BRIDGE)
            DiscoverReadiness.BRIDGE_NOT_WORKING -> listOf(RestoreAttention.DISCOVER_BRIDGE_NOT_WORKING)
            DiscoverReadiness.READY, DiscoverReadiness.UNKNOWN -> emptyList()
        }
    }

    val iconPack = RestoreCheck { settings, env ->
        val pack = settings.lookFeel.iconPackPackage ?: return@RestoreCheck emptyList()
        val installed = env.installedIconPacks() ?: return@RestoreCheck emptyList()
        if (pack in installed) emptyList() else listOf(RestoreAttention.ICON_PACK_MISSING)
    }

    val dockImage = RestoreCheck { settings, env ->
        val uri = settings.dock.backgroundImageUri ?: return@RestoreCheck emptyList()
        if (env.canReadContent(uri) == false) listOf(RestoreAttention.DOCK_IMAGE_UNREADABLE) else emptyList()
    }

    /** Only a card the user changed from the default is checked: an untouched default never warns. */
    val contactServices = RestoreCheck { settings, env ->
        val search = settings.search
        if (!search.contactsEnabled || search.contactCardButtons == ContactCardButton.DEFAULT_IDS) return@RestoreCheck emptyList()
        val byId = CommunicationProviders.KNOWN.associateBy { it.id }
        val chosen = ContactServices.buttons(search).mapNotNullTo(HashSet()) { byId[it.providerId]?.packageName }
        if (chosen.isEmpty()) return@RestoreCheck emptyList()
        val installed = env.installedPackages(chosen) ?: return@RestoreCheck emptyList()
        if (installed.containsAll(chosen)) emptyList() else listOf(RestoreAttention.CONTACT_SERVICE_APP_MISSING)
    }

    val ALL: List<RestoreCheck> = listOf(discover, iconPack, dockImage, contactServices)

    /** Runs [checks]; one failing check is skipped rather than hiding the others. Order is stable. */
    suspend fun run(
        settings: LauncherSettings,
        env: RestoreEnvironment,
        checks: List<RestoreCheck> = ALL,
    ): List<RestoreAttention> = checks.flatMap { check ->
        try {
            check.check(settings, env)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }.distinct()
}
