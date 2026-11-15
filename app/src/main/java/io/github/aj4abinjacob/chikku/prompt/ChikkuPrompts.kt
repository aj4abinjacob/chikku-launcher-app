package io.github.aj4abinjacob.chikku.prompt

import android.graphics.drawable.Drawable
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import kotlinx.coroutines.launch

/** Sets the badge color and, for [DESTRUCTIVE], paints the primary button in the error color. */
enum class PromptTone { NEUTRAL, WARNING, DESTRUCTIVE }

class PromptAction(val label: String, val onClick: () -> Unit)

/** The item a prompt is about (an app, a folder entry, a shortcut), shown as a card. Shown, never logged. */
class PromptSubject(
    val label: String,
    val icon: Drawable? = null,
    /** A short state tag, such as "Not installed". */
    val status: String? = null,
)

/**
 * One Chikku prompt: a short explanation with one main action. Text is resolved by the caller.
 * [onDismiss] runs when the prompt closes without an action (Cancel, back, swipe down, tapping
 * outside, or the host removing it).
 */
class PromptSpec(
    @DrawableRes val icon: Int,
    val title: String,
    val message: String,
    val primary: PromptAction,
    val dismissLabel: String,
    val tone: PromptTone = PromptTone.NEUTRAL,
    val subject: PromptSubject? = null,
    val secondary: PromptAction? = null,
    val onDismiss: () -> Unit = {},
)

/** One shown prompt. [dismiss] closes it only while it is still the one showing, never a later prompt. */
interface PromptHandle {
    val isShowing: Boolean
    fun dismiss()
}

/**
 * Shows Chikku's own prompts in any [ComponentActivity] instead of platform `AlertDialog`s, which
 * take the device skin (One UI, MIUI) and look foreign in a launcher people use to style their
 * phone. A prompt is a bottom sheet in the settings theme and accent, matching the folder and app
 * editors: icon badge, title, message, an optional subject card and full-width stacked actions
 * (easy to reach one-handed, and long labels never squeeze). One prompt at a time per instance:
 * HOME shares one between its controllers, and each keeps the [PromptHandle] of its own prompt so
 * closing a panel never takes down a prompt another controller showed.
 */
class ChikkuPrompts(
    private val activity: ComponentActivity,
    /** The user's accent color (style settings); null uses Chikku's default. */
    private val accent: () -> Int? = { null },
) {
    private var view: ComposeView? = null
    private var current: PromptSpec? = null

    val isShowing: Boolean get() = view != null

    fun show(spec: PromptSpec): PromptHandle {
        dismiss()
        val handle = object : PromptHandle {
            override val isShowing get() = current === spec
            override fun dismiss() = close(spec, acted = false)
        }
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return handle
        val accentColor = accent()
        val compose = ComposeView(activity).apply {
            // The content root only gets these owners from setContentView; an activity that shows
            // nothing but a prompt (ShortcutPinActivity) never calls it, and Compose would crash.
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setViewTreeOnBackPressedDispatcherOwner(activity)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ChikkuSettingsTheme(accent = accentColor) {
                    PromptSheet(spec, onClosed = { acted -> close(spec, acted) })
                }
            }
        }
        view = compose
        current = spec
        root.addView(compose, FrameLayout.LayoutParams(0, 0))
        return handle
    }

    /** Closes whichever prompt is showing, without an action; its [PromptSpec.onDismiss] runs. */
    fun dismiss() {
        val spec = current ?: return
        close(spec, acted = false)
    }

    private fun close(spec: PromptSpec, acted: Boolean) {
        if (current !== spec) return
        val shown = view
        view = null
        current = null
        (shown?.parent as? ViewGroup)?.removeView(shown)
        if (!acted) spec.onDismiss()
    }
}

/** The sheet itself; [onClosed] gets whether an action ran. Public for previews and tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptSheet(spec: PromptSpec, onClosed: (acted: Boolean) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    /** Runs [action] (if any) once the sheet has slid away, so a new screen never opens under it. */
    fun finish(action: (() -> Unit)?) {
        if (closing) return
        closing = true
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onClosed(action != null)
            action?.invoke()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!closing) {
                closing = true
                onClosed(false)
            }
        },
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Badge(spec.icon, spec.tone)
            Spacer(Modifier.height(16.dp))
            Text(
                spec.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                spec.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            spec.subject?.let {
                Spacer(Modifier.height(16.dp))
                SubjectCard(it)
            }
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val destructive = spec.tone == PromptTone.DESTRUCTIVE
                Button(
                    onClick = { finish(spec.primary.onClick) },
                    colors = if (destructive) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
                ) { Text(spec.primary.label) }
                spec.secondary?.let { secondary ->
                    FilledTonalButton(
                        onClick = { finish(secondary.onClick) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
                    ) { Text(secondary.label) }
                }
                TextButton(
                    onClick = { finish(null) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
                ) { Text(spec.dismissLabel) }
            }
        }
    }
}

@Composable
private fun Badge(@DrawableRes icon: Int, tone: PromptTone) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        PromptTone.NEUTRAL -> colors.secondaryContainer to colors.onSecondaryContainer
        PromptTone.WARNING -> colors.tertiaryContainer to colors.onTertiaryContainer
        PromptTone.DESTRUCTIVE -> colors.errorContainer to colors.onErrorContainer
    }
    Box(
        Modifier.size(BadgeSize).clip(RoundedCornerShape(16.dp)).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SubjectCard(subject: PromptSubject) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerHigh)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        subject.icon?.let { drawable ->
            val sizePx = with(LocalDensity.current) { SubjectIconSize.roundToPx() }
            val bitmap = remember(drawable, sizePx) {
                runCatching { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                Image(bitmap, contentDescription = null, modifier = Modifier.size(SubjectIconSize))
                Spacer(Modifier.width(16.dp))
            }
        }
        Text(
            subject.label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        subject.status?.let { status ->
            Spacer(Modifier.width(12.dp))
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                color = colors.onErrorContainer,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.errorContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private val BadgeSize = 56.dp
private val SubjectIconSize = 40.dp
private val ButtonHeight = 52.dp
