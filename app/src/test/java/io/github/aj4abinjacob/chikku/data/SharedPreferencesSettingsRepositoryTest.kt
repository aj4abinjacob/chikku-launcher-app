package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedPreferencesSettingsRepositoryTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)

    private fun newRepo() = SharedPreferencesSettingsRepository(context, Dispatchers.IO)

    @Test
    fun emptyStoreEmitsDefaults() = runTest {
        assertThat(newRepo().settings.first()).isEqualTo(LauncherSettings())
    }

    @Test
    fun updatesPersistAcrossInstancesAndAreCoerced() = runTest {
        val repo = newRepo()
        repo.update { it.copy(home = it.home.copy(desktopColumns = 6, iconScale = 99f, googleDiscoverEnabled = true), gestures = it.gestures.copy(doubleTap = GestureAction.LockScreen)) }
        val current = repo.settings.first()
        assertThat(current.home.desktopColumns).isEqualTo(6)
        assertThat(current.home.iconScale).isEqualTo(LauncherSettings.MAX_SCALE)

        val reopened = newRepo().settings.first()
        assertThat(reopened).isEqualTo(current)
        assertThat(reopened.gestures.doubleTap).isEqualTo(GestureAction.LockScreen)
        assertThat(reopened.home.googleDiscoverEnabled).isTrue()
    }

    @Test
    fun resetRestoresDefaults() = runTest {
        val repo = newRepo()
        repo.update { it.copy(labs = it.labs.copy(unlocked = true)) }
        repo.reset()
        assertThat(repo.settings.first()).isEqualTo(LauncherSettings())
        assertThat(newRepo().settings.first()).isEqualTo(LauncherSettings())
    }

    @Test
    fun exportImportRoundTripAndInvalidImportChangesNothing() = runTest {
        val repo = newRepo()
        repo.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
        val json = repo.exportJson()
        repo.reset()
        assertThat(repo.importJson(json)).isEqualTo(SettingsImportResult.Applied)
        assertThat(repo.settings.first().onboarding.completed).isTrue()

        assertThat(repo.importJson("not json")).isInstanceOf(SettingsImportResult.Rejected::class.java)
        assertThat(repo.settings.first().onboarding.completed).isTrue()
    }

    @Test
    fun damagedStoredValueIsPreservedNotSilentlyOverwritten() = runTest {
        prefs().edit().putString(SharedPreferencesSettingsRepository.KEY_SETTINGS, "{broken").commit()
        assertThat(newRepo().settings.first()).isEqualTo(LauncherSettings())
        assertThat(prefs().getString(SharedPreferencesSettingsRepository.KEY_DAMAGED, null)).isEqualTo("{broken")
    }
}
