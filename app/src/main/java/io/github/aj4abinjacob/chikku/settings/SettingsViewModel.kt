package io.github.aj4abinjacob.chikku.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsRepository
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.platform.HomeRoleController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings state: the stored [LauncherSettings] (null until loaded) and whether Chikku holds the
 * HOME role (read only; null means "not known yet"). Writes run in [appScope] so leaving the screen
 * cannot cancel a commit.
 */
class SettingsViewModel(
    private val roleController: HomeRoleController,
    private val settingsRepository: SettingsRepository,
    private val appScope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _isDefaultHome = MutableStateFlow<Boolean?>(null)
    val isDefaultHome: StateFlow<Boolean?> = _isDefaultHome.asStateFlow()

    val settings: StateFlow<LauncherSettings?> = settingsRepository.settings
        .catch { emit(LauncherSettings()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Applies [transform] to the latest stored settings. */
    fun update(transform: (LauncherSettings) -> LauncherSettings) {
        appScope.launch {
            try {
                settingsRepository.update(transform)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Storage failure: the UI keeps showing the stored value, which is unchanged.
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isDefaultHome.value = try {
                withContext(io) { roleController.isDefaultHome() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        fun factory(deps: HomeDependencies): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(deps.homeRoleController, deps.settingsRepository, deps.appScope) }
        }
    }
}
