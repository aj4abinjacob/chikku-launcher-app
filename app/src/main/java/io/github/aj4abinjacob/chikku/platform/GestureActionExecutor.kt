package io.github.aj4abinjacob.chikku.platform

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.speech.RecognizerIntent
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.settings.SettingsActivity

/** Outcome of running a [GestureAction]. Never thrown; the UI decides how to explain it. */
sealed interface GestureResult {
    /** The action ran (or was handed to the system/another app). */
    data object Performed : GestureResult

    /** [GestureAction.None]: nothing to do. */
    data object NoAction : GestureResult

    /**
     * The action needs Chikku's optional accessibility service (lock screen, recents, or the shade
     * when the status-bar call is blocked) and it is not enabled. The UI explains and links to
     * the system accessibility settings; it never enables anything itself.
     */
    data object NeedsAccessibilityService : GestureResult

    /** The target app/activity/shortcut could not be started. */
    data class Unavailable(val reason: LaunchResult.Reason) : GestureResult

    /** The target app is locked; the UI authenticates the user and runs the action again. */
    data class Locked(val locked: LaunchResult.Locked) : GestureResult
}

/** HOME-surface actions the executor cannot perform itself. Implemented by HomeActivity. */
interface GestureHost {
    fun openDrawer()

    /** Opens search; the default opens the drawer with its search field focused. */
    fun openSearch()

    /** Opens the Cards panel; a no-op where HOME cannot show it (e.g. safe mode). */
    fun openCards() = Unit

    /** Scrolls to `home.defaultPage`. */
    fun toggleDock() = Unit

    fun onAppLaunched() = Unit

    fun goToDefaultPage()
}

/** Opens the notification shade or quick settings through the status bar service. */
fun interface StatusBarExpander {
    /** True if the request was delivered; false if the device does not allow it. */
    fun expand(quickSettings: Boolean): Boolean
}

/** Performs an `AccessibilityService.GLOBAL_ACTION_*`; false when the service is not connected. */
fun interface GlobalActionPerformer {
    fun perform(action: Int): Boolean
}

/** Starts a pinned/dynamic shortcut. */
fun interface ShortcutStarter {
    fun start(packageName: String, shortcutId: String, userSerial: Long, sourceBounds: Rect?): LaunchResult
}

/**
 * Runs gesture actions (home gestures, home button, icon and folder swipes). Call on the main
 * thread; it only starts activities or sends binder calls. Never throws: every failure maps to a
 * [GestureResult].
 *
 * Boundaries: Recents through the accessibility service opens the system overview, but it is not
 * the stock gesture-navigation animation; a normal (non-root) launcher cannot provide that.
 */
class GestureActionExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val host: GestureHost,
    private val statusBar: StatusBarExpander = ReflectionStatusBarExpander(context),
    private val globalActions: GlobalActionPerformer = GlobalActionPerformer(ChikkuAccessibilityService::performGlobalAction),
    /** Required: the app-lock-guarded [LauncherAppsShortcuts] in production. */
    private val shortcuts: ShortcutStarter,
    private val externalIntents: ExternalIntentStarter = ExternalIntentStarter.DENY,
) {

    fun execute(action: GestureAction, sourceBounds: Rect? = null): GestureResult = try {
        run(action, sourceBounds).also { result ->
            if (result == GestureResult.Performed && (action is GestureAction.LaunchApp || action is GestureAction.LaunchShortcut)) {
                host.onAppLaunched()
            }
        }
    } catch (e: RuntimeException) {
        GestureResult.Unavailable(launchFailureReason(e))
    }

    private fun run(action: GestureAction, sourceBounds: Rect?): GestureResult = when (action) {
        GestureAction.None, GestureAction.OpenFolder -> GestureResult.NoAction
        GestureAction.OpenDrawer -> performed(host::openDrawer)
        GestureAction.OpenSearch -> performed(host::openSearch)
        GestureAction.OpenCards -> performed(host::openCards)
        GestureAction.ToggleDock -> performed(host::toggleDock)
        GestureAction.GoToDefaultPage -> performed(host::goToDefaultPage)
        GestureAction.ExpandNotifications -> expandShade(quickSettings = false)
        GestureAction.ExpandQuickSettings -> expandShade(quickSettings = true)
        GestureAction.LockScreen -> global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        GestureAction.Recents -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
        GestureAction.OpenLauncherSettings ->
            startFirst(listOf(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        GestureAction.Assistant -> startExternal(listOf(Intent(Intent.ACTION_ASSIST), Intent(Intent.ACTION_VOICE_COMMAND)))
        GestureAction.VoiceSearch -> startExternal(
            listOf(
                Intent(RecognizerIntent.ACTION_WEB_SEARCH),
                Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE),
            ),
        )
        is GestureAction.LaunchApp -> appLauncher.launch(action.key, sourceBounds).toGestureResult()
        is GestureAction.LaunchShortcut ->
            shortcuts.start(action.packageName, action.shortcutId, action.userSerial, sourceBounds).toGestureResult()
    }

    private fun expandShade(quickSettings: Boolean): GestureResult {
        if (runCatching { statusBar.expand(quickSettings) }.getOrDefault(false)) return GestureResult.Performed
        val fallback = if (quickSettings) {
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
        } else {
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        }
        return global(fallback)
    }

    private fun global(action: Int): GestureResult =
        if (globalActions.perform(action)) GestureResult.Performed else GestureResult.NeedsAccessibilityService

    private fun startExternal(intents: List<Intent>): GestureResult =
        if (externalIntents.start(intents.map { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })) GestureResult.Performed
        else GestureResult.Unavailable(LaunchResult.Reason.NOT_FOUND)

    /** Internal launcher settings only; external activities use the guarded asynchronous boundary. */
    private fun startFirst(intents: List<Intent>): GestureResult {
        var failure = LaunchResult.Reason.NOT_FOUND
        for (intent in intents) {
            // Always its own task: HOME is singleTask, so the assistant or voice search would otherwise
            // join the launcher's task, miss from Recents and be destroyed by the next Home press.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return GestureResult.Performed
            } catch (_: ActivityNotFoundException) {
                // Try the next fallback.
            } catch (e: SecurityException) {
                failure = launchFailureReason(e)
            }
        }
        return GestureResult.Unavailable(failure)
    }

    private inline fun performed(block: () -> Unit): GestureResult {
        block()
        return GestureResult.Performed
    }

    private fun LaunchResult.toGestureResult(): GestureResult = when (this) {
        LaunchResult.Started -> GestureResult.Performed
        is LaunchResult.Unavailable -> GestureResult.Unavailable(reason)
        is LaunchResult.Locked -> GestureResult.Locked(this)
    }
}

/**
 * Expands the shade through `StatusBarManager.expandNotificationsPanel`/`expandSettingsPanel`.
 * These are not public SDK methods; launchers call them with the normal EXPAND_STATUS_BAR
 * permission. Any device that blocks them makes this return false, and the executor falls back to
 * the accessibility service.
 */
class ReflectionStatusBarExpander(private val context: Context) : StatusBarExpander {
    @SuppressLint("WrongConstant", "PrivateApi", "DiscouragedPrivateApi")
    override fun expand(quickSettings: Boolean): Boolean = try {
        val service = context.getSystemService(STATUS_BAR_SERVICE) ?: return false
        val method = service.javaClass.getMethod(if (quickSettings) "expandSettingsPanel" else "expandNotificationsPanel")
        method.invoke(service)
        true
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: RuntimeException) {
        false
    } catch (_: LinkageError) {
        false
    }

    private companion object {
        // Context.STATUS_BAR_SERVICE is hidden.
        const val STATUS_BAR_SERVICE = "statusbar"
    }
}
