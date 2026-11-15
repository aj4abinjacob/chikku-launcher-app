package io.github.aj4abinjacob.chikku.platform

import android.content.Intent

/** Queues a user-requested handoff; true means accepted, including pending selection/authentication. */
fun interface ExternalIntentStarter {
    fun start(intents: List<Intent>): Boolean

    companion object {
        /** Missing UI wiring must never fall back to starting an unchecked activity. */
        val DENY = ExternalIntentStarter { false }
    }
}
