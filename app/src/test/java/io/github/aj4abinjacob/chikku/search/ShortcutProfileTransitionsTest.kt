package io.github.aj4abinjacob.chikku.search

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutHit
import io.github.aj4abinjacob.chikku.core.model.search.ShortcutLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ShortcutProfileTransitionsTest {
    private val personal = LauncherApp(AppKey("synthetic.player", "Player", 0), "Player", ProfileType.PERSONAL)
    private val work = personal.copy(key = personal.key.copy(userSerial = 10), profileType = ProfileType.WORK)
    private class Platform : ShortcutPlatform {
        var host = true
        val available = mutableSetOf(0L, 10L)
        var reads = 0
        var afterRead: () -> Unit = {}
        var failure: RuntimeException? = null
        override fun hasHostPermission() = host
        override fun isProfileAvailable(serial: Long): Boolean { failure?.let { throw it }; return serial in available }
        override fun read(serial: Long, owners: List<LauncherApp>): List<ShortcutHit> {
            reads++; afterRead()
            return owners.map { ShortcutHit(it.key.packageName, "play", serial, "Play", it.key) }
        }
    }

    @Test fun cachedResultsRespectProfileLockRemovalAndUnlockWithoutWaitingForAppEnumeration() = runTest {
        val platform = Platform()
        val source = LauncherAppsShortcutSource(platform, Dispatchers.Unconfined)
        val apps = listOf(personal, work)
        assertThat((source.shortcuts(apps) as ShortcutLookup.Found).shortcuts).hasSize(2)
        source.shortcuts(apps)
        assertThat(platform.reads).isEqualTo(2) // unchanged cache; state checks still execute
        platform.available.remove(10L)
        assertThat((source.shortcuts(apps) as ShortcutLookup.Found).shortcuts.map { it.userSerial }).containsExactly(0L)
        platform.available.add(10L)
        assertThat((source.shortcuts(apps) as ShortcutLookup.Found).shortcuts.map { it.userSerial }).containsExactly(0L, 10L)
    }

    @Test fun cachedResultsNeverSurviveHomeRoleLossAndUnknownProfileState() = runTest {
        val platform = Platform()
        val source = LauncherAppsShortcutSource(platform, Dispatchers.Unconfined)
        source.shortcuts(listOf(work))
        platform.host = false
        assertThat(source.shortcuts(listOf(work))).isEqualTo(ShortcutLookup.NotDefaultHome)
        platform.host = true
        platform.failure = IllegalArgumentException("synthetic binder failure")
        assertThat(source.shortcuts(listOf(work))).isEqualTo(ShortcutLookup.Unavailable)
        platform.failure = null
        assertThat((source.shortcuts(listOf(work)) as ShortcutLookup.Found).shortcuts).hasSize(1)
    }

    @Test fun lockOrRoleChangeDuringReadWithholdsTheReturnedMetadata() = runTest {
        val platform = Platform()
        val source = LauncherAppsShortcutSource(platform, Dispatchers.Unconfined)
        platform.afterRead = { platform.available.clear() }
        assertThat((source.shortcuts(listOf(work)) as ShortcutLookup.Found).shortcuts).isEmpty()
        platform.available.add(10L)
        platform.afterRead = { platform.host = false }
        assertThat(source.shortcuts(listOf(work))).isEqualTo(ShortcutLookup.NotDefaultHome)
    }

    @Test fun invalidateMakesTheNextLookupReadShortcutsAgainInsteadOfServingThe30SecondCache() = runTest {
        val platform = Platform()
        val source = LauncherAppsShortcutSource(platform, Dispatchers.Unconfined)
        source.shortcuts(listOf(work))
        source.shortcuts(listOf(work))
        assertThat(platform.reads).isEqualTo(1)
        source.invalidate()
        source.shortcuts(listOf(work))
        assertThat(platform.reads).isEqualTo(2)
    }
}
