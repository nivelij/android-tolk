package dev.nivelij.tolk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.nivelij.tolk.data.Prefs
import dev.nivelij.tolk.screen.openScreenServiceSettings
import dev.nivelij.tolk.screen.requestAddBubbleTile
import dev.nivelij.tolk.screen.screenServiceEnabled
import dev.nivelij.tolk.net.KeyInfo
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SettingsPage(
    prefs: Prefs,
    onBack: () -> Unit,
    onSaveKey: suspend (String) -> Result<KeyInfo>,
    onForgetKey: () -> Unit,
    onPickTextModel: () -> Unit,
    onPickVisionModel: () -> Unit,
    onNoDataCollection: (Boolean) -> Unit,
    onShowBubble: (Boolean) -> Unit,
) {
    val c = Tolk.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 32.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Back, "Back", onBack, background = Color.Transparent)
            BasicText("Settings", style = Tolk.type.wordmark.copy(color = c.ink), modifier = Modifier.padding(start = 4.dp))
        }

        Group("OpenRouter key") { KeySection(prefs, onSaveKey, onForgetKey) }

        Group("Floating translator") { FloatingSection(prefs, onShowBubble) }

        Group("Models") {
            Choice("Text", prefs.textModel, onPickTextModel)
            Divider()
            Choice("Photos", prefs.visionModel, onPickVisionModel)
        }

        Group("Privacy") {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    BasicText("Don't let providers store my data", style = Tolk.type.label.copy(color = c.ink))
                    Gap(4.dp)
                    BasicText(
                        "Only send requests to providers that don't keep or train on them. A few models stop working with this on.",
                        style = Tolk.type.small.copy(color = c.muted),
                    )
                }
                Switch(prefs.noDataCollection, onNoDataCollection)
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val c = Tolk.colors
    BasicText(title, style = Tolk.type.small.copy(color = c.muted), modifier = Modifier.padding(start = 28.dp, top = 28.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface),
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.padding(start = 18.dp).fillMaxWidth().height(1.dp).background(Tolk.colors.line))

@Composable
private fun Choice(label: String, value: String, onClick: () -> Unit) {
    val c = Tolk.colors
    Row(
        Modifier.fillMaxWidth().pressable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(label, style = Tolk.type.label.copy(color = c.ink))
            Gap(2.dp)
            BasicText(value, style = Tolk.type.small.copy(color = c.muted))
        }
        Glyph(Icons.ChevronDown, c.muted, size = 20.dp)
    }
}

@Composable
private fun KeySection(prefs: Prefs, onSaveKey: suspend (String) -> Result<KeyInfo>, onForgetKey: () -> Unit) {
    val c = Tolk.colors
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var editing by remember { mutableStateOf(prefs.apiKey == null) }
    var draft by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Column(Modifier.padding(18.dp)) {
        if (!editing && prefs.apiKey != null) {
            BasicText("Saved key ending in ${prefs.apiKey.takeLast(4)}", style = Tolk.type.label.copy(color = c.ink))
            Gap(4.dp)
            BasicText("Stored encrypted on this device only.", style = Tolk.type.small.copy(color = c.muted))
            Gap(14.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Replace", { editing = true; message = null }, background = c.background)
                Pill("Remove", { onForgetKey(); editing = true; message = null }, background = Color.Transparent, content = c.danger)
            }
        } else {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it; message = null },
                singleLine = true,
                textStyle = Tolk.type.label.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.background)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    if (draft.isEmpty()) BasicText("sk-or-…", style = Tolk.type.label.copy(color = c.muted))
                    inner()
                },
            )
            Gap(12.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    if (checking) "Checking…" else "Save key",
                    {
                        checking = true
                        scope.launch {
                            onSaveKey(draft).fold(
                                onSuccess = { info ->
                                    message = "Key works. " + usage(info) to true
                                    draft = ""
                                    editing = false
                                },
                                onFailure = { message = (it.message ?: "That key didn't work.") to false },
                            )
                            checking = false
                        }
                    },
                    enabled = draft.isNotBlank() && !checking,
                    background = c.ink,
                    content = c.background,
                )
                Pill("Get a key", { uri.openUri("https://openrouter.ai/keys") }, background = Color.Transparent, content = c.muted)
            }
        }
        message?.let { (text, ok) ->
            Gap(12.dp)
            BasicText(text, style = Tolk.type.small.copy(color = if (ok) c.ink else c.danger))
        }
    }
}

private fun usage(info: KeyInfo): String {
    val used = info.usage ?: return ""
    val limit = info.limit
    return if (limit != null) String.format(Locale.US, "$%.2f of $%.2f used.", used, limit)
    else String.format(Locale.US, "$%.2f used so far.", used)
}

@Composable
private fun FloatingSection(prefs: Prefs, onShowBubble: (Boolean) -> Unit) {
    val c = Tolk.colors
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(screenServiceEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = screenServiceEnabled(context) }

    Column(Modifier.padding(18.dp)) {
        BasicText("Translate anything on screen", style = Tolk.type.label.copy(color = c.ink))
        Gap(4.dp)
        BasicText(
            "A floating button over other apps. Tap it to translate the page you're reading, drag it to move it.",
            style = Tolk.type.small.copy(color = c.muted),
        )
        Gap(14.dp)
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText("Show floating button", style = Tolk.type.label.copy(color = c.ink), modifier = Modifier.weight(1f))
                Switch(prefs.showBubble, onShowBubble)
            }
            Gap(14.dp)
            var tileAdded by remember { mutableStateOf(false) }
            Pill(
                if (tileAdded) "Tile added" else "Add Quick Settings tile",
                { requestAddBubbleTile(context) { tileAdded = it } },
                icon = if (tileAdded) Icons.Check else Icons.Plus,
                background = c.background,
                enabled = !tileAdded,
            )
            Gap(8.dp)
            BasicText(
                "Turn the button on from the Quick Settings panel. To close it, drag it onto the cross at the bottom of the screen.",
                style = Tolk.type.small.copy(color = c.muted),
            )
        } else {
            Pill("Turn on", { openScreenServiceSettings(context) }, background = c.ink, content = c.background)
            Gap(10.dp)
            BasicText(
                "Android lists it under Accessibility as \"Tolk floating translator\".",
                style = Tolk.type.small.copy(color = c.muted),
            )
        }
    }
}
