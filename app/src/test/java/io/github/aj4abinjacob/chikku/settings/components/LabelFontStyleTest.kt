package io.github.aj4abinjacob.chikku.settings.components

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockLabelFont
import io.github.aj4abinjacob.chikku.home.LabelFontSpec
import io.github.aj4abinjacob.chikku.home.labelFontSpec
import org.junit.Test

/** The settings font chooser draws each option in the same face home's labels use. */
class LabelFontStyleTest {

    @Test
    fun specMatchesTheSystemFamiliesHomeDrawsWith() {
        assertThat(DockLabelFont.entries.associateWith { it.labelFontSpec() }).containsExactly(
            DockLabelFont.NORMAL, LabelFontSpec("sans-serif", 400),
            DockLabelFont.MEDIUM, LabelFontSpec("sans-serif-medium", 500),
            DockLabelFont.CONDENSED, LabelFontSpec("sans-serif-condensed", 400),
            DockLabelFont.LIGHT, LabelFontSpec("sans-serif-light", 300),
        )
    }

    @Test
    fun composeStyleUsesTheSameFamilyAndWeightAsHome() {
        DockLabelFont.entries.forEach { font ->
            val spec = font.labelFontSpec()
            val style = labelFontTextStyle(font)
            assertThat(style.fontWeight).isEqualTo(FontWeight(spec.weight))
            val family = style.fontFamily as FontListFontFamily
            assertThat(family.fonts).hasSize(1)
            assertThat(family.fonts.single().weight).isEqualTo(FontWeight(spec.weight))
            assertThat(family).isEqualTo(FontFamily(Font(DeviceFontFamilyName(spec.familyName), FontWeight(spec.weight))))
        }
    }

    @Test
    fun everyOptionHasADistinctStyle() {
        assertThat(DockLabelFont.entries.map { labelFontTextStyle(it) }.toSet()).hasSize(DockLabelFont.entries.size)
    }
}
