package io.github.aj4abinjacob.chikku.search

import android.content.Intent
import androidx.activity.ComponentActivity
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.search.ContactActionType
import io.github.aj4abinjacob.chikku.core.model.search.ContactChannelAction
import io.github.aj4abinjacob.chikku.core.model.search.ContactHit
import io.github.aj4abinjacob.chikku.home.FakeHomeDependencies
import io.github.aj4abinjacob.chikku.home.TestHomeApplication
import io.github.aj4abinjacob.chikku.platform.ContactActionLauncher
import io.github.aj4abinjacob.chikku.platform.ContactActionResult
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = TestHomeApplication::class)
class ContactSessionOwnershipAuditTest {
    private val contact = ContactHit(7, "synthetic", "Synthetic")
    private val action = PersonAction.Service(
        ContactChannelAction("whatsapp", ContactActionType.MESSAGE, 8, "synthetic/type"),
    )
    private val ready = ContactActionLauncher.Prepared.Ready(Intent(), "com.whatsapp")

    @Test fun dismissalAndReplacementCannotLaunchOrCloseTheNewSessionEvenWithSlowCancellation() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val gate = CompletableDeferred<Unit>()
        var session = 1
        var starts = 0
        var completed = 0
        val actions = SearchResultActions(
            host.get(), FakeHomeDependencies(), AppUnlocker { _, _ -> },
            {
                completed++
                session = 0
            }, {}, isActive = { session == 1 },
            actionScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            prepareContact = { _, _ ->
                withContext(NonCancellable) {
                    gate.await()
                    ready
                }
            },
            startContact = {
                starts++
                ContactActionResult.Started
            }, openFolder = {},
        )
        try {
            actions.performPerson(contact, action)
            actions.close()
            session = 2
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertThat(starts).isEqualTo(0)
            assertThat(completed).isEqualTo(0)
            assertThat(session).isEqualTo(2)
        } finally {
            gate.complete(Unit)
            actions.close()
            host.pause().stop().destroy()
        }
    }

    @Test fun sessionIdentityAloneRejectsACompletedOldPreparation() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val gate = CompletableDeferred<Unit>()
        var currentSession = 1
        var starts = 0
        var completed = 0
        val actions =
            SearchResultActions(
                host.get(), FakeHomeDependencies(), AppUnlocker { _, _ -> }, { completed++ }, {},
                isActive = { currentSession == 1 },
                actionScope = CoroutineScope(
                    backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
                ),
                prepareContact = { _, _ ->
                    gate.await()
                    ready
                },
                startContact = {
                    starts++
                    ContactActionResult.Started
                }, openFolder = {},
            )
        try {
            actions.performPerson(contact, action)
            currentSession = 2 // deliberately do not cancel: freshness must independently fail closed
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertThat(starts).isEqualTo(0)
            assertThat(completed).isEqualTo(0)
        } finally {
            gate.complete(Unit)
            actions.close()
            host.pause().stop().destroy()
        }
    }

    @Test fun standaloneStopInvalidatesPreparationEvenAfterResume() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        val actions = SearchResultActions(
            host.get(), FakeHomeDependencies(), AppUnlocker { _, _ -> }, {}, {},
            actionScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            prepareContact = { _, _ ->
                withContext(NonCancellable) {
                    gate.await()
                    ready
                }
            },
            startContact = {
                starts++
                ContactActionResult.Started
            }, openFolder = {},
        )
        try {
            actions.performPerson(contact, action)
            host.pause().stop().start().resume()
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertThat(starts).isEqualTo(0)
        } finally {
            gate.complete(Unit)
            actions.close()
            host.pause().stop().destroy()
        }
    }

    @Test fun dismissedAuthenticationRetryDoesNotPrepareOrStartAgain() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        var retry: (() -> Unit)? = null
        var prepared = 0
        var starts = 0
        val actions = SearchResultActions(
            host.get(), FakeHomeDependencies(),
            AppUnlocker { _, callback ->
                retry =
                    callback
            },
            {}, {},
            actionScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            prepareContact = { _, _ ->
                prepared++
                ready
            },
            startContact = {
                starts++
                ContactActionResult.Locked(LaunchResult.Locked("com.whatsapp", 0))
            }, openFolder = {},
        )
        try {
            actions.performPerson(contact, action)
            assertThat(retry).isNotNull()
            actions.close()
            retry!!.invoke()
            assertThat(prepared).isEqualTo(1)
            assertThat(starts).isEqualTo(1)
        } finally {
            actions.close()
            host.pause().stop().destroy()
        }
    }

    @Test fun successfulStandaloneCredentialsRetryContactAfterStopAndResume() = runTest {
        for (successBeforeRestart in listOf(false, true)) {
            val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            var retry: (() -> Unit)? = null
            var starts = 0
            var completed = 0
            var unlocked = false
            val actions = SearchResultActions(
                host.get(), FakeHomeDependencies(),
                AppUnlocker { _, callback ->
                    retry =
                        callback
                },
                { completed++ }, {},
                actionScope = CoroutineScope(
                    backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
                ),
                prepareContact = { _, _ -> ready },
                startContact = {
                    starts++
                    if (unlocked) {
                        ContactActionResult.Started
                    } else {
                        ContactActionResult.Locked(
                            LaunchResult.Locked("com.whatsapp", 0),
                        )
                    }
                }, openFolder = {},
            )
            try {
                actions.performPerson(contact, action)
                assertThat(retry).isNotNull()
                host.pause().stop()
                unlocked = true
                if (successBeforeRestart) {
                    retry!!.invoke()
                    assertThat(starts).isEqualTo(1) // authentication success must wait for STARTED
                }
                host.start().resume()
                if (!successBeforeRestart) retry!!.invoke()
                testScheduler.runCurrent()
                assertThat(starts).isEqualTo(2)
                assertThat(completed).isEqualTo(1)
            } finally {
                actions.close()
                host.pause().stop().destroy()
            }
        }
    }

    @Test fun successfulStandaloneCredentialsRetryAppButClosedSessionDiscardsDeferredSuccess() = runTest {
        for (closeBeforeRestart in listOf(false, true)) {
            val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            val deps = FakeHomeDependencies()
            val app = io.github.aj4abinjacob.chikku.home.app("Synthetic")
            deps.appLauncher.result = LaunchResult.Locked(app.key.packageName, app.key.userSerial)
            var retry: (() -> Unit)? = null
            var completed = 0
            val actions = SearchResultActions(
                host.get(),
                deps,
                AppUnlocker { _, callback -> retry = callback },
                { completed++ },
                {},
                openFolder = {},
            )
            try {
                actions.perform(io.github.aj4abinjacob.chikku.core.model.search.SearchRow.App(app))
                host.pause().stop()
                deps.appLauncher.result = LaunchResult.Started
                retry!!.invoke()
                assertThat(deps.appLauncher.launched).hasSize(1)
                if (closeBeforeRestart) actions.close()
                host.start().resume()
                assertThat(deps.appLauncher.launched).hasSize(if (closeBeforeRestart) 1 else 2)
                assertThat(completed).isEqualTo(if (closeBeforeRestart) 0 else 1)
            } finally {
                actions.close()
                host.pause().stop().destroy()
            }
        }
    }

    @Test fun successfulStandaloneCredentialsRetryExactWebHandlerAfterStop() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = host.get()
        val component = android.content.ComponentName("example.browser", "example.browser.Handler")
        org.robolectric.Shadows.shadowOf(activity.packageManager).apply {
            addActivityIfNotPresent(component).apply {
                exported = true
                enabled = true
                applicationInfo.enabled = true
            }
            addIntentFilterForActivity(
                component,
                android.content.IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("https")
                },
            )
        }
        var unlocked = false
        var retry: (() -> Unit)? = null
        var completed = 0
        val launcher = io.github.aj4abinjacob.chikku.platform.GuardedIntentLauncher(
            activity,
            io.github.aj4abinjacob.chikku.platform.AppLockGuard { _, _ ->
                unlocked
            },
            kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val actions = SearchResultActions(
            activity,
            FakeHomeDependencies(),
            AppUnlocker { _, callback ->
                retry =
                    callback
            },
            { completed++ },
            {},
            intentLauncher = launcher,
            actionScope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            openFolder = {
            },
        )
        try {
            actions.perform(
                io.github.aj4abinjacob.chikku.core.model.search.SearchRow.WebSearch(
                    io.github.aj4abinjacob.chikku.core.model.settings.WebSearchEngine.GOOGLE,
                    "synthetic",
                ),
            )
            assertThat(retry).isNotNull()
            host.pause().stop()
            unlocked = true
            retry!!.invoke()
            assertThat(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity).isNull()
            host.start().resume()
            assertThat(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity.component).isEqualTo(component)
            assertThat(completed).isEqualTo(1)
        } finally {
            actions.close()
            host.pause().stop().destroy()
        }
    }

    @Test fun activeServiceStartsOnceAndCompletesItsOwnSession() = runTest {
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        var completed = 0
        val actions =
            SearchResultActions(
                host.get(), FakeHomeDependencies(), AppUnlocker { _, _ -> }, { completed++ }, {},
                actionScope = CoroutineScope(
                    backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler),
                ),
                prepareContact = { _, _ ->
                    gate.await()
                    ready
                },
                startContact = {
                    starts++
                    ContactActionResult.Started
                }, openFolder = {},
            )
        try {
            actions.performPerson(contact, action)
            actions.performPerson(contact, action)
            gate.complete(Unit)
            testScheduler.runCurrent()
            assertThat(starts).isEqualTo(1)
            assertThat(completed).isEqualTo(1)
        } finally {
            gate.complete(Unit)
            actions.close()
            host.pause().stop().destroy()
        }
    }
}
