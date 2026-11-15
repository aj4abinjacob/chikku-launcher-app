package io.github.aj4abinjacob.chikku.settings.previews

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.drawer.DrawerModel
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerSettings
import io.github.aj4abinjacob.chikku.core.model.settings.DrawerStyle
import io.github.aj4abinjacob.chikku.core.model.settings.FolderSettings
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.core.model.settings.NightSettings
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.home.AppIconView
import io.github.aj4abinjacob.chikku.home.drawer.DrawerChrome
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.NightModeResolver
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.roundToInt

/** Drawer background and the label colour drawn on it, chosen as the drawer itself chooses them. */
internal data class DrawerSampleColors(val background: Int, val content: Int)

/**
 * Mirrors DrawerController.applyAppearance: the theme (night-mode) surface unless the user picked a
 * colour, multiplied by the opacity; labels take the theme's colour on an untouched opaque drawer,
 * else light or dark content by [DrawerModel.prefersLightContent].
 */
internal fun drawerSampleColors(theme: SurfaceColors, drawer: DrawerSettings): DrawerSampleColors {
    val background = DrawerModel.backgroundColor(theme.background, drawer.backgroundColor, drawer.backgroundAlpha)
    val content = DrawerChrome.contentColor(theme.onSurface, drawer, DrawerModel.prefersLightContent(background))
    return DrawerSampleColors(background, content)
}

/**
 * Sample drawer icons on the drawer's effective background over a stand-in wallpaper, so the
 * opacity is visible. [drawer] may be a draft that follows a slider while it is dragged.
 * [description] replaces the generic icon-preview description when given.
 */
@Composable
fun DrawerSamplePreview(
    drawer: DrawerSettings,
    night: NightSettings,
    desktopIconScale: Float,
    modifier: Modifier = Modifier,
    description: String? = null,
    animationSpeed: AnimationSpeed = AnimationSpeed.NORMAL,
    /** Only its icon shape is used: the samples take the same mask as real icons. */
    lookFeel: io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings =
        io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings(),
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val detailHeight = previewSceneHeight(config.screenHeightDp, config.fontScale, 188f).dp
    val colors = drawerSampleColors(SurfaceColors.resolve(LauncherSurface.DRAWER, night, context), drawer)
    val look = IconSampleLook(
        iconScale = if (drawer.matchDesktopSize) desktopIconScale else drawer.iconScale,
        showLabels = drawer.showLabels,
        labelScale = drawer.labelScale,
        labelFont = drawer.labelFont,
        labelShadow = drawer.labelShadow,
        labelSingleLine = drawer.labelSingleLine,
        labelColor = drawer.labelColor ?: colors.content,
        listMode = drawer.style == DrawerStyle.LIST,
        iconShape = lookFeel.iconShape,
        reshapeLegacyIcons = lookFeel.reshapeLegacyIcons,
    )
    // The mini viewport shows geometry; an enlarged real cell keeps font, shadow and line changes legible.
    Box(modifier.fillMaxWidth()) {
        DrawerLayoutLivePreview(LauncherSettings(drawer = drawer, night = night,
            home = io.github.aj4abinjacob.chikku.core.model.settings.HomeSettings(iconScale = desktopIconScale),
            lookFeel = io.github.aj4abinjacob.chikku.core.model.settings.LookFeelSettings(animationSpeed = animationSpeed)),
            description = description, pageControls = false)
        AndroidView(
            factory = { AppIconView(it, AppIconView.Style.DRAWER).apply {
                icon = previewSampleIcon(it, 3, lookFeel.iconShape, lookFeel.reshapeLegacyIcons)
                label = it.getString(PREVIEW_SAMPLES[3].second)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } },
            modifier = Modifier.align(Alignment.TopEnd).width(112.dp).height(detailHeight).clearAndSetSemantics {},
            update = { cell ->
                cell.background = LayerDrawable(arrayOf(wallpaperBackground(), GradientDrawable().apply { setColor(colors.background) }))
                look.style.applyTo(cell)
            },
        )
    }
}

/** Same rule as FolderController.themePanelColor; the wallpaper tint is device-specific and left out. */
internal fun folderThemePanelColor(context: Context, night: NightSettings): Int {
    val colors = SurfaceColors.resolve(LauncherSurface.FOLDER, night, context)
    return if (colors.isDark || NightModeResolver.isConfigurationNight(context.resources.configuration)) {
        colors.background
    } else {
        ContextCompat.getColor(context, R.color.folder_panel_background)
    }
}
