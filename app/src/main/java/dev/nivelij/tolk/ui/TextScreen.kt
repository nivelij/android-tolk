package dev.nivelij.tolk.ui

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.nivelij.tolk.Status
import dev.nivelij.tolk.TextState
import dev.nivelij.tolk.data.Language
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.Prefs
import dev.nivelij.tolk.data.Tone
import kotlinx.coroutines.delay

@Composable
fun Header(onSettings: () -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText("Tolk", style = Tolk.type.wordmark.copy(color = Tolk.colors.ink), modifier = Modifier.weight(1f))
        RoundButton(Icons.Settings, "Settings", onSettings, background = Color.Transparent)
    }
}

@Composable
fun LanguageChip(language: Language, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Tolk.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.surface)
            .pressable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LanguageDot(language.code)
        BasicText(language.name, style = Tolk.type.label.copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun LanguageBar(source: Language, target: Language, onSource: () -> Unit, onTarget: () -> Unit, onSwap: () -> Unit, canSwap: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LanguageChip(source, onSource, Modifier.weight(1f))
        RoundButton(Icons.Swap, "Swap languages", onSwap, enabled = canSwap, background = Color.Transparent)
        LanguageChip(target, onTarget, Modifier.weight(1f))
    }
}

@Composable
fun TextScreen(
    prefs: Prefs,
    state: TextState,
    onInput: (String) -> Unit,
    onContext: (String) -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    onExplain: () -> Unit,
    onTone: (Tone) -> Unit,
    onSwap: () -> Unit,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Tolk.colors
    val source = Languages.byCode(prefs.source)
    val target = Languages.byCode(prefs.target)
    var showContext by rememberSaveable { mutableStateOf(state.context.isNotEmpty()) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val translate = {
        keyboard?.hide()
        focus.clearFocus()
        onTranslate()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Header(onSettings)
        LanguageBar(source, target, onPickSource, onPickTarget, onSwap, canSwap = !source.isAuto || state.detected != null)

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 104.dp, max = 230.dp)
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp),
        ) {
            BasicTextField(
                value = state.input,
                onValueChange = onInput,
                textStyle = Tolk.type.input.copy(color = c.ink),
                cursorBrush = SolidColor(languageColor(target.code, c.dark)),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(end = 36.dp),
                decorationBox = { inner ->
                    if (state.input.isEmpty()) BasicText("Type or paste text", style = Tolk.type.input.copy(color = c.muted))
                    inner()
                },
            )
            if (state.input.isNotEmpty()) {
                RoundButton(Icons.Close, "Clear", onClear, Modifier.align(Alignment.TopEnd), size = 34.dp, background = c.surface, tint = c.muted)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = "Situation",
                icon = if (state.context.isBlank()) Icons.Plus else Icons.Check,
                onClick = { showContext = !showContext },
                background = if (state.context.isBlank()) Color.Transparent else c.surface,
                outline = if (state.context.isBlank()) c.line else null,
                content = if (state.context.isBlank()) c.muted else c.ink,
            )
            Pill(
                text = prefs.tone.label,
                onClick = { onTone(Tone.entries[(prefs.tone.ordinal + 1) % Tone.entries.size]) },
                background = Color.Transparent,
                outline = c.line,
                content = if (prefs.tone == Tone.Auto) c.muted else c.ink,
            )
            Box(Modifier.weight(1f))
            Pill(
                text = "Translate",
                onClick = translate,
                enabled = state.input.isNotBlank() && state.status != Status.Working,
                background = c.ink,
                content = c.background,
            )
        }

        AnimatedVisibility(showContext) {
            BasicTextField(
                value = state.context,
                onValueChange = onContext,
                singleLine = true,
                textStyle = Tolk.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (state.context.isEmpty()) BasicText("Where is this from? e.g. a pharmacy, a rental contract", style = Tolk.type.body.copy(color = c.muted))
                    inner()
                },
            )
        }

        TranslationSheet(target.code, Modifier.weight(1f).padding(top = 18.dp)) {
            TextResult(prefs, state, target, translate, onExplain, onSettings)
        }
    }
}

@Composable
fun TranslationSheet(code: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Tolk.colors
    val color by animateColorAsState(languageColor(code, c.dark), tween(450), label = "sheet")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(color)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp)
            .navigationBarsPadding()
            .padding(bottom = 104.dp),
        content = content,
    )
}

@Composable
private fun TextResult(prefs: Prefs, state: TextState, target: Language, onRetry: () -> Unit, onExplain: () -> Unit, onSettings: () -> Unit) {
    val c = Tolk.colors
    val label = when {
        state.detected != null -> "From ${Languages.byCode(state.detected).name}"
        else -> target.name
    }
    BasicText(label, style = Tolk.type.small.copy(color = c.onSheetMuted))
    Gap(10.dp)

    when {
        state.output.isNotEmpty() -> {
            SelectionContainer {
                BasicText(state.output, style = translationStyle(state.output.length).copy(color = c.onSheet))
            }
            if (state.status == Status.Idle) {
                Gap(22.dp)
                ResultActions(state.output, onExplain, explaining = state.notesStatus == Status.Working)
            }
            Notes(state)
        }
        prefs.apiKey == null -> {
            BasicText("Add your OpenRouter key to start translating.", style = translationStyle(0).copy(color = c.onSheet))
            Gap(20.dp)
            Pill("Open settings", onSettings, background = c.onSheet, content = Color(0xFF1C2333))
        }
        state.status == Status.Working -> Dots(c.onSheet)
        state.status is Status.Failed -> Failure(state.status.message, onRetry)
        else -> BasicText("The translation appears here.", style = translationStyle(0).copy(color = c.onSheetMuted))
    }
}

@Composable
private fun ResultActions(output: String, onExplain: () -> Unit, explaining: Boolean) {
    val c = Tolk.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    val chip = Color.White.copy(alpha = 0.14f)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (copied) "Copied" else "Copy", { clipboard.setText(AnnotatedString(output)); copied = true }, icon = if (copied) Icons.Check else Icons.Copy, background = chip, content = c.onSheet)
        Pill("Explain", onExplain, icon = Icons.Explain, background = chip, content = c.onSheet, enabled = !explaining)
        RoundButton(Icons.Share, "Share", {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, output), null))
        }, background = chip, tint = c.onSheet)
    }
}

@Composable
private fun Notes(state: TextState) {
    val c = Tolk.colors
    if (state.notes.isEmpty() && state.notesStatus != Status.Working && state.notesStatus !is Status.Failed) return
    Gap(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = 0.16f))
            .padding(18.dp),
    ) {
        when {
            state.notes.isNotEmpty() -> SelectionContainer {
                BasicText(withBold(state.notes.trim()), style = Tolk.type.body.copy(color = c.onSheet))
            }
            state.notesStatus is Status.Failed -> BasicText(state.notesStatus.message, style = Tolk.type.body.copy(color = c.onSheet))
            else -> Dots(c.onSheet)
        }
    }
}

@Composable
fun Failure(message: String, onRetry: () -> Unit) {
    val c = Tolk.colors
    BasicText(message, style = Tolk.type.title.copy(color = c.onSheet))
    Gap(16.dp)
    Pill("Try again", onRetry, icon = Icons.Retry, background = Color.White.copy(alpha = 0.14f), content = c.onSheet)
}

private val boldRun = Regex("""\*\*(.+?)\*\*""")

private fun withBold(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    boldRun.findAll(text).forEach { m ->
        append(text.substring(last, m.range.first).replace("*", ""))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[1]) }
        last = m.range.last + 1
    }
    append(text.substring(last).replace("*", ""))
}
