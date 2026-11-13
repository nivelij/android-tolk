package dev.nivelij.tolk

import dev.nivelij.tolk.data.DetectedLanguageSplitter
import dev.nivelij.tolk.data.Languages
import dev.nivelij.tolk.data.parsePhotoResult
import dev.nivelij.tolk.net.Message
import dev.nivelij.tolk.net.OpenRouter
import dev.nivelij.tolk.net.Part
import dev.nivelij.tolk.net.SseEvent
import dev.nivelij.tolk.net.batched
import dev.nivelij.tolk.net.chatBody
import dev.nivelij.tolk.net.parseSseLine
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsingTest {
    @Test
    fun `sse lines`() {
        assertEquals(SseEvent.Delta("Hal"), parseSseLine("""data: {"choices":[{"delta":{"content":"Hal"}}]}"""))
        assertEquals(SseEvent.Done, parseSseLine("data: [DONE]"))
        assertNull(parseSseLine(": OPENROUTER PROCESSING"))
        assertNull(parseSseLine("""data: {"choices":[{"delta":{"role":"assistant"}}]}"""))
        assertEquals(SseEvent.Error("boom"), parseSseLine("""data: {"error":{"message":"boom"}}"""))
    }

    @Test
    fun `splitter pulls the detected language off the front`() {
        val s = DetectedLanguageSplitter(expectTag = true)
        listOf("[", "[d", "e]", "]\nGood ", "morning").forEach { s.feed(it); if (s.tag == null) assertEquals("", s.body) }
        assertEquals("de", s.tag)
        assertEquals("Good morning", s.body)
    }

    @Test
    fun `splitter passes through when the model skips the tag`() {
        val s = DetectedLanguageSplitter(expectTag = true)
        s.feed("Good morning")
        assertNull(s.tag)
        assertEquals("Good morning", s.body)
    }

    @Test
    fun `photo result tolerates fences and partial lines`() {
        val raw = """
            ```jsonl
            {"lang":"de","kind":"menu","summary":"A bakery menu."}
            {"o":"Brezel","t":"Pretzel","n":""}
            {"o":"Apfelstrudel","t":"Apple strudel","n":"Warm, with vanilla sauce."}
            {"o":"Kaffee","t":"Cof
        """.trimIndent()
        val result = parsePhotoResult(raw)
        assertEquals("de", result.summary?.lang)
        assertEquals(listOf("Pretzel", "Apple strudel"), result.items.map { it.translation })
    }

    @Test
    fun `chat body uses parts only for images and adds the privacy flag`() {
        val body = JSONObject(
            chatBody(
                "m",
                listOf(Message("system", "sys"), Message("user", listOf(Part.Text("hi"), Part.Image("data:x")))),
                noDataCollection = true,
            )
        )
        val messages = body.getJSONArray("messages")
        assertEquals("sys", messages.getJSONObject(0).getString("content"))
        assertEquals("image_url", messages.getJSONObject(1).getJSONArray("content").getJSONObject(1).getString("type"))
        assertEquals("deny", body.getJSONObject("provider").getString("data_collection"))
        assertFalse(JSONObject(chatBody("m", listOf(Message("user", "x")), false)).has("provider"))
        assertEquals("minimal", JSONObject(chatBody("m", listOf(Message("user", "x")), false, "minimal")).getJSONObject("reasoning").getString("effort"))
    }

    @Test
    fun `stream reads deltas until done`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    ": OPENROUTER PROCESSING\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Guten \"}}]}\n\n" +
                        "data: {\"choices\":[{\"delta\":{\"content\":\"Morgen\"}}]}\n\n" +
                        "data: [DONE]\n\n"
                )
            )
            val api = OpenRouter(baseUrl = server.url("/v1").toString().removeSuffix("/"))
            val chunks = api.stream("k", "m", listOf(Message("user", "x")), true).toList()
            assertEquals("Guten Morgen", chunks.joinToString(""))
            assertEquals("Bearer k", server.takeRequest().getHeader("Authorization"))
        }
    }

    @Test
    fun `stream surfaces api errors`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"No auth"}}"""))
            val api = OpenRouter(baseUrl = server.url("/v1").toString().removeSuffix("/"))
            val error = runCatching { api.stream("k", "m", listOf(Message("user", "x")), true).toList() }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("rejected the key"))
        }
    }

    @Test
    fun `languages cover far more than the big ones`() {
        assertTrue(Languages.all.size > 150)
        assertEquals("Indonesian", Languages.byCode("id").name)
        assertEquals("Chinese (Traditional)", Languages.byCode("zh-Hant").name)
        assertEquals("Chinese (Simplified)", Languages.byCode("zh-hans").name)
        assertEquals("Chinese (Simplified)", Languages.byCode("zh").name)
        assertEquals("Chinese (Traditional)", Languages.byCode("zh-TW").name)
        assertEquals("Japanese", Languages.byCode("ja").name)
    }

    @Test
    fun `screen blocks drop repeats and stop at the character cap`() {
        val b = { t: String -> dev.nivelij.tolk.screen.ScreenBlock(t, 0, 0, 10, 10) }
        val capped = dev.nivelij.tolk.screen.capBlocks(listOf(b("Kopf"), b("Kopf"), b("Absatz eins"), b("Absatz zwei")), maxChars = 16)
        assertEquals(listOf("Kopf", "Absatz eins"), capped.map { it.text })
    }

    @Test
    fun `screen translation maps items back by number`() {
        val raw = """
            {"lang":"de"}
            {"i":0,"t":"Pretzel"}
            {"i":2,"t":"Baked goods"}
            {"i":3,"t":"Half a li
        """.trimIndent()
        val parsed = dev.nivelij.tolk.data.parseScreenTranslation(raw)
        assertEquals("de", parsed.lang)
        assertEquals(mapOf(0 to "Pretzel", 2 to "Baked goods"), parsed.items)
    }

    @Test
    fun `web text runs are glued back into one paragraph`() {
        val runs = listOf("Die ", "Brezel", " bzw. das Brezel", "[", "1", "]", " (in Bayern die ", "Breze", ") ist ein ", "Gebäck", ".", "Bäcker-", "Zunft")
        assertEquals("Die Brezel bzw. das Brezel (in Bayern die Breze) ist ein Gebäck. Bäcker-Zunft", dev.nivelij.tolk.screen.joinRuns(runs))
        assertEquals("Artikel Diskussion", dev.nivelij.tolk.screen.joinRuns(listOf("Artikel", "Diskussion")))
    }

    @Test
    fun `batched keeps every character and flushes the tail`() = runBlocking {
        val chunks = listOf("Gu", "ten ", "Mor", "gen")
        val batched = kotlinx.coroutines.flow.flowOf(*chunks.toTypedArray()).batched(windowMs = 10_000).toList()
        assertEquals("Guten Morgen", batched.joinToString(""))
        assertEquals(2, batched.size)
    }
}
