package io.github.aj4abinjacob.chikku.settings.iconpicker

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.icons.IconPackInfo
import org.junit.Test

/** Pure decisions behind the icon picker: captions, search and the stored override. */
class IconPickerLogicTest {

    private val key = AppKey("com.example", "com.example.Main", 0L)

    @Test
    fun captionsReadAsWords() {
        assertThat(iconLabel("google_maps")).isEqualTo("Google maps")
        assertThat(iconLabel("_camera")).isEqualTo("Camera")
        assertThat(iconLabel("x")).isEqualTo("X")
    }

    @Test
    fun searchIgnoresCaseAndUnderscores() {
        val names = listOf("google_maps", "gmail", "maps_alt")
        assertThat(filterIconNames(names, "  ")).isEqualTo(names)
        assertThat(filterIconNames(names, "MAPS_ALT")).containsExactly("maps_alt")
        assertThat(filterIconNames(names, "nothing")).isEmpty()
    }

    @Test
    fun overrideNeedsBothPackAndDrawable() {
        assertThat(AppCustomization(key).toOverride()).isNull()
        assertThat(AppCustomization(key, iconPackPackage = "pack").toOverride()).isNull()
        assertThat(AppCustomization(key, iconPackPackage = " ", iconDrawableName = "a").toOverride()).isNull()
        assertThat(AppCustomization(key, iconPackPackage = "pack", iconDrawableName = "maps").toOverride())
            .isEqualTo(CurrentOverride("pack", "maps"))
    }

    @Test
    fun packSearchMatchesLabelOrPackageIgnoringCase() {
        val packs = listOf(
            IconPackInfo("com.example.whicons", "Whicons"),
            IconPackInfo("org.sample.lines", "Line Icons"),
            IconPackInfo("net.pack.delta", "Delta"),
        )
        assertThat(filterPacks(packs, " ")).isEqualTo(packs)
        assertThat(filterPacks(packs, "ICONS").map { it.label }).containsExactly("Whicons", "Line Icons").inOrder()
        assertThat(filterPacks(packs, "net.pack")).containsExactly(packs[2])
        assertThat(filterPacks(packs, "zzz")).isEmpty()
    }

    @Test
    fun samplesPreferEverydayAppsThenFillFromTheMapping() {
        val components = linkedMapOf(
            "com.other.app/.Main" to "other",
            "com.android.settings/.Settings" to "settings",
            "com.android.settings/.Alias" to "settings_alt",
            "com.android.dialer/.Dialer" to "phone",
            "com.android.chrome/.Main" to "browser",
            "com.more.app/.Main" to "more",
        )
        // Phone, browser and settings come first in a fixed order, so every pack row shows the same apps.
        assertThat(sampleDrawableNames(components, 4)).containsExactly("phone", "browser", "settings", "other").inOrder()
        assertThat(sampleDrawableNames(components, 0)).isEmpty()
        assertThat(sampleDrawableNames(emptyMap(), 5)).isEmpty()
        // Duplicate drawables are offered once.
        assertThat(sampleDrawableNames(mapOf("a/.A" to "x", "b/.B" to "x"), 5)).containsExactly("x")
    }
}
