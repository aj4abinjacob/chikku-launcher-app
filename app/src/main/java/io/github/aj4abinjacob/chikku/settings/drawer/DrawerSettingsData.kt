package io.github.aj4abinjacob.chikku.settings.drawer

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.DrawerGroup
import io.github.aj4abinjacob.chikku.core.model.LauncherApp
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/*
 * Data access for the drawer settings screens. Reads come from the same repositories HOME uses;
 * writes run in the process scope so leaving the screen cannot cancel them. Labels are shown on
 * screen only and never logged.
 */

@Composable
internal fun rememberAppsState(deps: HomeDependencies): State<AppsState> {
    val flow = remember(deps) { deps.appsRepository.apps.catch { emit(AppsState.Failed(it.javaClass.simpleName)) } }
    return flow.collectAsStateWithLifecycle(AppsState.Loading)
}

@Composable
internal fun rememberCustomizations(deps: HomeDependencies): State<Map<AppKey, AppCustomization>> {
    val flow = remember(deps) { deps.appCustomizationRepository.customizations.catch { emit(emptyMap()) } }
    return flow.collectAsStateWithLifecycle(emptyMap())
}

/** Null until the stored groups have loaded. */
@Composable
internal fun rememberDrawerGroups(deps: HomeDependencies): State<List<DrawerGroup>?> {
    val flow = remember(deps) { deps.drawerGroupRepository.groups.catch { emit(emptyList()) } }
    return flow.collectAsStateWithLifecycle(null)
}

/** Runs a repository write in the app scope; a false result or failure shows a short message. */
internal fun HomeDependencies.write(context: Context, block: suspend () -> Boolean) {
    // Resolved when shown: the settings snackbar if the page is still open, otherwise dropped.
    val messages = ChikkuMessages.of(context)
    appScope.launch {
        val ok = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
        if (!ok) messages.show(R.string.drawer_save_failed)
    }
}

/** Label with a work marker so personal and work copies of one app can be told apart. */
@Composable
internal fun appTitle(app: LauncherApp): String =
    if (app.profileType == ProfileType.WORK) stringResource(R.string.drawer_app_work, app.label) else app.label

/** Loading / failure placeholder for app lists; returns the apps when loaded. */
@Composable
internal fun AppsStatus(state: AppsState) {
    when (state) {
        AppsState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is AppsState.Failed -> Text(
            stringResource(R.string.drawer_apps_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
        is AppsState.Loaded -> Unit
    }
}
