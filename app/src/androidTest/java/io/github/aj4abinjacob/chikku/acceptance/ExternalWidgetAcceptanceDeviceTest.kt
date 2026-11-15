package io.github.aj4abinjacob.chikku.acceptance

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import io.github.aj4abinjacob.chikku.DeviceTestSupport
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.WorkspaceItem
import io.github.aj4abinjacob.chikku.core.model.backup.*
import io.github.aj4abinjacob.chikku.data.restore.FileRestoreJournal
import io.github.aj4abinjacob.chikku.home.HomeActivity
import io.github.aj4abinjacob.chikku.home.StartupGuard
import io.github.aj4abinjacob.chikku.home.WorkspacePager
import io.github.aj4abinjacob.chikku.home.widget.ChikkuWidgetHost
import io.github.aj4abinjacob.chikku.home.widget.WidgetCellView
import io.github.aj4abinjacob.chikku.home.widget.WidgetSizing
import io.github.aj4abinjacob.chikku.home.widget.sizeSpec
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Explicitly orchestrated genuine external provider acceptance in fresh acceptance.widgets stores.
 * System consent/configuration are real UI. Never adopts BIND_APPWIDGET or uninstalls/resets apps.
 * Host-side process termination and provider package removal/reinstallation are separate stages.
 */
@RunWith(AndroidJUnit4::class)
class ExternalWidgetAcceptanceDeviceTest {
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun seedFreshSyntheticWidgetSession(): Unit = runBlocking {
        val context = guard()
        AcceptanceStores().use { stores ->
            check(!marker(context).exists()) { "External fixture already exists; do not reset it" }
            check(stores.workspace.exportContent()?.items?.isEmpty() == true && (stores.custom.all()?.isEmpty() == true) &&
                (stores.groups.all()?.isEmpty() == true) && ownIds(context).isEmpty()) { "Fresh production stores and host required; existing acceptance state is preserved" }
            provider(context)
            // Preserve all other preferences; this fresh session only completes onboarding.
            stores.settings.update { it.copy(onboarding = it.onboarding.copy(completed = true)) }
            stores.workspace.seedDefaultsOnce(emptyList())
            writeMarker(context, JSONObject().put("phase", "seeded"))
        }
    }

    @Test fun decliningRealSystemConsentLeavesNoPlacementOrOwnedOrphan(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "seeded")
        val info = provider(context)
        AcceptanceStores().use { stores ->
            val before = stores.workspace.exportContent()
            val ids = ownIds(context)
            withStableHome(context) { scenario ->
                pick(scenario, context, info)
                systemConsent(allow = false)
                check(DeviceTestSupport.waitUntil { runBlocking { stores.workspace.exportContent() == before } && ownIds(context) == ids }) {
                    "Declined binding retained a placement or owned widget ID"
                }
                check(DeviceTestSupport.waitUntil {
                    var homeActive = false
                    scenario.onActivity { homeActive = it.hasWindowFocus() }
                    homeActive
                }) { "Real consent dismissal did not return focus to HOME" }
            }
            writeMarker(context, JSONObject().put("phase", "declined"))
        }
    }

    @Test fun acceptingRealConsentAndExternalConfigurationPlacesHostedWidget(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "declined")
        val info = provider(context)
        AcceptanceStores().use { stores ->
            check(stores.workspace.exportContent()?.items?.isEmpty() == true) { "Fresh widget placement required" }
            withStableHome(context) { scenario ->
                pick(scenario, context, info)
                systemConsent(allow = true)
                configure()
                val item = waitForWidget(stores)
                assertHosted(scenario, item)
                assertOwnedReferences(stores, context, item)
                writeMarker(context, itemMarker(item, "configured").put("options",
                    awaitStableOptions(context, (item.target as ItemTarget.Widget).appWidgetId)))
            }
        }
    }

    @Test fun configuredProductionPlacementIsHostedAfterExternalRestart(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "configured", "rebound")
        provider(context)
        AcceptanceStores().use { stores ->
            val item = recordedWidget(stores, context)
            // More fresh launches than the crash-loop threshold catch fixture-induced unfinished
            // startups. The guard must recover naturally; never reset it between acceptance stages.
            repeat(StartupGuard.DEFAULT_THRESHOLD + 1) { launch ->
                withStableHome(context) { scenario ->
                    assertHosted(scenario, item)
                    assertOwnedReferences(stores, context, item)
                    if (launch == 0) {
                        scenario.recreate() // Actual lifecycle stop/start of the production host.
                        awaitStartupStable(scenario, context)
                        assertHosted(scenario, item)
                    }
                    awaitStableOptions(context, (item.target as ItemTarget.Widget).appWidgetId,
                        JSONObject(marker(context).readText()).getJSONObject("options"))
                }
            }
        }
    }

    @Test fun missingExternalProviderKeepsRecoverableProductionPlacement(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "configured", "rebound")
        check(!installed(context)) { "Host must remove only the separately installed external provider first" }
        AcceptanceStores().use { stores ->
            val item = recordedWidget(stores, context)
            withStableHome(context) { scenario ->
                check(DeviceTestSupport.waitUntil { cellState(scenario, item.id) { it.hostedId == 0 && it.placeholder.isShown } }) {
                    "Retained widget did not expose its recoverable placeholder"
                }
                named(By.desc(context.getString(R.string.widgets_unavailable_description)))
                check(recordedWidget(stores, context) == item) { "Provider removal changed the saved placement" }
            }
            // Android may discard provider-side options on uninstall; preserve our synthetic
            // pre-removal expectation instead of replacing it with an empty system bundle.
            writeMarker(context, JSONObject(marker(context).readText()).put("phase", "missing"))
        }
    }

    @Test fun reinstalledExternalProviderRebindsFreshIdAndRepeatsConfiguration(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "missing")
        provider(context)
        AcceptanceStores().use { stores ->
            val previous = recordedWidget(stores, context)
            withStableHome(context) { scenario ->
                named(By.desc(context.getString(R.string.widgets_unavailable_description))).click()
                // Android may retain this host's consent; configuration must still actually run.
                check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
                    activeControlPresent(EXTERNAL_PACKAGE, "$EXTERNAL_PACKAGE:id/config_digital_save") ||
                        activeControlPresent("com.android.settings", "android:id/button1")
                }) {
                    "Rebind reached neither real consent nor provider configuration"
                }
                if (activeControlPresent("com.android.settings", "android:id/button1")) systemConsent(allow = true)
                configure()
                val fresh = waitForWidget(stores, differentId = (previous.target as ItemTarget.Widget).appWidgetId)
                check(fresh.id == previous.id && fresh.placement == previous.placement && fresh.widgetPadding == previous.widgetPadding &&
                    (fresh.target as ItemTarget.Widget).provider == (previous.target as ItemTarget.Widget).provider) {
                    "Rebind changed the retained placement identity or geometry"
                }
                assertHosted(scenario, fresh)
                assertOwnedReferences(stores, context, fresh)
                val previousMarker = JSONObject(marker(context).readText())
                val expectedOptions = previousMarker.getJSONObject("options").takeUnless { previousMarker.optBoolean("restored") }
                val freshOptions = awaitStableOptions(context, (fresh.target as ItemTarget.Widget).appWidgetId, expectedOptions)
                writeMarker(context, itemMarker(fresh, "rebound").put("options", freshOptions))
            }
        }
    }

    @Test fun exportFullExternalWidgetBackup(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "configured", "rebound")
        provider(context)
        AcceptanceStores().use { stores ->
            val original = recordedWidget(stores, context)
            assertOwnedReferences(stores, context, original)
            val backup = (backupService(stores).create() as BackupCreateResult.Success).backup
            val encoded = BackupCodec.encode(backup)
            val decoded = (BackupCodec.decode(encoded) as BackupDecodeResult.Success).backup
            val restored = decoded.workspace.items.single()
            val restoredTarget = restored.target as? ItemTarget.Widget
            check(restored.id == original.id && restored.placement == original.placement && restored.widgetPadding == original.widgetPadding &&
                restoredTarget != null && restoredTarget.appWidgetId == 0 &&
                restoredTarget.provider == (original.target as ItemTarget.Widget).provider) { "Portable widget identity/geometry changed" }
            check(decoded.summary(emptySet()).widgets == 1 && !encoded.contains("appWidgetId")) { "Backup leaked a local widget binding" }
            withContext(Dispatchers.IO) {
                FileOutputStream(File(checkNotNull(context.getExternalFilesDir(null)), "external-widget-backup.json")).use {
                    it.write(encoded.toByteArray(Charsets.UTF_8)); it.fd.sync()
                }
            }
        }
    }

    /** Continue only the preserved failed rebind, after verifying all its original postconditions. */
    @Test fun retainedFreshBindingKeepsGeometryAndReportsSizeAfterHomeReopen(): Unit = runBlocking {
        val context = guard()
        requirePhase(context, "missing")
        provider(context)
        AcceptanceStores().use { stores ->
            val saved = JSONObject(marker(context).readText())
            val current = checkNotNull(stores.workspace.exportContent()?.items?.singleOrNull())
            val target = current.target as? ItemTarget.Widget
            check(target != null && target.appWidgetId > 0 && target.appWidgetId != saved.getInt("widget")) {
                "Only the already completed fresh binding may resume; do not replace or reset it"
            }
            val observed = itemMarker(current, "missing")
            check(markerKeys.filterNot { it == "widget" }.all { observed.optString(it) == saved.optString(it) }) {
                "Retained binding identity or geometry changed"
            }
            InstrumentationRegistry.getInstrumentation().addResults(Bundle().apply {
                putString("chikkuWidgetOptionsBeforeHome", widgetOptions(context, target.appWidgetId).toString())
                putString("chikkuWidgetExpectedOptions", saved.getJSONObject("options").toString())
            })
            withStableHome(context) { scenario ->
                assertHosted(scenario, current)
                assertOwnedReferences(stores, context, current)
                val options = awaitStableOptions(context, target.appWidgetId, saved.getJSONObject("options"))
                writeMarker(context, itemMarker(current, "rebound").put("options", options))
            }
        }
    }

    @Test fun restoreFreshExternalWidgetBackup(): Unit = runBlocking {
        val context = guard()
        AcceptanceStores().use { stores ->
            check(!marker(context).exists() && stores.workspace.exportContent()?.items?.isEmpty() == true &&
                (stores.custom.all()?.isEmpty() == true) && (stores.groups.all()?.isEmpty() == true) && ownIds(context).isEmpty()) {
                "Fresh installation required; existing acceptance state is preserved"
            }
            provider(context)
            val path = "/data/local/tmp/${context.packageName}-restore.json"
            val encoded = withContext(Dispatchers.IO) {
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cat $path").use { descriptor ->
                    FileInputStream(descriptor.fileDescriptor).use { input ->
                        val bytes = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            check(bytes.size() + count <= BackupCodec.MAX_BYTES) { "Synthetic restore input exceeds backup limit" }
                            bytes.write(buffer, 0, count)
                        }
                        bytes.toByteArray().toString(Charsets.UTF_8)
                    }
                }
            }
            val backup = (BackupCodec.decode(encoded) as BackupDecodeResult.Success).backup
            check(backup.workspace.items.size == 1 && backup.customizations.isEmpty() && backup.drawerGroups.isEmpty()) { "Only the synthetic widget backup is accepted" }
            val expected = backup.workspace.items.single()
            val target = expected.target as? ItemTarget.Widget
            check(target != null && target.provider.packageName == EXTERNAL_PACKAGE && target.provider.className == PROVIDER.className &&
                target.appWidgetId == 0 && target.provider.userSerial == personalSerial(context)) { "Pinned portable widget reference required" }
            check(backupService(stores).restore(backup) == BackupOperationResult.Applied) { "Production service restore failed" }
            stores.workspace.seedDefaultsOnce(emptyList())
            check(stores.workspace.exportContent() == backup.workspace && ownIds(context).isEmpty()) { "Fresh restore reused a local widget binding" }
            check(backup.summary(emptySet()).widgets == 1 && context.resources.getQuantityString(R.plurals.backup_preview_widgets, 1, 1).isNotBlank())
            writeMarker(context, itemMarker(expected, "missing").put("options", widgetOptions(context, 0)).put("restored", true))
            withStableHome(context) { scenario ->
                check(DeviceTestSupport.waitUntil { cellState(scenario, expected.id) { it.hostedId == 0 && it.placeholder.isShown } })
                named(By.desc(context.getString(R.string.widgets_unavailable_description)))
            }
        }
    }

    private fun backupService(stores: AcceptanceStores) = BackupService(stores.settings, stores.workspace, stores.custom,
        stores.groups, "cloud-external-widget", journal = FileRestoreJournal({ stores.context.noBackupFilesDir }, Dispatchers.IO))

    private fun personalSerial(context: Context) = context.getSystemService(UserManager::class.java).getSerialNumberForUser(Process.myUserHandle())

    private suspend fun assertOwnedReferences(stores: AcceptanceStores, context: Context, item: WorkspaceItem) {
        val widget = item.target as ItemTarget.Widget
        check(widget.provider.userSerial == personalSerial(context) && widget.appWidgetId in ownIds(context)) { "Configured widget is not owned by the personal production host" }
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widget.appWidgetId)
        check(info?.provider == PROVIDER && info.profile == Process.myUserHandle()) { "Configured provider/profile binding changed" }
        var retained = emptySet<Int>()
        check(backupService(stores).withWidgetReferences { retained = it }) { "Retained widget references could not be read safely" }
        check(ownIds(context).all { it in retained }) { "An owned widget ID is unreferenced by content or ordinary/durable Undo" }
    }

    private fun guard(): Context {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit cloud external-widget orchestration required", args.getString("externalWidgetFixture") == "true")
        val context = guardedAcceptanceContext()
        check(context.packageName.endsWith(".acceptance.widgets") && args.getString("expectedSchema") == "4" &&
            args.getString("expectedVersionCode") == "910002") { "Pinned fresh widget acceptance identity/version/schema required" }
        return context
    }

    private fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(EXTERNAL_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }

    private fun provider(context: Context): AppWidgetProviderInfo {
        assumeTrue("Pinned external provider is not installed", installed(context))
        check(context.packageManager.getPackageInfo(EXTERNAL_PACKAGE, 0).longVersionCode == 10L) { "Pinned provider version 10 required" }
        val info = AppWidgetManager.getInstance(context).getInstalledProvidersForProfile(Process.myUserHandle())
            .singleOrNull { it.provider == PROVIDER }
        assumeTrue("Pinned provider unavailable to the target UID", info != null)
        return checkNotNull(info).also { check(it.configure == CONFIGURE) { "Genuine provider configuration Activity required" } }
    }

    private suspend fun withStableHome(context: Context, block: suspend (ActivityScenario<HomeActivity>) -> Unit) {
        ActivityScenario.launch<HomeActivity>(
            Intent(context, HomeActivity::class.java).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        ).use { scenario ->
            awaitStartupStable(scenario, context)
            block(scenario)
        }
    }

    /**
     * Let HOME's real post-draw job clear its own guard before the fixture can close it. A recreated
     * activity may inherit an already-zero counter, so that value alone is not a stability signal:
     * require a new frame and a full visible, focused stability interval for this activity too.
     * Read-only inspection deliberately preserves failed-start and sticky-safe-mode evidence.
     */
    private fun awaitStartupStable(scenario: ActivityScenario<HomeActivity>, context: Context) {
        val drawnAt = AtomicLong(0L)
        scenario.onActivity { activity ->
            val decor = activity.window.decorView
            // A separate minified test APK cannot assume that app R8 retains AndroidX's
            // OneShotPreDrawListener ABI. Observe the same real frame through the platform API.
            decor.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (decor.viewTreeObserver.isAlive) decor.viewTreeObserver.removeOnPreDrawListener(this)
                    drawnAt.compareAndSet(0L, SystemClock.uptimeMillis())
                    return true
                }
            })
            decor.invalidate()
        }
        val prefs = context.getSharedPreferences("chikku_startup_guard", Context.MODE_PRIVATE)
        var readySince: Long? = null
        check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            var ready = false
            scenario.onActivity { activity ->
                val pager = activity.findViewById<WorkspacePager>(R.id.workspace_pager)
                val page = pager.pageAt(pager.currentPage)
                ready = activity.lifecycle.currentState == Lifecycle.State.RESUMED && activity.hasWindowFocus() &&
                    pager.isShown && page != null && page.width > 0 && page.height > 0 &&
                    !activity.findViewById<View>(R.id.recovery_panel).isShown
            }
            val now = SystemClock.uptimeMillis()
            if (!ready || drawnAt.get() == 0L) {
                readySince = null
                false
            } else {
                val since = readySince ?: now.also { readySince = it }
                now - since >= StartupGuard.STABLE_DELAY_MS && prefs.all.let { state ->
                    state["unfinished_startups"] == 0 && state["safe_mode"] == false
                }
            }
        }) { "HOME did not stay visible through startup stability with a cleared counter and safe mode off" }
    }

    private fun pick(scenario: ActivityScenario<HomeActivity>, context: Context, info: AppWidgetProviderInfo) {
        var description: String? = null
        check(DeviceTestSupport.waitUntil {
            scenario.onActivity { activity ->
                val pager = activity.findViewById<WorkspacePager>(R.id.workspace_pager)
                val page = pager.pageAt(pager.currentPage)
                if (page != null && page.width > 0 && page.height > 0 && pager.isShown) {
                    val cellW = (page.width - page.paddingLeft - page.paddingRight) / page.grid.columns
                    val cellH = (page.height - page.paddingTop - page.paddingBottom) / page.grid.rows
                    val span = WidgetSizing.defaultSpan(info.sizeSpec(), cellW, cellH, page.grid)
                    description = context.getString(R.string.widgets_picker_entry_description,
                        info.loadLabel(context.packageManager), span.first, span.second)
                }
            }
            description != null
        }) { "HOME workspace did not become ready" }
        scenario.onActivity { activity ->
            val pager = activity.findViewById<WorkspacePager>(R.id.workspace_pager)
            val node = pager.createAccessibilityNodeInfo()
            try {
                val action = node.actionList.singleOrNull { it.label?.toString() == context.getString(R.string.widgets_home_menu_action) }
                check(action != null && pager.performAccessibilityAction(action.id, null)) { "Named HOME options action unavailable" }
            } finally { @Suppress("DEPRECATION") node.recycle() }
        }
        named(By.text(context.getString(R.string.widgets_home_menu_widgets))).click()
        namedProvider(By.desc(checkNotNull(description))).click()
    }

    private fun namedProvider(selector: BySelector): UiObject2 {
        // Catalog loading is asynchronous. The first frame can contain only its progress view;
        // require the named provider or the real scrollable catalog before attempting to scroll.
        check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            device.hasObject(selector) || device.hasObject(By.scrollable(true))
        }) { "Widget picker exposed neither the pinned provider nor its scrollable catalog" }
        repeat(12) {
            if (device.hasObject(selector)) return named(selector)
            val scroller = device.findObjects(By.scrollable(true)).lastOrNull()
                ?: error("Widget picker scroll container unavailable")
            scroller.scroll(Direction.DOWN, 0.7f)
            device.waitForIdle()
        }
        error("Pinned provider was not exposed in the widget picker")
    }

    private fun systemConsent(allow: Boolean) {
        clickActiveControl("com.android.settings", if (allow) "android:id/button1" else "android:id/button2")
    }

    private fun configure() {
        clickActiveControl(EXTERNAL_PACKAGE, "$EXTERNAL_PACKAGE:id/config_digital_save")
    }

    private fun activeControlPresent(packageName: String, resourceId: String): Boolean =
        withActiveControl(packageName, resourceId) { true }

    private fun clickActiveControl(packageName: String, resourceId: String) {
        check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            withActiveControl(packageName, resourceId) { control ->
                if (!control.isEnabled || !control.isClickable) false else {
                    check(control.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { "Named active-window control rejected its click action" }
                    true
                }
            }
        }) { "Required named active-window control did not become actionable" }
    }

    /**
     * Android 10's all-window accessibility traversal dereferences an unbound host provider.
     * Read only the active consent/configuration window, preserving exact package/resource queries
     * and real node actions. A focus transition may briefly leave the unbound HOME active; only
     * that pinned framework NPE is retried within the same bounded wait, never assumption-skipped.
     */
    private fun withActiveControl(packageName: String, resourceId: String, action: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = try {
            InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow
        } catch (failure: NullPointerException) {
            if (Build.VERSION.SDK_INT != 29 || failure.message?.contains("AppWidgetServiceImpl\$Provider.id") != true) throw failure
            return false
        } ?: return false
        try {
            if (root.packageName?.toString() != packageName) return false
            val nodes = root.findAccessibilityNodeInfosByViewId(resourceId)
            try {
                val matching = nodes.filter {
                    it.packageName?.toString() == packageName && it.viewIdResourceName == resourceId && it.isVisibleToUser
                }
                check(matching.size <= 1) { "Named active-window control selector is ambiguous" }
                return matching.singleOrNull()?.let(action) ?: false
            } finally {
                @Suppress("DEPRECATION")
                nodes.forEach { it.recycle() }
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun named(selector: BySelector): UiObject2 {
        check(device.wait(Until.hasObject(selector), DeviceTestSupport.LOAD_TIMEOUT_MS)) { "Required named native control unavailable" }
        val matches = device.findObjects(selector)
        check(matches.size == 1) { "Native control selector is ambiguous" }
        var action = matches.single()
        while (!action.isClickable && action.parent != null) action = action.parent
        return action.also { check(it.isEnabled && it.isClickable) { "Named native control is not actionable" } }
    }

    private fun ownIds(context: Context): Set<Int> = AppWidgetHost(context, ChikkuWidgetHost.HOST_ID).appWidgetIds.toSet()

    private suspend fun waitForWidget(stores: AcceptanceStores, differentId: Int? = null): WorkspaceItem {
        var found: WorkspaceItem? = null
        check(DeviceTestSupport.waitUntil {
            found = runBlocking { stores.workspace.exportContent()?.items?.singleOrNull { item ->
                val widget = item.target as? ItemTarget.Widget
                widget?.provider?.packageName == EXTERNAL_PACKAGE && widget.provider.className == PROVIDER.className &&
                    widget.appWidgetId > 0 && (differentId == null || widget.appWidgetId != differentId)
            } }
            found != null
        }) { "Configured external widget was not durably placed" }
        return checkNotNull(found)
    }

    private fun assertHosted(scenario: ActivityScenario<HomeActivity>, item: WorkspaceItem) {
        val id = (item.target as ItemTarget.Widget).appWidgetId
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.packageManager.getResourcesForApplication(EXTERNAL_PACKAGE)
        val timeIds = listOf("widget_text_clock_24", "widget_text_clock_12").map {
            resources.getIdentifier(it, "id", EXTERNAL_PACKAGE)
        }.filter { it != 0 }
        check(timeIds.size == 2) { "Pinned provider's actual clock resources missing" }
        check(DeviceTestSupport.waitUntil { cellState(scenario, item.id) {
            it.hostedId == id && it.width > 0 && it.hostView?.width?.let { width -> width > 0 } == true &&
                it.hostView?.appWidgetInfo?.provider == PROVIDER && it.hostView?.isShown == true &&
                it.placeholder.visibility == View.GONE && timeIds.any { timeId ->
                    it.hostView?.findViewById<TextClock>(timeId)?.let { clock -> clock.isShown && clock.width > 0 && clock.text.isNotBlank() } == true
                }
        } }) { "Production HOME did not host the configured external widget" }
    }

    private fun cellState(scenario: ActivityScenario<HomeActivity>, itemId: String, predicate: (WidgetCellView) -> Boolean): Boolean {
        var result = false
        fun visit(view: View) {
            if (view is WidgetCellView) {
                val model = view.getTag(R.id.home_item_model) as? io.github.aj4abinjacob.chikku.home.HomeItemUi
                if (model?.id == itemId) result = predicate(view)
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        scenario.onActivity { visit(it.window.decorView) }
        return result
    }

    private suspend fun recordedWidget(stores: AcceptanceStores, context: Context): WorkspaceItem {
        val saved = JSONObject(marker(context).readText())
        val item = stores.workspace.exportContent()?.items?.singleOrNull { it.id == saved.getString("item") }
        check(item != null && markerKeys.all {
            itemMarker(item, saved.getString("phase")).optString(it) == saved.optString(it)
        }) {
            "Synthetic fixture placement changed; do not overwrite it"
        }
        val widgetId = (checkNotNull(item).target as ItemTarget.Widget).appWidgetId
        if (widgetId > 0 && AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) != null) {
            awaitStableOptions(context, widgetId, saved.getJSONObject("options"))
        }
        return checkNotNull(item)
    }

    private fun itemMarker(item: WorkspaceItem, phase: String): JSONObject {
        val widget = item.target as ItemTarget.Widget
        check(widget.provider.packageName == EXTERNAL_PACKAGE && widget.provider.className == PROVIDER.className)
        val place = item.placement
        return JSONObject().put("phase", phase).put("item", item.id).put("widget", widget.appWidgetId)
            .put("serial", widget.provider.userSerial).put("container", place.container.name).put("page", place.page)
            .put("x", place.x).put("y", place.y).put("spanX", place.spanX).put("spanY", place.spanY)
            .put("widgetPadding", item.widgetPadding?.name ?: "default")
    }

    private fun widgetOptions(context: Context, widgetId: Int): JSONObject {
        val options = if (widgetId > 0) AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId) else null
        return JSONObject().also { value -> optionKeys.forEach { value.put(it, options?.getInt(it) ?: 0) } }
    }

    /** Binder option updates can lag the hosted View; require two complete matching polls. */
    private fun awaitStableOptions(context: Context, widgetId: Int, expected: JSONObject? = null): JSONObject {
        var previous: List<Int>? = null
        var stable = 0
        var latest = JSONObject()
        check(DeviceTestSupport.waitUntil(DeviceTestSupport.LOAD_TIMEOUT_MS) {
            latest = widgetOptions(context, widgetId)
            val values = optionKeys.map { latest.getInt(it) }
            val matches = values.all { it > 0 } && (expected == null || optionKeys.all { latest.getInt(it) == expected.getInt(it) })
            stable = if (matches && values == previous) stable + 1 else if (matches) 1 else 0
            previous = values
            stable >= 2
        }) { "Provider size options did not become positive, stable and consistent with the saved placement: expected=$expected; actual=$latest" }
        return latest
    }

    private fun requirePhase(context: Context, vararg expected: String) {
        check(marker(context).isFile && JSONObject(marker(context).readText()).getString("phase") in expected) {
            "External widget fixture stage prerequisite missing"
        }
    }

    private fun writeMarker(context: Context, value: JSONObject) {
        FileOutputStream(marker(context)).use { it.write(value.toString().toByteArray(Charsets.UTF_8)); it.fd.sync() }
    }

    private fun marker(context: Context) = File(context.noBackupFilesDir, "external-widget-acceptance.json")

    private companion object {
        const val EXTERNAL_PACKAGE = "org.fossify.clock"
        val PROVIDER = ComponentName(EXTERNAL_PACKAGE, "org.fossify.clock.helpers.MyDigitalTimeWidgetProvider")
        val CONFIGURE = ComponentName(EXTERNAL_PACKAGE, "org.fossify.clock.activities.WidgetDigitalConfigureActivity")
        val markerKeys = listOf("phase", "item", "widget", "serial", "container", "page", "x", "y", "spanX", "spanY", "widgetPadding")
        val optionKeys = listOf(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
    }
}

