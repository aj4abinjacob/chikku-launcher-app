package io.github.aj4abinjacob.chikku.settings.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Whether [permission] is granted, as Compose state. A plain `checkSelfPermission` call during
 * composition is not observed: granting it from a system dialog changes nothing Compose watches,
 * so a page could keep showing the "no permission" state until something else redraws it.
 * This re-checks whenever the screen resumes (after the permission dialog or Android settings);
 * callers can also set the value directly from a permission result.
 */
@Composable
fun rememberPermissionGranted(permission: String): MutableState<Boolean> {
    val context = LocalContext.current
    val granted = remember(context, permission) { mutableStateOf(context.isGranted(permission)) }
    LifecycleResumeEffect(context, permission) {
        granted.value = context.isGranted(permission)
        onPauseOrDispose { }
    }
    return granted
}

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
