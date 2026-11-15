package io.github.aj4abinjacob.chikku.home

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import io.github.aj4abinjacob.chikku.settings.LocalHomeDependencies
import io.github.aj4abinjacob.chikku.settings.components.LocalDrawerScrollSettings

/**
 * Shows the settings' Compose dialogs (gesture action picker, swipe-actions editor) over HOME,
 * which is View-based. One dialog at a time: a zero-size [ComposeView] is added to [root] while it
 * is shown and removed on dismiss. The dialogs read [LocalHomeDependencies] like in settings.
 */
class ComposeDialogHost(
    private val root: FrameLayout,
    private val deps: HomeDependencies,
    private val settings: () -> LauncherSettings,
) {
    private var view: ComposeView? = null

    val isShowing: Boolean get() = view != null

    /** Shows [content]; it calls the given dismiss function when it is done. */
    fun show(content: @Composable (dismiss: () -> Unit) -> Unit) {
        dismiss()
        val accent = settings().lookFeel.accentColor
        val drawer = settings().drawer
        val compose = ComposeView(root.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                ChikkuSettingsTheme(accent = accent) {
                    CompositionLocalProvider(LocalHomeDependencies provides deps, LocalDrawerScrollSettings provides drawer) {
                        content(::dismiss)
                    }
                }
            }
        }
        view = compose
        root.addView(compose, FrameLayout.LayoutParams(0, 0))
    }

    fun dismiss() {
        val current = view ?: return
        view = null
        (current.parent as? ViewGroup)?.removeView(current)
    }
}
