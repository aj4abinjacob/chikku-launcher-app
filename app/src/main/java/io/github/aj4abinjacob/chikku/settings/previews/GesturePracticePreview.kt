package io.github.aj4abinjacob.chikku.settings.previews

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.AppCustomization
import io.github.aj4abinjacob.chikku.core.model.AppKey
import io.github.aj4abinjacob.chikku.core.model.CellPlacement
import io.github.aj4abinjacob.chikku.core.model.ContainerType
import io.github.aj4abinjacob.chikku.core.model.ItemActions
import io.github.aj4abinjacob.chikku.core.model.settings.LauncherSettings
import io.github.aj4abinjacob.chikku.home.SwipeDirection
import io.github.aj4abinjacob.chikku.home.Availability
import io.github.aj4abinjacob.chikku.home.HomeItemUi
import io.github.aj4abinjacob.chikku.home.ItemContent
import io.github.aj4abinjacob.chikku.home.folder.FolderGesture
import io.github.aj4abinjacob.chikku.home.folder.FolderGestureOutcome
import io.github.aj4abinjacob.chikku.home.folder.FolderGestures
import io.github.aj4abinjacob.chikku.home.gestures.DoubleTapTracker
import io.github.aj4abinjacob.chikku.home.gestures.MultiTouchGesture
import io.github.aj4abinjacob.chikku.home.gestures.classifyMultiTouch
import io.github.aj4abinjacob.chikku.home.gestures.doubleTapSwipeAction
import io.github.aj4abinjacob.chikku.home.gestures.homeButtonAction
import io.github.aj4abinjacob.chikku.home.gestures.homeSwipeAction
import io.github.aj4abinjacob.chikku.home.gestures.iconSwipeNudge
import io.github.aj4abinjacob.chikku.home.gestures.horizontalSwipeAction
import io.github.aj4abinjacob.chikku.home.gestures.resolveItemSwipeAction
import io.github.aj4abinjacob.chikku.home.gestures.rotationDeltaDegrees
import io.github.aj4abinjacob.chikku.settings.components.SettingsChoiceRow
import io.github.aj4abinjacob.chikku.settings.components.simpleGestureActionLabel
import io.github.aj4abinjacob.chikku.settings.sections.HomeGesture
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Resolves exactly the launcher's policies but returns data only; there is no action executor. */
internal fun practiceGestureAction(settings: LauncherSettings, gesture: HomeGesture, onDefaultPage: Boolean = true): GestureAction = when (gesture) {
    HomeGesture.SWIPE_UP -> homeSwipeAction(settings, SwipeDirection.UP)
    HomeGesture.SWIPE_DOWN -> homeSwipeAction(settings, SwipeDirection.DOWN)
    HomeGesture.SWIPE_LEFT -> horizontalSwipeAction(settings, true)
    HomeGesture.SWIPE_RIGHT -> horizontalSwipeAction(settings, false)
    HomeGesture.DOUBLE_TAP_SWIPE_UP -> doubleTapSwipeAction(settings, SwipeDirection.UP)
    HomeGesture.DOUBLE_TAP_SWIPE_DOWN -> doubleTapSwipeAction(settings, SwipeDirection.DOWN)
    HomeGesture.HOME_BUTTON -> homeButtonAction(settings, onDefaultPage)
    else -> gesture.get(settings.gestures)
}

/** Preview labels never inspect installed-app inventories, shortcuts or profile contents. */
@Composable
private fun safePracticeActionLabel(action: GestureAction?, defaultLabel: String? = null): String = when (action) {
    null -> defaultLabel ?: stringResource(R.string.gesture_action_use_default)
    is GestureAction.LaunchApp -> stringResource(R.string.preview_gesture_launch_app)
    is GestureAction.LaunchShortcut -> stringResource(R.string.preview_gesture_launch_shortcut)
    else -> stringResource(simpleGestureActionLabel(action) ?: R.string.gesture_action_none)
}

/** Real item/default/home precedence, using only the selected key and no app inventory. */
internal fun iconPracticeSwipeActions(key: AppKey, actions: ItemActions, customization: AppCustomization?, settings: LauncherSettings): Pair<GestureAction, GestureAction> {
    val sample = HomeItemUi("preview", CellPlacement(ContainerType.DESKTOP, 0, 0, 0),
        ItemContent.App(key, label = null, isWork = false, availability = Availability.UNKNOWN), actions)
    val defaults = customization?.let { mapOf(key to it) }.orEmpty()
    fun effective(direction: SwipeDirection) = resolveItemSwipeAction(sample, direction, defaults, settings.folders.swipeActionsEnabled)
        ?: homeSwipeAction(settings, direction)
    return effective(SwipeDirection.UP) to effective(SwipeDirection.DOWN)
}

/** Folder defaults include swipe-up-to-open for an overridden tap and global swipe policy. */
internal fun folderPracticeSwipeActions(actions: ItemActions, enabled: Boolean): Pair<GestureAction?, GestureAction?> {
    fun action(gesture: FolderGesture): GestureAction? = when (val result = FolderGestures.resolve(gesture, actions, enabled)) {
        FolderGestureOutcome.OpenFolder -> GestureAction.OpenFolder
        is FolderGestureOutcome.Perform -> result.action
        FolderGestureOutcome.PassThrough -> null
    }
    return action(FolderGesture.SWIPE_UP) to action(FolderGesture.SWIPE_DOWN)
}

@Composable
fun FolderSwipePracticePreview(actions: ItemActions, enabled: Boolean, modifier: Modifier = Modifier) {
    val (up, down) = folderPracticeSwipeActions(actions, enabled)
    SwipeActionsPracticePreview(up, down, stringResource(R.string.preview_gesture_home_fallback), modifier)
}

@Composable
internal fun GesturePracticePreview(settings: LauncherSettings, selected: HomeGesture, onDetected: (HomeGesture) -> Unit, modifier: Modifier = Modifier,
    /** Hoisted so a page control (Only on default page) can show the case it changes; null keeps it local. */
    onDefaultPage: Boolean? = null, onDefaultPageChange: ((Boolean) -> Unit)? = null) {
    var detected by remember(selected) { mutableStateOf<HomeGesture?>(null) }
    var localDefaultPage by remember { mutableStateOf(true) }
    val defaultPage = onDefaultPage ?: localDefaultPage
    val shown = detected ?: selected
    val action = practiceGestureAction(settings, shown, defaultPage)
    val label = safePracticeActionLabel(action)
    val shownTitle = stringResource(shown.title)
    val summary = if (action == GestureAction.None) stringResource(R.string.preview_gesture_not_set, shownTitle)
    else stringResource(R.string.preview_gesture_would_run, shownTitle, label)
    val description = stringResource(R.string.preview_gesture_touch_description, summary)
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.preview_gesture_practising, shownTitle), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 12.dp, vertical = 4.dp))
        AndroidView(modifier = Modifier.fillMaxWidth().height(PracticePadHeight),
            factory = { GesturePracticePad(it) }, update = { pad ->
                pad.contentDescription = description
                pad.onGesture = { detected = it; onDetected(it) }
            })
        Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (selected == HomeGesture.HOME_BUTTON) TextButton(onClick = { localDefaultPage = !defaultPage; onDefaultPageChange?.invoke(!defaultPage); detected = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (defaultPage) R.string.preview_gesture_on_default_page else R.string.preview_gesture_other_page))
            }
            TextButton(onClick = { detected = selected }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.preview_gesture_simulate))
            }
        }
        Text(stringResource(R.string.preview_gesture_safe_info), style = MaterialTheme.typography.labelSmall)
    }
}

/** Tall enough to swipe, pinch and rotate on comfortably; the page caps the whole preview at 40% of the screen. */
private val PracticePadHeight = 136.dp

/** Reused in the icon/folder editor, with no callbacks capable of performing the action. */
@Composable
fun SwipeActionsPracticePreview(swipeUp: GestureAction?, swipeDown: GestureAction?, defaultLabel: String, modifier: Modifier = Modifier) {
    var up by remember { mutableStateOf(true) }
    val label = safePracticeActionLabel(if (up) swipeUp else swipeDown, defaultLabel)
    val description = stringResource(R.string.preview_gesture_icon_touch_description)
    Column(modifier) {
        AndroidView(modifier = Modifier.fillMaxWidth().height(80.dp),
            factory = { GesturePracticePad(it, verticalOnly = true) },
            update = { pad ->
                pad.contentDescription = description
                pad.onGesture = { if (it == HomeGesture.SWIPE_UP) up = true else if (it == HomeGesture.SWIPE_DOWN) up = false }
            })
        Text(stringResource(R.string.preview_gesture_would_run, stringResource(if (up) R.string.gestures_swipe_up else R.string.gestures_swipe_down), label),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { up = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.preview_gesture_try_up)) }
            TextButton(onClick = { up = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.preview_gesture_try_down)) }
        }
        Text(stringResource(R.string.preview_gesture_safe_info), style = MaterialTheme.typography.labelSmall)
    }
}

/** Isolated touch surface. Shared classifiers recognize gestures; release emits only an enum. */
internal class GesturePracticePad(context: Context, private val verticalOnly: Boolean = false) : View(context) {
    var onGesture: (HomeGesture) -> Unit = {}
    private val config = ViewConfiguration.get(context)
    private val threshold = config.scaledTouchSlop * 3f
    private val tap = DoubleTapTracker(ViewConfiguration.getTapTimeout().toLong(), ViewConfiguration.getDoubleTapTimeout().toLong(),
        config.scaledTouchSlop.toFloat(), config.scaledDoubleTapSlop.toFloat())
    private var startX = 0f; private var startY = 0f
    private var lastX = 0f; private var lastY = 0f
    private var secondTap = false; private var tracking = false; private var multi = false
    private var startSpan = 0f; private var centroidX = 0f; private var centroidY = 0f
    private var lastAngle = 0f; private var rotation = 0f
    private var multiGesture: MultiTouchGesture? = null
    private var invalidMulti = false
    /** Icon swipes: the arrows follow the finger as a swiped icon does on HOME. */
    private val maxNudge = resources.getDimension(R.dimen.gestures_icon_swipe_nudge)
    private val nudgeTravel = resources.getDimension(R.dimen.gestures_min_swipe_distance)
    private var nudge = 0f
    private var nudgeBack: ValueAnimator? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBBD5ED.toInt(); strokeWidth = 2 * resources.displayMetrics.density }

    init {
        background = wallpaperBackground()
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f; val centerY = height / 2f + nudge; val d = 15 * resources.displayMetrics.density
        canvas.drawLine(centerX, centerY - d, centerX, centerY + d, paint)
        canvas.drawLine(centerX - d / 2, centerY - d / 2, centerX, centerY - d, paint)
        canvas.drawLine(centerX + d / 2, centerY - d / 2, centerX, centerY - d, paint)
        canvas.drawLine(centerX - d / 2, centerY + d / 2, centerX, centerY + d, paint)
        canvas.drawLine(centerX + d / 2, centerY + d / 2, centerX, centerY + d, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                tracking = true; multi = false; multiGesture = null; rotation = 0f; invalidMulti = false
                startX = event.x; startY = event.y; lastX = event.x; lastY = event.y
                tap.onDown(startX, startY, event.eventTime, false); secondTap = tap.isSecondTap
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                tap.cancel(); secondTap = false
                if (event.pointerCount == 2 && !verticalOnly && !invalidMulti) {
                    multi = true
                    startSpan = span(event); centroidX = midpointX(event); centroidY = midpointY(event); lastAngle = angle(event)
                } else { multi = true; multiGesture = null; invalidMulti = true }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return true
                lastX = event.x; lastY = event.y
                if (multi && event.pointerCount == 2 && !verticalOnly && !invalidMulti) {
                    rotation += rotationDeltaDegrees(lastAngle, angle(event)); lastAngle = angle(event)
                    multiGesture = classifyMultiTouch(startSpan, span(event), midpointX(event) - centroidX, midpointY(event) - centroidY, threshold, rotation)
                } else if (!multi) {
                    tap.onMove(event.x, event.y)
                    if (verticalOnly && ValueAnimator.areAnimatorsEnabled()) {
                        val dy = event.y - startY
                        nudgeBack?.cancel()
                        nudge = iconSwipeNudge(if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN, dy, maxNudge, nudgeTravel)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return true
                tracking = false; parent?.requestDisallowInterceptTouchEvent(false); releaseNudge()
                val recognized = if (multi) multiGesture?.asHomeGesture() else {
                    val dx = event.x - startX; val dy = event.y - startY
                    when {
                        abs(dy) >= threshold && abs(dy) > abs(dx) * 1.2f ->
                            if (secondTap && !verticalOnly) { if (dy < 0) HomeGesture.DOUBLE_TAP_SWIPE_UP else HomeGesture.DOUBLE_TAP_SWIPE_DOWN }
                            else if (dy < 0) HomeGesture.SWIPE_UP else HomeGesture.SWIPE_DOWN
                        !verticalOnly && abs(dx) >= threshold && abs(dx) > abs(dy) * 1.2f -> if (dx < 0) HomeGesture.SWIPE_LEFT else HomeGesture.SWIPE_RIGHT
                        !verticalOnly && tap.onUp(event.eventTime) -> HomeGesture.DOUBLE_TAP
                        else -> null
                    }
                }
                if (recognized != null) { onGesture(recognized); performClick() }
            }
            MotionEvent.ACTION_CANCEL -> { tracking = false; multiGesture = null; tap.cancel(); parent?.requestDisallowInterceptTouchEvent(false); releaseNudge() }
        }
        return true
    }

    private fun releaseNudge() {
        if (nudge == 0f) return
        nudgeBack?.cancel()
        nudgeBack = ValueAnimator.ofFloat(nudge, 0f).setDuration(160L).apply {
            interpolator = DecelerateInterpolator()
            addUpdateListener { nudge = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { nudgeBack?.cancel(); super.onDetachedFromWindow() }

    override fun performClick(): Boolean { super.performClick(); return true }
    private fun span(e: MotionEvent) = hypot(e.getX(1) - e.getX(0), e.getY(1) - e.getY(0))
    private fun midpointX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midpointY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f
    private fun angle(e: MotionEvent) = Math.toDegrees(atan2((e.getY(1) - e.getY(0)).toDouble(), (e.getX(1) - e.getX(0)).toDouble())).toFloat()
}

private fun MultiTouchGesture.asHomeGesture(): HomeGesture = when (this) {
    MultiTouchGesture.PINCH_IN -> HomeGesture.PINCH_IN
    MultiTouchGesture.PINCH_OUT -> HomeGesture.PINCH_OUT
    MultiTouchGesture.TWO_FINGER_SWIPE_UP -> HomeGesture.TWO_FINGER_SWIPE_UP
    MultiTouchGesture.TWO_FINGER_SWIPE_DOWN -> HomeGesture.TWO_FINGER_SWIPE_DOWN
    MultiTouchGesture.ROTATE_CLOCKWISE -> HomeGesture.ROTATE_CLOCKWISE
    MultiTouchGesture.ROTATE_COUNTERCLOCKWISE -> HomeGesture.ROTATE_COUNTERCLOCKWISE
}
