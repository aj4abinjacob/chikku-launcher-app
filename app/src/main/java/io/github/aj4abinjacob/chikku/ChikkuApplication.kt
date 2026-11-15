package io.github.aj4abinjacob.chikku

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.HomeDependenciesProvider
import io.github.aj4abinjacob.chikku.theme.NightModeController
import io.github.aj4abinjacob.chikku.theme.NightModeOwner

class ChikkuApplication : Application(), HomeDependenciesProvider, NightModeOwner {
    private val lazyContainer = lazy { AppContainer(this) }

    /** Process-wide dependency graph, created lazily so a failing component cannot block startup. */
    val container: AppContainer by lazyContainer

    override val homeDependencies: HomeDependencies get() = container

    /** Launcher night mode for Chikku's own activities; read in attachBaseContext, so kept outside the container. */
    override val nightModeController: NightModeController by lazy { NightModeController(this) }

    /** Forwards memory pressure to process-wide caches; never creates the container just to trim it. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (lazyContainer.isInitialized()) container.trimMemory(level)
    }

    @Deprecated("Superseded by onTrimMemory; some builds still deliver it.")
    @Suppress("DEPRECATION")
    override fun onLowMemory() {
        super.onLowMemory()
        if (lazyContainer.isInitialized()) container.trimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }
}
