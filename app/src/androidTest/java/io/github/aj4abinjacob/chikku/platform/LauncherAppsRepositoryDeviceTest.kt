package io.github.aj4abinjacob.chikku.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.DeviceTestSupport.targetContext
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.DefaultAppSearcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real LauncherApps enumeration on the device (not shadows). Asserts only on Chikku's own launcher
 * entry, so it does not depend on which third-party apps are installed.
 */
@RunWith(AndroidJUnit4::class)
class LauncherAppsRepositoryDeviceTest {
    private lateinit var scope: CoroutineScope
    private lateinit var repository: LauncherAppsRepository

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        repository = LauncherAppsRepository(targetContext, scope, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun loaded(): AppsState.Loaded = runBlocking {
        withTimeout(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            val state = repository.apps.first { it is AppsState.Loaded || it is AppsState.Failed }
            check(state is AppsState.Loaded) { "Apps failed to load: $state" }
            state
        }
    }

    @Test
    fun loadsChikkuSettingsWithPersonalProfileIdentity() {
        val state = loaded()
        val settings = state.apps.firstOrNull { it.key == DeviceTestSupport.settingsKey() }

        assertThat(settings).isNotNull()
        assertThat(settings!!.profileType).isEqualTo(ProfileType.PERSONAL)
        assertThat(settings.label).isNotEmpty()
        assertThat(state.listedProfiles).contains(DeviceTestSupport.personalSerial())
        // Identity is component + profile: the HOME activity is not a launcher entry.
        assertThat(state.apps.map { it.key.className })
            .doesNotContain(io.github.aj4abinjacob.chikku.home.HomeActivity::class.java.name)
    }

    @Test
    fun appsAreUniqueAndSortedDeterministically() {
        val state = loaded()

        assertThat(state.apps.map { it.key }.toSet()).hasSize(state.apps.size)
        assertThat(state.apps).isEqualTo(sortForDisplay(state.apps, displayCollator()))
        assertThat(sortForDisplay(state.apps.reversed(), displayCollator())).isEqualTo(state.apps)
        // Every listed app belongs to a profile reported as listed.
        assertThat(state.listedProfiles).containsAtLeastElementsIn(state.apps.map { it.key.userSerial }.toSet())
    }

    @Test
    fun refreshEmitsAFreshSnapshot() {
        // Keep a collector alive so the refresh reaches the shared upstream.
        val collected = CopyOnWriteArrayList<AppsState.Loaded>()
        val job = scope.launch { repository.apps.filterIsInstance<AppsState.Loaded>().collect { collected += it } }
        try {
            assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) { collected.isNotEmpty() }).isTrue()
            repository.refresh()
            assertThat(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) { collected.size >= 2 }).isTrue()
            assertThat(collected.last().apps.map { it.key }).contains(DeviceTestSupport.settingsKey())
        } finally {
            job.cancel()
        }
    }

    @Test
    fun defaultSearcherFindsChikkuSettingsByLabelPrefix() {
        val state = loaded()
        val label = state.apps.first { it.key == DeviceTestSupport.settingsKey() }.label
        val searcher = DefaultAppSearcher()

        for (prefixLength in listOf(minOf(3, label.length), minOf(6, label.length), label.length)) {
            val query = label.substring(0, prefixLength)
            val results = searcher.search(state.apps, query)
            assertThat(results.map { it.key }).contains(DeviceTestSupport.settingsKey())
        }
        // Full label, case-changed: exact match ranks first.
        val exact = searcher.search(state.apps, label.uppercase())
        assertThat(exact.first().label.lowercase()).isEqualTo(label.lowercase())
    }
}
