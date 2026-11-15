package io.github.aj4abinjacob.chikku.acceptance

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.UserManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.core.model.settings.*
import io.github.aj4abinjacob.chikku.data.*
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Copy this file into the historical checkout and compile it against that checkout's own APIs.
 * This deliberately uses production store names: the package must be an isolated acceptance ID.
 * Ordinary connected suites skip before touching any store. No real app inventory is queried.
 */
@RunWith(AndroidJUnit4::class)
class HistoricalUpgradeSeedDeviceTest {
    @Test fun seedHistoricalProductionState(): Unit = runBlocking {
        AcceptanceStores().use { stores ->
            assertThat(stores.workspace.exportContent()?.items).isEmpty()
            assertThat(stores.custom.all()).isEmpty()
            assertThat(stores.groups.all()).isEmpty()
            stores.settings.update { originalAcceptanceSettings() }
            assertThat(stores.workspace.replaceAll(originalAcceptanceWorkspace())).isInstanceOf(EditResult.Applied::class.java)
            assertThat(stores.custom.replaceAll(originalAcceptanceCustomizations())).isTrue()
            assertThat(stores.groups.replaceAll(originalAcceptanceGroups())).isTrue()
            // A later real HOME launch must not append device-default apps to this synthetic state.
            stores.workspace.seedDefaultsOnce(emptyList())
            stores.assertOriginalState()
        }
    }
}

/** Shared with current-APK acceptance; no custom DB name or in-memory replacement is used here. */
internal class AcceptanceStores : AutoCloseable {
    val context: Context = guardedAcceptanceContext()
    val database = ChikkuDatabaseFactory.create(context)
    val settings = SharedPreferencesSettingsRepository(context, Dispatchers.IO)
    val workspace = RoomWorkspaceRepository(database, Dispatchers.IO)
    val custom = RoomAppCustomizationRepository(database, Dispatchers.IO)
    val groups = RoomDrawerGroupRepository(database, Dispatchers.IO)

    init {
        val expected = InstrumentationRegistry.getArguments().getString("expectedSchema")!!.toInt()
        database.openHelper.writableDatabase.query("PRAGMA user_version").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(expected)
        }
    }

    suspend fun assertOriginalState(widgetId: Int = 7301) {
        assertThat(workspace.exportContent()).isEqualTo(originalAcceptanceWorkspace(widgetId))
        assertThat(custom.all()).containsExactlyElementsIn(originalAcceptanceCustomizations())
        assertThat(groups.all()).containsExactlyElementsIn(originalAcceptanceGroups())
        val actual = settings.settings.first()
        // New-version defaults can add fields; these are the historical sentinel values.
        assertThat(actual.onboarding.completed).isTrue()
        assertThat(actual.home.desktopColumns).isEqualTo(6)
        assertThat(actual.lookFeel.iconPackPackage).isEqualTo("synthetic.acceptance.missing.icons")
        assertThat(actual.dock.backgroundImageUri).isEqualTo("content://synthetic.acceptance.missing/image")
        assertThat(actual.search.onlineSuggestions).isFalse()
    }

    override fun close() = database.close()
}

internal fun guardedAcceptanceContext(): Context {
    val arguments = InstrumentationRegistry.getArguments()
    val expectedId = arguments.getString("acceptanceApplicationId")
    // Skip normal connected runs before opening production stores or writing preferences.
    assumeNotNull(expectedId)
    require(Regex("io\\.github\\.aj4abinjacob\\.chikku\\.acceptance\\.[a-z][a-z0-9_]{0,47}").matches(expectedId!!))
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    require(context.packageName == expectedId) { "Acceptance target does not match isolated ID" }
    require(arguments.getString("acceptanceDisposableEmulator") == "true")
    // A direct instrumentation invocation must enforce the same cloud/emulator restriction as
    // the host runner; an isolated package ID alone is not permission to modify a physical phone.
    InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { descriptor ->
        FileInputStream(descriptor.fileDescriptor).use { input ->
            require(input.readBytes().toString(Charsets.UTF_8).trim() == "1")
        }
    }
    require(arguments.getString("expectedVersionCode") != null && arguments.getString("expectedSchema") != null)
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    assertThat(info.longVersionCode).isEqualTo(arguments.getString("expectedVersionCode")!!.toLong())
    assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE).isEqualTo(0)
    val users = context.getSystemService(UserManager::class.java)
    assertThat(users.isSystemUser).isTrue()
    assertThat(users.isUserUnlocked).isTrue()
    return context
}

internal fun acceptanceApp() = AppKey("synthetic.acceptance.app", "synthetic.acceptance.app.Main", 0)

internal fun originalAcceptanceSettings() = LauncherSettings(
    onboarding = OnboardingSettings(completed = true),
).let { it.copy(
    home = it.home.copy(desktopColumns = 6),
    lookFeel = it.lookFeel.copy(iconPackPackage = "synthetic.acceptance.missing.icons"),
    dock = it.dock.copy(backgroundImageUri = "content://synthetic.acceptance.missing/image"),
) }

internal fun originalAcceptanceWorkspace(widgetId: Int = 7301) = WorkspaceContent(2, listOf(
    WorkspaceItem("acceptance-app", CellPlacement(ContainerType.DESKTOP, 0, 0, 0), ItemTarget.App(acceptanceApp())),
    WorkspaceItem("acceptance-folder", CellPlacement(ContainerType.DESKTOP, 0, 1, 0), ItemTarget.Folder("Synthetic folder", listOf(
        FolderEntry("acceptance-member", ItemTarget.App(acceptanceApp())),
        FolderEntry("acceptance-shortcut", ItemTarget.Shortcut("synthetic.acceptance.app", "synthetic-shortcut", 0,
            "Synthetic shortcut", "synthetic.acceptance.missing.icons", "synthetic_drawable")),
    ))),
    WorkspaceItem("acceptance-widget", CellPlacement(ContainerType.DESKTOP, 1, 0, 0, 2, 2),
        ItemTarget.Widget(widgetId, ComponentKey("synthetic.acceptance.missing.widget", "Provider", 0))),
))

internal fun originalAcceptanceCustomizations() = listOf(AppCustomization(acceptanceApp(), hidden = true,
    customLabel = "Synthetic alias", iconPackPackage = "synthetic.acceptance.missing.icons", iconDrawableName = "synthetic_drawable"))

internal fun originalAcceptanceGroups() = listOf(DrawerGroup("acceptance-group", "Synthetic group", 0,
    DrawerSortMode.MANUAL, listOf(acceptanceApp())))
