package io.github.aj4abinjacob.chikku.home.cards.widget

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBinding
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingPhase
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingState
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore
import io.github.aj4abinjacob.chikku.data.cards.MemoryCardWidgetBindingStore
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CardWidgetBindCoordinatorTest {
    private val provider = ComponentKey("widget.app", "widget.app.Provider", 0)
    private val request = CardWidgetRequest("weather", "original-instance", provider)
    private val platform = FakePlatform()
    private val store = MemoryCardWidgetBindingStore()
    private var current: CardWidgetRequest? = request
    private fun flow(storage: CardWidgetBindingStore = store) = CardWidgetBindCoordinator(storage, platform) { it == current }
    private suspend fun saved() = (store.load() as CardWidgetBindingState.Ready).bindings

    @Test fun pendingConsentIsDurableBeforeBindingIsAttempted() = runTest {
        platform.onBind = { id ->
            assertThat(saved().getValue(request.cardId).appWidgetId).isEqualTo(id)
            assertThat(saved().getValue(request.cardId).phase).isEqualTo(CardWidgetBindingPhase.CONSENT)
        }
        val pending = flow().begin(request) as CardWidgetStep.Consent
        assertThat(saved().getValue(request.cardId)).isEqualTo(pending.binding)
        assertThat(platform.deleted).isEmpty()
    }

    @Test fun consentCancellationReleasesOnlyTheNewDedicatedId() = runTest {
        platform.foreignIds += 7 // A workspace host ID: deliberately not owned by this host.
        val coordinator = flow()
        val pending = coordinator.begin(request) as CardWidgetStep.Consent
        coordinator.consentResult(pending.binding, false)
        assertThat(saved()).isEmpty()
        assertThat(platform.deleted).containsExactly(pending.binding.appWidgetId)
        assertThat(platform.foreignIds).contains(7)
    }

    @Test fun consentWithoutTheExactBoundProviderIsRejected() = runTest {
        val coordinator = flow()
        val pending = coordinator.begin(request) as CardWidgetStep.Consent
        platform.bound[pending.binding.appWidgetId] = provider.copy(className = "Other.Provider")
        assertThat(coordinator.consentResult(pending.binding, true)).isEqualTo(CardWidgetStep.Rejected)
        assertThat(saved()).isEmpty()
    }

    @Test fun configurationIsPersistedAndCancelledWithoutTouchingWorkspaceIds() = runTest {
        platform.bindAllowed = true
        platform.configurationRequired = true
        platform.foreignIds += 2
        val coordinator = flow()
        val pending = coordinator.begin(request) as CardWidgetStep.Configure
        assertThat(saved().getValue(request.cardId).phase).isEqualTo(CardWidgetBindingPhase.CONFIGURATION)
        coordinator.configurationResult(pending.binding, false)
        assertThat(platform.deleted).containsExactly(pending.binding.appWidgetId)
        assertThat(platform.foreignIds).contains(2)
    }

    @Test fun staleConfigurationAfterCardReplacementCannotCommitTheNewInstance() = runTest {
        platform.bindAllowed = true; platform.configurationRequired = true
        val coordinator = flow()
        val old = coordinator.begin(request) as CardWidgetStep.Configure
        val replacement = request.copy(instanceId = "restored-instance")
        current = replacement
        coordinator.reconcile(mapOf(request.cardId to replacement))
        val new = coordinator.begin(replacement) as CardWidgetStep.Configure
        coordinator.configurationResult(old.binding, true)
        assertThat(saved().getValue(request.cardId)).isEqualTo(new.binding)
        assertThat(platform.deleted).containsExactly(old.binding.appWidgetId)
    }

    @Test fun recreatedCoordinatorKeepsPendingIdAndResumesConsentWithoutAllocatingAgain() = runTest {
        val first = flow().begin(request) as CardWidgetStep.Consent
        val recreated = flow()
        recreated.reconcile(mapOf(request.cardId to request))
        val resumed = recreated.begin(request) as CardWidgetStep.Consent
        assertThat(resumed.binding).isEqualTo(first.binding)
        assertThat(platform.allocations).isEqualTo(1)
        assertThat(platform.deleted).isEmpty()
    }

    @Test fun providerLossKeepsBindingAndFreshRebindDeletesOldOnlyAfterCommit() = runTest {
        platform.bindAllowed = true
        val coordinator = flow()
        val first = coordinator.begin(request) as CardWidgetStep.Bound
        platform.available = false; platform.bound.remove(first.binding.appWidgetId)
        assertThat(coordinator.begin(request)).isEqualTo(CardWidgetStep.Rejected)
        assertThat(saved().getValue(request.cardId)).isEqualTo(first.binding)
        platform.available = true
        val rebound = coordinator.begin(request) as CardWidgetStep.Bound
        assertThat(rebound.binding.appWidgetId).isNotEqualTo(first.binding.appWidgetId)
        assertThat(platform.deleted).containsExactly(first.binding.appWidgetId)
    }

    @Test fun restoreInstanceIdentityForcesFreshIdAndConsent() = runTest {
        platform.bindAllowed = true
        val coordinator = flow()
        val first = coordinator.begin(request) as CardWidgetStep.Bound
        current = request.copy(instanceId = "new-portable-instance")
        coordinator.reconcile(mapOf(request.cardId to current!!))
        platform.bindAllowed = false
        val restored = coordinator.begin(current!!) as CardWidgetStep.Consent
        assertThat(restored.binding.appWidgetId).isNotEqualTo(first.binding.appWidgetId)
        assertThat(platform.deleted).containsExactly(first.binding.appWidgetId)
    }

    @Test fun damagedStoreNeverSweepsIdsOrSilentlyResets() = runTest {
        platform.owned += 10; platform.foreignIds += 2
        var recoveries = 0
        val damaged = object : CardWidgetBindingStore {
            override suspend fun load() = CardWidgetBindingState.Corrupt
            override suspend fun save(state: CardWidgetBindingState.Ready) = false
            override suspend fun recover(): Boolean { recoveries++; return false }
        }
        val coordinator = flow(damaged)
        assertThat(coordinator.reconcile(mapOf(request.cardId to request))).isFalse()
        assertThat(coordinator.begin(request)).isEqualTo(CardWidgetStep.StorageFailure)
        assertThat(platform.deleted).isEmpty()
        assertThat(recoveries).isEqualTo(0)
    }

    @Test fun failedPendingPersistenceReleasesAllocationAndKeepsOriginalState() = runTest {
        val failing = object : CardWidgetBindingStore by store {
            override suspend fun save(state: CardWidgetBindingState.Ready) = false
        }
        assertThat(flow(failing).begin(request)).isEqualTo(CardWidgetStep.StorageFailure)
        assertThat(platform.deleted).hasSize(1)
        assertThat(saved()).isEmpty()
    }

    @Test fun orphanSweepUsesOnlyDedicatedHostOwnershipEvenIfStoredIdIsForeign() = runTest {
        platform.owned += 11; platform.foreignIds += 3
        val before = store.load() as CardWidgetBindingState.Ready
        store.save(before.copy(bindings = mapOf("foreign-record" to CardWidgetBinding("foreign-record", "foreign-instance", provider,
            3, "token", CardWidgetBindingPhase.BOUND))))
        flow().reconcile(emptyMap())
        assertThat(platform.deleted).containsExactly(11)
        assertThat(platform.foreignIds).contains(3)
    }

    @Test fun changedTargetDuringBindingCancelsBeforeCommit() = runTest {
        platform.bindAllowed = true
        platform.onBind = { current = null }
        assertThat(flow().begin(request)).isEqualTo(CardWidgetStep.Rejected)
        assertThat(saved()).isEmpty()
        assertThat(platform.deleted).hasSize(1)
    }

    private class FakePlatform : CardWidgetBindingPlatform {
        val owned = mutableSetOf<Int>()
        val foreignIds = mutableSetOf<Int>()
        val bound = mutableMapOf<Int, ComponentKey>()
        val deleted = mutableListOf<Int>()
        var allocations = 0
        var bindAllowed = false
        var configurationRequired = false
        var available = true
        var onBind: (suspend (Int) -> Unit)? = null
        override fun ownedIds() = owned.toSet()
        override fun allocateId(): Int = (100 + allocations++).also { owned += it }
        override fun deleteId(id: Int) { check(id in owned); owned -= id; bound -= id; deleted += id }
        override fun providerAvailable(provider: ComponentKey) = available
        override fun bindIfAllowed(id: Int, provider: ComponentKey): Boolean {
            // The production operation is synchronous. This callback checks only already-loaded
            // test state; a runTest helper below installs the durable-record assertion.
            kotlinx.coroutines.runBlocking { onBind?.invoke(id) }
            if (bindAllowed) bound[id] = provider
            return bindAllowed
        }
        override fun boundProvider(id: Int) = bound[id]
        override fun needsConfiguration(id: Int) = configurationRequired
    }
}
