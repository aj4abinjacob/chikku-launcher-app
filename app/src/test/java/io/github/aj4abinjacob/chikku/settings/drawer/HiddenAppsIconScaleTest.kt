package io.github.aj4abinjacob.chikku.settings.drawer

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import org.junit.Test

class HiddenAppsIconScaleTest {
    @Test fun matchDesktopSizeUsesTheHomeIconScaleLikeTheRealDrawer() {
        val drawer = DrawerSettings(iconScale = 0.9f, matchDesktopSize = true)
        assertThat(drawer.withEffectiveIconScale(1.3f).iconScale).isEqualTo(1.3f)
        assertThat(drawer.copy(matchDesktopSize = false).withEffectiveIconScale(1.3f).iconScale).isEqualTo(0.9f)
    }
}
