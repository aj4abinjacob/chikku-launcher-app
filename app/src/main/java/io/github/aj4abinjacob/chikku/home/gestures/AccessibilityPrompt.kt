package io.github.aj4abinjacob.chikku.home.gestures

import android.app.Activity
import android.content.Context
import androidx.core.content.ContextCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.ChikkuAccessibilityService
import io.github.aj4abinjacob.chikku.prompt.ChikkuPrompts
import io.github.aj4abinjacob.chikku.prompt.PromptAction
import io.github.aj4abinjacob.chikku.prompt.PromptHandle
import io.github.aj4abinjacob.chikku.prompt.PromptSpec
import io.github.aj4abinjacob.chikku.prompt.PromptSubject

/**
 * Explains why a gesture (from HOME or a card) needs Chikku's optional accessibility service and
 * offers the system accessibility settings. A trust moment, so the copy states only what
 * `res/xml/chikku_accessibility_service.xml` guarantees, in a neutral tone; the subject card names
 * the exact entry to look for in system settings.
 */
internal fun ChikkuPrompts.showAccessibilityExplanation(
    activity: Activity,
    messages: ChikkuMessages = ChikkuMessages.of(activity),
): PromptHandle =
    show(accessibilityDisclosure(activity, messages))

/** Shared by gesture execution and the Settings access entry; no intent until affirmative consent. */
internal fun accessibilityDisclosure(
    context: Context,
    messages: ChikkuMessages = ChikkuMessages.of(context),
): PromptSpec =
    PromptSpec(
        icon = R.drawable.ic_settings_gestures,
        title = context.getString(R.string.prompt_accessibility_title),
        message = context.getString(R.string.prompt_accessibility_message),
        primary = PromptAction(context.getString(R.string.prompt_accessibility_open)) {
            openAccessibilitySettings(context, messages)
        },
        dismissLabel = context.getString(R.string.prompt_not_now),
        subject = PromptSubject(
            label = context.getString(R.string.gestures_accessibility_service_label),
            icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher),
        ),
    )

private fun openAccessibilitySettings(context: Context, messages: ChikkuMessages) {
    try {
        context.startActivity(ChikkuAccessibilityService.settingsIntent(context))
    } catch (_: RuntimeException) { // ActivityNotFoundException, SecurityException
        messages.show(R.string.gesture_accessibility_settings_unavailable)
    }
}
