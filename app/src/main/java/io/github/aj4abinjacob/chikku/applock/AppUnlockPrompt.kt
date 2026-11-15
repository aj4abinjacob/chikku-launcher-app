package io.github.aj4abinjacob.chikku.applock

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.UserManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Unlocks a locked app for the UI, then runs the original action again. */
fun interface AppUnlocker {
    fun unlock(locked: LaunchResult.Locked, onUnlocked: () -> Unit)
}

/**
 * Asks Android to confirm the device owner before a locked app opens, then retries the launch.
 *
 * Authentication is the system's own screen lock: Class 3 (strong) biometrics or the device PIN,
 * pattern or password. There is no Chikku PIN to guess, reset or recover, and the system enforces
 * attempt limits and lockouts. Weak (Class 2) face unlock is not accepted on Android 11+. Without
 * a secure screen lock, locked apps stay locked and the user is sent to set one up.
 *
 * One prompt at a time; it is cancelled when [activity] is destroyed. The "screen lock needed"
 * explanation is a Chikku prompt ([prompts]), not a platform dialog, so it matches the launcher's
 * look; pass the activity's shared instance so it never stacks with another Chikku prompt.
 */
class AppUnlockPrompt(
    private val activity: ComponentActivity,
    private val lock: AppLockManager,
    private val prompts: ChikkuPrompts = ChikkuPrompts(activity),
    /** Where failures ("unavailable", lockout) are shown; Chikku's own surface, never a toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
) : AppUnlocker {
    private var active: CancellationSignal? = null
    private var screenLockPrompt: PromptHandle? = null

    init {
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Not onStop: on Android 10 the PIN/pattern fallback is a separate activity that stops this one.
            override fun onDestroy(owner: LifecycleOwner) = cancel()
        })
    }

    /** Unlocks the app [locked] names, then runs [onUnlocked] (normally the original launch). */
    override fun unlock(locked: LaunchResult.Locked, onUnlocked: () -> Unit) {
        if (active != null) return
        activity.lifecycleScope.launch {
            // A launch that raced the first read of the lock list: re-check instead of prompting.
            if (lock.awaitLoaded().readFailed) return@launch showMessage(R.string.app_lock_prompt_unavailable)
            if (!lock.isLocked(locked.packageName, locked.userSerial)) return@launch onUnlocked()
            val label = loadLabel(locked) ?: activity.getString(R.string.app_lock_prompt_unknown_app)
            authenticate(
                title = activity.getString(R.string.app_lock_prompt_title, label),
                subtitle = activity.getString(R.string.app_lock_prompt_subtitle),
            ) {
                lock.grant(LockTarget(locked.packageName, locked.userSerial))
                onUnlocked()
            }
        }
    }

    /** Confirms the device owner before App lock settings can be viewed or changed. */
    fun confirmOwner(onConfirmed: () -> Unit) {
        if (active != null) return
        authenticate(
            title = activity.getString(R.string.app_lock_confirm_title),
            subtitle = activity.getString(R.string.app_lock_confirm_subtitle),
            noLockMessage = R.string.app_lock_needs_screen_lock_settings_message,
            onSuccess = onConfirmed,
        )
    }

    /** True if the device has a secure screen lock, which app lock requires. */
    fun isDeviceSecure(): Boolean = activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun cancel() {
        active?.cancel()
        active = null
        screenLockPrompt?.dismiss()
        screenLockPrompt = null
    }

    private fun authenticate(
        title: String,
        subtitle: String,
        noLockMessage: Int = R.string.app_lock_needs_screen_lock_message,
        onSuccess: () -> Unit,
    ) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (active != null) return
        if (!isDeviceSecure()) return showScreenLockNeeded(noLockMessage)
        val prompt = try {
            BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setSubtitle(subtitle)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                    } else {
                        @Suppress("DEPRECATION")
                        setDeviceCredentialAllowed(true)
                    }
                }
                .build()
        } catch (_: RuntimeException) {
            return showMessage(R.string.app_lock_prompt_unavailable)
        }
        val signal = CancellationSignal()
        active = signal
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (active !== signal) return
                active = null
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (active === signal) active = null
                when (errorCode) {
                    BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
                    BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> showMessage(R.string.app_lock_prompt_lockout)
                    BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL -> showScreenLockNeeded(noLockMessage)
                    BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT -> showMessage(R.string.app_lock_prompt_unavailable)
                    // Cancelled by the user or the system: stay locked, say nothing.
                    else -> Unit
                }
            }
            // onAuthenticationFailed (one rejected attempt) is shown by the system prompt itself.
        }
        try {
            prompt.authenticate(signal, activity.mainExecutor, callback)
        } catch (_: RuntimeException) {
            active = null
            showMessage(R.string.app_lock_prompt_unavailable)
        }
    }

    private fun showScreenLockNeeded(message: Int) {
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        screenLockPrompt = prompts.show(
            PromptSpec(
                icon = R.drawable.ic_settings_lock,
                title = activity.getString(R.string.app_lock_needs_screen_lock_title),
                message = activity.getString(message),
                tone = PromptTone.WARNING,
                primary = PromptAction(activity.getString(R.string.app_lock_set_screen_lock)) { openScreenLockSettings() },
                dismissLabel = activity.getString(R.string.prompt_not_now),
            ),
        )
    }

    private fun openScreenLockSettings() {
        try {
            activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.app_lock_prompt_unavailable)
        } catch (_: SecurityException) {
            showMessage(R.string.app_lock_prompt_unavailable)
        }
    }

    /** The app's label for the prompt title, read off the main thread; null if unavailable. */
    private suspend fun loadLabel(locked: LaunchResult.Locked): String? = withContext(Dispatchers.IO) {
        try {
            val user = activity.getSystemService(UserManager::class.java)?.getUserForSerialNumber(locked.userSerial)
                ?: return@withContext null
            val info = activity.getSystemService(LauncherApps::class.java)
                ?.getApplicationInfo(locked.packageName, 0, user) ?: return@withContext null
            info.loadLabel(activity.packageManager).toString().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun showMessage(message: Int) = messages.show(message)
}
