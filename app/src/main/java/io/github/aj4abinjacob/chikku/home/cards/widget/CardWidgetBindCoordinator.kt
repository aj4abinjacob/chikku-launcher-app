package io.github.aj4abinjacob.chikku.home.cards.widget

import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBinding
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingPhase
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingState
import io.github.aj4abinjacob.chikku.data.cards.CardWidgetBindingStore
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CardWidgetRequest(val cardId: String, val instanceId: String, val provider: ComponentKey)

interface CardWidgetBindingPlatform {
    fun ownedIds(): Set<Int>
    fun allocateId(): Int
    fun deleteId(id: Int)
    fun providerAvailable(provider: ComponentKey): Boolean
    fun bindIfAllowed(id: Int, provider: ComponentKey): Boolean
    fun boundProvider(id: Int): ComponentKey?
    fun needsConfiguration(id: Int): Boolean
}

sealed interface CardWidgetStep {
    data class Consent(val binding: CardWidgetBinding) : CardWidgetStep
    data class Configure(val binding: CardWidgetBinding) : CardWidgetStep
    data class Bound(val binding: CardWidgetBinding) : CardWidgetStep
    data object Ignored : CardWidgetStep
    data object Rejected : CardWidgetStep
    data object StorageFailure : CardWidgetStep
}

/** Every call runs off main. Durable pending records precede system consent/configuration. */
class CardWidgetBindCoordinator(
    private val store: CardWidgetBindingStore,
    private val platform: CardWidgetBindingPlatform,
    private val stillValid: suspend (CardWidgetRequest) -> Boolean,
) {
    private val mutex = Mutex()

    suspend fun begin(request: CardWidgetRequest): CardWidgetStep = mutex.withLock {
        var state = store.load() as? CardWidgetBindingState.Ready ?: return@withLock CardWidgetStep.StorageFailure
        if (!stillValid(request) || !platform.providerAvailable(request.provider)) return@withLock CardWidgetStep.Rejected
        val old = state.bindings[request.cardId]
        if (old != null && matches(old, request)) {
            if (old.phase != CardWidgetBindingPhase.BOUND) return@withLock continueBinding(old)
            if (old.appWidgetId in platform.ownedIds() && platform.boundProvider(old.appWidgetId) == request.provider) {
                return@withLock CardWidgetStep.Bound(old)
            }
        }
        val id = platform.allocateId()
        if (id <= 0 || id !in platform.ownedIds()) return@withLock CardWidgetStep.Rejected
        val previous = old?.takeIf { matches(it, request) && it.phase == CardWidgetBindingPhase.BOUND }?.appWidgetId
        val pending = CardWidgetBinding(request.cardId, request.instanceId, request.provider, id,
            UUID.randomUUID().toString(), CardWidgetBindingPhase.CONSENT, previous)
        if (!store.save(state.copy(bindings = state.bindings + (request.cardId to pending)))) {
            deleteOwn(id)
            return@withLock CardWidgetStep.StorageFailure
        }
        if (old != null && !matches(old, request)) {
            deleteOwn(old.appWidgetId); old.previousId?.let(::deleteOwn)
        }
        if (!stillValid(request)) return@withLock cancelLocked(pending)
        if (!platform.bindIfAllowed(id, request.provider)) CardWidgetStep.Consent(pending) else continueBinding(pending)
    }

    suspend fun consentResult(binding: CardWidgetBinding, granted: Boolean): CardWidgetStep = mutex.withLock {
        if (!isCurrent(binding)) return@withLock CardWidgetStep.Rejected
        if (!granted || platform.boundProvider(binding.appWidgetId) != binding.provider) cancelLocked(binding) else continueBinding(binding)
    }

    suspend fun configurationResult(binding: CardWidgetBinding, accepted: Boolean): CardWidgetStep = mutex.withLock {
        if (!isCurrent(binding)) return@withLock CardWidgetStep.Rejected
        if (!accepted) cancelLocked(binding) else commit(binding)
    }

    suspend fun cancel(binding: CardWidgetBinding): CardWidgetStep = mutex.withLock { cancelLocked(binding) }

    /** Keep valid pending records across process death; remove only our host's proven orphan IDs. */
    suspend fun reconcile(requests: Map<String, CardWidgetRequest>): Boolean = mutex.withLock {
        val state = store.load() as? CardWidgetBindingState.Ready ?: return@withLock false
        val retained = state.bindings.filter { (id, binding) -> requests[id]?.let { matches(binding, it) } == true }
        if (retained != state.bindings) {
            if (!store.save(state.copy(bindings = retained))) return@withLock false
        }
        val referenced = retained.values.flatMap { listOfNotNull(it.appWidgetId, it.previousId) }.toSet()
        platform.ownedIds().filter { it !in referenced }.forEach(::deleteOwn)
        true
    }

    /** User-confirmed recovery preserves the damaged file before releasing dedicated-host IDs. */
    suspend fun recover(): Boolean = mutex.withLock {
        if (!store.recover()) return@withLock false
        platform.ownedIds().forEach(::deleteOwn)
        true
    }

    private suspend fun continueBinding(binding: CardWidgetBinding): CardWidgetStep {
        if (!isCurrent(binding) || !stillValid(requestOf(binding)) || binding.appWidgetId !in platform.ownedIds() ||
            platform.boundProvider(binding.appWidgetId) != binding.provider) {
            // An unbound durable pending ID needs consent again after process recreation.
            if (isCurrent(binding) && stillValid(requestOf(binding)) && binding.appWidgetId in platform.ownedIds() &&
                platform.boundProvider(binding.appWidgetId) == null && platform.providerAvailable(binding.provider)) {
                return CardWidgetStep.Consent(binding)
            }
            return cancelLocked(binding)
        }
        if (platform.needsConfiguration(binding.appWidgetId)) {
            val state = store.load() as? CardWidgetBindingState.Ready ?: return CardWidgetStep.StorageFailure
            val pending = binding.copy(phase = CardWidgetBindingPhase.CONFIGURATION)
            if (!store.save(state.copy(bindings = state.bindings + (binding.cardId to pending)))) return CardWidgetStep.StorageFailure
            return CardWidgetStep.Configure(pending)
        }
        return commit(binding)
    }

    private suspend fun commit(binding: CardWidgetBinding): CardWidgetStep {
        if (!isCurrent(binding) || !stillValid(requestOf(binding)) || binding.appWidgetId !in platform.ownedIds() ||
            platform.boundProvider(binding.appWidgetId) != binding.provider) return cancelLocked(binding)
        val state = store.load() as? CardWidgetBindingState.Ready ?: return CardWidgetStep.StorageFailure
        val committed = binding.copy(phase = CardWidgetBindingPhase.BOUND, previousId = null)
        if (!store.save(state.copy(bindings = state.bindings + (binding.cardId to committed)))) return CardWidgetStep.StorageFailure
        binding.previousId?.let(::deleteOwn)
        return CardWidgetStep.Bound(committed)
    }

    private suspend fun cancelLocked(binding: CardWidgetBinding): CardWidgetStep {
        if (!isCurrent(binding)) return CardWidgetStep.Rejected
        val state = store.load() as? CardWidgetBindingState.Ready ?: return CardWidgetStep.StorageFailure
        val replacement = binding.previousId?.let { binding.copy(appWidgetId = it, phase = CardWidgetBindingPhase.BOUND, previousId = null) }
        val entries = state.bindings - binding.cardId
        if (!store.save(state.copy(bindings = if (replacement == null) entries else entries + (binding.cardId to replacement)))) return CardWidgetStep.StorageFailure
        deleteOwn(binding.appWidgetId)
        return CardWidgetStep.Rejected
    }

    private suspend fun isCurrent(binding: CardWidgetBinding): Boolean =
        (store.load() as? CardWidgetBindingState.Ready)?.let {
            it.bindings[binding.cardId]?.let { current ->
                current.token == binding.token && current.appWidgetId == binding.appWidgetId && current.phase == binding.phase
            } == true
        } == true

    private fun deleteOwn(id: Int) { if (id in platform.ownedIds()) platform.deleteId(id) }
    private fun matches(binding: CardWidgetBinding, request: CardWidgetRequest) =
        binding.cardId == request.cardId && binding.instanceId == request.instanceId && binding.provider == request.provider
    private fun requestOf(binding: CardWidgetBinding) = CardWidgetRequest(binding.cardId, binding.instanceId, binding.provider)
}
