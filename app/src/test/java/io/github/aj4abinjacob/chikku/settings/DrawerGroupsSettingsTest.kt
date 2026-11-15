package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.settings.drawer.moveGroup
import org.junit.Test

class DrawerGroupsSettingsTest {

    @Test
    fun moveGroupSwapsWithNeighbourAndRejectsEdges() {
        val ids = listOf("a", "b", "c")
        assertThat(moveGroup(ids, "b", -1)).containsExactly("b", "a", "c").inOrder()
        assertThat(moveGroup(ids, "b", 1)).containsExactly("a", "c", "b").inOrder()
        assertThat(moveGroup(ids, "a", -1)).isNull()
        assertThat(moveGroup(ids, "c", 1)).isNull()
        assertThat(moveGroup(ids, "missing", 1)).isNull()
    }
}
