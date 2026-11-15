package io.github.aj4abinjacob.chikku.settings.cards

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aj4abinjacob.chikku.R
import kotlinx.coroutines.delay

/**
 * First-run page of Settings → Cards: what a card is, where it appears (an animated drawing of the
 * Cards sheet over Home) and how to open it. [CardsCreateButton] is pinned below it.
 */
@Composable
internal fun CardsOnboarding(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(32.dp))
                .background(colors.surfaceContainer).padding(vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) { PhoneIllustration(scale = 1f) }
        Text(stringResource(R.string.cards_intro_headline),
            style = MaterialTheme.typography.headlineSmall.copy(lineBreak = LineBreak.Heading),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, start = 8.dp, end = 8.dp).semantics { heading() })
        Text(stringResource(R.string.cards_intro_body), style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp))
        Steps(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer).padding(20.dp))
    }
}

/** The onboarding's one primary action, pinned to the bottom so it is visible without scrolling. */
@Composable
internal fun CardsCreateButton(onCreate: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Button(onClick = onCreate, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Icon(painterResource(R.drawable.ic_action_add), null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.cards_intro_create), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Once cards exist: a folded "What are cards?" row that opens the same drawing and steps. */
@Composable
internal fun CardsIntro(modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val state = stringResource(if (expanded) R.string.settings_show_less else R.string.settings_learn_more)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics { stateDescription = state }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_settings_info), null, tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.cards_intro_title), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() })
            Icon(painterResource(if (expanded) R.drawable.ic_settings_collapse else R.drawable.ic_settings_expand), null,
                tint = colors.onSurfaceVariant)
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) {
                Text(stringResource(R.string.cards_intro_body), style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant)
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhoneIllustration(scale = 0.85f)
                    Spacer(Modifier.width(20.dp))
                    Steps(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Steps(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Step(1, stringResource(R.string.cards_intro_step1))
        Step(2, stringResource(R.string.cards_intro_step2))
        Step(3, stringResource(R.string.cards_intro_step3))
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * A drawn Home screen (wallpaper, app grid, dock) that dims while the Cards sheet slides up from the
 * bottom. Never real apps or cards. Tap to replay; static when system animations are off.
 */
@Composable
private fun PhoneIllustration(scale: Float) {
    fun Dp.s() = this * scale
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val animate = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var replay by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(replay) {
        if (!animate) return@LaunchedEffect
        progress.snapTo(0f)
        delay(600)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val description = stringResource(R.string.cards_intro_illustration)
    val replayLabel = stringResource(R.string.cards_intro_replay)
    val height = 272.dp.s()
    val sheetHeight = height * 0.6f
    val travel = with(LocalDensity.current) { (sheetHeight + 12.dp).toPx() }
    val icon = colors.onPrimaryContainer.copy(alpha = 0.3f)

    Box(
        Modifier.size(136.dp.s(), height).clip(RoundedCornerShape(24.dp.s())).background(colors.outlineVariant)
            .padding(3.dp.s()).clip(RoundedCornerShape(21.dp.s()))
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClickLabel = replayLabel) { replay++ }
            .semantics { contentDescription = description },
    ) {
        Column(Modifier.fillMaxSize().padding(start = 14.dp.s(), end = 14.dp.s(), top = 24.dp.s(), bottom = 12.dp.s())) {
            repeat(4) {
                Row(Modifier.fillMaxWidth().padding(bottom = 11.dp.s()), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(4) { Box(Modifier.size(19.dp.s()).clip(RoundedCornerShape(6.dp.s())).background(icon)) }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp.s())).background(icon.copy(alpha = 0.15f))
                    .padding(5.dp.s()),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) { repeat(4) { Box(Modifier.size(17.dp.s()).clip(CircleShape).background(icon)) } }
        }
        Box(Modifier.fillMaxSize().alpha(progress.value).background(Color.Black.copy(alpha = 0.4f)))
        Column(
            Modifier.align(Alignment.BottomCenter).padding(5.dp.s()).fillMaxWidth().height(sheetHeight)
                .graphicsLayer { translationY = (1f - progress.value) * travel }
                .clip(RoundedCornerShape(18.dp.s())).background(colors.surface)
                .padding(6.dp.s()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(20.dp.s(), 3.dp.s()).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = 0.4f)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp.s(), vertical = 6.dp.s()),
                verticalAlignment = Alignment.CenterVertically) {
                Bar(30.dp.s(), 5.dp.s(), colors.onSurface.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(6.dp.s()).clip(CircleShape).background(colors.onSurfaceVariant.copy(alpha = 0.6f)))
            }
            MiniCard(featured = true, scale = scale)
            Spacer(Modifier.height(5.dp.s()))
            MiniCard(featured = false, scale = scale)
        }
    }
}

/** A card in miniature: name and condition lines, a featured pill and action dots. */
@Composable
private fun MiniCard(featured: Boolean, scale: Float) {
    fun Dp.s() = this * scale
    val colors = MaterialTheme.colorScheme
    val line = colors.onSurfaceVariant.copy(alpha = 0.45f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp.s())).background(colors.surfaceContainerHigh)
            .padding(7.dp.s()),
    ) {
        Bar(48.dp.s(), 5.dp.s(), colors.onSurface.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp.s()))
        Bar(32.dp.s(), 3.dp.s(), line)
        if (featured) {
            Row(
                Modifier.padding(top = 6.dp.s()).fillMaxWidth().clip(RoundedCornerShape(8.dp.s()))
                    .background(colors.surfaceContainerHighest).padding(4.dp.s()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(11.dp.s()).clip(RoundedCornerShape(3.dp.s())).background(colors.primary))
                Spacer(Modifier.width(5.dp.s()))
                Bar(36.dp.s(), 3.dp.s(), line)
            }
        }
        Row(Modifier.padding(top = 6.dp.s()).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            repeat(4) { index ->
                Box(Modifier.size(12.dp.s()).clip(CircleShape)
                    .background(if (index == 0) colors.primary else colors.secondaryContainer))
            }
        }
    }
}

@Composable
private fun Bar(width: Dp, height: Dp, color: Color) {
    Box(Modifier.size(width, height).clip(CircleShape).background(color))
}
