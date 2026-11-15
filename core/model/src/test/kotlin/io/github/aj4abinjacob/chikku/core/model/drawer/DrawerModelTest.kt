package io.github.aj4abinjacob.chikku.core.model.drawer

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import org.junit.Test

class DrawerModelTest {

    private fun app(label: String, serial: Long = 0L, profile: ProfileType = ProfileType.PERSONAL) =
        LauncherApp(AppKey("pkg.${label.lowercase()}", "pkg.${label.lowercase()}.Main", serial), label, profile)

    private val alpha = app("Alpha")
    private val beta = app("Beta")
    private val gamma = app("Gamma")
    private val workBeta = app("Beta", serial = 10L, profile = ProfileType.WORK)
    private val apps = listOf(alpha, beta, workBeta, gamma)

    @Test
    fun hiddenKeysUseFullIdentity() {
        val customizations = mapOf(
            beta.key to AppCustomization(beta.key, hidden = true),
            gamma.key to AppCustomization(gamma.key, customLabel = "G"),
        )
        val hidden = DrawerModel.hiddenKeys(customizations)
        assertThat(hidden).containsExactly(beta.key)
        // Same package in the work profile is a different app and stays visible.
        assertThat(DrawerModel.withoutHidden(apps, hidden)).containsExactly(alpha, workBeta, gamma).inOrder()
    }

    @Test
    fun withoutHiddenKeepsListedProfilesAndNonLoadedStates() {
        val state = AppsState.Loaded(apps, listedProfiles = setOf(0L, 10L))
        val filtered = DrawerModel.withoutHidden(state, setOf(alpha.key)) as AppsState.Loaded
        assertThat(filtered.apps).containsExactly(beta, workBeta, gamma).inOrder()
        assertThat(filtered.listedProfiles).containsExactly(0L, 10L)
        assertThat(DrawerModel.withoutHidden(AppsState.Loading, setOf(alpha.key))).isEqualTo(AppsState.Loading)
        assertThat(DrawerModel.withoutHidden(state, emptySet())).isSameInstanceAs(state)
    }

    @Test
    fun singleAllTabByDefault() {
        val tabs = DrawerModel.tabs(apps, emptyList(), DrawerSettings())
        assertThat(tabs).hasSize(1)
        assertThat(tabs[0].kind).isEqualTo(DrawerTabKind.ALL)
        assertThat(tabs[0].apps).isEqualTo(apps)
    }

    @Test
    fun workTabSplitsProfilesOnlyWhenWorkAppsExist() {
        val settings = DrawerSettings(showWorkTab = true)
        val tabs = DrawerModel.tabs(apps, emptyList(), settings)
        assertThat(tabs.map { it.kind }).containsExactly(DrawerTabKind.PERSONAL, DrawerTabKind.WORK).inOrder()
        assertThat(tabs[0].apps).containsExactly(alpha, beta, gamma).inOrder()
        assertThat(tabs[1].apps).containsExactly(workBeta)

        val personalOnly = DrawerModel.tabs(listOf(alpha, beta), emptyList(), settings)
        assertThat(personalOnly.map { it.kind }).containsExactly(DrawerTabKind.ALL)
    }

    @Test
    fun groupsAppearOnlyWhenEnabledInRankOrder() {
        val groups = listOf(
            DrawerGroup("g2", "Work stuff", rank = 1, members = listOf(workBeta.key)),
            DrawerGroup("g1", "Games", rank = 0, members = listOf(gamma.key, alpha.key)),
        )
        assertThat(DrawerModel.tabs(apps, groups, DrawerSettings())).hasSize(1)

        val tabs = DrawerModel.tabs(apps, groups, DrawerSettings(groupsEnabled = true))
        assertThat(tabs.map { it.id }).containsExactly("all", "group:g1", "group:g2").inOrder()
        assertThat(tabs[1].name).isEqualTo("Games")
        // Alphabetical groups keep the drawer's order, not the member order.
        assertThat(tabs[1].apps).containsExactly(alpha, gamma).inOrder()
    }

    @Test
    fun manualGroupFollowsMemberOrderAndSkipsMissingApps() {
        val missing = AppKey("pkg.gone", "pkg.gone.Main", 0L)
        val group = DrawerGroup("g", "G", 0, DrawerSortMode.MANUAL, listOf(gamma.key, missing, alpha.key, gamma.key))
        assertThat(DrawerModel.groupApps(apps, group)).containsExactly(gamma, alpha).inOrder()
    }

    @Test
    fun hiddenMembersAreNotShownInGroups() {
        val visible = DrawerModel.withoutHidden(apps, setOf(alpha.key))
        val group = DrawerGroup("g", "G", 0, members = listOf(alpha.key, beta.key))
        assertThat(DrawerModel.groupApps(visible, group)).containsExactly(beta)
    }

    @Test
    fun selectedIndexFallsBackToFirstTab() {
        val tabs = DrawerModel.tabs(apps, listOf(DrawerGroup("g", "G", 0)), DrawerSettings(groupsEnabled = true))
        assertThat(DrawerModel.selectedIndex(tabs, "group:g")).isEqualTo(1)
        assertThat(DrawerModel.selectedIndex(tabs, "group:deleted")).isEqualTo(0)
        assertThat(DrawerModel.selectedIndex(tabs, null)).isEqualTo(0)
    }

    @Test
    fun pagesChunkByGridAndNeverEmpty() {
        val many = (1..11).map { app("App$it") }
        val pages = DrawerModel.pages(many, columns = 2, rows = 3)
        assertThat(pages.map { it.size }).containsExactly(6, 5).inOrder()
        assertThat(DrawerModel.pages(emptyList(), 4, 5)).containsExactly(emptyList<LauncherApp>())
        assertThat(DrawerModel.pages(many, 0, 0).size).isEqualTo(11)
    }

    @Test
    fun sectionsBucketByFirstLetter() {
        val list = listOf(app("1Password"), app("alpha"), app("Apex"), app("  bravo"), app("Écran"), app(""))
        val sections = DrawerModel.sections(list)
        assertThat(sections).containsExactly(
            DrawerSection("#", 0),
            DrawerSection("A", 1),
            DrawerSection("B", 3),
            DrawerSection("É", 4),
        ).inOrder()
    }

    @Test
    fun columnsAutoFitsWidthWithinBounds() {
        assertThat(DrawerModel.columns(5, 1000, 100)).isEqualTo(5)
        assertThat(DrawerModel.columns(DrawerSettings.AUTO_COLUMNS, 1000, 200)).isEqualTo(5)
        assertThat(DrawerModel.columns(DrawerSettings.AUTO_COLUMNS, 100, 200)).isEqualTo(3)
        assertThat(DrawerModel.columns(DrawerSettings.AUTO_COLUMNS, 5000, 100)).isEqualTo(8)
        assertThat(DrawerModel.columns(DrawerSettings.AUTO_COLUMNS, 0, 100)).isEqualTo(3)
    }

    @Test
    fun openMethodRules() {
        assertThat(DrawerModel.allowsSwipeOpen(DrawerOpenMethod.SWIPE_UP)).isTrue()
        assertThat(DrawerModel.allowsSwipeOpen(DrawerOpenMethod.BOTH)).isTrue()
        assertThat(DrawerModel.allowsSwipeOpen(DrawerOpenMethod.BUTTON)).isFalse()
        assertThat(DrawerModel.showsOpenButton(DrawerOpenMethod.SWIPE_UP)).isFalse()
        assertThat(DrawerModel.showsOpenButton(DrawerOpenMethod.BUTTON)).isTrue()
        assertThat(DrawerModel.showsOpenButton(DrawerOpenMethod.BOTH)).isTrue()
        assertThat(DrawerModel.showsOpenButton(DrawerOpenMethod.SWIPE_UP, swipeUpOpensDrawer = false)).isTrue()
    }

    @Test
    fun backgroundColorAppliesAlphaToThemeOrCustomColor() {
        val theme = 0xFFFFFFFF.toInt()
        assertThat(DrawerModel.backgroundColor(theme, null, 1f)).isEqualTo(theme)
        assertThat(DrawerModel.backgroundColor(theme, null, 0f)).isEqualTo(0x00FFFFFF)
        assertThat(DrawerModel.backgroundColor(theme, 0xFF102030.toInt(), 0.5f)).isEqualTo(0x80102030.toInt())
        assertThat(DrawerModel.backgroundColor(theme, 0xFF102030.toInt(), Float.NaN)).isEqualTo(0xFF102030.toInt())
    }

    @Test
    fun lightContentOnDarkOrTransparentBackgrounds() {
        assertThat(DrawerModel.prefersLightContent(0xFF000000.toInt())).isTrue()
        assertThat(DrawerModel.prefersLightContent(0xFF1B1B1F.toInt())).isTrue()
        assertThat(DrawerModel.prefersLightContent(0xFFFFFFFF.toInt())).isFalse()
        assertThat(DrawerModel.prefersLightContent(0x10FFFFFF)).isTrue() // mostly wallpaper
    }
    @Test fun wideWorkTabStaysEmptyWithoutAddingAnyUnavailableProfileApps() {
        val settings = DrawerSettings(showWorkTab = true,
            tabStyle = io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle.SEGMENTED)
        val tabs = DrawerModel.tabs(listOf(alpha, beta), emptyList(), settings)
        assertThat(tabs.map { it.kind }).containsExactly(DrawerTabKind.PERSONAL, DrawerTabKind.WORK).inOrder()
        assertThat(tabs[0].apps).containsExactly(alpha, beta)
        assertThat(tabs[1].apps).isEmpty()
    }

    @Test
    fun listGroupsMarkTheFirstAppOfEachLetter() {
        val list = listOf(app("Airbnb"), app("Amazon"), app("Bank"), app("1DM+"))
        assertThat(DrawerModel.listGroups(list)).containsExactly(
            DrawerSection("A", 0), DrawerSection("B", 2), DrawerSection(DrawerModel.OTHER_SECTION, 3),
        ).inOrder()
    }

    @Test
    fun listGroupsAreEmptyWhenTheOrderIsNotGroupedByLetter() {
        assertThat(DrawerModel.listGroups(listOf(app("Airbnb"), app("Bank"), app("Amazon")))).isEmpty()
        assertThat(DrawerModel.listGroups(listOf(app("Airbnb"), app("Amazon")))).isEmpty()
        assertThat(DrawerModel.listGroups(emptyList())).isEmpty()
    }
}
