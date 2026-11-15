package io.github.aj4abinjacob.chikku.home.folder

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.appmenu.AppMenuHandle
import io.github.aj4abinjacob.chikku.home.appmenu.GridAction
import io.github.aj4abinjacob.chikku.home.appmenu.MenuActionGrid
import io.github.aj4abinjacob.chikku.home.appmenu.arrangeMenu
import io.github.aj4abinjacob.chikku.home.appmenu.menuArrow
import io.github.aj4abinjacob.chikku.home.appmenu.placeMenuWindow

/**
 * Folder-specific menu, in the same card, labelled action buttons, arrow and placement as the app
 * menu (Style menu color, text color and corner radius): Edit, Open and Remove. Open works on a
 * locked layout; the other two are refused there. The workspace helper dismisses it when the press
 * becomes a drag.
 */
internal class FolderMenuPopup(
    context: Context,
    settings: LauncherSettings,
    private val onEdit: () -> Unit,
    private val onOpen: () -> Unit,
    private val onRemove: () -> Unit,
    private val onLocked: () -> Unit,
    private val isLocked: () -> Boolean,
) : AppMenuHandle {
    private val style = LauncherMenuStyle(context, settings)
    private val card = style.card().apply {
        // Extra room under the labels so they don't sit on the card's bottom edge.
        setPadding(0, style.dp(4f).toInt(), 0, style.dp(12f).toInt())
    }
    private val popup: PopupWindow = style.popup(card)
    private val cornerRadius = style.dp(settings.lookFeel.menuCornerRadiusDp)
    private val arrow = menuArrow(
        context,
        LauncherMenuStyle.cardFill(style.colors, customBackground = settings.lookFeel.menuBackgroundColor != null),
        LauncherMenuStyle.cardStroke(style.colors),
        LauncherMenuStyle.edgeWidth(context),
    )

    init {
        val color = LauncherMenuStyle.actionColor(style.colors)
        fun action(label: Int, icon: Int, editsLayout: Boolean, run: () -> Unit) =
            GridAction(context.getString(label), icon, color) {
                dismiss()
                if (editsLayout && isLocked()) onLocked() else run()
            }
        card.addView(
            MenuActionGrid(context).apply {
                val pad = style.dp(8f).toInt()
                setPadding(pad, 0, pad, 0)
                addActions(
                    listOf(
                        action(R.string.folder_edit_button, R.drawable.ic_menu_edit, editsLayout = true, onEdit),
                        action(R.string.folder_menu_open, R.drawable.ic_settings_folder, editsLayout = false, onOpen),
                        action(R.string.app_menu_remove, R.drawable.ic_menu_remove, editsLayout = true, onRemove),
                    ),
                )
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** [onDismiss] runs once the menu closes, however it closes. */
    fun show(anchor: View, onDismiss: () -> Unit = {}) {
        popup.setOnDismissListener { onDismiss() }
        val shadow = style.dp(LauncherMenuStyle.CARD_ELEVATION_DP).toInt()
        val column = popup.contentView as LinearLayout
        placeMenuWindow(
            popup, anchor, style.dp(MAX_WIDTH_DP).toInt() + 2 * shadow, exactWidth = false, showing = false,
            shadowPad = shadow, cornerRadius = cornerRadius,
        ) { above, arrowX ->
            arrangeMenu(column, listOf(card), arrow, above, arrowX)
        }
    }

    override fun dismiss() = popup.dismiss()

    private companion object {
        const val MAX_WIDTH_DP = 272f
    }
}
