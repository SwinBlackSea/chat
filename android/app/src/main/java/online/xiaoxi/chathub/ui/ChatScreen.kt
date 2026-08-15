package online.xiaoxi.chathub.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import online.xiaoxi.chathub.data.ApiClient
import online.xiaoxi.chathub.data.Backend
import online.xiaoxi.chathub.data.ChatEvent
import online.xiaoxi.chathub.data.ChatGenerationTracker
import online.xiaoxi.chathub.data.ConversationDto
import online.xiaoxi.chathub.data.WsEvent
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.theme.WxBubbleMe
import online.xiaoxi.chathub.theme.WxChatBackground
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLineStrong
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.WxText3

private data class UiMessage(
    val key: String,
    val role: String,
    val content: String,
    val error: String?,
    val senderUserId: String? = null,
    val time: String? = null,
)

@Composable
@kotlin.OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun ChatScreen(conversationId: Int, onBack: () -> Unit, onInfo: () -> Unit) {
    val api = remember { ApiClient() }
    val scope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val clipboard = LocalClipboardManager.current
    val activeConversations by ChatGenerationTracker.activeConversations.collectAsState()
    val streaming = conversationId in activeConversations
    var conv by remember { mutableStateOf<ConversationDto?>(null) }
    var messages by remember { mutableStateOf<List<UiMessage>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var screenError by remember { mutableStateOf<String?>(null) }
    var typingByPeer by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val isHuman = conv?.isHuman == true

    suspend fun reload() {
        // 会话信息（单会话端点）+ 消息并行拉取，减半 Tailscale 慢链路的等待
        coroutineScope {
            val convDeferred = async { api.conversation(conversationId) }
            val msgDeferred = async { api.messages(conversationId) }
            conv = convDeferred.await()
            messages = msgDeferred.await().map {
                UiMessage(it.id.toString(), it.role, it.content, it.error, it.senderUserId, it.createdAt)
            }
        }
        screenError = null
    }

    // 人人会话：实时事件（对方消息/回执/typing/online）驱动刷新与提示
    DisposableEffect(conversationId) {
        val listener: (WsEvent) -> Unit = { event ->
            when (event) {
                is WsEvent.Message -> {
                    if (event.conversationId == conversationId) {
                        mainHandler.post {
                            scope.launch {
                                try {
                                    reload()
                                    api.markRead(conversationId) // 正在看该会话，新消息即已读
                                } catch (e: Exception) {
                                    screenError = "刷新失败：${e.message}"
                                }
                            }
                        }
                    }
                }
                is WsEvent.Ack -> {
                    if (event.conversationId == conversationId) {
                        mainHandler.post {
                            scope.launch {
                                try {
                                    reload()
                                } catch (e: Exception) {
                                    screenError = "刷新失败：${e.message}"
                                }
                            }
                        }
                    }
                }
                is WsEvent.Typing -> {
                    if (event.from == conv?.peerUserId) {
                        mainHandler.post {
                            typingByPeer = true
                            mainHandler.postDelayed({ typingByPeer = false }, 2500)
                        }
                    }
                }
                else -> Unit
            }
        }
        WsHub.addListener(listener)
        onDispose { WsHub.removeListener(listener) }
    }

    LaunchedEffect(conversationId) {
        try {
            reload()
        } catch (e: Exception) {
            screenError = "加载失败：${e.message}"
        }
        // 进入会话即标记已读（清未读红点）
        try {
            api.markRead(conversationId)
        } catch (_: Exception) {
        }
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    LaunchedEffect(listState) {
        snapshotFlow { messages.size }.distinctUntilChanged().collect { size ->
            if (size > 0) listState.animateScrollToItem(size - 1)
        }
    }

    // 键盘弹出/收起时滚动到最新消息（避免最新几条被输入法遮挡）
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(streaming) {
        if (!streaming && conv != null) {
            delay(150)
            try {
                reload()
            } catch (e: Exception) {
                screenError = "刷新失败：${e.message}"
            }
        }
    }

    fun sendHuman() {
        val text = input.trim()
        if (text.isEmpty()) return
        val peer = conv?.peerUserId
        if (peer.isNullOrEmpty()) return
        if (!WsHub.connected.value) {
            screenError = "实时通道未连接，请检查网络后重试"
            return
        }
        input = ""
        val key = "h-${System.currentTimeMillis()}"
        messages = messages + UiMessage(key, "user", text, null, Backend.userId)
        screenError = null
        // 服务端落库后回 ack 事件，触发 reload 换成真实消息
        WsHub.sendMessage(peer, text)
    }

    fun send(regenerate: Boolean = false, regenerateContent: String? = null) {
        val text = if (regenerate) regenerateContent.orEmpty() else input.trim()
        if (text.isEmpty() || streaming) return
        if (!regenerate) input = ""
        val assistantKey = "a-${System.currentTimeMillis()}"
        val baseMessages = if (regenerate) {
            val lastUserIndex = messages.indexOfLast { it.role == "user" }
            if (lastUserIndex < 0) return
            messages.take(lastUserIndex + 1)
        } else {
            messages + UiMessage("u-$assistantKey", "user", text, null)
        }
        messages = baseMessages +
            UiMessage(assistantKey, "assistant", "", null)
        screenError = null
        api.startChat(
            conversationId = conversationId,
            content = text,
            regenerate = regenerate,
            onStart = { ChatGenerationTracker.start(conversationId, it) },
        ) { event ->
            mainHandler.post {
                when (event) {
                    is ChatEvent.Token -> messages = messages.map {
                        if (it.key == assistantKey) it.copy(content = it.content + event.delta) else it
                    }
                    is ChatEvent.Done -> {
                        ChatGenerationTracker.finish(conversationId)
                        scope.launch {
                            try {
                                reload()
                            } catch (e: Exception) {
                                screenError = "刷新失败：${e.message}"
                            }
                        }
                    }
                    is ChatEvent.Error -> {
                        messages = messages.map {
                            if (it.key == assistantKey) it.copy(error = event.message) else it
                        }
                        ChatGenerationTracker.finish(conversationId)
                        scope.launch {
                            try {
                                reload()
                            } catch (e: Exception) {
                                screenError = "刷新失败：${e.message}"
                            }
                        }
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(WxChatBackground)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onInfo),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    conv?.contactName ?: "",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        isHuman && typingByPeer -> "对方正在输入…"
                        streaming -> "对方正在输入…"
                        isHuman -> conv?.modelCode.orEmpty()
                        else -> conv?.modelCode.orEmpty()
                    },
                    fontSize = 11.sp,
                    color = WxText2,
                )
            }
            IconButton(onClick = onInfo) {
                Icon(Icons.Filled.MoreVert, contentDescription = "资料")
            }
        }

        if (screenError != null) {
            Text(
                screenError!!,
                color = WxRed,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        ) {
            itemsIndexed(messages, key = { _, message -> message.key }) { index, msg ->
                val mine = if (isHuman) {
                    msg.senderUserId == Backend.userId
                } else {
                    msg.role == "user"
                }
                // 时间分割线：第一条或与上一条间隔 ≥5 分钟
                if (index == 0 || shouldShowTimeDivider(messages[index - 1], msg)) {
                    Text(
                        formatWhen(msg.time),
                        fontSize = 11.sp,
                        color = WxText3,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                // 连续消息分组（同发送方相邻）
                val prevMine = if (index > 0) {
                    isMine(messages[index - 1], isHuman)
                } else {
                    null
                }
                val nextMine = if (index < messages.lastIndex) {
                    isMine(messages[index + 1], isHuman)
                } else {
                    null
                }
                val firstInGroup = prevMine != mine
                val lastInGroup = nextMine != mine
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) {
                        androidx.compose.foundation.layout.Arrangement.End
                    } else {
                        androidx.compose.foundation.layout.Arrangement.Start
                    },
                ) {
                    if (!mine && firstInGroup) {
                        Avatar(conv?.contactName ?: "A", conv?.avatarColor, size = 40.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    if (!mine && !firstInGroup) {
                        Spacer(Modifier.width(50.dp))
                    }
                    Column(
                        modifier = Modifier.widthIn(max = 250.dp),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 250.dp)
                                .background(
                                    if (mine) WxBubbleMe else Color.White,
                                    bubbleShape(mine, firstInGroup, lastInGroup),
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Column {
                                if (msg.content.isNotEmpty()) {
                                    MessageContent(msg.content, WxText)
                                }
                                if (msg.error != null) {
                                    Text(
                                        "[出错] ${msg.error.substringAfter(": ", msg.error)}",
                                        color = WxRed,
                                        fontSize = 12.sp,
                                    )
                                }
                                if (msg.content.isEmpty() && msg.error == null) {
                                    Text("…", color = WxText3, fontSize = 14.sp)
                                }
                            }
                        }
                        if (lastInGroup) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { clipboard.setText(AnnotatedString(msg.content)) },
                                    enabled = msg.content.isNotEmpty(),
                                ) {
                                    Text("复制", color = WxText2, fontSize = 12.sp)
                                }
                                val lastUser = messages.take(index).lastOrNull { it.role == "user" }
                                val isLastAssistant = msg.role == "assistant" &&
                                    index == messages.indexOfLast { it.role == "assistant" }
                                if (isLastAssistant && lastUser != null && !streaming) {
                                    TextButton(
                                        onClick = {
                                            send(regenerate = true, regenerateContent = lastUser.content)
                                        },
                                    ) {
                                        Text("重新生成", color = WxText2, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                    if (mine && firstInGroup) {
                        Spacer(Modifier.width(10.dp))
                        Avatar("我", null, size = 40.dp)
                    }
                    if (mine && !firstInGroup) {
                        Spacer(Modifier.width(50.dp))
                    }
                }
            }
        }

        if (streaming) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                TextButton(onClick = {
                    messages = messages.mapIndexed { index, message ->
                        if (index == messages.lastIndex && message.role == "assistant") {
                            message.copy(error = "生成已停止")
                        } else {
                            message
                        }
                    }
                    ChatGenerationTracker.stop(conversationId)
                    scope.launch {
                        delay(300)
                        try {
                            reload()
                        } catch (e: Exception) {
                            screenError = "刷新失败：${e.message}"
                        }
                    }
                }) {
                    Text("■ 停止", color = WxRed, fontSize = 13.sp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WxChatBackground)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = { Text("输入消息", color = WxText3) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (isHuman) sendHuman() else send() }),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { if (isHuman) sendHuman() else send() },
                enabled = input.isNotBlank() && !streaming,
                colors = ButtonDefaults.buttonColors(
                    containerColor = WxGreen,
                    disabledContainerColor = WxLineStrong,
                ),
            ) {
                Text("发送")
            }
        }
    }
}

private fun isMine(msg: UiMessage, isHuman: Boolean): Boolean =
    if (isHuman) msg.senderUserId == Backend.userId else msg.role == "user"

/** 时间分割线：第一条或与上一条消息间隔 ≥5 分钟。 */
private fun shouldShowTimeDivider(prev: UiMessage?, curr: UiMessage): Boolean {
    val prevTime = prev?.time ?: return true
    val currTime = curr.time ?: return false
    return try {
        val a = java.time.LocalDateTime.parse(prevTime.substringBefore("+").substringBefore("Z"))
        val b = java.time.LocalDateTime.parse(currTime.substringBefore("+").substringBefore("Z"))
        java.time.Duration.between(a, b).toMinutes() >= 5
    } catch (_: Exception) {
        true
    }
}

/** 微信式气泡圆角：尾巴角 4dp，其余 12dp；连续消息组内全圆角。 */
private fun bubbleShape(mine: Boolean, firstInGroup: Boolean, lastInGroup: Boolean): RoundedCornerShape {
    val small = 4.dp
    val large = 12.dp
    return if (mine) {
        when {
            firstInGroup && lastInGroup -> RoundedCornerShape(large, small, small, large)
            firstInGroup -> RoundedCornerShape(large, small, large, large)
            lastInGroup -> RoundedCornerShape(large, large, small, large)
            else -> RoundedCornerShape(large)
        }
    } else {
        when {
            firstInGroup && lastInGroup -> RoundedCornerShape(small, large, large, small)
            firstInGroup -> RoundedCornerShape(small, large, large, large)
            lastInGroup -> RoundedCornerShape(large, large, large, small)
            else -> RoundedCornerShape(large)
        }
    }
}
