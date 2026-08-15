package online.xiaoxi.chathub.data

import org.json.JSONObject

class SseParser(private val onEvent: (ChatEvent) -> Unit) {
    private var lastEvent: String = "token"

    fun handleLine(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty()) return
        if (line.startsWith("event:")) {
            lastEvent = line.removePrefix("event:").trim()
            return
        }
        if (!line.startsWith("data:")) return
        val data = line.removePrefix("data:").trim()
        if (data.isEmpty() || data == "[DONE]") return
        val json = try {
            JSONObject(data)
        } catch (_: Exception) {
            return
        }
        when (lastEvent) {
            "token" -> onEvent(ChatEvent.Token(json.optString("delta")))
            "done" -> onEvent(ChatEvent.Done(json.optInt("message_id")))
            "error" -> onEvent(ChatEvent.Error(json.optString("code"), json.optString("message")))
        }
        lastEvent = "token"
    }
}
