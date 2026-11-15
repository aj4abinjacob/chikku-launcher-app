package io.github.aj4abinjacob.chikku.settings.previews

import io.github.aj4abinjacob.chikku.core.model.settings.MenuActionChoice
import io.github.aj4abinjacob.chikku.core.model.settings.capMenuActions
import io.github.aj4abinjacob.chikku.core.model.settings.isMenuChoiceOn
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.AppMenuStyle
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings
import io.github.aj4abinjacob.chikku.core.model.settings.ScreenOrientation
import io.github.aj4abinjacob.chikku.core.model.settings.StatusBarMode
import io.github.aj4abinjacob.chikku.search.SearchTheme
import io.github.aj4abinjacob.chikku.home.LauncherMenuStyle
import io.github.aj4abinjacob.chikku.home.appmenu.MenuActionGrid
import io.github.aj4abinjacob.chikku.home.appmenu.arrangeMenu
import io.github.aj4abinjacob.chikku.home.appmenu.GridAction
import io.github.aj4abinjacob.chikku.home.appmenu.menuArrow
import io.github.aj4abinjacob.chikku.platform.icons.IconPackRepository
import io.github.aj4abinjacob.chikku.platform.icons.IconShaper
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

/** Reads only the selected pack's artwork; never queries installed apps or profiles. */
@Composable
fun AppearanceIconsLivePreview(look: LookFeelSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val size = with(LocalDensity.current) { 48.dp.roundToPx() }
    var resume by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) { resume++; onPauseOrDispose { } }
    val samples by produceState<AppearanceIconSamples?>(null, look.iconPackPackage, look.iconShape, look.reshapeLegacyIcons, size, resume) {
        value = null
        value = withContext(Dispatchers.IO) {
            try { loadAppearanceSamples(context, look, size) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RuntimeException) { loadAppearanceSamples(context, look.copy(iconPackPackage = null), size).copy(missingPack = look.iconPackPackage != null) }
        }
    }
    val description = stringResource(R.string.preview_appearance_icons_description)
    Column(modifier.fillMaxWidth().background(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) })).padding(12.dp),
        verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { index ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        samples?.icons?.getOrNull(index)?.let { Image(it, null, Modifier.fillMaxSize()) }
                    }
                    Text(stringResource(if (index == 3) R.string.preview_appearance_legacy else if (index == 2) R.string.preview_appearance_adaptive else R.string.preview_appearance_sample),
                        color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(stringResource(when {
            samples == null -> R.string.preview_appearance_loading
            samples!!.missingPack -> R.string.preview_appearance_missing_pack
            look.iconPackPackage != null && samples!!.packIconCount == 0 -> R.string.preview_appearance_empty_pack
            look.iconPackPackage != null -> R.string.preview_appearance_pack_sample
            else -> R.string.preview_appearance_sample_info
        }), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
    }
}

internal data class AppearanceIconSamples(val icons: List<ImageBitmap>, val missingPack: Boolean, val packIconCount: Int)

internal fun loadAppearanceSamples(context: Context, look: LookFeelSettings, size: Int): AppearanceIconSamples {
    val pack = look.iconPackPackage?.let { IconPackRepository(context).load(it) }
    // Bounded sample from mappings, independent of which apps the phone happens to have.
    val names = pack?.filter?.components?.values?.distinct()?.take(2).orEmpty()
    val packIcons = names.mapNotNull { pack?.drawable(it) }
    val source = (0..3).map { index ->
        when {
            index < packIcons.size -> packIcons[index]
            index == 2 -> AdaptiveIconDrawable(PREVIEW_SAMPLE_COLORS[2].toDrawable(),
                InsetDrawable(ContextCompat.getDrawable(context, PREVIEW_SAMPLES[2].first)!!.mutate().apply { setTint(android.graphics.Color.WHITE) }, .3f))
            else -> previewSampleIcon(context, index)
        }
    }
    return AppearanceIconSamples(source.map { IconShaper.apply(it, look.iconShape, look.reshapeLegacyIcons).toBitmap(size, size).asImageBitmap() },
        missingPack = look.iconPackPackage != null && pack == null, packIconCount = packIcons.size)
}

@Composable
fun AppearanceScreenLivePreview(look: LookFeelSettings, modifier: Modifier = Modifier) {
    val landscape = look.orientation == ScreenOrientation.LANDSCAPE
    val description = stringResource(R.string.preview_appearance_screen_description)
    Box(modifier.fillMaxWidth().height(142.dp).background(MaterialTheme.colorScheme.surfaceContainer).clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Column(Modifier.size(if (landscape) 240.dp else 100.dp, if (landscape) 108.dp else 130.dp).clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) }))) {
            if (look.statusBar != StatusBarMode.HIDDEN) {
                Row(Modifier.fillMaxWidth().height(20.dp).background(if (look.statusBar == StatusBarMode.SHOWN) Color.Black.copy(alpha = .3f) else Color.Transparent)
                    .padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.preview_appearance_time), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Icon(painterResource(R.drawable.ic_settings_home), null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                repeat(if (landscape) 6 else 3) { index -> Icon(painterResource(PREVIEW_SAMPLES[index % 4].first), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
            Box(Modifier.fillMaxWidth().height(8.dp).background(Color.Black.copy(alpha = .25f)))
        }
    }
    if (look.orientation == ScreenOrientation.AUTO) Text(stringResource(R.string.preview_appearance_auto_orientation),
        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
}

@Composable
fun AnimationSpeedLivePreview(speed: AnimationSpeed, modifier: Modifier = Modifier) {
    val playback = rememberPreviewPlayback(speed)
    // Speed is what this page compares, so each new step plays once; the first composition stays idle.
    var shownSpeed by remember { mutableStateOf(speed) }
    LaunchedEffect(playback) {
        if (shownSpeed != speed) { shownSpeed = speed; playback.replay(320L) }
    }
    val description = stringResource(R.string.preview_appearance_motion_description)
    val progress = if (playback.running) playback.progress else 1f
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(112.dp).background(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) }))
            .clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(.76f).height(80.dp).graphicsLayer {
                alpha = progress
                translationY = (1f - progress) * 36.dp.toPx()
                scaleX = .88f + progress * .12f; scaleY = scaleX
            }.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    PREVIEW_SAMPLES.forEach { (glyph, _) -> Icon(painterResource(glyph), null, modifier = Modifier.size(24.dp)) }
                }
            }
        }
        TextButton(onClick = { playback.replay(320L) }, modifier = Modifier.heightIn(min = 48.dp).align(Alignment.End)) {
            Text(stringResource(R.string.preview_appearance_replay))
        }
    }
}

internal data class PreviewMenuAction(val title: Int, val icon: Int, val description: Int)

/** Mirrors the launcher's action order for a synthetic, uninstallable app with a home placement. */
internal fun previewMenuActions(look: LookFeelSettings, fromDrawer: Boolean): List<PreviewMenuAction> {
    fun on(choice: MenuActionChoice) = look.isMenuChoiceOn(choice, fromDrawer)
    return capMenuActions(buildList {
        if (on(MenuActionChoice.INFO)) add(PreviewMenuAction(R.string.app_menu_app_info, R.drawable.ic_menu_app_info, R.string.app_menu_app_info_description))
        if (on(MenuActionChoice.EDIT)) add(PreviewMenuAction(R.string.app_menu_edit, R.drawable.ic_menu_edit, R.string.app_menu_edit_description))
        if (on(MenuActionChoice.SWIPE)) add(PreviewMenuAction(R.string.app_menu_swipe_actions, R.drawable.ic_menu_swipe, R.string.app_menu_swipe_actions_description))
        if (on(MenuActionChoice.REMOVE)) add(PreviewMenuAction(R.string.app_menu_remove, R.drawable.ic_menu_remove, R.string.app_menu_remove_description))
        if (on(MenuActionChoice.HIDE)) add(PreviewMenuAction(R.string.app_menu_hide, R.drawable.ic_menu_hide, R.string.app_menu_hide_description))
        if (on(MenuActionChoice.STORE)) add(PreviewMenuAction(R.string.app_menu_store, R.drawable.ic_menu_store, R.string.app_menu_store_description))
        if (on(MenuActionChoice.UNINSTALL)) add(PreviewMenuAction(R.string.app_menu_uninstall, R.drawable.ic_menu_uninstall, R.string.app_menu_uninstall_description))
    }.sortedBy { PREVIEW_MENU_ORDER.indexOf(it.title) }, { it.title }, PREVIEW_MENU_DROP_ORDER)
}

val AppMenuPreviewHeight = 150.dp

/** The Home/Drawer switch for [AppMenuLivePreview]; the two surfaces have separate action toggles. */
@Composable
fun AppMenuSurfaceSelector(fromDrawer: Boolean, onSurfaceChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { onSurfaceChange(false) }, modifier = Modifier.heightIn(min = 48.dp).semantics { selected = !fromDrawer }) { Text(stringResource(R.string.settings_section_home), color = if (!fromDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        TextButton(onClick = { onSurfaceChange(true) }, modifier = Modifier.heightIn(min = 48.dp).semantics { selected = fromDrawer }) { Text(stringResource(R.string.settings_section_drawer), color = if (fromDrawer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * The launcher's long-press menu for a synthetic app, drawn near real size in a fixed-height frame.
 * [focusAction] (an action title) is kept in view when the menu is taller than the frame, so the
 * row a toggle just added or removed is the one on screen. [showSurfaceSelector] false lets the
 * page put [AppMenuSurfaceSelector] elsewhere.
 */
@Composable
fun AppMenuLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier,
    fromDrawer: Boolean? = null, onSurfaceChange: ((Boolean) -> Unit)? = null,
    menuHeight: Dp = AppMenuPreviewHeight, focusAction: Int? = null, showSurfaceSelector: Boolean = true) {
    var localDrawer by remember { mutableStateOf(false) }
    val drawer = fromDrawer ?: localDrawer
    fun choose(value: Boolean) { localDrawer = value; onSurfaceChange?.invoke(value) }
    val resources = LocalResources.current
    val actions = previewMenuActions(settings.lookFeel, drawer)
    val description = stringResource(R.string.preview_menu_surface_description,
        stringResource(if (drawer) R.string.settings_section_drawer else R.string.settings_section_home),
        actions.joinToString { resources.getString(it.title) })
    Column(modifier) {
        if (showSurfaceSelector) AppMenuSurfaceSelector(drawer, ::choose, Modifier.fillMaxWidth())
        AndroidView(modifier = Modifier.fillMaxWidth().height(menuHeight).clipToBounds().clearAndSetSemantics { contentDescription = description },
            factory = { AppMenuPreviewFrame(it) }, update = { it.contentDescription = description; it.show(settings, actions, focusAction) })
        Text(stringResource(R.string.preview_lookfeel_menu_sample_info), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
}

/** Launcher action order with every optional action on; locates where a removed action was. */
private val PREVIEW_MENU_ORDER = listOf(R.string.app_menu_edit, R.string.app_menu_swipe_actions, R.string.app_menu_app_info,
    R.string.app_menu_store, R.string.app_menu_remove, R.string.app_menu_hide, R.string.app_menu_uninstall)

private val PREVIEW_MENU_DROP_ORDER = listOf(R.string.app_menu_store, R.string.app_menu_hide, R.string.app_menu_swipe_actions,
    R.string.app_menu_remove, R.string.app_menu_app_info, R.string.app_menu_edit, R.string.app_menu_uninstall)

/** Index of the row to keep in view: [focus] itself, or the row now in the place it left. */
internal fun previewMenuFocusIndex(actions: List<PreviewMenuAction>, focus: Int?): Int? {
    if (focus == null || actions.isEmpty()) return null
    actions.indexOfFirst { it.title == focus }.takeIf { it >= 0 }?.let { return it }
    val position = PREVIEW_MENU_ORDER.indexOf(focus)
    return actions.indexOfFirst { PREVIEW_MENU_ORDER.indexOf(it.title) > position }.takeIf { it >= 0 } ?: actions.lastIndex
}

/**
 * Mirrors AppMenuController's action card (same card, rows, labelled buttons, arrow and colors) for a
 * synthetic app. It is never shrunk below [MIN_SCALE], so corner radius, colors and labels stay
 * legible; a detailed menu taller than the frame is clipped around the focused row instead.
 * No action listeners; nothing here is focusable.
 */
internal class AppMenuPreviewFrame(previewContext: Context) : FrameLayout(previewContext) {
    /** The rounded action card, as the launcher builds it. */
    internal var actionCard: LinearLayout? = null; private set
    /** The view for each action, in order (detailed rows or icon-strip buttons). */
    internal var actionViews: List<View> = emptyList(); private set
    internal var focusView: View? = null; private set
    internal var menuScale = 1f; private set
    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = (value * density).roundToInt()

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; background = wallpaperBackground() }

    fun show(settings: LauncherSettings, actions: List<PreviewMenuAction>, focus: Int? = null) {
        removeAllViews()
        val style = LauncherMenuStyle(context, settings)
        val colors = style.colors
        val card = style.card().apply {
            setPadding(0, dp(4f), 0, dp(4f))
            minimumWidth = dp(232f)
        }
        actionViews = if (settings.lookFeel.appMenuStyle == AppMenuStyle.DETAILED) {
            actions.map { action -> detailedRow(action, colors.onSurface, colors.onSurfaceVariant).also { card.addView(it) } }
        } else {
            // The launcher's own labelled buttons, inert here.
            val grid = MenuActionGrid(context).apply { setPadding(dp(8f), 0, dp(8f), 0) }
            val buttons = grid.addActions(
                actions.map { action ->
                    GridAction(context.getString(action.title), action.icon,
                        if (action.title == R.string.app_menu_uninstall) LauncherMenuStyle.dangerColor(colors) else LauncherMenuStyle.actionColor(colors), onClick = null)
                },
            )
            card.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            buttons
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(LauncherMenuStyle.CARD_ELEVATION_DP); setPadding(pad, pad, pad, pad)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        // As over an icon below the menu: the launcher's arrow under the card, aiming at its middle.
        val arrow = menuArrow(context, LauncherMenuStyle.cardFill(colors, settings.lookFeel.menuBackgroundColor != null),
            LauncherMenuStyle.cardStroke(colors), LauncherMenuStyle.edgeWidth(context))
        arrangeMenu(column, listOf(card), arrow, above = true, arrowCenterX = null)
        actionCard = card
        focusView = previewMenuFocusIndex(actions, focus)?.let { actionViews[it] }
        addView(column)
    }

    private fun detailedRow(action: PreviewMenuAction, onSurface: Int, onSurfaceVariant: Int): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        }
        row.addView(ImageView(context).apply {
            setImageDrawable(context.getDrawable(action.icon)?.mutate()); setColorFilter(onSurfaceVariant)
            val inset = dp(1f); setPadding(inset, inset, inset, inset)
        }, LinearLayout.LayoutParams(dp(24f), dp(24f)).apply { marginEnd = dp(14f) })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = context.getString(action.title); setTextColor(onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); maxLines = 2; ellipsize = TextUtils.TruncateAt.END; maxWidth = dp(240f)
        })
        texts.addView(TextView(context).apply {
            text = context.getString(action.description); setTextColor(onSurfaceVariant)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); maxWidth = dp(240f)
        })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        // The launcher's popup wraps its content up to the screen width.
        val available = ((w - 2 * dp(FRAME_PAD_DP)) / MIN_SCALE).toInt().coerceAtLeast(0)
        getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val child = getChildAt(0) ?: return
        child.layout(0, 0, child.measuredWidth, child.measuredHeight)
        val pad = dp(FRAME_PAD_DP).toFloat()
        val contentW = child.measuredWidth.coerceAtLeast(1).toFloat(); val contentH = child.measuredHeight.coerceAtLeast(1).toFloat()
        val fitH = (height - 2 * pad) / contentH
        val scale = min(1f, min((width - 2 * pad) / contentW, maxOf(MIN_SCALE, fitH))).coerceAtLeast(.01f)
        menuScale = scale
        child.pivotX = 0f; child.pivotY = 0f; child.scaleX = scale; child.scaleY = scale
        child.translationX = (width - contentW * scale) / 2
        val scaledH = contentH * scale
        child.translationY = if (scaledH <= height - 2 * pad) (height - scaledH) / 2 else {
            val topAligned = pad; val bottomAligned = height - pad - scaledH
            val focus = focusView
            if (focus == null) topAligned else (height / 2f - centerWithin(focus, child) * scale).coerceIn(bottomAligned, topAligned)
        }
    }

    private fun centerWithin(view: View, ancestor: View): Float {
        var y = view.top + view.height / 2f
        var parent = view.parent as? View
        while (parent != null && parent !== ancestor) { y += parent.top; parent = parent.parent as? View }
        return y
    }

    internal companion object {
        /** Smallest scale; 16sp labels stay readable and a 48dp corner still reads as 36dp. */
        const val MIN_SCALE = .75f
        const val FRAME_PAD_DP = 4f
    }
}

/** The actual per-surface night tokens and custom color/opacity rules. */
@Composable
fun NightSurfacesLivePreview(settings: LauncherSettings, modifier: Modifier = Modifier) {
    val systemNight = isSystemInDarkTheme()
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LauncherSurface.entries.toList().chunked(2).forEach { surfaces ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                surfaces.forEach { surface ->
                    val tokens = SurfaceColors.resolve(surface, settings.night, systemNight)
                    val custom = when (surface) {
                        LauncherSurface.DRAWER -> settings.drawer.backgroundColor
                        LauncherSurface.FOLDER -> settings.folders.backgroundColor
                        LauncherSurface.SEARCH -> settings.search.backgroundColor
                        LauncherSurface.MENU -> settings.lookFeel.menuBackgroundColor
                    }
                    val label = stringResource(when (surface) {
                        LauncherSurface.DRAWER -> R.string.settings_section_drawer
                        LauncherSurface.FOLDER -> R.string.settings_section_folders
                        LauncherSurface.SEARCH -> R.string.settings_section_search
                        LauncherSurface.MENU -> R.string.look_header_menus
                    })
                    val description = stringResource(R.string.preview_appearance_night_surface_description, label,
                        stringResource(if (custom != null) R.string.preview_appearance_custom_color else if (tokens.isDark) R.string.preview_appearance_dark else R.string.preview_appearance_light))
                    val tile = Modifier.weight(1f)
                    when (surface) {
                        LauncherSurface.SEARCH -> SearchTheme(settings) {
                            val colors = MaterialTheme.colorScheme
                            NightSurfaceTile(colors.surface, colors.onSurface, label, description, tile)
                        }
                        LauncherSurface.MENU -> {
                            val colors = LauncherMenuStyle(context, settings).colors
                            NightSurfaceTile(Color(colors.background), Color(colors.onSurface), label, description, tile)
                        }
                        LauncherSurface.DRAWER -> {
                            val colors = drawerSampleColors(tokens, settings.drawer)
                            NightSurfaceTile(Color(colors.background), Color(settings.drawer.labelColor ?: colors.content), label, description, tile)
                        }
                        LauncherSurface.FOLDER -> {
                            val colors = folderNightSampleColors(tokens, settings)
                            NightSurfaceTile(Color(colors.background), Color(colors.content), label, description, tile)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.preview_appearance_night_info), style = MaterialTheme.typography.labelSmall)
    }
}

internal fun folderNightSampleColors(tokens: SurfaceColors, settings: LauncherSettings): DrawerSampleColors {
    val base = settings.folders.backgroundColor ?: tokens.background
    val alpha = settings.folders.backgroundAlpha.coerceIn(0f, 1f)
    val dark = alpha < .5f || ColorUtils.calculateLuminance(ColorUtils.setAlphaComponent(base, 255)) < .5
    return DrawerSampleColors(ColorUtils.setAlphaComponent(base, (alpha * 255).toInt()),
        settings.folders.labelColor ?: if (dark) SurfaceColors.DARK.onSurface else SurfaceColors.LIGHT.onSurface)
}

@Composable
private fun NightSurfaceTile(background: Color, content: Color, label: String, description: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(PREVIEW_WALLPAPER.map { Color(it) }))) {
        Column(Modifier.fillMaxWidth().background(background).padding(10.dp).clearAndSetSemantics { contentDescription = description }) {
            Text(label, color = content, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                PREVIEW_SAMPLES.take(3).forEach { (glyph, _) -> Icon(painterResource(glyph), null, tint = content, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}
