package io.github.aj4abinjacob.chikku.core.model.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MenuActionChoicesTest {
    private val defaults = LookFeelSettings()

    @Test
    fun defaultsAreFourActionsOnHomeAndOnDrawer() {
        assertThat(defaults.menuChoiceCount(false)).isEqualTo(4)
        assertThat(defaults.menuChoiceCount(true)).isEqualTo(4)
        assertThat(menuChoicesFor(false).filter { defaults.isMenuChoiceOn(it, false) })
            .containsExactly(MenuActionChoice.EDIT, MenuActionChoice.INFO, MenuActionChoice.REMOVE, MenuActionChoice.UNINSTALL)
    }

    @Test
    fun aFifthActionIsRefusedUntilOneIsSwitchedOff() {
        val refused = defaults.withMenuChoice(MenuActionChoice.STORE, fromDrawer = false, on = true)
        assertThat(refused).isEqualTo(defaults)
        val swapped = defaults.withMenuChoice(MenuActionChoice.REMOVE, false, false).withMenuChoice(MenuActionChoice.STORE, false, true)
        assertThat(swapped.menuShowStoreHome).isTrue()
        assertThat(swapped.menuChoiceCount(false)).isEqualTo(4)
    }

    @Test
    fun switchingAnActionOffAlwaysWorksAndSurfacesAreIndependent() {
        val off = defaults.withMenuChoice(MenuActionChoice.INFO, fromDrawer = true, on = false)
        assertThat(off.menuShowInfoDrawer).isFalse()
        assertThat(off.menuShowInfoHome).isTrue()
    }

    @Test
    fun drawerHasNoSwipeActionsOrRemove() {
        assertThat(menuChoicesFor(true)).containsNoneOf(MenuActionChoice.SWIPE, MenuActionChoice.REMOVE)
        assertThat(defaults.withMenuChoice(MenuActionChoice.SWIPE, true, true)).isEqualTo(defaults)
    }

    @Test
    fun capKeepsFourAndNeverDropsKeepFirst() {
        val order = listOf(10, 20, 30, 40, 50, 60)
        val drop = listOf(60, 50, 40, 30, 20, 10)
        assertThat(capMenuActions(order, { it }, drop)).containsExactly(10, 20, 30, 40).inOrder()
        assertThat(capMenuActions(order, { it }, drop, keepFirst = setOf(60))).containsExactly(10, 20, 30, 60).inOrder()
        assertThat(capMenuActions(listOf(1, 2, 3), { it }, drop)).containsExactly(1, 2, 3).inOrder()
    }
}
