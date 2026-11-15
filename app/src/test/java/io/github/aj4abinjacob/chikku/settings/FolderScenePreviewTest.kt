package io.github.aj4abinjacob.chikku.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.ContextThemeWrapper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.FolderScrollStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSortOrder
import io.github.aj4abinjacob.chikku.core.model.settings.FolderWindowStyle
import io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.folder.FolderIconDrawable
import io.github.aj4abinjacob.chikku.settings.previews.FolderClosedTile
import io.github.aj4abinjacob.chikku.settings.previews.FolderOpenStage
import io.github.aj4abinjacob.chikku.settings.previews.folderSceneLabels
import io.github.aj4abinjacob.chikku.settings.previews.folderSceneLayout
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderScenePreviewTest {
    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.Theme_Chikku_Home)
    private val labels = folderSceneLabels(context)

    @Test fun layoutFollowsColumnsSortOrderAndPaging() {
        assertThat(folderSceneLayout(FolderSettings(), labels).columns).isEqualTo(3)
        assertThat(folderSceneLayout(FolderSettings(columns = 2), labels).columns).isEqualTo(2)
        val manual = folderSceneLayout(FolderSettings(sortOrder = FolderSortOrder.MANUAL), labels).order
        val sorted = folderSceneLayout(FolderSettings(sortOrder = FolderSortOrder.ALPHABETICAL), labels).order
        assertThat(manual).isEqualTo(labels.indices.toList())
        assertThat(sorted.map(labels::get)).isInOrder()
        val paged = folderSceneLayout(FolderSettings(columns = 2, rows = 1, scrollStyle = FolderScrollStyle.HORIZONTAL_PAGED), labels)
        assertThat(paged.order).hasSize(2)
        assertThat(paged.pages).isEqualTo(3)
    }

    @Test fun openStageDrawsTheConfiguredWindowWithRealCells() {
        val folders = FolderSettings(columns = 2, rows = 1, scrollStyle = FolderScrollStyle.HORIZONTAL_PAGED,
            showName = false, cornerRadiusDp = 4f, backgroundAlpha = 1f, windowShadow = false)
        val stage = laidOut(FolderOpenStage(context).apply { bind(folders, 0xFFFFFFFF.toInt(), labels, folderSceneLayout(folders, labels)) })
        val cells = (0 until stage.grid!!.childCount).map { stage.grid!!.getChildAt(it) as AppIconView }
        assertThat(cells).hasSize(2)
        assertThat(cells.all { !it.isClickable && !it.isFocusable }).isTrue()
        assertThat(stage.grid!!.columns).isEqualTo(2)
        assertThat(stage.name!!.visibility).isEqualTo(View.GONE)
        assertThat(stage.pageIndicator!!.text.toString()).isEqualTo(context.getString(R.string.folder_page_indicator, 1, 3))
        assertThat((stage.panel!!.background as GradientDrawable).cornerRadius).isEqualTo(4f * context.resources.displayMetrics.density)
        assertThat(stage.panel!!.elevation).isEqualTo(0f)
        // Floating windows keep their own size and sit inside the tile.
        assertThat(stage.panel!!.measuredWidth * stage.scale).isAtMost(600f)

        val immersive = folders.copy(windowStyle = FolderWindowStyle.IMMERSIVE, showName = true, windowShadow = true)
        stage.bind(immersive, 0xFFFFFFFF.toInt(), labels, folderSceneLayout(immersive, labels))
        laidOut(stage)
        assertThat(stage.name!!.visibility).isEqualTo(View.VISIBLE)
        assertThat(stage.panel!!.getChildAt(0)).isSameInstanceAs(stage.name)
        assertThat(stage.panel!!.elevation).isGreaterThan(0f)
        // Immersive windows fill the tile less its inset.
        assertThat(stage.panel!!.measuredHeight * stage.scale).isWithin(2f).of(400f - 16 * context.resources.displayMetrics.density)
    }

    @Test fun closedTileUsesTheRealFolderIcon() {
        val tile = FolderClosedTile(context).apply { bind(FolderSettings(previewCount = 3), labels.indices.toList(), HomeSettings(iconScale = 1.2f)) }
        assertThat(tile.cell.icon).isInstanceOf(FolderIconDrawable::class.java)
        assertThat(tile.cell.iconScale).isEqualTo(1.2f)
        assertThat(tile.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
    }

    @Test fun closedTileFollowsTheHomeLabelSettings() {
        val tile = FolderClosedTile(context)
        val order = labels.indices.toList()
        val home = HomeSettings(
            showLabels = false, labelScale = 1.4f, labelShadow = false, labelSingleLine = false,
            labelColor = 0xFF123456.toInt(), iconLabelGapDp = 9, iconVerticalOffsetDp = 5,
        )
        tile.bind(FolderSettings(previewCount = 3), order, home)
        assertThat(tile.cell.showLabel).isFalse()
        assertThat(tile.cell.labelScale).isEqualTo(1.4f)
        assertThat(tile.cell.labelShadow).isFalse()
        assertThat(tile.cell.labelSingleLine).isFalse()
        assertThat(tile.cell.labelGapDp).isEqualTo(9)
        assertThat(tile.cell.verticalOffsetDp).isEqualTo(5)
        tile.bind(FolderSettings(previewCount = 3), order, home.copy(showLabels = true, labelScale = 0.8f))
        assertThat(tile.cell.showLabel).isTrue()
        assertThat(tile.cell.labelScale).isEqualTo(0.8f)
    }

    private fun <T : View> laidOut(view: T): T = view.apply {
        measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY))
        layout(0, 0, 600, 400)
    }
}
