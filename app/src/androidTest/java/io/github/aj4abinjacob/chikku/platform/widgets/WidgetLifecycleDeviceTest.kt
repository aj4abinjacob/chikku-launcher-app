package io.github.aj4abinjacob.chikku.platform.widgets

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.core.model.*
import io.github.aj4abinjacob.chikku.data.ChikkuDatabase
import io.github.aj4abinjacob.chikku.data.RoomWorkspaceRepository
import io.github.aj4abinjacob.chikku.home.widget.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Real native host with a test-only provider/host/database. No permission grant or user widget edit. */
@RunWith(AndroidJUnit4::class)
class WidgetLifecycleDeviceTest {
    @Test fun nativeBindingViewOptionsRestartProviderLossAndFreshRebind() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val provider = ComponentName(instrumentation.context.packageName, ReliabilityWidgetProvider::class.java.name)
        val key = ComponentKey(provider.packageName, provider.className, DeviceTestSupport.personalSerial())
        val pm = context.packageManager
        val originalEnabled = pm.getComponentEnabledSetting(provider)
        val manager = AppWidgetManager.getInstance(context)
        val host = AppWidgetHost(context, 0x524C)
        val allocated = mutableSetOf<Int>()
        val db = Room.inMemoryDatabaseBuilder(context, ChikkuDatabase::class.java).build()
        val repository = RoomWorkspaceRepository(db, Dispatchers.IO)
        // Shell identity is scoped to this test and always dropped; no stored bind consent changes.
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET,
            "android.permission.CHANGE_COMPONENT_ENABLED_STATE")
        try {
            val platform = AndroidWidgetPlatform(context, host)
            val id = platform.allocateId().also { allocated += it }
            assertThat(id).isNotEqualTo(0)
            assertThat(platform.bindIfAllowed(id, key)).isTrue()
            assertThat(platform.boundProvider(id)).isEqualTo(key)
            val placement = CellPlacement(ContainerType.DESKTOP, 0, 0, 0)
            val item = (repository.addWidget(id, key, placement) as EditResult.Applied).itemId
            lateinit var view: AppWidgetHostView
            instrumentation.runOnMainSync {
                host.startListening()
                view = host.createView(context, id, manager.getAppWidgetInfo(id))
            }
            // Use an explicit fixture action to publish from the actual provider UID. Android
            // owns APPWIDGET_UPDATE, whose background delivery can be delayed on this OEM.
            instrumentation.uiAutomation.executeShellCommand(
                "am broadcast -a $RELIABILITY_WIDGET_REFRESH -n ${provider.flattenToString()} --include-stopped-packages --receiver-foreground",
            ).use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
            assertThat(DeviceTestSupport.waitUntil {
                var matches = false
                instrumentation.runOnMainSync { matches = view.findViewById<TextView>(android.R.id.text1)?.text?.toString() == "Reliable widget" }
                matches
            }).isTrue()
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 120)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 120)
            })
            assertThat(manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)).isEqualTo(120)
            instrumentation.runOnMainSync { host.stopListening(); host.startListening() }
            assertThat(platform.boundProvider(id)).isEqualTo(key)
            pm.setComponentEnabledSetting(provider, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            assertThat(DeviceTestSupport.waitUntil { platform.boundProvider(id) == null }).isTrue()
            assertThat(repository.exportContent()!!.items.single().target).isEqualTo(ItemTarget.Widget(id, key))
            assertThat(widgetDisplayState(id, key, platform.boundProvider(id))).isInstanceOf(WidgetDisplayState.NeedsRebind::class.java)
            pm.setComponentEnabledSetting(provider, originalEnabled, PackageManager.DONT_KILL_APP)
            assertThat(DeviceTestSupport.waitUntil { WidgetCatalog(context).find(key) != null }).isTrue()
            val fresh = platform.allocateId().also { allocated += it }
            assertThat(fresh).isNotEqualTo(id)
            assertThat(platform.bindIfAllowed(fresh, key)).isTrue()
            assertThat(repository.rebindWidget(item, ItemTarget.Widget(id, key), fresh)).isInstanceOf(EditResult.Applied::class.java)
            assertThat(repository.exportContent()!!.items.single().placement).isEqualTo(placement)
            assertThat(repository.exportContent()!!.items.single().target).isEqualTo(ItemTarget.Widget(fresh, key))
        } finally {
            try {
                pm.setComponentEnabledSetting(provider, originalEnabled, PackageManager.DONT_KILL_APP)
                instrumentation.runOnMainSync { host.stopListening() }
                allocated.filter { it != 0 }.forEach(host::deleteAppWidgetId)
                host.deleteHost()
            } finally {
                db.close()
                instrumentation.uiAutomation.dropShellPermissionIdentity()
            }
        }
    }
}
