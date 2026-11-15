package io.github.aj4abinjacob.chikku.home.drawer

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.graphics.ColorUtils

/**
 * Letter bubble shown beside the fast scroller while it is dragged. Drawn in the parent's overlay,
 * so it never takes layout space or touches; TalkBack users already have the per-letter buttons.
 * The corner nearest the rail is square, pointing at the finger.
 */
internal class FastScrollerPopup(private val size: Float, private val textSize: Float) : Drawable() {

    var letter: String = ""
        private set
    var color: Int = 0
        set(value) {
            field = value
            fill.color = value
            text.color = if (ColorUtils.calculateLuminance(value) > 0.5) 0xDE000000.toInt() else 0xFFFFFFFF.toInt()
        }

    /** [io.github.aj4abinjacob.chikku.theme.Motion.scale]: 0 snaps, 1 is normal speed. */
    var motionScale: Float = 1f

    /** Whether the bubble is shown or animating in; false once it starts to hide. */
    val showing: Boolean get() = target == 1f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val path = Path()
    private val rect = RectF()
    private var pointsRight = true
    private var progress = 0f
    private var target = 0f
    private var bump = 0f
    private var animator: ValueAnimator? = null
    private var bumpAnimator: ValueAnimator? = null
    /** Runs when the hide animation finishes, so the owner can remove the drawable. */
    var onHidden: (() -> Unit)? = null

    /**
     * Places the bubble beside a rail at [railEdge] (the rail's edge facing the list), with its
     * pointed corner at [y], kept inside [parentHeight].
     */
    fun place(letter: String, railEdge: Float, y: Float, railOnRight: Boolean, parentHeight: Int, margin: Float) {
        if (letter != this.letter && this.letter.isNotEmpty() && showing) bounce()
        this.letter = letter
        pointsRight = railOnRight
        val top = (y - size).coerceIn(0f, (parentHeight - size).coerceAtLeast(0f))
        val left = if (railOnRight) railEdge - margin - size else railEdge + margin
        setBounds(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
        invalidateSelf()
    }

    fun show() = animateTo(1f)

    fun hide() = animateTo(0f)

    private fun animateTo(value: Float) {
        if (target == value) return
        target = value
        animator?.cancel()
        if (motionScale <= 0f || !ValueAnimator.areAnimatorsEnabled()) {
            progress = value
            invalidateSelf()
            if (value == 0f) onHidden?.invoke()
            return
        }
        animator = ValueAnimator.ofFloat(progress, value).apply {
            duration = ((if (value == 1f) SHOW_MS else HIDE_MS) * motionScale).toLong()
            interpolator = if (value == 1f) OvershootInterpolator(1.6f) else DecelerateInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; invalidateSelf() }
            if (value == 0f) {
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    private var cancelled = false
                    override fun onAnimationCancel(animation: android.animation.Animator) { cancelled = true }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        if (!cancelled) onHidden?.invoke()
                    }
                })
            }
            start()
        }
    }

    /** A small pop each time the letter changes, so the change is noticed at a glance. */
    private fun bounce() {
        bumpAnimator?.cancel()
        if (motionScale <= 0f || !ValueAnimator.areAnimatorsEnabled()) return
        bumpAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = (BUMP_MS * motionScale).toLong()
            interpolator = DecelerateInterpolator()
            addUpdateListener { bump = it.animatedValue as Float; invalidateSelf() }
            start()
        }
    }

    fun cancelAnimations() {
        animator?.cancel()
        bumpAnimator?.cancel()
        progress = 0f
        target = 0f
        bump = 0f
    }

    override fun draw(canvas: Canvas) {
        if (progress <= 0f || letter.isEmpty()) return
        val b = bounds
        val scale = (0.4f + 0.6f * progress + 0.08f * bump).coerceAtLeast(0f)
        // Grow out of the pointed corner, which sits beside the finger on the rail.
        val pivotX = if (pointsRight) b.right.toFloat() else b.left.toFloat()
        val save = canvas.save()
        canvas.scale(scale, scale, pivotX, b.bottom.toFloat())
        val alpha = (progress.coerceIn(0f, 1f) * 255).toInt()
        fill.alpha = alpha * android.graphics.Color.alpha(color) / 255
        text.alpha = alpha
        rect.set(b)
        val r = size / 2f
        // Corners: top-left, top-right, bottom-right, bottom-left.
        val radii = if (pointsRight) floatArrayOf(r, r, r, r, 0f, 0f, r, r) else floatArrayOf(r, r, r, r, r, r, 0f, 0f)
        path.reset()
        path.addRoundRect(rect, radii, Path.Direction.CW)
        canvas.drawPath(path, fill)
        // Bucket labels can be wider than one Latin letter (kana, "…"); keep them inside the bubble.
        text.textSize = textSize
        val maxWidth = size * 0.7f
        val width = text.measureText(letter)
        if (width > maxWidth) text.textSize = textSize * maxWidth / width
        val baseline = rect.centerY() - (text.descent() + text.ascent()) / 2f
        canvas.drawText(letter, rect.centerX(), baseline, text)
        canvas.restoreToCount(save)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fill.colorFilter = colorFilter
        text.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val SHOW_MS = 220L
        const val HIDE_MS = 160L
        const val BUMP_MS = 140L
    }
}
