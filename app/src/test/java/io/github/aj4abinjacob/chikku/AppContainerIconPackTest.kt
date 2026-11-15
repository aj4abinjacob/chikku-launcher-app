package io.github.aj4abinjacob.chikku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Icon packs are parsed once per process: every consumer gets the container's repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppContainerIconPackTest {

    @Test
    fun containerHandsOutOneIconPackRepository() {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())

        assertThat(container.iconPackRepository).isSameInstanceAs(container.iconPackRepository)
    }

    @Test
    fun invalidatingTheIconProviderDropsTheContainersCachedPack() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        // Any installed package parses as a pack (with an empty filter); the app's own is always present.
        val pkg = context.packageName
        assertThat(container.iconPackRepository.load(pkg)).isNotNull()

        container.iconProvider.invalidate(pkg)

        // Already dropped by the provider's invalidation: the provider uses this same repository.
        assertThat(container.iconPackRepository.invalidate(pkg)).isFalse()
    }

    @Test
    fun loadedPackStaysCachedWhenAnotherPackageIsInvalidated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        val pkg = context.packageName
        assertThat(container.iconPackRepository.load(pkg)).isNotNull()

        container.iconProvider.invalidate("sample.unrelated")

        assertThat(container.iconPackRepository.invalidate(pkg)).isTrue() // control: still cached until now
    }
}
