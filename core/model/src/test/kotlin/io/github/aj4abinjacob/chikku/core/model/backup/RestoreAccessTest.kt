package io.github.aj4abinjacob.chikku.core.model.backup

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RestoreAccessTest {
    @Test fun concurrentEditsWaitAndObserversDoNotReceiveIntermediateRestoreValues() = runTest {
        val access = RestoreAccess()
        val store = MutableStateFlow("before")
        val observed = mutableListOf<String>()
        backgroundScope.launch { access.observe(store, "unavailable").collect { observed += it } }
        runCurrent()
        val pause = CompletableDeferred<Unit>()
        val restore = async { access.restoring(false) { store.value = "intermediate"; pause.await(); store.value = "after"; true } }
        runCurrent()
        val edit = async { access.run(false) { assertThat(store.value).isEqualTo("after"); store.value = "edit"; true } }
        runCurrent()
        assertThat(edit.isCompleted).isFalse()
        assertThat(observed).doesNotContain("intermediate")
        pause.complete(Unit)
        restore.await(); edit.await(); runCurrent()
        assertThat(observed.last()).isEqualTo("edit")
    }

    @Test fun failedRecoveryBlocksOperationsAndCanBeRetriedWithoutDiscardingData() = runTest {
        var canRecover = false
        val access = RestoreAccess { canRecover }
        var touched = false
        assertThat(access.run(false) { touched = true; true }).isFalse()
        assertThat(touched).isFalse()
        canRecover = true
        assertThat(access.run(false) { touched = true; true }).isTrue()
        assertThat(touched).isTrue()
    }
}
