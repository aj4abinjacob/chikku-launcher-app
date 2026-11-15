package io.github.aj4abinjacob.chikku.message

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.StringRes

/** An optional button on a message, such as "Retry" or "Open settings". */
class MessageAction(@StringRes val label: Int, val onClick: () -> Unit)

/**
 * Short, transient status messages drawn by Chikku itself instead of a platform `Toast`, which the
 * device skin (One UI, MIUI) draws and which looks foreign in a launcher people use to style their
 * phone. HOME shows a [ChikkuMessageBarView] above the dock and gesture bar; settings shows a
 * snackbar in its theme ([SnackbarChikkuMessages]). Every implementation announces the text to
 * accessibility services and shows one message at a time (a new one replaces the current one).
 *
 * Messages are resource strings only: never pass app labels, search queries or private-profile
 * contents through here.
 */
interface ChikkuMessages {

    /** Shows [text]; [long] keeps it up longer. A message with an [action] is always kept up longer. */
    fun show(@StringRes text: Int, action: MessageAction? = null, long: Boolean = false)

    /** An activity (or other host) that provides its own messages surface. */
    interface Owner {
        val chikkuMessages: ChikkuMessages
    }

    companion object {
        /** Drops every message; for code with no screen to show it on. */
        val None: ChikkuMessages = object : ChikkuMessages {
            override fun show(text: Int, action: MessageAction?, long: Boolean) = Unit
        }

        /**
         * Messages for whatever activity [context] belongs to, resolved when a message is shown (so
         * a controller may be built before its activity's surface exists): the activity's own
         * surface if it is an [Owner] (HOME, settings), otherwise a [ChikkuMessageBarView] laid over
         * the activity's content. A context with no activity (the application) drops messages.
         * Safe to call from any thread; the message is shown on the main thread.
         */
        fun of(context: Context): ChikkuMessages = ContextMessages(context)

        internal fun findActivity(context: Context): Activity? {
            var current: Context? = context
            while (current != null) {
                if (current is Activity) return current
                current = (current as? ContextWrapper)?.baseContext
            }
            return null
        }

        /** The overlay bar of [activity], created on first use; one per activity. */
        internal fun overlayFor(activity: Activity): ChikkuMessageBarView? {
            val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return null
            for (i in 0 until content.childCount) {
                (content.getChildAt(i) as? ChikkuMessageBarView)?.let { return it }
            }
            return ChikkuMessageBarView.attach(content)
        }
    }
}

private class ContextMessages(private val context: Context) : ChikkuMessages {
    override fun show(text: Int, action: MessageAction?, long: Boolean) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            showNow(text, action, long)
        } else {
            Handler(Looper.getMainLooper()).post { showNow(text, action, long) }
        }
    }

    private fun showNow(text: Int, action: MessageAction?, long: Boolean) {
        val activity = ChikkuMessages.findActivity(context) ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val target = (activity as? ChikkuMessages.Owner)?.chikkuMessages ?: ChikkuMessages.overlayFor(activity) ?: return
        target.show(text, action, long)
    }
}
