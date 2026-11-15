package io.github.aj4abinjacob.chikku.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardsSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CardSettingsRecoveryTest {
    @Test fun malformedStoredCardIsPreservedAndInvalidImportCannotOverwriteValidCards() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SharedPreferencesSettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val damaged = """{"cards":{"items":[{"id":"saved","title":"Card","actions":["unknown"]}]}}"""
        prefs.edit().clear().putString(SharedPreferencesSettingsRepository.KEY_SETTINGS, damaged).commit()
        val repository = SharedPreferencesSettingsRepository(context, Dispatchers.IO)
        assertThat(repository.settings.first()).isEqualTo(LauncherSettings())
        assertThat(prefs.getString(SharedPreferencesSettingsRepository.KEY_DAMAGED, null)).isEqualTo(damaged)
        val valid = CardsSettings(listOf(AutomationCard("valid", "Morning")))
        repository.update { it.copy(cards = valid) }
        assertThat(repository.importJson(damaged)).isInstanceOf(SettingsImportResult.Rejected::class.java)
        assertThat(repository.settings.first().cards).isEqualTo(valid)
        assertThat(SharedPreferencesSettingsRepository(context, Dispatchers.IO).settings.first().cards).isEqualTo(valid)
        assertThat(prefs.getString(SharedPreferencesSettingsRepository.KEY_DAMAGED, null)).isEqualTo(damaged)
    }
}
