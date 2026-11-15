package io.github.aj4abinjacob.chikku.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.badges.badgeColor
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.BadgeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.home.folder.applyAccent
import io.github.aj4abinjacob.chikku.home.footprintAccent
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Chikku's surfaces never pick up the device skin (One UI, MIUI): themes resolve Chikku's own
 * widget styles and accent, and accent fallbacks never read the window theme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = TestHomeApplication::class)
class ChikkuThemeIsolationTest {
    private val app: Context = ApplicationProvider.getApplicationContext()

    private fun themed(style: Int, night: Boolean = false): Context {
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
        }
        return ContextThemeWrapper(app, style).apply { applyOverrideConfiguration(config) }
    }

    private fun Context.attr(attr: Int): TypedValue = TypedValue().also { assertThat(theme.resolveAttribute(attr, it, true)).isTrue() }

    @Test
    fun homeThemeUsesChikkuWidgetStylesAndAccent() {
        val home = themed(R.style.Theme_Chikku_Home)
        assertThat(home.attr(android.R.attr.borderlessButtonStyle).resourceId).isEqualTo(R.style.Widget_Chikku_Button_Borderless)
        assertThat(home.attr(android.R.attr.buttonStyle).resourceId).isEqualTo(R.style.Widget_Chikku_Button)
        assertThat(home.attr(android.R.attr.progressBarStyle).resourceId).isEqualTo(R.style.Widget_Chikku_ProgressBar)
        assertThat(home.attr(android.R.attr.textCursorDrawable).resourceId).isEqualTo(R.drawable.chikku_text_cursor)
        assertThat(home.attr(android.R.attr.colorAccent).data).isEqualTo(home.getColor(R.color.chikku_theme_accent))
        assertThat(home.attr(android.R.attr.colorControlActivated).data).isEqualTo(home.getColor(R.color.chikku_theme_accent))
        // HOME's window stays a transparent wallpaper window.
        assertThat(home.attr(android.R.attr.windowShowWallpaper).data).isNotEqualTo(0)
        assertThat(home.attr(android.R.attr.windowBackground).resourceId).isEqualTo(android.R.color.transparent)
    }

    @Test
    fun chikkuButtonStyleIsSentenceCaseWithChikkuRipple() {
        val button = Button(themed(R.style.Theme_Chikku_Home), null, android.R.attr.borderlessButtonStyle)
        assertThat(button.isAllCaps).isFalse()
        assertThat(button.background).isNotNull()
        val explicit = Button(themed(R.style.Theme_Chikku_Home), null, 0, R.style.Widget_Chikku_Button_Borderless)
        assertThat(explicit.isAllCaps).isFalse()
    }

    @Test
    fun settingsWindowMatchesSettingsSurfaceInBothModes() {
        val light = themed(R.style.Theme_Chikku)
        val dark = themed(R.style.Theme_Chikku, night = true)
        assertThat(light.getColor(R.color.chikku_settings_window)).isEqualTo(0xFFFAFAFA.toInt())
        assertThat(dark.getColor(R.color.chikku_settings_window)).isEqualTo(0xFF242424.toInt())
        assertThat(dark.attr(android.R.attr.windowBackground).resourceId).isEqualTo(R.color.chikku_settings_window)
        assertThat(light.getColor(R.color.chikku_theme_accent)).isEqualTo(CHIKKU_DEFAULT_ACCENT)
    }

    @Test
    fun translucentThemeStaysFullyTransparent() {
        val translucent = themed(R.style.Theme_Chikku_Translucent, night = true)
        assertThat(translucent.attr(android.R.attr.windowIsTranslucent).data).isNotEqualTo(0)
        assertThat(translucent.attr(android.R.attr.windowBackground).resourceId).isEqualTo(android.R.color.transparent)
    }

    @Test
    fun accentFallbacksUseChikkuAccentNeverTheThemes() {
        val none = LauncherSettings()
        assertThat(footprintAccent(none)).isEqualTo(CHIKKU_DEFAULT_ACCENT)
        assertThat(badgeColor(none)).isEqualTo(CHIKKU_DEFAULT_ACCENT)

        val accent = 0xFF00897B.toInt()
        val withAccent = none.copy(lookFeel = LookFeelSettings(accentColor = accent))
        assertThat(footprintAccent(withAccent)).isEqualTo(accent)
        assertThat(badgeColor(withAccent)).isEqualTo(accent)
        val dot = 0xFF00FF00.toInt()
        assertThat(badgeColor(withAccent.copy(badges = BadgeSettings(dotColor = dot)))).isEqualTo(dot)
    }

    @Test
    fun popupMenusUseChikkuAnimationOrNone() {
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.NORMAL))).isEqualTo(R.style.Animation_Chikku_Popup)
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.FAST))).isEqualTo(R.style.Animation_Chikku_Popup_Fast)
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.SLOW))).isEqualTo(R.style.Animation_Chikku_Popup_Slow)
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.RELAXED))).isEqualTo(R.style.Animation_Chikku_Popup_Relaxed)
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.NONE))).isEqualTo(0)
        assertThat(LauncherMenuStyle.popupAnimationStyle(Motion(AnimationSpeed.NORMAL, systemAnimationsEnabled = false))).isEqualTo(0)
    }

    @Test
    fun accentedFieldUsesAccentCursorAndSelection() {
        val field = EditText(themed(R.style.Theme_Chikku_Home))
        field.applyAccent(Color.RED)
        assertThat(field.highlightColor).isEqualTo((0x66 shl 24) or (Color.RED and 0xFFFFFF))
        assertThat(field.textCursorDrawable).isNotNull()
    }
}
