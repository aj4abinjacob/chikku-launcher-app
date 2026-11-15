package io.github.aj4abinjacob.chikku.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import java.io.IOException
import java.lang.reflect.Proxy
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DamagedSettingsRecoveryTest {
    @Test fun failedDamagePreservationBlocksReplacementUntilTheCopyIsDurable() = runTest {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "damaged-settings-${UUID.randomUUID()}"
        val prefs = base.getSharedPreferences(name, Context.MODE_PRIVATE)
        prefs.edit().putString(SharedPreferencesSettingsRepository.KEY_SETTINGS, "{broken").commit()
        var refuseCopy = true
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    var copyingDamage = false
                    lateinit var proxy: SharedPreferences.Editor
                    proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                        if (method.name == "putString" && args?.get(0) == SharedPreferencesSettingsRepository.KEY_DAMAGED) copyingDamage = true
                        if (method.name == "commit" && copyingDamage && refuseCopy) false
                        else method.invoke(editor, *(args ?: emptyArray())).let { if (it === editor) proxy else it }
                    } as SharedPreferences.Editor
                    return proxy
                }
            }
        }
        try {
            val repo = SharedPreferencesSettingsRepository(context, Dispatchers.IO, name)
            assertThat(repo.settings.first()).isEqualTo(LauncherSettings())
            val changed = LauncherSettings(home = LauncherSettings().home.copy(desktopColumns = 4))
            assertThat(SharedPreferencesSettingsRepository(context, Dispatchers.IO, name)
                .importJson(LauncherSettingsCodec.encode(changed))).isInstanceOf(SettingsImportResult.Rejected::class.java)
            try {
                SharedPreferencesSettingsRepository(context, Dispatchers.IO, name).reset()
                error("Expected refused initial reset")
            } catch (_: IOException) { }
            assertThat(repo.importJson(LauncherSettingsCodec.encode(changed))).isInstanceOf(SettingsImportResult.Rejected::class.java)
            try { repo.update { changed }; error("Expected refused replacement") } catch (_: IOException) { }
            try { repo.reset(); error("Expected refused reset") } catch (_: IOException) { }
            assertThat(prefs.getString(SharedPreferencesSettingsRepository.KEY_SETTINGS, null)).isEqualTo("{broken")
            assertThat(repo.settings.first()).isEqualTo(LauncherSettings())
            refuseCopy = false
            assertThat(repo.importJson(LauncherSettingsCodec.encode(changed))).isEqualTo(SettingsImportResult.Applied)
            assertThat(prefs.getString(SharedPreferencesSettingsRepository.KEY_DAMAGED, null)).isEqualTo("{broken")
            assertThat(SharedPreferencesSettingsRepository(base, Dispatchers.IO, name).settings.first()).isEqualTo(changed)
        } finally { base.deleteSharedPreferences(name) }
    }
}
