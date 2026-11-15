package io.github.aj4abinjacob.chikku.settings

import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerOpenMethod
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTransition
import io.github.aj4abinjacob.chikku.settings.previews.SyntheticPreviewIdentities
import io.github.aj4abinjacob.chikku.settings.previews.drawerReopenedPreviewPage
import io.github.aj4abinjacob.chikku.settings.previews.drawerAfterLaunchPreviewOpen
import io.github.aj4abinjacob.chikku.settings.previews.drawerOpeningPreviewChanged
import io.github.aj4abinjacob.chikku.settings.previews.drawerGroupsPreviewSceneHeight
import io.github.aj4abinjacob.chikku.settings.previews.syntheticMemberOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerFolderPreviewGapsTest {
    @Test fun drawerReopenReflectsRememberPositionWithoutDiscardingTheStoredSamplePosition() {
        assertEquals(3, drawerReopenedPreviewPage(3, true))
        assertEquals(0, drawerReopenedPreviewPage(3, false))
    }

    @Test fun sampleLaunchClosesOnlyWhenAutomaticallyCloseIsOn() {
        assertFalse(drawerAfterLaunchPreviewOpen(true))
        assertTrue(drawerAfterLaunchPreviewOpen(false))
    }

    @Test fun openingChoicesResetTheSampleWithoutResettingBehaviorDemonstrations() {
        val initial = DrawerSettings(openMethod = DrawerOpenMethod.BOTH, transition = DrawerTransition.SLIDE)
        assertTrue(drawerOpeningPreviewChanged(initial, initial.copy(openMethod = DrawerOpenMethod.BUTTON)))
        assertTrue(drawerOpeningPreviewChanged(initial, initial.copy(transition = DrawerTransition.FADE)))
        assertFalse(drawerOpeningPreviewChanged(initial, initial.copy(automaticallyClose = !initial.automaticallyClose)))
        assertFalse(drawerOpeningPreviewChanged(initial, initial.copy(rememberScrollPosition = !initial.rememberScrollPosition)))
    }

    @Test fun groupsSceneReservesFullCellHeightAtLargeFontsAndInLandscape() {
        val largeFont = drawerGroupsPreviewSceneHeight(800, 2f, 100f)
        val landscape = drawerGroupsPreviewSceneHeight(360, 2f, 100f)
        assertTrue(largeFont >= 176f)
        assertTrue(landscape >= 176f)
        assertEquals(156f, drawerGroupsPreviewSceneHeight(800, 1f, 76f), .01f)
    }

    @Test fun groupSamplesKeepTheirIdentityThroughReorderAdditionAndRemoval() {
        val identities = SyntheticPreviewIdentities<String>()
        assertEquals(listOf(0, 1, 2), identities.order(listOf("opaque-a", "opaque-b", "opaque-c")))
        assertEquals(listOf(2, 0, 1), identities.order(listOf("opaque-c", "opaque-a", "opaque-b")))
        assertEquals(listOf(2, 3), identities.order(listOf("opaque-c", "opaque-d")))
        assertEquals(listOf(1, 2), identities.order(listOf("opaque-b", "opaque-c")))
    }

    @Test fun alphabeticalSampleSortingAndManualReorderUseTheSameMembers() {
        val manual = listOf(0, 1, 2, 3)
        assertEquals(manual, syntheticMemberOrder(manual, DrawerSortMode.MANUAL))
        assertEquals(listOf(1, 2, 3, 0), syntheticMemberOrder(manual, DrawerSortMode.ALPHABETICAL))
        val moved = listOf(3, 0, 2, 1)
        assertEquals(moved, syntheticMemberOrder(moved, DrawerSortMode.MANUAL))
        assertEquals(listOf(1, 2, 3, 0), syntheticMemberOrder(moved, DrawerSortMode.ALPHABETICAL))
        assertEquals(emptyList<Int>(), syntheticMemberOrder(emptyList(), DrawerSortMode.ALPHABETICAL))
    }
}
