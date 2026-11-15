package io.github.aj4abinjacob.chikku.data.cards

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.ComponentKey
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = io.github.aj4abinjacob.chikku.home.TestHomeApplication::class)
class CardWidgetBindingStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val binding = CardWidgetBinding("card", "instance", ComponentKey("widget.app", "widget.app.Provider", 0),
        123, "token", CardWidgetBindingPhase.CONSENT)

    @Test fun pendingBindingSurvivesAStoreRecreation() = runTest {
        val directory = folder.newFolder()
        val original = FileCardWidgetBindingStore(directory)
        val state = original.load() as CardWidgetBindingState.Ready
        assertThat(original.save(state.copy(bindings = mapOf(binding.cardId to binding)))).isTrue()
        assertThat((FileCardWidgetBindingStore(directory).load() as CardWidgetBindingState.Ready).bindings.values).containsExactly(binding)
    }

    @Test fun damagedFileIsPreservedAndExplicitRecoveryIsRequired() = runTest {
        val directory = folder.newFolder()
        val file = File(directory, FileCardWidgetBindingStore.FILE_NAME)
        file.writeText("damaged-widget-state")
        val store = FileCardWidgetBindingStore(directory)
        assertThat(store.load()).isEqualTo(CardWidgetBindingState.Corrupt)
        assertThat(store.save(CardWidgetBindingState.Ready())).isFalse()
        assertThat(file.readText()).isEqualTo("damaged-widget-state")
        assertThat(File(directory, "${FileCardWidgetBindingStore.FILE_NAME}.damaged").readText()).isEqualTo(file.readText())
        assertThat(store.recover()).isTrue()
        assertThat(store.load()).isInstanceOf(CardWidgetBindingState.Ready::class.java)
        assertThat(File(directory, "${FileCardWidgetBindingStore.FILE_NAME}.damaged").readText()).isEqualTo("damaged-widget-state")
    }

    @Test fun aNewerSaveCannotBeOverwrittenByAnEarlierSnapshot() = runTest {
        val store = FileCardWidgetBindingStore(folder.newFolder())
        val stale = store.load() as CardWidgetBindingState.Ready
        assertThat(store.save(stale)).isTrue()
        assertThat(store.save(stale.copy(bindings = mapOf(binding.cardId to binding)))).isFalse()
        assertThat((store.load() as CardWidgetBindingState.Ready).bindings).isEmpty()
    }

    @Test fun unknownVersionIsNotSilentlyAccepted() = runTest {
        val directory = folder.newFolder()
        File(directory, FileCardWidgetBindingStore.FILE_NAME).writeText("{\"version\":99,\"bindings\":[]}")
        assertThat(FileCardWidgetBindingStore(directory).load()).isEqualTo(CardWidgetBindingState.Corrupt)
    }
}
