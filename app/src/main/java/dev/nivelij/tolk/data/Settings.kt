package dev.nivelij.tolk.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import dev.nivelij.tolk.screen.refreshBubbleTile

enum class Tone(val label: String) { Auto("Natural"), Formal("Formal"), Casual("Casual") }

data class Prefs(
    val apiKey: String? = null,
    val source: String = AUTO_CODE,
    val target: String = "en",
    val textModel: String = DEFAULT_MODEL,
    val visionModel: String = DEFAULT_MODEL,
    val tone: Tone = Tone.Auto,
    val noDataCollection: Boolean = true,
    val recents: List<String> = emptyList(),
    val showBubble: Boolean = true,
)

const val DEFAULT_MODEL = "google/gemini-3.8-flash"

private val Context.store by preferencesDataStore("tolk")

class Settings(private val context: Context, private val vault: KeyVault = KeyVault()) {
    @Volatile private var openedKey: Pair<String, String?>? = null

    val prefs: Flow<Prefs> = context.store.data.map { p ->
        Prefs(
            apiKey = p[API_KEY]?.let(::openKey),
            source = p[SOURCE] ?: AUTO_CODE,
            target = p[TARGET] ?: "en",
            textModel = p[TEXT_MODEL] ?: DEFAULT_MODEL,
            visionModel = p[VISION_MODEL] ?: DEFAULT_MODEL,
            tone = p[TONE]?.let { runCatching { Tone.valueOf(it) }.getOrNull() } ?: Tone.Auto,
            noDataCollection = p[NO_DATA_COLLECTION] ?: true,
            recents = p[RECENTS]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            showBubble = p[SHOW_BUBBLE] ?: true,
        )
    }.flowOn(Dispatchers.Default)

    private fun openKey(sealed: String): String? {
        openedKey?.let { (s, key) -> if (s == sealed) return key }
        return vault.open(sealed).also { openedKey = sealed to it }
    }

    suspend fun setApiKey(key: String?) = context.store.edit {
        if (key.isNullOrBlank()) it.remove(API_KEY) else it[API_KEY] = vault.seal(key.trim())
    }

    suspend fun setLanguages(source: String, target: String) = context.store.edit {
        it[SOURCE] = source
        it[TARGET] = target
        val used = listOf(target, source).filter { c -> c != AUTO_CODE }
        val old = it[RECENTS]?.split(',')?.filter { c -> c.isNotBlank() } ?: emptyList()
        it[RECENTS] = (used + old).distinct().take(6).joinToString(",")
    }

    suspend fun setTextModel(id: String) = edit(TEXT_MODEL, id)
    suspend fun setVisionModel(id: String) = edit(VISION_MODEL, id)
    suspend fun setTone(tone: Tone) = edit(TONE, tone.name)
    suspend fun setNoDataCollection(on: Boolean) = context.store.edit { it[NO_DATA_COLLECTION] = on }
    suspend fun setShowBubble(on: Boolean) {
        context.store.edit { it[SHOW_BUBBLE] = on }
        refreshBubbleTile(context)
    }

    private suspend fun edit(key: Preferences.Key<String>, value: String) = context.store.edit { it[key] = value }

    private companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val SOURCE = stringPreferencesKey("source")
        val TARGET = stringPreferencesKey("target")
        val TEXT_MODEL = stringPreferencesKey("text_model")
        val VISION_MODEL = stringPreferencesKey("vision_model")
        val TONE = stringPreferencesKey("tone")
        val NO_DATA_COLLECTION = booleanPreferencesKey("no_data_collection")
        val RECENTS = stringPreferencesKey("recents")
        val SHOW_BUBBLE = booleanPreferencesKey("show_bubble")
    }
}
