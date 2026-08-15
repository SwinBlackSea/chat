package online.xiaoxi.chathub.data

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** WebSocket 下行事件（人人聊天实时通道，独立于 AI 的 SSE）。 */
sealed class WsEvent {
    data class Message(
        val from: String,
        val content: String,
        val messageId: Int,
        val conversationId: Int,
        val ts: String,
    ) : WsEvent()

    data class Ack(val messageId: Int, val conversationId: Int, val delivered: Boolean) : WsEvent()
    data class Online(val userId: String, val online: Boolean) : WsEvent()
    data class Typing(val from: String) : WsEvent()
    data class Read(
        val from: String,
        val conversationId: Int = 0,
        val lastReadMsgId: Int = 0,
    ) : WsEvent()
    object Sync : WsEvent()
}

/**
 * 全局 WebSocket 连接（单例）：连接 /api/ws，协议层 ping 保活，断线指数退避重连。
 * 上行：message/typing/read；下行事件经 addListener 分发（回调在 OkHttp 线程，UI 需自行切主线程）。
 */
object WsHub {
    private val listeners = CopyOnWriteArrayList<(WsEvent) -> Unit>()
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    /** 在线的人联系人（由 online 事件维护，通讯录展示用）。 */
    private val _onlineUsers = MutableStateFlow<Set<String>>(emptySet())
    val onlineUsers = _onlineUsers.asStateFlow()

    private var ws: WebSocket? = null
    private var reconnectAttempt = 0
    @Volatile
    private var shouldRun = false
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ChatHub-WS").apply { isDaemon = true }
    }

    fun addListener(listener: (WsEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (WsEvent) -> Unit) {
        listeners.remove(listener)
    }

    /** 按当前身份连接；身份变化时先 stop() 再 start()。 */
    fun start() {
        if (Backend.userId.isEmpty() || Backend.baseUrl.isEmpty()) return
        shouldRun = true
        connect()
    }

    fun stop() {
        shouldRun = false
        ws?.close(1000, "bye")
        ws = null
        _connected.value = false
    }

    /** 身份或地址变化后重连。 */
    fun restart() {
        stop()
        start()
    }

    private fun connect() {
        if (!shouldRun || Backend.userId.isEmpty()) return
        val httpBase = Backend.baseUrl.trimEnd('/')
        val wsUrl = httpBase.replaceFirst("http", "ws") + "/api/ws?user_id=" + Backend.userId
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(25, TimeUnit.SECONDS)
            .build()
        ws = client.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connected.value = true
            reconnectAttempt = 0
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = try {
                JSONObject(text)
            } catch (_: Exception) {
                return
            }
            val event: WsEvent? = when (json.optString("type")) {
                "message" -> WsEvent.Message(
                    from = json.optString("from"),
                    content = json.optString("content"),
                    messageId = json.optInt("message_id"),
                    conversationId = json.optInt("conversation_id"),
                    ts = json.optString("ts"),
                )
                "ack" -> WsEvent.Ack(
                    messageId = json.optInt("message_id"),
                    conversationId = json.optInt("conversation_id"),
                    delivered = json.optBoolean("delivered"),
                )
                "online" -> {
                    val uid = json.optString("user_id")
                    if (uid.isNotEmpty()) {
                        val online = json.optBoolean("online")
                        _onlineUsers.update {
                            if (online) it + uid else it - uid
                        }
                    }
                    WsEvent.Online(json.optString("user_id"), json.optBoolean("online"))
                }
                "typing" -> WsEvent.Typing(json.optString("from"))
                "read" -> WsEvent.Read(
                    from = json.optString("from"),
                    conversationId = json.optInt("conversation_id"),
                    lastReadMsgId = json.optInt("last_read_msg_id"),
                )
                "sync" -> WsEvent.Sync
                else -> null
            }
            event?.let { e -> listeners.forEach { it(e) } }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connected.value = false
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connected.value = false
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldRun) return
        val delayMs = minOf(30_000L, 1_000L shl reconnectAttempt.coerceAtMost(5))
        reconnectAttempt++
        executor.schedule({ connect() }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** 发送消息；返回是否成功（ws 未连接/发送失败时返回 false）。 */
    fun sendMessage(to: String, content: String): Boolean {
        val socket = ws
        if (socket == null || !_connected.value) return false
        return try {
            socket.send(JSONObject(mapOf("type" to "message", "to" to to, "content" to content)).toString())
        } catch (_: Exception) {
            false
        }
    }

    fun sendTyping(to: String) {
        ws?.send(JSONObject(mapOf("type" to "typing", "to" to to)).toString())
    }

    fun sendRead(to: String) {
        ws?.send(JSONObject(mapOf("type" to "read", "to" to to)).toString())
    }
}
