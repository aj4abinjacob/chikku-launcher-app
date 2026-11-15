package io.github.aj4abinjacob.chikku.core.model.settings

/** Most launcher actions an app's long-press menu shows, whatever the settings say. */
const val MAX_MENU_ACTIONS = 4

/** An action a user can switch on or off in the app long-press menu (Folder is shown by the icon itself). */
enum class MenuActionChoice(val onHome: Boolean, val onDrawer: Boolean) {
    EDIT(true, true),
    SWIPE(true, false),
    INFO(true, true),
    STORE(true, true),
    REMOVE(true, false),
    HIDE(true, true),
    UNINSTALL(true, true),
}

/** The actions available on a surface: Swipe actions and Remove only exist for home icons. */
fun menuChoicesFor(fromDrawer: Boolean): List<MenuActionChoice> =
    MenuActionChoice.entries.filter { if (fromDrawer) it.onDrawer else it.onHome }

fun LookFeelSettings.isMenuChoiceOn(choice: MenuActionChoice, fromDrawer: Boolean): Boolean = when (choice) {
    MenuActionChoice.EDIT -> if (fromDrawer) menuShowEditDrawer else menuShowEditHome
    MenuActionChoice.SWIPE -> !fromDrawer && menuShowSwipeHome
    MenuActionChoice.INFO -> if (fromDrawer) menuShowInfoDrawer else menuShowInfoHome
    MenuActionChoice.STORE -> if (fromDrawer) menuShowStoreDrawer else menuShowStoreHome
    MenuActionChoice.REMOVE -> !fromDrawer && menuShowRemoveHome
    MenuActionChoice.HIDE -> if (fromDrawer) menuShowHideDrawer else menuShowHideHome
    MenuActionChoice.UNINSTALL -> if (fromDrawer) menuShowUninstallDrawer else menuShowUninstallHome
}

/** How many actions are switched on for a surface. */
fun LookFeelSettings.menuChoiceCount(fromDrawer: Boolean): Int = menuChoicesFor(fromDrawer).count { isMenuChoiceOn(it, fromDrawer) }

/** Whether one more action can be switched on without passing [MAX_MENU_ACTIONS]. */
fun LookFeelSettings.canAddMenuChoice(fromDrawer: Boolean): Boolean = menuChoiceCount(fromDrawer) < MAX_MENU_ACTIONS

/**
 * [choice] switched on or off for a surface. Switching one on when [MAX_MENU_ACTIONS] are already on
 * leaves the settings unchanged.
 */
fun LookFeelSettings.withMenuChoice(choice: MenuActionChoice, fromDrawer: Boolean, on: Boolean): LookFeelSettings {
    if (on && !isMenuChoiceOn(choice, fromDrawer) && !canAddMenuChoice(fromDrawer)) return this
    return when (choice) {
        MenuActionChoice.EDIT -> if (fromDrawer) copy(menuShowEditDrawer = on) else copy(menuShowEditHome = on)
        MenuActionChoice.SWIPE -> if (fromDrawer) this else copy(menuShowSwipeHome = on)
        MenuActionChoice.INFO -> if (fromDrawer) copy(menuShowInfoDrawer = on) else copy(menuShowInfoHome = on)
        MenuActionChoice.STORE -> if (fromDrawer) copy(menuShowStoreDrawer = on) else copy(menuShowStoreHome = on)
        MenuActionChoice.REMOVE -> if (fromDrawer) this else copy(menuShowRemoveHome = on)
        MenuActionChoice.HIDE -> if (fromDrawer) copy(menuShowHideDrawer = on) else copy(menuShowHideHome = on)
        MenuActionChoice.UNINSTALL -> if (fromDrawer) copy(menuShowUninstallDrawer = on) else copy(menuShowUninstallHome = on)
    }
}

/**
 * Which of [items] a menu keeps when more than [MAX_MENU_ACTIONS] are wanted (older backups, or a
 * contextual Folder action added by the icon): [keepFirst] items always stay, and the rest are dropped
 * from the end of [dropOrder] until four remain. Items keep their order.
 */
fun <T> capMenuActions(items: List<T>, id: (T) -> Int, dropOrder: List<Int>, keepFirst: Set<Int> = emptySet()): List<T> {
    var kept = items
    while (kept.size > MAX_MENU_ACTIONS) {
        val victim = dropOrder.firstOrNull { d -> d !in keepFirst && kept.any { id(it) == d } } ?: break
        kept = kept.filter { id(it) != victim }
    }
    return kept
}
