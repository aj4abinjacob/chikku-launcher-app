package io.github.aj4abinjacob.chikku.settings.components

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.platform.IconProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The shared app-icon loader: decodes a bitmap, shows nothing on failure, never swallows cancellation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppIconImageTest {
    private val key = AppKey("com.example.maps", "com.example.maps.Main", 0L)

    private class Provider(val onIcon: () -> Drawable) : IconProvider {
        override suspend fun icon(key: AppKey): Drawable = onIcon()
        override fun invalidate(packageName: String) = Unit
    }

    @Test
    fun decodesTheIconAtTheRequestedSize() = runBlocking {
        val bitmap = loadAppIcon(Provider { ColorDrawable(Color.RED) }, key, 24)
        assertThat(bitmap).isNotNull()
        assertThat(bitmap!!.width).isEqualTo(24)
        assertThat(bitmap.height).isEqualTo(24)
    }

    @Test
    fun aFailingProviderShowsNothing() = runBlocking {
        assertThat(loadAppIcon(Provider { throw IllegalStateException("gone") }, key, 24)).isNull()
    }

    @Test
    fun cancellationIsRethrown() {
        assertThrows(CancellationException::class.java) {
            runBlocking { loadAppIcon(Provider { throw CancellationException("cancelled") }, key, 24) }
        }
    }
}
