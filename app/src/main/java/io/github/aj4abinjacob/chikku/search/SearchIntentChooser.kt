package io.github.aj4abinjacob.chikku.search

import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.platform.IntentTarget
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import io.github.aj4abinjacob.chikku.settings.components.rememberAppIconCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconBitmapCache
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconImage
import io.github.aj4abinjacob.chikku.settings.iconpicker.rememberIconBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** Selection stays in Chikku so the chosen destination can be authenticated before it opens. */
internal class SearchIntentChooser(
    private val activity: ComponentActivity,
    private val settings: Flow<LauncherSettings>,
) {
    private var view: ComposeView? = null
    val isShowing: Boolean get() = view != null
    private val observer = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = dismiss()
        override fun onDestroy(owner: LifecycleOwner) = dismiss()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun show(targets: List<IntentTarget>, onSelected: (IntentTarget) -> Unit) {
        dismiss()
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val compose = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val current by settings.collectAsStateWithLifecycle(initialValue = LauncherSettings())
                ChikkuSettingsTheme(accent = current.lookFeel.accentColor) {
                    ModalBottomSheet(
                        onDismissRequest = ::dismiss,
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        // Lazy rows with each app's icon, so a long list of handlers stays quick to scan.
                        val cache = rememberAppIconCache()
                        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(24.dp)) {
                            item(key = "title") {
                                Text(stringResource(R.string.search_micro_open_with), style = MaterialTheme.typography.titleLarge)
                            }
                            items(targets) { target ->
                                TargetRow(target, cache) {
                                    if (isShowing) {
                                        dismiss()
                                        onSelected(target)
                                    }
                                }
                            }
                            item(key = "cancel") {
                                TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                    Text(stringResource(android.R.string.cancel))
                                }
                            }
                        }
                    }
                }
            }
        }
        view = compose
        root.addView(compose, FrameLayout.LayoutParams(0, 0))
        activity.lifecycle.addObserver(observer)
    }

    fun dismiss() {
        val shown = view
        view = null
        activity.lifecycle.removeObserver(observer)
        (shown?.parent as? ViewGroup)?.removeView(shown)
    }
}

@Composable
private fun TargetRow(target: IntentTarget, cache: IconBitmapCache, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = rememberIconBitmap("handler|${target.userSerial}|${target.packageName}", TargetIconSize, cache) {
        withContext(Dispatchers.IO) { runCatching { IconShaper.systemSafe(context.packageManager.getApplicationIcon(target.packageName)) }.getOrNull() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconImage(icon, TargetIconSize)
        Spacer(Modifier.width(16.dp))
        Text(target.label, style = MaterialTheme.typography.bodyLarge)
    }
}

private val TargetIconSize = 36.dp
