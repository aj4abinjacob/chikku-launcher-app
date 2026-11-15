package io.github.aj4abinjacob.chikku.data.restore

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.RejectReason
import io.github.aj4abinjacob.chikku.core.model.WorkspaceRepository
import io.github.aj4abinjacob.chikku.core.model.backup.RestoreAccess
import io.github.aj4abinjacob.chikku.home.FakeWorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecoveryWorkspaceTest {

    private class PageRecordingRepository : WorkspaceRepository by FakeWorkspaceRepository() {
        val removedPages = mutableListOf<Int>()
        override suspend fun removePage(page: Int, deleteItems: Boolean): EditResult {
            removedPages += page
            return EditResult.Applied("page")
        }
    }

    @Test
    fun removePageReachesTheStore() = runTest {
        val source = PageRecordingRepository()
        val result = RecoveryWorkspace(source, RestoreAccess()).removePage(2)
        assertThat(result).isEqualTo(EditResult.Applied("page"))
        assertThat(source.removedPages).containsExactly(2)
    }

    @Test
    fun removePageIsBlockedWhileRecoveryIsRequired() = runTest {
        val source = PageRecordingRepository()
        val result = RecoveryWorkspace(source, RestoreAccess { false }).removePage(2)
        assertThat(result).isEqualTo(EditResult.Rejected(RejectReason.STORAGE_ERROR))
        assertThat(source.removedPages).isEmpty()
    }

    /**
     * Production wraps every workspace store in [RecoveryWorkspace]. A method it does not override
     * falls through to the interface default (usually "rejected" or a no-op), which silently breaks
     * that feature in the app while tests of the Room repository still pass.
     */
    @Test
    fun everyRepositoryOperationIsForwarded() {
        val missing = WorkspaceRepository::class.java.methods
            .filter { !it.isSynthetic }
            .filter { method ->
                // The compiler adds a bridge to the interface default for methods a class does not override.
                val declared = runCatching { RecoveryWorkspace::class.java.getDeclaredMethod(method.name, *method.parameterTypes) }.getOrNull()
                declared == null || declared.isBridge
            }
            .map { it.name }
        assertThat(missing).isEmpty()
    }
}
