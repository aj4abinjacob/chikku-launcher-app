package io.github.aj4abinjacob.chikku.settings.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.core.model.cards.CardTargetSnapshot
import io.github.aj4abinjacob.chikku.platform.cards.CardValidation
import io.github.aj4abinjacob.chikku.settings.sections.cardPersonalApps
import io.github.aj4abinjacob.chikku.settings.sections.cardValidationRequest
import io.github.aj4abinjacob.chikku.settings.sections.cardVisibleTargets
import io.github.aj4abinjacob.chikku.settings.sections.cardWidgetLabel
import org.junit.Test

/**
 * The filters between a target validation result and what the card editor shows. A loosening here
 * would show the label of a hidden, app-locked or other-profile app. Synthetic data only.
 */
class CardSettingsPrivacyRulesTest {
    private fun app(name: String, serial: Long = 0L, profile: ProfileType = ProfileType.PERSONAL) =
        LauncherApp(AppKey("sample.$name", "sample.$name.Main", serial), name, profile)

    private fun snapshot(apps: List<LauncherApp>, personal: Long? = 0L, widgets: Map<String, ComponentKey> = emptyMap(),
        shortcuts: Set<CardFeatured.Shortcut> = emptySet()) =
        CardTargetSnapshot(apps, emptySet(), personal, shortcuts, widgets)

    private val provider = ComponentKey("sample.widgets", "sample.widgets.Provider", 0L)
    private fun widgetItem(id: String = "w1", target: ComponentKey = provider) =
        WorkspaceItem(id, CellPlacement(ContainerType.DESKTOP, 0, 0, 0, spanX = 2, spanY = 1), ItemTarget.Widget(7, target))

    // --- cardPersonalApps ---

    @Test
    fun anAppHiddenInCustomizationsIsDroppedFromThePersonalApps() {
        val shown = app("shown")
        val hiddenApp = app("secret")
        val result = cardPersonalApps(snapshot(listOf(shown, hiddenApp)), setOf(hiddenApp.key), listOf(shown, hiddenApp))
        assertThat(result).containsExactly(shown)
    }

    @Test
    fun anAppOutsideThePersonalProfileIsDropped() {
        val personal = app("personal")
        val work = app("work", serial = 10L, profile = ProfileType.WORK)
        // Right serial but a non-personal profile type, and personal type but another serial.
        val mislabelled = app("mislabelled", serial = 0L, profile = ProfileType.WORK)
        val otherSerial = app("otherSerial", serial = 10L)
        val all = listOf(personal, work, mislabelled, otherSerial)
        assertThat(cardPersonalApps(snapshot(all), emptySet(), all)).containsExactly(personal)
    }

    @Test
    fun anAppNoLongerInstalledIsDropped() {
        val kept = app("kept")
        val gone = app("gone")
        assertThat(cardPersonalApps(snapshot(listOf(kept, gone)), emptySet(), listOf(kept))).containsExactly(kept)
    }

    @Test
    fun noValidatedTargetsGivesNoPersonalApps() {
        assertThat(cardPersonalApps(null, emptySet(), listOf(app("a")))).isNull()
        // Without a personal profile nothing qualifies.
        assertThat(cardPersonalApps(snapshot(listOf(app("a")), personal = null), emptySet(), listOf(app("a")))).isEmpty()
    }

    // --- cardVisibleTargets ---

    @Test
    fun aShortcutOfAnAppLockedAppIsDroppedFromTheVisibleTargets() {
        val open = CardFeatured.Shortcut("sample.open", "s1", 0L)
        val locked = CardFeatured.Shortcut("sample.locked", "s2", 0L)
        val validated = snapshot(emptyList(), shortcuts = setOf(open, locked))
        val result = cardVisibleTargets(validated, emptyList(), emptySet(), emptyList()) { pkg, _ -> pkg != "sample.locked" }
        assertThat(result.shortcuts).containsExactly(open)
    }

    @Test
    fun theLockCheckSeesThePackageAndProfileOfEachShortcut() {
        val work = CardFeatured.Shortcut("sample.same", "s1", 10L)
        val personal = CardFeatured.Shortcut("sample.same", "s2", 0L)
        val asked = mutableListOf<Pair<String, Long>>()
        val result = cardVisibleTargets(snapshot(emptyList(), shortcuts = setOf(work, personal)), emptyList(), emptySet(), emptyList()) { pkg, serial ->
            asked += pkg to serial
            serial == 0L
        }
        assertThat(result.shortcuts).containsExactly(personal)
        assertThat(asked).containsExactly("sample.same" to 10L, "sample.same" to 0L)
    }

    @Test
    fun visibleTargetsTakeTheLiveAppsAndHiddenSetNotTheValidatedOnes() {
        val live = app("live")
        val stale = app("stale")
        val hiddenNow = setOf(stale.key)
        val result = cardVisibleTargets(snapshot(listOf(stale)), listOf(live), hiddenNow, emptyList()) { _, _ -> true }
        assertThat(result.apps).containsExactly(live)
        assertThat(result.hidden).isEqualTo(hiddenNow)
    }

    @Test
    fun aWidgetWhoseProviderChangedOrWhoseItemIsGoneIsDroppedFromTheVisibleTargets() {
        val other = ComponentKey("sample.widgets", "sample.widgets.Other", 0L)
        val validated = snapshot(emptyList(), widgets = mapOf("same" to provider, "changed" to provider, "removed" to provider))
        val items = listOf(widgetItem("same"), widgetItem("changed", target = other))
        val result = cardVisibleTargets(validated, emptyList(), emptySet(), items) { _, _ -> true }
        assertThat(result.widgets.keys).containsExactly("same")
    }

    // --- cardWidgetLabel ---

    private fun validation(widgets: Map<String, ComponentKey>, labels: Map<String, String>) =
        CardValidation(snapshot(emptyList(), widgets = widgets), widgetLabels = labels)

    @Test
    fun aWidgetOfTheSameProviderGetsItsLabel() {
        val choices = validation(mapOf("w1" to provider), mapOf("w1" to "Sample clock"))
        assertThat(cardWidgetLabel(widgetItem(), choices, emptySet())).isEqualTo("Sample clock")
    }

    @Test
    fun aWidgetWhoseProviderChangedGetsNoLabel() {
        val other = ComponentKey("sample.widgets", "sample.widgets.Other", 0L)
        val choices = validation(mapOf("w1" to other), mapOf("w1" to "Sample clock"))
        assertThat(cardWidgetLabel(widgetItem(), choices, emptySet())).isNull()
    }

    @Test
    fun aWidgetWhoseOwningAppIsHiddenGetsNoLabel() {
        val choices = validation(mapOf("w1" to provider), mapOf("w1" to "Sample clock"))
        assertThat(cardWidgetLabel(widgetItem(), choices, setOf("sample.widgets" to 0L))).isNull()
        // A hidden app of the same package in another profile does not hide this one.
        assertThat(cardWidgetLabel(widgetItem(), choices, setOf("sample.widgets" to 10L))).isEqualTo("Sample clock")
    }

    @Test
    fun noLabelWithoutAValidationResultAFoundLabelOrAWidgetItem() {
        assertThat(cardWidgetLabel(widgetItem(), null, emptySet())).isNull()
        assertThat(cardWidgetLabel(widgetItem(), validation(mapOf("w1" to provider), emptyMap()), emptySet())).isNull()
        val appItem = WorkspaceItem("a1", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(AppKey("sample.a", "sample.a.Main", 0L)))
        assertThat(cardWidgetLabel(appItem, validation(mapOf("a1" to provider), mapOf("a1" to "x")), emptySet())).isNull()
    }

    // --- cardValidationRequest ---

    @Test
    fun theValidationRequestProbesEveryAppAndPlacedWidgetThenKeepsTheSavedCards() {
        val apps = listOf(app("one"), app("two"))
        val saved = AutomationCard("saved", "Saved")
        val appItem = WorkspaceItem("a1", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(apps[0].key))
        val request = cardValidationRequest(apps, listOf(appItem, widgetItem("w9")), listOf(saved))
        assertThat(request.map { it.featured }).containsExactly(
            CardFeatured.App(apps[0].key), CardFeatured.App(apps[1].key), CardFeatured.Widget("w9"), saved.featured,
        ).inOrder()
        assertThat(request.last()).isSameInstanceAs(saved)
    }
}
