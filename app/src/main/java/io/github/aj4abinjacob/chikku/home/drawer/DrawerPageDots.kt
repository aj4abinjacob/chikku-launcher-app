package io.github.aj4abinjacob.chikku.home.drawer

import android.content.Context
import android.util.AttributeSet
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.PageIndicatorView

/** Drawer indicator uses the same tap, scrub, fractional progress and accessible navigation as home. */
class DrawerPageDots @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : PageIndicatorView(context, attrs) {
    override val descriptionResource: Int get() = R.string.drawer_page_description
}
