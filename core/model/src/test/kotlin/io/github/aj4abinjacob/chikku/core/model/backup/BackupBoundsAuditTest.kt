package io.github.aj4abinjacob.chikku.core.model.backup

import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.WorkspaceContent
import io.github.aj4abinjacob.chikku.core.model.WorkspaceLimits
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import org.junit.Test

class BackupBoundsAuditTest {
    private fun archive(pages: Int = 65) = ChikkuBackup(
        0,
        "audit",
        LauncherSettings(),
        WorkspaceContent(pages, emptyList()),
        emptyList(),
        emptyList(),
    )

    @Test fun maximumSupportedPageCountRoundTrips() {
        for (pages in listOf(64, 65, WorkspaceLimits.MAX_PAGES)) {
            val backup = archive(pages)
            assertThat((BackupCodec.decode(BackupCodec.encode(backup)) as BackupDecodeResult.Success).backup)
                .isEqualTo(backup)
        }
    }

    @Test fun tinyHostileArchiveCannotRequestAnUnboundedPageAllocation() {
        for (pages in listOf(WorkspaceLimits.MAX_PAGES + 1, Int.MAX_VALUE)) {
            val backup = archive(pages)
            val text = BackupCodec.encodeLocalCheckpoint(backup)
            assertThat(BackupCodec.decode(text)).isInstanceOf(BackupDecodeResult.Failure::class.java)
            assertThat((BackupCodec.decodeLocalCheckpoint(text) as BackupDecodeResult.Success).backup).isEqualTo(backup)
            try {
                BackupCodec.encode(backup)
                error("Expected export rejection")
            } catch (
                _: IllegalArgumentException,
            ) { }
        }
    }

    @Test fun oversizedOutputIsRejectedBeforeAFileCanBeReportedSaved() {
        val backup = archive().copy(appVersion = "x".repeat(BackupCodec.MAX_BYTES))
        try {
            BackupCodec.encode(backup)
            error("Expected export rejection")
        } catch (_: IllegalArgumentException) { }
        assertThat(BackupCodec.decode(BackupCodec.encodeLocalCheckpoint(backup)))
            .isEqualTo(BackupDecodeResult.Failure(BackupError.TooLarge))
        assertThat(
            (
                BackupCodec.decodeLocalCheckpoint(
                    BackupCodec.encodeLocalCheckpoint(backup),
                ) as BackupDecodeResult.Success
                ).backup,
        )
            .isEqualTo(backup)
    }

    @Test fun localCheckpointRetainsHistoricalListsWithoutRelaxingPortableLimits() {
        val members = (0..BackupCodec.MAX_ENTRIES).map { AppKey("synthetic.p$it", "Main", 0) }
        val backup = archive().copy(
            drawerGroups = listOf(DrawerGroup("group", "Synthetic", 0, DrawerSortMode.MANUAL, members)),
        )
        val text = BackupCodec.encodeLocalCheckpoint(backup)
        assertThat(BackupCodec.decode(text)).isInstanceOf(BackupDecodeResult.Failure::class.java)
        assertThat((BackupCodec.decodeLocalCheckpoint(text) as BackupDecodeResult.Success).backup).isEqualTo(backup)
        try {
            BackupCodec.encode(backup)
            error("Expected export rejection")
        } catch (_: IllegalArgumentException) { }
    }
}
