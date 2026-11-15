package io.github.aj4abinjacob.chikku.settings.iconpicker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.EditResult
import io.github.aj4abinjacob.chikku.core.model.ItemTarget
import io.github.aj4abinjacob.chikku.core.model.ProfileType
import io.github.aj4abinjacob.chikku.core.model.Workspace
import io.github.aj4abinjacob.chikku.core.model.WorkspaceState
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightMode
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.homeDependencies
import io.github.aj4abinjacob.chikku.platform.ShortcutIconLoader
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.platform.icons.IconStyle
import io.github.aj4abinjacob.chikku.settings.ChikkuSettingsTheme
import io.github.aj4abinjacob.chikku.settings.components.LocalDrawerScrollSettings
import io.github.aj4abinjacob.chikku.settings.components.settingsPageColor
import io.github.aj4abinjacob.chikku.theme.NightModeController
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Per-app icon picker: (choose app) -> default icon or an icon pack -> searchable grid of the pack's
 * drawables, with the pack's own icon for the app suggested first. The choice is stored as
 * `AppCustomization.iconPackPackage` + `iconDrawableName`; "Default icon" clears it. Icons stay in
 * the pack's APK; nothing is copied into Chikku. Styled like Chikku settings, in the user's accent.
 * Opened with [packChooserIntent] it instead chooses the launcher-wide icon pack, and with
 * [shortcutIntent] the icon of one pinned shortcut (stored on its workspace entry; no app step).
 */
class IconPickerActivity : ComponentActivity() {

    private lateinit var deps: HomeDependencies
    private lateinit var loader: IconPickerLoader
    private var createdNight: NightMode = NightMode.SYSTEM
    private val snackbar = SnackbarHostState()

    /** True while a choice is being written, so a second tap cannot race the first. */
    private var saving = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        createdNight = NightModeController.applyOverride(this, newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        deps = homeDependencies()
        loader = IconPickerLoader(this, deps.iconPackRepository ?: IconPackRepository(this), deps.iconProvider, ShortcutIconLoader(this, deps.appLock, sharedPacks = deps.iconPackRepository))
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialKey = keyFrom(intent)
        val choosingPack = intent?.getBooleanExtra(EXTRA_CHOOSE_PACK, false) == true
        val shortcut = shortcutFrom(intent)
        setContent {
            val settings by remember { deps.settingsRepository.settings.catch { } }
                .collectAsStateWithLifecycle(initialValue = null)
            val customizations by remember { deps.appCustomizationRepository.customizations.catch { emit(emptyMap()) } }
                .collectAsStateWithLifecycle(initialValue = null)
            val apps by remember { deps.appsRepository.apps.catch { emit(AppsState.Loaded(emptyList())) } }
                .collectAsStateWithLifecycle(initialValue = AppsState.Loading)
            val workspace by remember { deps.workspaceRepository.workspace.catch { emit(WorkspaceState.Failed("")) } }
                .collectAsStateWithLifecycle(initialValue = WorkspaceState.Loading)
            ChikkuSettingsTheme(accent = settings?.lookFeel?.accentColor, speed = settings?.lookFeel?.animationSpeed) {
                // The status bar sits on the accent-colored top bar; keep its icons readable on light accents.
                val lightStatusBar = MaterialTheme.colorScheme.primaryContainer.luminance() > LIGHT_LUMINANCE
                SideEffect {
                    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = lightStatusBar
                }
                CompositionLocalProvider(LocalDrawerScrollSettings provides (settings?.drawer ?: DrawerSettings())) {
                    if (shortcut != null) {
                        ShortcutPicker(shortcut, settings, workspace, apps)
                    } else {
                        IconPickerRoot(initialKey, choosingPack, settings, customizations, apps)
                    }
                }
            }
        }
    }

    /** Where the picker is: no [target] = choosing an app; no [pack] = choosing default or a pack. */
    private data class Step(val target: IconTarget?, val pack: String?, val packLabel: String?) {
        val depth: Int get() = when {
            target == null -> 0
            pack == null -> 1
            else -> 2
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun IconPickerRoot(
        initialKey: AppKey?,
        choosingPack: Boolean,
        settings: LauncherSettings?,
        customizations: Map<AppKey, AppCustomization>?,
        appsState: AppsState,
    ) {
        var target by rememberSaveable(stateSaver = AppKeySaver) { mutableStateOf(initialKey) }
        val apps = (appsState as? AppsState.Loaded)?.apps
        val key = target
        val label = key?.let { k -> customizations?.get(k)?.customLabel ?: apps?.firstOrNull { it.key == k }?.label }
            ?: stringResource(R.string.icon_picker_unknown_app)
        PickerScaffold(
            target = key?.let(IconTarget::App),
            targetLabel = label,
            current = key?.let { customizations?.get(it) }?.toOverride(),
            choosingPack = choosingPack,
            settings = settings,
            backFromTarget = if (initialKey == null && !choosingPack) ({ target = null }) else null,
            chooseTarget = { AppStep(apps, deps.appSearcher, loader, it) { picked -> target = picked } },
            onSave = { pack, drawable -> key?.let { save(it, pack, drawable) } },
        )
    }

    /** The icon of one pinned shortcut, followed live from the workspace; closes if the shortcut goes away. */
    @Composable
    private fun ShortcutPicker(ref: ShortcutRef, settings: LauncherSettings?, workspace: WorkspaceState, apps: AppsState) {
        val found = (workspace as? WorkspaceState.Ready)?.workspace?.let { ref.findAvailable(it, apps) }
        if (workspace !is WorkspaceState.Loading && apps !is AppsState.Loading && found == null) {
            SideEffect { if (!saving) finish() }
            return
        }
        PickerScaffold(
            target = found?.let { s ->
                IconTarget.Shortcut(
                    ref.itemId, ref.folderId, s.packageName, s.shortcutId, s.userSerial,
                    s.iconPackPackage?.let { pack -> s.iconDrawableName?.let { CurrentOverride(pack, it) } },
                )
            },
            targetLabel = found?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.icon_picker_unknown_app),
            current = found?.iconPackPackage?.let { pack -> found.iconDrawableName?.let { CurrentOverride(pack, it) } },
            choosingPack = false,
            settings = settings,
            backFromTarget = null,
            chooseTarget = { Box(Modifier.fillMaxSize()) }, // the workspace is still loading
            onSave = { pack, drawable -> saveShortcut(ref, pack, drawable) },
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun PickerScaffold(
        target: IconTarget?,
        targetLabel: String,
        current: CurrentOverride?,
        choosingPack: Boolean,
        settings: LauncherSettings?,
        /** Back from the default-or-pack step to choosing an app; null closes the picker instead. */
        backFromTarget: (() -> Unit)?,
        chooseTarget: @Composable (IconBitmapCache) -> Unit,
        onSave: (pack: String?, drawable: String?) -> Unit,
    ) {
        var packName by rememberSaveable { mutableStateOf<String?>(null) }
        var packLabel by rememberSaveable { mutableStateOf<String?>(null) }
        val back: () -> Unit = {
            when {
                packName != null -> {
                    packName = null
                    packLabel = null
                }
                target != null && backFromTarget != null -> backFromTarget()
                else -> finish()
            }
        }
        BackHandler(onBack = back)
        val cache = remember { IconBitmapCache() }
        val step = Step(target, packName, packLabel)
        val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = settingsPageColor(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = stringResource(
                                    when {
                                        choosingPack -> R.string.look_icon_pack
                                        step.depth == 0 -> R.string.icon_picker_choose_app
                                        step.depth == 1 -> R.string.icon_picker_title
                                        else -> R.string.icon_picker_choose_icon
                                    },
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.semantics { heading() },
                            )
                            step.packLabel?.takeIf { step.depth == 2 }?.let { label ->
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SUBTITLE_ALPHA),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = back) {
                            Icon(painterResource(R.drawable.ic_settings_back), contentDescription = stringResource(R.string.settings_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            val motion = LocalMotion.current
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentKey = { it.depth },
                transitionSpec = {
                    // Deeper steps come in from the end edge; going back reverses it.
                    val forward = targetState.depth > initialState.depth
                    val sign = if (forward) 1 else -1
                    (fadeIn(motion.tweenSpec(STEP_MS)) + slideInHorizontally(motion.tweenSpec(STEP_MS)) { sign * it / SLIDE_FRACTION })
                        .togetherWith(fadeOut(motion.tweenSpec(STEP_MS / 2)) + slideOutHorizontally(motion.tweenSpec(STEP_MS)) { -sign * it / SLIDE_FRACTION })
                },
                label = "iconPickerStep",
            ) { shown ->
                Box(Modifier.fillMaxSize()) {
                    // The shown step's target; its override and label are the live ones.
                    val key = if (shown.target != null) target ?: shown.target else null
                    val pack = shown.pack
                    val label = targetLabel
                    when {
                        choosingPack -> IconPackChooserStep(
                            selected = settings?.lookFeel?.iconPackPackage,
                            settingsLoaded = settings != null,
                            loader = loader,
                            cache = cache,
                            onSelect = ::savePack,
                        )
                        key == null -> chooseTarget(cache)
                        pack == null -> PackStep(
                            target = key,
                            appLabel = label,
                            current = current,
                            style = settings?.let { IconStyle.from(it.lookFeel) },
                            loader = loader,
                            cache = cache,
                            onDefault = { onSave(null, null) },
                            onPack = {
                                packLabel = it.label
                                packName = it.packageName
                            },
                        )
                        else -> IconStep(key, label, pack, current, loader, cache) { name -> onSave(pack, name) }
                    }
                }
            }
        }
    }

    /**
     * Writes the choice on the app scope (so it completes even if the screen goes away) and closes the
     * picker once it is stored; the changed icon is the confirmation. A failed write keeps the picker
     * open and says so in a snackbar, so the user can retry.
     */
    private fun save(key: AppKey, pack: String?, drawable: String?) = persist {
        deps.appCustomizationRepository.update(key) {
            it.copy(iconPackPackage = pack, iconDrawableName = drawable)
        }
    }

    private fun saveShortcut(ref: ShortcutRef, pack: String?, drawable: String?) = persist {
        deps.workspaceRepository.setShortcutIcon(ref.itemId, ref.folderId, pack, drawable) is EditResult.Applied
    }

    /** The launcher-wide pack; null returns to system icons. */
    private fun savePack(pack: String?) = persist {
        deps.settingsRepository.update { it.copy(lookFeel = it.lookFeel.copy(iconPackPackage = pack)) }
        true
    }

    private fun persist(write: suspend () -> Boolean) {
        if (saving) return
        saving = true
        val result = deps.appScope.async {
            try {
                write()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
        lifecycleScope.launch {
            val ok = result.await()
            saving = false
            if (ok) {
                finish()
            } else {
                snackbar.showSnackbar(getString(R.string.app_menu_action_failed))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val mode = try {
                deps.settingsRepository.settings.first().night.mode
            } catch (_: Exception) {
                return@launch
            }
            NightModeController.onModeChanged(this@IconPickerActivity, createdNight, mode)
        }
    }

    companion object {
        private const val EXTRA_PACKAGE = "io.github.aj4abinjacob.chikku.iconpicker.PACKAGE"
        private const val EXTRA_CLASS = "io.github.aj4abinjacob.chikku.iconpicker.CLASS"
        private const val EXTRA_USER_SERIAL = "io.github.aj4abinjacob.chikku.iconpicker.USER_SERIAL"
        private const val EXTRA_CHOOSE_PACK = "io.github.aj4abinjacob.chikku.iconpicker.CHOOSE_PACK"
        private const val EXTRA_SHORTCUT_ITEM = "io.github.aj4abinjacob.chikku.iconpicker.SHORTCUT_ITEM"
        private const val EXTRA_SHORTCUT_FOLDER = "io.github.aj4abinjacob.chikku.iconpicker.SHORTCUT_FOLDER"

        private const val LIGHT_LUMINANCE = 0.179f
        private const val SUBTITLE_ALPHA = 0.8f
        private const val STEP_MS = 220
        private const val SLIDE_FRACTION = 8

        /** Picker for [key]'s icon, or with no key for choosing the app first. */
        fun intent(context: Context, key: AppKey? = null): Intent =
            Intent(context, IconPickerActivity::class.java).apply {
                if (key != null) {
                    putExtra(EXTRA_PACKAGE, key.packageName)
                    putExtra(EXTRA_CLASS, key.className)
                    putExtra(EXTRA_USER_SERIAL, key.userSerial)
                }
            }

        /** Picker for a pinned shortcut's icon: workspace item [itemId], or with [folderId] that folder's entry. */
        fun shortcutIntent(context: Context, itemId: String, folderId: String?): Intent =
            Intent(context, IconPickerActivity::class.java).apply {
                putExtra(EXTRA_SHORTCUT_ITEM, itemId)
                if (folderId != null) putExtra(EXTRA_SHORTCUT_FOLDER, folderId)
            }

        private fun shortcutFrom(intent: Intent?): ShortcutRef? {
            val item = intent?.getStringExtra(EXTRA_SHORTCUT_ITEM)?.takeIf { it.isNotBlank() } ?: return null
            return ShortcutRef(item, intent.getStringExtra(EXTRA_SHORTCUT_FOLDER)?.takeIf { it.isNotBlank() })
        }

        /** Chooser for the icon pack used across the launcher. */
        fun packChooserIntent(context: Context): Intent =
            Intent(context, IconPickerActivity::class.java).putExtra(EXTRA_CHOOSE_PACK, true)

        private fun keyFrom(intent: Intent?): AppKey? {
            val pkg = intent?.getStringExtra(EXTRA_PACKAGE) ?: return null
            val cls = intent.getStringExtra(EXTRA_CLASS) ?: return null
            if (!intent.hasExtra(EXTRA_USER_SERIAL) || pkg.isBlank() || cls.isBlank()) return null
            return AppKey(pkg, cls, intent.getLongExtra(EXTRA_USER_SERIAL, 0L))
        }
    }
}

/** A pinned shortcut by where it lives: workspace item [itemId], or entry [itemId] of folder [folderId]. */
internal data class ShortcutRef(val itemId: String, val folderId: String?) {
    /** Stored labels must disappear with the platform's filtered app/profile list. */
    fun findAvailable(workspace: Workspace, apps: AppsState): ItemTarget.Shortcut? {
        val shortcut = find(workspace) ?: return null
        val available = (apps as? AppsState.Loaded)?.apps.orEmpty().any {
            it.key.packageName == shortcut.packageName && it.key.userSerial == shortcut.userSerial &&
                (it.profileType == ProfileType.PERSONAL || it.profileType == ProfileType.WORK)
        }
        return shortcut.takeIf { available }
    }

    fun find(workspace: Workspace): ItemTarget.Shortcut? {
        val item = workspace.items.firstOrNull { it.id == (folderId ?: itemId) } ?: return null
        return if (folderId == null) {
            item.target as? ItemTarget.Shortcut
        } else {
            (item.target as? ItemTarget.Folder)?.entries?.firstOrNull { it.id == itemId }?.target as? ItemTarget.Shortcut
        }
    }
}

/** The per-app pack icon stored in [AppCustomization], or null when the app uses its default icon. */
internal fun AppCustomization.toOverride(): CurrentOverride? {
    val pack = iconPackPackage?.takeIf { it.isNotBlank() } ?: return null
    val name = iconDrawableName?.takeIf { it.isNotBlank() } ?: return null
    return CurrentOverride(pack, name)
}

private val AppKeySaver = Saver<AppKey?, List<String>>(
    save = { key -> key?.let { listOf(it.packageName, it.className, it.userSerial.toString()) } ?: emptyList() },
    restore = { parts ->
        if (parts.size == 3) parts[2].toLongOrNull()?.let { AppKey(parts[0], parts[1], it) } else null
    },
)
