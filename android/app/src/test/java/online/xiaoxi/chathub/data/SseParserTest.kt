package online.xiaoxi.chathub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SseParserTest {

    private fun parse(vararg lines: String): List<ChatEvent> {
        val events = mutableListOf<ChatEvent>()
        val parser = SseParser { events.add(it) }
        lines.forEach { parser.handleLine(it) }
        return events
    }

    @Test
    fun tokenEventsAccumulateDeltas() {
        val events = parse(
            "event: token",
            """data: {"delta":"你"}""",
            "event: token",
            """data: {"delta":"好"}""",
        )
        assertEquals(listOf(ChatEvent.Token("你"), ChatEvent.Token("好")), events)
    }

    @Test
    fun defaultsToTokenEventWithoutEventLine() {
        val events = parse("""data: {"delta":"hi"}""")
        assertEquals(listOf(ChatEvent.Token("hi")), events)
    }

    @Test
    fun ignoresCommentsBlankAndNonDataLines() {
        val events = parse(
            ": keep-alive",
            "",
            "event: token",
            "not-sse garbage",
            """data: {"delta":"A"}""",
            "",
        )
        assertEquals(listOf(ChatEvent.Token("A")), events)
    }

    @Test
    fun doneEventCarriesMessageId() {
        val events = parse(
            "event: done",
            """data: {"message_id":42,"usage":{"prompt_tokens":1}}""",
        )
        assertEquals(listOf(ChatEvent.Done(42)), events)
    }

    @Test
    fun errorEventCarriesCodeAndMessage() {
        val events = parse(
            "event: error",
            """data: {"code":"bad_key","message":"API Key 无效"}""",
        )
        assertEquals(listOf(ChatEvent.Error("bad_key", "API Key 无效")), events)
    }

    @Test
    fun ignoresDoneMarker() {
        assertTrue(parse("data: [DONE]").isEmpty())
    }

    @Test
    fun ignoresInvalidJsonData() {
        assertTrue(parse("""data: {broken""").isEmpty())
    }

    @Test
    fun eventTypeResetsToTokenAfterData() {
        val events = parse(
            "event: error",
            """data: {"code":"rate_limit","message":"慢"}""",
            """data: {"delta":"你"}""",
        )
        assertEquals(listOf(ChatEvent.Error("rate_limit", "慢"), ChatEvent.Token("你")), events)
    }

    @Test
    fun handlesChunkedTokensAcrossLines() {
        // 模拟上游分片：同一事件多行 data（仅最后一行有内容时只取该行，多余行忽略）
        val events = parse(
            "event: token",
            """data: {"delta":"A"}""",
            "event: token",
            """data: {"delta":"B"}""",
            "event: token",
            """data: {"delta":"C"}""",
        )
        assertEquals(
            listOf(ChatEvent.Token("A"), ChatEvent.Token("B"), ChatEvent.Token("C")),
            events,
        )
    }
}
