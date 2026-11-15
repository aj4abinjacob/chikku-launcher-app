package io.github.aj4abinjacob.chikku.platform

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LauncherAppsRepositoryTest {

    private val personal = SourceProfile(null, 0L, ProfileType.PERSONAL)
    private val work = SourceProfile(null, 10L, ProfileType.WORK)

    private class FakeSource : AppsPlatformSource {
        var profiles = listOf<SourceProfile>()
        val activities = mutableMapOf<Long, List<RawActivity>>()
        var failProfiles: Exception? = null
        var failSerial: Long? = null
        var failObserve = false
        var enumerations = 0
        var listener: ((String?) -> Unit)? = null
        var closed = false

        override fun profiles(): List<SourceProfile> {
            enumerations++
            failProfiles?.let { throw it }
            return profiles
        }

        override fun activities(profile: SourceProfile): List<RawActivity> {
            if (profile.serial == failSerial) throw IllegalStateException("profile went away")
            return activities[profile.serial].orEmpty()
        }

        override fun observe(onEvent: (String?) -> Unit): AutoCloseable {
            if (failObserve) throw SecurityException("no callback")
            listener = onEvent
            return AutoCloseable { closed = true }
        }
    }

    private fun TestScope.repository(
        source: FakeSource,
        changed: MutableList<String> = mutableListOf(),
    ) = LauncherAppsRepository(
        source = source,
        scope = backgroundScope,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        onPackageChanged = { changed += it },
        collatorFactory = { Collator.getInstance(Locale.US).apply { strength = Collator.PRIMARY } },
        debounceMillis = 250,
    )

    private fun TestScope.collect(repo: LauncherAppsRepository): MutableList<AppsState> {
        val states = mutableListOf<AppsState>()
        backgroundScope.launch { repo.apps.toList(states) }
        runCurrent()
        return states
    }

    @Test
    fun emitsLoadingThenSortedAppsAcrossProfiles() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal, work)
            activities[0L] = listOf(
                RawActivity("com.b", "com.b.Main", "zebra"),
                RawActivity("com.a", "com.a.Main", "Éclair"),
                RawActivity("com.c", "com.c.Main", "apple"),
            )
            activities[10L] = listOf(RawActivity("com.c", "com.c.Main", "Apple"))
        }
        val states = collect(repository(source))

        assertThat(states.first()).isEqualTo(AppsState.Loading)
        val loaded = states.last() as AppsState.Loaded
        assertThat(loaded.apps.map { it.label to it.profileType }).containsExactly(
            "apple" to ProfileType.PERSONAL,
            "Apple" to ProfileType.WORK,
            "Éclair" to ProfileType.PERSONAL,
            "zebra" to ProfileType.PERSONAL,
        ).inOrder()
        assertThat(loaded.apps[1].key).isEqualTo(AppKey("com.c", "com.c.Main", 10L))
    }

    @Test
    fun blankOrNullLabelsFallBackToPackageAndInvalidComponentsAreSkipped() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal)
            activities[0L] = listOf(
                RawActivity("com.blank", "com.blank.Main", "   "),
                RawActivity("com.none", "com.none.Main", null),
                RawActivity("", "x.Main", "Broken"),
                RawActivity("com.dup", "com.dup.Main", "Dup"),
                RawActivity("com.dup", "com.dup.Main", "Dup"),
            )
        }
        val loaded = collect(repository(source)).last() as AppsState.Loaded
        assertThat(loaded.apps.map { it.label }).containsExactly("com.blank", "com.none", "Dup")
    }

    @Test
    fun enumerationFailureEmitsGenericFailedAndRefreshRetries() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal)
            activities[0L] = listOf(RawActivity("com.secret.pkg", "com.secret.pkg.Main", "Secret"))
            failProfiles = RuntimeException("com.secret.pkg exploded")
        }
        val repo = repository(source)
        val states = collect(repo)

        val failed = states.last() as AppsState.Failed
        assertThat(failed.reason).isEqualTo(LauncherAppsRepository.FAILURE_REASON)
        assertThat(failed.reason).doesNotContain("secret")

        source.failProfiles = null
        repo.refresh()
        advanceTimeBy(251)
        runCurrent()
        assertThat((states.last() as AppsState.Loaded).apps).hasSize(1)
    }

    @Test
    fun failingSecondaryProfileKeepsPersonalApps() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal, work)
            activities[0L] = listOf(RawActivity("com.a", "com.a.Main", "A"))
            failSerial = 10L
        }
        val loaded = collect(repository(source)).last() as AppsState.Loaded
        assertThat(loaded.apps.map { it.key.packageName }).containsExactly("com.a")
        // The failed profile is not "listed", so its workspace items are not shown as uninstalled.
        assertThat(loaded.listedProfiles).containsExactly(0L)
    }

    @Test
    fun burstOfEventsIsCoalescedAndPackagesAreReportedImmediately() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal)
            activities[0L] = listOf(RawActivity("com.a", "com.a.Main", "A"))
        }
        val changed = mutableListOf<String>()
        val states = collect(repository(source, changed))
        assertThat(source.enumerations).isEqualTo(1)

        source.activities[0L] = source.activities[0L]!! + RawActivity("com.b", "com.b.Main", "B")
        repeat(20) { source.listener!!.invoke("com.b") }
        source.listener!!.invoke(null)
        assertThat(changed).hasSize(20)

        advanceTimeBy(100)
        runCurrent()
        assertThat(source.enumerations).isEqualTo(1)
        advanceTimeBy(200)
        runCurrent()
        assertThat(source.enumerations).isEqualTo(2)
        assertThat((states.last() as AppsState.Loaded).apps.map { it.label }).containsExactly("A", "B").inOrder()
    }

    @Test fun profileTransitionClearsReplayBeforeTheDebouncedReload() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal, work)
            activities[0L] = listOf(RawActivity("personal", "Main", "Personal"))
            activities[10L] = listOf(RawActivity("work", "Main", "Work"))
        }
        val repo = repository(source)
        val states = collect(repo)
        assertThat((states.last() as AppsState.Loaded).apps).hasSize(2)
        source.profiles = listOf(personal)
        source.listener!!.invoke(null)
        runCurrent()
        assertThat(states.last()).isEqualTo(AppsState.Loading)
        advanceTimeBy(251); runCurrent()
        assertThat((states.last() as AppsState.Loaded).listedProfiles).containsExactly(0L)
        source.profiles = listOf(personal, work)
        source.listener!!.invoke(null)
        advanceTimeBy(251); runCurrent()
        assertThat((states.last() as AppsState.Loaded).apps).hasSize(2)
    }

    @Test
    fun ordinaryPackageEventsKeepThePreviousAppsUntilTheReloadFinishes() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal, work)
            activities[0L] = listOf(RawActivity("com.a", "com.a.Main", "A"))
            activities[10L] = listOf(RawActivity("com.w", "com.w.Main", "W"))
        }
        val states = collect(repository(source))
        val initial = states.size
        assertThat(states.last()).isInstanceOf(AppsState.Loaded::class.java)

        source.activities[0L] = source.activities[0L]!! + RawActivity("com.b", "com.b.Main", "B")
        source.listener!!.invoke("com.b")
        runCurrent()
        advanceTimeBy(100)
        runCurrent()
        // Mid-debounce: still the previous Loaded value, never Loading.
        assertThat(states).hasSize(initial)
        advanceTimeBy(200)
        runCurrent()
        assertThat(states.drop(initial - 1)).doesNotContain(AppsState.Loading)
        assertThat((states.last() as AppsState.Loaded).apps.map { it.label }).containsExactly("A", "B", "W")
    }

    @Test
    fun profileAvailabilityEventDropsHiddenProfileAppsImmediately() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal, work)
            activities[0L] = listOf(RawActivity("com.a", "com.a.Main", "A"))
            activities[10L] = listOf(RawActivity("com.w", "com.w.Main", "W"))
        }
        val states = collect(repository(source))
        source.profiles = listOf(personal)
        // Package-level availability callbacks name packages and then signal a null event.
        source.listener!!.invoke("com.w")
        source.listener!!.invoke(null)
        runCurrent()
        assertThat(states.last()).isEqualTo(AppsState.Loading)
        advanceTimeBy(251)
        runCurrent()
        assertThat((states.last() as AppsState.Loaded).apps.map { it.label }).containsExactly("A")
    }

    @Test
    fun observeFailureStillLoadsApps() = runTest {
        val source = FakeSource().apply {
            profiles = listOf(personal)
            activities[0L] = listOf(RawActivity("com.a", "com.a.Main", "A"))
            failObserve = true
        }
        assertThat(collect(repository(source)).last()).isInstanceOf(AppsState.Loaded::class.java)
    }

    @Test
    fun callbacksAreUnregisteredAfterLastCollectorStops() = runTest {
        val source = FakeSource().apply { profiles = listOf(personal) }
        val repo = repository(source)
        val job = backgroundScope.launch { repo.apps.collect {} }
        runCurrent()
        assertThat(source.listener).isNotNull()
        job.cancel()
        advanceTimeBy(LauncherAppsRepository.STOP_TIMEOUT_MILLIS + 1)
        runCurrent()
        assertThat(source.closed).isTrue()
    }
}
