package dev.nivelij.tolk.data

import dev.nivelij.tolk.net.Message
import dev.nivelij.tolk.net.Part
import org.json.JSONObject

object Prompts {
    fun translate(text: String, source: Language, target: Language, tone: Tone, context: String): List<Message> {
        val system = buildString {
            appendLine("You are an expert human translator. Translate the user's text into ${target.name}.")
            if (source.isAuto) {
                appendLine("Start your reply with the source language's code in double square brackets, for example [[de]] or [[zh-Hant]], then a newline, then the translation.")
            } else {
                appendLine("The text is in ${source.name}.")
            }
            when (tone) {
                Tone.Formal -> appendLine("Use a formal, polite register.")
                Tone.Casual -> appendLine("Use a casual, friendly register.")
                Tone.Auto -> appendLine("Match the register of the original.")
            }
            if (context.isNotBlank()) appendLine("Situation the text comes from: ${context.trim()}")
            appendLine("Translate meaning, not word by word: it must read as if a native ${target.name} speaker wrote it.")
            appendLine("Keep names, numbers, line breaks and formatting. Do not add quotes, notes, or explanations.")
        }
        return listOf(Message("system", system), Message("user", text))
    }

    const val SCREEN_CONTEXT =
        "Text captured from a phone screen, one item per line in reading order. Lines can be headlines, paragraphs, " +
            "links, buttons or fragments of a web page. Keep one output line per input line, and leave lines that are already in the target language as they are."

    fun explain(source: String, translation: String, target: Language): List<Message> = listOf(
        Message(
            "system",
            "You help someone understand a translation they just received. Reply in English. " +
                "Give at most 5 short points, each on its own line starting with \"• \": notable word choices, " +
                "idioms or cultural meaning, the register (formal or casual), and one or two natural alternatives in ${target.name}. " +
                "Plain text only: no Markdown, no asterisks. No preamble.",
        ),
        Message("user", "Original:\n$source\n\nTranslation (${target.name}):\n$translation"),
    )

    fun photo(dataUrl: String, target: Language, context: String): List<Message> {
        val system = buildString {
            appendLine("You read text in photos and translate it into ${target.name}.")
            appendLine("Reply in JSON Lines only: one JSON object per line, no code fences, no other text.")
            appendLine("""First line: {"lang": source language code, "kind": one of menu|sign|document|label|screen|other, "summary": one sentence in ${target.name} saying what this is}""")
            appendLine("""Then one line per piece of text, in reading order: {"o": original text, "t": translation, "n": short note in ${target.name} or ""}""")
            appendLine("Group text that belongs together, like a dish name with its description. Skip page numbers, watermarks and decorative text.")
            appendLine("Drop dot leaders and filler punctuation between an item and its price; write the price after an en dash, e.g. \"Apple strudel – €6.20\".")
            appendLine("Use the note only when it helps: what a dish actually is, what a sign means in practice, a price or unit explained.")
            if (context.isNotBlank()) appendLine("Situation: ${context.trim()}")
        }
        return listOf(
            Message("system", system),
            Message("user", listOf(Part.Text("Translate the text in this photo."), Part.Image(dataUrl))),
        )
    }
}

class DetectedLanguageSplitter(private val expectTag: Boolean) {
    private val raw = StringBuilder()
    var tag: String? = null
        private set
    var body: String = ""
        private set

    fun feed(chunk: String) {
        raw.append(chunk)
        if (!expectTag) {
            body = raw.toString()
            return
        }
        val text = raw.toString().trimStart()
        when {
            text.length < 2 && "[[".startsWith(text) -> body = ""
            text.startsWith("[[") -> {
                val end = text.indexOf("]]")
                if (end < 0) {
                    body = if (text.length > 20) text else ""
                } else {
                    tag = text.substring(2, end).trim().ifEmpty { null }
                    body = text.substring(end + 2).trimStart()
                }
            }
            else -> body = raw.toString()
        }
    }
}

data class PhotoSummary(val lang: String?, val kind: String?, val summary: String?)
data class PhotoItem(val original: String, val translation: String, val note: String)
data class PhotoResult(val summary: PhotoSummary?, val items: List<PhotoItem>)

fun parsePhotoResult(raw: String): PhotoResult {
    var summary: PhotoSummary? = null
    val items = mutableListOf<PhotoItem>()
    raw.lineSequence()
        .map { it.trim().removeSuffix(",") }
        .filter { it.startsWith("{") && it.endsWith("}") }
        .forEach { line ->
            val json = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
            when {
                json.has("t") -> items += PhotoItem(
                    original = json.optString("o"),
                    translation = json.optString("t"),
                    note = json.optString("n"),
                )
                json.has("summary") || json.has("lang") -> summary = PhotoSummary(
                    lang = json.optString("lang").ifBlank { null },
                    kind = json.optString("kind").ifBlank { null },
                    summary = json.optString("summary").ifBlank { null },
                )
            }
        }
    return PhotoResult(summary, items)
}
