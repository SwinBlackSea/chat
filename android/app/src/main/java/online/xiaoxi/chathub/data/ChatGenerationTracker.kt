package online.xiaoxi.chathub.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call

object ChatGenerationTracker {
    private val calls = ConcurrentHashMap<Int, Call>()
    private val _activeConversations = MutableStateFlow<Set<Int>>(emptySet())
    val activeConversations = _activeConversations.asStateFlow()

    fun start(conversationId: Int, call: Call) {
        calls.put(conversationId, call)?.cancel()
        _activeConversations.update { it + conversationId }
    }

    fun finish(conversationId: Int) {
        calls.remove(conversationId)
        _activeConversations.update { it - conversationId }
    }

    fun stop(conversationId: Int) {
        calls.remove(conversationId)?.cancel()
        _activeConversations.update { it - conversationId }
    }
}
