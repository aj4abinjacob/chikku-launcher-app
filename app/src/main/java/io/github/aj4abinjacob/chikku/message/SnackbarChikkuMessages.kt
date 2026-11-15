package io.github.aj4abinjacob.chikku.message

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Messages as a Material 3 snackbar in Chikku's settings theme, for Compose screens (settings).
 * The [state] belongs to a `SnackbarHost` the screen places at the bottom; M3's host already
 * stretches the duration for accessibility services and the snackbar is a polite live region, so
 * TalkBack reads it. A new message replaces the one showing.
 */
class SnackbarChikkuMessages(
    private val context: Context,
    private val scope: CoroutineScope,
    val state: SnackbarHostState,
) : ChikkuMessages {
    override fun show(text: Int, action: MessageAction?, long: Boolean) {
        val message = context.getString(text)
        val label = action?.let { context.getString(it.label) }
        scope.launch(Dispatchers.Main.immediate) {
            state.currentSnackbarData?.dismiss()
            val result = state.showSnackbar(
                message = message,
                actionLabel = label,
                duration = if (long || action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) action?.onClick?.invoke()
        }
    }
}

/** The screen's messages; provided by [io.github.aj4abinjacob.chikku.settings.SettingsActivity]. */
val LocalChikkuMessages: ProvidableCompositionLocal<ChikkuMessages?> = staticCompositionLocalOf { null }

/**
 * Messages for composable code: [LocalChikkuMessages] when provided, otherwise the hosting
 * activity's ([ChikkuMessages.of]).
 */
@Composable
fun rememberChikkuMessages(): ChikkuMessages {
    val provided = LocalChikkuMessages.current
    val context = LocalContext.current
    val fallback = remember(context) { ChikkuMessages.of(context) }
    return provided ?: fallback
}
