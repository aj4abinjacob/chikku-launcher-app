package io.github.aj4abinjacob.chikku.platform.icons

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalendarIconMetadataTest {
    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun absentMalformedAndOutOfRangeDailyIconsFallBack() {
        assertThat(calendarIcon(resources, null, "example", 11)).isNull()
        val metadata = Bundle().apply { putInt("example.dynamic_icons", R.string.app_name) }
        assertThat(calendarIcon(resources, metadata, "example", 11)).isNull()
        assertThat(calendarIcon(resources, metadata, "example", 0)).isNull()
        assertThat(calendarIcon(resources, metadata, "example", 32)).isNull()
        assertThat(calendarIcon(resources, metadata, "other", 11)).isNull()
    }
}
