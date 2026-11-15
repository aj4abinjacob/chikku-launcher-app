package io.github.aj4abinjacob.chikku.home.widget

import android.view.View
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.WorkspaceUi

/**
 * How `WorkspaceController` renders widget items. Without a binder, widgets fall back to a
 * placeholder icon (e.g. in tests).
 */
interface WidgetViewBinder {
    fun createWidgetView(): View

    fun isWidgetView(view: View): Boolean

    fun bindWidget(view: View, item: HomeItemUi, content: ItemContent.Widget)

    fun recycleWidget(view: View)

    /** Called after every workspace render (e.g. to release orphaned ids once per start). */
    fun onWorkspaceRendered(ui: WorkspaceUi.Ready)
}
