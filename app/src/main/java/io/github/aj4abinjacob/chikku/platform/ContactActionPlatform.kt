package io.github.aj4abinjacob.chikku.platform

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import android.provider.ContactsContract.Data
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProvider
import io.github.aj4abinjacob.chikku.core.model.search.CommunicationProviders
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.core.model.search.ProviderEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Whether Chikku can start [ProviderEntryPoint] of a provider app right now. An entry point is
 * supported only when exactly one exported activity of that package handles it and it demands no
 * permission Chikku lacks. WhatsApp and Signal guard their call entries with CALL_PHONE, which
 * Chikku never requests, so those calls are not offered (docs/CONTACT_SERVICES.md).
 */
fun interface ContactEntryPointProbe {
    fun supports(provider: CommunicationProvider, entryPoint: ProviderEntryPoint): Boolean
}

class AndroidContactEntryPointProbe(context: Context) : ContactEntryPointProbe {
    private val context = context.applicationContext

    override fun supports(provider: CommunicationProvider, entryPoint: ProviderEntryPoint): Boolean = try {
        val intent = ContactActionIntents.view(provider.packageName, entryPoint.mimeType, PROBE_DATA_ID)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        // An ambiguous match resolves to the platform chooser, whose package differs: not supported.
        info != null && info.packageName == provider.packageName && info.exported && info.isEnabled &&
            (info.permission == null || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
    } catch (_: RuntimeException) {
        false
    }

    private companion object {
        /** Resolution matches on type and scheme only; the row id is irrelevant. */
        const val PROBE_DATA_ID = 0L
    }
}

object ContactActionIntents {
    /** Android's Contacts-app handoff: view the provider's own data row, restricted to its package. */
    fun view(packageName: String, mimeType: String, dataId: Long): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(Data.CONTENT_URI, dataId), mimeType)
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Outcome of a communication-service action. Typed, never thrown into the UI. */
sealed interface ContactActionResult {
    data object Started : ContactActionResult

    /** The provider app is locked in Chikku's app lock; nothing started. Unlock, then retry. */
    data class Locked(val launch: LaunchResult.Locked) : ContactActionResult

    /** READ_CONTACTS was revoked after the results were shown. */
    data object PermissionMissing : ContactActionResult

    /** The contact or its synced row changed or was deleted since the results were shown. */
    data object ContactChanged : ContactActionResult

    /** The app was removed or disabled, no longer offers the action, or rejected the launch. */
    data object Unavailable : ContactActionResult
}

/**
 * Validates and starts a [ContactChannelAction] after an explicit tap: the data row must still
 * belong to the contact, the app must still support the entry point, and app lock is asked last.
 * [prepare] runs on [io]; [start] on the main thread. Nothing is logged.
 */
class ContactActionLauncher(
    context: Context,
    private val lock: AppLockGuard,
    private val io: CoroutineDispatcher,
    private val probe: ContactEntryPointProbe = AndroidContactEntryPointProbe(context),
    private val providers: List<CommunicationProvider> = CommunicationProviders.KNOWN,
) {
    private val context = context.applicationContext

    /** A validated launch, or the reason there is none. */
    sealed interface Prepared {
        data class Ready(val intent: Intent, val packageName: String) : Prepared
        data class Failed(val result: ContactActionResult) : Prepared
    }

    suspend fun prepare(contact: ContactHit, action: ContactChannelAction): Prepared = try {
        withContext(io) { validate(contact, action) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: SecurityException) {
        Prepared.Failed(ContactActionResult.PermissionMissing)
    } catch (_: RuntimeException) {
        Prepared.Failed(ContactActionResult.Unavailable)
    }

    fun start(prepared: Prepared.Ready): ContactActionResult {
        val serial = personalSerial() ?: return ContactActionResult.Unavailable
        if (!lock.admit(prepared.packageName, serial)) {
            return ContactActionResult.Locked(LaunchResult.Locked(prepared.packageName, serial))
        }
        return try {
            context.startActivity(prepared.intent)
            ContactActionResult.Started
        } catch (_: ActivityNotFoundException) {
            ContactActionResult.Unavailable
        } catch (_: SecurityException) {
            ContactActionResult.Unavailable
        } catch (_: RuntimeException) {
            ContactActionResult.Unavailable
        }
    }

    private fun validate(contact: ContactHit, action: ContactChannelAction): Prepared {
        val provider = providers.firstOrNull { it.id == action.providerId }
        val entryPoint = provider?.entryPoints?.firstOrNull { it.mimeType == action.mimeType && it.type == action.type }
        if (provider == null || entryPoint == null) return Prepared.Failed(ContactActionResult.Unavailable)
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return Prepared.Failed(ContactActionResult.PermissionMissing)
        }
        val current = context.contentResolver.query(
            ContentUris.withAppendedId(Data.CONTENT_URI, action.dataId),
            ROW_PROJECTION, null, null, null,
        )?.use { c -> if (c.moveToFirst()) Row(c.getLong(0), c.getString(1), c.getString(2)) else null }
            ?: return Prepared.Failed(ContactActionResult.ContactChanged)
        // Contact ids change when contacts are joined; the lookup key survives that.
        val sameContact = current.contactId == contact.id || current.lookupKey == contact.lookupKey
        if (current.mimeType != action.mimeType || !sameContact) return Prepared.Failed(ContactActionResult.ContactChanged)
        if (!probe.supports(provider, entryPoint)) return Prepared.Failed(ContactActionResult.Unavailable)
        return Prepared.Ready(ContactActionIntents.view(provider.packageName, action.mimeType, action.dataId), provider.packageName)
    }

    private fun personalSerial(): Long? =
        context.getSystemService(UserManager::class.java).personalUserSerial(requireUnlocked = false)

    private data class Row(val contactId: Long, val mimeType: String?, val lookupKey: String?)

    private companion object {
        val ROW_PROJECTION = arrayOf(Data.CONTACT_ID, Data.MIMETYPE, Data.LOOKUP_KEY)
    }
}
