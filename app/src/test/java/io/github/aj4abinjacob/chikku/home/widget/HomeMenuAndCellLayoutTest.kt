package io.github.aj4abinjacob.chikku.home.widget

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.home.CellLayout
import io.github.aj4abinjacob.chikku.home.HomeMenu
import io.github.aj4abinjacob.chikku.home.HomeMenuEntry
import io.github.aj4abinjacob.chikku.home.HomeMenuGroup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeMenuAndCellLayoutTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun homeMenuSortsByOrderReplacesByIdAndHidesInvisible() {
        val menu = HomeMenu(context)
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_SETTINGS, R.string.widgets_home_menu_settings, 300) {})
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WIDGETS, R.string.widgets_home_menu_widgets, 100) {})
        menu.addEntry(HomeMenuEntry("other", R.string.widgets_home_menu_wallpapers, 150, isVisible = { false }) {})
        var replaced = false
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WIDGETS, R.string.widgets_home_menu_widgets, 100) { replaced = true })

        val visible = menu.visibleEntries()
        assertThat(visible.map { it.id }).containsExactly(HomeMenu.ENTRY_WIDGETS, HomeMenu.ENTRY_SETTINGS).inOrder()
        visible.first().onClick()
        assertThat(replaced).isTrue()

        menu.removeEntry(HomeMenu.ENTRY_SETTINGS)
        assertThat(menu.visibleEntries().map { it.id }).containsExactly(HomeMenu.ENTRY_WIDGETS)
    }

    @Test
    fun homeMenuListsGroupsInOrderWhateverTheEntryOrder() {
        val menu = HomeMenu(context)
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_SETTINGS, R.string.widgets_home_menu_settings, 1, group = HomeMenuGroup.SETTINGS) {})
        menu.addEntry(HomeMenuEntry("add_page", R.string.home_menu_add_page, 100, group = HomeMenuGroup.PAGES) {})
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WALLPAPERS, R.string.widgets_home_menu_wallpapers, 200) {})
        menu.addEntry(HomeMenuEntry(HomeMenu.ENTRY_WIDGETS, R.string.widgets_home_menu_widgets, 100) {})
        assertThat(menu.visibleEntries().map { it.id })
            .containsExactly(HomeMenu.ENTRY_WIDGETS, HomeMenu.ENTRY_WALLPAPERS, "add_page", HomeMenu.ENTRY_SETTINGS).inOrder()
    }

    private data class Item(val id: String, val z: Int, val widget: Boolean)

    private class WidgetMarker(context: Context) : View(context)

    private fun CellLayout.bindItems(items: List<Item>) = bind(
        items = items,
        id = { it.id },
        cellOf = { CellLayout.LayoutParams(0, 0, 2, 2).apply { zIndex = it.z } },
        create = { if (it.widget) WidgetMarker(context) else View(context) },
        update = { _, _ -> },
        recycle = {},
        canReuse = { view, item -> (view is WidgetMarker) == item.widget },
    )

    @Test
    fun cellLayoutDrawsOverlappingItemsByZIndex() {
        val layout = CellLayout(context)
        layout.bindItems(listOf(Item("top", 5, true), Item("bottom", 0, false), Item("middle", 2, false)))
        val order = (0 until layout.childCount).map { pos ->
            layout.getChildAt(layout.drawingOrderAt(pos)).getTag(R.id.home_item_id)
        }
        assertThat(order).containsExactly("bottom", "middle", "top").inOrder()

        // Raising z re-sorts without recreating views.
        val bottomView = layout.getChildAt(1)
        layout.bindItems(listOf(Item("top", 5, true), Item("bottom", 9, false), Item("middle", 2, false)))
        assertThat(layout.getChildAt(layout.drawingOrderAt(2))).isSameInstanceAs(bottomView)
    }

    @Test
    fun cellLayoutReplacesAViewThatCannotShowTheItem() {
        val layout = CellLayout(context)
        layout.bindItems(listOf(Item("a", 0, false)))
        val icon = layout.getChildAt(0)
        layout.bindItems(listOf(Item("a", 0, true)))
        assertThat(layout.childCount).isEqualTo(1)
        assertThat(layout.getChildAt(0)).isNotSameInstanceAs(icon)
        assertThat(layout.getChildAt(0)).isInstanceOf(WidgetMarker::class.java)
    }

    private fun CellLayout.drawingOrderAt(position: Int): Int = getChildDrawingOrder(position)
}
