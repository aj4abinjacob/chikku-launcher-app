package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.GestureSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.settings.components.simpleGestureActionLabel
import io.github.aj4abinjacob.chikku.settings.sections.HomeGesture
import org.junit.Test

class GesturesSettingsTest {

    @Test
    fun everySimpleActionOfferedByThePickerHasALabel() {
        GestureAction.simpleActions.forEach { assertThat(simpleGestureActionLabel(it)).isNotNull() }
        assertThat(simpleGestureActionLabel(GestureAction.LaunchApp(AppKey("p", "p.Main", 0)))).isNull()
    }

    @Test
    fun eachHomeGestureRowWritesOnlyItsOwnField() {
        val picked = GestureAction.LaunchShortcut("com.example.dialer", "call:mum", 10)
        HomeGesture.entries.forEach { gesture ->
            val updated = gesture.set(GestureSettings(), picked)
            assertThat(gesture.get(updated)).isEqualTo(picked)
            HomeGesture.entries.filter { it != gesture }.forEach { other ->
                assertThat(other.get(updated)).isEqualTo(other.get(GestureSettings()))
            }
        }
    }

    @Test
    fun pickedActionsSurviveTheSettingsBackupFormat() {
        val gestures = GestureSettings(
            swipeUp = GestureAction.OpenSearch,
            swipeDown = GestureAction.ExpandNotifications,
            doubleTap = GestureAction.LockScreen,
            pinchIn = GestureAction.LaunchApp(AppKey("com.example.mail", "com.example.mail.Main", 10)),
            pinchOut = GestureAction.LaunchShortcut("com.example.dialer", "call:mum", 0),
            twoFingerSwipeUp = GestureAction.Recents,
            twoFingerSwipeDown = GestureAction.VoiceSearch,
            homeGesture = GestureAction.Assistant,
        )
        val settings = LauncherSettings(gestures = gestures)
        val decoded = LauncherSettingsCodec.decode(LauncherSettingsCodec.encode(settings))
        assertThat(decoded.gestures).isEqualTo(gestures)
    }
}
