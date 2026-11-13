package dev.nivelij.tolk.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
