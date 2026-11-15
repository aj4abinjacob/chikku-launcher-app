package io.github.aj4abinjacob.chikku.search

import io.github.aj4abinjacob.chikku.theme.withChikkuAccent
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.settings.components.CHIKKU_DEFAULT_ACCENT
import org.junit.Test

class SearchAccentAndNoticesTest {

    @Test
    fun unsetAccentUsesChikkuDefaultInsteadOfMaterialPurple() {
        val light = lightColorScheme().withChikkuAccent(accent = null, dark = false)
        assertThat(light.primary.toArgb()).isEqualTo(CHIKKU_DEFAULT_ACCENT)
        assertThat(light.primaryContainer.toArgb()).isEqualTo(CHIKKU_DEFAULT_ACCENT)
        // Lightened on dark surfaces, as in Chikku settings.
        val dark = darkColorScheme().withChikkuAccent(accent = null, dark = true)
        assertThat(dark.primary).isEqualTo(Color(0xFFFFAD89))
        assertThat(dark.primaryContainer.toArgb()).isEqualTo(CHIKKU_DEFAULT_ACCENT)
    }

    @Test
    fun userAccentDrivesPrimaryRolesAndKeepsSurfaces() {
        val base = darkColorScheme().copy(surface = Color.Black)
        val accent = 0x802196F3.toInt() // alpha is ignored
        val scheme = base.withChikkuAccent(accent, dark = true)
        assertThat(scheme.primary.toArgb()).isEqualTo(0xFF2196F3.toInt())
        assertThat(scheme.primaryContainer.toArgb()).isEqualTo(0xFF2196F3.toInt())
        assertThat(scheme.surface).isEqualTo(Color.Black)
        assertThat(scheme.onPrimary).isEqualTo(Color.Black)
    }

    @Test
    fun contrastRatioIsSymmetricAndBounded() {
        assertThat(contrastRatio(Color.Black, Color.White)).isWithin(0.01f).of(21f)
        assertThat(contrastRatio(Color.White, Color.Black)).isWithin(0.01f).of(21f)
        assertThat(contrastRatio(Color.Gray, Color.Gray)).isWithin(0.001f).of(1f)
    }

    @Test
    fun noticesReplaceAndClearOnlyTheShownOne() {
        val notices = SearchNotices()
        assertThat(notices.current.value).isNull()
        notices.show(R.string.search_open_failed)
        val first = notices.current.value!!
        notices.show(R.string.search_open_failed)
        val second = notices.current.value!!
        // Same text twice is a new notice, so it shows again.
        assertThat(second).isNotEqualTo(first)
        notices.shown(first)
        assertThat(notices.current.value).isEqualTo(second)
        notices.shown(second)
        assertThat(notices.current.value).isNull()
    }
}
