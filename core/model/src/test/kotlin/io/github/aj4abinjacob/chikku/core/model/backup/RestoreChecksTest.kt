package io.github.aj4abinjacob.chikku.core.model.backup

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.DockSettings
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SearchSettings
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RestoreChecksTest {

    private class FakeEnv(
        var discover: DiscoverReadiness = DiscoverReadiness.READY,
        var packs: Set<String>? = emptySet(),
        var readable: Boolean? = true,
        var packages: Set<String>? = emptySet(),
    ) : RestoreEnvironment {
        var discoverAsked = 0
        override suspend fun discoverReadiness(): DiscoverReadiness = discover.also { discoverAsked++ }
        override suspend fun installedIconPacks(): Set<String>? = packs
        override suspend fun canReadContent(uri: String): Boolean? = readable
        var packagesAsked = 0
        override suspend fun installedPackages(packages: Set<String>): Set<String>? =
            this.packages?.intersect(packages).also { packagesAsked++ }
    }

    private val discoverOn = LauncherSettings(home = HomeSettings(googleDiscoverEnabled = true))

    private fun contacts(buttons: List<String>, enabled: Boolean = true) =
        LauncherSettings(search = SearchSettings(contactsEnabled = enabled, contactCardButtons = buttons))

    @Test
    fun missingAppForAChosenCardButtonIsReported() = runTest {
        val buttons = listOf("signal:message", "whatsapp:message", "phone:voice_call")
        assertThat(RestoreChecks.run(contacts(buttons), FakeEnv(packages = setOf("com.whatsapp"))))
            .containsExactly(RestoreAttention.CONTACT_SERVICE_APP_MISSING)
        assertThat(RestoreChecks.run(contacts(buttons), FakeEnv(packages = setOf("com.whatsapp", "org.thoughtcrime.securesms")))).isEmpty()
        assertThat(RestoreChecks.run(contacts(buttons, enabled = false), FakeEnv(packages = emptySet()))).isEmpty()
        assertThat(RestoreChecks.run(contacts(buttons), FakeEnv(packages = null))).isEmpty()
        // Phone-only cards need no app.
        assertThat(RestoreChecks.run(contacts(listOf("phone:voice_call", "sms:message")), FakeEnv(packages = emptySet()))).isEmpty()
    }

    @Test
    fun anUntouchedDefaultCardNeverWarnsOrProbes() = runTest {
        val env = FakeEnv(packages = emptySet())
        assertThat(RestoreChecks.run(contacts(SearchSettings().contactCardButtons), env)).isEmpty()
        assertThat(env.packagesAsked).isEqualTo(0)
    }

    @Test
    fun defaultSettingsNeedNothingAndDoNotProbeDiscover() = runTest {
        val env = FakeEnv(discover = DiscoverReadiness.NO_GOOGLE_APP, packs = null, readable = false)
        assertThat(RestoreChecks.run(LauncherSettings(), env)).isEmpty()
        assertThat(env.discoverAsked).isEqualTo(0)
    }

    @Test
    fun discoverWithoutGoogleAppOrBridgeIsReported() = runTest {
        assertThat(RestoreChecks.run(discoverOn, FakeEnv(discover = DiscoverReadiness.NO_GOOGLE_APP)))
            .containsExactly(RestoreAttention.DISCOVER_NEEDS_GOOGLE_APP)
        assertThat(RestoreChecks.run(discoverOn, FakeEnv(discover = DiscoverReadiness.NO_BRIDGE)))
            .containsExactly(RestoreAttention.DISCOVER_NEEDS_BRIDGE)
        assertThat(RestoreChecks.run(discoverOn, FakeEnv(discover = DiscoverReadiness.BRIDGE_NOT_WORKING)))
            .containsExactly(RestoreAttention.DISCOVER_BRIDGE_NOT_WORKING)
    }

    @Test
    fun readyOrUnknownDiscoverIsNotReported() = runTest {
        assertThat(RestoreChecks.run(discoverOn, FakeEnv(discover = DiscoverReadiness.READY))).isEmpty()
        assertThat(RestoreChecks.run(discoverOn, FakeEnv(discover = DiscoverReadiness.UNKNOWN))).isEmpty()
    }

    @Test
    fun missingIconPackIsReportedOnlyWhenTheListIsKnown() = runTest {
        val settings = LauncherSettings(lookFeel = LookFeelSettings(iconPackPackage = "com.pack"))
        assertThat(RestoreChecks.run(settings, FakeEnv(packs = setOf("com.other"))))
            .containsExactly(RestoreAttention.ICON_PACK_MISSING)
        assertThat(RestoreChecks.run(settings, FakeEnv(packs = setOf("com.pack")))).isEmpty()
        assertThat(RestoreChecks.run(settings, FakeEnv(packs = null))).isEmpty()
    }

    @Test
    fun unreadableDockImageIsReportedOnlyWhenKnown() = runTest {
        val settings = LauncherSettings(dock = DockSettings(backgroundImageUri = "content://x/1"))
        assertThat(RestoreChecks.run(settings, FakeEnv(readable = false)))
            .containsExactly(RestoreAttention.DOCK_IMAGE_UNREADABLE)
        assertThat(RestoreChecks.run(settings, FakeEnv(readable = null))).isEmpty()
    }

    @Test
    fun aFailingCheckDoesNotHideTheOthers() = runTest {
        val settings = discoverOn.copy(lookFeel = LookFeelSettings(iconPackPackage = "com.pack"))
        val broken = RestoreCheck { _, _ -> error("boom") }
        val result = RestoreChecks.run(settings, FakeEnv(discover = DiscoverReadiness.NO_BRIDGE), listOf(broken) + RestoreChecks.ALL)
        assertThat(result).containsExactly(RestoreAttention.DISCOVER_NEEDS_BRIDGE, RestoreAttention.ICON_PACK_MISSING).inOrder()
    }
}
