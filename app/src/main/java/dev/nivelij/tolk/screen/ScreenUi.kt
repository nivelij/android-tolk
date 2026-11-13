package dev.nivelij.tolk.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import dev.nivelij.tolk.ui.Atkinson
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.nivelij.tolk.PhotoState
import dev.nivelij.tolk.Status
import dev.nivelij.tolk.data.Language
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.PhotoResult
import dev.nivelij.tolk.ui.Dots
import dev.nivelij.tolk.ui.Failure
import dev.nivelij.tolk.ui.Gap
import dev.nivelij.tolk.ui.Glyph
import dev.nivelij.tolk.ui.Icons
import dev.nivelij.tolk.ui.Pill
import dev.nivelij.tolk.ui.PhotoResultList
import dev.nivelij.tolk.ui.RoundButton
import dev.nivelij.tolk.ui.Tolk
import dev.nivelij.tolk.ui.TolkTheme
import dev.nivelij.tolk.ui.languageColor
import dev.nivelij.tolk.ui.translationStyle
import kotlinx.coroutines.delay

enum class ScreenMode { Text, Photo }

data class ScreenState(
    val mode: ScreenMode = ScreenMode.Text,
    val blocks: List<ScreenBlock> = emptyList(),
    val items: Map<Int, String> = emptyMap(),
    val output: String = "",
    val detected: String? = null,
    val photo: PhotoResult = PhotoResult(null, emptyList()),
    val status: Status = Status.Idle,
)

@Composable
fun Bubble(
    onTap: () -> Unit,
    onGrab: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onRelease: () -> Unit,
) {
    TolkTheme {
        val c = Tolk.colors
        Box(
            Modifier
                .padding(8.dp)
                .size(54.dp)
                .shadow(10.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(c.ink)
                .pointerInput(Unit) { bubbleGestures(onTap, onGrab, onDrag, onRelease) },
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Icons.Bubbles, c.background, size = 26.dp, contentDescription = "Translate screen")
        }
    }
}

// One handler instead of separate tap and drag detectors, so a long hold, a drag, or both
// all count as "grabbed" and the dismiss target shows exactly once per touch.
private suspend fun PointerInputScope.bubbleGestures(
    onTap: () -> Unit,
    onGrab: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onRelease: () -> Unit,
) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    var grabbed = false
    var dragging = false
    var travelled = Offset.Zero
    while (true) {
        val event = if (grabbed) {
            awaitPointerEvent()
        } else {
            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
        }
        if (event == null) {
            grabbed = true
            onGrab()
            continue
        }
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        val delta = change.positionChange()
        travelled += delta
        if (!dragging && travelled.getDistance() > viewConfiguration.touchSlop) {
            dragging = true
            if (!grabbed) {
                grabbed = true
                onGrab()
            }
        }
        if (dragging) {
            change.consume()
            onDrag(delta.x, delta.y)
        }
    }
    if (grabbed) onRelease() else onTap()
}

@Composable
fun DismissTarget(near: Boolean) {
    TolkTheme {
        val c = Tolk.colors
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val size by animateDpAsState(if (near) 76.dp else 60.dp, spring(dampingRatio = 0.6f, stiffness = 600f), label = "target")
        Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(shown, enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f)) {
                Box(
                    Modifier
                        .size(size)
                        .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(CircleShape)
                        .background(if (near) c.danger else Color(0xE61C2333)),
                    contentAlignment = Alignment.Center,
                ) {
                    Glyph(Icons.Close, Color.White, size = 28.dp, contentDescription = "Drop here to close")
                }
            }
        }
    }
}

@Composable
fun ScreenPanel(state: ScreenState, target: Language, onClose: () -> Unit, onRetry: () -> Unit) {
    TolkTheme {
        val c = Tolk.colors
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
            AnimatedVisibility(
                shown,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 500f)) { it } + fadeIn(tween(150)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.78f)
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                        .background(languageColor(target.code, c.dark))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val from = state.detected?.let { "From ${Languages.byCode(it).name} into ${target.name}" } ?: "Into ${target.name}"
                        BasicText(from, style = Tolk.type.small.copy(color = c.onSheetMuted), modifier = Modifier.weight(1f))
                        RoundButton(Icons.Close, "Close", onClose, background = Color.White.copy(alpha = 0.14f), tint = c.onSheet, size = 40.dp)
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp),
                    ) {
                        when (state.mode) {
                            ScreenMode.Text -> TextBody(state, onRetry)
                            ScreenMode.Photo -> PhotoResultList(PhotoState(result = state.photo, status = state.status), onRetry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextBody(state: ScreenState, onRetry: () -> Unit) {
    val c = Tolk.colors
    when {
        state.output.isNotEmpty() -> {
            SelectionContainer {
                BasicText(state.output, style = translationStyle(maxOf(state.output.length, 400)).copy(color = c.onSheet))
            }
            if (state.status == Status.Working) Dots(c.onSheet)
            if (state.status is Status.Failed) {
                Gap(12.dp)
                Failure(state.status.message, onRetry)
            }
            if (state.status == Status.Idle) {
                Gap(18.dp)
                CopyPill(state.output)
            }
        }
        state.status is Status.Failed -> Failure(state.status.message, onRetry)
        else -> Dots(c.onSheet)
    }
}

@Composable
private fun CopyPill(text: String) {
    val c = Tolk.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(
            if (copied) "Copied" else "Copy",
            { clipboard.setText(AnnotatedString(text)); copied = true },
            icon = if (copied) Icons.Check else Icons.Copy,
            background = Color.White.copy(alpha = 0.14f),
            content = c.onSheet,
        )
    }
}

// Display-only: the window is not touchable, so taps and scrolls reach the page underneath.
@Composable
fun InPlaceOverlay(state: ScreenState, target: Language) {
    TolkTheme {
        val c = Tolk.colors
        val accent = languageColor(target.code, c.dark)
        val view = LocalView.current
        var origin by remember { mutableStateOf<IntOffset?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    val location = IntArray(2).also(view::getLocationOnScreen)
                    origin = IntOffset(location[0], location[1])
                },
        ) {
            val offset = origin ?: return@Box
            // Big blocks first so small ones (links, buttons) end up on top when boxes overlap.
            state.blocks.withIndex()
                .sortedByDescending { (_, b) -> b.width.toLong() * b.height }
                .forEach { (i, block) ->
                    val text = state.items[i] ?: return@forEach
                    if (text.isBlank() || text == block.text) return@forEach
                    FittedBlock(text, block, offset, accent)
                }
        }
    }
}

@Composable
private fun FittedBlock(text: String, block: ScreenBlock, origin: IntOffset, accent: Color) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val padding = with(density) { 4.dp.roundToPx() }
    // Shrink until the text fits the original box without splitting a word; if even the smallest size
    // can't fit the longest word, widen the box instead of breaking it mid-word.
    val (style, widthPx) = remember(text, block) {
        val base = TextStyle(fontFamily = Atkinson, color = Color(0xFF1C2333))
        val innerWidth = (block.width - padding * 2).coerceAtLeast(1)
        val innerHeight = block.height - padding * 2
        val longestWord = text.split(' ').maxByOrNull { it.length }.orEmpty()
        val minPx = 10f * density.density
        var sizePx = (block.height / 1.35f).coerceIn(minPx, 24f * density.density)
        var fitted: TextStyle
        var wordWidth: Int
        while (true) {
            fitted = with(density) { base.copy(fontSize = sizePx.toSp(), lineHeight = (sizePx * 1.22f).toSp()) }
            wordWidth = measurer.measure(longestWord, fitted, maxLines = 1).size.width
            val height = measurer.measure(text, fitted, constraints = Constraints(maxWidth = maxOf(innerWidth, wordWidth))).size.height
            if ((height <= innerHeight && wordWidth <= innerWidth) || sizePx <= minPx) break
            sizePx -= density.density
        }
        fitted to maxOf(block.width, wordWidth + padding * 2 + 1)
    }
    with(density) {
        Box(
            Modifier
                .offset { IntOffset(block.left - origin.x, block.top - origin.y) }
                .width(widthPx.toDp())
                .heightIn(min = block.height.toDp())
                .shadow(2.dp, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFFFBFBFD))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(5.dp))
                .padding(4.dp),
        ) {
            BasicText(text, style = style)
        }
    }
}

@Composable
fun InPlaceBar(state: ScreenState, target: Language, onList: () -> Unit, onClose: () -> Unit, onRetry: () -> Unit) = TolkTheme {
    val c = Tolk.colors
    Row(
        Modifier
            .padding(12.dp)
            .shadow(14.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(languageColor(target.code, c.dark))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label = when (val status = state.status) {
            Status.Working -> "Translating ${state.items.size} of ${state.blocks.size}"
            is Status.Failed -> status.message
            Status.Idle -> state.detected?.let { "From ${Languages.byCode(it).name}" } ?: "Into ${target.name}"
        }
        BasicText(label, style = Tolk.type.small.copy(color = c.onSheet), maxLines = 2, modifier = Modifier.weight(1f, fill = false))
        if (state.status is Status.Failed) {
            Pill("Retry", onRetry, background = Color.White.copy(alpha = 0.16f), content = c.onSheet, style = Tolk.type.small)
        } else {
            Pill("List", onList, background = Color.White.copy(alpha = 0.16f), content = c.onSheet, style = Tolk.type.small)
        }
        RoundButton(Icons.Close, "Close", onClose, background = Color.White.copy(alpha = 0.16f), tint = c.onSheet, size = 38.dp)
    }
}
