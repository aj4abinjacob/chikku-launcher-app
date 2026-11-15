package io.github.aj4abinjacob.chikku.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.toDrawable
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.settings.AnimationSpeed
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.theme.LauncherSurface
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors

/**
 * The look shared by HOME's popup menus (home and widget menus): one rounded card in the menu
 * surface colors (Style menu color, text color and corner radius), with icon rows and
 * dividers, floating on a soft shadow.
 */
class LauncherMenuStyle(private val context: Context, private val prefs: LauncherSettings) {

    val colors: SurfaceColors = run {
        val base = SurfaceColors.resolve(LauncherSurface.MENU, prefs.night, context)
        val look = prefs.lookFeel
        val contrast = look.menuBackgroundColor?.let { background ->
            if (ColorUtils.calculateLuminance(background) < 0.5) SurfaceColors.DARK else SurfaceColors.LIGHT
        } ?: base
        base.copy(
            background = look.menuBackgroundColor ?: base.background,
            onSurface = look.menuTextColor ?: contrast.onSurface,
            onSurfaceVariant = look.menuTextColor ?: contrast.onSurfaceVariant,
        )
    }

    /** The rounded card that holds the rows. */
    fun card(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBackground(colors, prefs.lookFeel.menuCornerRadiusDp, prefs.lookFeel.menuBackgroundColor != null, context)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = dp(CARD_ELEVATION_DP / 2)
        val v = dp(6f).toInt()
        setPadding(0, v, 0, v)
    }

    /** A focusable popup around [card], with a transparent margin so the card's shadow is not clipped. */
    fun popup(card: View): PopupWindow {
        val frame = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(CARD_ELEVATION_DP).toInt()
            setPadding(pad, pad, pad, pad)
            addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return PopupWindow(frame, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = 0f
            isOutsideTouchable = true
            animationStyle = popupAnimationStyle(Motion.current(prefs.lookFeel.animationSpeed))
        }
    }

    /** An icon and a label; [onClick] runs on tap. At least [ROW_HEIGHT_DP] tall. */
    fun row(title: CharSequence, @DrawableRes iconRes: Int, onClick: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(ROW_HEIGHT_DP).toInt()
        val h = dp(16f).toInt()
        setPadding(h, 0, h, 0)
        isClickable = true
        isFocusable = true
        selectableBackground()
        setOnClickListener { onClick() }
        if (iconRes != 0) {
            addView(icon(iconRes), LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()).apply {
                marginEnd = dp(16f).toInt()
            })
        }
        addView(TextView(context).apply {
            text = title
            setTextColor(colors.onSurface)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    fun icon(@DrawableRes res: Int) = ImageView(context).apply {
        setImageDrawable(context.getDrawable(res)?.mutate())
        setColorFilter(colors.onSurfaceVariant)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun divider() = View(context).apply {
        setBackgroundColor(colors.divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1f).toInt().coerceAtLeast(1)).apply {
            topMargin = dp(6f).toInt()
            bottomMargin = dp(6f).toInt()
        }
    }

    fun dp(value: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

    private fun View.selectableBackground() {
        val out = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)) setBackgroundResource(out.resourceId)
    }

    companion object {
        const val CARD_ELEVATION_DP = 6f

        /** Minimum height of a menu row. */
        const val ROW_HEIGHT_DP = 52f

        /** Largest menu corner radius the Style setting offers. */
        const val MAX_CORNER_RADIUS_DP = 48f

        /** Strength of [actionColor] against the menu's text color. */
        private const val ACTION_ALPHA = 0.66f

        /** White laid over a dark menu's fill so the card stands a step above a dark drawer. */
        private const val DARK_LIFT = 0.08f

        /** Hairline around a dark card, where its shadow does not show (white at 16%). */
        private const val DARK_OUTLINE = 0x29FFFFFF

        /** How much a long-press menu dims the screen behind it. */
        const val SCRIM_DIM = 0.32f

        /**
         * Dims everything behind a shown menu [window], as Pixel and One UI launchers do, so the
         * menu does not look pasted onto the icons around it. Tapping the dimmed area closes the
         * menu (it is outside-touchable). The window manager fades the dim with the window.
         */
        fun dimBehind(window: PopupWindow) {
            val root = window.contentView?.rootView ?: return
            val params = root.layoutParams as? WindowManager.LayoutParams ?: return
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            params.dimAmount = SCRIM_DIM
            try {
                root.context.getSystemService(WindowManager::class.java)?.updateViewLayout(root, params)
            } catch (_: IllegalArgumentException) {
                // The window closed before it could be dimmed.
            }
        }

        /**
         * A popup card's fill. A shadow does not show on a dark drawer or wallpaper, so on a dark
         * surface the fill is lifted a step (unless the user picked their own [customBackground])
         * and the card gets a hairline outline; otherwise the card would melt into what is behind it.
         */
        fun cardBackground(colors: SurfaceColors, cornerRadiusDp: Float, customBackground: Boolean, context: Context): GradientDrawable {
            val density = context.resources.displayMetrics.density
            return GradientDrawable().apply {
                cornerRadius = (cornerRadiusDp.takeIf { it.isFinite() }?.coerceIn(0f, MAX_CORNER_RADIUS_DP) ?: 14f) * density
                setColor(cardFill(colors, customBackground))
                cardStroke(colors)?.let { setStroke(edgeWidth(context), it) }
            }
        }

        /** Width of a card's hairline outline, in px. */
        fun edgeWidth(context: Context): Int = context.resources.displayMetrics.density.toInt().coerceAtLeast(1)

        /** The card's fill color (see [cardBackground]). */
        fun cardFill(colors: SurfaceColors, customBackground: Boolean): Int =
            if (isDark(colors) && !customBackground) liftedDark(colors.background) else colors.background

        /**
         * Glyphs and labels of the launcher's own actions: the menu's text color, toned down, so
         * they read as secondary to the app's shortcuts (full-color icons, primary text).
         */
        fun actionColor(colors: SurfaceColors): Int =
            ColorUtils.setAlphaComponent(colors.onSurface, (Color.alpha(colors.onSurface) * ACTION_ALPHA).toInt())

        /**
         * Colour of the one destructive action (Uninstall), so it reads apart from the rest of the
         * menu: the Material error tone, lightened on a dark card.
         */
        fun dangerColor(colors: SurfaceColors): Int = if (isDark(colors)) 0xFFF2B8B5.toInt() else 0xFFB3261E.toInt()

        /** The card's hairline outline: only on a dark card, where its shadow does not show. */
        fun cardStroke(colors: SurfaceColors): Int? = if (isDark(colors)) DARK_OUTLINE else null

        /** The card's edge: the hairline on a dark card, the divider color on a light one (whose shadow shows). */
        fun cardEdge(colors: SurfaceColors): Int = if (isDark(colors)) DARK_OUTLINE else colors.divider

        private fun isDark(colors: SurfaceColors) = ColorUtils.calculateLuminance(colors.background) < 0.5

        fun liftedDark(background: Int): Int =
            ColorUtils.compositeColors(ColorUtils.setAlphaComponent(Color.WHITE, (DARK_LIFT * 255).toInt()), background)

        /**
         * Window animation for HOME's popup menus: Chikku's own fade and scale at the user's
         * animation speed, none when the user (or the system) turned animations off. Never `-1`,
         * which picks the platform's drop-down animation and lets the device skin (One UI, MIUI)
         * restyle it.
         */
        fun popupAnimationStyle(motion: Motion): Int = if (!motion.enabled) 0 else when (motion.speed) {
            AnimationSpeed.FAST -> R.style.Animation_Chikku_Popup_Fast
            AnimationSpeed.SLOW -> R.style.Animation_Chikku_Popup_Slow
            AnimationSpeed.RELAXED -> R.style.Animation_Chikku_Popup_Relaxed
            AnimationSpeed.NORMAL, AnimationSpeed.NONE -> R.style.Animation_Chikku_Popup
        }
    }
}
