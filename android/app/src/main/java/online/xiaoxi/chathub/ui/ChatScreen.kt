package online.xiaoxi.chathub.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
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
import online.xiaoxi.chathub.data.UiCache
import online.xiaoxi.chathub.data.UiMessage
import online.xiaoxi.chathub.data.WsEvent
import online.xiaoxi.chathub.data.WsHub
import online.xiaoxi.chathub.theme.WxBubbleMe
import online.xiaoxi.chathub.theme.WxChatBackground
import online.xiaoxi.chathub.theme.WxGreen
import online.xiaoxi.chathub.theme.WxLineStrong
import online.xiaoxi.chathub.theme.WxRed
import online.xiaoxi.chathub.theme.WxText
import online.xiaoxi.chathub.theme.WxText2
import online.xiaoxi.chathub.theme.Accent
import online.xiaoxi.chathub.theme.FriendBubble
import online.xiaoxi.chathub.theme.WxText3

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
    // 消息初始用缓存（退出重进时瞬时渲染，LazyColumn 首帧即定位底部，无加载抖动）
    val cachedMessages = remember { UiCache.messages[conversationId] ?: emptyList() }
    var messages by remember { mutableStateOf(cachedMessages) }
    var input by rememberSaveable { mutableStateOf("") }
    var screenError by remember { mutableStateOf<String?>(null) }
    var typingByPeer by remember { mutableStateOf(false) }
    var remoteLoaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (cachedMessages.size - 1).coerceAtLeast(0),
    )

    val isHuman = conv?.isHuman == true

    suspend fun reload() {
        // 会话信息（单会话端点）+ 消息并行拉取，减半 Tailscale 慢链路的等待
        coroutineScope {
            val convDeferred = async { api.conversation(conversationId) }
            val msgDeferred = async { api.messages(conversationId) }
            conv = convDeferred.await()
            val remote = msgDeferred.await().map {
                UiMessage(
                    it.id.toString(), it.role, it.content, it.error,
                    it.senderUserId, it.createdAt, it.read,
                )
            }
            // 合并本地乐观消息：临时 key（h-/a-/u-）且内容尚未在服务端结果中的保留，
            // 防止 reload 覆盖尚未确认的消息（并发互发时丢消息的根因）
            val remoteKey = remote.map { it.key }.toSet()
            val remoteSignature = remote.map { Triple(it.role, it.content, it.senderUserId) }.toSet()
            val optimistic = messages.filter { m ->
                (m.key.startsWith("h-") || m.key.startsWith("a-") || m.key.startsWith("u-")) &&
                    m.key !in remoteKey &&
                    Triple(m.role, m.content, m.senderUserId) !in remoteSignature
            }
            messages = remote + optimistic
            UiCache.messages[conversationId] = messages
            if (!remoteLoaded) {
                // 首次定位：与数据填充同帧（非挂起 requestScrollToItem 立即设置滚动目标），
                // 消除"进页先闪现顶部/空白再滚到底"的抖动；不受并发刷新取消影响。
                // 有缓存时 LazyColumn 首帧已在底部（initialFirstVisibleItemIndex），此分支只兜底无缓存场景
                remoteLoaded = true
                if (messages.isNotEmpty()) {
                    listState.requestScrollToItem(messages.size - 1)
                }
            }
        }
        screenError = null
    }

    // 串行化刷新：同一会话并发触发 reload 时取消旧的，防"旧数据后完成覆盖新数据"
    // （并发消息/回执乱序的根因之一）
    val reloadJobHolder = remember { mutableListOf<Job?>(null) }
    fun reloadAsync(errorPrefix: String = "刷新失败", then: (suspend () -> Unit)? = null) {
        reloadJobHolder[0]?.cancel()
        reloadJobHolder[0] = scope.launch {
            try {
                reload()
                then?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                screenError = "$errorPrefix：${e.message}"
            }
        }
    }

    // 人人会话：实时事件（对方消息/回执/typing/online）驱动刷新与提示
    DisposableEffect(conversationId) {
        val listener: (WsEvent) -> Unit = { event ->
            when (event) {
                is WsEvent.Message -> {
                    if (event.conversationId == conversationId) {
                        mainHandler.post {
                            // 立即追加：ws 数据直接上屏（零网络等待，平滑插入不抖动），
                            // 后台 reload 校准顺序/完整性（漏消息由此补齐）
                            val key = event.messageId.toString()
                            if (messages.none { it.key == key }) {
                                messages = messages + UiMessage(
                                    key, "user", event.content, null, event.from, event.ts,
                                )
                            }
                            // 正在看该会话 → 新消息即已读：与 reload 并行，互不阻塞
                            // （reload 失败不丢已读上报）
                            scope.launch { runCatching { api.markRead(conversationId) } }
                            reloadAsync()
                        }
                    }
                }
                is WsEvent.Ack -> {
                    if (event.conversationId == conversationId) {
                        mainHandler.post {
                            // 乐观消息（h-/a- 临时 key）→ 服务端真实 id：
                            // 按内容匹配第一条未确认的乐观消息，稳定映射（并发多条不串位）
                            val content = event.content
                            if (!content.isNullOrEmpty()) {
                                val index = messages.indexOfFirst {
                                    (it.key.startsWith("h-") || it.key.startsWith("a-")) &&
                                        it.content == content
                                }
                                if (index >= 0) {
                                    messages = messages.toMutableList().apply {
                                        this[index] = this[index].copy(key = event.messageId.toString())
                                    }
                                }
                            }
                            reloadAsync()
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
                is WsEvent.Read -> {
                    // 对方已读回执：本地即时更新"自己发出去的消息"的已读状态
                    // （senderUserId 必须是自己——之前误用 event.from 匹配导致永远不更新）。
                    // 若消息仍是乐观 key（Ack 未到，key 非数字），本地更新跳过，
                    // 用后台 reload 校准兜底（服务端 read 字段已计算，重拉即显示已读）
                    if (event.conversationId == conversationId) {
                        mainHandler.post {
                            messages = messages.map {
                                val id = it.key.toIntOrNull()
                                if (it.senderUserId == Backend.userId && id != null && id <= event.lastReadMsgId) {
                                    it.copy(read = true)
                                } else {
                                    it
                                }
                            }
                            reloadAsync()
                        }
                    }
                }
                is WsEvent.Sync -> {
                    // ws 断线重连后服务端发 sync：人人会话补齐离线期间错过的消息
                    // （AI 会话跳过——AI 回答走独立 SSE 链路，不受 ws 重连影响，也避免打断流式渲染）
                    if (conv?.isHuman == true) {
                        mainHandler.post {
                            reloadAsync()
                        }
                    }
                }
                is WsEvent.Avatar -> {
                    // 对方换了头像：刷新会话拿到新头像 URL（URL 带 ?v= 版本，Avatar 自动重载）
                    if (event.userId == conv?.peerUserId) {
                        mainHandler.post {
                            reloadAsync()
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
        // 初次加载：串行化（取消进行中的刷新，防旧数据覆盖），完成后标记已读
        // （首次定位到底部由 reload() 内部完成，不受并发刷新取消影响）
        reloadAsync("加载失败") {
            try {
                api.markRead(conversationId)
            } catch (_: Exception) {
            }
        }
    }

    // 首次定位到底部：由 remoteLoaded（服务端数据就绪）触发，独立于刷新协程生命周期，
    // 不受并发 reload 取消影响（挂起 scrollToItem 等待布局完成，定位准确）
    LaunchedEffect(remoteLoaded) {
        if (remoteLoaded && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { messages.size }.distinctUntilChanged().collect { size ->
            // 仅在初次定位完成（remoteLoaded）后跟随新消息，避免与首次定位竞争
            if (size > 0 && remoteLoaded) {
                // 仅当用户正贴住底部（最后可见项在末尾且底部贴近视口底）时
                // 才平滑跟随新消息；上滑阅读历史时不打扰（微信式行为）
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                val atBottom = last != null &&
                    last.index >= info.totalItemsCount - 3 &&
                    last.offset + last.size >= info.viewportEndOffset - 60
                if (atBottom) listState.animateScrollToItem(size - 1)
            }
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
            reloadAsync()
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
        val ok = WsHub.sendMessage(peer, text)
        if (!ok) {
            screenError = "实时通道未连接，正在重连…"
            WsHub.restart()
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streaming) return
        input = ""
        val assistantKey = "a-${System.currentTimeMillis()}"
        val baseMessages = messages + UiMessage("u-$assistantKey", "user", text, null)
        messages = baseMessages +
            UiMessage(assistantKey, "assistant", "", null)
        screenError = null
        api.startChat(
            conversationId = conversationId,
            content = text,
            onStart = { ChatGenerationTracker.start(conversationId, it) },
        ) { event ->
            mainHandler.post {
                when (event) {
                    is ChatEvent.Token -> messages = messages.map {
                        if (it.key == assistantKey) it.copy(content = it.content + event.delta) else it
                    }
                    is ChatEvent.Done -> {
                        ChatGenerationTracker.finish(conversationId)
                        // 推进已读不依赖本页面作用域：AI 回复是用户自己触发的，
                        // 中途退出后回复完成也必须推进已读，否则列表重新出现未读红点
                        GlobalScope.launch {
                            runCatching { api.markRead(conversationId) }
                        }
                        reloadAsync()
                    }
                    is ChatEvent.Error -> {
                        messages = messages.map {
                            if (it.key == assistantKey) it.copy(error = event.message) else it
                        }
                        ChatGenerationTracker.finish(conversationId)
                        GlobalScope.launch {
                            runCatching { api.markRead(conversationId) }
                        }
                        reloadAsync()
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(WxChatBackground)
            .statusBarsPadding()
            .imePadding(),
    ) {
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
                        typingByPeer -> "对方正在输入…"
                        streaming -> "对方正在输入…"
                        isHuman -> ""  // 人人会话不显示副标题（避免与标题重复）
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
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            // 加载占位：消息未到时同容器内显示轻量加载，内容到达直接替换，无布局跳动
            if (messages.isEmpty() && screenError == null) {
                item {
                    Box(
                        Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Accent,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("加载中…", color = WxText3, fontSize = 14.sp)
                        }
                    }
                }
            }
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
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                ) {
                    // 每条消息独立：头像垂直居中于气泡，间隔均匀（微信 A-B-A 一致）
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!mine) {
                            Avatar(
                                conv?.contactName ?: "A",
                                conv?.avatarColor,
                                size = 40.dp,
                                onClick = onInfo,
                                imageUrl = if (isHuman) conv?.avatar else null,
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        // 已读/未读：自己的消息显示在气泡左侧小字（未读强调色、已读灰）
                        if (mine && isHuman) {
                            Text(
                                if (msg.read) "已读" else "未读",
                                color = if (msg.read) WxText2 else Accent,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Box {
                            // 气泡
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 250.dp)
                                    .background(
                                        if (mine) WxBubbleMe else FriendBubble,
                                        bubbleShape(mine),
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

                        }
                        if (mine) {
                            Spacer(Modifier.width(10.dp))
                            Avatar("我", null, size = 40.dp, imageUrl = Backend.myAvatar)
                        }
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
                        reloadAsync()
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
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { state ->
                        // 输入框获焦（键盘弹出）时滚动到最新消息
                        if (state.isFocused && messages.isNotEmpty()) {
                            scope.launch { listState.scrollToItem(messages.size - 1) }
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF2F2F7),
                    unfocusedContainerColor = Color(0xFFF2F2F7),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                placeholder = { Text("输入消息", color = WxText3) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (isHuman) sendHuman() else send() }),
                maxLines = 4,
            )
            Spacer(Modifier.width(4.dp))
            TextButton(
                onClick = { if (isHuman) sendHuman() else send() },
                enabled = input.isNotBlank() && !streaming,
            ) {
                Text(
                    "发送",
                    fontSize = 15.sp,
                    color = if (input.isNotBlank() && !streaming) Accent else WxText3,
                )
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

/** 独立气泡圆角：尖角 8dp 朝说话者一侧，其余 12dp（skill 规范）。 */
private fun bubbleShape(mine: Boolean): RoundedCornerShape {
    val small = 8.dp
    val large = 12.dp
    return if (mine) {
        RoundedCornerShape(large, small, small, large)
    } else {
        RoundedCornerShape(small, large, large, small)
    }
}
