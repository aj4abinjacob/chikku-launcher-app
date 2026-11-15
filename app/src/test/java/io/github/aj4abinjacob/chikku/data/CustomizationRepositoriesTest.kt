package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomizationRepositoriesTest {
    private lateinit var db: ChikkuDatabase
    private lateinit var customizations: RoomAppCustomizationRepository
    private lateinit var groups: RoomDrawerGroupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).allowMainThreadQueries().build()
        customizations = RoomAppCustomizationRepository(db, Dispatchers.IO)
        groups = RoomDrawerGroupRepository(db, Dispatchers.IO)
    }

    @After
    fun tearDown() = db.close()

    private val personal = AppKey("com.a", "com.a.Main", 0)
    private val work = AppKey("com.a", "com.a.Main", 10)

    @Test
    fun customizationsAreKeyedByProfileAndDefaultRowsAreDeleted() = runTest {
        assertThat(customizations.setHidden(personal, true)).isTrue()
        customizations.update(work) { it.copy(customLabel = "Work A", swipeUpAction = GestureAction.OpenSearch) }
        val map = customizations.customizations.first { it.size == 2 }
        assertThat(map.getValue(personal).hidden).isTrue()
        assertThat(map.getValue(work)).isEqualTo(AppCustomization(work, customLabel = "Work A", swipeUpAction = GestureAction.OpenSearch))

        customizations.setHidden(personal, false)
        assertThat(customizations.get(personal)).isNull()
        assertThat(db.customizationDao().all()).hasSize(1)

        assertThat(customizations.replaceAll(listOf(AppCustomization(personal, iconPackPackage = "p", iconDrawableName = "d")))).isTrue()
        assertThat(customizations.all()).containsExactly(AppCustomization(personal, iconPackPackage = "p", iconDrawableName = "d"))
    }

    @Test
    fun drawerGroupsKeepOrderAndMembers() = runTest {
        val social = groups.createGroup("Social")!!
        val games = groups.createGroup("Games", DrawerSortMode.MANUAL)!!
        groups.addMember(social, work)
        groups.addMember(social, personal)
        groups.addMember(social, personal) // duplicate ignored
        assertThat(groups.reorderGroups(listOf(games, social))).isTrue()
        assertThat(groups.reorderGroups(listOf(games))).isFalse()
        groups.renameGroup(games, "Play")

        val all = groups.groups.first { list -> list.size == 2 && list.first().name == "Play" }
        assertThat(all.map { it.id }).containsExactly(games, social).inOrder()
        assertThat(all[0].sortMode).isEqualTo(DrawerSortMode.MANUAL)
        assertThat(all[1].members).containsExactly(work, personal).inOrder()

        groups.removeMember(social, work)
        assertThat(groups.all()!!.single { it.id == social }.members).containsExactly(personal)
        assertThat(groups.deleteGroup(social)).isTrue()
        assertThat(db.drawerGroupDao().members()).isEmpty()
        assertThat(groups.setMembers("missing", listOf(personal))).isFalse()

        val backup = groups.all()!!
        groups.createGroup("Extra")
        assertThat(groups.replaceAll(backup)).isTrue()
        assertThat(groups.all()!!.map { it.name }).containsExactly("Play")
    }
}
