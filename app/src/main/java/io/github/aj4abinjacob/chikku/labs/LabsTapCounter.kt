package io.github.aj4abinjacob.chikku.labs

import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings

/**
 * Hidden Labs switch (G11): tapping the version number in Settings → About [TAPS_TO_TOGGLE] times
 * in a row toggles `labs.unlocked`. A pause longer than [MAX_GAP_MILLIS] between taps starts over.
 */
class LabsTapCounter(
    private val onToggle: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var taps = 0
    private var lastTapAt = 0L

    fun onTap() {
        val now = clock()
        taps = if (taps > 0 && now - lastTapAt <= MAX_GAP_MILLIS) taps + 1 else 1
        lastTapAt = now
        if (taps >= TAPS_TO_TOGGLE) {
            taps = 0
            onToggle()
        }
    }

    companion object {
        const val TAPS_TO_TOGGLE = 7
        const val MAX_GAP_MILLIS = 1_000L

        fun toggled(settings: LauncherSettings): LauncherSettings =
            settings.copy(labs = settings.labs.copy(unlocked = !settings.labs.unlocked))
    }
}
