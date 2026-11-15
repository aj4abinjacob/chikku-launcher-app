package io.github.aj4abinjacob.chikku.home.drawer

import android.content.Context
import android.util.AttributeSet
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import io.github.aj4abinjacob.chikku.core.model.settings.ListEdgeEffect
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrollSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.ListScrolling

/** The Vertical and List drawer: a flick carries as far as the scroll speed says, and the edge effect can be off. */
class VerticalDrawerRecyclerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : RecyclerView(context, attrs) {
    private val maxFling = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private val systemOverScrollMode = overScrollMode

    var scrollSpeed = ListScrollSpeed.NORMAL

    var edgeEffect = ListEdgeEffect.SYSTEM
        set(value) {
            field = value
            overScrollMode = if (value == ListEdgeEffect.NONE) OVER_SCROLL_NEVER else systemOverScrollMode
        }

    override fun fling(velocityX: Int, velocityY: Int): Boolean =
        super.fling(velocityX, ListScrolling.flingVelocity(velocityY, scrollSpeed, maxFling))
}
