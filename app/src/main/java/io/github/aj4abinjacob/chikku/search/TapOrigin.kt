package io.github.aj4abinjacob.chikku.search

import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * Where the last finger went down in a search screen (screen pixels), so an app or shortcut opened
 * from a result can animate out of the tapped spot like one opened from HOME. Results are mixed
 * Compose rows and interop views, so the tap is a common origin for all of them.
 */
class TapOrigin(private val clock: () -> Long = SystemClock::uptimeMillis) {
    private var x = 0
    private var y = 0
    private var at = Long.MIN_VALUE

    fun record(screenX: Int, screenY: Int) {
        x = screenX
        y = screenY
        at = clock()
    }

    /**
     * A [sizePx] square centred on the last tap, or null without a recent one (the keyboard's Go
     * action launches the first result and must not reveal from an old tap).
     */
    fun bounds(sizePx: Int): Rect? {
        if (at == Long.MIN_VALUE || clock() - at > MAX_AGE_MILLIS) return null
        val half = sizePx / 2
        return Rect(x - half, y - half, x + half, y + half)
    }

    private companion object {
        /** Covers a tap's down, up and click dispatch; a long-press opens the menu instead. */
        const val MAX_AGE_MILLIS = 1_000L
    }
}

/** Records every finger-down inside this element into [origin] without consuming it. */
internal fun Modifier.recordTaps(origin: TapOrigin?): Modifier = if (origin == null) this else composed {
    val view = LocalView.current
    pointerInput(origin) {
        awaitPointerEventScope {
            while (true) {
                val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.changedToDown() } ?: continue
                val location = IntArray(2).also(view::getLocationOnScreen)
                origin.record(location[0] + down.position.x.toInt(), location[1] + down.position.y.toInt())
            }
        }
    }
}
