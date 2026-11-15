package io.github.aj4abinjacob.chikku.data.restore

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettingsCodec
import io.github.aj4abinjacob.chikku.core.model.settings.SettingsImportResult
import io.github.aj4abinjacob.chikku.data.SharedPreferencesSettingsRepository
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
class SettingsCommitFailureTest {
    @Test fun unsuccessfulCommitNeverReportsAppliedOrPublishesTheUnsavedValue() = runTest {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val name = "commit-failure-${UUID.randomUUID()}"
        var fail = false
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val preferences = base.getSharedPreferences(name, mode)
                return object : SharedPreferences by preferences {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = preferences.edit()
                        lateinit var proxy: SharedPreferences.Editor
                        proxy = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
                            arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                            if (method.name == "commit" && fail) false
                            else method.invoke(editor, *(args ?: emptyArray())).let { if (it === editor) proxy else it }
                        } as SharedPreferences.Editor
                        return proxy
                    }
                }
            }
        }
        try {
            val repository = SharedPreferencesSettingsRepository(context, Dispatchers.IO, name)
            val before = repository.settings.first()
            val next = before.copy(home = before.home.copy(desktopColumns = 4))
            fail = true
            assertThat(repository.importJson(LauncherSettingsCodec.encode(next))).isInstanceOf(SettingsImportResult.Rejected::class.java)
            assertThat(repository.settings.first()).isEqualTo(before)
            try { repository.update { next }; error("Expected failed save") } catch (_: IOException) { }
            assertThat(repository.settings.first()).isEqualTo(before)
            fail = false
            assertThat(repository.importJson(LauncherSettingsCodec.encode(next))).isEqualTo(SettingsImportResult.Applied)
            assertThat(repository.settings.first()).isEqualTo(next)
        } finally { base.deleteSharedPreferences(name) }
    }
}
