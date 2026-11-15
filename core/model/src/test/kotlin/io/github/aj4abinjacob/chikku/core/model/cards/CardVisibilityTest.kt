package io.github.aj4abinjacob.chikku.core.model.cards

import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardVisibilityTest {
    private val key = AppKey("personal.app", "personal.app.Main", 0)
    private val app = LauncherApp(key, "Personal", ProfileType.PERSONAL)
    private val base = CardTargetSnapshot(listOf(app), emptySet(), 0)
    private val card = AutomationCard("card", "Private title", featured = CardFeatured.App(key))

    @Test fun visiblePersonalComponentIsAllowed() { assertTrue(CardVisibility.targetsAllowed(card, base)) }
    @Test fun hiddenFeaturedAppWithholdsWholeCard() {
        assertFalse(CardVisibility.targetsAllowed(card, base.copy(hidden = setOf(key))))
    }
    @Test fun hiddenActionWithholdsCardWhoseFeaturedAppIsVisible() {
        val other = key.copy(className = "personal.app.Other")
        val targets = base.copy(apps = listOf(app, app.copy(key = other)), hidden = setOf(other))
        assertFalse(CardVisibility.targetsAllowed(card.copy(actions = listOf(GestureAction.LaunchApp(other))), targets))
    }
    @Test fun missingComponentCannotUseSiblingActivityAsIdentity() {
        assertFalse(CardVisibility.targetsAllowed(card, base.copy(apps = listOf(app.copy(key = key.copy(className = "Other"))))))
    }
    @Test fun workPrivateAndUnknownPersonalIdentityAreExcluded() {
        ProfileType.entries.filter { it != ProfileType.PERSONAL }.forEach {
            assertFalse(CardVisibility.targetsAllowed(card, base.copy(apps = listOf(app.copy(profileType = it)))))
        }
        assertFalse(CardVisibility.targetsAllowed(card, base.copy(personalUserSerial = null)))
        assertFalse(CardVisibility.targetsAllowed(card, base.copy(personalUserSerial = 10)))
    }
    @Test fun shortcutsRequireResolvedExactIdAndVisiblePersonalOwner() {
        val target = CardFeatured.Shortcut(key.packageName, "compose", key.userSerial)
        val shortcutCard = card.copy(featured = target)
        assertFalse(CardVisibility.targetsAllowed(shortcutCard, base))
        assertTrue(CardVisibility.targetsAllowed(shortcutCard, base.copy(shortcuts = setOf(target))))
        assertFalse(CardVisibility.targetsAllowed(shortcutCard, base.copy(shortcuts = setOf(target.copy(shortcutId = "other")))))
        assertFalse(CardVisibility.targetsAllowed(shortcutCard, base.copy(shortcuts = setOf(target), hidden = setOf(key))))
    }
    @Test fun hiddenSiblingAlsoWithholdsPackageAddressedShortcut() {
        val target = CardFeatured.Shortcut(key.packageName, "compose", key.userSerial)
        assertFalse(CardVisibility.targetsAllowed(card.copy(featured = target),
            base.copy(shortcuts = setOf(target), hidden = setOf(key.copy(className = "Other")))))
    }
    @Test fun verifiedPersonalWidgetDoesNotNeedLauncherActivity() {
        val widget = CardFeatured.Widget("placement")
        val provider = ComponentKey("widget.only", "widget.only.Provider", 0)
        assertTrue(CardVisibility.targetsAllowed(card.copy(featured = widget),
            base.copy(apps = emptyList(), widgets = mapOf(widget.itemId to provider))))
    }
    @Test fun lostHiddenOrOtherProfileWidgetWithholdsWholeCard() {
        val widget = CardFeatured.Widget("placement")
        val provider = ComponentKey(key.packageName, "Provider", 0)
        assertFalse(CardVisibility.targetsAllowed(card.copy(featured = widget), base))
        assertFalse(CardVisibility.targetsAllowed(card.copy(featured = widget),
            base.copy(widgets = mapOf(widget.itemId to provider), hidden = setOf(key))))
        assertFalse(CardVisibility.targetsAllowed(card.copy(featured = widget),
            base.copy(widgets = mapOf(widget.itemId to provider.copy(userSerial = 10)))))
    }
    @Test fun shortcutButtonHasTheSamePrivacyRuleAsFeaturedShortcut() {
        val target = CardFeatured.Shortcut(key.packageName, "compose", 0)
        val withButton = card.copy(actions = listOf(GestureAction.LaunchShortcut(target.packageName, target.shortcutId, target.userSerial)))
        assertFalse(CardVisibility.targetsAllowed(withButton, base))
        assertTrue(CardVisibility.targetsAllowed(withButton, base.copy(shortcuts = setOf(target))))
    }
}
