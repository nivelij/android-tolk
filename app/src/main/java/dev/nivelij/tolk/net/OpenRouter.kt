package dev.nivelij.tolk.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface Part {
    data class Text(val text: String) : Part
    data class Image(val dataUrl: String) : Part
}

data class Message(val role: String, val parts: List<Part>) {
    constructor(role: String, text: String) : this(role, listOf(Part.Text(text)))
}

data class Model(
    val id: String,
    val name: String,
    val acceptsImages: Boolean,
    val promptPerMillion: Double,
    val completionPerMillion: Double,
)

data class KeyInfo(val label: String?, val usage: Double?, val limit: Double?)

class ApiException(message: String) : IOException(message)

private val sharedClient: OkHttpClient by lazy {
    OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
}

class OpenRouter(
    private val http: OkHttpClient = sharedClient,
    private val baseUrl: String = "https://openrouter.ai/api/v1",
) {
    fun stream(apiKey: String, model: String, messages: List<Message>, noDataCollection: Boolean, effort: String? = null): Flow<String> =
        channelFlow {
            val call = http.newCall(
                authed(apiKey, "$baseUrl/chat/completions")
                    .post(chatBody(model, messages, noDataCollection, effort).toRequestBody(JSON))
                    .build()
            )
            launch(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(errorMessage(response))
                    val source = response.body!!.source()
                    while (true) {
                        val line = source.readUtf8Line() ?: break
                        when (val event = parseSseLine(line)) {
                            is SseEvent.Delta -> send(event.text)
                            is SseEvent.Error -> throw ApiException(event.message)
                            SseEvent.Done -> break
                            null -> Unit
                        }
                    }
                }
                channel.close()
            }
            awaitClose { call.cancel() }
        }

    suspend fun models(): List<Model> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url("$baseUrl/models").build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(errorMessage(response))
            parseModels(response.body!!.string())
        }
    }

    suspend fun keyInfo(apiKey: String): KeyInfo = withContext(Dispatchers.IO) {
        http.newCall(authed(apiKey, "$baseUrl/key").build()).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(errorMessage(response))
            val data = JSONObject(response.body!!.string()).optJSONObject("data") ?: JSONObject()
            KeyInfo(
                label = data.optString("label").ifBlank { null },
                usage = data.optDouble("usage").takeUnless { it.isNaN() },
                limit = data.optDouble("limit").takeUnless { it.isNaN() },
            )
        }
    }

    private fun authed(apiKey: String, url: String) = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $apiKey")
        .header("X-Title", "Tolk")

    private fun errorMessage(response: Response): String {
        val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
        val message = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
        return when {
            response.code == 401 -> "OpenRouter rejected the key. Check it in Settings."
            response.code == 402 -> "Your OpenRouter account is out of credit."
            message != null && message.contains("data policy", ignoreCase = true) ->
                "No provider for this model meets \"Don't let providers store my data\". Turn it off in Settings or pick another model."
            message != null -> message
            else -> "OpenRouter answered ${response.code}."
        }
    }

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}

// Models stream a few characters per event; redrawing a long screen translation on every one is wasted work.
fun Flow<String>.batched(windowMs: Long = 50): Flow<String> = flow {
    val pending = StringBuilder()
    var last = 0L
    collect { chunk ->
        pending.append(chunk)
        val now = System.nanoTime() / 1_000_000
        if (now - last >= windowMs) {
            emit(pending.toString())
            pending.clear()
            last = now
        }
    }
    if (pending.isNotEmpty()) emit(pending.toString())
}

sealed interface SseEvent {
    data class Delta(val text: String) : SseEvent
    data class Error(val message: String) : SseEvent
    data object Done : SseEvent
}

fun parseSseLine(line: String): SseEvent? {
    if (!line.startsWith("data:")) return null
    val payload = line.removePrefix("data:").trim()
    if (payload == "[DONE]") return SseEvent.Done
    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
    json.optJSONObject("error")?.let { return SseEvent.Error(it.optString("message", "The model stopped with an error.")) }
    val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") ?: return null
    val content = delta.opt("content")
    return if (content is String && content.isNotEmpty()) SseEvent.Delta(content) else null
}

fun chatBody(model: String, messages: List<Message>, noDataCollection: Boolean, effort: String? = null): String {
    val body = JSONObject()
        .put("model", model)
        .put("stream", true)
        .put("messages", JSONArray().apply {
            messages.forEach { message ->
                val content: Any = if (message.parts.size == 1 && message.parts[0] is Part.Text) {
                    (message.parts[0] as Part.Text).text
                } else {
                    JSONArray().apply {
                        message.parts.forEach { part ->
                            put(
                                when (part) {
                                    is Part.Text -> JSONObject().put("type", "text").put("text", part.text)
                                    is Part.Image -> JSONObject().put("type", "image_url")
                                        .put("image_url", JSONObject().put("url", part.dataUrl))
                                }
                            )
                        }
                    }
                }
                put(JSONObject().put("role", message.role).put("content", content))
            }
        })
    if (noDataCollection) body.put("provider", JSONObject().put("data_collection", "deny"))
    if (effort != null) body.put("reasoning", JSONObject().put("effort", effort))
    return body.toString()
}

fun parseModels(json: String): List<Model> {
    val data = JSONObject(json).getJSONArray("data")
    return (0 until data.length()).map { i ->
        val m = data.getJSONObject(i)
        val inputs = m.optJSONObject("architecture")?.optJSONArray("input_modalities")
        val pricing = m.optJSONObject("pricing")
        Model(
            id = m.getString("id"),
            name = m.optString("name", m.getString("id")),
            acceptsImages = inputs != null && (0 until inputs.length()).any { inputs.optString(it) == "image" },
            promptPerMillion = (pricing?.optString("prompt")?.toDoubleOrNull() ?: 0.0) * 1_000_000,
            completionPerMillion = (pricing?.optString("completion")?.toDoubleOrNull() ?: 0.0) * 1_000_000,
        )
    }.filter { it.promptPerMillion >= 0 && !it.id.endsWith(":batch") }
}
