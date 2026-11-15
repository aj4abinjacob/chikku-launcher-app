package io.github.aj4abinjacob.chikku.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GestureActionTest {

    @Test
    fun everyActionRoundTrips() {
        val actions = GestureAction.simpleActions + listOf(
            GestureAction.LaunchApp(AppKey("com.example", "com.example.Main", 10)),
            GestureAction.LaunchShortcut("com.example", "id:with:colons/and/slashes", 0),
        )
        actions.forEach { action ->
            assertThat(GestureAction.decode(GestureAction.encode(action))).isEqualTo(action)
        }
    }

    @Test
    fun simpleActionsAreInPickerOrder() {
        assertThat(GestureAction.simpleActions).containsExactly(
            GestureAction.None,
            GestureAction.OpenDrawer, GestureAction.OpenSearch, GestureAction.OpenCards, GestureAction.GoToDefaultPage, GestureAction.ToggleDock,
            GestureAction.OpenLauncherSettings,
            GestureAction.ExpandNotifications, GestureAction.ExpandQuickSettings, GestureAction.Recents, GestureAction.LockScreen,
            GestureAction.Assistant, GestureAction.VoiceSearch,
        ).inOrder()
    }

    @Test
    fun encodedNamesAreStable() {
        assertThat(GestureAction.encode(GestureAction.OpenDrawer)).isEqualTo("open_drawer")
        assertThat(GestureAction.encode(GestureAction.None)).isEqualTo("none")
        assertThat(GestureAction.encode(GestureAction.OpenCards)).isEqualTo("open_cards")
        assertThat(GestureAction.decode("open_cards")).isEqualTo(GestureAction.OpenCards)
        assertThat(GestureAction.encode(GestureAction.LaunchApp(AppKey("p", "p.A", 3)))).isEqualTo("app:3:p/p.A")
        assertThat(GestureAction.encode(GestureAction.LaunchShortcut("p", "s", 0))).isEqualTo("shortcut:0:p:s")
    }

    @Test
    fun malformedInputDecodesToNullWithoutThrowing() {
        listOf(null, "", "bogus", "app:", "app:x:p/c", "app:1:p", "app:1:/c", "shortcut:1:p", "shortcut:z:p:s", "shortcut:1::s")
            .forEach { assertThat(GestureAction.decode(it)).isNull() }
        assertThat(GestureAction.decodeOrDefault("bogus", GestureAction.OpenSearch)).isEqualTo(GestureAction.OpenSearch)
    }
}
