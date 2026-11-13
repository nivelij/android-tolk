package dev.nivelij.tolk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.nivelij.tolk.PhotoState
import dev.nivelij.tolk.Status
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.PhotoItem
import dev.nivelij.tolk.data.Prefs

@Composable
fun PhotoScreen(
    prefs: Prefs,
    state: PhotoState,
    onSnap: () -> Unit,
    onGallery: () -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onPickTarget: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Tolk.colors
    val target = Languages.byCode(prefs.target)
    Column(Modifier.fillMaxSize()) {
        Header(onSettings)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText("Translate photos into", style = Tolk.type.label.copy(color = c.muted), modifier = Modifier.padding(start = 8.dp))
            LanguageChip(target, onPickTarget, Modifier.weight(1f, fill = false))
        }

        val bitmap = state.bitmap
        if (bitmap == null) {
            Viewfinder(onSnap, onGallery, state, Modifier.weight(1f))
        } else {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(26.dp)),
            ) {
                Image(image, contentDescription = "Your photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Row(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val glass = Color.Black.copy(alpha = 0.45f)
                    RoundButton(Icons.Camera, "Take another photo", onSnap, background = glass, tint = Color.White, size = 40.dp)
                    RoundButton(Icons.Close, "Close photo", onClear, background = glass, tint = Color.White, size = 40.dp)
                }
            }
            TranslationSheet(target.code, Modifier.weight(1f).padding(top = 16.dp)) {
                PhotoResultList(state, onRetry)
            }
        }
    }
}

@Composable
private fun Viewfinder(onSnap: () -> Unit, onGallery: () -> Unit, state: PhotoState, modifier: Modifier) {
    val c = Tolk.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .navigationBarsPadding()
            .padding(bottom = 96.dp),
    ) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val arm = 34.dp.toPx()
            val w = 3.dp.toPx()
            val color = c.line
            listOf(
                Offset(0f, 0f) to Offset(1f, 1f),
                Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f),
                Offset(size.width, size.height) to Offset(-1f, -1f),
            ).forEach { (corner, dir) ->
                drawLine(color, corner, corner + Offset(arm * dir.x, 0f), w, StrokeCap.Round)
                drawLine(color, corner, corner + Offset(0f, arm * dir.y), w, StrokeCap.Round)
            }
        }
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(c.ink)
                    .pressable(onClick = onSnap),
                contentAlignment = Alignment.Center,
            ) {
                Glyph(Icons.Camera, c.background, size = 36.dp, contentDescription = "Take a photo")
            }
            Gap(20.dp)
            BasicText("Menus, signs, letters, labels", style = Tolk.type.title.copy(color = c.ink))
            Gap(6.dp)
            BasicText("Snap it and read it in your language.", style = Tolk.type.body.copy(color = c.muted))
            Gap(22.dp)
            Pill("Choose from gallery", onGallery, icon = Icons.Image, background = c.surface)
            if (state.status is Status.Failed) {
                Gap(16.dp)
                BasicText(state.status.message, style = Tolk.type.body.copy(color = c.danger))
            }
        }
    }
}

@Composable
fun PhotoResultList(state: PhotoState, onRetry: () -> Unit) {
    val c = Tolk.colors
    val summary = state.result.summary
    val from = summary?.lang?.let { Languages.byCode(it).name }
    BasicText(
        listOfNotNull(from?.let { "From $it" }, summary?.kind?.replaceFirstChar { it.uppercase() }).joinToString(", ").ifEmpty { if (state.status == Status.Working) "Reading the photo" else "" },
        style = Tolk.type.small.copy(color = c.onSheetMuted),
    )
    summary?.summary?.let {
        Gap(8.dp)
        BasicText(it, style = Tolk.type.title.copy(color = c.onSheet))
    }
    Gap(14.dp)
    SelectionContainer {
        Column {
            state.result.items.forEachIndexed { i, item ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.14f)))
                PhotoRow(item)
            }
        }
    }
    when (val status = state.status) {
        Status.Working -> Dots(c.onSheet)
        is Status.Failed -> {
            Gap(12.dp)
            Failure(status.message, onRetry)
        }
        Status.Idle -> Unit
    }
}

@Composable
private fun PhotoRow(item: PhotoItem) {
    val c = Tolk.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        BasicText(item.translation, style = Tolk.type.title.copy(color = c.onSheet, fontWeight = FontWeight.Medium))
        if (item.original.isNotBlank() && item.original != item.translation) {
            Gap(3.dp)
            BasicText(item.original, style = Tolk.type.small.copy(color = c.onSheetMuted))
        }
        if (item.note.isNotBlank()) {
            Gap(8.dp)
            BasicText(item.note, style = Tolk.type.body.copy(color = c.onSheet.copy(alpha = 0.86f)))
        }
    }
}
