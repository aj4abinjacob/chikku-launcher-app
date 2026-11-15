package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.ChikkuApplication
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptSubject
import io.github.aj4abinjacob.chikku.theme.NightModeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * System pin-request entry point. Arbitrary intents cannot substitute a valid framework token.
 *
 * The window is translucent (`Theme.Chikku.Translucent`), so the user sees only Chikku's confirm
 * sheet over the app that asked. Every outcome (add, cancel, back, swipe, tap outside) finishes
 * the activity exactly once.
 */
class ShortcutPinActivity : ComponentActivity() {
    private var finished = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        NightModeController.applyOverride(this, newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apps = getSystemService(LauncherApps::class.java) ?: run { finishOnce(); return }
        val request = try { apps.getPinItemRequest(intent) } catch (_: RuntimeException) { null }
        val info = request?.takeIf { it.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT }?.shortcutInfo
        if (request == null || !valid(request) || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT ||
            info == null || info.userHandle != Process.myUserHandle() || !info.isEnabled) {
            // A real request from another app gets an answer; only a malformed launch closes quietly.
            if (request != null) Toast.makeText(this, R.string.shortcut_pin_unavailable, Toast.LENGTH_SHORT).show()
            finishOnce()
            return
        }
        val container = (application as ChikkuApplication).container
        lifecycleScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                try {
                    val settings = container.settingsRepository.settings.first()
                    val allowed = container.backupService.prepare() && !settings.home.layoutLocked &&
                        apps.hasShortcutHostPermission() && getSystemService(UserManager::class.java).isUserUnlocked
                    if (allowed) Prepared(settings.lookFeel.accentColor, shortcutIcon(apps, info), requesterLabel(apps, info)) else null
                } catch (_: RuntimeException) { null }
            }
            if (prepared == null || !valid(request)) {
                Toast.makeText(this@ShortcutPinActivity, R.string.shortcut_pin_unavailable, Toast.LENGTH_SHORT).show()
                finishOnce()
                return@launch
            }
            val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
            val message = prepared.requester?.let { getString(R.string.shortcut_pin_message, it) }
                ?: getString(R.string.shortcut_pin_message_unknown_app)
            ChikkuPrompts(this@ShortcutPinActivity) { prepared.accent }.show(
                PromptSpec(
                    icon = R.drawable.ic_settings_home,
                    title = getString(R.string.shortcut_pin_title),
                    message = message,
                    subject = PromptSubject(label.ifBlank { getString(R.string.shortcut_pin_unnamed) }, prepared.icon),
                    primary = PromptAction(getString(R.string.shortcut_pin_add)) { accept(apps, request, info, label) },
                    dismissLabel = getString(R.string.shortcut_pin_cancel),
                    onDismiss = ::finishOnce,
                ),
            )
        }
    }

    private fun accept(apps: LauncherApps, request: LauncherApps.PinItemRequest, info: ShortcutInfo, label: String) {
        val container = (application as ChikkuApplication).container
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val users = getSystemService(UserManager::class.java)
                val serial = users.getSerialNumberForUser(info.userHandle)
                val allowed = try {
                    serial >= 0 && !container.settingsRepository.settings.first().home.layoutLocked &&
                        users.isUserUnlocked && apps.hasShortcutHostPermission() && valid(request)
                } catch (_: RuntimeException) { false }
                // Place first, then accept: accepting pins the shortcut for its app, so a home screen
                // with no room must not leave it pinned but missing. Undo the placement if accept fails.
                val placed = if (allowed) container.workspaceRepository.addShortcut(
                    ItemTarget.Shortcut(info.`package`, info.id, serial, label), null,
                ) else null
                if (placed is EditResult.Applied && !acceptRequest(request)) {
                    container.workspaceRepository.removeItem(placed.itemId)
                    null
                } else placed
            }
            if (result !is EditResult.Applied) {
                Toast.makeText(this@ShortcutPinActivity, R.string.shortcut_pin_unavailable, Toast.LENGTH_LONG).show()
            }
            finishOnce()
        }
    }

    private fun finishOnce() {
        if (finished) return
        finished = true
        finish()
    }

    /** The shortcut's own icon for the confirm card; null if the system cannot provide it. Off main. */
    private fun shortcutIcon(apps: LauncherApps, info: ShortcutInfo): Drawable? =
        try { apps.getShortcutIconDrawable(info, resources.displayMetrics.densityDpi) } catch (_: RuntimeException) { null }

    /** The label of the app that published the shortcut, shown (never logged); null if unavailable. Off main. */
    private fun requesterLabel(apps: LauncherApps, info: ShortcutInfo): String? = try {
        apps.getApplicationInfo(info.`package`, 0, info.userHandle)
            ?.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    private fun acceptRequest(request: LauncherApps.PinItemRequest): Boolean =
        try { valid(request) && request.accept() } catch (_: RuntimeException) { false }

    private fun valid(request: LauncherApps.PinItemRequest): Boolean =
        try { request.isValid } catch (_: RuntimeException) { false }

    private class Prepared(val accent: Int?, val icon: Drawable?, val requester: String?)
}
