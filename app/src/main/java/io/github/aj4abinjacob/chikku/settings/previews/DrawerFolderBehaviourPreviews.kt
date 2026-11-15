package io.github.aj4abinjacob.chikku.settings.previews

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.DrawerSortMode
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabBarPosition
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerTabStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.SurfaceColors

internal fun drawerAfterLaunchPreviewOpen(automaticallyClose: Boolean): Boolean = !automaticallyClose

internal fun drawerReopenedPreviewPage(page: Int, rememberPosition: Boolean): Int =
    if (rememberPosition) page else 0

internal fun drawerOpeningPreviewChanged(before: DrawerSettings, after: DrawerSettings): Boolean =
    before.openMethod != after.openMethod || before.transition != after.transition

/** Real cells need their full measured height, unlike scaled canvas-only preview scenes. */
internal fun drawerGroupsPreviewSceneHeight(screenHeightDp: Int, fontScale: Float, cellHeightDp: Float): Float =
    maxOf(previewSceneHeight(screenHeightDp, fontScale, 156f),
        cellHeightDp + 24f + 12f + 20f * fontScale.coerceAtLeast(1f))

/** Keeps sample titles associated with identity during reorders; identities never become UI text. */
internal class SyntheticPreviewIdentities<T> {
    private val ordinals = linkedMapOf<T, Int>()
    fun order(identities: List<T>): List<Int> = identities.map { id -> ordinals.getOrPut(id) { ordinals.size } }
}

internal fun syntheticMemberOrder(order: List<Int>, sort: DrawerSortMode): List<Int> =
    if (sort == DrawerSortMode.MANUAL) order else order.sortedWith(compareBy<Int> { syntheticNameIndex(it) }.thenBy { it / 4 })

// Shuffled names make alphabetical and manual ordering visibly different even before reordering.
private fun syntheticNameIndex(index: Int): Int = intArrayOf(3, 0, 1, 2)[Math.floorMod(index, 4)]
private val sampleNames = intArrayOf(R.string.preview_gap_sample_alpha, R.string.preview_gap_sample_beta,
    R.string.preview_gap_sample_delta, R.string.preview_gap_sample_zulu)

/** Pinned synthetic drawer tabs and real launcher icon cells. Never receives labels or icons. */
@Composable
fun DrawerGroupsLivePreview(
    settings: LauncherSettings,
    groupOrder: List<Int>,
    memberOrder: List<Int>? = null,
    sortMode: DrawerSortMode = DrawerSortMode.MANUAL,
    modifier: Modifier = Modifier,
    selectedGroup: Int? = null,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val cellHeightDp = AppIconView.desiredHeight(context, AppIconView.Style.DRAWER,
        iconScale = .7f, labelScale = .85f) / context.resources.displayMetrics.density
    val sceneHeight = drawerGroupsPreviewSceneHeight(configuration.screenHeightDp, configuration.fontScale, cellHeightDp).dp
    val shownMembers = memberOrder?.let { syntheticMemberOrder(it, sortMode) } ?: listOf(0, 1, 2, 3)
    val visibleTabs = if (settings.drawer.groupsEnabled) {
        if (selectedGroup != null && selectedGroup !in groupOrder.take(3)) groupOrder.take(2) + selectedGroup
        else groupOrder.take(3)
    } else emptyList()
    val baseTabs = if (settings.drawer.showWorkTab) listOf(stringResource(R.string.drawer_tab_personal), stringResource(R.string.drawer_tab_work))
        else listOf(stringResource(R.string.drawer_tab_all))
    val tabLabels = baseTabs + visibleTabs.map {
        stringResource(R.string.preview_gap_group_number, it + 1)
    }
    val names = sampleNames.map { stringResource(it) }
    val orderDescription = shownMembers.take(4).joinToString { index ->
        names[syntheticNameIndex(index)] + " ${index / 4 + 1}"
    }
    val tabDescription = tabLabels.joinToString()
    val description = stringResource(R.string.preview_gap_groups_description, tabDescription, orderDescription,
        groupOrder.size, memberOrder?.size ?: 4,
        stringResource(if (sortMode == DrawerSortMode.ALPHABETICAL) R.string.drawer_groups_sort_alphabetical else R.string.drawer_groups_sort_manual))
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))) {
        Column(Modifier.fillMaxWidth().height(sceneHeight).padding(12.dp).clearAndSetSemantics { contentDescription = description }) {
            val selectedTab = selectedGroup?.let { group -> visibleTabs.indexOf(group).takeIf { it >= 0 }?.plus(baseTabs.size) } ?: 0
            val accent = settings.drawer.tabAccentColor?.let(::Color) ?: MaterialTheme.colorScheme.primaryContainer
            val accentText = settings.drawer.tabAccentColor?.let { Color(DrawerChrome.accentTabTextColor(it)) }
                ?: MaterialTheme.colorScheme.onPrimaryContainer
            val segmented = settings.drawer.tabStyle == DrawerTabStyle.SEGMENTED
            val tabs: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabLabels.forEachIndexed { index, label ->
                        Text(label, Modifier.weight(1f).background(
                            if (index == selectedTab) accent else if (segmented) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            RoundedCornerShape(if (segmented) 14.dp else 18.dp)).padding(horizontal = 6.dp, vertical = 6.dp),
                            color = if (index == selectedTab && segmented) accentText else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (settings.drawer.tabBarPosition == DrawerTabBarPosition.TOP) tabs()
            if (shownMembers.isEmpty()) {
                Text(stringResource(R.string.preview_gap_empty_group), Modifier.weight(1f).padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    shownMembers.take(4).forEach { index ->
                        val name = stringResource(sampleNames[syntheticNameIndex(index)]) + " ${index / 4 + 1}"
                        AndroidView(modifier = Modifier.weight(1f), factory = { context ->
                            AppIconView(context, AppIconView.Style.DRAWER).apply {
                                isClickable = false
                                isFocusable = false
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            }
                        }, update = { cell ->
                            val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, settings.night, cell.context), settings.drawer)
                            cell.icon = previewSampleIcon(cell.context, index)
                            cell.label = name
                            cell.showLabel = true
                            cell.iconScale = .7f
                            cell.labelScale = .85f
                            cell.setLabelColor(colors.content)
                            cell.setBackgroundColor(colors.background)
                        })
                    }
                }
            }
            if (settings.drawer.tabBarPosition == DrawerTabBarPosition.BOTTOM) tabs()
        }
        Text(stringResource(R.string.preview_gap_groups_caption, groupOrder.size, memberOrder?.size ?: 4),
            Modifier.fillMaxWidth().height((36f * configuration.fontScale.coerceAtLeast(1f)).dp).padding(horizontal = 16.dp).clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
