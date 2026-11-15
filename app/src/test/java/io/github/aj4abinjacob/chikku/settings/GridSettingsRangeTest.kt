package io.github.aj4abinjacob.chikku.settings

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GridSpec
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.sections.gridRange
import org.junit.Test

class GridSettingsRangeTest {
    @Test fun normalSizesUseUsualRangeWhileSavedExtendedSizeRemainsVisible() {
        val settings = LauncherSettings()
        assertThat(gridRange(settings, 5).last).isEqualTo(8)
        assertThat(gridRange(settings, 10).last).isEqualTo(10)
        assertThat(gridRange(settings, GridSpec.MAX_DIMENSION).last).isEqualTo(GridSpec.MAX_DIMENSION)
    }

    @Test fun turningOffExtendedGridDoesNotMisrepresentSavedSizes() {
        val settings = LauncherSettings().let { it.copy(labs = it.labs.copy(extendedGrid = true)) }
        assertThat(gridRange(settings, 5).last).isEqualTo(GridSpec.MAX_DIMENSION)
        val disabled = settings.copy(labs = settings.labs.copy(extendedGrid = false))
        assertThat(10 in gridRange(disabled, 10)).isTrue()
        assertThat(gridRange(disabled, 5).last).isEqualTo(8)
    }

    @Test fun savedSizeNeverExpandsBeyondStorageBoundary() {
        assertThat(gridRange(LauncherSettings(), Int.MAX_VALUE).last).isEqualTo(GridSpec.MAX_DIMENSION)
        assertThat(gridRange(LauncherSettings(), -1).last).isEqualTo(8)
    }
}
