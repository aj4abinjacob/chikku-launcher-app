package io.github.aj4abinjacob.chikku.home.appmenu

import io.github.aj4abinjacob.chikku.core.model.settings.MenuActionChoice
import io.github.aj4abinjacob.chikku.core.model.settings.capMenuActions
import io.github.aj4abinjacob.chikku.core.model.settings.isMenuChoiceOn
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.applock.AppUnlocker
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.AppsState
import io.github.aj4abinjacob.chikku.core.model.search.HistoryTarget
import io.github.aj4abinjacob.chikku.core.model.settings.AppMenuStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.home.HomeDependencies
import io.github.aj4abinjacob.chikku.home.screenBounds
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.showItemFootprint
import io.github.aj4abinjacob.chikku.message.ChikkuMessages
import io.github.aj4abinjacob.chikku.platform.AppMenuDetails
import io.github.aj4abinjacob.chikku.platform.AppMenuPlatform
import io.github.aj4abinjacob.chikku.platform.AppShortcut
import io.github.aj4abinjacob.chikku.platform.LaunchResult
import io.github.aj4abinjacob.chikku.platform.openStoreListing
import io.github.aj4abinjacob.chikku.settings.iconpicker.IconPickerActivity
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The app an icon menu is for. [homeItemId] is set when the icon is on the workspace or dock; with
 * [folderId] it is instead an entry of that folder, and "Remove" takes it out of the folder.
 * [fromDrawer] (drawer and search results) selects the drawer's Hide/Store/Uninstall toggles.
 */
data class AppMenuTarget(
    val key: AppKey,
    val label: String,
    val homeItemId: String? = null,
    val fromDrawer: Boolean = false,
    val folderId: String? = null,
    /** Set when the icon is a folder covered by this app: adds a "Folder" action for the folder's own menu. */
    val onFolderOptions: (() -> Unit)? = null,
)

/**
 * A pinned shortcut an icon menu is for: workspace item [itemId], or with [folderId] that folder's
 * entry [itemId]. Its menu changes the icon or removes it; the owning app's actions stay on the app.
 */
data class ShortcutMenuTarget(val itemId: String, val folderId: String? = null)

/**
 * Long-press app menu: app shortcuts, App info, Uninstall, Edit (label and
 * icon), Hide (drawer) and Remove (home). Shortcuts and launcher actions are two rounded cards,
 * the shortcuts nearest the item, with an arrow on that card pointing at the icon.
 * [AppMenuStyle.POPUP] shows the actions as labelled buttons ([MenuActionGrid]);
 * [AppMenuStyle.DETAILED] lists every action as a row with a description. Colors follow night
 * mode's "dark menus" toggle through [SurfaceColors].
 */
class AppMenuController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val deps: HomeDependencies,
    private val settings: () -> LauncherSettings,
    private val onRemoveFromHome: (itemId: String) -> Unit,
    /** "Remove" on a folder entry's menu; null hides the entry for folder contents. */
    private val onRemoveFromFolder: ((folderId: String, entryId: String) -> Unit)? = null,
    /** Authenticates before a locked app's shortcut or App info screen opens. */
    private val unlocker: AppUnlocker,
    private val platform: AppMenuPlatform = AppMenuPlatform(activity, deps.appLock),
    /** "Edit swipe actions" for a workspace icon (gestures feature); null hides the entry. */
    private val onEditSwipeActions: ((AppMenuTarget) -> Unit)? = null,
    /** Injectable platform boundary, including asynchronous shortcut/uninstall capability loading. */
    private val detailsLoader: suspend (AppKey) -> AppMenuDetails = { key ->
        val shape = settings().lookFeel.iconShape
        withContext(Dispatchers.IO) { platform.loadDetails(key, shape) }
    },
    /** Shows the app edit sheet over HOME. */
    private val showEditor: (AppEditRequest) -> Unit,
    /** Closes that sheet, saving its staged edit. */
    private val dismissEditor: () -> Unit = {},
    /** Lets HOME apply the same successful-launch policy as an ordinary icon tap. */
    private val onShortcutLaunched: (AppMenuTarget) -> Unit = {},
    private val shortcutLauncher: (AppShortcut, Rect?) -> LaunchResult = platform::startShortcut,
    /** Chikku's message bar for short notices; never a platform toast. */
    private val messages: ChikkuMessages = ChikkuMessages.of(activity),
    private val externalIntents: io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter = io.github.aj4abinjacob.chikku.platform.ExternalIntentStarter.DENY,
) {
    private var popup: PopupWindow? = null
    private var detailsJob: Job? = null
    private var watchJob: Job? = null
    /** Tears down the current menu (job, footprint) whether or not its window was shown yet. */
    private var closeCurrent: (() -> Unit)? = null

    fun show(anchor: View, target: AppMenuTarget): AppMenuHandle? =
        open(anchor, load = { detailsLoader(target.key) }, staleKey = target.key) { content, colors, detailed, prefs, details ->
            fill(content, anchor, target, colors, detailed, prefs, details)
        }

    /** Change icon and Remove for a pinned shortcut (no app shortcuts to load). */
    fun showShortcut(anchor: View, target: ShortcutMenuTarget): AppMenuHandle? =
        open(anchor, load = null, staleKey = null) { content, colors, detailed, prefs, _ ->
            val actions = buildList {
                add(MenuAction(R.string.app_menu_change_icon, R.string.app_menu_change_icon_description, R.drawable.ic_menu_edit) {
                    try {
                        activity.startActivity(IconPickerActivity.shortcutIntent(activity, target.itemId, target.folderId))
                    } catch (_: ActivityNotFoundException) {
                        showMessage(R.string.app_menu_action_failed)
                    }
                })
                if (target.folderId == null || onRemoveFromFolder != null) {
                    val description = if (target.folderId != null) R.string.app_menu_remove_shortcut_from_folder_description else R.string.app_menu_remove_shortcut_description
                    add(MenuAction(R.string.app_menu_remove, description, R.drawable.ic_menu_remove) {
                        if (target.folderId != null) onRemoveFromFolder?.invoke(target.folderId, target.itemId)
                        else onRemoveFromHome(target.itemId)
                    })
                }
            }
            menuCards(content, shortcutCard = null, actions, colors, detailed, prefs.lookFeel)
        }

    /**
     * Opens a menu on [anchor] whose cards [build] makes, once [load] (if any) has its details or
     * has taken [DETAILS_WAIT_MS]; late details rebuild it in place. A non-null [staleKey] closes
     * the menu when that app is locked/unlocked or leaves the app list while it is open.
     */
    private fun open(
        anchor: View,
        load: (suspend () -> AppMenuDetails)?,
        staleKey: AppKey?,
        build: (LinearLayout, SurfaceColors, Boolean, LauncherSettings, AppMenuDetails?) -> MenuCards,
    ): AppMenuHandle? {
        if (!anchor.isAttachedToWindow) return null
        dismiss()
        val prefs = settings()
        val baseColors = SurfaceColors.resolve(LauncherSurface.MENU, prefs.night, activity)
        val look = prefs.lookFeel
        val contrastColors = look.menuBackgroundColor?.let { background ->
            if (ColorUtils.calculateLuminance(background) < 0.5) SurfaceColors.DARK else SurfaceColors.LIGHT
        } ?: baseColors
        val colors = baseColors.copy(
            background = look.menuBackgroundColor ?: baseColors.background,
            onSurface = look.menuTextColor ?: contrastColors.onSurface,
            onSurfaceVariant = look.menuTextColor ?: contrastColors.onSurfaceVariant,
        )
        val detailed = prefs.lookFeel.appMenuStyle == AppMenuStyle.DETAILED
        // Transparent column of cards; the padding leaves room for the cards' shadows.
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(CARD_ELEVATION_DP).toInt()
            setPadding(pad, pad, pad, pad)
        }
        val window = PopupWindow(
            ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                clipToPadding = false
                addView(content)
            },
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = 0f
            isOutsideTouchable = true
            animationStyle = LauncherMenuStyle.popupAnimationStyle(Motion.current(prefs.lookFeel.animationSpeed))
        }
        // The footprint answers the press at once; the window follows with its shortcuts.
        val clearFootprint = showItemFootprint(anchor, prefs)
        var closed = false
        val close = {
            if (!closed) {
                closed = true
                detailsJob?.cancel()
                watchJob?.cancel()
                watchJob = null
                clearFootprint()
                if (popup === window) {
                    popup = null
                    closeCurrent = null
                }
            }
        }
        window.setOnDismissListener { close() }
        popup = window
        closeCurrent = close
        watchJob = staleKey?.let { key -> scope.launch { dismissWhenStale(window, key) } }

        detailsJob = scope.launch {
            val loading = load?.let { loader ->
                async {
                    try {
                        loader()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            // Shown once at its final size: opening first without shortcuts and growing when they
            // arrived made the menu jump sideways (it stays lined up with the icon's edge). Details
            // slower than this still open the menu, and fill it in when they come.
            val details = loading?.let { withTimeoutOrNull(DETAILS_WAIT_MS) { it.await() } }
            if (popup !== window || !anchor.isAttachedToWindow) return@launch close()
            val cards = build(content, colors, detailed, prefs, details)
            val final = loading == null || details != null
            // Positioned by hand: showAsDropDown picks above/below once, before late shortcuts
            // could grow the menu, which pushed it off the bottom of the screen from the dock.
            place(window, content, cards, anchor, detailed, compact = final && !cards.withShortcuts, showing = false)
            if (final) return@launch
            val late = loading?.await() ?: return@launch
            if (popup === window && window.isShowing) {
                val lateCards = build(content, colors, detailed, prefs, late)
                place(window, content, lateCards, anchor, detailed, compact = !lateCards.withShortcuts, showing = true)
            }
        }
        return AppMenuHandle { if (popup === window) dismiss() }
    }

    /**
     * The menu is a snapshot of the app's shortcuts, uninstall ability and lock state, so it closes
     * when the app's lock changes or the app leaves the launcher's app list while it is open.
     */
    private suspend fun dismissWhenStale(window: PopupWindow, key: AppKey) {
        var baseline: Pair<Boolean, Boolean>? = null
        combine(
            deps.appLock.state.map { deps.appLock.isLocked(key) },
            deps.appsRepository.apps.map { state -> (state as? AppsState.Loaded)?.apps?.any { it.key == key } ?: true },
        ) { locked, listed -> locked to listed }.first { now ->
            val start = baseline ?: now.also { baseline = it }
            now.first != start.first || (start.second && !now.second)
        }
        if (popup === window) dismiss()
    }

    fun dismiss() {
        val window = popup
        if (window?.isShowing == true) window.dismiss() else closeCurrent?.invoke()
        popup = null
        closeCurrent = null
    }

    /** Also closes an open edit sheet (activity destroyed or HOME pressed). */
    fun dismissAll() {
        dismiss()
        dismissEditor()
    }

    private fun fill(
        content: LinearLayout,
        anchor: View,
        target: AppMenuTarget,
        colors: SurfaceColors,
        detailed: Boolean,
        prefs: LauncherSettings,
        details: AppMenuDetails?,
    ): MenuCards {
        // A locked app's shortcuts are hidden: their labels can reveal its content (chats, contacts).
        val shortcuts = if (deps.appLock.isLocked(target.key)) emptyList() else details?.shortcuts.orEmpty()
        val look = prefs.lookFeel
        val shortcutCard = shortcuts.takeIf { it.isNotEmpty() }?.let {
            card(colors, look).apply {
                shortcuts.forEach { shortcut ->
                    addView(row(shortcut.label, null, shortcut.icon, tint = false, colors, detailed) { launchShortcut(shortcut, anchor, target) })
                }
            }
        }

        fun on(choice: MenuActionChoice) = look.isMenuChoiceOn(choice, target.fromDrawer)
        val actions = capMenuActions(buildList {
            if (on(MenuActionChoice.INFO)) {
                add(
                    MenuAction(R.string.app_menu_app_info, R.string.app_menu_app_info_description, R.drawable.ic_menu_app_info) {
                        openAppInfo(target, anchor)
                    },
                )
            }
            if (on(MenuActionChoice.EDIT)) {
                add(MenuAction(R.string.app_menu_edit, R.string.app_menu_edit_description, R.drawable.ic_menu_edit) { showEditDialog(target) })
            }
            target.onFolderOptions?.let { open ->
                add(MenuAction(R.string.app_menu_folder, R.string.app_menu_folder_description, R.drawable.ic_settings_folder) { open() })
            }
            if (on(MenuActionChoice.SWIPE) && target.homeItemId != null && target.folderId == null && onEditSwipeActions != null) {
                add(
                    MenuAction(R.string.app_menu_swipe_actions, R.string.app_menu_swipe_actions_description, R.drawable.ic_menu_swipe) {
                        onEditSwipeActions.invoke(target)
                    },
                )
            }
            if (on(MenuActionChoice.REMOVE) && target.homeItemId != null && (target.folderId == null || onRemoveFromFolder != null)) {
                val description = if (target.folderId != null) R.string.app_menu_remove_from_folder_description else R.string.app_menu_remove_description
                add(
                    MenuAction(R.string.app_menu_remove, description, R.drawable.ic_menu_remove) {
                        if (target.folderId != null) onRemoveFromFolder?.invoke(target.folderId, target.homeItemId)
                        else onRemoveFromHome(target.homeItemId)
                    },
                )
            }
            if (on(MenuActionChoice.HIDE)) {
                add(
                    MenuAction(R.string.app_menu_hide, R.string.app_menu_hide_description, R.drawable.ic_menu_hide) {
                        deps.appScope.launch {
                            val ok = deps.appCustomizationRepository.setHidden(target.key, true)
                            showMessage(if (ok) R.string.app_menu_hidden else R.string.app_menu_action_failed)
                        }
                    },
                )
            }
            if (on(MenuActionChoice.STORE)) {
                add(MenuAction(R.string.app_menu_store, R.string.app_menu_store_description, R.drawable.ic_menu_store) {
                    if (!activity.openStoreListing(target.key.packageName, externalIntents)) showMessage(R.string.app_menu_action_failed)
                })
            }
            if (details?.canUninstall == true && on(MenuActionChoice.UNINSTALL)) {
                add(
                    MenuAction(R.string.app_menu_uninstall, R.string.app_menu_uninstall_description, R.drawable.ic_menu_uninstall) {
                        uninstall(target.key)
                    },
                )
            }
        }.sortedBy { ACTION_ORDER.indexOf(it.title) }, { it.title }, ACTION_DROP_ORDER, keepFirst = setOf(R.string.app_menu_folder))

        return menuCards(content, shortcutCard, actions, colors, detailed, look)
    }

    /** The action card below [shortcutCard] (if any), stacked into [content] with the arrow. */
    private fun menuCards(
        content: LinearLayout,
        shortcutCard: View?,
        actions: List<MenuAction>,
        colors: SurfaceColors,
        detailed: Boolean,
        look: LookFeelSettings,
    ): MenuCards {
        val actionCard = card(colors, look).apply {
            if (!detailed) {
                addView(actionGrid(actions, colors))
                // On its own, the action card wraps to its buttons.
                if (shortcutCard == null) minimumWidth = 0
            } else {
                actions.forEach { action -> addView(row(action.title, action.description, action.icon, colors, detailed, action.onClick)) }
            }
        }
        val fill = LauncherMenuStyle.cardFill(colors, customBackground = look.menuBackgroundColor != null)
        val arrow = menuArrow(activity, fill, LauncherMenuStyle.cardStroke(colors), LauncherMenuStyle.edgeWidth(activity))
        val cards = MenuCards(listOfNotNull(shortcutCard, actionCard), arrow)
        // Stacked once for measuring; placement restacks them for the side the menu opens on.
        arrangeMenu(content, cards.nearFirst, arrow, above = true, arrowCenterX = 0)
        return cards
    }

    /** A menu's cards, nearest the item first, and the arrow on the nearest one. */
    private class MenuCards(val nearFirst: List<View>, val arrow: MenuArrowView) {
        val withShortcuts get() = nearFirst.size > 1
    }

    private class MenuAction(val title: Int, val description: Int, val icon: Int, val onClick: () -> Unit)

    /** The launcher actions as labelled buttons ([MenuActionGrid.addActions]) that close the menu when tapped. */
    private fun actionGrid(actions: List<MenuAction>, colors: SurfaceColors): View = MenuActionGrid(activity).apply {
        val pad = dp(8f).toInt()
        setPadding(pad, 0, pad, 0)
        addActions(
            actions.map { action ->
                val color = if (action.title == R.string.app_menu_uninstall) LauncherMenuStyle.dangerColor(colors) else LauncherMenuStyle.actionColor(colors)
                GridAction(activity.getString(action.title), action.icon, color) {
                    dismiss()
                    action.onClick()
                }
            },
        )
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** A rounded, elevated card; children (and their ripples) are clipped to its corners. */
    private fun card(colors: SurfaceColors, look: LookFeelSettings): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = LauncherMenuStyle.cardBackground(colors, look.menuCornerRadiusDp, look.menuBackgroundColor != null, activity)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = dp(CARD_ELEVATION_DP / 2)
        minimumWidth = dp(MIN_WIDTH_DP).toInt()
        setPadding(0, dp(4f).toInt(), 0, dp(4f).toInt())
        layoutParams = cardParams()
    }

    private fun cardParams() =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun row(title: Int, description: Int, icon: Int, colors: SurfaceColors, detailed: Boolean, onClick: () -> Unit): View =
        row(
            activity.getString(title),
            activity.getString(description),
            activity.getDrawable(icon)?.mutate(),
            tint = true,
            colors,
            detailed,
            onClick,
        )

    private fun row(
        title: String,
        description: String?,
        icon: Drawable?,
        /** Tint [icon] with the surface's secondary color (launcher action glyphs, not app shortcut icons). */
        tint: Boolean,
        colors: SurfaceColors,
        detailed: Boolean,
        onClick: () -> Unit,
    ): View {
        val horizontal = dp(ROW_PADDING_DP).toInt()
        val vertical = dp(if (detailed) 10f else 6f).toInt()
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (detailed) 64f else SHORTCUT_ROW_DP).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            if (activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
                setBackgroundResource(outValue.resourceId)
            }
            setOnClickListener {
                dismiss()
                onClick()
            }
        }
        // Always reserve the icon slot so labels line up even when a shortcut has no icon. Tinted
        // launcher glyphs sit inside the slot at 22dp, app shortcut icons fill it.
        val size = dp(ROW_ICON_DP).toInt()
        row.addView(
            ImageView(activity).apply {
                setImageDrawable(icon)
                if (tint) {
                    setColorFilter(colors.onSurfaceVariant)
                    val inset = dp(3f).toInt()
                    setPadding(inset, inset, inset, inset)
                }
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(16f).toInt() },
        )
        val texts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            TextView(activity).apply {
                text = title
                setTextColor(colors.onSurface)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                maxLines = if (detailed) 2 else 1
                ellipsize = TextUtils.TruncateAt.END
                maxWidth = dp(240f).toInt()
            },
        )
        if (detailed && description != null) {
            texts.addView(
                TextView(activity).apply {
                    text = description
                    setTextColor(colors.onSurfaceVariant)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    maxWidth = dp(240f).toInt()
                },
            )
        }
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun openAppInfo(target: AppMenuTarget, anchor: View) {
        when (val result = deps.appLauncher.openAppDetails(target.key, anchor.screenBounds())) {
            LaunchResult.Started -> Unit
            is LaunchResult.Locked -> unlocker.unlock(result) { openAppInfo(target, anchor) }
            is LaunchResult.Unavailable -> showMessage(R.string.app_menu_action_failed)
        }
    }

    private fun launchShortcut(shortcut: AppShortcut, anchor: View, target: AppMenuTarget) {
        when (val result = shortcutLauncher(shortcut, anchor.screenBounds())) {
            LaunchResult.Started -> {
                deps.searchHistoryRepository?.let { history ->
                    deps.appScope.launch { history.record(HistoryTarget.Shortcut(target.key.packageName, shortcut.id, target.key.userSerial)) }
                }
                onShortcutLaunched(target)
            }
            is LaunchResult.Locked -> unlocker.unlock(result) { launchShortcut(shortcut, anchor, target) }
            is LaunchResult.Unavailable -> showMessage(R.string.app_menu_action_failed)
        }
    }

    private fun uninstall(key: AppKey) {
        val intent = platform.uninstallIntent(key) ?: return showMessage(R.string.app_menu_action_failed)
        try {
            activity.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.app_menu_action_failed)
        } catch (_: SecurityException) {
            showMessage(R.string.app_menu_action_failed)
        }
    }

    /** Rename (empty restores the app's own label) or pick a different icon, in [showEditor]'s sheet. */
    private fun showEditDialog(target: AppMenuTarget) {
        scope.launch {
            val current = try {
                deps.appCustomizationRepository.get(target.key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            val originalLabel = resolveOriginalLabel(target, current?.customLabel)
            if (activity.isFinishing || activity.isDestroyed) return@launch
            showEditor(
                AppEditRequest(
                    key = target.key,
                    originalLabel = originalLabel,
                    customLabel = current?.customLabel,
                    hasCustomIcon = current?.iconDrawableName != null,
                    onSaveLabel = { custom ->
                        deps.appScope.launch {
                            val ok = deps.appCustomizationRepository.update(target.key) { it.copy(customLabel = custom) }
                            if (!ok) showMessage(R.string.app_menu_action_failed)
                        }
                    },
                    onChangeIcon = {
                        try {
                            activity.startActivity(IconPickerActivity.intent(activity, target.key))
                        } catch (_: ActivityNotFoundException) {
                            showMessage(R.string.app_menu_action_failed)
                        }
                    },
                ),
            )
        }
    }

    /**
     * The app's own label. Search results and the drawer carry the custom name as [AppMenuTarget.label];
     * using that as the original would make an unchanged Done clear the custom name, so the label is
     * looked up by identity. Falls back to [AppMenuTarget.label] when the app is not listed.
     */
    private suspend fun resolveOriginalLabel(target: AppMenuTarget, customLabel: String?): String {
        if (customLabel == null) return target.label
        val loaded = try {
            deps.appsRepository.apps.first { it !is AppsState.Loading } as? AppsState.Loaded
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return loaded?.apps?.firstOrNull { it.key == target.key }?.label ?: target.label
    }

    private fun showMessage(message: Int) = messages.show(message)

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.resources.displayMetrics)

    private fun place(window: PopupWindow, content: LinearLayout, cards: MenuCards, anchor: View, detailed: Boolean, compact: Boolean, showing: Boolean) {
        val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
        // Weighted shortcut labels otherwise consume the whole AT_MOST frame width, so a menu with
        // shortcuts gets a fixed width; an action card on its own wraps to its buttons.
        val width = if (detailed) frame.width() else dp(POPUP_WIDTH_DP).toInt()
        placeMenuWindow(
            window, anchor, width, exactWidth = !detailed && !compact, showing,
            shadowPad = dp(CARD_ELEVATION_DP).toInt(), cornerRadius = dp(settings().lookFeel.menuCornerRadiusDp),
        ) { above, arrowX ->
            arrangeMenu(content, cards.nearFirst, cards.arrow, above, arrowX)
        }
    }

    private companion object {
        const val CARD_ELEVATION_DP = 6f
        /** How long a menu waits for its shortcuts before opening without them. */
        const val DETAILS_WAIT_MS = 150L
        /** Compact menu width, card plus its shadow margin. */
        const val POPUP_WIDTH_DP = 272f
        const val MIN_WIDTH_DP = 232f

        /** Row height of a shortcut in the buttons style: a little under the home menu's, 44dp. */
        const val SHORTCUT_ROW_DP = 44f

        /** Order of the launcher actions: the app's own, then its info, then what leaves the home screen. */
        private val ACTION_ORDER = listOf(
            R.string.app_menu_edit, R.string.app_menu_folder, R.string.app_menu_swipe_actions, R.string.app_menu_app_info, R.string.app_menu_store,
            R.string.app_menu_remove, R.string.app_menu_hide, R.string.app_menu_uninstall,
        )
        /** First to go when an icon's own Folder action pushes the menu past the cap; Folder itself never goes. */
        private val ACTION_DROP_ORDER = listOf(
            R.string.app_menu_store, R.string.app_menu_hide, R.string.app_menu_swipe_actions, R.string.app_menu_remove,
            R.string.app_menu_app_info, R.string.app_menu_edit, R.string.app_menu_uninstall,
        )
        const val ROW_PADDING_DP = 16f
        const val ROW_ICON_DP = 28f
    }
}
