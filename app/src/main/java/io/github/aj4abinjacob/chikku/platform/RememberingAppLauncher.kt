package io.github.aj4abinjacob.chikku.platform

import android.graphics.Rect
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.search.SearchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Remembers successful launches only; recording is asynchronous and gated (opt-in) in the store. */
class RememberingAppLauncher(
    private val delegate: AppLauncher,
    private val history: SearchHistoryRepository,
    private val scope: CoroutineScope,
) : AppLauncher {
    override fun launch(key: AppKey, sourceBounds: Rect?): LaunchResult {
        val result = delegate.launch(key, sourceBounds)
        if (result == LaunchResult.Started) scope.launch { history.record(HistoryTarget.App(key)) }
        return result
    }

    override fun openAppDetails(key: AppKey, sourceBounds: Rect?) = delegate.openAppDetails(key, sourceBounds)
}

/** Remembers successfully started shortcuts (home, folder, card and gesture taps); opt-in in the store. */
class RememberingShortcutStarter(
    private val delegate: ShortcutStarter,
    private val history: SearchHistoryRepository?,
    private val scope: CoroutineScope,
) : ShortcutStarter {
    override fun start(packageName: String, shortcutId: String, userSerial: Long, sourceBounds: Rect?): LaunchResult {
        val result = delegate.start(packageName, shortcutId, userSerial, sourceBounds)
        if (result == LaunchResult.Started && history != null) {
            scope.launch { history.record(HistoryTarget.Shortcut(packageName, shortcutId, userSerial)) }
        }
        return result
    }
}
