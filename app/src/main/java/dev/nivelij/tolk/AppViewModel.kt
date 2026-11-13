package dev.nivelij.tolk

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.nivelij.tolk.data.DetectedLanguageSplitter
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.PhotoResult
import dev.nivelij.tolk.data.Photos
import dev.nivelij.tolk.data.Prefs
import dev.nivelij.tolk.data.Prompts
import dev.nivelij.tolk.data.Settings
import dev.nivelij.tolk.data.Tone
import dev.nivelij.tolk.data.parsePhotoResult
import dev.nivelij.tolk.net.KeyInfo
import dev.nivelij.tolk.net.Message
import dev.nivelij.tolk.net.Model
import dev.nivelij.tolk.net.OpenRouter
import dev.nivelij.tolk.net.batched
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Mode { Text, Photo }

sealed interface Status {
    data object Idle : Status
    data object Working : Status
    data class Failed(val message: String) : Status
}

data class TextState(
    val input: String = "",
    val context: String = "",
    val output: String = "",
    val translatedFrom: String = "",
    val detected: String? = null,
    val notes: String = "",
    val status: Status = Status.Idle,
    val notesStatus: Status = Status.Idle,
)

data class PhotoState(
    val bitmap: Bitmap? = null,
    val result: PhotoResult = PhotoResult(null, emptyList()),
    val status: Status = Status.Idle,
)

data class ModelsState(val models: List<Model> = emptyList(), val status: Status = Status.Idle)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val api = OpenRouter()

    val prefs: StateFlow<Prefs?> = settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _mode = MutableStateFlow(Mode.Text)
    val mode = _mode.asStateFlow()

    private val _text = MutableStateFlow(TextState())
    val text = _text.asStateFlow()

    private val _photo = MutableStateFlow(PhotoState())
    val photo = _photo.asStateFlow()

    private val _models = MutableStateFlow(ModelsState())
    val models = _models.asStateFlow()

    private var textJob: Job? = null
    private var notesJob: Job? = null
    private var photoJob: Job? = null

    private val _settingsRequested = MutableStateFlow(false)
    val settingsRequested = _settingsRequested.asStateFlow()

    fun openSettings() {
        _settingsRequested.value = true
    }

    fun settingsShown() {
        _settingsRequested.value = false
    }

    fun setMode(mode: Mode) {
        _mode.value = mode
    }

    fun setInput(value: String) = _text.update { it.copy(input = value) }
    fun setContext(value: String) = _text.update { it.copy(context = value) }

    fun clearInput() {
        textJob?.cancel()
        notesJob?.cancel()
        _text.update { TextState(context = it.context) }
    }

    fun setLanguages(source: String, target: String) = viewModelScope.launch { settings.setLanguages(source, target) }
    fun setTone(tone: Tone) = viewModelScope.launch { settings.setTone(tone) }
    fun setTextModel(id: String) = viewModelScope.launch { settings.setTextModel(id) }
    fun setVisionModel(id: String) = viewModelScope.launch { settings.setVisionModel(id) }
    fun setNoDataCollection(on: Boolean) = viewModelScope.launch { settings.setNoDataCollection(on) }
    fun setShowBubble(on: Boolean) = viewModelScope.launch { settings.setShowBubble(on) }

    fun swapLanguages() = viewModelScope.launch {
        val p = currentPrefs()
        val state = _text.value
        val newTarget = if (p.source == dev.nivelij.tolk.data.AUTO_CODE) state.detected ?: return@launch else p.source
        settings.setLanguages(p.target, newTarget)
        if (state.output.isNotBlank()) {
            _text.update { TextState(input = state.output, context = state.context) }
            translate()
        }
    }

    fun translate() {
        val input = _text.value.input
        if (input.isBlank()) return
        textJob?.cancel()
        notesJob?.cancel()
        textJob = viewModelScope.launch {
            val p = currentPrefs()
            val key = p.apiKey ?: return@launch fail { copy(status = Status.Failed(NO_KEY)) }
            val source = Languages.byCode(p.source)
            val target = Languages.byCode(p.target)
            _text.update { it.copy(output = "", notes = "", detected = null, translatedFrom = input, status = Status.Working, notesStatus = Status.Idle) }
            val splitter = DetectedLanguageSplitter(expectTag = source.isAuto)
            runStream(key, p.textModel, Prompts.translate(input, source, target, p.tone, _text.value.context), p.noDataCollection, FAST,
                onChunk = { chunk ->
                    splitter.feed(chunk)
                    _text.update { it.copy(output = splitter.body, detected = splitter.tag) }
                },
                onDone = { _text.update { it.copy(status = Status.Idle) } },
                onError = { msg -> _text.update { it.copy(status = Status.Failed(msg)) } },
            )
        }
    }

    fun explain() {
        val state = _text.value
        if (state.output.isBlank()) return
        notesJob?.cancel()
        notesJob = viewModelScope.launch {
            val p = currentPrefs()
            val key = p.apiKey ?: return@launch
            val notes = StringBuilder()
            _text.update { it.copy(notes = "", notesStatus = Status.Working) }
            runStream(key, p.textModel, Prompts.explain(state.translatedFrom, state.output, Languages.byCode(p.target)), p.noDataCollection, FAST,
                onChunk = { chunk ->
                    notes.append(chunk)
                    _text.update { it.copy(notes = notes.toString()) }
                },
                onDone = { _text.update { it.copy(notesStatus = Status.Idle) } },
                onError = { msg -> _text.update { it.copy(notesStatus = Status.Failed(msg)) } },
            )
        }
    }

    fun receiveText(value: String) {
        _mode.value = Mode.Text
        _text.update { TextState(input = value, context = it.context) }
        translate()
    }

    fun receivePhoto(uri: Uri) {
        _mode.value = Mode.Photo
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            val bitmap = runCatching { withContext(Dispatchers.IO) { Photos.load(getApplication(), uri) } }.getOrElse {
                Log.w("Tolk", "Couldn't decode $uri", it)
                _photo.value = PhotoState(status = Status.Failed("Couldn't open that image."))
                return@launch
            }
            _photo.value = PhotoState(bitmap = bitmap, status = Status.Working)
            translatePhoto(bitmap)
        }
    }

    fun retryPhoto() {
        val bitmap = _photo.value.bitmap ?: return
        photoJob?.cancel()
        photoJob = viewModelScope.launch {
            _photo.update { it.copy(result = PhotoResult(null, emptyList()), status = Status.Working) }
            translatePhoto(bitmap)
        }
    }

    fun clearPhoto() {
        photoJob?.cancel()
        _photo.value = PhotoState()
    }

    private suspend fun translatePhoto(bitmap: Bitmap) {
        val p = currentPrefs()
        val key = p.apiKey ?: return _photo.update { it.copy(status = Status.Failed(NO_KEY)) }
        val dataUrl = withContext(Dispatchers.Default) { Photos.toDataUrl(bitmap) }
        val raw = StringBuilder()
        runStream(key, p.visionModel, Prompts.photo(dataUrl, Languages.byCode(p.target), _text.value.context), p.noDataCollection, PHOTO,
            onChunk = { chunk ->
                raw.append(chunk)
                _photo.update { it.copy(result = parsePhotoResult(raw.toString())) }
            },
            onDone = {
                _photo.update {
                    if (it.result.items.isEmpty()) it.copy(status = Status.Failed("No readable text found in this photo."))
                    else it.copy(status = Status.Idle)
                }
            },
            onError = { msg -> _photo.update { it.copy(status = Status.Failed(msg)) } },
        )
    }

    fun loadModels() {
        if (_models.value.models.isNotEmpty() || _models.value.status == Status.Working) return
        viewModelScope.launch {
            _models.value = ModelsState(status = Status.Working)
            _models.value = runCatching { ModelsState(api.models().sortedBy { it.name.lowercase() }) }
                .getOrElse { ModelsState(status = Status.Failed(it.message ?: "Couldn't load models.")) }
        }
    }

    suspend fun saveKey(key: String): Result<KeyInfo> {
        val result = runCatching { api.keyInfo(key.trim()) }
        if (result.isSuccess) settings.setApiKey(key)
        return result
    }

    fun forgetKey() = viewModelScope.launch { settings.setApiKey(null) }

    private suspend fun runStream(
        key: String,
        model: String,
        messages: List<Message>,
        noDataCollection: Boolean,
        effort: String?,
        onChunk: (String) -> Unit,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        try {
            api.stream(key, model, messages, noDataCollection, effort).batched().collect(onChunk)
            onDone()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(e.message ?: "Something went wrong talking to OpenRouter.")
        }
    }

    private suspend fun currentPrefs(): Prefs = settings.prefs.first()

    private inline fun fail(block: TextState.() -> TextState) = _text.update(block)

    private companion object {
        const val NO_KEY = "Add your OpenRouter key in Settings first."
        // Gemini 3.8 Flash thinks by default: ~7.6s to first word vs ~2.1s at "minimal", with no visible quality loss in tests.
        const val FAST = "minimal"
        const val PHOTO = "low"
    }
}
