package io.github.aj4abinjacob.chikku.home.cards

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.theme.Motion
import io.github.aj4abinjacob.chikku.theme.SurfaceColors
import kotlin.math.max
import kotlin.math.min

internal data class CardSurfaceItem(
    val card: AutomationCard,
    val featuredLabel: String?,
    val actions: List<Pair<Int, String>>,
    /** When the card shows, e.g. "Weekdays · 09:00–18:00"; null for an unconditional card. */
    val subtitle: String? = null,
)

/**
 * Matching cards in a floating sheet at the bottom of HOME, within thumb reach. Each card shows its
 * name, why it is showing, optional content, a large featured button and a row of action icons.
 * Featured Home widget references navigate to their placement. Drag the handle down to dismiss.
 */
internal class CardsSurface(
    private val root: FrameLayout,
    private val onClose: () -> Unit,
    private val onSettings: () -> Unit,
    private val onTap: (String, Int?, View) -> Unit,
    /** Replaces the placeholder in [ImageView] with the app or shortcut icon of an action, when it loads. */
    private val loadIcon: (GestureAction, ImageView) -> Unit = { _, _ -> },
    /** A card's view is being replaced or removed; release anything hosted inside it (widgets). */
    private val onCardDiscarded: (String) -> Unit = {},
    private val createContent: (AutomationCard, SurfaceColors) -> View? = { _, _ -> null },
) {
    private val context = root.context
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val overlay = FrameLayout(context).apply {
        tag = "cards_panel"
        setBackgroundColor(SCRIM)
        contentDescription = context.getString(R.string.cards_surface_close)
        isClickable = true
        isFocusable = true
        setOnClickListener { onClose() }
    }
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        elevation = dp(12).toFloat()
    }
    private val handle = View(context)
    private val heading = TextView(context).apply {
        text = context.getString(R.string.cards_settings_title)
        textSize = 22f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        ViewCompat.setAccessibilityHeading(this, true)
    }
    private val settings = iconButton(R.drawable.ic_menu_edit, context.getString(R.string.cards_surface_settings), onSettings)
    private val close = iconButton(R.drawable.ic_search_clear, context.getString(R.string.cards_surface_close), onClose)
    private val obscured = mutableListOf<Pair<View, Int>>()
    private var edgeInsets = Insets.NONE

    /** One entry per child of [content]: what it shows, so unchanged cards keep their views. */
    private class Shown(val signature: Any, val view: View, val cardId: String?)
    private val shown = LinkedHashMap<String, Shown>()
    private var shownColors: SurfaceColors? = null
    private var rendered = false
    private var motion = Motion.DEFAULT
    private var closing = false
    private var onClosed: (() -> Unit)? = null

    init {
        val grabber = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(handle, LinearLayout.LayoutParams(dp(36), dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(10)
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(2), dp(8), dp(4))
                addView(heading, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(settings, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
            })
        }
        dragToDismiss(grabber)
        panel.addView(grabber)
        panel.addView(ScrollView(context).apply {
            isFillViewport = false
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(12), dp(4), dp(12), dp(12))
            addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        overlay.addView(panel, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL))
        ViewCompat.setOnApplyWindowInsetsListener(overlay) { _, insets ->
            edgeInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            applyPanelBounds()
            insets
        }
        overlay.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) applyPanelBounds()
        }
        applyPanelBounds()
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            obscured += child to child.importantForAccessibility
            child.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        root.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.requestApplyInsets(overlay)
        ViewCompat.setAccessibilityPaneTitle(panel, context.getString(R.string.cards_settings_title))
        close.requestFocus()
    }

    fun render(
        items: List<CardSurfaceItem>,
        colors: SurfaceColors,
        motion: Motion,
        animate: Boolean = false,
        empty: CardsPanelEmpty = CardsPanelEmpty.NONE_MATCH,
    ) {
        val cardColor = layer(colors.background, colors.onSurface, if (colors.isDark) 0.08f else 0.05f)
        val tileColor = layer(colors.background, colors.onSurface, if (colors.isDark) 0.14f else 0.09f)
        heading.setTextColor(colors.onSurface)
        listOf(settings, close).forEach { it.imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant) }
        handle.background = rounded(ColorUtils.setAlphaComponent(colors.onSurfaceVariant, 0x66), 2)
        panel.background = rounded(colors.background, 32)
        this.motion = motion
        reconcile(items, empty, colors, cardColor, tileColor, motion)
        if (animate && motion.enabled) {
            overlay.alpha = 0f
            overlay.animate().alpha(1f).setDuration(motion.duration(180)).start()
            panel.translationY = dp(56).toFloat()
            panel.animate().translationY(0f).setInterpolator(DecelerateInterpolator(2f))
                .setDuration(motion.duration(260)).start()
        }
    }

    /**
     * Updates [content] in place: cards whose item and colours are unchanged keep their views (scroll
     * position, press state, hosted widgets); only changed, new and removed cards are touched.
     */
    private fun reconcile(items: List<CardSurfaceItem>, empty: CardsPanelEmpty, colors: SurfaceColors,
        cardColor: Int, tileColor: Int, motion: Motion) {
        val restyle = shownColors != colors
        shownColors = colors
        val desired = LinkedHashMap<String, Pair<Any, () -> View>>()
        if (items.isEmpty() && empty != CardsPanelEmpty.PENDING) {
            desired["empty:${empty.name}"] = empty to { emptyState(empty, colors, tileColor) }
        }
        items.forEach { item -> desired["card:${item.card.id}"] = item to { card(item, colors, cardColor, tileColor) } }
        // Drop views that are gone or stale first, so replacements never overlap their predecessors.
        val iterator = shown.entries.iterator()
        while (iterator.hasNext()) {
            val (key, old) = iterator.next()
            val want = desired[key]
            if (want != null && !restyle && want.first == old.signature) continue
            iterator.remove()
            content.removeView(old.view)
            old.cardId?.let(onCardDiscarded)
        }
        val animateIn = rendered && motion.enabled
        var index = 0
        desired.forEach { (key, want) ->
            val existing = shown[key]
            val view = existing?.view ?: want.second().also { created ->
                shown[key] = Shown(want.first, created, key.removePrefix("card:").takeIf { key.startsWith("card:") })
                if (animateIn) {
                    created.alpha = 0f
                    created.animate().alpha(1f).setDuration(motion.duration(160)).start()
                }
            }
            if (content.getChildAt(index) !== view) {
                content.removeView(view)
                content.addView(view, index, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { if (key.startsWith("card:")) bottomMargin = dp(10) })
            }
            index++
        }
        rendered = true
    }

    /**
     * Dismisses the sheet. With [animate] it slides down and fades from wherever it is (also from a
     * half-finished drag), mirroring the open animation; [onClosed] runs once it is fully gone.
     */
    fun close(animate: Boolean = false, onClosed: () -> Unit = {}) {
        if (closing) {
            if (!animate) finishClose()
            return
        }
        closing = true
        this.onClosed = onClosed
        val duration = motion.duration(EXIT_MS)
        if (!animate || duration == 0L || overlay.parent == null || !overlay.isAttachedToWindow) { finishClose(); return }
        panel.animate().cancel()
        overlay.animate().cancel()
        overlay.isClickable = false
        val distance = (panel.height + (panel.layoutParams as FrameLayout.LayoutParams).bottomMargin).toFloat()
        val remaining = (1f - panel.translationY / distance.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val scaled = (duration * remaining).toLong().coerceAtLeast(motion.duration(60))
        overlay.animate().alpha(0f).setDuration(scaled).start()
        panel.animate().translationY(distance).setInterpolator(AccelerateInterpolator(1.5f)).setDuration(scaled)
            .withEndAction { finishClose() }.start()
    }

    private fun finishClose() {
        closing = true
        panel.animate().cancel()
        overlay.animate().cancel()
        if (overlay.parent === root) root.removeView(overlay)
        obscured.forEach { (view, importance) -> view.importantForAccessibility = importance }
        obscured.clear()
        onClosed?.also { onClosed = null }?.invoke()
    }

    private fun card(item: CardSurfaceItem, colors: SurfaceColors, cardColor: Int, tileColor: Int): View {
        val id = item.card.id
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = rounded(cardColor, 24)
            tag = "card:$id"
        }
        card.addView(TextView(context).apply {
            text = item.card.title
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(colors.onSurface)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            ViewCompat.setAccessibilityHeading(this, true)
        })
        item.subtitle?.let { subtitle ->
            card.addView(TextView(context).apply {
                text = subtitle
                textSize = 13f
                setTextColor(colors.onSurfaceVariant)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
            })
        }
        createContent(item.card, colors)?.let { embedded ->
            card.addView(embedded, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        item.featuredLabel?.let { label ->
            card.addView(featured(id, label, item.card.featured, colors, tileColor),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(12) })
        }
        if (item.actions.isNotEmpty()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            item.actions.forEach { (index, label) ->
                val action = item.card.actions.getOrNull(index) ?: GestureAction.None
                row.addView(actionTile(id, index, label, action, colors, tileColor),
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            // Keep tiles icon-sized when a card has only one or two actions.
            repeat((MIN_ROW_SLOTS - item.actions.size).coerceAtLeast(0)) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
            }
            card.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        }
        return card
    }

    /** A wide pill: icon, label and a chevron. */
    private fun featured(id: String, label: String, target: CardFeatured, colors: SurfaceColors, tileColor: Int) =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            setPadding(dp(10), dp(8), dp(12), dp(8))
            background = pressable(tileColor, colors.onSurface, 18)
            tag = "card_featured:$id"
            contentDescription = label
            isClickable = true
            isFocusable = true
            setOnClickListener { v -> onTap(id, null, v) }
            val icon = ImageView(context).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            when (target) {
                is CardFeatured.App -> appIcon(icon, GestureAction.LaunchApp(target.key))
                is CardFeatured.Shortcut -> appIcon(icon, GestureAction.LaunchShortcut(target.packageName, target.shortcutId, target.userSerial))
                is CardFeatured.Widget -> glyph(icon, R.drawable.ic_menu_widgets, colors.onSurface)
                CardFeatured.None -> glyph(icon, R.drawable.ic_settings_cards, colors.onSurface)
            }
            addView(icon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(TextView(context).apply {
                text = label
                textSize = 16f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(colors.onSurface)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(12), 0, dp(8), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_settings_chevron)
                imageTintList = ColorStateList.valueOf(colors.onSurfaceVariant)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(20), dp(20)))
        }

    /** A round icon with a one-line caption, like a quick-settings tile. */
    private fun actionTile(id: String, index: Int, label: String, action: GestureAction, colors: SurfaceColors, tileColor: Int) =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumHeight = dp(48)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            background = pressable(0, colors.onSurface, 16)
            tag = "card_action:$id:$index"
            contentDescription = label
            isClickable = true
            isFocusable = true
            setOnClickListener { v -> onTap(id, index, v) }
            val launches = action is GestureAction.LaunchApp || action is GestureAction.LaunchShortcut
            val icon = ImageView(context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if (launches) appIcon(this, action) else {
                    glyph(this, cardActionGlyph(action), colors.onSurface)
                    background = rounded(tileColor, 24)
                    val pad = dp(12)
                    setPadding(pad, pad, pad, pad)
                }
            }
            addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(context).apply {
                text = shortLabel(label, action)
                textSize = 12f
                gravity = Gravity.CENTER_HORIZONTAL
                setTextColor(colors.onSurfaceVariant)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun emptyState(empty: CardsPanelEmpty, colors: SurfaceColors, tileColor: Int) = LinearLayout(context).apply {
        tag = "cards_empty:${empty.name}"
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(20), dp(16), dp(12))
        addView(ImageView(context).apply {
            glyph(this, R.drawable.ic_settings_cards, colors.onSurfaceVariant)
            background = rounded(tileColor, 32)
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(64), dp(64)))
        addView(TextView(context).apply {
            text = context.getString(empty.title)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(colors.onSurface)
            setPadding(0, dp(16), 0, dp(4))
        })
        addView(TextView(context).apply {
            text = context.getString(empty.body)
            textSize = 14f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(colors.onSurfaceVariant)
        })
        addView(TextView(context).apply {
            text = context.getString(empty.action)
            contentDescription = text
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(colors.onSurface)
            minHeight = dp(48)
            setPadding(dp(24), 0, dp(24), 0)
            background = pressable(tileColor, colors.onSurface, 24)
            isClickable = true
            isFocusable = true
            setOnClickListener { onSettings() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(16) })
    }

    /** "Open Maps" reads as "Maps" under an icon; other actions keep their names. */
    private fun shortLabel(label: String, action: GestureAction): String {
        if (action !is GestureAction.LaunchApp) return label
        val template = context.getString(R.string.gesture_action_launch_app, "\u0000")
        val prefix = template.substringBefore('\u0000')
        val suffix = template.substringAfter('\u0000')
        return if (label.startsWith(prefix) && label.endsWith(suffix) && label.length > prefix.length + suffix.length)
            label.substring(prefix.length, label.length - suffix.length) else label
    }

    private fun appIcon(view: ImageView, action: GestureAction) {
        view.setImageResource(R.drawable.ic_app_placeholder)
        loadIcon(action, view)
    }

    private fun glyph(view: ImageView, @DrawableRes res: Int, tint: Int) {
        view.setImageResource(res)
        view.imageTintList = ColorStateList.valueOf(tint)
    }

    /** Drag the handle or title down to dismiss; a short drag springs back. */
    @SuppressLint("ClickableViewAccessibility")
    private fun dragToDismiss(grabber: View) {
        var startY = 0f
        grabber.setOnTouchListener { _, event ->
            if (closing) return@setOnTouchListener true
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    // A spring-back may still be running: stop both, keeping where they are.
                    panel.animate().cancel()
                    overlay.animate().cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panel.translationY = (event.rawY - startY).coerceAtLeast(0f)
                    overlay.alpha = (1f - panel.translationY / max(panel.height, 1)).coerceIn(DRAG_MIN_ALPHA, 1f)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (event.actionMasked == MotionEvent.ACTION_UP && panel.translationY > min(panel.height / 4f, dp(120).toFloat())) onClose()
                    else {
                        val duration = motion.duration(SPRING_BACK_MS)
                        if (duration == 0L) { panel.translationY = 0f; overlay.alpha = 1f }
                        else {
                            overlay.animate().alpha(1f).setDuration(duration).start()
                            panel.animate().translationY(0f).setDuration(duration).start()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Floats above the navigation bar; centered with a bounded width on large screens. */
    private fun applyPanelBounds() {
        val width = overlay.width.takeIf { it > 0 } ?: root.width
        val bounded = width > dp(640)
        val safeSide = max(edgeInsets.left, edgeInsets.right)
        val left = dp(8) + if (bounded) safeSide else edgeInsets.left
        val right = dp(8) + if (bounded) safeSide else edgeInsets.right
        (panel.layoutParams as FrameLayout.LayoutParams).let { params ->
            params.gravity = Gravity.BOTTOM or if (bounded) Gravity.CENTER_HORIZONTAL else Gravity.FILL_HORIZONTAL
            params.width = if (bounded) min(dp(600), (width - left - right).coerceAtLeast(0)) else ViewGroup.LayoutParams.MATCH_PARENT
            params.setMargins(left, dp(24) + edgeInsets.top, right, dp(8) + edgeInsets.bottom)
            panel.layoutParams = params
        }
    }

    private fun iconButton(@DrawableRes res: Int, label: String, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(res)
        contentDescription = label
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        val pad = dp(12)
        setPadding(pad, pad, pad, pad)
        isClickable = true
        isFocusable = true
        background = RippleDrawable(ColorStateList.valueOf(0x22808080), null, rounded(0xFF000000.toInt(), 24))
        setOnClickListener { onClick() }
    }

    private fun pressable(fill: Int, onColor: Int, radius: Int): Drawable = RippleDrawable(
        ColorStateList.valueOf(ColorUtils.setAlphaComponent(onColor, 0x1F)),
        if (fill == 0) null else rounded(fill, radius), rounded(0xFF000000.toInt(), radius),
    )

    private fun layer(base: Int, over: Int, amount: Float) = ColorUtils.blendARGB(base, over, amount)

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val SCRIM = 0x80000000.toInt()
        const val MIN_ROW_SLOTS = 4
        const val EXIT_MS = 220L
        const val SPRING_BACK_MS = 160L
        const val DRAG_MIN_ALPHA = 0.35f
    }
}
