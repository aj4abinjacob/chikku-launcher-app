package io.github.aj4abinjacob.chikku.settings.cards

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import io.github.aj4abinjacob.chikku.home.Haptics
import io.github.aj4abinjacob.chikku.settings.components.PlacementGate
import io.github.aj4abinjacob.chikku.settings.components.animatePlacement
import io.github.aj4abinjacob.chikku.theme.LocalMotion
import io.github.aj4abinjacob.chikku.theme.tweenSpec
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.aj4abinjacob.chikku.R
import io.github.aj4abinjacob.chikku.core.model.GestureAction
import io.github.aj4abinjacob.chikku.core.model.cards.AutomationCard
import io.github.aj4abinjacob.chikku.core.model.cards.CardFeatured
import io.github.aj4abinjacob.chikku.home.cards.cardConditionSummary
import io.github.aj4abinjacob.chikku.home.cards.cardContentGlyph

private val ItemGap = 8.dp
private val ItemShape = RoundedCornerShape(24.dp)

/**
 * The page's scroll state and viewport, so a card dragged near the viewport edge can scroll the page.
 * [viewport] is the scrolling container's own coordinates (set with `onGloballyPositioned` before
 * `verticalScroll`).
 */
internal class ListAutoScroll(val state: ScrollState) {
    var viewport: LayoutCoordinates? = null
}

/**
 * A column whose items can be dragged by a handle to a new position. [content] receives the
 * handle modifier to attach; [onMove] reports the drop position once, after the dragged card has
 * glided into its slot.
 *
 * The card lifts on pickup, the others slide aside to show where it lands, and it settles into
 * place before the order is stored; the list order change and the removal of every preview
 * translation happen in the same frame, so nothing jumps. Items that move for any other reason
 * (add, delete, undo, a menu move) glide to their new place. Haptics mark pickup, each slot
 * crossed and the drop, and holding a card near the viewport edge scrolls the page ([autoScroll]).
 */
@Composable
internal fun <T> CardReorderList(
    cards: List<T>,
    enabled: Boolean,
    key: (T) -> Any,
    onMove: (T, Int) -> Unit,
    autoScroll: ListAutoScroll? = null,
    content: @Composable (index: Int, item: T, handle: Modifier) -> Unit,
) {
    val motion = LocalMotion.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var lift by remember { mutableFloatStateOf(0f) }
    var settleTarget by remember { mutableStateOf<Int?>(null) }
    val gate = remember { PlacementGate() }
    var listReady by remember { mutableStateOf(false) }
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    LaunchedEffect(Unit) { listReady = true }
    val gap = with(LocalDensity.current) { ItemGap.toPx() }
    val edgeZone = with(LocalDensity.current) { EdgeScrollZone.toPx() }
    val maxScrollSpeed = with(LocalDensity.current) { EdgeScrollSpeed.toPx() }
    val currentItems by rememberUpdatedState(cards)
    val currentMove by rememberUpdatedState(onMove)
    val currentMotion by rememberUpdatedState(motion)

    fun targetIndex(items: List<T>, from: Int, by: Float): Int =
        reorderTargetIndex(items.size, from, by, gap) { heights[key(items[it])] ?: 0 }

    fun slotOffset(items: List<T>, from: Int, to: Int): Float =
        reorderSlotOffset(from, to, gap) { heights[key(items[it])] ?: 0 }

    val from = cards.indexOfFirst { key(it) == dragging }
    val to = if (from < 0) -1 else settleTarget ?: targetIndex(cards, from, offset)
    val draggedHeight = if (from >= 0) (heights[dragging] ?: 0) + gap else 0f

    // A felt tick for each slot the card passes while it is held.
    var lastSlot by remember { mutableIntStateOf(-1) }
    LaunchedEffect(dragging, to) {
        if (dragging == null || settleTarget != null) { lastSlot = -1; return@LaunchedEffect }
        if (lastSlot >= 0 && lastSlot != to) Haptics.tick(view)
        lastSlot = to
    }

    fun finishDrag(id: Any, commit: Boolean) {
        if (settleTarget != null) return
        val items = currentItems
        val start = items.indexOfFirst { key(it) == id }
        if (start < 0) {
            dragging = null; offset = 0f; lift = 0f
            return
        }
        val drop = if (commit) targetIndex(items, start, offset) else start
        settleTarget = drop
        val item = items[start]
        val finalOffset = slotOffset(items, start, drop)
        scope.launch {
            try {
                val spec = currentMotion.tweenSpec<Float>(SETTLE_MS)
                coroutineScope {
                    launch { animate(offset, finalOffset, animationSpec = spec) { value, _ -> offset = value } }
                    launch { animate(lift, 0f, animationSpec = spec) { value, _ -> lift = value } }
                }
            } finally {
                // Order change and translation reset land together, so the card stays where it is.
                if (drop != start) {
                    gate.suppress = true
                    Haptics.drop(view)
                    currentMove(item, drop)
                } else if (commit) Haptics.drop(view)
                dragging = null
                offset = 0f
                lift = 0f
                settleTarget = null
            }
            if (gate.suppress) {
                withFrameNanos { }
                withFrameNanos { }
                gate.suppress = false
            }
        }
    }

    // The page follows a card held near its edge; the card is kept under the finger.
    LaunchedEffect(dragging, settleTarget != null) {
        val id = dragging
        val scroll = autoScroll
        if (id == null || scroll == null || settleTarget != null) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = (now - last) / 1_000_000_000f
            last = now
            val viewport = scroll.viewport
            val list = listCoordinates
            val items = currentItems
            val index = items.indexOfFirst { key(it) == id }
            if (viewport == null || list == null || !viewport.isAttached || !list.isAttached || index < 0) continue
            var before = 0f
            for (i in 0 until index) before += (heights[key(items[i])] ?: 0) + gap
            val top = list.positionInRoot().y + before + offset
            val bottom = top + (heights[id] ?: 0)
            val viewTop = viewport.positionInRoot().y
            val viewBottom = viewTop + viewport.size.height
            val speed = edgeScrollFraction(top, bottom, viewTop, viewBottom, edgeZone)
            if (speed != 0f) offset += scroll.state.scrollBy(speed * maxScrollSpeed * dt)
        }
    }
    // Saving disables the handles mid-drag: put the card back rather than leaving it held.
    LaunchedEffect(enabled) { if (!enabled) dragging?.let { finishDrag(it, commit = false) } }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).onGloballyPositioned { listCoordinates = it },
        verticalArrangement = Arrangement.spacedBy(ItemGap),
    ) {
        cards.forEachIndexed { index, item ->
            val id = key(item)
            key(id) {
                val isDragged = id == dragging
                val shift = reorderShift(index, from, to, isDragged, draggedHeight)
                val animatedShift by animateFloatAsState(
                    shift,
                    if (dragging == null) snap() else motion.tweenSpec(SHIFT_MS),
                    label = "cardShift",
                )
                val handle = Modifier.pointerInput(id, enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (dragging != null) return@detectVerticalDragGestures
                            dragging = id
                            offset = 0f
                            Haptics.gesture(view)
                            scope.launch { animate(lift, 1f, animationSpec = currentMotion.tweenSpec(LIFT_MS)) { v, _ -> lift = v } }
                        },
                        onDragEnd = { if (dragging == id) finishDrag(id, commit = true) },
                        onDragCancel = { if (dragging == id) finishDrag(id, commit = false) },
                    ) { change, amount ->
                        change.consume()
                        if (dragging == id && settleTarget == null) offset += amount
                    }
                }
                // New cards fade in; the first composition of the list does not animate.
                val appear = remember { Animatable(if (listReady && motion.enabled) 0f else 1f) }
                LaunchedEffect(Unit) { if (appear.value < 1f) appear.animateTo(1f, motion.tweenSpec(APPEAR_MS)) }
                Box(
                    Modifier
                        .animatePlacement(motion, gate)
                        .onSizeChanged { heights[id] = it.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                isDragged -> offset
                                dragging == null -> 0f
                                else -> animatedShift
                            }
                            alpha = appear.value
                            if (isDragged) {
                                shadowElevation = 8.dp.toPx() * lift
                                shape = ItemShape
                                scaleX = 1f + 0.02f * lift
                                scaleY = 1f + 0.02f * lift
                            }
                        },
                ) { content(index, item, handle) }
            }
        }
    }
}

/**
 * The slot an item dragged from [from] by [by] px would land in. [heightOf] gives the measured
 * height of the item at an index (0 if not measured yet); every neighbour also costs [gap].
 * Crossing a neighbour needs more than half of its slot.
 */
internal fun reorderTargetIndex(count: Int, from: Int, by: Float, gap: Float, heightOf: (Int) -> Int): Int {
    var target = from
    var remaining = by
    while (remaining > 0 && target < count - 1) {
        val next = heightOf(target + 1) + gap
        if (remaining <= next / 2) break
        remaining -= next
        target++
    }
    while (remaining < 0 && target > 0) {
        val previous = heightOf(target - 1) + gap
        if (-remaining <= previous / 2) break
        remaining += previous
        target--
    }
    return target
}

/** Distance from the item at [from] to the top of the slot [to] (negative when moving up). */
internal fun reorderSlotOffset(from: Int, to: Int, gap: Float, heightOf: (Int) -> Int): Float {
    var sum = 0f
    if (to > from) for (i in from + 1..to) sum += heightOf(i) + gap
    else for (i in to until from) sum -= heightOf(i) + gap
    return sum
}

/** How far a non-dragged item slides aside to open the slot [to] for the item lifted from [from]. */
internal fun reorderShift(index: Int, from: Int, to: Int, isDragged: Boolean, draggedHeight: Float): Float = when {
    from < 0 || isDragged -> 0f
    index in (from + 1)..to -> -draggedHeight
    index in to until from -> draggedHeight
    else -> 0f
}

/**
 * Edge auto-scroll: 0 when the held card is clear of both edge zones, up to +1 (down) or -1 (up)
 * the deeper it sits in the bottom or top zone. The bottom zone wins when both apply.
 */
internal fun edgeScrollFraction(top: Float, bottom: Float, viewTop: Float, viewBottom: Float, edgeZone: Float): Float = when {
    bottom > viewBottom - edgeZone -> ((bottom - (viewBottom - edgeZone)) / edgeZone).coerceIn(0f, 1f)
    top < viewTop + edgeZone -> -((viewTop + edgeZone - top) / edgeZone).coerceIn(0f, 1f)
    else -> 0f
}

private const val LIFT_MS = 120
private const val SHIFT_MS = 200
private const val SETTLE_MS = 180
private const val APPEAR_MS = 220
private val EdgeScrollZone = 88.dp
private val EdgeScrollSpeed = 640.dp

/** One card in the list: drag handle, icon, name, when it shows, its actions, on/off switch and a menu. */
@Composable
internal fun CardListRow(
    card: AutomationCard,
    title: String,
    canEdit: Boolean,
    enabled: Boolean,
    // The drag handle's modifier from the reorder list, not the row's own `modifier`.
    @Suppress("ModifierParameter") handle: Modifier,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val actionCount = card.actions.count { it != GestureAction.None }
    val subtitle = if (!canEdit) stringResource(R.string.cards_unavailable_summary) else listOfNotNull(
        stringResource(R.string.cards_list_off).takeIf { !card.enabled },
        cardConditionSummary(context, card.conditions) ?: stringResource(R.string.cards_when_always),
        pluralStringResource(R.plurals.cards_list_actions, actionCount, actionCount).takeIf { actionCount > 0 },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().clip(ItemShape).background(colors.surfaceContainer)
            .clickable(enabled = enabled && canEdit, role = Role.Button, onClick = onOpen)
            .heightIn(min = 76.dp).padding(end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val reorder = stringResource(R.string.cards_list_reorder, title)
        Box(handle.size(40.dp, 56.dp).semantics { contentDescription = reorder }, contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_action_drag), null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        val featured = card.featured
        Box(Modifier.alpha(if (card.enabled) 1f else 0.5f)) {
            when {
                canEdit && featured is CardFeatured.App -> CardActionIcon(GestureAction.LaunchApp(featured.key), 44.dp)
                canEdit && featured is CardFeatured.Shortcut ->
                    CardActionIcon(GestureAction.LaunchShortcut(featured.packageName, featured.shortcutId, featured.userSerial), 44.dp)
                else -> Box(Modifier.size(44.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (canEdit) cardContentGlyph(card.content) else R.drawable.ic_app_unavailable), null,
                        tint = colors.onSecondaryContainer, modifier = Modifier.size(22.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (card.enabled || !canEdit) 1f else 0.6f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val toggle = stringResource(R.string.cards_list_enable, title)
        Switch(
            checked = card.enabled,
            onCheckedChange = onToggle,
            enabled = enabled && canEdit,
            modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = toggle },
            thumbContent = if (card.enabled) {
                { Icon(painterResource(R.drawable.ic_settings_check), null, Modifier.size(SwitchDefaults.IconSize), tint = colors.primary) }
            } else null,
        )
        Box {
            IconButton(onClick = { menu = true }, enabled = enabled) {
                Icon(painterResource(R.drawable.ic_menu_more), stringResource(R.string.cards_list_more, title))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (canMoveUp) DropdownMenuItem(text = { Text(stringResource(R.string.cards_move_up_short)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_swipe_up), null) },
                    onClick = { menu = false; onMove(-1) })
                if (canMoveDown) DropdownMenuItem(text = { Text(stringResource(R.string.cards_move_down_short)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_swipe_down), null) },
                    onClick = { menu = false; onMove(1) })
                DropdownMenuItem(text = { Text(stringResource(R.string.cards_delete_short), color = colors.error) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_menu_remove), null, tint = colors.error) },
                    onClick = { menu = false; onDelete() })
            }
        }
    }
}

/** Shown behind a card while it is swiped away. */
@Composable
internal fun DeleteBackground() {
    Box(
        Modifier.fillMaxSize().clip(ItemShape).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(painterResource(R.drawable.ic_menu_remove), null, tint = MaterialTheme.colorScheme.onErrorContainer)
    }
}
