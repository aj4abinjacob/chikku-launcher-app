package io.github.aj4abinjacob.chikku.message

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import java.util.concurrent.CopyOnWriteArrayList

/** Test double: records every message (resource ID) instead of showing it. */
class RecordingMessages : ChikkuMessages {
    val shown = CopyOnWriteArrayList<Int>()
    val last: Int? get() = shown.lastOrNull()

    override fun show(text: Int, action: MessageAction?, long: Boolean) {
        shown += text
    }
}

/** The text of the Chikku message bar showing in this activity (HOME's or an overlay), or null. */
fun Activity.shownChikkuMessage(): CharSequence? = window.decorView.findMessageBar()?.shownText

private fun View.findMessageBar(): ChikkuMessageBarView? {
    if (this is ChikkuMessageBarView) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findMessageBar()?.let { return it }
    return null
}
