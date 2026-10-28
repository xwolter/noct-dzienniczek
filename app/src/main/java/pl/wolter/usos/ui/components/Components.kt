package pl.wolter.usos.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.wolter.usos.ui.theme.P
import pl.wolter.usos.ui.theme.T

/** Sprężyste wciśnięcie (One UI): lekkie pomniejszenie + przyciemnienie. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    scaleTo: Float = .965f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        if (pressed) scaleTo else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = src, indication = null,
            onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
}

/** Wejście elementu: wysunięcie od dołu + pojawienie z opóźnieniem zależnym od pozycji (stagger). */
fun Modifier.appear(index: Int, key: Any? = Unit): Modifier = composed {
    var played by rememberSaveable(key) { mutableStateOf(false) }
    val a = remember(key) { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(key) {
        if (!played) {
            delay(30L * index.coerceAtMost(12))
            a.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            played = true
        }
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 28.dp.toPx()
        val s = .97f + .03f * a.value
        scaleX = s; scaleY = s
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    radius: Dp = 26.dp,
    color: Color = P.c.card,
    padding: PaddingValues = PaddingValues(vertical = 6.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(color)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = T.t13b, color = P.c.sub, modifier = modifier.padding(start = 10.dp, top = 6.dp))
}

/** Pasek postępu, który „dojeżdża” do wartości po wejściu na ekran. */
@Composable
fun AnimatedBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = P.c.line,
    height: Dp = 5.dp,
    delayMs: Int = 120,
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(delayMs.toLong()); started = true }
    val f by animateFloatAsState(
        if (started) fraction.coerceIn(0f, 1f) else 0f,
        spring(dampingRatio = .8f, stiffness = 60f), label = "bar",
    )
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** Liczba, która odlicza się od zera do wartości. */
@Composable
fun CountText(value: Double?, format: (Double) -> String, style: TextStyle, color: Color, modifier: Modifier = Modifier, placeholder: String = "-") {
    if (value == null) { Text(placeholder, style = style, color = color, modifier = modifier); return }
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text(format(a.value.toDouble()), style = style, color = color, modifier = modifier)
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, style: TextStyle = T.t11) {
    Text(
        text, style = style, color = fg, maxLines = 1,
        modifier = modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun Avatar(initials: String, size: Dp = 42.dp, style: TextStyle = T.t14b) {
    Box(Modifier.size(size).clip(CircleShape).background(P.c.blueTint), contentAlignment = Alignment.Center) {
        Text(initials, style = style, color = P.c.blue)
    }
}

@Composable
fun GradeBadge(value: String, minWidth: Dp = 48.dp, height: Dp = 38.dp, style: TextStyle = T.t16.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold)) {
    val (bg, fg) = P.c.grade(value)
    val bgA by animateColorAsState(bg, label = "gbg")
    val fgA by animateColorAsState(fg, label = "gfg")
    Box(
        Modifier.defaultMinSize(minWidth, height).height(height).clip(RoundedCornerShape(14.dp)).background(bgA).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(value, style = style, color = fgA, textAlign = TextAlign.Center) }
}

/** Segmentowany przełącznik z płynnie przesuwającą się „pigułką”. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(P.c.card).padding(5.dp),
    ) {
        val w = (maxWidth - 4.dp * (options.size - 1)) / options.size
        val x by animateDpAsState(
            (w + 4.dp) * selected,
            spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow), label = "seg",
        )
        Box(Modifier.offset(x = x).width(w).height(38.dp).clip(RoundedCornerShape(18.dp)).background(P.c.primaryBtn))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, o ->
                val fg by animateColorAsState(if (i == selected) P.c.primaryBtnFg else P.c.text2, label = "segfg")
                Box(
                    Modifier.width(w).height(38.dp).clip(RoundedCornerShape(18.dp))
                        .press(.94f) { if (i != selected) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(i) } },
                    contentAlignment = Alignment.Center,
                ) { Text(o, style = T.t13b, color = fg, maxLines = 1) }
            }
        }
    }
}

/** Przełącznik w stylu One UI z animacją kciuka. */
@Composable
fun OneSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 18.dp else 0.dp, spring(dampingRatio = .6f, stiffness = 500f), label = "sw")
    val bg by animateColorAsState(if (on) P.c.blue else P.c.toggleOff, label = "swbg")
    Box(Modifier.width(46.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(3.dp)) {
        Box(Modifier.offset(x = x).size(22.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun Chevron() { Text("›", style = T.h20.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color = P.c.faint) }

@Composable
fun Row2(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth()
            .let { if (onClick != null) it.press(.98f, onLongClick, onClick) else it }
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, dark: Boolean = true, light: Color = P.c.card, onClick: () -> Unit) {
    Box(
        modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (dark) P.c.primaryBtn else light)
            .press(.96f, onClick = onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.t14b, color = if (dark) P.c.primaryBtnFg else P.c.ink, maxLines = 1) }
}

@Composable
fun InfoTile(label: String, value: String, modifier: Modifier = Modifier, bg: Color = P.c.card) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(label, style = T.t11, color = P.c.muted)
        Text(value.ifBlank { "-" }, style = T.t15.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = P.c.ink)
    }
}
