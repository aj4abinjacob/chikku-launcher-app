package io.github.aj4abinjacob.chikku

import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.ContinuationInterceptor

/**
 * Returns Compose's main dispatcher to its initial state. Call it first in a test that drives
 * input or animation through Compose coroutines.
 *
 * There is one `AndroidUiDispatcher.Main` per Robolectric sandbox, and Robolectric clears the main
 * looper after each test. A test that ends with a Compose dispatch posted leaves the dispatcher
 * marked as scheduled with nothing in the looper, so it never posts again: pointer input stops
 * resuming in later tests of that sandbox (a drag does nothing). Whether that happens depended on
 * which tests ran before and on sandbox caching. The queued work belongs to a finished test and is
 * dropped. The fields are Compose internals, so this throws if a Compose update renames them.
 */
fun resetComposeMainDispatcher() {
    val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as AndroidUiDispatcher
    fun field(name: String) = AndroidUiDispatcher::class.java.getDeclaredField(name).apply { isAccessible = true }
    synchronized(field("lock").get(dispatcher)!!) {
        (field("toRunTrampolined").get(dispatcher) as MutableCollection<*>).clear()
        (field("toRunOnFrame").get(dispatcher) as MutableCollection<*>).clear()
        field("scheduledTrampolineDispatch").setBoolean(dispatcher, false)
        field("scheduledFrameDispatch").setBoolean(dispatcher, false)
    }
}
