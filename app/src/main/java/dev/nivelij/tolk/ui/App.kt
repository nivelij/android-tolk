package dev.nivelij.tolk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nivelij.tolk.AppViewModel
import dev.nivelij.tolk.Mode
import dev.nivelij.tolk.data.AUTO_CODE
import dev.nivelij.tolk.data.Photos

private fun deviceLanguages(): List<String> {
    val locales = android.os.LocaleList.getDefault()
    return (0 until locales.size()).map { locales[it].language } + "en"
}

private enum class Picker { Source, Target, TextModel, VisionModel }

@Composable
fun TolkApp(vm: AppViewModel) {
    val prefs = vm.prefs.collectAsStateWithLifecycle().value ?: return
    val mode by vm.mode.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val photo by vm.photo.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val settingsRequested by vm.settingsRequested.collectAsStateWithLifecycle()
    LaunchedEffect(settingsRequested) {
        if (settingsRequested) {
            settingsOpen = true
            vm.settingsShown()
        }
    }
    var captureUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = captureUri
        if (ok && uri != null) vm.receivePhoto(uri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.receivePhoto(uri)
    }
    val snap = {
        val uri = Photos.newCaptureUri(context)
        captureUri = uri
        takePicture.launch(uri)
    }
    val gallery = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    TolkTheme {
        val c = Tolk.colors
        Box(Modifier.fillMaxSize().background(c.background)) {
            when (mode) {
                Mode.Text -> TextScreen(
                    prefs = prefs,
                    state = text,
                    onInput = vm::setInput,
                    onContext = vm::setContext,
                    onClear = vm::clearInput,
                    onTranslate = vm::translate,
                    onExplain = vm::explain,
                    onTone = { vm.setTone(it) },
                    onSwap = { vm.swapLanguages() },
                    onPickSource = { picker = Picker.Source },
                    onPickTarget = { picker = Picker.Target },
                    onSettings = { settingsOpen = true },
                )
                Mode.Photo -> PhotoScreen(
                    prefs = prefs,
                    state = photo,
                    onSnap = snap,
                    onGallery = gallery,
                    onClear = vm::clearPhoto,
                    onRetry = vm::retryPhoto,
                    onPickTarget = { picker = Picker.Target },
                    onSettings = { settingsOpen = true },
                )
            }

            Dock(mode, vm::setMode, Modifier.align(Alignment.BottomCenter))

            FullPage(settingsOpen, onDismiss = { settingsOpen = false }) {
                SettingsPage(
                    prefs = prefs,
                    onBack = { settingsOpen = false },
                    onSaveKey = vm::saveKey,
                    onForgetKey = { vm.forgetKey() },
                    onPickTextModel = { picker = Picker.TextModel },
                    onPickVisionModel = { picker = Picker.VisionModel },
                    onNoDataCollection = { vm.setNoDataCollection(it) },
                    onShowBubble = { vm.setShowBubble(it) },
                )
            }

            LanguagePicker(
                visible = picker == Picker.Source || picker == Picker.Target,
                title = if (picker == Picker.Source) "Translate from" else "Translate into",
                selected = if (picker == Picker.Source) prefs.source else prefs.target,
                includeAuto = picker == Picker.Source,
                recents = (prefs.recents + deviceLanguages()).distinct().take(8),
                onPick = { language ->
                    when (picker) {
                        Picker.Source -> vm.setLanguages(language.code, if (language.code == prefs.target) prefs.source.takeIf { it != AUTO_CODE } ?: prefs.target else prefs.target)
                        else -> vm.setLanguages(if (language.code == prefs.source) AUTO_CODE else prefs.source, language.code)
                    }
                    picker = null
                },
                onDismiss = { picker = null },
            )

            ModelPicker(
                visible = picker == Picker.TextModel || picker == Picker.VisionModel,
                title = if (picker == Picker.VisionModel) "Model for photos" else "Model for text",
                imagesOnly = picker == Picker.VisionModel,
                selected = if (picker == Picker.VisionModel) prefs.visionModel else prefs.textModel,
                state = models,
                onLoad = vm::loadModels,
                onPick = { id ->
                    if (picker == Picker.VisionModel) vm.setVisionModel(id) else vm.setTextModel(id)
                    picker = null
                },
                onDismiss = { picker = null },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dock(mode: Mode, onMode: (Mode) -> Unit, modifier: Modifier) {
    val c = Tolk.colors
    AnimatedVisibility(
        !WindowInsets.isImeVisible,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(CircleShape)
                .background(c.surface)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DockItem("Text", Icons.Text, mode == Mode.Text) { onMode(Mode.Text) }
            DockItem("Photo", Icons.Camera, mode == Mode.Photo) { onMode(Mode.Photo) }
        }
    }
}

@Composable
private fun DockItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = Tolk.colors
    val bg by androidx.compose.animation.animateColorAsState(if (selected) c.ink else c.surface, label = "dock")
    val fg by androidx.compose.animation.animateColorAsState(if (selected) c.background else c.muted, label = "dockfg")
    Row(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .pressable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(icon, fg, size = 20.dp)
        BasicText(label, style = Tolk.type.label.copy(color = fg))
    }
}
