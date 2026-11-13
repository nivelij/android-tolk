package dev.nivelij.tolk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import dev.nivelij.tolk.ModelsState
import dev.nivelij.tolk.Status
import dev.nivelij.tolk.data.Language
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.net.Model
import java.util.Locale

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, placeholder: String) {
    val c = Tolk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(CircleShape)
            .background(c.surface)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Glyph(Icons.Search, c.muted, size = 20.dp)
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = Tolk.type.label.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) BasicText(placeholder, style = Tolk.type.label.copy(color = c.muted))
                inner()
            },
        )
        if (query.isNotEmpty()) RoundButton(Icons.Close, "Clear search", { onQuery("") }, size = 28.dp, background = Color.Transparent, tint = c.muted)
    }
}

@Composable
private fun SectionLabel(text: String) {
    BasicText(text, style = Tolk.type.small.copy(color = Tolk.colors.muted), modifier = Modifier.padding(start = 24.dp, top = 18.dp, bottom = 6.dp))
}

@Composable
fun LanguagePicker(
    visible: Boolean,
    title: String,
    selected: String,
    includeAuto: Boolean,
    recents: List<String>,
    onPick: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    BottomSheet(visible, onDismiss) {
        var query by remember(visible) { mutableStateOf("") }
        BasicText(title, style = Tolk.type.title.copy(color = Tolk.colors.ink), modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        SearchField(query, { query = it }, "Search ${Languages.all.size} languages")
        val results = remember(query) { Languages.search(query) }
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight().imePadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            if (query.isBlank()) {
                if (includeAuto) item { LanguageRow(Languages.auto, selected == Languages.auto.code) { onPick(Languages.auto) } }
                val recent = recents.map(Languages::byCode)
                if (recent.isNotEmpty()) {
                    item { SectionLabel("Your languages") }
                    items(recent, key = { "r-" + it.code }) { LanguageRow(it, it.code == selected) { onPick(it) } }
                }
                item { SectionLabel("All languages") }
            }
            items(results, key = { it.code }) { LanguageRow(it, it.code == selected) { onPick(it) } }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun LanguageRow(language: Language, selected: Boolean, onClick: () -> Unit) {
    val c = Tolk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LanguageDot(language.code, size = 12.dp)
        Column(Modifier.weight(1f)) {
            BasicText(language.name, style = Tolk.type.label.copy(color = c.ink))
            if (language.native.isNotBlank() && language.native != language.name) {
                BasicText(language.native, style = Tolk.type.small.copy(color = c.muted))
            }
        }
        if (selected) Glyph(Icons.Check, c.ink, size = 20.dp)
    }
}

@Composable
fun ModelPicker(
    visible: Boolean,
    title: String,
    imagesOnly: Boolean,
    selected: String,
    state: ModelsState,
    onLoad: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(visible) { if (visible) onLoad() }
    BottomSheet(visible, onDismiss) {
        val c = Tolk.colors
        var query by remember(visible) { mutableStateOf("") }
        BasicText(title, style = Tolk.type.title.copy(color = c.ink), modifier = Modifier.padding(start = 24.dp, top = 16.dp))
        SearchField(query, { query = it }, "Search models, e.g. gemini flash")
        val results = remember(query, state.models, imagesOnly) {
            val words = query.lowercase().split(' ').filter { it.isNotBlank() }
            state.models.filter { m ->
                (!imagesOnly || m.acceptsImages) && words.all { w -> m.id.lowercase().contains(w) || m.name.lowercase().contains(w) }
            }
        }
        when (val status = state.status) {
            Status.Working -> Dots(c.ink, Modifier.padding(start = 24.dp))
            is Status.Failed -> BasicText(status.message, style = Tolk.type.body.copy(color = c.danger), modifier = Modifier.padding(24.dp))
            Status.Idle -> LazyColumn(Modifier.fillMaxWidth().fillMaxHeight().imePadding()) {
                items(results, key = { it.id }) { ModelRow(it, it.id == selected) { onPick(it.id) } }
                item { Box(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun ModelRow(model: Model, selected: Boolean, onClick: () -> Unit) {
    val c = Tolk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(model.name, style = Tolk.type.label.copy(color = c.ink))
            BasicText(model.id, style = Tolk.type.small.copy(color = c.muted))
            BasicText(price(model), style = Tolk.type.small.copy(color = c.muted))
        }
        if (selected) Glyph(Icons.Check, c.ink, size = 20.dp)
    }
}

private fun price(model: Model): String =
    if (model.promptPerMillion == 0.0 && model.completionPerMillion == 0.0) "Free"
    else String.format(Locale.US, "$%.2f in, $%.2f out per million tokens", model.promptPerMillion, model.completionPerMillion)
