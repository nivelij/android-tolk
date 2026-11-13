package dev.nivelij.tolk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Glyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp, contentDescription: String? = null) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}

fun Modifier.pressable(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 900f), label = "press")
    Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.4f }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Tolk.colors.surface,
    tint: Color = Tolk.colors.ink,
    size: Dp = 44.dp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .pressable(enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Glyph(icon, tint, size = size * 0.48f, contentDescription = contentDescription)
    }
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = Tolk.colors.surface,
    content: Color = Tolk.colors.ink,
    outline: Color? = null,
    enabled: Boolean = true,
    style: TextStyle = Tolk.type.label,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .then(if (outline != null) Modifier.border(1.dp, outline, CircleShape) else Modifier)
            .pressable(enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Glyph(icon, content, size = 18.dp)
        BasicText(text, style = style.copy(color = content), maxLines = 1)
    }
}

@Composable
fun Switch(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = Tolk.colors
    val offset by animateDpAsState(if (checked) 20.dp else 0.dp, spring(stiffness = 700f), label = "knob")
    Box(
        modifier
            .width(48.dp)
            .height(28.dp)
            .clip(CircleShape)
            .background(if (checked) c.ink else c.line)
            .pressable(role = Role.Switch) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) c.background else c.surface),
        )
    }
}

@Composable
fun BottomSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Tolk.colors
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (c.dark) 0.55f else 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 500f)) { it },
            exit = slideOutVertically(tween(220)) { it },
        ) {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(c.background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.line))
                }
                content()
            }
        }
    }
}

@Composable
fun FullPage(visible: Boolean, onDismiss: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(
        visible,
        enter = slideInVertically(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 6 } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200)) { it / 6 } + fadeOut(tween(160)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Tolk.colors.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            content = content,
        )
    }
}

@Composable
fun LanguageDot(code: String, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    val c = Tolk.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (code == dev.nivelij.tolk.data.AUTO_CODE) c.muted else languageColor(code, c.dark)),
    )
}

@Composable
fun Gap(size: Dp) = Spacer(Modifier.size(size))

@Composable
fun Dots(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(520, delayMillis = i * 160), RepeatMode.Reverse),
                label = "dot$i",
            )
            Box(Modifier.size(10.dp).graphicsLayer { this.alpha = alpha }.clip(CircleShape).background(color))
        }
    }
}
