package io.github.aj4abinjacob.chikku.settings.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R

/**
 * What the settings top bar shows while a section is on one of its sub-pages. [SettingsRoot]
 * [io.github.aj4abinjacob.chikku.settings.SettingsRoot] owns one and reads it for the title and
 * the back arrow; [SettingsSubPage] fills it in.
 */
@Stable
class SettingsTopBarState {
    /** Title of the open sub-page, or null on a section's main page. */
    var subPageTitle: String? by mutableStateOf(null)
        internal set

    /** Goes back one level from the open sub-page; null on a section's main page. */
    var subPageBack: (() -> Unit)? by mutableStateOf(null)
        internal set
}

/**
 * One sub-page of a settings section: [route] is the value stored in [rememberSettingsSubPage]
 * (unique within the section), [title] its top-bar title. Declare one per sub-page next to the
 * section and use it both there and in `SettingsSearchIndex` definitions (`subPage = ...`).
 */
data class SettingsSubPageLink(val route: String, @StringRes val title: Int)

/** Top bar state of the enclosing settings screen. Sections never provide this themselves. */
val LocalSettingsTopBar = staticCompositionLocalOf { SettingsTopBarState() }

/**
 * Sub-page a settings search result asked for (see `SettingsSearchEntry.subPage`), or null.
 * Read only as the initial value of [rememberSettingsSubPage].
 */
val LocalSettingsDeepLink = staticCompositionLocalOf<String?> { null }

/**
 * Which sub-page of a section is open (null for the section's main page). Survives rotation and
 * process death, and starts on the sub-page a search result opened. Route names are the section's
 * own string constants, shared with its `SettingsSearchIndex` definitions.
 */
@Composable
fun rememberSettingsSubPage(): MutableState<String?> {
    val deepLink = LocalSettingsDeepLink.current
    return rememberSaveable { mutableStateOf(deepLink) }
}

/**
 * Shows [content] as a sub-page: the settings top bar shows [title], and both the system Back and
 * the top bar's back arrow call [onBack] (usually: return to the section's main page or the parent
 * sub-page).
 */
@Composable
fun SettingsSubPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val topBar = LocalSettingsTopBar.current
    val currentBack by rememberUpdatedState(onBack)
    BackHandler { currentBack() }
    DisposableEffect(topBar, title) {
        val back = { currentBack() }
        topBar.subPageTitle = title
        topBar.subPageBack = back
        onDispose {
            // A sub-page that replaces this one may already have registered itself.
            if (topBar.subPageBack === back) {
                topBar.subPageTitle = null
                topBar.subPageBack = null
            }
        }
    }
    content()
}

/** A row on a section's main page that opens a sub-page; [summary] shows its current state. */
@Composable
fun SettingsSubPageRow(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    onClick: () -> Unit,
) {
    SettingsNavigationRow(
        title = title,
        summary = summary,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        trailing = {
            Icon(
                painterResource(R.drawable.ic_settings_chevron),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * The prominent on/off switch at the top of a feature's sub-page (Android "main switch bar").
 * When it is off, hide the feature's other rows and show a [SettingsInfoText] saying what turning
 * it on does, instead of greying them out.
 */
@Composable
fun SettingsMainSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (checked) scheme.primaryContainer else scheme.surfaceContainer)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (checked) scheme.onPrimaryContainer else scheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            // The bar itself is filled with the container colour, so invert the checked track and thumb.
            colors = SwitchDefaults.colors(
                checkedTrackColor = scheme.onPrimaryContainer,
                checkedThumbColor = scheme.primaryContainer,
                checkedBorderColor = scheme.onPrimaryContainer,
            ),
            thumbContent = if (checked) {
                {
                    Icon(painterResource(R.drawable.ic_settings_check), null, Modifier.size(SwitchDefaults.IconSize),
                        tint = scheme.onPrimaryContainer)
                }
            } else {
                null
            },
        )
    }
}

/**
 * Joins the non-null [parts] of a sub-page summary with the localized separator
 * ("Scroll thumb · Right"); returns null when every part is null.
 */
@Composable
fun settingsSummary(vararg parts: String?): String? {
    val present = parts.filterNotNull()
    if (present.isEmpty()) return null
    val resources = LocalResources.current
    return present.reduce { joined, next -> resources.getString(R.string.settings_summary_join, joined, next) }
}
